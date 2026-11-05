package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import com.example.bi.domain.*;
import com.example.bi.dto.RenameDatasetRequest;
import com.example.bi.repository.*;
import com.example.bi.service.ingestion.ImportCleanupJob;
import com.example.bi.service.ingestion.ParsedRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class DatasetLifecycleIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private StagingRepository stagingRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ImportCleanupJob importCleanupJob;

    private User testUser;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmailNormalized("lifecycle_user@example.com")) {
            User u = new User();
            u.setEmail("lifecycle_user@example.com");
            u.setEmailNormalized("lifecycle_user@example.com");
            u.setDisplayName("Lifecycle User");
            u.setPasswordHash(passwordEncoder.encode("secretPassword123"));
            u.setRole(Role.ANALYST);
            testUser = userRepository.save(u);
        } else {
            testUser = userRepository.findByEmailNormalized("lifecycle_user@example.com").orElseThrow();
        }
    }

    private Dataset createDataset(String name, DatasetStatus status, int rowCount) {
        Dataset ds = new Dataset();
        ds.setOwner(testUser);
        ds.setName(name);
        ds.setCurrencyCode("INR");
        ds.setStatus(status);
        ds.setRowCount(rowCount);
        ds.setDataVersion(0);
        return datasetRepository.save(ds);
    }

    private ImportJob createJob(Dataset dataset, ImportJobStatus status) {
        ImportJob job = new ImportJob();
        job.setDataset(dataset);
        job.setCreatedBy(testUser);
        job.setOriginalFilename("file.csv");
        job.setFileFormat(FileFormat.CSV);
        job.setFileSizeBytes(1024L);
        job.setFileSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        job.setDateFormat("ISO");
        job.setPolicy(ImportPolicy.IMPORT_VALID_ONLY);
        job.setStatus(status);
        job.setExpiresAt(Instant.now().plusSeconds(86400));
        return importJobRepository.save(job);
    }

    @Test
    void listDatasets_pagedAndSorted_returnsDatasets() throws Exception {
        createDataset("Dataset Alpha " + UUID.randomUUID(), DatasetStatus.ACTIVE, 100);
        createDataset("Dataset Beta " + UUID.randomUUID(), DatasetStatus.DRAFT, 0);

        mockMvc.perform(get("/api/datasets")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "name")
                        .param("direction", "asc")
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(10)))
                .andExpect(jsonPath("$.content", not(empty())));
    }

    @Test
    void listDatasets_invalidSortField_returns400() throws Exception {
        mockMvc.perform(get("/api/datasets")
                        .param("sort", "unallowed_column")
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_SORT_PARAMETER")));
    }

    @Test
    void getDatasetDetails_returnsMetadataAndImportHistory() throws Exception {
        Dataset dataset = createDataset("Details Dataset " + UUID.randomUUID(), DatasetStatus.ACTIVE, 50);
        ImportJob job = createJob(dataset, ImportJobStatus.COMPLETED);

        mockMvc.perform(get("/api/datasets/{id}", dataset.getId())
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(dataset.getId().toString())))
                .andExpect(jsonPath("$.name", is(dataset.getName())))
                .andExpect(jsonPath("$.latestImport.id", is(job.getId().toString())))
                .andExpect(jsonPath("$.importHistory", hasSize(1)));
    }

    @Test
    void renameDataset_successAndConflict() throws Exception {
        Dataset ds1 = createDataset("Original Name " + UUID.randomUUID(), DatasetStatus.ACTIVE, 10);
        Dataset ds2 = createDataset("Taken Name " + UUID.randomUUID(), DatasetStatus.ACTIVE, 20);

        // Success rename
        RenameDatasetRequest req1 = new RenameDatasetRequest("Brand New Name " + UUID.randomUUID());
        mockMvc.perform(patch("/api/datasets/{id}", ds1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1))
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(req1.name())));

        // Conflict rename (trying to rename to ds2's name)
        RenameDatasetRequest req2 = new RenameDatasetRequest(ds2.getName());
        mockMvc.perform(patch("/api/datasets/{id}", ds1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2))
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DATASET_NAME_TAKEN")));
    }

    @Test
    void deleteDataset_withCascade_leavesZeroOrphans() throws Exception {
        Dataset dataset = createDataset("Dataset To Delete " + UUID.randomUUID(), DatasetStatus.ACTIVE, 2);
        ImportJob job = createJob(dataset, ImportJobStatus.COMPLETED);

        // Stage rows and commit one row into sales_records
        List<ParsedRow> rows = List.of(
                new ParsedRow(2, RowStatus.VALID, "ORD-1", LocalDate.of(2026, 1, 15), "Product A", "Electronics", "North", 10, new BigDecimal("500.00"), new BigDecimal("300.00"), "hash1", Collections.emptyMap(), Collections.emptyList())
        );
        stagingRepository.saveStagingRows(job.getId(), rows);
        salesRecordRepository.insertFromStaging(dataset.getId(), job.getId(), false);

        // Verify entities exist
        assertEquals(1, salesRecordRepository.countCommittedRows(dataset.getId()));

        // Mismatched confirmation name -> 400
        mockMvc.perform(delete("/api/datasets/{id}", dataset.getId())
                        .param("confirmName", "Wrong Name")
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DATASET_NAME_MISMATCH")));

        // Valid confirmation name -> 204
        mockMvc.perform(delete("/api/datasets/{id}", dataset.getId())
                        .param("confirmName", dataset.getName())
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Verify cascade deletion leaves zero orphan rows in any table
        assertFalse(datasetRepository.existsById(dataset.getId()));
        assertEquals(0L, salesRecordRepository.countCommittedRows(dataset.getId()));

        Integer remainingJobs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_jobs WHERE dataset_id = ?", Integer.class, dataset.getId());
        assertEquals(0, remainingJobs);

        Integer remainingStaging = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_staging_rows WHERE import_job_id = ?", Integer.class, job.getId());
        assertEquals(0, remainingStaging);

        Integer remainingIssues = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_row_issues WHERE import_job_id = ?", Integer.class, job.getId());
        assertEquals(0, remainingIssues);
    }

    @Test
    void cancelImport_draftDatasetWithoutCompletedImports_deletesDatasetAndStaging() throws Exception {
        Dataset dataset = createDataset("Draft To Cancel " + UUID.randomUUID(), DatasetStatus.DRAFT, 0);
        ImportJob job = createJob(dataset, ImportJobStatus.VALIDATED);

        List<ParsedRow> rows = List.of(
                new ParsedRow(2, RowStatus.VALID, "ORD-1", LocalDate.of(2026, 1, 15), "Product A", "Electronics", "North", 10, new BigDecimal("500.00"), new BigDecimal("300.00"), "hash1", Collections.emptyMap(), Collections.emptyList())
        );
        stagingRepository.saveStagingRows(job.getId(), rows);
        assertEquals(1, stagingRepository.countStagingRows(job.getId(), null));

        mockMvc.perform(post("/api/datasets/{id}/imports/{jobId}/cancel", dataset.getId(), job.getId())
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Staging data wiped
        assertEquals(0, stagingRepository.countStagingRows(job.getId(), null));
        // DRAFT dataset had no completed imports, so it was deleted
        assertFalse(datasetRepository.existsById(dataset.getId()));
    }

    @Test
    void importCleanupJob_marksExpiredJobsAndPurgesStaging() {
        Dataset dataset = createDataset("Expiring Dataset " + UUID.randomUUID(), DatasetStatus.ACTIVE, 0);
        ImportJob job = createJob(dataset, ImportJobStatus.VALIDATED);
        // Set expiresAt to the past
        job.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
        importJobRepository.save(job);

        List<ParsedRow> rows = List.of(
                new ParsedRow(2, RowStatus.VALID, "ORD-EXP", LocalDate.of(2026, 1, 15), "Product A", "Electronics", "North", 10, new BigDecimal("500.00"), new BigDecimal("300.00"), "hash1", Collections.emptyMap(), Collections.emptyList())
        );
        stagingRepository.saveStagingRows(job.getId(), rows);
        assertEquals(1, stagingRepository.countStagingRows(job.getId(), null));

        int expiredCount = importCleanupJob.cleanupExpiredImports();
        assertTrue(expiredCount >= 1);

        ImportJob refreshed = importJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(ImportJobStatus.EXPIRED, refreshed.getStatus());
        assertEquals(0, stagingRepository.countStagingRows(job.getId(), null));
    }
}
