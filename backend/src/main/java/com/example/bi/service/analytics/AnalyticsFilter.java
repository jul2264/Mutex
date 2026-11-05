package com.example.bi.service.analytics;

import com.example.bi.domain.CompareMode;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsFilter(
        LocalDate from,
        LocalDate to,
        List<String> categories,
        List<String> regions,
        String product,
        CompareMode compare
) {
    public static AnalyticsFilter empty() {
        return new AnalyticsFilter(null, null, null, null, null, CompareMode.NONE);
    }
}

