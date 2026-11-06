package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import com.example.bi.domain.*;
import com.example.bi.dto.BulkFixRequest;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class BulkFixIT {

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
        if (!userRepository.existsByEmailNormalized("bulkfix_user@example.com")) {
            User u = new User();
            u.setEmail("bulkfix_user@example.com");
            u.setEmailNormalized("bulkfix_user@example.com");
            u.setDisplayName("Bulk Fix User");
            u.setPasswordHash(passwordEncoder.encode("secretPassword123"));
            u.setRole(Role.ANALYST);
            testUser = userRepository.save(u);
        } else {
            testUser = userRepository.findByEmailNormalized("bulkfix_user@example.com").orElseThrow();
        }

        if (!userRepository.existsByEmailNormalized("other_bulk_user@example.com")) {
            User u = new User();
            u.setEmail("other_bulk_user@example.com");
            u.setEmailNormalized("other_bulk_user@example.com");
            u.setDisplayName("Other User");
            u.setPasswordHash(passwordEncoder.encode("secretPassword123"));
            u.setRole(Role.ANALYST);
            otherUser = userRepository.save(u);
        } else {
            otherUser = userRepository.findByEmailNormalized("other_bulk_user@example.com").orElseThrow();
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
    void bulkFix_fixesInvalidRowsAndPreservesLargeQuantityRow() throws Exception {
        Dataset dataset = createDataset("BulkFix Dataset " + UUID.randomUUID(), testUser);
        ImportJob job = createJob(dataset, testUser, ImportJobStatus.VALIDATED, 1, 6, 0);

        // Row 2: VALID (Date 2026-02-10)
        ParsedRow row2 = new ParsedRow(2, RowStatus.VALID, "ORD-2001", LocalDate.of(2026, 2, 10),
                "Wireless Mouse", "Electronics", "North", 2, new BigDecimal("450.00"),
                new BigDecimal("300.00"), "hash2", Collections.emptyMap(), Collections.emptyList());

        // Row 3: INVALID (missing date)
        ParsedRow row3 = new ParsedRow(3, RowStatus.INVALID, "ORD-2002", null,
                "Mechanical Keyboard", "Electronics", "South", 1, new BigDecimal("2500.00"),
                new BigDecimal("1800.00"), null, Map.of("order_id", "ORD-2002", "order_date", "not-a-date", "product_name", "Mechanical Keyboard", "category", "Electronics", "region", "South", "quantity", "1", "unit_price", "2500.00", "unit_cost", "1800.00"),
                List.of(new RowIssue(3, "order_date", IssueSeverity.ERROR, "INVALID_DATE", "invalid date", "not-a-date")));

        // Row 4: INVALID (zero quantity)
        ParsedRow row4 = new ParsedRow(4, RowStatus.INVALID, "ORD-2003", LocalDate.of(2026, 1, 12),
                "USB-C Hub", "Electronics", "East", 0, new BigDecimal("800.00"),
                new BigDecimal("500.00"), null, Map.of("order_id", "ORD-2003", "order_date", "2026-01-12", "product_name", "USB-C Hub", "category", "Electronics", "region", "East", "quantity", "0", "unit_price", "800.00", "unit_cost", "500.00"),
                List.of(new RowIssue(4, "quantity", IssueSeverity.ERROR, "QUANTITY_NOT_POSITIVE", "quantity <= 0", "0")));

        // Row 5: INVALID (decimal quantity 2.5)
        ParsedRow row5 = new ParsedRow(5, RowStatus.INVALID, "ORD-2004", LocalDate.of(2026, 1, 14),
                "Monitor Arm", "Accessories", "West", null, new BigDecimal("1500.00"),
                new BigDecimal("1000.00"), null, Map.of("order_id", "ORD-2004", "order_date", "2026-01-14", "product_name", "Monitor Arm", "category", "Accessories", "region", "West", "quantity", "2.5", "unit_price", "1500.00", "unit_cost", "1000.00"),
                List.of(new RowIssue(5, "quantity", IssueSeverity.ERROR, "INVALID_INTEGER", "decimal qty", "2.5")));

        // Row 6: INVALID (large quantity 1000001 - MUST BE KEPT UNTOUCHED)
        ParsedRow row6 = new ParsedRow(6, RowStatus.INVALID, "ORD-2005", LocalDate.of(2026, 1, 15),
                "Bulk Cable Clip", "Accessories", "North", 1000001, new BigDecimal("10.00"),
                new BigDecimal("5.00"), null, Map.of("order_id", "ORD-2005", "order_date", "2026-01-15", "product_name", "Bulk Cable Clip", "category", "Accessories", "region", "North", "quantity", "1000001", "unit_price", "10.00", "unit_cost", "5.00"),
                List.of(new RowIssue(6, "quantity", IssueSeverity.ERROR, "QUANTITY_TOO_LARGE", "qty > 1000000", "1000001")));

        // Row 7: INVALID (missing product name)
        ParsedRow row7 = new ParsedRow(7, RowStatus.INVALID, "ORD-2006", LocalDate.of(2026, 1, 16),
                null, "Office", "South", 5, new BigDecimal("120.00"),
                new BigDecimal("80.00"), null, Map.of("order_id", "ORD-2006", "order_date", "2026-01-16", "product_name", "", "category", "Office", "region", "South", "quantity", "5", "unit_price", "120.00", "unit_cost", "80.00"),
                List.of(new RowIssue(7, "product_name", IssueSeverity.ERROR, "REQUIRED_VALUE_MISSING", "missing product", "")));

        // Row 8: INVALID (negative price -500.00)
        ParsedRow row8 = new ParsedRow(8, RowStatus.INVALID, "ORD-2007", LocalDate.of(2026, 1, 17),
                "Desk Pad", "Office", "East", 1, null,
                new BigDecimal("300.00"), null, Map.of("order_id", "ORD-2007", "order_date", "2026-01-17", "product_name", "Desk Pad", "category", "Office", "region", "East", "quantity", "1", "unit_price", "-500.00", "unit_cost", "300.00"),
                List.of(new RowIssue(8, "unit_price", IssueSeverity.ERROR, "NEGATIVE_VALUE", "price negative", "-500.00")));

        stagingRepository.saveStagingRows(job.getId(), List.of(row2, row3, row4, row5, row6, row7, row8));

        BulkFixRequest request = new BulkFixRequest(
                "ABS_VALUE",
                "ROUND_HALF_UP",
                "SET_ONE",
                "LATEST_VALID",
                null,
                true,
                "Unspecified Product",
                true,
                "Uncategorized",
                true,
                "Unassigned"
        );

        mockMvc.perform(post("/api/datasets/{datasetId}/imports/{jobId}/bulk-fix", dataset.getId(), job.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())
                .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed", is(6)))
                .andExpect(jsonPath("$.repairedToValid", is(5)))
                .andExpect(jsonPath("$.remainingInvalid", is(1)))
                .andExpect(jsonPath("$.untouchedLargeQuantityCount", is(1)))
                .andExpect(jsonPath("$.counts.valid", is(6))) // 1 initial + 5 repaired
                .andExpect(jsonPath("$.counts.invalid", is(1))); // row 6 untouched

        // Verify row 3 was fixed to latest valid date (2026-02-10)
        StagingRepository.StagingRowData repairedRow3 = stagingRepository.findStagingRow(job.getId(), 3).orElseThrow();
        assertEquals(RowStatus.VALID, repairedRow3.rowStatus());
        assertEquals(LocalDate.of(2026, 2, 10), repairedRow3.orderDate());

        // Verify row 4 quantity is now 1
        StagingRepository.StagingRowData repairedRow4 = stagingRepository.findStagingRow(job.getId(), 4).orElseThrow();
        assertEquals(RowStatus.VALID, repairedRow4.rowStatus());
        assertEquals(1, repairedRow4.quantity());

        // Verify row 5 quantity rounded half-up: 2.5 -> 3
        StagingRepository.StagingRowData repairedRow5 = stagingRepository.findStagingRow(job.getId(), 5).orElseThrow();
        assertEquals(RowStatus.VALID, repairedRow5.rowStatus());
        assertEquals(3, repairedRow5.quantity());

        // Verify row 6 is still untouched and INVALID
        StagingRepository.StagingRowData untouchedRow6 = stagingRepository.findStagingRow(job.getId(), 6).orElseThrow();
        assertEquals(RowStatus.INVALID, untouchedRow6.rowStatus());
        assertEquals(1000001, untouchedRow6.quantity());

        // Verify row 7 has product placeholder
        StagingRepository.StagingRowData repairedRow7 = stagingRepository.findStagingRow(job.getId(), 7).orElseThrow();
        assertEquals(RowStatus.VALID, repairedRow7.rowStatus());
        assertEquals("Unspecified Product", repairedRow7.productName());

        // Verify row 8 has positive price 500.00
        StagingRepository.StagingRowData repairedRow8 = stagingRepository.findStagingRow(job.getId(), 8).orElseThrow();
        assertEquals(RowStatus.VALID, repairedRow8.rowStatus());
        assertEquals(new BigDecimal("500.00"), repairedRow8.unitPrice());
    }

    @Test
    void bulkFix_whenJobCompleted_returns409Conflict() throws Exception {
        Dataset dataset = createDataset("BulkFix Completed " + UUID.randomUUID(), testUser);
        ImportJob job = createJob(dataset, testUser, ImportJobStatus.COMPLETED, 1, 0, 0);

        BulkFixRequest request = new BulkFixRequest(
                "ABS_VALUE", "ROUND_HALF_UP", "SET_ONE", "LATEST_VALID",
                null, true, "Product", true, "Category", true, "Region"
        );

        mockMvc.perform(post("/api/datasets/{datasetId}/imports/{jobId}/bulk-fix", dataset.getId(), job.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())
                .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("IMPORT_NOT_EDITABLE")));
    }

    @Test
    void bulkFix_whenForeignDataset_returns404NotFound() throws Exception {
        Dataset dataset = createDataset("Foreign Dataset " + UUID.randomUUID(), testUser);
        ImportJob job = createJob(dataset, testUser, ImportJobStatus.VALIDATED, 1, 1, 0);

        BulkFixRequest request = new BulkFixRequest(
                "ABS_VALUE", "ROUND_HALF_UP", "SET_ONE", "LATEST_VALID",
                null, true, "Product", true, "Category", true, "Region"
        );

        mockMvc.perform(post("/api/datasets/{datasetId}/imports/{jobId}/bulk-fix", dataset.getId(), job.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())
                .with(user(otherUser.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("DATASET_NOT_FOUND")));
    }
}

