package com.example.bi.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DatasetResponse(
        UUID id,
        String name,
        String currencyCode,
        String status,
        int rowCount,
        LocalDate minOrderDate,
        LocalDate maxOrderDate,
        int dataVersion,
        Instant createdAt,
        Instant updatedAt
) {}

