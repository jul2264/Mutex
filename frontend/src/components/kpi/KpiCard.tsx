import React from 'react';

interface KpiCardProps {
  title: string;
  value: string | null | undefined;
  subtitle?: string;
  unavailableReason?: string | null;
  isLoading?: boolean;
  isError?: boolean;
  errorMessage?: string;
  onRetry?: () => void;
}

export const KpiCard: React.FC<KpiCardProps> = ({
  title,
  value,
  subtitle,
  unavailableReason,
  isLoading,
  isError,
  errorMessage,
  onRetry,
}) => {
  return (
    <div
      className="bg-white rounded-xl p-6 border border-black/8 transition-all duration-200"
      style={{ borderRadius: '12px', border: '1px solid rgba(0,0,0,0.08)' }}
    >
      <div className="text-xs font-medium uppercase tracking-wider text-[#757575] mb-2">
        {title}
      </div>

      {isLoading ? (
        <div className="space-y-2 animate-pulse py-1">
          <div className="h-8 bg-black/5 rounded w-3/4"></div>
          {subtitle && <div className="h-4 bg-black/5 rounded w-1/2"></div>}
        </div>
      ) : isError ? (
        <div className="py-1">
          <div className="text-sm text-[#f64932] font-medium">Failed to load</div>
          {errorMessage && (
            <div className="text-xs text-[#757575] truncate mt-1">{errorMessage}</div>
          )}
          {onRetry && (
            <button
              onClick={onRetry}
              className="mt-2 text-xs font-medium text-[#0075de] hover:underline cursor-pointer"
            >
              Retry
            </button>
          )}
        </div>
      ) : unavailableReason ? (
        <div className="py-1">
          <div className="text-2xl font-bold text-[#757575]">Not available</div>
          <div className="text-xs text-[#757575] mt-1 font-mono">
            Reason: {unavailableReason}
          </div>
        </div>
      ) : (
        <div className="py-1">
          <div className="text-3xl font-bold tracking-tight text-black">
            {value ?? '—'}
          </div>
          {subtitle && (
            <div className="text-xs text-[#757575] mt-1 font-normal">
              {subtitle}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

