package com.example.bi.service.analytics;

import com.example.bi.dto.Metric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KpiCalculatorTest {

    @Test
    @DisplayName("Fixture F1: gross margin calculates to 33.33%")
    void grossMargin_fixtureF1() {
        BigDecimal revenue = new BigDecimal("9000.00");
        BigDecimal grossProfit = new BigDecimal("3000.00");

        Metric margin = KpiCalculator.calculateGrossMarginPct(revenue, grossProfit);

        assertThat(margin.value()).isEqualTo("33.33");
        assertThat(margin.unavailableReason()).isNull();
    }

    @Test
    @DisplayName("Fixture F1: revenue growth calculates to -20.00% month-over-month")
    void revenueGrowth_fixtureF1() {
        BigDecimal current = new BigDecimal("4000.00");
        BigDecimal previous = new BigDecimal("5000.00");

        Metric growth = KpiCalculator.calculateRevenueGrowthPct(current, previous);

        assertThat(growth.value()).isEqualTo("-20.00");
        assertThat(growth.unavailableReason()).isNull();
    }

    @Test
    @DisplayName("Zero revenue returns unavailable with ZERO_REVENUE")
    void grossMargin_zeroRevenue() {
        Metric margin = KpiCalculator.calculateGrossMarginPct(BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(margin.value()).isNull();
        assertThat(margin.unavailableReason()).isEqualTo("ZERO_REVENUE");
    }

    @Test
    @DisplayName("Negative profit remains visible and calculates negative margin")
    void grossMargin_negativeProfit() {
        BigDecimal revenue = new BigDecimal("1000.00");
        BigDecimal grossProfit = new BigDecimal("-200.00");

        Metric margin = KpiCalculator.calculateGrossMarginPct(revenue, grossProfit);

        assertThat(margin.value()).isEqualTo("-20.00");
        assertThat(margin.unavailableReason()).isNull();
    }

    @Test
    @DisplayName("Rounding uses HALF_UP to 2 decimal places")
    void grossMargin_halfUpRounding() {
        // 100 / 6 = 16.6666... -> 16.67
        Metric margin = KpiCalculator.calculateGrossMarginPct(new BigDecimal("600.00"), new BigDecimal("100.00"));
        assertThat(margin.value()).isEqualTo("16.67");

        // 100 / 8 = 12.50
        Metric margin2 = KpiCalculator.calculateGrossMarginPct(new BigDecimal("800.00"), new BigDecimal("100.00"));
        assertThat(margin2.value()).isEqualTo("12.50");
    }

    @Test
    @DisplayName("Zero previous revenue returns unavailable with PREVIOUS_ZERO")
    void revenueGrowth_previousZero() {
        Metric growth = KpiCalculator.calculateRevenueGrowthPct(new BigDecimal("1000.00"), BigDecimal.ZERO);

        assertThat(growth.value()).isNull();
        assertThat(growth.unavailableReason()).isEqualTo("PREVIOUS_ZERO");
    }

    @Test
    @DisplayName("Null previous revenue returns unavailable with NOT_REQUESTED")
    void revenueGrowth_notRequested() {
        Metric growth = KpiCalculator.calculateRevenueGrowthPct(new BigDecimal("1000.00"), null);

        assertThat(growth.value()).isNull();
        assertThat(growth.unavailableReason()).isEqualTo("NOT_REQUESTED");
    }
}

