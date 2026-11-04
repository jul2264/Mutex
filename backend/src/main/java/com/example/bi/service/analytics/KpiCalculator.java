package com.example.bi.service.analytics;

import com.example.bi.dto.Metric;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class KpiCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private KpiCalculator() {}

    public static Metric calculateGrossMarginPct(BigDecimal revenue, BigDecimal grossProfit) {
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) == 0) {
            return Metric.unavailable("ZERO_REVENUE");
        }
        if (grossProfit == null) {
            grossProfit = BigDecimal.ZERO;
        }
        BigDecimal margin = grossProfit.multiply(HUNDRED).divide(revenue, 2, RoundingMode.HALF_UP);
        return Metric.of(margin.toPlainString());
    }

    public static Metric calculateRevenueGrowthPct(BigDecimal currentRevenue, BigDecimal previousRevenue) {
        if (previousRevenue == null) {
            return Metric.unavailable("NOT_REQUESTED");
        }
        if (previousRevenue.compareTo(BigDecimal.ZERO) == 0) {
            return Metric.unavailable("PREVIOUS_ZERO");
        }
        if (currentRevenue == null) {
            currentRevenue = BigDecimal.ZERO;
        }
        BigDecimal growth = currentRevenue.subtract(previousRevenue)
                .multiply(HUNDRED)
                .divide(previousRevenue, 2, RoundingMode.HALF_UP);
        return Metric.of(growth.toPlainString());
    }
}

