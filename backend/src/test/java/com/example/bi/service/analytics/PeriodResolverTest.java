package com.example.bi.service.analytics;

import com.example.bi.domain.CompareMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PeriodResolverTest {

    private PeriodResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PeriodResolver();
    }

    @Test
    @DisplayName("Whole single month (Feb 2026): previous period is Jan 2026 (whole month) - F1 oracle")
    void wholeMonth_previousIsPrecedingMonth() {
        AnalyticsFilter filter = new AnalyticsFilter(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                null, null, null, null
        );

        PeriodResolver.ResolvedPeriod period = resolver.resolve(filter, null, null);

        assertTrue(period.isComparing());
        assertEquals(LocalDate.of(2026, 2, 1), period.effectiveFrom());
        assertEquals(LocalDate.of(2026, 2, 28), period.effectiveTo());
        assertEquals(LocalDate.of(2026, 3, 1), period.effectiveToExclusive());

        assertEquals(LocalDate.of(2026, 1, 1), period.previousFrom());
        assertEquals(LocalDate.of(2026, 1, 31), period.previousTo());
        assertEquals(LocalDate.of(2026, 2, 1), period.previousToExclusive());
    }

    @Test
    @DisplayName("Whole multi-month (Jan to March 2026): previous period is Oct to Dec 2025")
    void wholeMultiMonth_spansPastYearBoundary() {
        AnalyticsFilter filter = new AnalyticsFilter(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                null, null, null, CompareMode.PREVIOUS_PERIOD
        );

        PeriodResolver.ResolvedPeriod period = resolver.resolve(filter, null, null);

        assertTrue(period.isComparing());
        assertEquals(LocalDate.of(2025, 10, 1), period.previousFrom());
        assertEquals(LocalDate.of(2025, 12, 31), period.previousTo());
    }

    @Test
    @DisplayName("Leap February (Feb 2024): previous period is Jan 2024")
    void leapFebruary_recognizesWholeMonth() {
        AnalyticsFilter filter = new AnalyticsFilter(
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 29),
                null, null, null, CompareMode.PREVIOUS_PERIOD
        );

        PeriodResolver.ResolvedPeriod period = resolver.resolve(filter, null, null);

        assertTrue(period.isComparing());
        assertEquals(LocalDate.of(2024, 1, 1), period.previousFrom());
        assertEquals(LocalDate.of(2024, 1, 31), period.previousTo());
    }

    @Test
    @DisplayName("Single day (2026-02-10): previous period is 2026-02-09")
    void singleDay_previousIsImmediatelyPrecedingDay() {
        AnalyticsFilter filter = new AnalyticsFilter(
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 2, 10),
                null, null, null, CompareMode.PREVIOUS_PERIOD
        );

        PeriodResolver.ResolvedPeriod period = resolver.resolve(filter, null, null);

        assertTrue(period.isComparing());
        assertEquals(LocalDate.of(2026, 2, 9), period.previousFrom());
        assertEquals(LocalDate.of(2026, 2, 9), period.previousTo());
    }

    @Test
    @DisplayName("Partial range (2026-01-15 to 2026-02-10, 27 days): previous period is 27 days before 2026-01-15")
    void partialRange_usesDayCount() {
        AnalyticsFilter filter = new AnalyticsFilter(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 2, 10),
                null, null, null, CompareMode.PREVIOUS_PERIOD
        );

        PeriodResolver.ResolvedPeriod period = resolver.resolve(filter, null, null);

        assertTrue(period.isComparing());
        assertEquals(LocalDate.of(2026, 1, 14), period.previousTo());
        // 27 days inclusive: 2026-01-14 minus 26 days = 2025-12-19
        assertEquals(LocalDate.of(2025, 12, 19), period.previousFrom());
    }

    @Test
    @DisplayName("Defaults to dataset min/max date when filter bounds are omitted; compare is NONE by default")
    void omittedBounds_defaultsToDatasetDates_compareNone() {
        AnalyticsFilter filter = AnalyticsFilter.empty();
        LocalDate minDate = LocalDate.of(2026, 1, 15);
        LocalDate maxDate = LocalDate.of(2026, 2, 10);

        PeriodResolver.ResolvedPeriod period = resolver.resolve(filter, minDate, maxDate);

        assertFalse(period.isComparing());
        assertEquals(minDate, period.effectiveFrom());
        assertEquals(maxDate, period.effectiveTo());
        assertEquals(LocalDate.of(2026, 2, 11), period.effectiveToExclusive());
        assertNull(period.previousFrom());
        assertNull(period.previousTo());
    }
}

