import React from 'react';
import { useParams } from 'react-router-dom';
import { useSummary } from '../hooks/useSummary';
import { useAuth } from '../hooks/useAuth';
import { KpiCard } from '../components/kpi/KpiCard';
import { formatCount, formatMoney, formatPercent } from '../lib/format';

interface AnalyticsPageProps {
  datasetId?: string;
}

export const AnalyticsPage: React.FC<AnalyticsPageProps> = ({
  datasetId: propDatasetId,
}) => {
  const { datasetId: paramDatasetId } = useParams<{ datasetId?: string }>();
  const datasetId = propDatasetId || paramDatasetId || '00000000-0000-0000-0000-000000000002'; // Seeded F1 dataset

  const { user, logout, isLoggingOut } = useAuth();
  const { data, isLoading, isError, error, refetch } = useSummary(datasetId);

  return (
    <div className="min-h-screen bg-[#f6f5f4] text-black">
      {/* Top Navigation */}
      <header className="border-b border-black/8 bg-[#f6f5f4] sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="font-bold text-xl tracking-tight text-black flex items-center gap-2">
              <span className="inline-block w-6 h-6 rounded bg-[#0075de] text-white text-xs font-bold flex items-center justify-center">
                μ
              </span>
              Mutex
            </span>
            <span className="text-xs text-[#757575] border-l border-black/10 pl-3">
              Sales Analytics
            </span>
          </div>

          <div className="flex items-center gap-3">
            <span className="text-xs font-medium px-2.5 py-1 rounded-full bg-white border border-black/8 text-[#615d59]">
              Dataset: F1 Sample Sales
            </span>

            {user && (
              <div className="flex items-center gap-2 border-l border-black/10 pl-3">
                <span className="text-xs text-[#37352f] font-medium hidden sm:inline">
                  {user.displayName}
                </span>
                <button
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

      {/* Main Content */}
      <main className="max-w-6xl mx-auto px-6 py-8">
        <div className="mb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-black">
              Key Performance Indicators
            </h1>
            <p className="text-sm text-[#757575] mt-1">
              Verified dataset metrics ({data?.period?.from ?? '2026-01-15'} to {data?.period?.to ?? '2026-02-10'})
            </p>
          </div>

          {data && (
            <div className="text-xs text-[#757575] bg-white px-3 py-1.5 rounded-lg border border-black/8">
              Orders: <strong className="text-black">{data.orders}</strong> · Line Items: <strong className="text-black">{data.lineItems}</strong>
            </div>
          )}
        </div>

        {/* 4 Core KPI Cards Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <KpiCard
            title="Total Revenue"
            value={formatMoney(data?.revenue, data?.currency)}
            subtitle="Gross list revenue"
            isLoading={isLoading}
            isError={isError}
            errorMessage={error instanceof Error ? error.message : undefined}
            onRetry={() => refetch()}
          />

          <KpiCard
            title="Gross Profit"
            value={formatMoney(data?.grossProfit, data?.currency)}
            subtitle="Revenue minus cost of goods"
            isLoading={isLoading}
            isError={isError}
            errorMessage={error instanceof Error ? error.message : undefined}
            onRetry={() => refetch()}
          />

          <KpiCard
            title="Gross Margin"
            value={formatPercent(data?.grossMarginPct?.value)}
            unavailableReason={data?.grossMarginPct?.unavailableReason}
            subtitle="Profit share of revenue"
            isLoading={isLoading}
            isError={isError}
            errorMessage={error instanceof Error ? error.message : undefined}
            onRetry={() => refetch()}
          />

          <KpiCard
            title="Units Sold"
            value={formatCount(data?.unitsSold)}
            subtitle="Total units across orders"
            isLoading={isLoading}
            isError={isError}
            errorMessage={error instanceof Error ? error.message : undefined}
            onRetry={() => refetch()}
          />
        </div>

        {/* Compliance Footnote */}
        <div className="mt-8 pt-4 border-t border-black/8 text-xs text-[#757575]">
          Revenue = quantity × unit price, before tax and discounts (BR-06). Currency: {data?.currency ?? 'INR'}.
        </div>
      </main>
    </div>
  );
};

