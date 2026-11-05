package com.example.bi.dto;

import java.util.List;
import java.util.UUID;

public record TopProductsResponse(
        UUID datasetId,
        String currency,
        int dataVersion,
        List<TopProductRow> products
) {}

