package com.example.bi.service.ingestion;

import com.example.bi.domain.ImportJob;
import com.example.bi.domain.ImportJobStatus;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.StagingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class StartupRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StartupRecoveryService.class);

    private final ImportJobRepository importJobRepository;
    private final StagingRepository stagingRepository;

    public StartupRecoveryService(ImportJobRepository importJobRepository, StagingRepository stagingRepository) {
        this.importJobRepository = importJobRepository;
        this.stagingRepository = stagingRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedJobs() {
        List<ImportJob> stuckJobs = importJobRepository.findByStatus(ImportJobStatus.PARSING);
        if (!stuckJobs.isEmpty()) {
            log.warn("Found {} interrupted import job(s) in PARSING state. Marking as FAILED and cleaning staging.", stuckJobs.size());
            for (ImportJob job : stuckJobs) {
                job.setStatus(ImportJobStatus.FAILED);
                job.setFileErrorCode("INTERRUPTED");
                job.setFileErrorMessage("Import process was interrupted by server shutdown");
                job.setCompletedAt(Instant.now());
                importJobRepository.save(job);

                stagingRepository.deleteStagingData(job.getId());
            }
        }
    }
}

