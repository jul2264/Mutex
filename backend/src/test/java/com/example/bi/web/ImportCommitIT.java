package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import com.example.bi.domain.*;
import com.example.bi.dto.ConfirmImportRequest;
import com.example.bi.repository.DatasetRepository;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.SalesRecordRepository;
import com.example.bi.repository.StagingRepository;
import com.example.bi.repository.UserRepository;
import com.example.bi.service.ingestion.ParsedRow;
import com.example.bi.service.ingestion.RowIssue;
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
import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class ImportCommitIT {

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

    private User testUser;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmailNormalized("commit_user@example.com")) {
            User u = new User();
            u.setEmail("commit_user@example.com");
            u.setEmailNormalized("commit_user@example.com");
            u.setDisplayName("Commit User");
            u.setPasswordHash(passwordEncoder.encode("secretPassword123"));
            u.setRole(Role.ANALYST);
            testUser = userRepository.save(u);
        } else {
            testUser = userRepository.findByEmailNormalized("commit_user@example.com").orElseThrow();
        }
    }

    private Dataset createDataset(String name) {
        Dataset ds = new Dataset();
        ds.setOwner(testUser);
        ds.setName(name);
        ds.setCurrencyCode("INR");
        ds.setStatus(DatasetStatus.DRAFT);
        ds.setRowCount(0);
        ds.setDataVersion(0);
        return datasetRepository.save(ds);
    }

    private ImportJob createJob(Dataset dataset, ImportPolicy policy, ImportJobStatus status, int valid, int invalid, int duplicates) {
        ImportJob job = new ImportJob();
        job.setDataset(dataset);
        job.setCreatedBy(testUser);
        job.setOriginalFilename("test.csv");
        job.setFileFormat(FileFormat.CSV);
        job.setFileSizeBytes(1024L);
        job.setFileSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        job.setDateFormat("yyyy-MM-dd");
        job.setPolicy(policy);
        job.setStatus(status);
        job.setTotalRows(valid + invalid + duplicates);
        job.setValidRows(valid);
        job.setInvalidRows(invalid);
        job.setDuplicateRows(duplicates);
        job.setRowsProcessed(valid + invalid + duplicates);
        job.setExpiresAt(Instant.now().plusSeconds(86400));
        return importJobRepository.save(job);
    }

    @Test
    void confirmImport_happyPath_movesRowsToSalesRecordsAndCleansStaging() throws Exception {
        Dataset dataset = createDataset("Happy Dataset " + UUID.randomUUID());
        ImportJob job = createJob(dataset, ImportPolicy.IMPORT_VALID_ONLY, ImportJobStatus.VALIDATED, 2, 0, 1);

        // Stage rows
        List<ParsedRow> rows = List.of(
                new ParsedRow(2, RowStatus.VALID, "ORD-1", LocalDate.of(2026, 1, 15), "Product A", "Electronics", "North", 10, new BigDecimal("500.00"), new BigDecimal("300.00"), "hash1", Collections.emptyMap(), Collections.emptyList()),
                new ParsedRow(3, RowStatus.VALID, "ORD-2", LocalDate.of(2026, 2, 10), "Product B", "Electronics", "South", 5, new BigDecimal("800.00"), new BigDecimal("600.00"), "hash2", Collections.emptyMap(), Collections.emptyList()),
                new ParsedRow(4, RowStatus.DUPLICATE_CANDIDATE, "ORD-1", LocalDate.of(2026, 1, 15), "Product A", "Electronics", "North", 10, new BigDecimal("500.00"), new BigDecimal("300.00"), "hash1", Collections.emptyMap(), Collections.emptyList())
        );
        stagingRepository.saveStagingRows(job.getId(), rows);

        // Preview before confirm
        mockMvc.perform(get("/api/datasets/{id}/preview", dataset.getId())
                        .param("source", "STAGED")
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("STAGED")))
                .andExpect(jsonPath("$.counts.total", is(3)))
                .andExpect(jsonPath("$.counts.valid", is(2)))
                .andExpect(jsonPath("$.counts.duplicateCandidate", is(1)))
                .andExpect(jsonPath("$.content.length()", is(3)));

        // Confirm without duplicates
        ConfirmImportRequest req = new ConfirmImportRequest(job.getId(), false);
        mockMvc.perform(post("/api/datasets/{id}/imports/confirm", dataset.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetId", is(dataset.getId().toString())))
                .andExpect(jsonPath("$.importJobId", is(job.getId().toString())))
                .andExpect(jsonPath("$.importedRows", is(2)))
                .andExpect(jsonPath("$.dataVersion", is(1)))
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        // Verify database state
        assertEquals(2, salesRecordRepository.countCommittedRows(dataset.getId()));
        Dataset updatedDs = datasetRepository.findById(dataset.getId()).orElseThrow();
        assertEquals(DatasetStatus.ACTIVE, updatedDs.getStatus());
        assertEquals(2, updatedDs.getRowCount());
        assertEquals(LocalDate.of(2026, 1, 15), updatedDs.getMinOrderDate());
        assertEquals(LocalDate.of(2026, 2, 10), updatedDs.getMaxOrderDate());
        assertEquals(1, updatedDs.getDataVersion());

        ImportJob updatedJob = importJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(ImportJobStatus.COMPLETED, updatedJob.getStatus());
        assertEquals(2, updatedJob.getImportedRows());

        // Staging rows must be completely wiped
        assertEquals(0, stagingRepository.countStagingRows(job.getId(), null));

        // Preview committed rows
        mockMvc.perform(get("/api/datasets/{id}/preview", dataset.getId())
                        .param("source", "COMMITTED")
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("COMMITTED")))
                .andExpect(jsonPath("$.counts.total", is(2)))
                .andExpect(jsonPath("$.content.length()", is(2)))
                .andExpect(jsonPath("$.content[0].status", is("VALID")));
    }

    @Test
    void confirmImport_idempotentReplay_returnsSameResponseWithoutInsertingDuplicateRows() throws Exception {
        Dataset dataset = createDataset("Replay Dataset " + UUID.randomUUID());
        ImportJob job = createJob(dataset, ImportPolicy.IMPORT_VALID_ONLY, ImportJobStatus.VALIDATED, 1, 0, 0);

        List<ParsedRow> rows = List.of(
                new ParsedRow(2, RowStatus.VALID, "ORD-1", LocalDate.of(2026, 1, 15), "Product A", "Electronics", "North", 10, new BigDecimal("500.00"), new BigDecimal("300.00"), "hash1", Collections.emptyMap(), Collections.emptyList())
        );
        stagingRepository.saveStagingRows(job.getId(), rows);

        ConfirmImportRequest req = new ConfirmImportRequest(job.getId(), false);

        // First confirm
        mockMvc.perform(post("/api/datasets/{id}/imports/confirm", dataset.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows", is(1)))
                .andExpect(jsonPath("$.dataVersion", is(1)));

        assertEquals(1, salesRecordRepository.countCommittedRows(dataset.getId()));

        // Second confirm (replay)
        mockMvc.perform(post("/api/datasets/{id}/imports/confirm", dataset.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows", is(1)))
                .andExpect(jsonPath("$.dataVersion", is(1)))
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        // sales_records still exactly 1 row (no extra inserts)
        assertEquals(1, salesRecordRepository.countCommittedRows(dataset.getId()));
    }

    @Test
    void confirmImport_strictPolicyBlocked_returns422() throws Exception {
        Dataset dataset = createDataset("Strict Dataset " + UUID.randomUUID());
        ImportJob job = createJob(dataset, ImportPolicy.REJECT_FILE_ON_ANY_ERROR, ImportJobStatus.VALIDATED, 5, 1, 0);

        ConfirmImportRequest req = new ConfirmImportRequest(job.getId(), false);
        mockMvc.perform(post("/api/datasets/{id}/imports/confirm", dataset.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.code", is("STRICT_POLICY_BLOCKED")));

        assertEquals(0, salesRecordRepository.countCommittedRows(dataset.getId()));
        ImportJob remainingJob = importJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(ImportJobStatus.VALIDATED, remainingJob.getStatus());
    }

    @Test
    void confirmImport_nothingToImport_returns422() throws Exception {
        Dataset dataset = createDataset("Empty Dataset " + UUID.randomUUID());
        ImportJob job = createJob(dataset, ImportPolicy.IMPORT_VALID_ONLY, ImportJobStatus.VALIDATED, 0, 5, 0);

        ConfirmImportRequest req = new ConfirmImportRequest(job.getId(), false);
        mockMvc.perform(post("/api/datasets/{id}/imports/confirm", dataset.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.code", is("NOTHING_TO_IMPORT")));
    }

    @Test
    void confirmImport_concurrentCalls_insertsExactlyOnce() throws Exception {
        Dataset dataset = createDataset("Concurrent Dataset " + UUID.randomUUID());
        ImportJob job = createJob(dataset, ImportPolicy.IMPORT_VALID_ONLY, ImportJobStatus.VALIDATED, 2, 0, 0);

        List<ParsedRow> rows = List.of(
                new ParsedRow(2, RowStatus.VALID, "ORD-1", LocalDate.of(2026, 1, 15), "Product A", "Electronics", "North", 10, new BigDecimal("500.00"), new BigDecimal("300.00"), "hash1", Collections.emptyMap(), Collections.emptyList()),
                new ParsedRow(3, RowStatus.VALID, "ORD-2", LocalDate.of(2026, 2, 10), "Product B", "Electronics", "South", 5, new BigDecimal("800.00"), new BigDecimal("600.00"), "hash2", Collections.emptyMap(), Collections.emptyList())
        );
        stagingRepository.saveStagingRows(job.getId(), rows);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ConfirmImportRequest req = new ConfirmImportRequest(job.getId(), false);

        Callable<Integer> confirmTask = () -> {
            barrier.await();
            return mockMvc.perform(post("/api/datasets/{id}/imports/confirm", dataset.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                            .with(csrf()))
                    .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(confirmTask);
        Future<Integer> f2 = executor.submit(confirmTask);

        int status1 = f1.get(10, TimeUnit.SECONDS);
        int status2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(200, status1);
        assertEquals(200, status2);

        // Crucial assertion: sales_records has exactly 2 rows, not 4
        assertEquals(2, salesRecordRepository.countCommittedRows(dataset.getId()));
    }

    @Test
    void confirmImport_failureInjection_rollsBackAndLeavesJobValidated() throws Exception {
        Dataset dataset = createDataset("Failure Dataset " + UUID.randomUUID());
        ImportJob job = createJob(dataset, ImportPolicy.IMPORT_VALID_ONLY, ImportJobStatus.VALIDATED, 1, 0, 0);

        // Inject staged row that violates sales_records CHECK constraint: quantity BETWEEN 1 AND 1000000
        // We bypass validator by inserting quantity = 0 directly into staging table
        jdbcTemplate.update("""
                INSERT INTO import_staging_rows (
                    import_job_id, source_row_number, row_status, order_id, order_date,
                    product_name, category, region, quantity, unit_price, unit_cost, row_hash
                ) VALUES (?, 2, 'VALID', 'ORD-BAD', '2026-01-15', 'Bad Product', 'Cat', 'Reg', 0, 100.00, 50.00, 'badhash')
                """, job.getId());

        ConfirmImportRequest req = new ConfirmImportRequest(job.getId(), false);

        mockMvc.perform(post("/api/datasets/{id}/imports/confirm", dataset.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is("INTERNAL_ERROR")));

        // Transaction rolled back: 0 sales_records, job remains VALIDATED, staging rows still present
        assertEquals(0, salesRecordRepository.countCommittedRows(dataset.getId()));
        ImportJob remainingJob = importJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(ImportJobStatus.VALIDATED, remainingJob.getStatus());
        assertEquals(1, stagingRepository.countStagingRows(job.getId(), null));
    }

    @Test
    void preview_whenJobIsParsing_resolvesToStagedAndReturnsRows() throws Exception {
        Dataset dataset = createDataset("Parsing Dataset " + UUID.randomUUID());
        ImportJob job = createJob(dataset, ImportPolicy.IMPORT_VALID_ONLY, ImportJobStatus.PARSING, 1, 0, 0);

        List<ParsedRow> rows = List.of(
                new ParsedRow(2, RowStatus.VALID, "ORD-1", LocalDate.of(2026, 1, 15), "Product A", "Electronics", "North", 10, new BigDecimal("500.00"), new BigDecimal("300.00"), "hash1", Collections.emptyMap(), Collections.emptyList())
        );
        stagingRepository.saveStagingRows(job.getId(), rows);

        // Preview without source param must resolve to STAGED because job is in PARSING
        mockMvc.perform(get("/api/datasets/{id}/preview", dataset.getId())
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("STAGED")))
                .andExpect(jsonPath("$.counts.total", is(1)))
                .andExpect(jsonPath("$.counts.valid", is(1)))
                .andExpect(jsonPath("$.content.length()", is(1)));

        // Preview with specific jobId param
        mockMvc.perform(get("/api/datasets/{id}/preview", dataset.getId())
                        .param("jobId", job.getId().toString())
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("STAGED")))
                .andExpect(jsonPath("$.counts.total", is(1)))
                .andExpect(jsonPath("$.content.length()", is(1)));
    }
}
