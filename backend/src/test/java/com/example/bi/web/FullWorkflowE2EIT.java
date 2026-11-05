package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import com.example.bi.domain.ImportJobStatus;
import com.example.bi.repository.ImportJobRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class FullWorkflowE2EIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Test
    @DisplayName("E2E Journey: Register -> Login -> Upload dirty.csv -> Review -> Confirm -> Analytics -> Export -> Isolation -> Logout")
    void fullEndToEndUserJourney() throws Exception {
        String email = "analyst." + UUID.randomUUID() + "@example.com";
        String password = "SecurePassword123!";
        String displayName = "Lead Analyst";

        // 1. Register new user
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"%s\"}", email, password, displayName))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.displayName", is(displayName)));

        // 2. Login to obtain session
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // 3. Upload sample-data/dirty.csv
        File sampleFile = new File("sample-data/dirty.csv");
        if (!sampleFile.exists()) {
            sampleFile = new File("../sample-data/dirty.csv");
        }
        byte[] dirtyCsvBytes = Files.readAllBytes(sampleFile.toPath());
        MockMultipartFile file = new MockMultipartFile(
                "file", "dirty.csv", "text/csv", dirtyCsvBytes
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/datasets/upload")
                        .file(file)
                        .param("name", "Q1 Dirty Sales Ingestion")
                        .param("dateFormat", "ISO")
                        .param("policy", "IMPORT_VALID_ONLY")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.datasetId").exists())
                .andExpect(jsonPath("$.importJobId").exists())
                .andReturn();

        String datasetIdStr = com.jayway.jsonpath.JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.datasetId");
        String jobIdStr = com.jayway.jsonpath.JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.importJobId");
        UUID datasetId = UUID.fromString(datasetIdStr);
        UUID jobId = UUID.fromString(jobIdStr);

        // 4. Wait for ingestion worker to complete parsing
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            var job = importJobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() == ImportJobStatus.VALIDATED) {
                break;
            }
            Thread.sleep(100);
        }

        // 5. Review job status counts (dirty.csv has 7 valid, 6 invalid, 1 duplicate candidates = 14 total)
        mockMvc.perform(get("/api/datasets/{datasetId}/imports/{jobId}", datasetId, jobId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(14)))
                .andExpect(jsonPath("$.validRows", is(7)))
                .andExpect(jsonPath("$.invalidRows", is(6)))
                .andExpect(jsonPath("$.duplicateRows", is(1)))
                .andExpect(jsonPath("$.canConfirm", is(true)));

        // 6. Confirm the import
        mockMvc.perform(post("/api/datasets/{datasetId}/imports/confirm", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importJobId\":\"" + jobId + "\",\"includeDuplicates\":false}")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.importedRows", is(7)));

        // 7. Verify analytics summary matches confirmed records
        MvcResult summaryResult = mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", datasetId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems", is(7)))
                .andExpect(jsonPath("$.revenue").exists())
                .andExpect(jsonPath("$.grossProfit").exists())
                .andReturn();

        String summaryRevenue = com.jayway.jsonpath.JsonPath.read(summaryResult.getResponse().getContentAsString(), "$.revenue");

        // 8. Filter and export records to CSV, verifying totals match dashboard
        MvcResult exportAsync = mockMvc.perform(get("/api/datasets/{datasetId}/reports/export", datasetId)
                        .param("format", "csv")
                        .param("type", "records")
                        .session(session))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult exportFinal = mockMvc.perform(asyncDispatch(exportAsync))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andReturn();

        byte[] exportBytes = exportFinal.getResponse().getContentAsByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(exportBytes, 3, exportBytes.length - 3); // Skip BOM
        CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(new InputStreamReader(bais, StandardCharsets.UTF_8));

        List<CSVRecord> exportedRecords = parser.getRecords();
        assertThat(exportedRecords).hasSize(7);

        BigDecimal sumExportRevenue = BigDecimal.ZERO;
        for (CSVRecord rec : exportedRecords) {
            sumExportRevenue = sumExportRevenue.add(new BigDecimal(rec.get("revenue")));
        }
        assertThat(sumExportRevenue).isEqualByComparingTo(summaryRevenue);

        // 9. Hostile file upload is rejected cleanly
        MockMultipartFile hostileFile = new MockMultipartFile(
                "file", "script.exe", "text/csv", new byte[]{0x4D, 0x5A, 0x00, 0x00} // MZ PE executable header
        );
        mockMvc.perform(multipart("/api/datasets/upload")
                        .file(hostileFile)
                        .param("name", "Hostile Test")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_FILE_TYPE")));

        // 10. Multi-tenant isolation: Second user (User B) receives 404 DATASET_NOT_FOUND attempting to access
        String userBEmail = "userb." + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"Password123!\",\"displayName\":\"User B\"}", userBEmail))
                        .with(csrf()))
                .andExpect(status().isCreated());

        MvcResult userBLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"Password123!\"}", userBEmail))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession sessionB = (MockHttpSession) userBLogin.getRequest().getSession(false);

        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", datasetId)
                        .session(sessionB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("DATASET_NOT_FOUND")));

        // 11. Logout invalidates session
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Subsequent API calls with invalidated session return 401
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", datasetId)
                        .session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }
}
