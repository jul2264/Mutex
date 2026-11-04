package com.example.bi.web;

import com.example.bi.dto.SummaryResponse;
import com.example.bi.service.analytics.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/datasets/{datasetId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> getSummary(
            @PathVariable UUID datasetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        SummaryResponse summary = analyticsService.getSummary(datasetId, from, to);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(summary);
    }
}

