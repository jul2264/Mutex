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
    <div className="bg-white rounded-xl border border-black/10 shadow-sm p-5 mb-8 transition-opacity duration-200">
      <div className="flex flex-wrap items-center justify-between gap-4 mb-4 pb-4 border-b border-black/8">
        {/* Date presets & Custom Pickers */}
        <div className="flex flex-wrap items-center gap-2.5">
          <span className="text-xs font-bold text-[#444] uppercase tracking-wider flex items-center gap-1.5 mr-1">
            <Calendar className="w-4 h-4 text-[#0075de]" /> Date:
          </span>

          <div className="inline-flex rounded-lg border border-black/15 p-1 bg-[#f0eeeb] text-xs font-medium">
            <button
              type="button"
              onClick={() => handlePresetChange('all')}
              className={`px-3 py-1.5 rounded-md transition-all cursor-pointer ${
                selectedPreset === 'all'
                  ? 'bg-white shadow-xs font-bold text-black border border-black/10'
                  : 'text-[#555] hover:text-black hover:bg-black/5'
              }`}
            >
              All Time
            </button>
            <button
              type="button"
              onClick={() => handlePresetChange('last30')}
              className={`px-3 py-1.5 rounded-md transition-all cursor-pointer ${
                selectedPreset === 'last30'
                  ? 'bg-white shadow-xs font-bold text-black border border-black/10'
                  : 'text-[#555] hover:text-black hover:bg-black/5'
              }`}
            >
              Last 30 Days
            </button>
            <button
              type="button"
              onClick={() => handlePresetChange('lastMonth')}
              className={`px-3 py-1.5 rounded-md transition-all cursor-pointer ${
                selectedPreset === 'lastMonth'
                  ? 'bg-white shadow-xs font-bold text-black border border-black/10'
                  : 'text-[#555] hover:text-black hover:bg-black/5'
              }`}
            >
              Last Full Month
            </button>
            <button
              type="button"
              onClick={() => handlePresetChange('custom')}
              className={`px-3 py-1.5 rounded-md transition-all cursor-pointer ${
                selectedPreset === 'custom'
                  ? 'bg-white shadow-xs font-bold text-black border border-black/10'
                  : 'text-[#555] hover:text-black hover:bg-black/5'
              }`}
            >
              Custom
            </button>
          </div>

          {selectedPreset === 'custom' && (
            <div className="flex items-center gap-2 ml-2 text-xs">
              <input
                type="date"
                value={filter.from || ''}
                onChange={(e) => onChange({ from: e.target.value || undefined })}
                className="px-2.5 py-1.5 rounded-md border border-black/20 bg-white text-xs font-medium focus:outline-none focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de]"
              />
              <span className="text-[#555] font-semibold">to</span>
              <input
                type="date"
                value={filter.to || ''}
                onChange={(e) => onChange({ to: e.target.value || undefined })}
                className="px-2.5 py-1.5 rounded-md border border-black/20 bg-white text-xs font-medium focus:outline-none focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de]"
              />
            </div>
          )}
        </div>

        {/* Compare Toggle & Reset */}
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-xs font-semibold text-[#222] cursor-pointer bg-[#f6f5f4] hover:bg-[#eeecea] px-3 py-1.5 rounded-lg border border-black/10 transition-colors">
            <input
              type="checkbox"
              checked={filter.compare === 'PREVIOUS_PERIOD'}
              onChange={(e) =>
                onChange({ compare: e.target.checked ? 'PREVIOUS_PERIOD' : undefined })
              }
              className="w-4 h-4 rounded border-black/25 text-[#0075de] focus:ring-[#0075de]"
            />
            <GitCompare className="w-3.5 h-3.5 text-[#0075de]" />
            <span>Compare Previous Period</span>
          </label>

          {hasActiveFilters && (
            <button
              type="button"
              onClick={onReset}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#555] hover:text-red-700 px-2.5 py-1.5 rounded-lg hover:bg-red-50 border border-transparent hover:border-red-200 transition-colors cursor-pointer"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Reset
            </button>
          )}

          {isUpdating && (
            <span className="text-xs font-semibold text-amber-800 bg-amber-100 border border-amber-300 px-2.5 py-1 rounded-full flex items-center gap-1.5 shadow-2xs">
              <span className="w-2 h-2 rounded-full bg-amber-600 animate-pulse" />
              Updating...
            </span>
          )}
        </div>
      </div>

      {/* Row 2: Search, Categories, Regions */}
      <div className="flex flex-wrap items-center gap-4">
        {/* Product Search */}
        <div className="relative min-w-[220px] flex-1 max-w-sm">
          <Search className="w-4 h-4 absolute left-3 top-2.5 text-[#757575]" />
          <input
            type="text"
            value={productSearch}
            onChange={(e) => setProductSearch(e.target.value)}
            placeholder="Search product..."
            className="w-full pl-9 pr-3 py-1.5 rounded-lg border border-black/20 text-xs font-medium focus:outline-none focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] bg-white placeholder-[#888]"
          />
        </div>

        {/* Categories Multi-Select Pills */}
        {options && options.categories.length > 0 && (
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-xs font-bold text-[#444] uppercase tracking-wider flex items-center gap-1 mr-0.5">
              <Filter className="w-3.5 h-3.5 text-[#0075de]" /> Category:
            </span>
            {options.categories.map((cat) => {
              const active = (filter.category || []).includes(cat);
              return (
                <button
                  key={cat}
                  type="button"
                  onClick={() => handleCategoryToggle(cat)}
                  className={`text-xs px-3 py-1 rounded-full border transition-all cursor-pointer font-medium ${
                    active
                      ? 'bg-[#0075de] text-white border-[#0075de] shadow-2xs font-semibold'
                      : 'bg-white text-[#333] border-black/15 hover:border-black/35 hover:bg-black/5'
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
            <span className="text-xs font-bold text-[#444] uppercase tracking-wider flex items-center gap-1 mr-0.5">
              Region:
            </span>
            {options.regions.map((reg) => {
              const active = (filter.region || []).includes(reg);
              return (
                <button
                  key={reg}
                  type="button"
                  onClick={() => handleRegionToggle(reg)}
                  className={`text-xs px-3 py-1 rounded-full border transition-all cursor-pointer font-medium ${
                    active
                      ? 'bg-[#0075de] text-white border-[#0075de] shadow-2xs font-semibold'
                      : 'bg-white text-[#333] border-black/15 hover:border-black/35 hover:bg-black/5'
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
