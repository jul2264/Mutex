import React, { useState } from 'react';
import type { BreakdownRow } from '../../api/types';
import { formatMoney, formatPercent } from '../../lib/format';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from 'recharts';
import { Table, BarChart2 } from 'lucide-react';

interface RegionChartProps {
  regions: BreakdownRow[];
  currency?: string;
  isLoading?: boolean;
}

export const RegionChart: React.FC<RegionChartProps> = ({
  regions,
  currency = 'INR',
  isLoading,
}) => {
  const [viewAsTable, setViewAsTable] = useState<boolean>(false);
  const [metric, setMetric] = useState<'revenue' | 'grossProfit' | 'unitsSold'>('revenue');

  if (isLoading) {
    return (
      <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 h-80 flex items-center justify-center animate-pulse">
        <div className="text-xs text-[#757575]">Loading regional performance...</div>
      </div>
    );
  }

  if (!regions || regions.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 h-80 flex flex-col items-center justify-center text-center">
        <BarChart2 className="w-8 h-8 text-[#757575] mb-2" />
        <span className="text-sm font-semibold text-black">No regional data</span>
        <span className="text-xs text-[#757575] mt-1">Adjust filters to see regional breakdown.</span>
      </div>
    );
  }

  const chartData = regions.map((r) => ({
    name: r.name,
    revenue: parseFloat(r.revenue) || 0,
    grossProfit: parseFloat(r.grossProfit) || 0,
    unitsSold: r.unitsSold,
    sharePct: parseFloat(r.revenueSharePct) || 0,
    marginPct: r.grossMarginPct?.value ? parseFloat(r.grossMarginPct.value) : null,
    marginDelta: r.marginDeltaPoints ? parseFloat(r.marginDeltaPoints) : null,
  }));

  return (
    <div className="bg-white dark:bg-[#121214] rounded-xl border border-black/8 dark:border-white/10 shadow-xs p-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h2 className="text-base font-bold text-black dark:text-white">Regions</h2>
          <p className="text-xs text-[#757575] dark:text-zinc-400 mt-0.5">Performance breakdown by sales region.</p>
        </div>

        <div className="flex items-center gap-2">
          {/* Metric Selector */}
          <div className="inline-flex rounded-lg border border-black/10 dark:border-white/15 p-0.5 bg-[#f6f5f4] dark:bg-[#18181b] text-xs font-medium">
            <button
              type="button"
              onClick={() => setMetric('revenue')}
              className={`px-2.5 py-1 rounded-md transition-colors cursor-pointer ${
                metric === 'revenue' ? 'bg-white dark:bg-[#27272a] shadow-2xs font-semibold text-black dark:text-white' : 'text-[#757575] dark:text-zinc-400 hover:text-black dark:hover:text-white'
              }`}
            >
              Revenue
            </button>
            <button
              type="button"
              onClick={() => setMetric('grossProfit')}
              className={`px-2.5 py-1 rounded-md transition-colors cursor-pointer ${
                metric === 'grossProfit' ? 'bg-white dark:bg-[#27272a] shadow-2xs font-semibold text-black dark:text-white' : 'text-[#757575] dark:text-zinc-400 hover:text-black dark:hover:text-white'
              }`}
            >
              Profit
            </button>
            <button
              type="button"
              onClick={() => setMetric('unitsSold')}
              className={`px-2.5 py-1 rounded-md transition-colors cursor-pointer ${
                metric === 'unitsSold' ? 'bg-white dark:bg-[#27272a] shadow-2xs font-semibold text-black dark:text-white' : 'text-[#757575] dark:text-zinc-400 hover:text-black dark:hover:text-white'
              }`}
            >
              Units
            </button>
          </div>

          <button
            type="button"
            onClick={() => setViewAsTable(!viewAsTable)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-black/10 dark:border-white/15 text-xs font-semibold text-[#37352f] dark:text-zinc-300 hover:bg-black/5 dark:hover:bg-white/5 transition-colors cursor-pointer"
          >
            {viewAsTable ? <BarChart2 className="w-3.5 h-3.5" /> : <Table className="w-3.5 h-3.5" />}
            {viewAsTable ? 'Chart' : 'Table'}
          </button>
        </div>
      </div>

      {viewAsTable ? (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs" aria-label="Regional Sales Table">
            <thead>
              <tr className="bg-[#faf9f8] dark:bg-[#18181b] border-b border-black/8 dark:border-white/10 text-[#757575] dark:text-zinc-400 font-semibold uppercase tracking-wider">
                <th className="py-2.5 px-3">Region</th>
                <th className="py-2.5 px-3 text-right">Revenue</th>
                <th className="py-2.5 px-3 text-right">Share</th>
                <th className="py-2.5 px-3 text-right">Gross Profit</th>
                <th className="py-2.5 px-3 text-right">Margin %</th>
                <th className="py-2.5 px-3 text-right">Margin Delta (vs Overall)</th>
                <th className="py-2.5 px-3 text-right">Units</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-black/5 dark:divide-white/10">
              {regions.map((r) => {
                const deltaNum = r.marginDeltaPoints ? parseFloat(r.marginDeltaPoints) : null;
                return (
                  <tr key={r.name} className="hover:bg-black/[0.02] dark:hover:bg-white/[0.04]">
                    <td className="py-2.5 px-3 font-semibold text-black dark:text-white">{r.name}</td>
                    <td className="py-2.5 px-3 text-right font-mono text-black dark:text-white">{formatMoney(r.revenue, currency)}</td>
                    <td className="py-2.5 px-3 text-right font-mono text-[#757575] dark:text-zinc-400">{r.revenueSharePct}%</td>
                    <td className="py-2.5 px-3 text-right font-mono">
                      <span className={parseFloat(r.grossProfit) < 0 ? 'text-red-600 dark:text-red-400' : 'text-black dark:text-white'}>
                        {formatMoney(r.grossProfit, currency)}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono text-black dark:text-white">
                      {r.grossMarginPct?.value ? formatPercent(r.grossMarginPct.value) : '—'}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono">
                      {deltaNum !== null ? (
                        <span
                          className={`inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[11px] font-semibold ${
                            deltaNum > 0
                              ? 'bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/50'
                              : deltaNum < 0
                              ? 'bg-red-50 dark:bg-red-950/50 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-800/50'
                              : 'text-[#757575] dark:text-zinc-400'
                          }`}
                        >
                          {deltaNum > 0 ? `+${r.marginDeltaPoints}` : r.marginDeltaPoints} pts
                        </span>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono text-black dark:text-white">{r.unitsSold.toLocaleString()}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="h-64 w-full pt-2" aria-label="Regional Breakdown Chart">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData} margin={{ top: 10, right: 10, left: 10, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(128,128,128,0.15)" />
              <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#888' }} stroke="rgba(128,128,128,0.25)" />
              <YAxis
                tick={{ fontSize: 11, fill: '#888' }}
                stroke="rgba(128,128,128,0.25)"
                tickFormatter={(val) => (val >= 1000 ? `${(val / 1000).toFixed(0)}k` : val)}
              />
              <Tooltip
                content={({ active, payload, label }) => {
                  if (active && payload && payload.length) {
                    const row = chartData.find((d) => d.name === label);
                    return (
                      <div className="bg-white dark:bg-[#18181b] p-3 rounded-lg border border-black/15 dark:border-white/15 shadow-md text-xs space-y-1">
                        <div className="font-bold text-black dark:text-white pb-1 border-b border-black/10 dark:border-white/10">{label}</div>
                        <div className="text-[#0075de] dark:text-[#38bdf8] font-medium flex justify-between gap-4">
                          <span>Revenue:</span>
                          <span className="font-mono">{row ? formatMoney(String(row.revenue), currency) : '—'}</span>
                        </div>
                        <div className="text-emerald-700 dark:text-emerald-400 font-medium flex justify-between gap-4">
                          <span>Gross Profit:</span>
                          <span className="font-mono">{row ? formatMoney(String(row.grossProfit), currency) : '—'}</span>
                        </div>
                        <div className="text-[#37352f] dark:text-zinc-300 flex justify-between gap-4">
                          <span>Margin:</span>
                          <span className="font-mono">{row?.marginPct !== null && row?.marginPct !== undefined ? `${row.marginPct}%` : '—'}</span>
                        </div>
                        {row?.marginDelta !== null && row?.marginDelta !== undefined && (
                          <div className="text-[#757575] dark:text-zinc-400 flex justify-between gap-4">
                            <span>Delta vs Overall:</span>
                            <span className={`font-mono font-semibold ${row.marginDelta > 0 ? 'text-emerald-700 dark:text-emerald-400' : 'text-red-700 dark:text-red-400'}`}>
                              {row.marginDelta > 0 ? `+${row.marginDelta}` : row.marginDelta} pts
                            </span>
                          </div>
                        )}
                      </div>
                    );
                  }
                  return null;
                }}
              />
              <Bar
                dataKey={metric}
                fill={metric === 'grossProfit' ? '#10b981' : '#0075de'}
                radius={[4, 4, 0, 0]}
                maxBarSize={48}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
};

