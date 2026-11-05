package com.example.bi.service.ingestion;

import com.example.bi.domain.Dataset;
import com.example.bi.domain.DatasetStatus;
import com.example.bi.domain.ImportJob;
import com.example.bi.domain.ImportJobStatus;
import com.example.bi.exception.DatasetNotFoundException;
import com.example.bi.exception.ImportNotCancelableException;
import com.example.bi.repository.DatasetRepository;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.StagingRepository;
import com.example.bi.service.identity.CurrentUserProvider;
import com.example.bi.service.identity.DatasetAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ImportCancelService {

    private static final Logger log = LoggerFactory.getLogger(ImportCancelService.class);

    private final DatasetAccessService datasetAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final DatasetRepository datasetRepository;
    private final ImportJobRepository importJobRepository;
    private final StagingRepository stagingRepository;

    public ImportCancelService(
            DatasetAccessService datasetAccessService,
            CurrentUserProvider currentUserProvider,
            DatasetRepository datasetRepository,
            ImportJobRepository importJobRepository,
            StagingRepository stagingRepository
    ) {
        this.datasetAccessService = datasetAccessService;
        this.currentUserProvider = currentUserProvider;
        this.datasetRepository = datasetRepository;
        this.importJobRepository = importJobRepository;
        this.stagingRepository = stagingRepository;
    }

    @Transactional
    public void cancelImport(UUID datasetId, UUID jobId) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        ImportJob job = importJobRepository.findByIdAndDatasetIdAndOwnerIdForUpdate(jobId, datasetId, userId)
                .orElseThrow(() -> new DatasetNotFoundException("Import job not found: " + jobId));

        if (job.getStatus() != ImportJobStatus.VALIDATED) {
            throw new ImportNotCancelableException(
                    "Import job is in status " + job.getStatus() + " and cannot be cancelled"
            );
        }

        stagingRepository.deleteStagingData(job.getId());
        log.info("Import job {} cancelled and staging data deleted", jobId);

        // If dataset is DRAFT and has no completed imports, remove dataset
        if (dataset.getStatus() == DatasetStatus.DRAFT) {
            long completedCount = importJobRepository.countByDatasetIdAndStatus(datasetId, ImportJobStatus.COMPLETED);
            if (completedCount == 0) {
                importJobRepository.delete(job);
                datasetRepository.delete(dataset);
                log.info("DRAFT dataset {} had no completed imports; deleted upon cancelling import {}", datasetId, jobId);
                return;
            }
        }

        ImportJobStateMachine.validateTransition(job.getStatus(), ImportJobStatus.CANCELLED);
        job.setStatus(ImportJobStatus.CANCELLED);
        importJobRepository.save(job);
    }
}

