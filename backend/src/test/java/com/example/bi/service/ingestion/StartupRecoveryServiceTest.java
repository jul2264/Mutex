package com.example.bi.service.ingestion;

import com.example.bi.domain.*;
import com.example.bi.repository.DatasetRepository;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.StagingRepository;
import com.example.bi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.example.bi.TestcontainersConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@Transactional
class StartupRecoveryServiceTest {

    @Autowired
    private StartupRecoveryService recoveryService;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StagingRepository stagingRepository;

    @Test
    void recoverInterruptedJobs_marksStuckParsingJobsAsFailed() {
        User user = userRepository.findAll().get(0);

        Dataset dataset = new Dataset();
        dataset.setOwner(user);
        dataset.setName("Stuck Recovery DS " + UUID.randomUUID());
        dataset.setStatus(DatasetStatus.DRAFT);
        dataset = datasetRepository.save(dataset);

        ImportJob stuckJob = new ImportJob();
        stuckJob.setDataset(dataset);
        stuckJob.setCreatedBy(user);
        stuckJob.setOriginalFilename("stuck.csv");
        stuckJob.setFileFormat(FileFormat.CSV);
        stuckJob.setFileSizeBytes(100);
        stuckJob.setFileSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        stuckJob.setStatus(ImportJobStatus.PARSING);
        stuckJob.setCreatedAt(Instant.now());
        stuckJob = importJobRepository.save(stuckJob);

        // Run recovery
        recoveryService.recoverInterruptedJobs();

        ImportJob recovered = importJobRepository.findById(stuckJob.getId()).orElseThrow();
        assertEquals(ImportJobStatus.FAILED, recovered.getStatus());
        assertEquals("INTERRUPTED", recovered.getFileErrorCode());
        assertNotNull(recovered.getCompletedAt());
    }
}
