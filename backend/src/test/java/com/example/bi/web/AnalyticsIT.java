package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class AnalyticsIT {

    private static final UUID SEEDED_DATASET_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID UNKNOWN_DATASET_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/datasets/{id}/analytics/summary returns verified F1 oracle totals")
    void getSummary_returnsF1Totals() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", SEEDED_DATASET_ID)
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
                .andExpect(jsonPath("$.grossMarginPct.unavailableReason").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/datasets/{id}/analytics/summary for unknown dataset returns 404 problem detail")
    void getSummary_unknownDataset_returns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", UNKNOWN_DATASET_ID)
                        .header("X-Request-Id", "req-test-1234")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.code", is("DATASET_NOT_FOUND")))
                .andExpect(jsonPath("$.requestId", is("req-test-1234")));
    }
}
