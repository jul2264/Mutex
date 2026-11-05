package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import com.example.bi.domain.Role;
import com.example.bi.domain.User;
import com.example.bi.repository.UserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class XlsxAndPerformanceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmailNormalized("xlsxuser@example.com")) {
            User u = new User();
            u.setEmail("xlsxuser@example.com");
            u.setEmailNormalized("xlsxuser@example.com");
            u.setDisplayName("XLSX User");
            u.setPasswordHash(passwordEncoder.encode("secretPassword123"));
            u.setRole(Role.ANALYST);
            testUser = userRepository.save(u);
        } else {
            testUser = userRepository.findByEmailNormalized("xlsxuser@example.com").orElseThrow();
        }
    }

    @Test
    void uploadXlsx_parsesAndStagesSuccessfully(@TempDir Path tempDir) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sales");
            Row h = sheet.createRow(0);
            String[] cols = {"order_id", "order_date", "product_name", "category", "region", "quantity", "unit_price", "unit_cost"};
            for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);

            for (int r = 1; r <= 10; r++) {
                Row row = sheet.createRow(r);
                row.createCell(0).setCellValue("ORD-" + r);
                row.createCell(1).setCellValue("2026-01-" + String.format("%02d", r));
                row.createCell(2).setCellValue("Product " + r);
                row.createCell(3).setCellValue("Electronics");
                row.createCell(4).setCellValue("North");
                row.createCell(5).setCellValue(r);
                row.createCell(6).setCellValue(100.0 * r);
                row.createCell(7).setCellValue(60.0 * r);
            }
            wb.write(baos);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                baos.toByteArray()
        );

        String dsName = "XLSX Ingestion " + UUID.randomUUID();

        MvcResult result = mockMvc.perform(multipart("/api/datasets/upload")
                        .file(file)
                        .param("name", dsName)
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String datasetId = json.get("datasetId").asText();
        String jobId = json.get("importJobId").asText();

        JsonNode statusJson = pollUntilFinished(datasetId, jobId, testUser.getEmailNormalized());
        assertEquals("VALIDATED", statusJson.get("status").asText());
        assertEquals(10, statusJson.get("totalRows").asInt());
        assertEquals(10, statusJson.get("validRows").asInt());
        assertEquals(0, statusJson.get("invalidRows").asInt());
        assertTrue(statusJson.get("canConfirm").asBoolean());
    }

    @Test
    void macroEnabledWorkbook_rejectedWithFileContentMismatch() throws Exception {
        // Construct a ZIP that contains [Content_Types].xml, xl/workbook.xml, and xl/vbaProject.bin
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<Types></Types>".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zos.write("<workbook></workbook>".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("xl/vbaProject.bin"));
            zos.write("macro-code".getBytes());
            zos.closeEntry();
        }

        MockMultipartFile macroFile = new MockMultipartFile(
                "file",
                "malicious.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                baos.toByteArray()
        );

        mockMvc.perform(multipart("/api/datasets/upload")
                        .file(macroFile)
                        .param("name", "Macro Test " + UUID.randomUUID())
                        .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FILE_CONTENT_MISMATCH"));
    }

    @Test
    @Tag("performance")
    void performanceBenchmark_100kRows_finishesWithinTimeout(@TempDir Path tempDir) throws Exception {
        Path bigCsv = tempDir.resolve("big_100k.csv");
        long genStart = System.currentTimeMillis();

        try (BufferedWriter writer = Files.newBufferedWriter(bigCsv)) {
            writer.write("order_id,order_date,product_name,category,region,quantity,unit_price,unit_cost\n");
            for (int i = 1; i <= 100_000; i++) {
                writer.write(String.format("O-%d,2026-01-15,Keyboard Model %d,Office,North,2,500.00,300.00\n", i, (i % 50)));
            }
        }

        long genTime = System.currentTimeMillis() - genStart;
        System.out.println("Generated 100k CSV in " + genTime + " ms");

        MockMultipartFile file;
        try (InputStream is = new BufferedInputStream(new FileInputStream(bigCsv.toFile()))) {
            file = new MockMultipartFile("file", "big_100k.csv", "text/csv", is);

            long uploadStart = System.currentTimeMillis();

            MvcResult res = mockMvc.perform(multipart("/api/datasets/upload")
                            .file(file)
                            .param("name", "Big 100k " + UUID.randomUUID())
                            .with(user(testUser.getEmailNormalized()).roles("ANALYST"))
                            .with(csrf()))
                    .andExpect(status().isAccepted())
                    .andReturn();

            JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
            String datasetId = json.get("datasetId").asText();
            String jobId = json.get("importJobId").asText();

            // Poll until finished (timeout 120s)
            long deadline = System.currentTimeMillis() + 120_000;
            JsonNode finalStatus = null;
            while (System.currentTimeMillis() < deadline) {
                MvcResult statusRes = mockMvc.perform(get("/api/datasets/{datasetId}/imports/{jobId}", datasetId, jobId)
                                .with(user(testUser.getEmailNormalized()).roles("ANALYST")))
                        .andExpect(status().isOk())
                        .andReturn();

                JsonNode node = objectMapper.readTree(statusRes.getResponse().getContentAsString());
                String status = node.get("status").asText();
                if ("VALIDATED".equals(status) || "FAILED".equals(status)) {
                    finalStatus = node;
                    break;
                }
                Thread.sleep(500);
            }

            assertNotNull(finalStatus, "100k import did not finish within 120s");
            assertEquals("VALIDATED", finalStatus.get("status").asText());
            assertEquals(100_000, finalStatus.get("totalRows").asInt());
            assertEquals(100_000, finalStatus.get("validRows").asInt());

            long totalElapsed = System.currentTimeMillis() - uploadStart;
            System.out.println("100k CSV parsing & staging completed in " + totalElapsed + " ms (target: < 120,000 ms)");
            assertTrue(totalElapsed < 120_000, "100k ingestion took " + totalElapsed + " ms which exceeds target");
        }
    }

    private JsonNode pollUntilFinished(String datasetId, String jobId, String userEmail) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult res = mockMvc.perform(get("/api/datasets/{datasetId}/imports/{jobId}", datasetId, jobId)
                            .with(user(userEmail).roles("ANALYST")))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
            String status = node.get("status").asText();
            if ("VALIDATED".equals(status) || "FAILED".equals(status) || "COMPLETED".equals(status)) {
                return node;
            }
            Thread.sleep(150);
        }
        throw new AssertionError("Import job timed out waiting for completion: " + jobId);
    }
}
