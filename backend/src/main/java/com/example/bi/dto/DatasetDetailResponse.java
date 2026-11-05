package com.example.bi.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DatasetDetailResponse(
        UUID id,
        String name,
        String currencyCode,
        String status,
        int rowCount,
        LocalDate minOrderDate,
        LocalDate maxOrderDate,
        int dataVersion,
        Instant createdAt,
        Instant updatedAt,
        ImportJobSummaryDto latestImport,
        List<ImportJobSummaryDto> importHistory
) {}

