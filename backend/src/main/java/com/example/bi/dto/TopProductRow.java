package com.example.bi.dto;

public record TopProductRow(
        String product,
        String revenue,
        String grossProfit,
        long unitsSold,
        int revenueRank,
        int profitRank
) {}

