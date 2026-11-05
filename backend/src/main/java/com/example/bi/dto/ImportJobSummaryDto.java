package com.example.bi.dto;

import java.time.Instant;
import java.util.UUID;

public record ImportJobSummaryDto(
        UUID id,
        UUID datasetId,
        String status,
        String originalFilename,
        String policy,
        int totalRows,
        int validRows,
        int invalidRows,
        int duplicateRows,
        int importedRows,
        Instant createdAt,
        Instant completedAt
) {}

