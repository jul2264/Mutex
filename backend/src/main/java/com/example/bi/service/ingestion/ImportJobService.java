package com.example.bi.service.ingestion;

import com.example.bi.domain.ImportJob;
import com.example.bi.domain.ImportJobStatus;
import com.example.bi.domain.ImportPolicy;
import com.example.bi.dto.FileErrorDto;
import com.example.bi.dto.ImportJobStatusResponse;
import com.example.bi.exception.DatasetNotFoundException;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.StagingRepository;
import com.example.bi.service.identity.CurrentUserProvider;
import com.example.bi.service.identity.DatasetAccessService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.UUID;

@Service
public class ImportJobService {

    private final DatasetAccessService datasetAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final ImportJobRepository importJobRepository;
    private final StagingRepository stagingRepository;

    public ImportJobService(
            DatasetAccessService datasetAccessService,
            CurrentUserProvider currentUserProvider,
            ImportJobRepository importJobRepository,
            StagingRepository stagingRepository
    ) {
        this.datasetAccessService = datasetAccessService;
        this.currentUserProvider = currentUserProvider;
        this.importJobRepository = importJobRepository;
        this.stagingRepository = stagingRepository;
    }

    public ImportJobStatusResponse getJobStatus(UUID datasetId, UUID jobId) {
        datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        ImportJob job = importJobRepository.findByIdAndDatasetIdAndOwnerId(jobId, datasetId, userId)
                .orElseThrow(() -> new DatasetNotFoundException("Import job not found: " + jobId));

        boolean canConfirm = false;
        String confirmBlockedReason = null;

        if (job.getStatus() == ImportJobStatus.VALIDATED) {
            if (job.getValidRows() == 0) {
                confirmBlockedReason = "No valid rows available to import";
            } else if (job.getPolicy() != ImportPolicy.IMPORT_VALID_ONLY && job.getInvalidRows() > 0) {
                confirmBlockedReason = "Strict policy prevents import when invalid rows exist";
            } else {
                canConfirm = true;
            }
        } else if (job.getStatus() == ImportJobStatus.PARSING) {
            confirmBlockedReason = "Import file is still parsing";
        } else if (job.getStatus() == ImportJobStatus.FAILED) {
            confirmBlockedReason = "Import job failed validation";
        } else if (job.getStatus() == ImportJobStatus.COMPLETED) {
            confirmBlockedReason = "Import job has already been completed";
        } else if (job.getStatus() == ImportJobStatus.CANCELLED) {
            confirmBlockedReason = "Import job was cancelled";
        } else if (job.getStatus() == ImportJobStatus.EXPIRED) {
            confirmBlockedReason = "Import job has expired";
        }

        FileErrorDto fileError = (job.getFileErrorCode() != null)
                ? new FileErrorDto(job.getFileErrorCode(), job.getFileErrorMessage())
                : null;

        return new ImportJobStatusResponse(
                job.getId(),
                job.getDataset().getId(),
                job.getStatus().name(),
                job.getPolicy().name(),
                job.getOriginalFilename(),
                job.getRowsProcessed(),
                job.getTotalRows(),
                job.getValidRows(),
                job.getInvalidRows(),
                job.getDuplicateRows(),
                job.getWarningCount(),
                job.getBlankRowsSkipped(),
                job.isIssuesTruncated(),
                canConfirm,
                confirmBlockedReason,
                fileError,
                job.getExpiresAt()
        );
    }

    public void checkAccess(UUID datasetId, UUID jobId) {
        datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();
        if (!importJobRepository.existsByIdAndDatasetIdAndOwnerId(jobId, datasetId, userId)) {
            throw new DatasetNotFoundException("Import job not found: " + jobId);
        }
    }

    public void writeErrorReportCsv(UUID datasetId, UUID jobId, Writer writer) throws IOException {
        checkAccess(datasetId, jobId);

        List<RowIssue> issues = stagingRepository.findIssuesByJobId(jobId);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("source_row_number", "column_name", "severity", "code", "message", "raw_value")
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (RowIssue issue : issues) {
                printer.printRecord(
                        issue.sourceRowNumber(),
                        FormulaSanitizer.sanitizeText(issue.columnName()),
                        issue.severity().name(),
                        issue.code(),
                        FormulaSanitizer.sanitizeText(issue.message()),
                        FormulaSanitizer.sanitizeText(issue.rawValue())
                );
            }
            printer.flush();
        }
    }
}
