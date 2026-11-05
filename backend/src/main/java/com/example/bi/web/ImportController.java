package com.example.bi.web;

import com.example.bi.dto.ImportJobStatusResponse;
import com.example.bi.service.ingestion.ImportJobService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/datasets/{datasetId}/imports")
public class ImportController {

    private final ImportJobService importJobService;

    public ImportController(ImportJobService importJobService) {
        this.importJobService = importJobService;
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ImportJobStatusResponse> getJobStatus(
            @PathVariable("datasetId") UUID datasetId,
            @PathVariable("jobId") UUID jobId
    ) {
        ImportJobStatusResponse response = importJobService.getJobStatus(datasetId, jobId);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{jobId}/errors.csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> downloadErrorReport(
            @PathVariable("datasetId") UUID datasetId,
            @PathVariable("jobId") UUID jobId
    ) {
        importJobService.checkAccess(datasetId, jobId);

        StreamingResponseBody stream = outputStream -> {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                importJobService.writeErrorReportCsv(datasetId, jobId, writer);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"errors.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(stream);
    }
}
