import React from 'react';
import type { TopProductRow } from '../../api/types';
import { formatMoney } from '../../lib/format';
import { Trophy, ArrowDownUp } from 'lucide-react';

interface TopProductsTableProps {
  products: TopProductRow[];
  currency?: string;
  rankBy: 'REVENUE' | 'PROFIT';
  onRankByChange: (rankBy: 'REVENUE' | 'PROFIT') => void;
  isLoading?: boolean;
}

export const TopProductsTable: React.FC<TopProductsTableProps> = ({
  products,
  currency = 'INR',
  rankBy,
  onRankByChange,
  isLoading,
}) => {
  if (isLoading) {
    return (
      <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 h-80 flex items-center justify-center animate-pulse">
        <div className="text-xs text-[#757575]">Loading top products...</div>
      </div>
    );
  }

  if (!products || products.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 h-80 flex flex-col items-center justify-center text-center">
        <Trophy className="w-8 h-8 text-[#757575] mb-2" />
        <span className="text-sm font-semibold text-black">No products found</span>
        <span className="text-xs text-[#757575] mt-1">Adjust filters or search term to see top products.</span>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h2 className="text-base font-bold text-black flex items-center gap-2">
            <Trophy className="w-4 h-4 text-amber-500" /> Top Products
          </h2>
          <p className="text-xs text-[#757575] mt-0.5">
            Ranked products showing dual revenue and gross profit ranking.
          </p>
        </div>

        {/* RankBy Switcher */}
        <div className="inline-flex rounded-lg border border-black/10 p-0.5 bg-[#f6f5f4] text-xs font-medium">
          <button
            type="button"
            onClick={() => onRankByChange('REVENUE')}
            className={`px-3 py-1.5 rounded-md transition-colors flex items-center gap-1.5 ${
              rankBy === 'REVENUE'
                ? 'bg-white shadow-2xs font-semibold text-black'
                : 'text-[#757575] hover:text-black'
            }`}
          >
            <ArrowDownUp className="w-3 h-3 text-[#0075de]" /> Rank by Revenue
          </button>
          <button
            type="button"
            onClick={() => onRankByChange('PROFIT')}
            className={`px-3 py-1.5 rounded-md transition-colors flex items-center gap-1.5 ${
              rankBy === 'PROFIT'
                ? 'bg-white shadow-2xs font-semibold text-black'
                : 'text-[#757575] hover:text-black'
            }`}
          >
            <ArrowDownUp className="w-3 h-3 text-emerald-600" /> Rank by Profit
          </button>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs" aria-label="Top Products Table">
          <thead>
            <tr className="bg-[#faf9f8] border-b border-black/8 text-[#757575] font-semibold uppercase tracking-wider">
              <th className="py-2.5 px-3">Product</th>
              <th className="py-2.5 px-3 text-center">Revenue Rank</th>
              <th className="py-2.5 px-3 text-center">Profit Rank</th>
              <th className="py-2.5 px-3 text-right">Revenue</th>
              <th className="py-2.5 px-3 text-right">Gross Profit</th>
              <th className="py-2.5 px-3 text-right">Margin %</th>
              <th className="py-2.5 px-3 text-right">Units</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-black/5">
            {products.map((p) => {
              const rev = parseFloat(p.revenue) || 0;
              const profit = parseFloat(p.grossProfit) || 0;
              const margin = rev > 0 ? ((profit / rev) * 100).toFixed(2) : '—';
              const rankDivergence = p.revenueRank !== p.profitRank;

              return (
                <tr key={p.product} className="hover:bg-black/[0.02]">
                  <td className="py-2.5 px-3 font-semibold text-black">{p.product}</td>
                  <td className="py-2.5 px-3 text-center">
                    <span
                      className={`inline-block px-2 py-0.5 rounded text-[11px] font-mono font-semibold ${
                        p.revenueRank <= 3
                          ? 'bg-[#0075de]/10 text-[#0075de]'
                          : 'bg-black/5 text-[#555]'
                      }`}
                    >
                      #{p.revenueRank}
                    </span>
                  </td>
                  <td className="py-2.5 px-3 text-center">
                    <span
                      className={`inline-block px-2 py-0.5 rounded text-[11px] font-mono font-semibold ${
                        p.profitRank <= 3
                          ? 'bg-emerald-50 text-emerald-700'
                          : 'bg-black/5 text-[#555]'
                      }`}
                    >
                      #{p.profitRank}
                      {rankDivergence && (
                        <span className="ml-1 text-[10px] text-[#757575]" title="Revenue and profit ranks diverge">
                          *
                        </span>
                      )}
                    </span>
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono">{formatMoney(p.revenue, currency)}</td>
                  <td className="py-2.5 px-3 text-right font-mono">
                    <span className={profit < 0 ? 'text-red-600' : ''}>
                      {formatMoney(p.grossProfit, currency)}
                    </span>
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono">
                    {margin !== '—' ? `${margin}%` : '—'}
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono">{p.unitsSold.toLocaleString()}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};
