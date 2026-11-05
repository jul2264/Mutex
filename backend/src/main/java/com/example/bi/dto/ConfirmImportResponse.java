package com.example.bi.dto;

import java.util.UUID;

public record ConfirmImportResponse(
        UUID datasetId,
        UUID importJobId,
        int importedRows,
        int dataVersion,
        String status
) {}

