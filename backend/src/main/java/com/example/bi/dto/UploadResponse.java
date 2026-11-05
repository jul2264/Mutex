package com.example.bi.dto;

import java.util.UUID;

public record UploadResponse(
        UUID datasetId,
        UUID importJobId,
        String statusUrl
) {}
