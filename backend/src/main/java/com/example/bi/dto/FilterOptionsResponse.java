package com.example.bi.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FilterOptionsResponse(
        UUID datasetId,
        List<String> categories,
        List<String> regions,
        LocalDate minOrderDate,
        LocalDate maxOrderDate,
        int totalProducts,
        boolean categoriesTruncated,
        boolean regionsTruncated
) {}

