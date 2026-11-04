package com.example.bi.service.analytics;

import com.example.bi.domain.Dataset;
import com.example.bi.dto.*;
import com.example.bi.repository.AnalyticsRepository;
import com.example.bi.service.identity.DatasetAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final DatasetAccessService datasetAccessService;
    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(DatasetAccessService datasetAccessService, AnalyticsRepository analyticsRepository) {
        this.datasetAccessService = datasetAccessService;
        this.analyticsRepository = analyticsRepository;
    }

    public SummaryResponse getSummary(UUID datasetId, LocalDate from, LocalDate to) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);

        LocalDate effectiveFrom = from != null ? from : dataset.getMinOrderDate();
        LocalDate effectiveTo = to != null ? to : dataset.getMaxOrderDate();
        LocalDate effectiveToExclusive = effectiveTo != null ? effectiveTo.plusDays(1) : null;

        SalesTotals currentTotals = analyticsRepository.totals(datasetId, effectiveFrom, effectiveToExclusive);

        Metric margin = KpiCalculator.calculateGrossMarginPct(currentTotals.revenue(), currentTotals.grossProfit());
        Metric growth = KpiCalculator.calculateRevenueGrowthPct(currentTotals.revenue(), null);

        PeriodDto period = new PeriodDto(effectiveFrom, effectiveTo, null, null);

        return new SummaryResponse(
                dataset.getId(),
                dataset.getCurrencyCode(),
                dataset.getDataVersion(),
                period,
                formatMoney(currentTotals.revenue()),
                formatMoney(currentTotals.grossProfit()),
                currentTotals.unitsSold(),
                currentTotals.orders(),
                currentTotals.lineItems(),
                margin,
                growth,
                null
        );
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

