package com.example.bi.web;

import com.example.bi.dto.*;
import com.example.bi.service.dataset.DatasetService;
import com.example.bi.service.ingestion.ImportCancelService;
import com.example.bi.service.ingestion.ImportCommitService;
import com.example.bi.service.ingestion.ImportJobService;
import com.example.bi.service.ingestion.StagingRowEditService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/datasets/{datasetId}/imports")
public class ImportController {

    private final ImportJobService importJobService;
    private final ImportCommitService importCommitService;
    private final ImportCancelService importCancelService;
    private final DatasetService datasetService;
    private final StagingRowEditService stagingRowEditService;

    public ImportController(
            ImportJobService importJobService,
            ImportCommitService importCommitService,
            ImportCancelService importCancelService,
            DatasetService datasetService,
            StagingRowEditService stagingRowEditService
    ) {
        this.importJobService = importJobService;
        this.importCommitService = importCommitService;
        this.importCancelService = importCancelService;
        this.datasetService = datasetService;
        this.stagingRowEditService = stagingRowEditService;
    }

    @GetMapping
    public ResponseEntity<List<ImportJobSummaryDto>> getImportHistory(@PathVariable("datasetId") UUID datasetId) {
        List<ImportJobSummaryDto> history = datasetService.getImportHistory(datasetId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ImportJobStatusResponse> getJobStatus(
            @PathVariable("datasetId") UUID datasetId,
            @PathVariable("jobId") UUID jobId
    ) {
        ImportJobStatusResponse response = importJobService.getJobStatus(datasetId, jobId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<ConfirmImportResponse> confirmImport(
            @PathVariable("datasetId") UUID datasetId,
            @Valid @RequestBody ConfirmImportRequest request
    ) {
        ConfirmImportResponse response = importCommitService.confirmImport(datasetId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{jobId}/rows/{rowNumber}")
    public ResponseEntity<EditRowResponse> editRow(
            @PathVariable("datasetId") UUID datasetId,
            @PathVariable("jobId") UUID jobId,
            @PathVariable("rowNumber") int rowNumber,
            @RequestBody EditRowRequest request
    ) {
        EditRowResponse response = stagingRowEditService.editRow(datasetId, jobId, rowNumber, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Void> cancelImport(
            @PathVariable("datasetId") UUID datasetId,
            @PathVariable("jobId") UUID jobId
    ) {
        importCancelService.cancelImport(datasetId, jobId);
        return ResponseEntity.noContent().build();
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
