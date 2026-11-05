package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class ReportIT {

    private static final UUID SEEDED_DATASET_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("CSV records export on F1 reproduces F1 totals, UTF-8 BOM, and headers")
    void f1_csvExport_reproducesF1OracleAndFormat() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/datasets/{id}/reports/export", SEEDED_DATASET_ID)
                        .param("format", "csv")
                        .param("type", "records")
                        .with(user("dev@example.com").roles("ANALYST")))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();

        byte[] content = finalResult.getResponse().getContentAsByteArray();
        assertThat(content).hasSizeGreaterThan(3);

        // Assert UTF-8 BOM (0xEF, 0xBB, 0xBF)
        assertThat(content[0]).isEqualTo((byte) 0xEF);
        assertThat(content[1]).isEqualTo((byte) 0xBB);
        assertThat(content[2]).isEqualTo((byte) 0xBF);

        // Skip BOM and parse CSV
        ByteArrayInputStream bais = new ByteArrayInputStream(content, 3, content.length - 3);
        CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(new InputStreamReader(bais, StandardCharsets.UTF_8));

        List<CSVRecord> records = parser.getRecords();
        assertThat(records).hasSize(2);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        long totalUnits = 0;

        for (CSVRecord record : records) {
            totalRevenue = totalRevenue.add(new BigDecimal(record.get("revenue")));
            totalProfit = totalProfit.add(new BigDecimal(record.get("gross_profit")));
            totalUnits += Long.parseLong(record.get("quantity"));
        }

        // F1 Oracle check: revenue 9000.00, profit 3000.00, units 15
        assertThat(totalRevenue).isEqualByComparingTo("9000.00");
        assertThat(totalProfit).isEqualByComparingTo("3000.00");
        assertThat(totalUnits).isEqualTo(15);
    }

    @Test
    @DisplayName("Formula injection: text columns prefixed with single quote, numeric columns untouched")
    void formulaInjection_escapesTextColumns_andPreservesNegativeNumbers() throws Exception {
        UUID injectionDatasetId = UUID.randomUUID();

        // Create a dataset owned by dev user
        jdbcTemplate.update("""
                INSERT INTO datasets (id, owner_id, name, currency_code, status, row_count, min_order_date, max_order_date, data_version)
                VALUES (?, ?, 'Injection Test Dataset', 'INR', 'ACTIVE', 1, '2026-01-15', '2026-01-15', 1)
                """, injectionDatasetId, DEV_USER_ID);

        String orderId = "=CMD";
        String orderDate = "2026-01-15";
        String productName = "+cmd|' /C calc'!A0";
        String category = "@DangerousCategory";
        String region = "\tSecretRegion";
        int quantity = 2;
        BigDecimal unitPrice = new BigDecimal("100.00");
        BigDecimal unitCost = new BigDecimal("150.00"); // Negative profit!
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                String.join("\u001F", orderId, orderDate, productName, category, region, "2", "100.00", "150.00").getBytes(StandardCharsets.UTF_8)
        );
        String hash = HexFormat.of().formatHex(digest);

        jdbcTemplate.update("""
                INSERT INTO sales_records (
                    dataset_id, order_id, order_date, product_name, category, region,
                    quantity, unit_price, unit_cost, row_hash
                ) VALUES (?, ?, '2026-01-15', ?, ?, ?, ?, ?, ?, ?)
                """, injectionDatasetId, orderId, productName, category, region, quantity, unitPrice, unitCost, hash);

        MvcResult mvcResult = mockMvc.perform(get("/api/datasets/{id}/reports/export", injectionDatasetId)
                        .param("format", "csv")
                        .param("type", "records")
                        .with(user("dev@example.com").roles("ANALYST")))
                .andExpect(request().asyncStarted())
                .andReturn();

        byte[] content = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        ByteArrayInputStream bais = new ByteArrayInputStream(content, 3, content.length - 3);
        CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(new InputStreamReader(bais, StandardCharsets.UTF_8));

        CSVRecord record = parser.getRecords().get(0);

        // Text columns must be prefixed with single quote
        assertThat(record.get("order_id")).isEqualTo("'=CMD");
        assertThat(record.get("product_name")).isEqualTo("'+cmd|' /C calc'!A0");
        assertThat(record.get("category")).isEqualTo("'@DangerousCategory");
        assertThat(record.get("region")).isEqualTo("'\tSecretRegion");

        // Numeric columns must NOT be escaped and must preserve negative values
        assertThat(record.get("quantity")).isEqualTo("2");
        assertThat(record.get("unit_price")).isEqualTo("100.00");
        assertThat(record.get("unit_cost")).isEqualTo("150.00");
        assertThat(record.get("revenue")).isEqualTo("200.00");
        assertThat(record.get("gross_profit")).isEqualTo("-100.00");
    }

    @Test
    @DisplayName("XLSX records export parses back with Apache POI and matches totals")
    void xlsxRecordsExport_parsesWithPoi() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/datasets/{id}/reports/export", SEEDED_DATASET_ID)
                        .param("format", "xlsx")
                        .param("type", "records")
                        .with(user("dev@example.com").roles("ANALYST")))
                .andExpect(request().asyncStarted())
                .andReturn();

        byte[] content = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheet("Records");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3); // 1 header + 2 F1 data rows

            // Check row 0 header
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Order ID");
            assertThat(sheet.getRow(0).getCell(8).getStringCellValue()).isEqualTo("Revenue");

            // Check row 1 data
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("A-1001");
            assertThat(sheet.getRow(1).getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(10.0);
            assertThat(sheet.getRow(1).getCell(8).getNumericCellValue()).isEqualTo(5000.00);

            // Check row 2 data
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("A-1002");
            assertThat(sheet.getRow(2).getCell(5).getNumericCellValue()).isEqualTo(5.0);
            assertThat(sheet.getRow(2).getCell(8).getNumericCellValue()).isEqualTo(4000.00);
        }
    }

    @Test
    @DisplayName("XLSX summary workbook parses with POI and matches KPI summary values")
    void xlsxSummaryWorkbook_parsesWithPoi_andMatchesSummaryApi() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/datasets/{id}/reports/export", SEEDED_DATASET_ID)
                        .param("format", "xlsx")
                        .param("type", "summary")
                        .with(user("dev@example.com").roles("ANALYST")))
                .andExpect(request().asyncStarted())
                .andReturn();

        byte[] content = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheet("Summary")).isNotNull();
            assertThat(workbook.getSheet("Trends")).isNotNull();
            assertThat(workbook.getSheet("Categories")).isNotNull();
            assertThat(workbook.getSheet("Regions")).isNotNull();
            assertThat(workbook.getSheet("Top Products")).isNotNull();

            Sheet summarySheet = workbook.getSheet("Summary");
            assertThat(summarySheet.getRow(0).getCell(0).getStringCellValue())
                    .contains("Mutex Business Intelligence — KPI Summary Report");
        }
    }

    @Test
    @DisplayName("Summary in CSV is rejected with 400 Bad Request")
    void csvSummaryExport_rejectedWith400() throws Exception {
        mockMvc.perform(get("/api/datasets/{id}/reports/export", SEEDED_DATASET_ID)
                        .param("format", "csv")
                        .param("type", "summary")
                        .with(user("dev@example.com").roles("ANALYST")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_FILTER_PARAMETER")));
    }

    @Test
    @DisplayName("Foreign or nonexistent dataset returns 404, never 403")
    void foreignDataset_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        mockMvc.perform(get("/api/datasets/{id}/reports/export", unknownId)
                        .param("format", "csv")
                        .param("type", "records")
                        .with(user("dev@example.com").roles("ANALYST")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("DATASET_NOT_FOUND")));
    }
}
