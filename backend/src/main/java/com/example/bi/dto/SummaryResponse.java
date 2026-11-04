package com.example.bi.dto;

import java.util.UUID;

public record SummaryResponse(
        UUID datasetId,
        String currency,
        int dataVersion,
        PeriodDto period,
        String revenue,
        String grossProfit,
        long unitsSold,
        long orders,
        long lineItems,
        Metric grossMarginPct,
        Metric revenueGrowthPct,
        PreviousTotalsDto previous
) {}

