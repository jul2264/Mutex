package com.example.bi.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConfirmImportRequest(
        @NotNull(message = "Import job ID is required")
        UUID importJobId,
        boolean includeDuplicates
) {}

