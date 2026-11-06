package com.example.bi.service.ingestion;

import com.example.bi.domain.Dataset;
import com.example.bi.domain.ImportJob;
import com.example.bi.domain.ImportJobStatus;
import com.example.bi.dto.*;
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
import java.util.*;

@Service
public class StagingRowEditService {

    private final DatasetAccessService datasetAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final ImportJobRepository importJobRepository;
    private final StagingRepository stagingRepository;
    private final Clock clock;

    public StagingRowEditService(
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
    public EditRowResponse editRow(UUID datasetId, UUID jobId, int rowNumber, EditRowRequest request) {
        // 1. Ownership check - throws 404 if not found/not owned
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        // 2. Lock job for update
        ImportJob job = importJobRepository.findByIdAndDatasetIdAndOwnerIdForUpdate(jobId, datasetId, userId)
                .orElseThrow(() -> new DatasetNotFoundException("Import job not found: " + jobId));

        // 3. Only VALIDATED jobs can be edited
        if (job.getStatus() != ImportJobStatus.VALIDATED) {
            throw new ImportNotEditableException(
                    "Import job is in status " + job.getStatus() + " and cannot be edited. Row editing is only allowed in VALIDATED status."
            );
        }

        // 4. Staging row must exist
        StagingRepository.StagingRowData existingRow = stagingRepository.findStagingRow(jobId, rowNumber)
                .orElseThrow(() -> new DatasetNotFoundException("Staging row not found: " + rowNumber));

        // 5. Build raw cell map and validate row
        Map<String, String> cells = new HashMap<>();
        cells.put("order_id", request.orderId());
        cells.put("order_date", request.orderDate());
        cells.put("product_name", request.productName());
        cells.put("category", request.category());
        cells.put("region", request.region());
        cells.put("quantity", request.quantity());
        cells.put("unit_price", request.unitPrice());
        cells.put("unit_cost", request.unitCost());

        RowNormalizer normalizer = new RowNormalizer();
        RowValidator validator = new RowValidator(clock, job.getDateFormat(), normalizer);
        ParsedRow parsedRow = validator.validateRow(rowNumber, cells);

        // 6. Update row in database
        stagingRepository.updateStagingRow(jobId, parsedRow);

        // 7. Replace row issues
        stagingRepository.deleteIssuesForRow(jobId, rowNumber);
        if (!parsedRow.issues().isEmpty()) {
            stagingRepository.saveIssues(jobId, parsedRow.issues());
        }

        // 8. Re-evaluate duplicate detection across all rows
        stagingRepository.resetDuplicateCandidates(jobId);
        stagingRepository.markDuplicates(jobId);

        // 9. Recompute staging counts
        StagingRepository.StagingCounts stagingCounts = stagingRepository.calculateCounts(jobId);

        // 10. Update ImportJob entity counts
        job.setValidRows(stagingCounts.validRows());
        job.setInvalidRows(stagingCounts.invalidRows());
        job.setDuplicateRows(stagingCounts.duplicateRows());
        job.setWarningCount(stagingCounts.warningCount());
        importJobRepository.save(job);

        // 11. Fetch final state of the row and its issues
        StagingRepository.StagingRowData updatedRowData = stagingRepository.findStagingRow(jobId, rowNumber)
                .orElseThrow(() -> new DatasetNotFoundException("Staging row not found after update: " + rowNumber));

        Map<Integer, List<RowIssue>> issuesMap = stagingRepository.findIssuesForRows(jobId, List.of(rowNumber));
        List<RowIssue> rowIssues = issuesMap.getOrDefault(rowNumber, Collections.emptyList());
        List<RowIssueDto> issueDtos = rowIssues.stream()
                .map(i -> new RowIssueDto(
                        i.columnName(),
                        i.severity().name(),
                        i.code(),
                        i.message(),
                        i.rawValue()
                ))
                .toList();

        RowValuesDto values = new RowValuesDto(
                updatedRowData.orderId(),
                updatedRowData.orderDate() != null ? updatedRowData.orderDate().toString() : null,
                updatedRowData.productName(),
                updatedRowData.category(),
                updatedRowData.region(),
                updatedRowData.quantity(),
                formatMoney(updatedRowData.unitPrice()),
                formatMoney(updatedRowData.unitCost())
        );

        PreviewRowDto previewRowDto = new PreviewRowDto(
                updatedRowData.sourceRowNumber(),
                updatedRowData.rowStatus().name(),
                values,
                updatedRowData.rawValues(),
                issueDtos
        );

        int totalStaged = stagingCounts.validRows() + stagingCounts.invalidRows() + stagingCounts.duplicateRows();
        PreviewCountsDto countsDto = new PreviewCountsDto(
                totalStaged,
                stagingCounts.validRows(),
                stagingCounts.invalidRows(),
                stagingCounts.duplicateRows(),
                stagingCounts.warningCount()
        );

        return new EditRowResponse(previewRowDto, countsDto);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
