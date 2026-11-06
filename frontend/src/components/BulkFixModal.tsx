import React, { useState } from 'react';
import { Loader2, X, Sparkles, ShieldAlert } from 'lucide-react';
import type { BulkFixRequest } from '../api/types';

interface BulkFixModalProps {
  isOpen: boolean;
  onClose: () => void;
  onApply: (request: BulkFixRequest) => void;
  isPending: boolean;
  invalidCount: number;
  latestValidDate?: string | null;
}

export const BulkFixModal: React.FC<BulkFixModalProps> = ({
  isOpen,
  onClose,
  onApply,
  isPending,
  invalidCount,
  latestValidDate,
}) => {
  const [invertNegativePrices, setInvertNegativePrices] = useState(true);
  const [decimalQtyStrategy, setDecimalQtyStrategy] = useState<'ROUND_HALF_UP' | 'TRUNCATE'>('ROUND_HALF_UP');
  const [setZeroQtyToOne, setSetZeroQtyToOne] = useState(true);
  
  const [dateStrategy, setDateStrategy] = useState<'LATEST_VALID' | 'CUSTOM'>('LATEST_VALID');
  const [customDate, setCustomDate] = useState(() => latestValidDate || new Date().toISOString().slice(0, 10));

  const [fillProduct, setFillProduct] = useState(true);
  const [productPlaceholder, setProductPlaceholder] = useState('Unspecified Product');

  const [fillCategory, setFillCategory] = useState(true);
  const [categoryPlaceholder, setCategoryPlaceholder] = useState('Uncategorized');

  const [fillRegion, setFillRegion] = useState(true);
  const [regionPlaceholder, setRegionPlaceholder] = useState('Unassigned');
  const [capExcessiveQuantity, setCapExcessiveQuantity] = useState(true);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const payload: BulkFixRequest = {
      negativePriceStrategy: invertNegativePrices ? 'ABS_VALUE' : 'IGNORE',
      decimalQuantityStrategy: decimalQtyStrategy,
      zeroQuantityStrategy: setZeroQtyToOne ? 'SET_ONE' : 'IGNORE',
      dateFixStrategy: dateStrategy,
      customDate: dateStrategy === 'CUSTOM' ? customDate : null,
      fillMissingProduct: fillProduct,
      productPlaceholder: productPlaceholder.trim() || 'Unspecified Product',
      fillMissingCategory: fillCategory,
      categoryPlaceholder: categoryPlaceholder.trim() || 'Uncategorized',
      fillMissingRegion: fillRegion,
      regionPlaceholder: regionPlaceholder.trim() || 'Unassigned',
      capExcessiveQuantity,
    };
    onApply(payload);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs transition-opacity animate-in fade-in duration-200">
      <div
        className="w-full max-w-2xl bg-white dark:bg-[#121214] rounded-2xl shadow-2xl border border-black/10 dark:border-white/10 overflow-hidden flex flex-col max-h-[90vh]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="bulk-fix-title"
      >
        {/* Header */}
        <div className="px-6 py-5 border-b border-black/8 dark:border-white/10 flex items-center justify-between bg-[#fcfbf9] dark:bg-[#18181b]">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-[#0075de]/10 dark:bg-[#38bdf8]/15 text-[#0075de] dark:text-[#38bdf8] flex items-center justify-center">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h2 id="bulk-fix-title" className="text-base font-bold text-black dark:text-white flex items-center gap-2">
                Guided Bulk-Fix Rules Wizard
                <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-red-100 dark:bg-red-950/50 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-900/40">
                  {invalidCount} invalid rows
                </span>
              </h2>
              <p className="text-xs text-[#757575] dark:text-[#a1a1aa] mt-0.5">
                Apply user-confirmed remediation rules across invalid spreadsheet entries.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isPending}
            className="p-1.5 rounded-lg text-[#757575] hover:text-black dark:text-[#a1a1aa] dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/5 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-6 py-5 space-y-5 text-xs">
          {/* Rule 1: Negative Prices */}
          <div className="p-3.5 rounded-xl border border-black/8 dark:border-white/10 bg-[#fbfbfa] dark:bg-[#18181b]/50">
            <label className="flex items-start gap-3 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={invertNegativePrices}
                onChange={(e) => setInvertNegativePrices(e.target.checked)}
                className="mt-0.5 rounded border-black/20 dark:border-white/20 text-[#0075de] focus:ring-[#0075de]"
              />
              <div className="flex-1">
                <span className="font-semibold text-black dark:text-white text-xs block">
                  Invert Negative Prices & Costs to Positive
                </span>
                <span className="text-[11px] text-[#757575] dark:text-[#a1a1aa] mt-0.5 block">
                  Converts accidental negative signs to positive values (e.g. <code className="font-mono text-[#0075de] dark:text-[#38bdf8]">-₹500.00</code> → <code className="font-mono text-emerald-600 dark:text-emerald-400">₹500.00</code>).
                </span>
              </div>
            </label>
          </div>

          {/* Rule 2: Decimal Quantities */}
          <div className="p-3.5 rounded-xl border border-black/8 dark:border-white/10 bg-[#fbfbfa] dark:bg-[#18181b]/50 space-y-2.5">
            <div>
              <span className="font-semibold text-black dark:text-white text-xs block">
                Decimal Quantity Resolution
              </span>
              <span className="text-[11px] text-[#757575] dark:text-[#a1a1aa]">
                Quantities must be whole numbers. Choose rounding behavior for non-integers (e.g. <code className="font-mono">2.5</code>).
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2 pt-1">
              <label
                className={`flex items-center gap-2 p-2.5 rounded-lg border cursor-pointer transition-colors ${
                  decimalQtyStrategy === 'ROUND_HALF_UP'
                    ? 'border-[#0075de] bg-[#0075de]/5 dark:border-[#38bdf8] dark:bg-[#38bdf8]/10 text-black dark:text-white font-medium'
                    : 'border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5 text-[#555] dark:text-[#a1a1aa]'
                }`}
              >
                <input
                  type="radio"
                  name="decimalQty"
                  value="ROUND_HALF_UP"
                  checked={decimalQtyStrategy === 'ROUND_HALF_UP'}
                  onChange={() => setDecimalQtyStrategy('ROUND_HALF_UP')}
                  className="text-[#0075de]"
                />
                <span className="text-xs">Round to Nearest Integer (2.5 → 3)</span>
              </label>

              <label
                className={`flex items-center gap-2 p-2.5 rounded-lg border cursor-pointer transition-colors ${
                  decimalQtyStrategy === 'TRUNCATE'
                    ? 'border-[#0075de] bg-[#0075de]/5 dark:border-[#38bdf8] dark:bg-[#38bdf8]/10 text-black dark:text-white font-medium'
                    : 'border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5 text-[#555] dark:text-[#a1a1aa]'
                }`}
              >
                <input
                  type="radio"
                  name="decimalQty"
                  value="TRUNCATE"
                  checked={decimalQtyStrategy === 'TRUNCATE'}
                  onChange={() => setDecimalQtyStrategy('TRUNCATE')}
                  className="text-[#0075de]"
                />
                <span className="text-xs">Truncate / Floor (2.5 → 2)</span>
              </label>
            </div>
          </div>

          {/* Rule 3: Zero or Non-Positive Quantities */}
          <div className="p-3.5 rounded-xl border border-black/8 dark:border-white/10 bg-[#fbfbfa] dark:bg-[#18181b]/50">
            <label className="flex items-start gap-3 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={setZeroQtyToOne}
                onChange={(e) => setSetZeroQtyToOne(e.target.checked)}
                className="mt-0.5 rounded border-black/20 dark:border-white/20 text-[#0075de] focus:ring-[#0075de]"
              />
              <div className="flex-1">
                <span className="font-semibold text-black dark:text-white text-xs block">
                  Set Zero or Negative Quantities to 1
                </span>
                <span className="text-[11px] text-[#757575] dark:text-[#a1a1aa] mt-0.5 block">
                  Replaces non-positive quantities (`0` or negative) with minimum valid order quantity (`1`).
                </span>
              </div>
            </label>
          </div>

          {/* Rule 4: Missing / Invalid Date Strategy */}
          <div className="p-3.5 rounded-xl border border-black/8 dark:border-white/10 bg-[#fbfbfa] dark:bg-[#18181b]/50 space-y-2.5">
            <div>
              <span className="font-semibold text-black dark:text-white text-xs block">
                Missing or Malformed Date Strategy
              </span>
              <span className="text-[11px] text-[#757575] dark:text-[#a1a1aa]">
                How to resolve rows where <code className="font-mono">order_date</code> is unparseable or blank.
              </span>
            </div>

            <div className="space-y-2 pt-1">
              <label
                className={`flex items-center justify-between p-2.5 rounded-lg border cursor-pointer transition-colors ${
                  dateStrategy === 'LATEST_VALID'
                    ? 'border-[#0075de] bg-[#0075de]/5 dark:border-[#38bdf8] dark:bg-[#38bdf8]/10 text-black dark:text-white font-medium'
                    : 'border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5 text-[#555] dark:text-[#a1a1aa]'
                }`}
              >
                <div className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="dateStrategy"
                    value="LATEST_VALID"
                    checked={dateStrategy === 'LATEST_VALID'}
                    onChange={() => setDateStrategy('LATEST_VALID')}
                    className="text-[#0075de]"
                  />
                  <span>Use latest valid order date in file</span>
                </div>
                {latestValidDate && (
                  <span className="font-mono text-[11px] px-2 py-0.5 rounded bg-black/5 dark:bg-white/10 text-[#0075de] dark:text-[#38bdf8] font-bold">
                    {latestValidDate}
                  </span>
                )}
              </label>

              <label
                className={`flex flex-col p-2.5 rounded-lg border cursor-pointer transition-colors ${
                  dateStrategy === 'CUSTOM'
                    ? 'border-[#0075de] bg-[#0075de]/5 dark:border-[#38bdf8] dark:bg-[#38bdf8]/10 text-black dark:text-white font-medium'
                    : 'border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5 text-[#555] dark:text-[#a1a1aa]'
                }`}
              >
                <div className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="dateStrategy"
                    value="CUSTOM"
                    checked={dateStrategy === 'CUSTOM'}
                    onChange={() => setDateStrategy('CUSTOM')}
                    className="text-[#0075de]"
                  />
                  <span>Pick custom date for missing entries</span>
                </div>
                {dateStrategy === 'CUSTOM' && (
                  <div className="mt-2 pl-6">
                    <input
                      type="date"
                      value={customDate}
                      onChange={(e) => setCustomDate(e.target.value)}
                      className="px-2.5 py-1 text-xs rounded border border-black/20 dark:border-white/20 bg-white dark:bg-[#121214] text-black dark:text-white"
                    />
                  </div>
                )}
              </label>
            </div>
          </div>

          {/* Rule 5: Editable Placeholders for Missing Text */}
          <div className="p-3.5 rounded-xl border border-black/8 dark:border-white/10 bg-[#fbfbfa] dark:bg-[#18181b]/50 space-y-3">
            <div>
              <span className="font-semibold text-black dark:text-white text-xs block">
                Missing Required Text Placeholders
              </span>
              <span className="text-[11px] text-[#757575] dark:text-[#a1a1aa]">
                Supply custom default placeholders for rows with missing product names, categories, or regions.
              </span>
            </div>

            <div className="space-y-2 pt-1">
              <div className="flex items-center gap-3">
                <input
                  type="checkbox"
                  id="fillProduct"
                  checked={fillProduct}
                  onChange={(e) => setFillProduct(e.target.checked)}
                  className="rounded border-black/20 dark:border-white/20 text-[#0075de]"
                />
                <label htmlFor="fillProduct" className="w-28 text-xs text-[#444] dark:text-[#ccc] select-none">
                  Product Name:
                </label>
                <input
                  type="text"
                  disabled={!fillProduct}
                  value={productPlaceholder}
                  onChange={(e) => setProductPlaceholder(e.target.value)}
                  className="flex-1 px-2.5 py-1 text-xs rounded border border-black/15 dark:border-white/15 bg-white dark:bg-[#121214] text-black dark:text-white disabled:opacity-50"
                  placeholder="e.g. Unspecified Product"
                />
              </div>

              <div className="flex items-center gap-3">
                <input
                  type="checkbox"
                  id="fillCat"
                  checked={fillCategory}
                  onChange={(e) => setFillCategory(e.target.checked)}
                  className="rounded border-black/20 dark:border-white/20 text-[#0075de]"
                />
                <label htmlFor="fillCat" className="w-28 text-xs text-[#444] dark:text-[#ccc] select-none">
                  Category:
                </label>
                <input
                  type="text"
                  disabled={!fillCategory}
                  value={categoryPlaceholder}
                  onChange={(e) => setCategoryPlaceholder(e.target.value)}
                  className="flex-1 px-2.5 py-1 text-xs rounded border border-black/15 dark:border-white/15 bg-white dark:bg-[#121214] text-black dark:text-white disabled:opacity-50"
                  placeholder="e.g. Uncategorized"
                />
              </div>

              <div className="flex items-center gap-3">
                <input
                  type="checkbox"
                  id="fillReg"
                  checked={fillRegion}
                  onChange={(e) => setFillRegion(e.target.checked)}
                  className="rounded border-black/20 dark:border-white/20 text-[#0075de]"
                />
                <label htmlFor="fillReg" className="w-28 text-xs text-[#444] dark:text-[#ccc] select-none">
                  Region:
                </label>
                <input
                  type="text"
                  disabled={!fillRegion}
                  value={regionPlaceholder}
                  onChange={(e) => setRegionPlaceholder(e.target.value)}
                  className="flex-1 px-2.5 py-1 text-xs rounded border border-black/15 dark:border-white/15 bg-white dark:bg-[#121214] text-black dark:text-white disabled:opacity-50"
                  placeholder="e.g. Unassigned"
                />
              </div>
            </div>
          </div>

          {/* Rule 5: 1,000,000 Quantity Cap */}
          <div className="p-3.5 rounded-xl border border-black/8 dark:border-white/10 bg-[#fbfbfa] dark:bg-[#18181b]/50">
            <label className="flex items-start gap-3 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={capExcessiveQuantity}
                onChange={(e) => setCapExcessiveQuantity(e.target.checked)}
                className="mt-0.5 rounded border-black/20 dark:border-white/20 text-[#0075de]"
              />
              <div className="space-y-0.5">
                <span className="font-semibold text-black dark:text-white">Cap quantities exceeding 1,000,000</span>
                <p className="text-[#666] dark:text-[#a1a1aa] text-[11px]">
                  Automatically cap quantities greater than 1,000,000 down to the maximum allowed limit of 1,000,000 (e.g. 1,000,001 becomes 1,000,000).
                </p>
              </div>
            </label>
          </div>

          {/* Safety / Status Notice: Out-of-Range Quantities */}
          <div
            className={`p-3.5 rounded-xl border text-[11px] leading-relaxed flex items-start gap-2.5 transition-colors ${
              capExcessiveQuantity
                ? 'border-emerald-200 dark:border-emerald-900/60 bg-emerald-50/50 dark:bg-emerald-950/20 text-emerald-800 dark:text-emerald-300'
                : 'border-amber-200 dark:border-amber-900/60 bg-amber-50/50 dark:bg-amber-950/20 text-amber-800 dark:text-amber-300'
            }`}
          >
            <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5" />
            <div>
              {capExcessiveQuantity ? (
                <span>
                  <strong>1,000,000 Limit Cap Active:</strong> Rows exceeding 1,000,000 will be capped down to 1,000,000 to become <code className="font-mono">VALID</code>.
                </span>
              ) : (
                <span>
                  <strong>Manual Review Safeguard:</strong> Rows exceeding 1,000,000 will remain untouched as <code className="font-mono">INVALID</code> for your manual review.
                </span>
              )}
            </div>
          </div>

          {/* Dialog Action Buttons */}
          <div className="pt-2 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              disabled={isPending}
              className="px-4 py-2 text-xs font-semibold text-[#555] dark:text-[#a1a1aa] hover:bg-black/5 dark:hover:bg-white/5 rounded-lg transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="px-5 py-2 text-xs font-semibold text-white bg-[#0075de] hover:bg-[#0060b8] disabled:opacity-50 rounded-lg shadow-sm transition-colors flex items-center gap-2 cursor-pointer"
            >
              {isPending ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Applying Repairs...</span>
                </>
              ) : (
                <>
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>Apply Bulk Repairs</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
