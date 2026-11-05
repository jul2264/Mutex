package com.example.bi.dto;

import java.util.List;
import java.util.UUID;

public record CategoriesResponse(
        UUID datasetId,
        String currency,
        int dataVersion,
        List<BreakdownRow> categories
) {}

