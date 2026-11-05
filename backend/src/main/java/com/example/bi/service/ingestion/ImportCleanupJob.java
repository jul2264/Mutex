package com.example.bi.service.ingestion;

import com.example.bi.domain.Dataset;
import com.example.bi.domain.DatasetStatus;
import com.example.bi.domain.ImportJob;
import com.example.bi.domain.ImportJobStatus;
import com.example.bi.repository.DatasetRepository;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.StagingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ImportCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ImportCleanupJob.class);

    private final ImportJobRepository importJobRepository;
    private final StagingRepository stagingRepository;
    private final DatasetRepository datasetRepository;
    private final Clock clock;

    public ImportCleanupJob(
            ImportJobRepository importJobRepository,
            StagingRepository stagingRepository,
            DatasetRepository datasetRepository,
            Clock clock
    ) {
        this.importJobRepository = importJobRepository;
        this.stagingRepository = stagingRepository;
        this.datasetRepository = datasetRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.import.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void runCleanup() {
        cleanupExpiredImports();
        cleanupAbandonedDraftDatasets();
    }

    @Transactional
    public int cleanupExpiredImports() {
        Instant now = Instant.now(clock);
        List<ImportJob> expiredJobs = importJobRepository.findByStatusAndExpiresAtBefore(ImportJobStatus.VALIDATED, now);

        for (ImportJob job : expiredJobs) {
            log.info("Marking import job {} as EXPIRED (expired at {})", job.getId(), job.getExpiresAt());
            ImportJobStateMachine.validateTransition(job.getStatus(), ImportJobStatus.EXPIRED);
            job.setStatus(ImportJobStatus.EXPIRED);
            importJobRepository.save(job);
            stagingRepository.deleteStagingData(job.getId());
        }

        if (!expiredJobs.isEmpty()) {
            log.info("Cleaned up {} expired import jobs", expiredJobs.size());
        }
        return expiredJobs.size();
    }

    @Transactional
    public int cleanupAbandonedDraftDatasets() {
        Instant cutoff = Instant.now(clock).minus(7, ChronoUnit.DAYS);
        List<Dataset> oldDrafts = datasetRepository.findByStatusAndCreatedAtBefore(DatasetStatus.DRAFT, cutoff);

        int deletedCount = 0;
        for (Dataset dataset : oldDrafts) {
            List<ImportJob> activeJobs = importJobRepository.findByDatasetIdAndStatusIn(
                    dataset.getId(),
                    List.of(ImportJobStatus.PARSING, ImportJobStatus.VALIDATED)
            );
            if (activeJobs.isEmpty()) {
                log.info("Deleting abandoned DRAFT dataset {} created at {}", dataset.getId(), dataset.getCreatedAt());
                datasetRepository.delete(dataset);
                deletedCount++;
            }
        }

        if (deletedCount > 0) {
            log.info("Deleted {} abandoned DRAFT datasets", deletedCount);
        }
        return deletedCount;
    }
}

