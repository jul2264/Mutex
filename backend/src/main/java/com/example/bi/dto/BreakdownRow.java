package com.example.bi.dto;

public record BreakdownRow(
        String name,
        String revenue,
        String grossProfit,
        long unitsSold,
        String revenueSharePct,
        Metric grossMarginPct,
        String marginDeltaPoints,
        Metric revenueGrowthPct
) {}

