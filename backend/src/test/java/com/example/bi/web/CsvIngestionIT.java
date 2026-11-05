package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import com.example.bi.domain.Role;
import com.example.bi.domain.User;
import com.example.bi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class CsvIngestionIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmailNormalized("usera@example.com")) {
            User u = new User();
            u.setEmail("usera@example.com");
            u.setEmailNormalized("usera@example.com");
            u.setDisplayName("User A");
            u.setPasswordHash(passwordEncoder.encode("secretPassword123"));
            u.setRole(Role.ANALYST);
            userA = userRepository.save(u);
        } else {
            userA = userRepository.findByEmailNormalized("usera@example.com").orElseThrow();
        }

        if (!userRepository.existsByEmailNormalized("userb@example.com")) {
            User u = new User();
            u.setEmail("userb@example.com");
            u.setEmailNormalized("userb@example.com");
            u.setDisplayName("User B");
            u.setPasswordHash(passwordEncoder.encode("secretPassword123"));
            u.setRole(Role.ANALYST);
            userB = userRepository.save(u);
        } else {
            userB = userRepository.findByEmailNormalized("userb@example.com").orElseThrow();
        }
    }

    @Test
    void uploadClean200_parsesAndStagesAllRowsAsValid() throws Exception {
        byte[] csvBytes = Files.readAllBytes(new File("../sample-data/clean_200.csv").toPath());
        MockMultipartFile file = new MockMultipartFile("file", "clean_200.csv", "text/csv", csvBytes);

        String datasetName = "Clean 200 " + UUID.randomUUID();

        MvcResult uploadResult = mockMvc.perform(multipart("/api/datasets/upload")
                        .file(file)
                        .param("name", datasetName)
                        .param("dateFormat", "yyyy-MM-dd")
                        .param("policy", "IMPORT_VALID_ONLY")
                        .with(user(userA.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.datasetId").isNotEmpty())
                .andExpect(jsonPath("$.importJobId").isNotEmpty())
                .andExpect(jsonPath("$.statusUrl").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String datasetId = json.get("datasetId").asText();
        String jobId = json.get("importJobId").asText();

        // Poll status until complete (up to 10 seconds)
        JsonNode statusJson = pollUntilFinished(datasetId, jobId, userA.getEmailNormalized());

        assertEquals("VALIDATED", statusJson.get("status").asText());
        assertEquals(200, statusJson.get("totalRows").asInt());
        assertEquals(200, statusJson.get("validRows").asInt());
        assertEquals(0, statusJson.get("invalidRows").asInt());
        assertEquals(0, statusJson.get("duplicateRows").asInt());
        assertEquals(0, statusJson.get("blankRowsSkipped").asInt());
        assertTrue(statusJson.get("canConfirm").asBoolean());
        assertTrue(statusJson.get("confirmBlockedReason").isNull());
    }

    @Test
    void uploadDirtyCsv_countsReconcileAndErrorReportDownloads() throws Exception {
        byte[] csvBytes = Files.readAllBytes(new File("../sample-data/dirty.csv").toPath());
        MockMultipartFile file = new MockMultipartFile("file", "dirty.csv", "text/csv", csvBytes);

        String datasetName = "Dirty Test " + UUID.randomUUID();

        MvcResult uploadResult = mockMvc.perform(multipart("/api/datasets/upload")
                        .file(file)
                        .param("name", datasetName)
                        .param("dateFormat", "yyyy-MM-dd")
                        .param("policy", "IMPORT_VALID_ONLY")
                        .with(user(userA.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode json = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String datasetId = json.get("datasetId").asText();
        String jobId = json.get("importJobId").asText();

        JsonNode statusJson = pollUntilFinished(datasetId, jobId, userA.getEmailNormalized());

        assertEquals("VALIDATED", statusJson.get("status").asText());

        // Reconcile counts:
        // valid (7) + invalid (6) + duplicate (1) = totalRows (14)
        int validRows = statusJson.get("validRows").asInt();
        int invalidRows = statusJson.get("invalidRows").asInt();
        int duplicateRows = statusJson.get("duplicateRows").asInt();
        int totalRows = statusJson.get("totalRows").asInt();
        int blankRowsSkipped = statusJson.get("blankRowsSkipped").asInt();

        assertEquals(7, validRows, "Valid rows mismatch");
        assertEquals(6, invalidRows, "Invalid rows mismatch");
        assertEquals(1, duplicateRows, "Duplicate rows mismatch");
        assertEquals(14, totalRows, "Total rows mismatch");
        assertEquals(validRows + invalidRows + duplicateRows, totalRows, "valid + invalid + duplicate must equal total");
        assertTrue(blankRowsSkipped >= 1, "Blank rows skipped should be at least 1");

        // Download errors.csv
        MvcResult asyncResult = mockMvc.perform(get("/api/datasets/{datasetId}/imports/{jobId}/errors.csv", datasetId, jobId)
                        .with(user(userA.getEmailNormalized()).roles("ANALYST")))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult errorCsvResult = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("errors.csv")))
                .andReturn();

        String csvContent = errorCsvResult.getResponse().getContentAsString();
        assertTrue(csvContent.contains("source_row_number,column_name,severity,code,message,raw_value"));
        assertTrue(csvContent.contains("INVALID_DATE"));
        assertTrue(csvContent.contains("QUANTITY_NOT_POSITIVE"));
        assertTrue(csvContent.contains("INVALID_INTEGER"));
        assertTrue(csvContent.contains("QUANTITY_TOO_LARGE"));
        assertTrue(csvContent.contains("REQUIRED_VALUE_MISSING"));
        assertTrue(csvContent.contains("NEGATIVE_VALUE"));
        assertTrue(csvContent.contains("DUPLICATE_IN_FILE"));
    }

    @Test
    void uploadMissingHeader_marksJobFailedWithMissingColumnCode() throws Exception {
        byte[] csvBytes = Files.readAllBytes(new File("../sample-data/missing_header.csv").toPath());
        MockMultipartFile file = new MockMultipartFile("file", "missing_header.csv", "text/csv", csvBytes);

        String datasetName = "Missing Header " + UUID.randomUUID();

        MvcResult uploadResult = mockMvc.perform(multipart("/api/datasets/upload")
                        .file(file)
                        .param("name", datasetName)
                        .with(user(userA.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode json = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String datasetId = json.get("datasetId").asText();
        String jobId = json.get("importJobId").asText();

        JsonNode statusJson = pollUntilFinished(datasetId, jobId, userA.getEmailNormalized());

        assertEquals("FAILED", statusJson.get("status").asText());
        assertNotNull(statusJson.get("fileError"));
        assertEquals("MISSING_COLUMN", statusJson.get("fileError").get("code").asText());
        assertTrue(statusJson.get("fileError").get("message").asText().contains("order_date"));
    }

    @Test
    void hostileFiles_rejectedWithCorrectHttpStatusAndProblemDetail() throws Exception {
        // 1. Empty file -> 400 FILE_EMPTY
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
        mockMvc.perform(multipart("/api/datasets/upload")
                        .file(emptyFile)
                        .param("name", "Empty " + UUID.randomUUID())
                        .with(user(userA.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_EMPTY"));

        // 2. Binary file (PNG signature) renamed to .csv -> 415 FILE_CONTENT_MISMATCH
        byte[] fakePng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile binaryCsv = new MockMultipartFile("file", "image.csv", "text/csv", fakePng);
        mockMvc.perform(multipart("/api/datasets/upload")
                        .file(binaryCsv)
                        .param("name", "Binary " + UUID.randomUUID())
                        .with(user(userA.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FILE_CONTENT_MISMATCH"));

        // 3. Unsupported extension (.txt) -> 415 UNSUPPORTED_FILE_TYPE
        MockMultipartFile textFile = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/datasets/upload")
                        .file(textFile)
                        .param("name", "Text " + UUID.randomUUID())
                        .with(user(userA.getEmailNormalized()).roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void authorizationMatrix_ingestionEndpointsProtected() throws Exception {
        // 1. Anonymous upload -> 401
        MockMultipartFile file = new MockMultipartFile("file", "clean_200.csv", "text/csv", "header".getBytes());
        mockMvc.perform(multipart("/api/datasets/upload")
                        .file(file)
                        .param("name", "Anon DS")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        // 2. User B accessing User A's import job -> 404, never 403
        UUID fakeDatasetId = UUID.randomUUID();
        UUID fakeJobId = UUID.randomUUID();
        mockMvc.perform(get("/api/datasets/{datasetId}/imports/{jobId}", fakeDatasetId, fakeJobId)
                        .with(user(userB.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASET_NOT_FOUND"));

        // 3. User B downloading User A's errors.csv -> 404
        mockMvc.perform(get("/api/datasets/{datasetId}/imports/{jobId}/errors.csv", fakeDatasetId, fakeJobId)
                        .with(user(userB.getEmailNormalized()).roles("ANALYST")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASET_NOT_FOUND"));
    }

    private JsonNode pollUntilFinished(String datasetId, String jobId, String userEmail) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
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
            Thread.sleep(100);
        }
        throw new AssertionError("Import job timed out waiting for completion: " + jobId);
    }
}
