package com.example.bi.service.analytics;

import com.example.bi.exception.InvalidDateRangeException;
import com.example.bi.exception.InvalidFilterParameterException;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

@Component
public class AnalyticsFilterValidator {

    public void validate(AnalyticsFilter filter) {
        if (filter == null) {
            return;
        }

        if (filter.from() != null && filter.to() != null) {
            if (filter.from().isAfter(filter.to())) {
                throw new InvalidDateRangeException("Start date ('from') must not be after end date ('to')");
            }

            long daysBetween = ChronoUnit.DAYS.between(filter.from(), filter.to());
            // 10 years ~ 3653 days
            if (daysBetween > 3653) {
                throw new InvalidDateRangeException("Date range cannot exceed 10 years");
            }
        }

        if (filter.categories() != null) {
            if (filter.categories().size() > 50) {
                throw new InvalidFilterParameterException("Cannot filter by more than 50 categories");
            }
            for (String cat : filter.categories()) {
                if (cat != null && cat.length() > 100) {
                    throw new InvalidFilterParameterException("Category filter item exceeds 100 characters");
                }
            }
        }

        if (filter.regions() != null) {
            if (filter.regions().size() > 50) {
                throw new InvalidFilterParameterException("Cannot filter by more than 50 regions");
            }
            for (String reg : filter.regions()) {
                if (reg != null && reg.length() > 100) {
                    throw new InvalidFilterParameterException("Region filter item exceeds 100 characters");
                }
            }
        }

        if (filter.product() != null && filter.product().length() > 100) {
            throw new InvalidFilterParameterException("Product search string exceeds 100 characters");
        }
    }
}

