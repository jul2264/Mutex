package com.example.bi.service.analytics;

import com.example.bi.domain.CompareMode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

@Component
public class PeriodResolver {

    public record ResolvedPeriod(
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            LocalDate effectiveToExclusive,
            LocalDate previousFrom,
            LocalDate previousTo,
            LocalDate previousToExclusive,
            boolean isComparing
    ) {}

    public ResolvedPeriod resolve(
            AnalyticsFilter filter,
            LocalDate datasetMinDate,
            LocalDate datasetMaxDate
    ) {
        LocalDate from = (filter != null) ? filter.from() : null;
        LocalDate to = (filter != null) ? filter.to() : null;

        LocalDate effectiveFrom = from != null ? from : datasetMinDate;
        LocalDate effectiveTo = to != null ? to : datasetMaxDate;

        if (effectiveFrom == null || effectiveTo == null) {
            return new ResolvedPeriod(effectiveFrom, effectiveTo, null, null, null, null, false);
        }

        LocalDate effectiveToExclusive = effectiveTo.plusDays(1);

        // Determine if comparison should be active
        boolean shouldCompare;
        if (filter != null && filter.compare() != null) {
            shouldCompare = filter.compare() == CompareMode.PREVIOUS_PERIOD;
        } else {
            // Default: PREVIOUS_PERIOD when both from and to are explicitly provided, otherwise NONE
            shouldCompare = (from != null && to != null);
        }

        if (!shouldCompare) {
            return new ResolvedPeriod(effectiveFrom, effectiveTo, effectiveToExclusive, null, null, null, false);
        }

        // BR-10 Comparison period calculation
        LocalDate prevFrom;
        LocalDate prevTo;

        if (isWholeCalendarMonths(effectiveFrom, effectiveTo)) {
            YearMonth startYm = YearMonth.from(effectiveFrom);
            YearMonth endYm = YearMonth.from(effectiveTo);
            long months = ChronoUnit.MONTHS.between(startYm, endYm) + 1;

            YearMonth prevEndYm = startYm.minusMonths(1);
            YearMonth prevStartYm = prevEndYm.minusMonths(months - 1);

            prevFrom = prevStartYm.atDay(1);
            prevTo = prevEndYm.atEndOfMonth();
        } else {
            long days = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1;
            prevTo = effectiveFrom.minusDays(1);
            prevFrom = prevTo.minusDays(days - 1);
        }

        LocalDate prevToExclusive = prevTo.plusDays(1);
        return new ResolvedPeriod(
                effectiveFrom,
                effectiveTo,
                effectiveToExclusive,
                prevFrom,
                prevTo,
                prevToExclusive,
                true
        );
    }

    public static boolean isWholeCalendarMonths(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return false;
        }
        return from.getDayOfMonth() == 1 && to.equals(to.with(TemporalAdjusters.lastDayOfMonth()));
    }
}

