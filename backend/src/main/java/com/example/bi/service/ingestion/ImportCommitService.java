package com.example.bi.service.ingestion;

import com.example.bi.domain.Dataset;
import com.example.bi.domain.DatasetStatus;
import com.example.bi.domain.ImportJob;
import com.example.bi.domain.ImportJobStatus;
import com.example.bi.domain.ImportPolicy;
import com.example.bi.dto.ConfirmImportRequest;
import com.example.bi.dto.ConfirmImportResponse;
import com.example.bi.exception.DatasetNotFoundException;
import com.example.bi.exception.ImportNotConfirmableException;
import com.example.bi.exception.NothingToImportException;
import com.example.bi.exception.StrictPolicyBlockedException;
import com.example.bi.repository.DatasetRepository;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.SalesRecordRepository;
import com.example.bi.repository.StagingRepository;
import com.example.bi.service.identity.CurrentUserProvider;
import com.example.bi.service.identity.DatasetAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ImportCommitService {

    private static final Logger log = LoggerFactory.getLogger(ImportCommitService.class);

    private final DatasetAccessService datasetAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final DatasetRepository datasetRepository;
    private final ImportJobRepository importJobRepository;
    private final StagingRepository stagingRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final Clock clock;

    public ImportCommitService(
            DatasetAccessService datasetAccessService,
            CurrentUserProvider currentUserProvider,
            DatasetRepository datasetRepository,
            ImportJobRepository importJobRepository,
            StagingRepository stagingRepository,
            SalesRecordRepository salesRecordRepository,
            Clock clock
    ) {
        this.datasetAccessService = datasetAccessService;
        this.currentUserProvider = currentUserProvider;
        this.datasetRepository = datasetRepository;
        this.importJobRepository = importJobRepository;
        this.stagingRepository = stagingRepository;
        this.salesRecordRepository = salesRecordRepository;
        this.clock = clock;
    }

    @Transactional
    public ConfirmImportResponse confirmImport(UUID datasetId, ConfirmImportRequest request) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();
        UUID jobId = request.importJobId();

        // 1. Lock job row with SELECT ... FOR UPDATE
        ImportJob job = importJobRepository.findByIdAndDatasetIdAndOwnerIdForUpdate(jobId, datasetId, userId)
                .orElseThrow(() -> new DatasetNotFoundException("Import job not found: " + jobId));

        // 2. Idempotent replay: if already COMPLETED, return stored result
        if (job.getStatus() == ImportJobStatus.COMPLETED) {
            log.info("Import job {} already COMPLETED, returning replay result", jobId);
            return new ConfirmImportResponse(
                    dataset.getId(),
                    job.getId(),
                    job.getImportedRows(),
                    dataset.getDataVersion(),
                    job.getStatus().name()
            );
        }

        // 3. Must be in VALIDATED status
        if (job.getStatus() != ImportJobStatus.VALIDATED) {
            throw new ImportNotConfirmableException(
                    "Import job is in status " + job.getStatus() + " and cannot be confirmed"
            );
        }

        // 4. Strict policy check
        if (job.getPolicy() != ImportPolicy.IMPORT_VALID_ONLY && job.getInvalidRows() > 0) {
            throw new StrictPolicyBlockedException(
                    "Strict policy blocked confirmation because " + job.getInvalidRows() + " invalid rows exist"
            );
        }

        // 5. Nothing to import check
        int candidatesToImport = job.getValidRows() + (request.includeDuplicates() ? job.getDuplicateRows() : 0);
        if (candidatesToImport <= 0) {
            throw new NothingToImportException("No rows available to import");
        }

        // 6. Copy rows from staging to sales_records
        int inserted = salesRecordRepository.insertFromStaging(dataset.getId(), job.getId(), request.includeDuplicates());
        if (inserted <= 0) {
            throw new NothingToImportException("No rows were imported from staging");
        }

        // 7. Update dataset: row_count, min/max dates, data_version + 1, ACTIVE, updated_at
        SalesRecordRepository.DatasetMetrics metrics = salesRecordRepository.calculateDatasetMetrics(dataset.getId());
        dataset.setRowCount(metrics.rowCount());
        dataset.setMinOrderDate(metrics.minDate());
        dataset.setMaxOrderDate(metrics.maxDate());
        dataset.setDataVersion(dataset.getDataVersion() + 1);
        dataset.setStatus(DatasetStatus.ACTIVE);
        dataset.setUpdatedAt(Instant.now(clock));
        datasetRepository.save(dataset);

        // 8. Update import_job: status = COMPLETED, imported_rows, completed_at
        ImportJobStateMachine.validateTransition(job.getStatus(), ImportJobStatus.COMPLETED);
        job.setStatus(ImportJobStatus.COMPLETED);
        job.setImportedRows(inserted);
        job.setCompletedAt(Instant.now(clock));
        importJobRepository.save(job);

        // 9. Delete staging rows and issues
        stagingRepository.deleteStagingData(job.getId());

        log.info("Import job {} confirmed successfully for dataset {}. Imported rows: {}, dataVersion: {}",
                job.getId(), dataset.getId(), inserted, dataset.getDataVersion());

        return new ConfirmImportResponse(
                dataset.getId(),
                job.getId(),
                inserted,
                dataset.getDataVersion(),
                job.getStatus().name()
        );
    }
}

