package com.example.bi.service.analytics;

import com.example.bi.domain.Dataset;
import com.example.bi.domain.RankBy;
import com.example.bi.dto.*;
import com.example.bi.repository.AnalyticsRepository;
import com.example.bi.service.identity.DatasetAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final DatasetAccessService datasetAccessService;
    private final AnalyticsRepository analyticsRepository;
    private final AnalyticsFilterValidator filterValidator;
    private final PeriodResolver periodResolver;

    public AnalyticsService(
            DatasetAccessService datasetAccessService,
            AnalyticsRepository analyticsRepository,
            AnalyticsFilterValidator filterValidator,
            PeriodResolver periodResolver
    ) {
        this.datasetAccessService = datasetAccessService;
        this.analyticsRepository = analyticsRepository;
        this.filterValidator = filterValidator;
        this.periodResolver = periodResolver;
    }

    public SummaryResponse getSummary(UUID datasetId, AnalyticsFilter filter) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        filterValidator.validate(filter);

        PeriodResolver.ResolvedPeriod period = periodResolver.resolve(
                filter, dataset.getMinOrderDate(), dataset.getMaxOrderDate()
        );

        SalesTotals current = analyticsRepository.totals(
                datasetId,
                period.effectiveFrom(),
                period.effectiveToExclusive(),
                filter != null ? filter.categories() : null,
                filter != null ? filter.regions() : null,
                filter != null ? filter.product() : null
        );

        Metric margin = KpiCalculator.calculateGrossMarginPct(current.revenue(), current.grossProfit());
        Metric growth;
        PreviousTotalsDto previousDto = null;

        if (period.isComparing()) {
            SalesTotals prev = analyticsRepository.totals(
                    datasetId,
                    period.previousFrom(),
                    period.previousToExclusive(),
                    filter != null ? filter.categories() : null,
                    filter != null ? filter.regions() : null,
                    filter != null ? filter.product() : null
            );

            previousDto = new PreviousTotalsDto(
                    formatMoney(prev.revenue()),
                    formatMoney(prev.grossProfit()),
                    prev.unitsSold()
            );
            growth = KpiCalculator.calculateRevenueGrowthPct(current.revenue(), prev.revenue());
        } else {
            growth = KpiCalculator.calculateRevenueGrowthPct(current.revenue(), null);
        }

        PeriodDto periodDto = new PeriodDto(
                period.effectiveFrom(),
                period.effectiveTo(),
                period.previousFrom(),
                period.previousTo()
        );

        return new SummaryResponse(
                dataset.getId(),
                dataset.getCurrencyCode(),
                dataset.getDataVersion(),
                periodDto,
                formatMoney(current.revenue()),
                formatMoney(current.grossProfit()),
                current.unitsSold(),
                current.orders(),
                current.lineItems(),
                margin,
                growth,
                previousDto
        );
    }

    public TrendsResponse getTrends(UUID datasetId, AnalyticsFilter filter) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        filterValidator.validate(filter);

        PeriodResolver.ResolvedPeriod period = periodResolver.resolve(
                filter, dataset.getMinOrderDate(), dataset.getMaxOrderDate()
        );

        List<AnalyticsRepository.TrendRowData> rows = analyticsRepository.monthlyTrends(
                datasetId,
                period.effectiveFrom(),
                period.effectiveTo(),
                period.effectiveToExclusive(),
                filter != null ? filter.categories() : null,
                filter != null ? filter.regions() : null,
                filter != null ? filter.product() : null
        );

        List<TrendPoint> points = new ArrayList<>();
        for (AnalyticsRepository.TrendRowData r : rows) {
            Metric margin = KpiCalculator.calculateGrossMarginPct(r.revenue(), r.grossProfit());
            Metric growth = (r.prevRevenue() != null)
                    ? KpiCalculator.calculateRevenueGrowthPct(r.revenue(), r.prevRevenue())
                    : Metric.unavailable("NOT_REQUESTED");

            points.add(new TrendPoint(
                    r.month(),
                    formatMoney(r.revenue()),
                    formatMoney(r.grossProfit()),
                    r.unitsSold(),
                    margin,
                    growth
            ));
        }

        return new TrendsResponse(dataset.getId(), dataset.getCurrencyCode(), dataset.getDataVersion(), points);
    }

    public CategoriesResponse getCategories(
            UUID datasetId,
            AnalyticsFilter filter,
            String sort,
            String direction
    ) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        filterValidator.validate(filter);

        PeriodResolver.ResolvedPeriod period = periodResolver.resolve(
                filter, dataset.getMinOrderDate(), dataset.getMaxOrderDate()
        );

        SalesTotals total = analyticsRepository.totals(
                datasetId,
                period.effectiveFrom(),
                period.effectiveToExclusive(),
                filter != null ? filter.categories() : null,
                filter != null ? filter.regions() : null,
                filter != null ? filter.product() : null
        );

        List<AnalyticsRepository.BreakdownRowData> rows = analyticsRepository.categoryBreakdown(
                datasetId,
                period.effectiveFrom(),
                period.effectiveToExclusive(),
                filter != null ? filter.categories() : null,
                filter != null ? filter.regions() : null,
                filter != null ? filter.product() : null,
                sort,
                direction
        );

        List<BreakdownRow> result = new ArrayList<>();
        for (AnalyticsRepository.BreakdownRowData r : rows) {
            String share = calculateSharePct(r.revenue(), total.revenue());
            Metric margin = KpiCalculator.calculateGrossMarginPct(r.revenue(), r.grossProfit());

            result.add(new BreakdownRow(
                    r.name(),
                    formatMoney(r.revenue()),
                    formatMoney(r.grossProfit()),
                    r.unitsSold(),
                    share,
                    margin,
                    null,
                    null
            ));
        }

        return new CategoriesResponse(dataset.getId(), dataset.getCurrencyCode(), dataset.getDataVersion(), result);
    }

    public RegionsResponse getRegions(
            UUID datasetId,
            AnalyticsFilter filter,
            String sort,
            String direction
    ) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        filterValidator.validate(filter);

        PeriodResolver.ResolvedPeriod period = periodResolver.resolve(
                filter, dataset.getMinOrderDate(), dataset.getMaxOrderDate()
        );

        SalesTotals total = analyticsRepository.totals(
                datasetId,
                period.effectiveFrom(),
                period.effectiveToExclusive(),
                filter != null ? filter.categories() : null,
                filter != null ? filter.regions() : null,
                filter != null ? filter.product() : null
        );

        Metric overallMarginMetric = KpiCalculator.calculateGrossMarginPct(total.revenue(), total.grossProfit());
        BigDecimal overallMargin = (overallMarginMetric.value() != null)
                ? new BigDecimal(overallMarginMetric.value())
                : null;

        List<AnalyticsRepository.BreakdownRowData> rows = analyticsRepository.regionBreakdown(
                datasetId,
                period.effectiveFrom(),
                period.effectiveToExclusive(),
                filter != null ? filter.categories() : null,
                filter != null ? filter.regions() : null,
                filter != null ? filter.product() : null,
                sort,
                direction,
                period.previousFrom(),
                period.previousToExclusive()
        );

        List<BreakdownRow> result = new ArrayList<>();
        for (AnalyticsRepository.BreakdownRowData r : rows) {
            String share = calculateSharePct(r.revenue(), total.revenue());
            Metric regionMarginMetric = KpiCalculator.calculateGrossMarginPct(r.revenue(), r.grossProfit());

            String marginDelta = null;
            if (overallMargin != null && regionMarginMetric.value() != null) {
                BigDecimal regionMargin = new BigDecimal(regionMarginMetric.value());
                marginDelta = regionMargin.subtract(overallMargin).setScale(2, RoundingMode.HALF_UP).toPlainString();
            }

            Metric growth = null;
            if (period.isComparing()) {
                growth = KpiCalculator.calculateRevenueGrowthPct(r.revenue(), r.prevRevenue());
            }

            result.add(new BreakdownRow(
                    r.name(),
                    formatMoney(r.revenue()),
                    formatMoney(r.grossProfit()),
                    r.unitsSold(),
                    share,
                    regionMarginMetric,
                    marginDelta,
                    growth
            ));
        }

        return new RegionsResponse(dataset.getId(), dataset.getCurrencyCode(), dataset.getDataVersion(), result);
    }

    public TopProductsResponse getTopProducts(
            UUID datasetId,
            AnalyticsFilter filter,
            Integer limit,
            RankBy rankBy
    ) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        filterValidator.validate(filter);

        PeriodResolver.ResolvedPeriod period = periodResolver.resolve(
                filter, dataset.getMinOrderDate(), dataset.getMaxOrderDate()
        );

        int effectiveLimit = (limit != null) ? Math.clamp(limit, 1, 50) : 10;
        RankBy effectiveRankBy = (rankBy != null) ? rankBy : RankBy.REVENUE;

        List<AnalyticsRepository.TopProductRowData> rows = analyticsRepository.topProducts(
                datasetId,
                period.effectiveFrom(),
                period.effectiveToExclusive(),
                filter != null ? filter.categories() : null,
                filter != null ? filter.regions() : null,
                filter != null ? filter.product() : null,
                effectiveLimit,
                effectiveRankBy
        );

        List<TopProductRow> result = rows.stream()
                .map(r -> new TopProductRow(
                        r.product(),
                        formatMoney(r.revenue()),
                        formatMoney(r.grossProfit()),
                        r.unitsSold(),
                        r.revenueRank(),
                        r.profitRank()
                ))
                .toList();

        return new TopProductsResponse(dataset.getId(), dataset.getCurrencyCode(), dataset.getDataVersion(), result);
    }

    public FilterOptionsResponse getFilterOptions(UUID datasetId) {
        datasetAccessService.requireOwned(datasetId);
        AnalyticsRepository.FilterOptionsData data = analyticsRepository.filterOptions(datasetId);

        return new FilterOptionsResponse(
                datasetId,
                data.categories(),
                data.regions(),
                data.minOrderDate(),
                data.maxOrderDate(),
                data.totalProducts(),
                data.categoriesTruncated(),
                data.regionsTruncated()
        );
    }

    private String calculateSharePct(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0 || part == null) {
            return "0.00";
        }
        return part.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
