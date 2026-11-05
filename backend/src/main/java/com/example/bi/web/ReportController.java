package com.example.bi.web;

import com.example.bi.domain.CompareMode;
import com.example.bi.domain.ExportFormat;
import com.example.bi.domain.ExportType;
import com.example.bi.service.analytics.AnalyticsFilter;
import com.example.bi.service.report.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/datasets/{datasetId}/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @PathVariable UUID datasetId,
            @RequestParam(required = false, defaultValue = "csv") String format,
            @RequestParam(required = false, defaultValue = "records") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) CompareMode compare
    ) {
        ExportFormat exportFormat = ExportFormat.fromString(format);
        ExportType exportType = ExportType.fromString(type);
        AnalyticsFilter filter = new AnalyticsFilter(from, to, category, region, product, compare);

        ReportService.ExportResult result = reportService.generateExport(datasetId, exportFormat, exportType, filter);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, result.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.body());
    }
}

