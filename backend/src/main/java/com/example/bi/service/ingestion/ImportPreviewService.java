package com.example.bi.service.ingestion;

import com.example.bi.domain.Dataset;
import com.example.bi.domain.ImportJob;
import com.example.bi.domain.ImportJobStatus;
import com.example.bi.domain.RowStatus;
import com.example.bi.dto.*;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.SalesRecordRepository;
import com.example.bi.repository.StagingRepository;
import com.example.bi.service.identity.CurrentUserProvider;
import com.example.bi.service.identity.DatasetAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ImportPreviewService {

    private final DatasetAccessService datasetAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final ImportJobRepository importJobRepository;
    private final StagingRepository stagingRepository;
    private final SalesRecordRepository salesRecordRepository;

    public ImportPreviewService(
            DatasetAccessService datasetAccessService,
            CurrentUserProvider currentUserProvider,
            ImportJobRepository importJobRepository,
            StagingRepository stagingRepository,
            SalesRecordRepository salesRecordRepository
    ) {
        this.datasetAccessService = datasetAccessService;
        this.currentUserProvider = currentUserProvider;
        this.importJobRepository = importJobRepository;
        this.stagingRepository = stagingRepository;
        this.salesRecordRepository = salesRecordRepository;
    }

    public PreviewResponse getPreview(UUID datasetId, UUID jobId, String sourceParam, String statusParam, int page, int size) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        int pageSize = Math.clamp(size, 1, 100);
        int pageIndex = Math.max(page, 0);
        int offset = pageIndex * pageSize;

        // Determine source
        String source = resolveSource(datasetId, userId, jobId, sourceParam);

        if ("COMMITTED".equalsIgnoreCase(source)) {
            return getCommittedPreview(dataset, pageIndex, pageSize, offset);
        } else {
            return getStagedPreview(datasetId, userId, jobId, statusParam, pageIndex, pageSize, offset);
        }
    }

    public PreviewResponse getPreview(UUID datasetId, String sourceParam, String statusParam, int page, int size) {
        return getPreview(datasetId, null, sourceParam, statusParam, page, size);
    }

    private String resolveSource(UUID datasetId, UUID userId, UUID jobId, String sourceParam) {
        if (sourceParam != null && !sourceParam.isBlank()) {
            return sourceParam.toUpperCase(Locale.ROOT);
        }

        if (jobId != null) {
            Optional<ImportJob> jobOpt = importJobRepository.findByIdAndDatasetIdAndOwnerId(jobId, datasetId, userId);
            if (jobOpt.isPresent()) {
                ImportJob job = jobOpt.get();
                if (job.getStatus() == ImportJobStatus.PARSING || job.getStatus() == ImportJobStatus.VALIDATED) {
                    return "STAGED";
                } else if (job.getStatus() == ImportJobStatus.COMPLETED) {
                    return "COMMITTED";
                }
            }
        }

        // Default: STAGED when a VALIDATED or PARSING job exists, otherwise COMMITTED
        List<ImportJob> activeJobs = importJobRepository.findByDatasetIdAndStatusIn(
                datasetId,
                List.of(ImportJobStatus.PARSING, ImportJobStatus.VALIDATED)
        );
        return (!activeJobs.isEmpty()) ? "STAGED" : "COMMITTED";
    }

    private PreviewResponse getCommittedPreview(Dataset dataset, int page, int size, int offset) {
        long totalElements = salesRecordRepository.countCommittedRows(dataset.getId());
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<SalesRecordRepository.CommittedRowData> rows =
                salesRecordRepository.findCommittedRows(dataset.getId(), offset, size);

        List<PreviewRowDto> content = new ArrayList<>();
        int displayRow = offset + 1;
        for (SalesRecordRepository.CommittedRowData r : rows) {
            int rowNumber = (r.sourceRowNumber() != null) ? r.sourceRowNumber() : displayRow++;
            RowValuesDto values = new RowValuesDto(
                    r.orderId(),
                    r.orderDate() != null ? r.orderDate().toString() : null,
                    r.productName(),
                    r.category(),
                    r.region(),
                    r.quantity(),
                    formatMoney(r.unitPrice()),
                    formatMoney(r.unitCost())
            );

            content.add(new PreviewRowDto(
                    rowNumber,
                    "VALID",
                    values,
                    null,
                    Collections.emptyList()
            ));
        }

        PreviewCountsDto counts = new PreviewCountsDto(
                (int) totalElements,
                (int) totalElements,
                0,
                0,
                0
        );

        return new PreviewResponse("COMMITTED", counts, page, size, totalElements, totalPages, content);
    }

    private PreviewResponse getStagedPreview(
            UUID datasetId,
            UUID userId,
            UUID jobId,
            String statusParam,
            int page,
            int size,
            int offset
    ) {
        ImportJob job = null;
        if (jobId != null) {
            job = importJobRepository.findByIdAndDatasetIdAndOwnerId(jobId, datasetId, userId).orElse(null);
        }
        if (job == null) {
            List<ImportJob> activeJobs = importJobRepository.findByDatasetIdAndStatusIn(
                    datasetId,
                    List.of(ImportJobStatus.PARSING, ImportJobStatus.VALIDATED)
            );
            if (!activeJobs.isEmpty()) {
                job = activeJobs.get(0);
            } else {
                job = importJobRepository.findLatestByDatasetIdAndOwnerId(datasetId, userId).orElse(null);
            }
        }

        if (job == null) {
            PreviewCountsDto counts = new PreviewCountsDto(0, 0, 0, 0, 0);
            return new PreviewResponse("STAGED", counts, page, size, 0L, 0, Collections.emptyList());
        }

        UUID effectiveJobId = job.getId();

        // Calculate counts
        StagingRepository.StagingCounts stagingCounts = stagingRepository.calculateCounts(effectiveJobId);
        int totalStaged = stagingCounts.validRows() + stagingCounts.invalidRows() + stagingCounts.duplicateRows();
        PreviewCountsDto counts = new PreviewCountsDto(
                totalStaged,
                stagingCounts.validRows(),
                stagingCounts.invalidRows(),
                stagingCounts.duplicateRows(),
                stagingCounts.warningCount()
        );

        RowStatus statusFilter = null;
        if (statusParam != null && !statusParam.isBlank() && !"ALL".equalsIgnoreCase(statusParam)) {
            try {
                statusFilter = RowStatus.valueOf(statusParam.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }

        long totalElements = stagingRepository.countStagingRows(effectiveJobId, statusFilter);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<StagingRepository.StagingRowData> stagedRows =
                stagingRepository.findStagingRows(effectiveJobId, statusFilter, offset, size);

        List<Integer> rowNumbers = stagedRows.stream()
                .map(StagingRepository.StagingRowData::sourceRowNumber)
                .toList();

        Map<Integer, List<RowIssue>> issuesMap = stagingRepository.findIssuesForRows(effectiveJobId, rowNumbers);

        List<PreviewRowDto> content = new ArrayList<>();
        for (StagingRepository.StagingRowData r : stagedRows) {
            RowValuesDto values = new RowValuesDto(
                    r.orderId(),
                    r.orderDate() != null ? r.orderDate().toString() : null,
                    r.productName(),
                    r.category(),
                    r.region(),
                    r.quantity(),
                    formatMoney(r.unitPrice()),
                    formatMoney(r.unitCost())
            );

            List<RowIssue> rowIssues = issuesMap.getOrDefault(r.sourceRowNumber(), Collections.emptyList());
            List<RowIssueDto> issueDtos = rowIssues.stream()
                    .map(i -> new RowIssueDto(
                            i.columnName(),
                            i.severity().name(),
                            i.code(),
                            i.message(),
                            i.rawValue()
                    ))
                    .toList();

            content.add(new PreviewRowDto(
                    r.sourceRowNumber(),
                    r.rowStatus().name(),
                    values,
                    r.rawValues(),
                    issueDtos
            ));
        }

        return new PreviewResponse("STAGED", counts, page, size, totalElements, totalPages, content);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

