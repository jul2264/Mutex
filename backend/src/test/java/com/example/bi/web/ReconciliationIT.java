package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import com.example.bi.dto.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class ReconciliationIT {

    private static final UUID RECON_DATASET_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedDatasetIfMissing() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM datasets WHERE id = ?", Integer.class, RECON_DATASET_ID
        );
        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO datasets (id, owner_id, name, currency_code, status, row_count, min_order_date, max_order_date, data_version)
                VALUES (?, ?, 'Reconciliation Dataset', 'INR', 'ACTIVE', 20, '2026-01-05', '2026-03-30', 1)
                """, RECON_DATASET_ID, DEV_USER_ID);

        UUID jobId = UUID.fromString("00000000-0000-0000-0000-0000000000f3");
        jdbcTemplate.update("""
                INSERT INTO import_jobs (
                    id, dataset_id, created_by, original_filename, file_format, file_size_bytes, file_sha256,
                    date_format, policy, status, total_rows, valid_rows, invalid_rows, duplicate_rows,
                    rows_processed, imported_rows
                ) VALUES (?, ?, ?, 'recon.csv', 'CSV', 1024, 'dummy_sha256', 'ISO', 'IMPORT_VALID_ONLY', 'COMPLETED', 20, 20, 0, 0, 20, 20)
                """, jobId, RECON_DATASET_ID, DEV_USER_ID);

        Object[][] rows = {
                {"O-101", "2026-01-05", "Laptop", "Electronics", "North", 2, "1000.00", "700.00"},
                {"O-102", "2026-01-10", "Desk", "Furniture", "North", 1, "500.00", "300.00"},
                {"O-103", "2026-01-15", "Chair", "Furniture", "South", 4, "150.00", "100.00"},
                {"O-104", "2026-01-20", "Notebook", "Stationery", "East", 10, "20.00", "10.00"},
                {"O-105", "2026-01-25", "Monitor", "Electronics", "South", 2, "300.00", "250.00"},
                {"O-106", "2026-01-28", "Desk", "Furniture", "East", 2, "500.00", "600.00"},
                {"O-201", "2026-02-02", "Laptop", "Electronics", "South", 3, "1000.00", "700.00"},
                {"O-202", "2026-02-08", "Monitor", "Electronics", "North", 1, "300.00", "200.00"},
                {"O-203", "2026-02-14", "Chair", "Furniture", "East", 2, "150.00", "80.00"},
                {"O-204", "2026-02-18", "Notebook", "Stationery", "North", 20, "20.00", "12.00"},
                {"O-205", "2026-02-22", "Desk", "Furniture", "South", 1, "500.00", "350.00"},
                {"O-206", "2026-02-25", "Laptop", "Electronics", "East", 1, "1000.00", "750.00"},
                {"O-207", "2026-02-28", "Monitor", "Electronics", "East", 2, "300.00", "220.00"},
                {"O-301", "2026-03-03", "Laptop", "Electronics", "North", 2, "1000.00", "700.00"},
                {"O-302", "2026-03-09", "Desk", "Furniture", "North", 2, "500.00", "300.00"},
                {"O-303", "2026-03-15", "Chair", "Furniture", "South", 6, "150.00", "100.00"},
                {"O-304", "2026-03-20", "Notebook", "Stationery", "East", 15, "20.00", "10.00"},
                {"O-305", "2026-03-22", "Notebook", "Stationery", "South", 25, "20.00", "15.00"},
                {"O-306", "2026-03-28", "Monitor", "Electronics", "North", 3, "300.00", "210.00"},
                {"O-307", "2026-03-30", "Chair", "Furniture", "North", 2, "150.00", "110.00"}
        };

        for (int i = 0; i < rows.length; i++) {
            Object[] r = rows[i];
            String orderId = (String) r[0];
            LocalDate orderDate = LocalDate.parse((String) r[1]);
            String product = (String) r[2];
            String category = (String) r[3];
            String region = (String) r[4];
            int qty = (int) r[5];
            BigDecimal price = new BigDecimal((String) r[6]);
            BigDecimal cost = new BigDecimal((String) r[7]);
            String hash = computeRowHash(orderId, orderDate, product, category, region, qty, price, cost);

            jdbcTemplate.update("""
                    INSERT INTO sales_records (
                        dataset_id, import_job_id, source_row_number, order_id, order_date, product_name, category, region, quantity, unit_price, unit_cost, row_hash
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, RECON_DATASET_ID, jobId, i + 1, orderId, orderDate, product, category, region, qty, price, cost, hash);
        }
    }

    private String computeRowHash(String orderId, LocalDate orderDate, String product, String category, String region, int qty, BigDecimal price, BigDecimal cost) {
        try {
            String raw = String.join("\u001f",
                    orderId, orderDate.toString(), product, category, region,
                    String.valueOf(qty), price.setScale(2).toPlainString(), cost.setScale(2).toPlainString());
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Reconciliation invariance over 50 random filter combinations: sum(trends) == sum(categories) == sum(regions) == summary (revenue & units), and topProducts <= summary")
    void testReconciliationAcross50RandomFilterCombinations() throws Exception {
        Random rng = new Random(42); // Deterministic seed for reproducible tests

        List<String> allCategories = List.of("Electronics", "Furniture", "Stationery", "NonExistentCat");
        List<String> allRegions = List.of("North", "South", "East", "West");
        List<String> sampleProducts = List.of("Laptop", "Desk", "Chair", "Monitor", "Notebook", "o", "a", "SpecialNonExistent");

        LocalDate minDatasetDate = LocalDate.of(2026, 1, 1);
        LocalDate maxDatasetDate = LocalDate.of(2026, 3, 31);

        for (int iteration = 1; iteration <= 50; iteration++) {
            // Generate random date bounds
            LocalDate from = null;
            LocalDate to = null;
            if (rng.nextBoolean()) {
                int startOffset = rng.nextInt(75);
                from = minDatasetDate.plusDays(startOffset);
                int endOffset = startOffset + rng.nextInt(90 - startOffset);
                to = minDatasetDate.plusDays(endOffset);
            }

            // Generate random category subset
            List<String> categories = new ArrayList<>();
            for (String cat : allCategories) {
                if (rng.nextBoolean()) {
                    categories.add(cat);
                }
            }

            // Generate random region subset
            List<String> regions = new ArrayList<>();
            for (String reg : allRegions) {
                if (rng.nextBoolean()) {
                    regions.add(reg);
                }
            }

            // Generate random product search
            String product = null;
            if (rng.nextInt(3) == 0) {
                product = sampleProducts.get(rng.nextInt(sampleProducts.size()));
            }

            // Execute calls for this combination
            SummaryResponse summary = callApi("/api/datasets/{datasetId}/analytics/summary", from, to, categories, regions, product, SummaryResponse.class);
            TrendsResponse trends = callApi("/api/datasets/{datasetId}/analytics/trends", from, to, categories, regions, product, TrendsResponse.class);
            CategoriesResponse catsResp = callApi("/api/datasets/{datasetId}/analytics/categories", from, to, categories, regions, product, CategoriesResponse.class);
            RegionsResponse regionsResp = callApi("/api/datasets/{datasetId}/analytics/regions", from, to, categories, regions, product, RegionsResponse.class);
            TopProductsResponse topProducts = callApi("/api/datasets/{datasetId}/analytics/top-products", from, to, categories, regions, product, TopProductsResponse.class);

            // Reconcile Revenue
            BigDecimal summaryRevenue = new BigDecimal(summary.revenue());

            BigDecimal trendsRevenue = trends.points().stream()
                    .map(p -> new BigDecimal(p.revenue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal categoriesRevenue = catsResp.categories().stream()
                    .map(c -> new BigDecimal(c.revenue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal regionsRevenue = regionsResp.regions().stream()
                    .map(r -> new BigDecimal(r.revenue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal topProductsRevenue = topProducts.products().stream()
                    .map(p -> new BigDecimal(p.revenue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Reconcile Units
            long summaryUnits = summary.unitsSold();

            long trendsUnits = trends.points().stream()
                    .mapToLong(TrendPoint::unitsSold)
                    .sum();

            long categoriesUnits = catsResp.categories().stream()
                    .mapToLong(BreakdownRow::unitsSold)
                    .sum();

            long regionsUnits = regionsResp.regions().stream()
                    .mapToLong(BreakdownRow::unitsSold)
                    .sum();

            // Assertions using compareTo for BigDecimal
            String context = String.format("Iteration %d: from=%s, to=%s, cats=%s, regs=%s, prod=%s",
                    iteration, from, to, categories, regions, product);

            assertThat(trendsRevenue.compareTo(summaryRevenue))
                    .as("Trends revenue must match summary revenue in " + context)
                    .isEqualTo(0);

            assertThat(categoriesRevenue.compareTo(summaryRevenue))
                    .as("Categories revenue must match summary revenue in " + context)
                    .isEqualTo(0);

            assertThat(regionsRevenue.compareTo(summaryRevenue))
                    .as("Regions revenue must match summary revenue in " + context)
                    .isEqualTo(0);

            assertThat(trendsUnits)
                    .as("Trends units must match summary units in " + context)
                    .isEqualTo(summaryUnits);

            assertThat(categoriesUnits)
                    .as("Categories units must match summary units in " + context)
                    .isEqualTo(summaryUnits);

            assertThat(regionsUnits)
                    .as("Regions units must match summary units in " + context)
                    .isEqualTo(summaryUnits);

            assertThat(topProductsRevenue.compareTo(summaryRevenue))
                    .as("Top products revenue must not exceed summary revenue in " + context)
                    .isLessThanOrEqualTo(0);
        }
    }

    private <T> T callApi(
            String path,
            LocalDate from,
            LocalDate to,
            List<String> categories,
            List<String> regions,
            String product,
            Class<T> responseClass
    ) throws Exception {
        MockHttpServletRequestBuilder request = get(path, RECON_DATASET_ID)
                .with(user("dev@example.com").roles("ANALYST"))
                .accept(MediaType.APPLICATION_JSON);

        if (from != null) {
            request.param("from", from.toString());
        }
        if (to != null) {
            request.param("to", to.toString());
        }
        if (categories != null && !categories.isEmpty()) {
            for (String cat : categories) {
                request.param("category", cat);
            }
        }
        if (regions != null && !regions.isEmpty()) {
            for (String reg : regions) {
                request.param("region", reg);
            }
        }
        if (product != null) {
            request.param("product", product);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), responseClass);
    }
}

