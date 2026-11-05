import React, { useState } from 'react';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getDataset } from '../api/datasets';
import { parseFilterParams } from '../lib/filters';
import { useAuth } from '../hooks/useAuth';
import {
  ArrowLeft,
  FileSpreadsheet,
  FileText,
  Download,
  Filter,
  BarChart3,
} from 'lucide-react';

export const ReportsPage: React.FC = () => {
  const { datasetId } = useParams<{ datasetId: string }>();
  const [searchParams] = useSearchParams();
  const { user, logout, isLoggingOut } = useAuth();

  const filter = parseFilterParams(searchParams);

  const [format, setFormat] = useState<'csv' | 'xlsx'>('csv');
  const [type, setType] = useState<'records' | 'summary'>('records');

  const { data: dataset } = useQuery({
    queryKey: ['dataset', datasetId],
    queryFn: () => {
      if (!datasetId) throw new Error('No dataset ID');
      return getDataset(datasetId);
    },
    enabled: Boolean(datasetId),
  });

  const activeFiltersList: Array<{ label: string; value: string }> = [];
  if (filter.from || filter.to) {
    activeFiltersList.push({
      label: 'Date Range',
      value: `${filter.from || 'Beginning'} to ${filter.to || 'Latest'}`,
    });
  }
  if (filter.category && filter.category.length > 0) {
    activeFiltersList.push({
      label: 'Categories',
      value: filter.category.join(', '),
    });
  }
  if (filter.region && filter.region.length > 0) {
    activeFiltersList.push({
      label: 'Regions',
      value: filter.region.join(', '),
    });
  }
  if (filter.product) {
    activeFiltersList.push({
      label: 'Product Filter',
      value: `"${filter.product}"`,
    });
  }
  if (filter.compare === 'PREVIOUS_PERIOD') {
    activeFiltersList.push({
      label: 'Comparison',
      value: 'Previous Period Active',
    });
  }

  return (
    <div className="min-h-screen bg-[#f6f5f4] text-black">
      {/* Top Header */}
      <header className="border-b border-black/8 bg-[#f6f5f4] sticky top-0 z-10">
        <div className="max-w-4xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link to="/" className="font-bold text-xl tracking-tight text-black flex items-center gap-2">
              <span className="w-6 h-6 rounded bg-[#0075de] text-white text-xs font-bold flex items-center justify-center">
                μ
              </span>
              Mutex
            </Link>
            <span className="text-xs text-[#757575] border-l border-black/10 pl-3">
              Export Reports
            </span>
          </div>

          <div className="flex items-center gap-3">
            {user && (
              <div className="flex items-center gap-2">
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

      {/* Main Container */}
      <main className="max-w-4xl mx-auto px-6 py-8">
        <div className="mb-6">
          <Link
            to={datasetId ? `/datasets/${datasetId}/analytics?${searchParams.toString()}` : '/'}
            className="inline-flex items-center gap-1.5 text-xs text-[#757575] hover:text-black transition-colors mb-3"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            Back to Analytics
          </Link>
          <h1 className="text-2xl font-bold tracking-tight text-black">
            Export Dataset &amp; Analytics
          </h1>
          <p className="text-sm text-[#757575] mt-1">
            Download your verified sales records or full analytical workbooks in CSV or XLSX format.
          </p>
        </div>

        {/* Dataset Details Card */}
        <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 mb-6">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs font-semibold text-[#757575] uppercase tracking-wider block mb-1">
                Target Dataset
              </span>
              <h2 className="text-lg font-bold text-black">{dataset?.name || 'Loading...'}</h2>
              <p className="text-xs text-[#757575] mt-0.5">
                Currency: <strong>{dataset?.currencyCode || 'INR'}</strong> · Total Committed Rows:{' '}
                <strong>{dataset?.rowCount.toLocaleString() || '—'}</strong>
              </p>
            </div>
            {datasetId && (
              <Link
                to={`/datasets/${datasetId}/analytics`}
                className="px-3 py-1.5 rounded-lg border border-black/10 text-xs font-semibold text-[#37352f] hover:bg-black/5 flex items-center gap-1.5"
              >
                <BarChart3 className="w-3.5 h-3.5" /> View Dashboard
              </Link>
            )}
          </div>
        </div>

        {/* Active Filters Summary */}
        <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 mb-6">
          <h3 className="text-sm font-bold text-black flex items-center gap-2 mb-3">
            <Filter className="w-4 h-4 text-[#0075de]" /> Active Export Scope
          </h3>
          {activeFiltersList.length === 0 ? (
            <p className="text-xs text-[#757575]">
              No active filters — exporting the entire dataset without restrictions.
            </p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {activeFiltersList.map((f, i) => (
                <div key={i} className="p-2.5 rounded-lg bg-[#faf9f8] border border-black/6 text-xs">
                  <span className="text-[#757575] block font-semibold text-[10px] uppercase">
                    {f.label}
                  </span>
                  <span className="text-black font-medium mt-0.5 block">{f.value}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Export Configuration Form */}
        <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 space-y-6">
          <div>
            <label className="block text-xs font-semibold text-[#37352f] uppercase tracking-wider mb-2">
              Export Type
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <label
                className={`p-4 rounded-xl border cursor-pointer transition-all flex items-start gap-3 ${
                  type === 'records'
                    ? 'border-[#0075de] bg-[#0075de]/5 ring-1 ring-[#0075de]'
                    : 'border-black/10 hover:border-black/20 bg-[#faf9f8]'
                }`}
              >
                <input
                  type="radio"
                  name="type"
                  value="records"
                  checked={type === 'records'}
                  onChange={() => setType('records')}
                  className="mt-0.5 text-[#0075de]"
                />
                <div>
                  <strong className="block text-sm font-bold text-black">Filtered Records</strong>
                  <span className="text-xs text-[#757575] mt-1 block">
                    Individual sales transactions matching active filters with all 8 columns plus line revenue and profit.
                  </span>
                </div>
              </label>

              <label
                className={`p-4 rounded-xl border cursor-pointer transition-all flex items-start gap-3 ${
                  type === 'summary'
                    ? 'border-[#0075de] bg-[#0075de]/5 ring-1 ring-[#0075de]'
                    : 'border-black/10 hover:border-black/20 bg-[#faf9f8]'
                }`}
              >
                <input
                  type="radio"
                  name="type"
                  value="summary"
                  checked={type === 'summary'}
                  onChange={() => {
                    setType('summary');
                    setFormat('xlsx'); // Summary workbook requires XLSX
                  }}
                  className="mt-0.5 text-[#0075de]"
                />
                <div>
                  <strong className="block text-sm font-bold text-black">Summary Workbook</strong>
                  <span className="text-xs text-[#757575] mt-1 block">
                    Multi-sheet workbook containing KPI overview, monthly trends, category breakdowns, and top products.
                  </span>
                </div>
              </label>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#37352f] uppercase tracking-wider mb-2">
              File Format
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <label
                className={`p-3 rounded-xl border cursor-pointer transition-all flex items-center gap-3 ${
                  format === 'csv'
                    ? 'border-[#0075de] bg-[#0075de]/5 ring-1 ring-[#0075de]'
                    : 'border-black/10 hover:border-black/20 bg-[#faf9f8]'
                } ${type === 'summary' ? 'opacity-40 cursor-not-allowed' : ''}`}
              >
                <input
                  type="radio"
                  name="format"
                  value="csv"
                  disabled={type === 'summary'}
                  checked={format === 'csv'}
                  onChange={() => setFormat('csv')}
                  className="text-[#0075de]"
                />
                <FileText className="w-5 h-5 text-[#757575]" />
                <div>
                  <strong className="block text-xs font-bold text-black">CSV (Comma-Separated)</strong>
                  <span className="text-[11px] text-[#757575]">UTF-8 with BOM, formula-sanitized</span>
                </div>
              </label>

              <label
                className={`p-3 rounded-xl border cursor-pointer transition-all flex items-center gap-3 ${
                  format === 'xlsx'
                    ? 'border-[#0075de] bg-[#0075de]/5 ring-1 ring-[#0075de]'
                    : 'border-black/10 hover:border-black/20 bg-[#faf9f8]'
                }`}
              >
                <input
                  type="radio"
                  name="format"
                  value="xlsx"
                  checked={format === 'xlsx'}
                  onChange={() => setFormat('xlsx')}
                  className="text-[#0075de]"
                />
                <FileSpreadsheet className="w-5 h-5 text-emerald-600" />
                <div>
                  <strong className="block text-xs font-bold text-black">Excel Workbook (.XLSX)</strong>
                  <span className="text-[11px] text-[#757575]">Native Excel format with numeric cells</span>
                </div>
              </label>
            </div>
          </div>

          {/* Action Row */}
          <div className="flex items-center justify-between pt-4 border-t border-black/8">
            <span className="text-xs text-[#757575]">
              Full export streaming with SXSSFWorkbook will be completed in M8.
            </span>
            <button
              type="button"
              onClick={() => {
                alert(`Exporting ${type} in ${format.toUpperCase()} format. Ready for M8 streaming export engine.`);
              }}
              className="px-5 py-2 bg-[#0075de] hover:bg-[#0060b8] text-white text-sm font-semibold rounded-lg shadow-xs flex items-center gap-2 cursor-pointer"
            >
              <Download className="w-4 h-4" /> Download Export
            </button>
          </div>
        </div>
      </main>
    </div>
  );
};
