import React, { useState } from 'react';
import { FileSpreadsheet, FileText, Download, AlertCircle, CheckCircle2, Loader2, Info } from 'lucide-react';
import type { AnalyticsFilterParams } from '../../api/types';
import { downloadExport } from '../../api/reports';
import { ApiError } from '../../api/apiFetch';

interface ExportPanelProps {
  datasetId: string;
  datasetName?: string;
  filter: AnalyticsFilterParams;
}

export const ExportPanel: React.FC<ExportPanelProps> = ({ datasetId, datasetName, filter }) => {
  const [format, setFormat] = useState<'csv' | 'xlsx'>('csv');
  const [type, setType] = useState<'records' | 'summary'>('records');
  const [isExporting, setIsExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errorCode, setErrorCode] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  // If user selects Summary, automatically switch format to XLSX (CSV does not support summary workbook)
  const handleTypeChange = (newType: 'records' | 'summary') => {
    setType(newType);
    if (newType === 'summary' && format === 'csv') {
      setFormat('xlsx');
    }
  };

  const handleFormatChange = (newFormat: 'csv' | 'xlsx') => {
    setFormat(newFormat);
    if (newFormat === 'csv' && type === 'summary') {
      setType('records');
    }
  };

  const handleExport = async () => {
    setIsExporting(true);
    setError(null);
    setErrorCode(null);
    setSuccess(false);

    try {
      await downloadExport(datasetId, format, type, filter);
      setSuccess(true);
      setTimeout(() => setSuccess(false), 5000);
    } catch (err: any) {
      if (err instanceof ApiError) {
        setErrorCode(err.code || null);
        setError(err.message);
      } else {
        setError(err?.message || 'Failed to download export');
      }
    } finally {
      setIsExporting(false);
    }
  };

  // Compile active filter descriptors
  const filterDescriptions: string[] = [];
  if (filter.from || filter.to) {
    filterDescriptions.push(`Date: ${filter.from || 'Earliest'} to ${filter.to || 'Latest'}`);
  }
  if (filter.category && filter.category.length > 0) {
    filterDescriptions.push(`Categories (${filter.category.length}): ${filter.category.join(', ')}`);
  }
  if (filter.region && filter.region.length > 0) {
    filterDescriptions.push(`Regions (${filter.region.length}): ${filter.region.join(', ')}`);
  }
  if (filter.product && filter.product.trim()) {
    filterDescriptions.push(`Product contains: "${filter.product.trim()}"`);
  }

  return (
    <div className="bg-white border border-black/8 rounded-xl p-6 shadow-sm">
      <div className="mb-6">
        <h2 className="text-base font-semibold text-black mb-1">
          Export Configuration
        </h2>
        <p className="text-xs text-[#757575]">
          Generate and download reports for {datasetName ? <span className="font-medium text-black">"{datasetName}"</span> : 'this dataset'} adhering to active filters.
        </p>
      </div>

      {/* Active Filter Scope Callout */}
      <div className="mb-6 bg-[#f6f5f4] border border-black/5 rounded-lg p-3 text-xs">
        <div className="flex items-center gap-1.5 font-medium text-black mb-1.5">
          <Info className="w-3.5 h-3.5 text-[#0075de]" />
          <span>Active Export Scope</span>
        </div>
        {filterDescriptions.length > 0 ? (
          <ul className="space-y-1 text-[#37352f] pl-5 list-disc">
            {filterDescriptions.map((desc, idx) => (
              <li key={idx}>{desc}</li>
            ))}
          </ul>
        ) : (
          <p className="text-[#757575]">
            No filters applied. Exporting all records in the dataset.
          </p>
        )}
      </div>

      <div className="space-y-5">
        {/* Type Selection */}
        <div>
          <label className="block text-xs font-semibold text-black uppercase tracking-wider mb-2">
            Export Type
          </label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <button
              type="button"
              onClick={() => handleTypeChange('records')}
              className={`flex items-start gap-3 p-3.5 rounded-lg border text-left transition-all ${
                type === 'records'
                  ? 'border-[#0075de] bg-[#0075de]/5 ring-1 ring-[#0075de]'
                  : 'border-black/10 hover:border-black/20 hover:bg-black/[0.02]'
              }`}
            >
              <FileText className={`w-5 h-5 mt-0.5 ${type === 'records' ? 'text-[#0075de]' : 'text-[#757575]'}`} />
              <div>
                <span className="block text-xs font-semibold text-black">
                  Raw Filtered Records
                </span>
                <span className="block text-[11px] text-[#757575] mt-0.5">
                  Row-by-row transaction records including prices, costs, revenues, and profits.
                </span>
              </div>
            </button>

            <button
              type="button"
              onClick={() => handleTypeChange('summary')}
              className={`flex items-start gap-3 p-3.5 rounded-lg border text-left transition-all ${
                type === 'summary'
                  ? 'border-[#0075de] bg-[#0075de]/5 ring-1 ring-[#0075de]'
                  : 'border-black/10 hover:border-black/20 hover:bg-black/[0.02]'
              }`}
            >
              <FileSpreadsheet className={`w-5 h-5 mt-0.5 ${type === 'summary' ? 'text-[#0075de]' : 'text-[#757575]'}`} />
              <div>
                <div className="flex items-center gap-1.5">
                  <span className="block text-xs font-semibold text-black">
                    Summary Workbook
                  </span>
                  <span className="text-[10px] font-medium bg-[#0075de]/10 text-[#0075de] px-1.5 py-0.2 rounded">
                    Excel only
                  </span>
                </div>
                <span className="block text-[11px] text-[#757575] mt-0.5">
                  5-sheet workbook: Summary, Trends, Categories, Regions, and Top Products.
                </span>
              </div>
            </button>
          </div>
        </div>

        {/* Format Selection */}
        <div>
          <label className="block text-xs font-semibold text-black uppercase tracking-wider mb-2">
            File Format
          </label>
          <div className="flex items-center gap-3">
            <button
              type="button"
              disabled={type === 'summary'}
              onClick={() => handleFormatChange('csv')}
              className={`flex-1 flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg border text-xs font-medium transition-all ${
                type === 'summary'
                  ? 'opacity-40 cursor-not-allowed border-black/10 text-[#757575]'
                  : format === 'csv'
                  ? 'border-[#0075de] bg-[#0075de]/5 text-[#0075de] font-semibold ring-1 ring-[#0075de]'
                  : 'border-black/10 hover:border-black/20 hover:bg-black/[0.02] text-[#37352f]'
              }`}
            >
              <FileText className="w-4 h-4" />
              <span>CSV (.csv)</span>
            </button>

            <button
              type="button"
              onClick={() => handleFormatChange('xlsx')}
              className={`flex-1 flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg border text-xs font-medium transition-all ${
                format === 'xlsx'
                  ? 'border-[#0075de] bg-[#0075de]/5 text-[#0075de] font-semibold ring-1 ring-[#0075de]'
                  : 'border-black/10 hover:border-black/20 hover:bg-black/[0.02] text-[#37352f]'
              }`}
            >
              <FileSpreadsheet className="w-4 h-4" />
              <span>Excel (.xlsx)</span>
            </button>
          </div>
          {type === 'summary' && (
            <p className="text-[11px] text-[#757575] mt-1.5">
              Summary workbook requires Excel (.xlsx) format for multi-sheet layout.
            </p>
          )}
        </div>

        {/* Error Notification */}
        {error && (
          <div className="p-3.5 rounded-lg border border-red-200 bg-red-50 text-red-900 text-xs flex items-start gap-2.5">
            <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold text-red-800">
                {errorCode === 'EXPORT_TOO_LARGE' ? 'Export Exceeds Size Limit' : 'Export Failed'}
              </p>
              <p className="text-red-700 mt-0.5">{error}</p>
              {errorCode === 'EXPORT_TOO_LARGE' && (
                <p className="text-red-600 text-[11px] mt-1.5 font-medium">
                  Tip: Use the filter bar to select a narrower date range, specific categories, or regions.
                </p>
              )}
            </div>
          </div>
        )}

        {/* Success Notification */}
        {success && (
          <div className="p-3.5 rounded-lg border border-emerald-200 bg-emerald-50 text-emerald-900 text-xs flex items-center gap-2.5">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>Export generated and download started!</span>
          </div>
        )}

        {/* Action Button */}
        <div className="pt-2">
          <button
            type="button"
            onClick={handleExport}
            disabled={isExporting}
            className="w-full flex items-center justify-center gap-2 bg-[#0075de] hover:bg-[#0060b9] disabled:opacity-50 text-white text-xs font-semibold py-2.5 px-4 rounded-lg transition-colors shadow-sm"
          >
            {isExporting ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Preparing and downloading export...</span>
              </>
            ) : (
              <>
                <Download className="w-4 h-4" />
                <span>Download {format.toUpperCase()} Export</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};
