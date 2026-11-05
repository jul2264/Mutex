package com.example.bi.service.ingestion;

import com.example.bi.config.ImportProperties;
import com.example.bi.domain.ImportJob;
import com.example.bi.domain.ImportJobStatus;
import com.example.bi.domain.IssueSeverity;
import com.example.bi.exception.FileValidationException;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.StagingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class ImportWorker {

    private static final Logger log = LoggerFactory.getLogger(ImportWorker.class);

    private final ImportJobRepository importJobRepository;
    private final StagingRepository stagingRepository;
    private final ImportProperties importProperties;
    private final Clock clock;

    public ImportWorker(
            ImportJobRepository importJobRepository,
            StagingRepository stagingRepository,
            ImportProperties importProperties,
            Clock clock
    ) {
        this.importJobRepository = importJobRepository;
        this.stagingRepository = stagingRepository;
        this.importProperties = importProperties;
        this.clock = clock;
    }

    @Async("importJobExecutor")
    public CompletableFuture<Void> processImportJob(UUID importJobId, File tempFile, String dateFormatPattern) {
        log.info("Starting ingestion processing for importJobId={}", importJobId);
        long startTime = System.currentTimeMillis();
        long timeoutMillis = importProperties.getTimeoutSeconds() * 1000L;

        try {
            RowNormalizer normalizer = new RowNormalizer();
            RowValidator validator = new RowValidator(clock, dateFormatPattern, normalizer);

            int blankRows = 0;
            int totalParsedRows = 0;
            boolean issuesTruncated = false;
            int totalIssuesCount = 0;

            List<ParsedRow> rowBatch = new ArrayList<>(importProperties.getBatchSize());
            List<RowIssue> issueBatch = new ArrayList<>(importProperties.getBatchSize());

            try (CsvRowReader reader = new CsvRowReader(tempFile, importProperties.getMaxRows(), importProperties.getMaxColumns())) {
                boolean hasExtraColumns = !reader.getExtraColumns().isEmpty();

                while (reader.hasNext()) {
                    if (System.currentTimeMillis() - startTime > timeoutMillis) {
                        throw new FileValidationException("PROCESSING_TIMEOUT", "File processing exceeded maximum timeout limit");
                    }

                    RawRow rawRow = reader.nextRow();
                    if (rawRow.isBlank()) {
                        blankRows++;
                        continue;
                    }

                    totalParsedRows++;
                    ParsedRow parsedRow = validator.validateRow(rawRow.sourceRowNumber(), rawRow.cells());
                    rowBatch.add(parsedRow);

                    if (!parsedRow.issues().isEmpty()) {
                        for (RowIssue issue : parsedRow.issues()) {
                            totalIssuesCount++;
                            if (totalIssuesCount <= importProperties.getMaxStoredIssues()) {
                                issueBatch.add(issue);
                            } else {
                                issuesTruncated = true;
                            }
                        }
                    }

                    if (rowBatch.size() >= importProperties.getBatchSize()) {
                        flushBatch(importJobId, rowBatch, issueBatch, totalParsedRows);
                    }
                }

                if (!rowBatch.isEmpty()) {
                    flushBatch(importJobId, rowBatch, issueBatch, totalParsedRows);
                }

                // In-file duplicate detection (set-based SQL)
                stagingRepository.markDuplicates(importJobId);

                // Compute final status counts
                StagingRepository.StagingCounts counts = stagingRepository.calculateCounts(importJobId);
                int warningCount = counts.warningCount() + (hasExtraColumns ? 1 : 0);

                markJobValidated(importJobId, totalParsedRows, counts.validRows(), counts.invalidRows(),
                        counts.duplicateRows(), blankRows, warningCount, issuesTruncated);

                log.info("Completed ingestion processing for importJobId={}. Valid={}, Invalid={}, Duplicates={}, Total={}",
                        importJobId, counts.validRows(), counts.invalidRows(), counts.duplicateRows(), totalParsedRows);

            } catch (FileValidationException fve) {
                log.warn("File validation failure for importJobId={}: code={}, message={}", importJobId, fve.getCode(), fve.getMessage());
                markJobFailed(importJobId, fve.getCode(), fve.getMessage());
            } catch (Exception ex) {
                log.error("Unexpected error processing importJobId={}", importJobId, ex);
                markJobFailed(importJobId, "PROCESSING_ERROR", "Failed to parse and stage spreadsheet");
            }

        } finally {
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    tempFile.deleteOnExit();
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private void flushBatch(UUID importJobId, List<ParsedRow> rowBatch, List<RowIssue> issueBatch, int totalParsedRows) {
        stagingRepository.saveStagingRows(importJobId, rowBatch);
        if (!issueBatch.isEmpty()) {
            stagingRepository.saveIssues(importJobId, issueBatch);
            issueBatch.clear();
        }
        rowBatch.clear();
        updateProgress(importJobId, totalParsedRows);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID importJobId, int rowsProcessed) {
        importJobRepository.findById(importJobId).ifPresent(job -> {
            job.setRowsProcessed(rowsProcessed);
            importJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobValidated(
            UUID importJobId,
            int totalRows,
            int validRows,
            int invalidRows,
            int duplicateRows,
            int blankRowsSkipped,
            int warningCount,
            boolean issuesTruncated
    ) {
        importJobRepository.findById(importJobId).ifPresent(job -> {
            ImportJobStateMachine.validateTransition(job.getStatus(), ImportJobStatus.VALIDATED);
            job.setStatus(ImportJobStatus.VALIDATED);
            job.setTotalRows(totalRows);
            job.setValidRows(validRows);
            job.setInvalidRows(invalidRows);
            job.setDuplicateRows(duplicateRows);
            job.setBlankRowsSkipped(blankRowsSkipped);
            job.setWarningCount(warningCount);
            job.setRowsProcessed(totalRows);
            job.setIssuesTruncated(issuesTruncated);
            job.setValidatedAt(Instant.now());
            job.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
            importJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobFailed(UUID importJobId, String code, String message) {
        importJobRepository.findById(importJobId).ifPresent(job -> {
            if (job.getStatus() == ImportJobStatus.PARSING) {
                ImportJobStateMachine.validateTransition(job.getStatus(), ImportJobStatus.FAILED);
                job.setStatus(ImportJobStatus.FAILED);
                job.setFileErrorCode(code);
                job.setFileErrorMessage(message);
                job.setCompletedAt(Instant.now());
                importJobRepository.save(job);
            }
        });
    }
}
