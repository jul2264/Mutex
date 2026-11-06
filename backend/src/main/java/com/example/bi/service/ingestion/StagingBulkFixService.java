package com.example.bi.service.ingestion;

import com.example.bi.domain.Dataset;
import com.example.bi.domain.ImportJob;
import com.example.bi.domain.ImportJobStatus;
import com.example.bi.dto.BulkFixRequest;
import com.example.bi.dto.BulkFixResponse;
import com.example.bi.dto.DiscardDuplicatesResponse;
import com.example.bi.dto.PreviewCountsDto;
import com.example.bi.exception.DatasetNotFoundException;
import com.example.bi.exception.ImportNotEditableException;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.StagingRepository;
import com.example.bi.service.identity.CurrentUserProvider;
import com.example.bi.service.identity.DatasetAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

@Service
public class StagingBulkFixService {

    private final DatasetAccessService datasetAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final ImportJobRepository importJobRepository;
    private final StagingRepository stagingRepository;
    private final Clock clock;

    public StagingBulkFixService(
            DatasetAccessService datasetAccessService,
            CurrentUserProvider currentUserProvider,
            ImportJobRepository importJobRepository,
            StagingRepository stagingRepository,
            Clock clock
    ) {
        this.datasetAccessService = datasetAccessService;
        this.currentUserProvider = currentUserProvider;
        this.importJobRepository = importJobRepository;
        this.stagingRepository = stagingRepository;
        this.clock = clock;
    }

    @Transactional
    public BulkFixResponse applyBulkFix(UUID datasetId, UUID jobId, BulkFixRequest request) {
        // 1. Verify ownership (returns 404 if not found/not owned)
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        // 2. Lock job for update
        ImportJob job = importJobRepository.findByIdAndDatasetIdAndOwnerIdForUpdate(jobId, datasetId, userId)
                .orElseThrow(() -> new DatasetNotFoundException("Import job not found: " + jobId));

        // 3. State guard: must be VALIDATED
        if (job.getStatus() != ImportJobStatus.VALIDATED) {
            throw new ImportNotEditableException(
                    "Import job is in status " + job.getStatus() + " and cannot be repaired. Bulk fix is only allowed in VALIDATED status."
            );
        }

        // 4. Resolve latest valid order date
        LocalDate latestValidDate = stagingRepository.findLatestValidOrderDate(jobId);
        if (latestValidDate == null) {
            latestValidDate = LocalDate.now(clock);
        }

        // 5. Query all invalid staging rows
        List<StagingRepository.StagingRowData> invalidRows = stagingRepository.findInvalidStagingRows(jobId);
        if (invalidRows.isEmpty()) {
            StagingRepository.StagingCounts counts = stagingRepository.calculateCounts(jobId);
            PreviewCountsDto countsDto = new PreviewCountsDto(
                    counts.validRows() + counts.invalidRows() + counts.duplicateRows(),
                    counts.validRows(),
                    counts.invalidRows(),
                    counts.duplicateRows(),
                    counts.warningCount()
            );
            return new BulkFixResponse(0, 0, 0, 0, countsDto);
        }

        RowNormalizer normalizer = new RowNormalizer();
        RowValidator validator = new RowValidator(clock, job.getDateFormat(), normalizer);

        List<ParsedRow> rowsToUpdate = new ArrayList<>();
        List<Integer> rowNumbersToUpdate = new ArrayList<>();
        List<RowIssue> allNewIssues = new ArrayList<>();

        int repairedToValid = 0;
        int untouchedLargeQuantityCount = 0;

        for (StagingRepository.StagingRowData row : invalidRows) {
            Map<String, Object> raw = row.rawValues() != null ? row.rawValues() : Collections.emptyMap();

            String rawQty = raw.get("quantity") != null ? raw.get("quantity").toString() : (row.quantity() != null ? row.quantity().toString() : null);

            // Out-of-range quantities (> 1,000,000): cap to 1,000,000 if enabled, else keep untouched for manual review
            boolean isLarge = isLargeQuantity(rawQty, row.quantity());
            if (isLarge) {
                if (request.isCapExcessiveQuantity()) {
                    rawQty = "1000000";
                } else {
                    untouchedLargeQuantityCount++;
                    continue;
                }
            }

            Map<String, String> cells = new HashMap<>();
            cells.put("order_id", getCellString(raw, "order_id", row.orderId()));
            cells.put("order_date", transformDate(raw, row.orderDate(), request, latestValidDate));
            cells.put("product_name", transformProduct(raw, row.productName(), request));
            cells.put("category", transformCategory(raw, row.category(), request));
            cells.put("region", transformRegion(raw, row.region(), request));
            cells.put("quantity", transformQuantity(rawQty, request));
            cells.put("unit_price", transformMoney(raw, "unit_price", row.unitPrice(), request));
            cells.put("unit_cost", transformMoney(raw, "unit_cost", row.unitCost(), request));

            ParsedRow parsedRow = validator.validateRow(row.sourceRowNumber(), cells);
            rowsToUpdate.add(parsedRow);
            rowNumbersToUpdate.add(row.sourceRowNumber());
            allNewIssues.addAll(parsedRow.issues());

            if (parsedRow.isValid()) {
                repairedToValid++;
            }
        }

        // 6. Batch update staging rows and replace issues
        if (!rowsToUpdate.isEmpty()) {
            stagingRepository.batchUpdateStagingRows(jobId, rowsToUpdate);
            stagingRepository.batchReplaceIssues(jobId, rowNumbersToUpdate, allNewIssues);
        }

        // 7. Duplicate detection re-evaluation
        stagingRepository.resetDuplicateCandidates(jobId);
        stagingRepository.markDuplicates(jobId);

        // 8. Re-calculate counts and persist to job entity
        StagingRepository.StagingCounts stagingCounts = stagingRepository.calculateCounts(jobId);
        job.setValidRows(stagingCounts.validRows());
        job.setInvalidRows(stagingCounts.invalidRows());
        job.setDuplicateRows(stagingCounts.duplicateRows());
        job.setWarningCount(stagingCounts.warningCount());
        importJobRepository.save(job);

        int totalStaged = stagingCounts.validRows() + stagingCounts.invalidRows() + stagingCounts.duplicateRows();
        PreviewCountsDto countsDto = new PreviewCountsDto(
                totalStaged,
                stagingCounts.validRows(),
                stagingCounts.invalidRows(),
                stagingCounts.duplicateRows(),
                stagingCounts.warningCount()
        );

        return new BulkFixResponse(
                invalidRows.size(),
                repairedToValid,
                stagingCounts.invalidRows(),
                untouchedLargeQuantityCount,
                countsDto
        );
    }

    private boolean isLargeQuantity(String rawQty, Integer parsedQty) {
        if (parsedQty != null && parsedQty > 1_000_000) {
            return true;
        }
        if (rawQty != null && !rawQty.isBlank()) {
            String clean = RowNormalizer.cleanNumericText(rawQty);
            try {
                BigDecimal bd = new BigDecimal(clean);
                return bd.compareTo(new BigDecimal("1000000")) > 0;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private String getCellString(Map<String, Object> raw, String key, Object fallback) {
        if (raw.containsKey(key) && raw.get(key) != null) {
            return raw.get(key).toString();
        }
        return fallback != null ? fallback.toString() : "";
    }

    private String transformDate(Map<String, Object> raw, LocalDate existingDate, BulkFixRequest request, LocalDate latestValidDate) {
        String original = getCellString(raw, "order_date", existingDate);
        if (existingDate != null && (original == null || original.isBlank())) {
            return existingDate.toString();
        }

        // Check if date is missing or malformed
        boolean isDateIssue = (original == null || original.isBlank() || existingDate == null);

        if (isDateIssue) {
            if (request.isDateCustom() && request.customDate() != null && !request.customDate().isBlank()) {
                return request.customDate().strip();
            }
            if (request.isDateLatestValid() || request.dateFixStrategy() == null || "DEFAULT".equalsIgnoreCase(request.dateFixStrategy())) {
                return latestValidDate.toString();
            }
        }
        return original != null ? original : "";
    }

    private String transformProduct(Map<String, Object> raw, String existingProduct, BulkFixRequest request) {
        String original = getCellString(raw, "product_name", existingProduct);
        if (original.isBlank() && (request.shouldFillProduct() || request.fillMissingProduct() == null)) {
            String placeholder = request.productPlaceholder();
            return (placeholder != null && !placeholder.isBlank()) ? placeholder.strip() : "Unspecified Product";
        }
        return original;
    }

    private String transformCategory(Map<String, Object> raw, String existingCategory, BulkFixRequest request) {
        String original = getCellString(raw, "category", existingCategory);
        if (original.isBlank() && (request.shouldFillCategory() || request.fillMissingCategory() == null)) {
            String placeholder = request.categoryPlaceholder();
            return (placeholder != null && !placeholder.isBlank()) ? placeholder.strip() : "Uncategorized";
        }
        return original;
    }

    private String transformRegion(Map<String, Object> raw, String existingRegion, BulkFixRequest request) {
        String original = getCellString(raw, "region", existingRegion);
        if (original.isBlank() && (request.shouldFillRegion() || request.fillMissingRegion() == null)) {
            String placeholder = request.regionPlaceholder();
            return (placeholder != null && !placeholder.isBlank()) ? placeholder.strip() : "Unassigned";
        }
        return original;
    }

    private String transformQuantity(String rawQty, BulkFixRequest request) {
        if (rawQty == null || rawQty.isBlank()) {
            return "";
        }
        String clean = RowNormalizer.cleanNumericText(rawQty);
        try {
            if (clean.contains(".")) {
                BigDecimal bd = new BigDecimal(clean);
                if (request.isCapExcessiveQuantity() && bd.compareTo(new BigDecimal("1000000")) > 0) {
                    return "1000000";
                }
                if (bd.stripTrailingZeros().scale() > 0) {
                    if (request.isDecimalQuantityTruncate()) {
                        return bd.setScale(0, RoundingMode.FLOOR).toPlainString();
                    } else if (request.isDecimalQuantityRound() || request.decimalQuantityStrategy() == null || "DEFAULT".equalsIgnoreCase(request.decimalQuantityStrategy())) {
                        return bd.setScale(0, RoundingMode.HALF_UP).toPlainString();
                    }
                }
            } else {
                long val = Long.parseLong(clean);
                if (request.isCapExcessiveQuantity() && val > 1_000_000) {
                    return "1000000";
                }
                if (val <= 0 && (request.isZeroQuantitySetOne() || request.zeroQuantityStrategy() == null || "DEFAULT".equalsIgnoreCase(request.zeroQuantityStrategy()))) {
                    return "1";
                }
            }
        } catch (Exception ignored) {}
        return rawQty;
    }

    private String transformMoney(Map<String, Object> raw, String key, BigDecimal existingValue, BulkFixRequest request) {
        String original = getCellString(raw, key, existingValue);
        if (original == null || original.isBlank()) {
            return "";
        }
        String clean = RowNormalizer.cleanNumericText(original);
        if (clean.startsWith("-")) {
            if (request.isNegativePriceAbs() || request.negativePriceStrategy() == null || "DEFAULT".equalsIgnoreCase(request.negativePriceStrategy())) {
                return clean.substring(1).stripLeading();
            }
        }
        return original;
    }

    @Transactional
    public DiscardDuplicatesResponse discardDuplicates(UUID datasetId, UUID jobId) {
        // 1. Ownership check (throws 404 if not owned)
        datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        // 2. Lock job for update
        ImportJob job = importJobRepository.findByIdAndDatasetIdAndOwnerIdForUpdate(jobId, datasetId, userId)
                .orElseThrow(() -> new DatasetNotFoundException("Import job not found: " + jobId));

        // 3. State guard: must be VALIDATED
        if (job.getStatus() != ImportJobStatus.VALIDATED) {
            throw new ImportNotEditableException(
                    "Import job is in status " + job.getStatus() + " and duplicates cannot be discarded. Only allowed in VALIDATED status."
            );
        }

        // 4. Delete duplicate candidates and associated issues
        int discarded = stagingRepository.deleteDuplicateCandidates(jobId);

        // 5. Re-calculate counts
        StagingRepository.StagingCounts stagingCounts = stagingRepository.calculateCounts(jobId);
        int totalStaged = stagingCounts.validRows() + stagingCounts.invalidRows() + stagingCounts.duplicateRows();

        job.setTotalRows(totalStaged);
        job.setValidRows(stagingCounts.validRows());
        job.setInvalidRows(stagingCounts.invalidRows());
        job.setDuplicateRows(stagingCounts.duplicateRows());
        job.setWarningCount(stagingCounts.warningCount());
        importJobRepository.save(job);

        PreviewCountsDto countsDto = new PreviewCountsDto(
                totalStaged,
                stagingCounts.validRows(),
                stagingCounts.invalidRows(),
                stagingCounts.duplicateRows(),
                stagingCounts.warningCount()
        );

        return new DiscardDuplicatesResponse(discarded, countsDto);
    }
}

