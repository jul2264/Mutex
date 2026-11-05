package com.example.bi.dto;

public record TrendPoint(
        String month,
        String revenue,
        String grossProfit,
        long unitsSold,
        Metric grossMarginPct,
        Metric revenueGrowthPct
) {}

