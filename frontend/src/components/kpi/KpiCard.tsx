import React from 'react';
import type { Metric } from '../../api/types';
import { ArrowUpRight, ArrowDownRight, Info } from 'lucide-react';

interface KpiCardProps {
  title: string;
  value: string | null | undefined;
  subtitle?: string;
  metric?: Metric | null;
  growth?: Metric | null;
  deltaText?: string | null;
  isLoading?: boolean;
  isError?: boolean;
  errorMessage?: string;
  onRetry?: () => void;
}

export const KpiCard: React.FC<KpiCardProps> = ({
  title,
  value,
  subtitle,
  metric,
  growth,
  deltaText,
  isLoading,
  isError,
  errorMessage,
  onRetry,
}) => {
  const unavailableReason = metric?.unavailableReason;
  const displayValue = metric ? (metric.value ? `${metric.value}%` : null) : value;

  const getReasonExplanation = (reason: string) => {
    switch (reason) {
      case 'ZERO_REVENUE':
        return 'Margin cannot be calculated when revenue is zero.';
      case 'PREVIOUS_ZERO':
        return 'Growth cannot be calculated from a zero revenue baseline.';
      case 'NOT_REQUESTED':
        return 'Previous period comparison is not active.';
      default:
        return reason;
    }
  };

  const renderGrowthBadge = () => {
    if (!growth || !growth.value) return null;
    const num = parseFloat(growth.value);
    const isPositive = num > 0;
    const isNegative = num < 0;

    return (
      <span
        className={`inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-xs font-semibold ${
          isPositive
            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
            : isNegative
            ? 'bg-red-50 text-red-700 border border-red-200'
            : 'bg-black/5 text-[#555] border border-black/10'
        }`}
      >
        {isPositive && <ArrowUpRight className="w-3 h-3" />}
        {isNegative && <ArrowDownRight className="w-3 h-3" />}
        {isPositive ? `+${growth.value}%` : `${growth.value}%`}
      </span>
    );
  };

  return (
    <div className="bg-white rounded-xl p-5 border border-black/8 shadow-xs transition-all duration-200 flex flex-col justify-between">
      <div>
        <div className="flex items-center justify-between gap-2 mb-2">
          <span className="text-xs font-semibold uppercase tracking-wider text-[#757575]">
            {title}
          </span>
          {renderGrowthBadge()}
        </div>

        {isLoading ? (
          <div className="space-y-2 animate-pulse py-1">
            <div className="h-8 bg-black/5 rounded w-3/4" />
            <div className="h-3.5 bg-black/5 rounded w-1/2" />
          </div>
        ) : isError ? (
          <div className="py-1">
            <div className="text-sm text-red-600 font-semibold">Failed to load</div>
            {errorMessage && (
              <div className="text-xs text-[#757575] truncate mt-1">{errorMessage}</div>
            )}
            {onRetry && (
              <button
                type="button"
                onClick={onRetry}
                className="mt-2 text-xs font-semibold text-[#0075de] hover:underline cursor-pointer"
              >
                Retry
              </button>
            )}
          </div>
        ) : unavailableReason ? (
          <div className="py-1">
            <div className="text-xl font-bold text-[#757575]">Not available</div>
            <div className="text-xs text-[#757575] mt-1.5 flex items-center gap-1.5 bg-[#f6f5f4] p-1.5 rounded-lg border border-black/6">
              <Info className="w-3.5 h-3.5 shrink-0 text-[#757575]" />
              <span>{getReasonExplanation(unavailableReason)}</span>
            </div>
          </div>
        ) : (
          <div className="py-1">
            <div className="text-3xl font-bold tracking-tight text-black">
              {displayValue ?? '—'}
            </div>
            {deltaText && (
              <div className="text-xs font-medium text-[#757575] mt-1">
                {deltaText}
              </div>
            )}
          </div>
        )}
      </div>

      {subtitle && !unavailableReason && (
        <div className="text-xs text-[#757575] mt-3 pt-2 border-t border-black/5 font-normal">
          {subtitle}
        </div>
      )}
    </div>
  );
};
