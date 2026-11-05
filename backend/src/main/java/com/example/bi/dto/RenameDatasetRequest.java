package com.example.bi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameDatasetRequest(
        @NotBlank(message = "Dataset name cannot be blank")
        @Size(min = 1, max = 100, message = "Dataset name must be between 1 and 100 characters")
        String name
) {}

