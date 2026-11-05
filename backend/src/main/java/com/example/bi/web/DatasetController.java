package com.example.bi.web;

import com.example.bi.domain.ImportPolicy;
import com.example.bi.dto.UploadResponse;
import com.example.bi.service.ingestion.DatasetUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetUploadService datasetUploadService;

    public DatasetController(DatasetUploadService datasetUploadService) {
        this.datasetUploadService = datasetUploadService;
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
}
