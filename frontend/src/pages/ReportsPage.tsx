import React from 'react';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getDataset } from '../api/datasets';
import { fetchFilterOptions } from '../api/analytics';
import { parseFilterParams, serializeFilterParams } from '../lib/filters';
import { useAuth } from '../hooks/useAuth';
import { ExportPanel } from '../components/reports/ExportPanel';
import { FilterBar } from '../components/filters/FilterBar';
import {
  ArrowLeft,
  BarChart3,
} from 'lucide-react';
import type { AnalyticsFilterParams } from '../api/types';

export const ReportsPage: React.FC = () => {
  const { datasetId } = useParams<{ datasetId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user, logout, isLoggingOut } = useAuth();

  const filter = parseFilterParams(searchParams);

  const { data: dataset } = useQuery({
    queryKey: ['dataset', datasetId],
    queryFn: () => {
      if (!datasetId) throw new Error('No dataset ID');
      return getDataset(datasetId);
    },
    enabled: Boolean(datasetId),
  });

  const { data: filterOptions, isFetching: isFetchingOptions } = useQuery({
    queryKey: ['filterOptions', datasetId],
    queryFn: () => {
      if (!datasetId) throw new Error('No dataset ID');
      return fetchFilterOptions(datasetId);
    },
    enabled: Boolean(datasetId),
  });

  const handleFilterChange = (updated: Partial<AnalyticsFilterParams>) => {
    const merged: AnalyticsFilterParams = { ...filter, ...updated };
    setSearchParams(serializeFilterParams(merged), { replace: true });
  };

  const handleResetFilters = () => {
    setSearchParams(serializeFilterParams({}), { replace: true });
  };

  return (
    <div className="min-h-screen bg-[#f6f5f4] text-black">
      {/* Top Navigation Header */}
      <header className="border-b border-black/10 bg-white sticky top-0 z-30 shadow-sm">
        <div className="max-w-5xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Link to="/" className="font-bold text-2xl tracking-tight text-black flex items-center gap-3 hover:opacity-85 transition-opacity">
              <span className="w-10 h-10 rounded-xl bg-[#0075de] text-white text-base font-extrabold flex items-center justify-center shadow-sm">
                μ
              </span>
              Mutex
            </Link>
            <span className="text-sm font-semibold text-[#555] border-l-2 border-black/10 pl-4 py-0.5">
              Export Center
            </span>
          </div>

          <div className="flex items-center gap-4">
            {user && (
              <div className="flex items-center gap-3">
                <span className="text-sm text-[#111] font-semibold hidden sm:inline">
                  {user.displayName}
                </span>
                <button
                  onClick={() => logout()}
                  disabled={isLoggingOut}
                  className="text-xs text-[#666] hover:text-red-700 hover:bg-red-50 px-3 py-1.5 rounded-lg border border-black/10 font-semibold transition-colors cursor-pointer"
                >
                  {isLoggingOut ? 'Logging out...' : 'Sign out'}
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-4xl mx-auto px-6 py-8">
        <div className="mb-6">
          <Link
            to={datasetId ? `/datasets/${datasetId}/analytics?${searchParams.toString()}` : '/'}
            className="inline-flex items-center gap-1.5 text-xs text-[#757575] hover:text-black transition-colors mb-3"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            Back to Analytics Dashboard
          </Link>
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-black">
                Export Verified Data &amp; Reports
              </h1>
              <p className="text-sm text-[#757575] mt-1">
                Stream row-level transaction records or download full multi-sheet analytical workbooks.
              </p>
            </div>
            {datasetId && (
              <Link
                to={`/datasets/${datasetId}/analytics?${searchParams.toString()}`}
                className="px-3.5 py-2 rounded-lg border border-black/10 text-xs font-semibold text-[#37352f] hover:bg-black/5 flex items-center gap-1.5 shrink-0 self-start sm:self-auto transition-colors"
              >
                <BarChart3 className="w-4 h-4 text-[#0075de]" />
                <span>View Dashboard</span>
              </Link>
            )}
          </div>
        </div>

        {/* Dataset Header Card */}
        <div className="bg-white rounded-xl border border-black/8 shadow-xs p-5 mb-6 flex items-center justify-between">
          <div>
            <span className="text-[10px] font-semibold text-[#757575] uppercase tracking-wider block mb-0.5">
              Source Dataset
            </span>
            <h2 className="text-base font-bold text-black">{dataset?.name || 'Loading dataset...'}</h2>
            <p className="text-xs text-[#757575] mt-0.5">
              Currency: <strong>{dataset?.currencyCode || 'INR'}</strong> · Total Committed Records:{' '}
              <strong>{dataset?.rowCount != null ? dataset.rowCount.toLocaleString() : '—'}</strong>
            </p>
          </div>
        </div>

        {/* Filter Bar */}
        <div className="mb-6">
          <FilterBar
            filter={filter}
            options={filterOptions}
            onChange={handleFilterChange}
            onReset={handleResetFilters}
            isUpdating={isFetchingOptions}
          />
        </div>

        {/* Export Panel Component */}
        {datasetId && (
          <ExportPanel
            datasetId={datasetId}
            datasetName={dataset?.name}
            filter={filter}
          />
        )}
      </main>
    </div>
  );
};
