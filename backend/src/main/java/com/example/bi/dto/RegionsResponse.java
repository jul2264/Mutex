package com.example.bi.dto;

import java.util.List;
import java.util.UUID;

public record RegionsResponse(
        UUID datasetId,
        String currency,
        int dataVersion,
        List<BreakdownRow> regions
) {}

