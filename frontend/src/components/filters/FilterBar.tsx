import React, { useState, useEffect } from 'react';
import type { FilterOptionsResponse, AnalyticsFilterParams } from '../../api/types';
import { getDatePreset, computePresetDates, type DatePreset } from '../../lib/filters';
import { Search, RotateCcw, Calendar, Filter, GitCompare } from 'lucide-react';

interface FilterBarProps {
  filter: AnalyticsFilterParams;
  options?: FilterOptionsResponse;
  onChange: (updated: Partial<AnalyticsFilterParams>) => void;
  onReset: () => void;
  isUpdating?: boolean;
}

export const FilterBar: React.FC<FilterBarProps> = ({
  filter,
  options,
  onChange,
  onReset,
  isUpdating,
}) => {
  const [productSearch, setProductSearch] = useState<string>(filter.product || '');
  const [prevFilterProduct, setPrevFilterProduct] = useState<string | undefined>(filter.product);
  const [selectedPreset, setSelectedPreset] = useState<DatePreset>(
    getDatePreset(filter.from, filter.to)
  );

  // Sync internal search input if external filter changes (e.g. onReset)
  if (filter.product !== prevFilterProduct) {
    setPrevFilterProduct(filter.product);
    setProductSearch(filter.product || '');
  }

  // Debounce product search changes by 300ms
  useEffect(() => {
    const handler = setTimeout(() => {
      if ((filter.product || '') !== productSearch.trim()) {
        onChange({ product: productSearch.trim() || undefined });
      }
    }, 300);

    return () => clearTimeout(handler);
  }, [productSearch, filter.product, onChange]);

  const handlePresetChange = (preset: DatePreset) => {
    setSelectedPreset(preset);
    if (preset === 'custom') return;
    const dates = computePresetDates(preset);
    onChange({ from: dates.from, to: dates.to });
  };

  const handleCategoryToggle = (cat: string) => {
    const current = filter.category || [];
    const updated = current.includes(cat)
      ? current.filter((c) => c !== cat)
      : [...current, cat];
    onChange({ category: updated.length > 0 ? updated : undefined });
  };

  const handleRegionToggle = (reg: string) => {
    const current = filter.region || [];
    const updated = current.includes(reg)
      ? current.filter((r) => r !== reg)
      : [...current, reg];
    onChange({ region: updated.length > 0 ? updated : undefined });
  };

  const hasActiveFilters = Boolean(
    filter.from ||
      filter.to ||
      (filter.category && filter.category.length > 0) ||
      (filter.region && filter.region.length > 0) ||
      filter.product ||
      filter.compare === 'PREVIOUS_PERIOD'
  );

  return (
    <div className="bg-white rounded-xl border border-black/8 shadow-xs p-4 mb-6 transition-opacity duration-200">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-3 pb-3 border-b border-black/6">
        {/* Date presets & Custom Pickers */}
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold text-[#757575] uppercase tracking-wider flex items-center gap-1.5 mr-1">
            <Calendar className="w-3.5 h-3.5" /> Date:
          </span>

          <div className="inline-flex rounded-lg border border-black/10 p-0.5 bg-[#f6f5f4] text-xs font-medium">
            <button
              type="button"
              onClick={() => handlePresetChange('all')}
              className={`px-2.5 py-1 rounded-md transition-colors ${
                selectedPreset === 'all' ? 'bg-white shadow-2xs font-semibold text-black' : 'text-[#757575] hover:text-black'
              }`}
            >
              All Time
            </button>
            <button
              type="button"
              onClick={() => handlePresetChange('last30')}
              className={`px-2.5 py-1 rounded-md transition-colors ${
                selectedPreset === 'last30' ? 'bg-white shadow-2xs font-semibold text-black' : 'text-[#757575] hover:text-black'
              }`}
            >
              Last 30 Days
            </button>
            <button
              type="button"
              onClick={() => handlePresetChange('lastMonth')}
              className={`px-2.5 py-1 rounded-md transition-colors ${
                selectedPreset === 'lastMonth' ? 'bg-white shadow-2xs font-semibold text-black' : 'text-[#757575] hover:text-black'
              }`}
            >
              Last Full Month
            </button>
            <button
              type="button"
              onClick={() => handlePresetChange('custom')}
              className={`px-2.5 py-1 rounded-md transition-colors ${
                selectedPreset === 'custom' ? 'bg-white shadow-2xs font-semibold text-black' : 'text-[#757575] hover:text-black'
              }`}
            >
              Custom
            </button>
          </div>

          {selectedPreset === 'custom' && (
            <div className="flex items-center gap-1.5 ml-2 text-xs">
              <input
                type="date"
                value={filter.from || ''}
                onChange={(e) => onChange({ from: e.target.value || undefined })}
                className="px-2 py-1 rounded border border-black/15 bg-[#faf9f8] text-xs focus:outline-none focus:border-[#0075de]"
              />
              <span className="text-[#757575]">to</span>
              <input
                type="date"
                value={filter.to || ''}
                onChange={(e) => onChange({ to: e.target.value || undefined })}
                className="px-2 py-1 rounded border border-black/15 bg-[#faf9f8] text-xs focus:outline-none focus:border-[#0075de]"
              />
            </div>
          )}
        </div>

        {/* Compare Toggle & Reset */}
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-1.5 text-xs font-medium text-[#37352f] cursor-pointer">
            <input
              type="checkbox"
              checked={filter.compare === 'PREVIOUS_PERIOD'}
              onChange={(e) =>
                onChange({ compare: e.target.checked ? 'PREVIOUS_PERIOD' : undefined })
              }
              className="rounded border-black/20 text-[#0075de] focus:ring-[#0075de]"
            />
            <GitCompare className="w-3.5 h-3.5 text-[#0075de]" />
            Compare Previous Period
          </label>

          {hasActiveFilters && (
            <button
              type="button"
              onClick={onReset}
              className="inline-flex items-center gap-1 text-xs text-[#757575] hover:text-red-600 px-2 py-1 rounded hover:bg-red-50 transition-colors"
            >
              <RotateCcw className="w-3 h-3" />
              Reset
            </button>
          )}

          {isUpdating && (
            <span className="text-[11px] font-medium text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse" />
              Updating...
            </span>
          )}
        </div>
      </div>

      {/* Row 2: Search, Categories, Regions */}
      <div className="flex flex-wrap items-center gap-3">
        {/* Product Search */}
        <div className="relative min-w-[200px] flex-1 max-w-xs">
          <Search className="w-3.5 h-3.5 absolute left-2.5 top-2.5 text-[#757575]" />
          <input
            type="text"
            value={productSearch}
            onChange={(e) => setProductSearch(e.target.value)}
            placeholder="Search product..."
            className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-black/12 text-xs focus:outline-none focus:ring-1 focus:ring-[#0075de] bg-[#faf9f8]"
          />
        </div>

        {/* Categories Multi-Select Pills */}
        {options && options.categories.length > 0 && (
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-[11px] font-semibold text-[#757575] uppercase tracking-wider flex items-center gap-1">
              <Filter className="w-3 h-3" /> Category:
            </span>
            {options.categories.map((cat) => {
              const active = (filter.category || []).includes(cat);
              return (
                <button
                  key={cat}
                  type="button"
                  onClick={() => handleCategoryToggle(cat)}
                  className={`text-xs px-2.5 py-1 rounded-full border transition-all cursor-pointer ${
                    active
                      ? 'bg-[#0075de] text-white border-[#0075de] font-medium shadow-2xs'
                      : 'bg-[#faf9f8] text-[#555] border-black/10 hover:border-black/25'
                  }`}
                >
                  {cat}
                </button>
              );
            })}
          </div>
        )}

        {/* Regions Multi-Select Pills */}
        {options && options.regions.length > 0 && (
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-[11px] font-semibold text-[#757575] uppercase tracking-wider flex items-center gap-1">
              Region:
            </span>
            {options.regions.map((reg) => {
              const active = (filter.region || []).includes(reg);
              return (
                <button
                  key={reg}
                  type="button"
                  onClick={() => handleRegionToggle(reg)}
                  className={`text-xs px-2.5 py-1 rounded-full border transition-all cursor-pointer ${
                    active
                      ? 'bg-[#0075de] text-white border-[#0075de] font-medium shadow-2xs'
                      : 'bg-[#faf9f8] text-[#555] border-black/10 hover:border-black/25'
                  }`}
                >
                  {reg}
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
