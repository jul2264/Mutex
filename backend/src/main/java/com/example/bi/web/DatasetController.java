package com.example.bi.web;

import com.example.bi.domain.ImportPolicy;
import com.example.bi.dto.*;
import com.example.bi.service.dataset.DatasetService;
import com.example.bi.service.ingestion.DatasetUploadService;
import com.example.bi.service.ingestion.ImportPreviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetUploadService datasetUploadService;
    private final DatasetService datasetService;
    private final ImportPreviewService importPreviewService;

    public DatasetController(
            DatasetUploadService datasetUploadService,
            DatasetService datasetService,
            ImportPreviewService importPreviewService
    ) {
        this.datasetUploadService = datasetUploadService;
        this.datasetService = datasetService;
        this.importPreviewService = importPreviewService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "dateFormat", defaultValue = "ISO") String dateFormat,
            @RequestParam(value = "policy", defaultValue = "IMPORT_VALID_ONLY") ImportPolicy policy,
            @RequestParam(value = "currency", defaultValue = "INR") String currency
    ) throws IOException {
        UploadResponse response = datasetUploadService.uploadAndStartImport(file, name, dateFormat, policy, currency);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<DatasetResponse>> listDatasets(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "direction", required = false) String direction
    ) {
        PageResponse<DatasetResponse> response = datasetService.listDatasets(page, size, sort, direction);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DatasetDetailResponse> getDataset(@PathVariable("id") UUID id) {
        DatasetDetailResponse response = datasetService.getDatasetDetails(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DatasetResponse> renameDataset(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RenameDatasetRequest request
    ) {
        DatasetResponse response = datasetService.renameDataset(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataset(
            @PathVariable("id") UUID id,
            @RequestParam("confirmName") String confirmName
    ) {
        datasetService.deleteDataset(id, confirmName);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<PreviewResponse> getPreview(
            @PathVariable("id") UUID id,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        PreviewResponse response = importPreviewService.getPreview(id, source, status, page, size);
        return ResponseEntity.ok(response);
    }
}
