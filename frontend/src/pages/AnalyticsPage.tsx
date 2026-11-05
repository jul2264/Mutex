import React, { useMemo } from 'react';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  fetchSummary,
  fetchTrends,
  fetchCategories,
  fetchRegions,
  fetchTopProducts,
  fetchFilterOptions,
} from '../api/analytics';
import { getDataset } from '../api/datasets';
import { parseFilterParams, serializeFilterParams } from '../lib/filters';
import type { AnalyticsFilterParams } from '../api/types';
import { useAuth } from '../hooks/useAuth';
import { formatCount, formatMoney, formatPercent } from '../lib/format';
import { FilterBar } from '../components/filters/FilterBar';
import { KpiCard } from '../components/kpi/KpiCard';
import { TrendChart } from '../components/charts/TrendChart';
import { CategoryChart } from '../components/charts/CategoryChart';
import { RegionChart } from '../components/charts/RegionChart';
import { TopProductsTable } from '../components/charts/TopProductsTable';
import {
  ArrowLeft,
  Settings,
  Download,
  AlertCircle,
  RotateCcw,
  Info,
} from 'lucide-react';

export const AnalyticsPage: React.FC = () => {
  const { datasetId } = useParams<{ datasetId?: string }>();
  const activeDatasetId = datasetId || '00000000-0000-0000-0000-000000000002'; // Seeded F1 fallback

  const [searchParams, setSearchParams] = useSearchParams();
  const filter = useMemo(() => parseFilterParams(searchParams), [searchParams]);

  const { user, logout, isLoggingOut } = useAuth();

  // Queries
  const { data: dataset } = useQuery({
    queryKey: ['dataset', activeDatasetId],
    queryFn: () => getDataset(activeDatasetId),
    enabled: Boolean(activeDatasetId),
  });

  const { data: filterOptions } = useQuery({
    queryKey: ['filterOptions', activeDatasetId],
    queryFn: ({ signal }) => fetchFilterOptions(activeDatasetId, signal),
    enabled: Boolean(activeDatasetId),
    staleTime: 5 * 60 * 1000,
  });

  const {
    data: summary,
    isLoading: isSummaryLoading,
    isPlaceholderData: isSummaryPlaceholder,
    isError: isSummaryError,
    error: summaryError,
    refetch: refetchSummary,
  } = useQuery({
    queryKey: ['summary', activeDatasetId, filter],
    queryFn: ({ signal }) => fetchSummary(activeDatasetId, filter, signal),
    enabled: Boolean(activeDatasetId),
    placeholderData: (prev) => prev,
  });

  const {
    data: trends,
    isLoading: isTrendsLoading,
    isPlaceholderData: isTrendsPlaceholder,
    refetch: refetchTrends,
  } = useQuery({
    queryKey: ['trends', activeDatasetId, filter],
    queryFn: ({ signal }) => fetchTrends(activeDatasetId, filter, signal),
    enabled: Boolean(activeDatasetId),
    placeholderData: (prev) => prev,
  });

  const {
    data: categoriesData,
    isLoading: isCategoriesLoading,
    isPlaceholderData: isCategoriesPlaceholder,
    refetch: refetchCategories,
  } = useQuery({
    queryKey: ['categories', activeDatasetId, filter],
    queryFn: ({ signal }) => fetchCategories(activeDatasetId, filter, signal),
    enabled: Boolean(activeDatasetId),
    placeholderData: (prev) => prev,
  });

  const {
    data: regionsData,
    isLoading: isRegionsLoading,
    isPlaceholderData: isRegionsPlaceholder,
    refetch: refetchRegions,
  } = useQuery({
    queryKey: ['regions', activeDatasetId, filter],
    queryFn: ({ signal }) => fetchRegions(activeDatasetId, filter, signal),
    enabled: Boolean(activeDatasetId),
    placeholderData: (prev) => prev,
  });

  const {
    data: topProductsData,
    isLoading: isTopProductsLoading,
    isPlaceholderData: isTopProductsPlaceholder,
    refetch: refetchTopProducts,
  } = useQuery({
    queryKey: ['topProducts', activeDatasetId, filter],
    queryFn: ({ signal }) => fetchTopProducts(activeDatasetId, filter, signal),
    enabled: Boolean(activeDatasetId),
    placeholderData: (prev) => prev,
  });

  const isUpdating =
    isSummaryPlaceholder ||
    isTrendsPlaceholder ||
    isCategoriesPlaceholder ||
    isRegionsPlaceholder ||
    isTopProductsPlaceholder;

  const handleFilterChange = (updated: Partial<AnalyticsFilterParams>) => {
    const nextFilter: AnalyticsFilterParams = {
      ...filter,
      ...updated,
    };
    const nextParams = serializeFilterParams(nextFilter);
    setSearchParams(nextParams, { replace: true });
  };

  const handleResetFilters = () => {
    setSearchParams(new URLSearchParams(), { replace: true });
  };

  const currency = summary?.currency || dataset?.currencyCode || 'INR';

  // Delta calculations when comparison is active
  const previousRev = summary?.previous?.revenue ? parseFloat(summary.previous.revenue) : null;
  const currentRev = summary?.revenue ? parseFloat(summary.revenue) : null;
  const revenueDelta =
    previousRev !== null && currentRev !== null
      ? formatMoney(String(currentRev - previousRev), currency)
      : null;

  const previousProfit = summary?.previous?.grossProfit ? parseFloat(summary.previous.grossProfit) : null;
  const currentProfit = summary?.grossProfit ? parseFloat(summary.grossProfit) : null;
  const profitDelta =
    previousProfit !== null && currentProfit !== null
      ? formatMoney(String(currentProfit - previousProfit), currency)
      : null;

  const previousUnits = summary?.previous?.unitsSold;
  const currentUnits = summary?.unitsSold;
  const unitsDelta =
    previousUnits !== undefined && currentUnits !== undefined
      ? formatCount(currentUnits - previousUnits)
      : null;

  const isEmpty = summary && summary.lineItems === 0;

  return (
    <div className="min-h-screen bg-[#f6f5f4] text-black">
      {/* Top Header */}
      <header className="border-b border-black/8 bg-[#f6f5f4] sticky top-0 z-20">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link to="/" className="font-bold text-xl tracking-tight text-black flex items-center gap-2">
              <span className="w-6 h-6 rounded bg-[#0075de] text-white text-xs font-bold flex items-center justify-center">
                μ
              </span>
              Mutex
            </Link>
            <span className="text-xs text-[#757575] border-l border-black/10 pl-3">
              Analytics Dashboard
            </span>
          </div>

          <div className="flex items-center gap-3">
            <span className="text-xs font-medium px-2.5 py-1 rounded-full bg-white border border-black/8 text-[#615d59]">
              Dataset: {dataset?.name || 'Sales Data'} ({currency})
            </span>

            <Link
              to={`/datasets/${activeDatasetId}/reports?${searchParams.toString()}`}
              className="px-3 py-1.5 rounded-lg border border-black/10 text-xs font-semibold text-[#37352f] hover:bg-black/5 flex items-center gap-1.5 transition-colors"
            >
              <Download className="w-3.5 h-3.5" /> Export
            </Link>

            <Link
              to={`/datasets/${activeDatasetId}`}
              className="p-1.5 rounded-lg border border-black/10 text-[#757575] hover:text-black hover:bg-black/5 transition-colors"
              title="Dataset Settings"
            >
              <Settings className="w-4 h-4" />
            </Link>

            {user && (
              <div className="flex items-center gap-2 border-l border-black/10 pl-3">
                <span className="text-xs text-[#37352f] font-medium hidden sm:inline">
                  {user.displayName}
                </span>
                <button
                  type="button"
                  onClick={() => logout()}
                  disabled={isLoggingOut}
                  className="text-xs text-[#757575] hover:text-black hover:bg-black/5 px-2 py-1 rounded transition-colors"
                >
                  {isLoggingOut ? 'Logging out...' : 'Sign out'}
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-6xl mx-auto px-6 py-8">
        {/* Page Title & Breadcrumb */}
        <div className="mb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <Link
              to="/"
              className="inline-flex items-center gap-1.5 text-xs text-[#757575] hover:text-black transition-colors mb-2"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              All Datasets
            </Link>
            <h1 className="text-2xl font-bold tracking-tight text-black">
              Key Performance Indicators
            </h1>
            <p className="text-xs text-[#757575] mt-1">
              {summary?.period
                ? `Active period: ${summary.period.from || 'Dataset start'} to ${summary.period.to || 'Dataset end'}`
                : 'Loading active date boundaries...'}
              {summary?.period?.previousFrom && (
                <span className="ml-1 text-[#0075de]">
                  (Comparing with {summary.period.previousFrom} to {summary.period.previousTo})
                </span>
              )}
            </p>
          </div>

          {summary && (
            <div className="text-xs text-[#757575] bg-white px-3 py-1.5 rounded-lg border border-black/8 self-start sm:self-auto flex items-center gap-2">
              <span>
                Orders: <strong className="text-black font-mono">{summary.orders.toLocaleString()}</strong>
              </span>
              <span>·</span>
              <span>
                Line Items: <strong className="text-black font-mono">{summary.lineItems.toLocaleString()}</strong>
              </span>
            </div>
          )}
        </div>

        {/* Global Filter Bar */}
        <FilterBar
          filter={filter}
          options={filterOptions}
          onChange={handleFilterChange}
          onReset={handleResetFilters}
          isUpdating={isUpdating}
        />

        {/* Error Banner */}
        {isSummaryError && (
          <div className="mb-6 p-4 rounded-xl bg-red-50 border border-red-200 text-red-800 text-sm flex items-center justify-between">
            <div className="flex items-center gap-3">
              <AlertCircle className="w-5 h-5 text-red-600 shrink-0" />
              <span>
                Failed to load analytics: {summaryError instanceof Error ? summaryError.message : 'Network error'}
              </span>
            </div>
            <button
              type="button"
              onClick={() => {
                refetchSummary();
                refetchTrends();
                refetchCategories();
                refetchRegions();
                refetchTopProducts();
              }}
              className="px-3 py-1 bg-red-600 text-white rounded-md text-xs font-semibold hover:bg-red-700"
            >
              Retry
            </button>
          </div>
        )}

        {/* Empty State Banner when no rows match active filters */}
        {isEmpty ? (
          <div className="bg-white rounded-xl border border-black/8 shadow-xs p-12 text-center my-6">
            <AlertCircle className="w-10 h-10 text-amber-500 mx-auto mb-3" />
            <h2 className="text-lg font-bold text-black mb-1">No sales records match these filters</h2>
            <p className="text-xs text-[#757575] max-w-md mx-auto mb-4">
              Try expanding your date range, clearing category or region filters, or resetting your search term.
            </p>
            <button
              type="button"
              onClick={handleResetFilters}
              className="px-4 py-2 bg-[#0075de] hover:bg-[#0060b8] text-white text-xs font-semibold rounded-lg shadow-xs inline-flex items-center gap-2 cursor-pointer"
            >
              <RotateCcw className="w-3.5 h-3.5" /> Reset Filters
            </button>
          </div>
        ) : (
          /* Main Dashboard Visuals */
          <div className={`space-y-6 transition-opacity duration-200 ${isUpdating ? 'opacity-60' : 'opacity-100'}`}>
            {/* 4 Core KPI Cards Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <KpiCard
                title="Total Revenue"
                value={formatMoney(summary?.revenue, currency)}
                subtitle="Gross list revenue"
                growth={summary?.revenueGrowthPct}
                deltaText={revenueDelta ? `${revenueDelta} vs prev` : undefined}
                isLoading={isSummaryLoading}
              />

              <KpiCard
                title="Gross Profit"
                value={formatMoney(summary?.grossProfit, currency)}
                subtitle="Revenue minus cost of goods"
                deltaText={profitDelta ? `${profitDelta} vs prev` : undefined}
                isLoading={isSummaryLoading}
              />

              <KpiCard
                title="Gross Margin %"
                value={summary?.grossMarginPct?.value ? formatPercent(summary.grossMarginPct.value) : undefined}
                subtitle="Gross profit ÷ revenue × 100"
                metric={summary?.grossMarginPct}
                isLoading={isSummaryLoading}
              />

              <KpiCard
                title="Units Sold"
                value={summary?.unitsSold !== undefined ? summary.unitsSold.toLocaleString() : undefined}
                subtitle="Total items sold"
                deltaText={unitsDelta ? `${unitsDelta} vs prev` : undefined}
                isLoading={isSummaryLoading}
              />
            </div>

            {/* Monthly Trend Chart */}
            <TrendChart
              points={trends?.points || []}
              currency={currency}
              isLoading={isTrendsLoading}
            />

            {/* Category & Region Breakdowns Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <CategoryChart
                categories={categoriesData?.categories || []}
                currency={currency}
                isLoading={isCategoriesLoading}
              />

              <RegionChart
                regions={regionsData?.regions || []}
                currency={currency}
                isLoading={isRegionsLoading}
              />
            </div>

            {/* Top Products Dual Ranking Table */}
            <TopProductsTable
              products={topProductsData?.products || []}
              currency={currency}
              rankBy={filter.rankBy || 'REVENUE'}
              onRankByChange={(rankBy) => handleFilterChange({ rankBy })}
              isLoading={isTopProductsLoading}
            />

            {/* Footnotes & Methodological Disclaimers (BR-06) */}
            <div className="pt-4 border-t border-black/8 text-[11px] text-[#757575] flex items-start gap-2">
              <Info className="w-3.5 h-3.5 shrink-0 mt-0.5 text-[#757575]" />
              <div>
                <strong>Accounting Footnote (BR-06):</strong> Revenue is calculated as{' '}
                <code className="font-mono bg-black/5 px-1 py-0.5 rounded">quantity × list unit price</code>, before tax and discounts.
                Gross profit equals{' '}
                <code className="font-mono bg-black/5 px-1 py-0.5 rounded">quantity × (unit price − unit cost)</code>. Negative line profits remain visible and reduce totals.
                All figures displayed in <strong className="text-black">{currency}</strong>.
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};
