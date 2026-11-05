package com.example.bi.dto;

import java.time.Instant;
import java.util.UUID;

public record ImportJobStatusResponse(
        UUID id,
        UUID datasetId,
        String status,
        String policy,
        String fileName,
        int rowsProcessed,
        int totalRows,
        int validRows,
        int invalidRows,
        int duplicateRows,
        int warningCount,
        int blankRowsSkipped,
        boolean issuesTruncated,
        boolean canConfirm,
        String confirmBlockedReason,
        FileErrorDto fileError,
        Instant expiresAt
) {}

