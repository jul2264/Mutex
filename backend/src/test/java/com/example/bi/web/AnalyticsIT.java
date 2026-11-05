package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class AnalyticsIT {

    private static final UUID SEEDED_DATASET_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID F2_DATASET_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final UUID UNKNOWN_DATASET_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedF2Dataset() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM datasets WHERE id = ?", Integer.class, F2_DATASET_ID
        );
        if (count != null && count > 0) {
            return;
        }

        // Insert F2 dataset
        jdbcTemplate.update("""
                INSERT INTO datasets (id, owner_id, name, currency_code, status, row_count, min_order_date, max_order_date, data_version)
                VALUES (?, ?, 'F2 Oracle Dataset', 'INR', 'ACTIVE', 20, '2026-01-05', '2026-03-30', 1)
                """, F2_DATASET_ID, DEV_USER_ID);

        UUID f2JobId = UUID.fromString("00000000-0000-0000-0000-0000000000f3");
        jdbcTemplate.update("""
                INSERT INTO import_jobs (
                    id, dataset_id, created_by, original_filename, file_format, file_size_bytes, file_sha256,
                    date_format, policy, status, total_rows, valid_rows, invalid_rows, duplicate_rows,
                    rows_processed, imported_rows
                ) VALUES (?, ?, ?, 'f2.csv', 'CSV', 1024, 'dummy_sha256', 'ISO', 'IMPORT_VALID_ONLY', 'COMPLETED', 20, 20, 0, 0, 20, 20)
                """, f2JobId, F2_DATASET_ID, DEV_USER_ID);

        // 20 F2 rows per docs/kpi-test-fixture.md
        Object[][] f2Rows = {
                // January 2026
                {"O-101", "2026-01-05", "Laptop", "Electronics", "North", 2, "1000.00", "700.00"},
                {"O-102", "2026-01-10", "Desk", "Furniture", "North", 1, "500.00", "300.00"},
                {"O-103", "2026-01-15", "Chair", "Furniture", "South", 4, "150.00", "100.00"},
                {"O-104", "2026-01-20", "Notebook", "Stationery", "East", 10, "20.00", "10.00"},
                {"O-105", "2026-01-25", "Monitor", "Electronics", "South", 2, "300.00", "250.00"},
                {"O-106", "2026-01-28", "Desk", "Furniture", "East", 2, "500.00", "600.00"}, // negative profit!
                // February 2026
                {"O-201", "2026-02-02", "Laptop", "Electronics", "South", 3, "1000.00", "700.00"},
                {"O-202", "2026-02-08", "Monitor", "Electronics", "North", 1, "300.00", "200.00"},
                {"O-203", "2026-02-14", "Chair", "Furniture", "East", 2, "150.00", "80.00"},
                {"O-204", "2026-02-18", "Notebook", "Stationery", "North", 20, "20.00", "12.00"},
                {"O-205", "2026-02-22", "Desk", "Furniture", "South", 1, "500.00", "350.00"},
                {"O-206", "2026-02-25", "Laptop", "Electronics", "East", 1, "1000.00", "750.00"},
                {"O-207", "2026-02-28", "Monitor", "Electronics", "East", 2, "300.00", "220.00"},
                // March 2026
                {"O-301", "2026-03-03", "Laptop", "Electronics", "North", 2, "1000.00", "700.00"},
                {"O-302", "2026-03-09", "Desk", "Furniture", "North", 2, "500.00", "300.00"},
                {"O-303", "2026-03-15", "Chair", "Furniture", "South", 6, "150.00", "100.00"},
                {"O-304", "2026-03-20", "Notebook", "Stationery", "East", 15, "20.00", "10.00"},
                {"O-305", "2026-03-22", "Notebook", "Stationery", "South", 25, "20.00", "15.00"},
                {"O-306", "2026-03-28", "Monitor", "Electronics", "North", 3, "300.00", "210.00"},
                {"O-307", "2026-03-30", "Chair", "Furniture", "North", 2, "150.00", "110.00"}
        };

        for (int i = 0; i < f2Rows.length; i++) {
            Object[] r = f2Rows[i];
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
                    """, F2_DATASET_ID, f2JobId, i + 1, orderId, orderDate, product, category, region, qty, price, cost, hash);
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

    // ==========================================
    // F1 Oracle Tests
    // ==========================================

    @Test
    @DisplayName("GET /summary on F1 returns verified totals")
    void f1_summary_returnsVerifiedTotals() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", SEEDED_DATASET_ID)
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetId", is(SEEDED_DATASET_ID.toString())))
                .andExpect(jsonPath("$.currency", is("INR")))
                .andExpect(jsonPath("$.revenue", is("9000.00")))
                .andExpect(jsonPath("$.grossProfit", is("3000.00")))
                .andExpect(jsonPath("$.unitsSold", is(15)))
                .andExpect(jsonPath("$.orders", is(2)))
                .andExpect(jsonPath("$.lineItems", is(2)))
                .andExpect(jsonPath("$.grossMarginPct.value", is("33.33")))
                .andExpect(jsonPath("$.revenueGrowthPct.unavailableReason", is("NOT_REQUESTED")));
    }

    @Test
    @DisplayName("GET /summary on F1 with comparison returns growth = -20.00% (BR-10 whole month)")
    void f1_summary_comparison_returnsNegativeGrowth() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", SEEDED_DATASET_ID)
                        .param("from", "2026-02-01")
                        .param("to", "2026-02-28")
                        .param("compare", "PREVIOUS_PERIOD")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue", is("4000.00")))
                .andExpect(jsonPath("$.grossProfit", is("1000.00")))
                .andExpect(jsonPath("$.unitsSold", is(5)))
                .andExpect(jsonPath("$.previous.revenue", is("5000.00")))
                .andExpect(jsonPath("$.previous.grossProfit", is("2000.00")))
                .andExpect(jsonPath("$.previous.unitsSold", is(10)))
                .andExpect(jsonPath("$.revenueGrowthPct.value", is("-20.00")));
    }

    @Test
    @DisplayName("GET /trends on F1 returns zero-filled monthly series with LAG growth")
    void f1_trends_returnsMonthlySeries() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/trends", SEEDED_DATASET_ID)
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(2)))
                .andExpect(jsonPath("$.points[0].month", is("2026-01")))
                .andExpect(jsonPath("$.points[0].revenue", is("5000.00")))
                .andExpect(jsonPath("$.points[0].grossProfit", is("2000.00")))
                .andExpect(jsonPath("$.points[0].unitsSold", is(10)))
                .andExpect(jsonPath("$.points[0].grossMarginPct.value", is("40.00")))
                .andExpect(jsonPath("$.points[0].revenueGrowthPct.unavailableReason", is("NOT_REQUESTED")))
                .andExpect(jsonPath("$.points[1].month", is("2026-02")))
                .andExpect(jsonPath("$.points[1].revenue", is("4000.00")))
                .andExpect(jsonPath("$.points[1].grossProfit", is("1000.00")))
                .andExpect(jsonPath("$.points[1].unitsSold", is(5)))
                .andExpect(jsonPath("$.points[1].grossMarginPct.value", is("25.00")))
                .andExpect(jsonPath("$.points[1].revenueGrowthPct.value", is("-20.00")));
    }

    // ==========================================
    // F2 Oracle Tests (Hand-computed oracle)
    // ==========================================

    @Test
    @DisplayName("GET /summary on F2 matches hand-computed totals")
    void f2_summary_matchesOracle() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", F2_DATASET_ID)
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue", is("16900.00")))
                .andExpect(jsonPath("$.grossProfit", is("4785.00")))
                .andExpect(jsonPath("$.unitsSold", is(106)))
                .andExpect(jsonPath("$.orders", is(20)))
                .andExpect(jsonPath("$.lineItems", is(20)))
                .andExpect(jsonPath("$.grossMarginPct.value", is("28.31")));
    }

    @Test
    @DisplayName("GET /trends on F2 matches hand-computed monthly totals and MoM growth")
    void f2_trends_matchesOracle() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/trends", F2_DATASET_ID)
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(3)))
                // Jan 2026
                .andExpect(jsonPath("$.points[0].month", is("2026-01")))
                .andExpect(jsonPath("$.points[0].revenue", is("4900.00")))
                .andExpect(jsonPath("$.points[0].grossProfit", is("1000.00")))
                .andExpect(jsonPath("$.points[0].unitsSold", is(21)))
                .andExpect(jsonPath("$.points[0].grossMarginPct.value", is("20.41")))
                // Feb 2026
                .andExpect(jsonPath("$.points[1].month", is("2026-02")))
                .andExpect(jsonPath("$.points[1].revenue", is("6100.00")))
                .andExpect(jsonPath("$.points[1].grossProfit", is("1860.00")))
                .andExpect(jsonPath("$.points[1].unitsSold", is(30)))
                .andExpect(jsonPath("$.points[1].grossMarginPct.value", is("30.49")))
                .andExpect(jsonPath("$.points[1].revenueGrowthPct.value", is("24.49")))
                // Mar 2026
                .andExpect(jsonPath("$.points[2].month", is("2026-03")))
                .andExpect(jsonPath("$.points[2].revenue", is("5900.00")))
                .andExpect(jsonPath("$.points[2].grossProfit", is("1925.00")))
                .andExpect(jsonPath("$.points[2].unitsSold", is(55)))
                .andExpect(jsonPath("$.points[2].grossMarginPct.value", is("32.63")))
                .andExpect(jsonPath("$.points[2].revenueGrowthPct.value", is("-3.28")));
    }

    @Test
    @DisplayName("GET /categories on F2 matches hand-computed breakdown")
    void f2_categories_matchesOracle() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/categories", F2_DATASET_ID)
                        .param("sort", "revenue")
                        .param("direction", "desc")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(3)))
                // Electronics
                .andExpect(jsonPath("$.categories[0].name", is("Electronics")))
                .andExpect(jsonPath("$.categories[0].revenue", is("10400.00")))
                .andExpect(jsonPath("$.categories[0].grossProfit", is("2980.00")))
                .andExpect(jsonPath("$.categories[0].unitsSold", is(16)))
                .andExpect(jsonPath("$.categories[0].revenueSharePct", is("61.54")))
                .andExpect(jsonPath("$.categories[0].grossMarginPct.value", is("28.65")))
                // Furniture
                .andExpect(jsonPath("$.categories[1].name", is("Furniture")))
                .andExpect(jsonPath("$.categories[1].revenue", is("5100.00")))
                .andExpect(jsonPath("$.categories[1].grossProfit", is("1270.00")))
                .andExpect(jsonPath("$.categories[1].unitsSold", is(20)))
                .andExpect(jsonPath("$.categories[1].revenueSharePct", is("30.18")))
                .andExpect(jsonPath("$.categories[1].grossMarginPct.value", is("24.90")))
                // Stationery
                .andExpect(jsonPath("$.categories[2].name", is("Stationery")))
                .andExpect(jsonPath("$.categories[2].revenue", is("1400.00")))
                .andExpect(jsonPath("$.categories[2].grossProfit", is("535.00")))
                .andExpect(jsonPath("$.categories[2].unitsSold", is(70)))
                .andExpect(jsonPath("$.categories[2].revenueSharePct", is("8.28")))
                .andExpect(jsonPath("$.categories[2].grossMarginPct.value", is("38.21")));
    }

    @Test
    @DisplayName("GET /regions on F2 matches hand-computed breakdown and margin deltas")
    void f2_regions_matchesOracle() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/regions", F2_DATASET_ID)
                        .param("sort", "revenue")
                        .param("direction", "desc")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions", hasSize(3)))
                // North
                .andExpect(jsonPath("$.regions[0].name", is("North")))
                .andExpect(jsonPath("$.regions[0].revenue", is("7400.00")))
                .andExpect(jsonPath("$.regions[0].grossProfit", is("2410.00")))
                .andExpect(jsonPath("$.regions[0].unitsSold", is(33)))
                .andExpect(jsonPath("$.regions[0].revenueSharePct", is("43.79")))
                .andExpect(jsonPath("$.regions[0].grossMarginPct.value", is("32.57")))
                .andExpect(jsonPath("$.regions[0].marginDeltaPoints", is("4.26"))) // 32.57 - 28.31 = 4.26
                // South
                .andExpect(jsonPath("$.regions[1].name", is("South")))
                .andExpect(jsonPath("$.regions[1].revenue", is("6100.00")))
                .andExpect(jsonPath("$.regions[1].grossProfit", is("1775.00")))
                .andExpect(jsonPath("$.regions[1].unitsSold", is(41)))
                .andExpect(jsonPath("$.regions[1].revenueSharePct", is("36.09")))
                .andExpect(jsonPath("$.regions[1].grossMarginPct.value", is("29.10")))
                .andExpect(jsonPath("$.regions[1].marginDeltaPoints", is("0.79"))) // 29.10 - 28.31 = 0.79
                // East
                .andExpect(jsonPath("$.regions[2].name", is("East")))
                .andExpect(jsonPath("$.regions[2].revenue", is("3400.00")))
                .andExpect(jsonPath("$.regions[2].grossProfit", is("600.00")))
                .andExpect(jsonPath("$.regions[2].unitsSold", is(32)))
                .andExpect(jsonPath("$.regions[2].revenueSharePct", is("20.12")))
                .andExpect(jsonPath("$.regions[2].grossMarginPct.value", is("17.65")))
                .andExpect(jsonPath("$.regions[2].marginDeltaPoints", is("-10.66"))); // 17.65 - 28.31 = -10.66
    }

    @Test
    @DisplayName("GET /top-products on F2 proves dual rankings (Desk revenue #2 vs profit #4, Chair rev #4 vs profit #2)")
    void f2_topProducts_dualRankings() throws Exception {
        // Ordered by revenue
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/top-products", F2_DATASET_ID)
                        .param("rankBy", "REVENUE")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(5)))
                .andExpect(jsonPath("$.products[0].product", is("Laptop")))
                .andExpect(jsonPath("$.products[0].revenueRank", is(1)))
                .andExpect(jsonPath("$.products[0].profitRank", is(1)))
                .andExpect(jsonPath("$.products[1].product", is("Desk")))
                .andExpect(jsonPath("$.products[1].revenueRank", is(2)))
                .andExpect(jsonPath("$.products[1].profitRank", is(4)))
                .andExpect(jsonPath("$.products[2].product", is("Monitor")))
                .andExpect(jsonPath("$.products[2].revenueRank", is(3)))
                .andExpect(jsonPath("$.products[2].profitRank", is(3)))
                .andExpect(jsonPath("$.products[3].product", is("Chair")))
                .andExpect(jsonPath("$.products[3].revenueRank", is(4)))
                .andExpect(jsonPath("$.products[3].profitRank", is(2)))
                .andExpect(jsonPath("$.products[4].product", is("Notebook")))
                .andExpect(jsonPath("$.products[4].revenueRank", is(5)))
                .andExpect(jsonPath("$.products[4].profitRank", is(5)));

        // Ordered by profit
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/top-products", F2_DATASET_ID)
                        .param("rankBy", "PROFIT")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(5)))
                .andExpect(jsonPath("$.products[0].product", is("Laptop")))
                .andExpect(jsonPath("$.products[1].product", is("Chair")))
                .andExpect(jsonPath("$.products[2].product", is("Monitor")))
                .andExpect(jsonPath("$.products[3].product", is("Desk")))
                .andExpect(jsonPath("$.products[4].product", is("Notebook")));
    }

    @Test
    @DisplayName("GET /filter-options on F2 returns canonical filter metadata")
    void f2_filterOptions_matchesDatasetMetadata() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/filter-options", F2_DATASET_ID)
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", contains("Electronics", "Furniture", "Stationery")))
                .andExpect(jsonPath("$.regions", contains("East", "North", "South")))
                .andExpect(jsonPath("$.minOrderDate", is("2026-01-05")))
                .andExpect(jsonPath("$.maxOrderDate", is("2026-03-30")))
                .andExpect(jsonPath("$.totalProducts", is(5)))
                .andExpect(jsonPath("$.categoriesTruncated", is(false)))
                .andExpect(jsonPath("$.regionsTruncated", is(false)));
    }

    // ==========================================
    // Boundaries, Validations & Edge Cases
    // ==========================================

    @Test
    @DisplayName("Date boundary: `to` date is inclusive (order on `to` is included)")
    void dateBoundary_toIsInclusive() throws Exception {
        // Query from 2026-01-05 to 2026-01-10: includes O-101 (Jan 5) and O-102 (Jan 10), excludes O-103 (Jan 15)
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", F2_DATASET_ID)
                        .param("from", "2026-01-05")
                        .param("to", "2026-01-10")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue", is("2500.00"))) // 2000 + 500
                .andExpect(jsonPath("$.grossProfit", is("800.00"))) // 600 + 200
                .andExpect(jsonPath("$.unitsSold", is(3)))
                .andExpect(jsonPath("$.orders", is(2)))
                .andExpect(jsonPath("$.lineItems", is(2)));
    }

    @Test
    @DisplayName("Empty filter result: returns zero revenue, zero units, ZERO_REVENUE margin")
    void emptyFilterResult_returnsZeroRevenueAndUnavailableMargin() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", F2_DATASET_ID)
                        .param("product", "NonExistentProductXYZ")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue", is("0.00")))
                .andExpect(jsonPath("$.grossProfit", is("0.00")))
                .andExpect(jsonPath("$.unitsSold", is(0)))
                .andExpect(jsonPath("$.orders", is(0)))
                .andExpect(jsonPath("$.lineItems", is(0)))
                .andExpect(jsonPath("$.grossMarginPct.unavailableReason", is("ZERO_REVENUE")));
    }

    @Test
    @DisplayName("Negative profit remains visible and correctly aggregates (O-106)")
    void negativeProfit_remainsVisible() throws Exception {
        // O-106 is Desk in East on 2026-01-28 with profit -200.00
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", F2_DATASET_ID)
                        .param("from", "2026-01-28")
                        .param("to", "2026-01-28")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue", is("1000.00")))
                .andExpect(jsonPath("$.grossProfit", is("-200.00")))
                .andExpect(jsonPath("$.grossMarginPct.value", is("-20.00")));
    }

    @Test
    @DisplayName("Wildcard escaping in product search: '%' and '_' match literally")
    void wildcardEscaping_matchesLiterally() throws Exception {
        // Insert a record with product name containing '%' and '_'
        UUID testJobId = UUID.fromString("00000000-0000-0000-0000-0000000000f3");
        jdbcTemplate.update("""
                INSERT INTO sales_records (
                    dataset_id, import_job_id, source_row_number, order_id, order_date, product_name, category, region, quantity, unit_price, unit_cost, row_hash
                ) VALUES (?, ?, 99, 'O-WILDCARD', '2026-01-15', 'Special%Item_100', 'Electronics', 'North', 1, 100.00, 50.00, 'wildcard_hash')
                """, F2_DATASET_ID, testJobId);

        // Searching for exact literal "Special%Item_100" should find it
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", F2_DATASET_ID)
                        .param("product", "Special%Item_100")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue", is("100.00")))
                .andExpect(jsonPath("$.lineItems", is(1)));

        // Searching for "SpecialXItemY100" should NOT match (proving % and _ were not wildcarded)
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", F2_DATASET_ID)
                        .param("product", "SpecialXItemY100")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems", is(0)));

        // Cleanup
        jdbcTemplate.update("DELETE FROM sales_records WHERE order_id = 'O-WILDCARD'");
    }

    @Test
    @DisplayName("SQL injection in product parameter is treated literally as safe bound value")
    void sqlInjection_treatedLiterally() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", F2_DATASET_ID)
                        .param("product", "' OR '1'='1' --")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems", is(0)));
    }

    @Test
    @DisplayName("Unknown sort column returns 400 INVALID_SORT_PARAMETER")
    void unknownSortColumn_returns400() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/categories", F2_DATASET_ID)
                        .param("sort", "malicious_col; DROP TABLE sales_records;--")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("INVALID_SORT_PARAMETER")));
    }

    @Test
    @DisplayName("Invalid sort direction returns 400 INVALID_SORT_PARAMETER")
    void invalidSortDirection_returns400() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/regions", F2_DATASET_ID)
                        .param("direction", "sideways")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("INVALID_SORT_PARAMETER")));
    }

    @Test
    @DisplayName("Invalid date range (from > to) returns 400 INVALID_DATE_RANGE")
    void invalidDateRange_returns400() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", F2_DATASET_ID)
                        .param("from", "2026-03-01")
                        .param("to", "2026-01-01")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("INVALID_DATE_RANGE")));
    }

    @Test
    @DisplayName("Date range spanning > 10 years returns 400 INVALID_DATE_RANGE")
    void dateRangeExceeding10Years_returns400() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", F2_DATASET_ID)
                        .param("from", "2015-01-01")
                        .param("to", "2026-01-01")
                        .with(user("dev@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("INVALID_DATE_RANGE")));
    }

    @Test
    @DisplayName("GET /summary for unknown dataset returns 404 problem detail")
    void getSummary_unknownDataset_returns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", UNKNOWN_DATASET_ID)
                        .with(user("dev@example.com").roles("ANALYST"))
                        .header("X-Request-Id", "req-test-1234")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.code", is("DATASET_NOT_FOUND")))
                .andExpect(jsonPath("$.requestId", is("req-test-1234")));
    }
}
