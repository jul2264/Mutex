package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import com.example.bi.domain.*;
import com.example.bi.dto.EditRowRequest;
import com.example.bi.repository.DatasetRepository;
import com.example.bi.repository.ImportJobRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class RowEditIT {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmailNormalized("edit_user@example.com")) {
            User u = new User();
            u.setEmail("edit_user@example.com");
            u.setEmailNormalized("edit_user@example.com");
            u.setDisplayName("Edit User");
            u.setPasswordHash(passwordEncoder.encode("secretPassword123"));
            u.setRole(Role.ANALYST);
            testUser = userRepository.save(u);
        } else {
            testUser = userRepository.findByEmailNormalized("edit_user@example.com").orElseThrow();
        }

        if (!userRepository.existsByEmailNormalized("other_user@example.com")) {
            User u = new User();
            u.setEmail("other_user@example.com");
            u.setEmailNormalized("other_user@example.com");
            u.setDisplayName("Other User");
            u.setPasswordHash(passwordEncoder.encode("secretPassword123"));
            u.setRole(Role.ANALYST);
            otherUser = userRepository.save(u);
        } else {
            otherUser = userRepository.findByEmailNormalized("other_user@example.com").orElseThrow();
        }
    }

    private Dataset createDataset(String name, User owner) {
        Dataset ds = new Dataset();
        ds.setOwner(owner);
        ds.setName(name);
        ds.setCurrencyCode("INR");
        ds.setStatus(DatasetStatus.DRAFT);
        ds.setRowCount(0);
        ds.setDataVersion(0);
        return datasetRepository.save(ds);
    }

    private ImportJob createJob(Dataset dataset, User creator, ImportJobStatus status, int valid, int invalid, int duplicates) {
        ImportJob job = new ImportJob();
        job.setDataset(dataset);
        job.setCreatedBy(creator);
        job.setOriginalFilename("test.csv");
        job.setFileFormat(FileFormat.CSV);
        job.setFileSizeBytes(1024L);
        job.setFileSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        job.setDateFormat("yyyy-MM-dd");
        job.setPolicy(ImportPolicy.IMPORT_VALID_ONLY);
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
    void editRow_fixInvalidRow_becomesValidAndUpdatesCounts() throws Exception {
        Dataset dataset = createDataset("Edit Dataset " + UUID.randomUUID(), testUser);
        ImportJob job = createJob(dataset, testUser, ImportJobStatus.VALIDATED, 1, 1, 0);

        // Stage row 2 (VALID) and row 3 (INVALID - missing date)
        ParsedRow row2 = new ParsedRow(2, RowStatus.VALID, "ORD-1", LocalDate.of(2026, 1, 15),
                "Product A", "Electronics", "North", 10, new BigDecimal("500.00"),
                new BigDecimal("300.00"), "hash1", Collections.emptyMap(), Collections.emptyList());

        Map<String, String> rawValues = new HashMap<>();
        rawValues.put("order_id", "ORD-2");
        rawValues.put("order_date", "");
        rawValues.put("product_name", "Product B");
        rawValues.put("category", "Electronics");
        rawValues.put("region", "South");
        rawValues.put("quantity", "5");
        rawValues.put("unit_price", "800.00");
        rawValues.put("unit_cost", "600.00");

        RowIssue issue = new RowIssue(3, "order_date", IssueSeverity.ERROR, "REQUIRED_VALUE_MISSING", "order_date is required", "");

        ParsedRow row3 = new ParsedRow(3, RowStatus.INVALID, "ORD-2", null,
                "Product B", "Electronics", "South", 5, new BigDecimal("800.00"),
                new BigDecimal("600.00"), null, rawValues, List.of(issue));

        stagingRepository.saveStagingRows(job.getId(), List.of(row2, row3));
        stagingRepository.saveIssues(job.getId(), List.of(issue));

        // Now edit row 3 with a valid orderDate
        EditRowRequest editRequest = new EditRowRequest(
                "ORD-2", "2026-02-10", "Product B", "Electronics", "South", "5", "800.00", "600.00"
        );

        mockMvc.perform(patch("/api/datasets/{datasetId}/imports/{jobId}/rows/{rowNumber}",
                dataset.getId(), job.getId(), 3)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(editRequest))
                .with(csrf())
                .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.rowNumber", is(3)))
                .andExpect(jsonPath("$.row.status", is("VALID")))
                .andExpect(jsonPath("$.row.values.orderDate", is("2026-02-10")))
                .andExpect(jsonPath("$.row.issues.length()", is(0)))
                .andExpect(jsonPath("$.counts.valid", is(2)))
                .andExpect(jsonPath("$.counts.invalid", is(0)));
    }

    @Test
    void editRow_stillInvalid_returnsNewIssuesAndStaysInvalid() throws Exception {
        Dataset dataset = createDataset("Edit Dataset Invalid " + UUID.randomUUID(), testUser);
        ImportJob job = createJob(dataset, testUser, ImportJobStatus.VALIDATED, 1, 1, 0);

        ParsedRow row2 = new ParsedRow(2, RowStatus.VALID, "ORD-1", LocalDate.of(2026, 1, 15),
                "Product A", "Electronics", "North", 10, new BigDecimal("500.00"),
                new BigDecimal("300.00"), "hash1", Collections.emptyMap(), Collections.emptyList());

        ParsedRow row3 = new ParsedRow(3, RowStatus.INVALID, "ORD-2", null,
                "Product B", "Electronics", "South", 5, new BigDecimal("800.00"),
                new BigDecimal("600.00"), null, Map.of("order_date", ""), Collections.emptyList());

        stagingRepository.saveStagingRows(job.getId(), List.of(row2, row3));

        // Edit row 3 but with invalid quantity = 0
        EditRowRequest editRequest = new EditRowRequest(
                "ORD-2", "2026-02-10", "Product B", "Electronics", "South", "0", "800.00", "600.00"
        );

        mockMvc.perform(patch("/api/datasets/{datasetId}/imports/{jobId}/rows/{rowNumber}",
                dataset.getId(), job.getId(), 3)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(editRequest))
                .with(csrf())
                .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.rowNumber", is(3)))
                .andExpect(jsonPath("$.row.status", is("INVALID")))
                .andExpect(jsonPath("$.row.issues[0].code", is("QUANTITY_NOT_POSITIVE")))
                .andExpect(jsonPath("$.counts.valid", is(1)))
                .andExpect(jsonPath("$.counts.invalid", is(1)));
    }

    @Test
    void editRow_whenJobNotValidated_returnsConflict() throws Exception {
        Dataset dataset = createDataset("Edit Dataset Conflict " + UUID.randomUUID(), testUser);
        ImportJob job = createJob(dataset, testUser, ImportJobStatus.COMPLETED, 1, 0, 0);

        EditRowRequest editRequest = new EditRowRequest(
                "ORD-1", "2026-01-15", "Product A", "Electronics", "North", "10", "500.00", "300.00"
        );

        mockMvc.perform(patch("/api/datasets/{datasetId}/imports/{jobId}/rows/{rowNumber}",
                dataset.getId(), job.getId(), 2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(editRequest))
                .with(csrf())
                .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("IMPORT_NOT_EDITABLE")));
    }

    @Test
    void editRow_whenForeignDataset_returns404() throws Exception {
        Dataset dataset = createDataset("Foreign Dataset " + UUID.randomUUID(), testUser);
        ImportJob job = createJob(dataset, testUser, ImportJobStatus.VALIDATED, 1, 0, 0);

        EditRowRequest editRequest = new EditRowRequest(
                "ORD-1", "2026-01-15", "Product A", "Electronics", "North", "10", "500.00", "300.00"
        );

        // otherUser attempts to edit testUser's dataset
        mockMvc.perform(patch("/api/datasets/{datasetId}/imports/{jobId}/rows/{rowNumber}",
                dataset.getId(), job.getId(), 2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(editRequest))
                .with(csrf())
                .with(user(otherUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("DATASET_NOT_FOUND")));
    }
}

