package com.example.bi.dto;

import java.util.List;
import java.util.UUID;

public record TrendsResponse(
        UUID datasetId,
        String currency,
        int dataVersion,
        List<TrendPoint> points
) {}

