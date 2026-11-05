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
    <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h2 className="text-base font-bold text-black">Regions</h2>
          <p className="text-xs text-[#757575] mt-0.5">Performance breakdown by sales region.</p>
        </div>

        <div className="flex items-center gap-2">
          {/* Metric Selector */}
          <div className="inline-flex rounded-lg border border-black/10 p-0.5 bg-[#f6f5f4] text-xs font-medium">
            <button
              type="button"
              onClick={() => setMetric('revenue')}
              className={`px-2.5 py-1 rounded-md transition-colors ${
                metric === 'revenue' ? 'bg-white shadow-2xs font-semibold text-black' : 'text-[#757575] hover:text-black'
              }`}
            >
              Revenue
            </button>
            <button
              type="button"
              onClick={() => setMetric('grossProfit')}
              className={`px-2.5 py-1 rounded-md transition-colors ${
                metric === 'grossProfit' ? 'bg-white shadow-2xs font-semibold text-black' : 'text-[#757575] hover:text-black'
              }`}
            >
              Profit
            </button>
            <button
              type="button"
              onClick={() => setMetric('unitsSold')}
              className={`px-2.5 py-1 rounded-md transition-colors ${
                metric === 'unitsSold' ? 'bg-white shadow-2xs font-semibold text-black' : 'text-[#757575] hover:text-black'
              }`}
            >
              Units
            </button>
          </div>

          <button
            type="button"
            onClick={() => setViewAsTable(!viewAsTable)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-black/10 text-xs font-semibold text-[#37352f] hover:bg-black/5 transition-colors"
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
              <tr className="bg-[#faf9f8] border-b border-black/8 text-[#757575] font-semibold uppercase tracking-wider">
                <th className="py-2.5 px-3">Region</th>
                <th className="py-2.5 px-3 text-right">Revenue</th>
                <th className="py-2.5 px-3 text-right">Share</th>
                <th className="py-2.5 px-3 text-right">Gross Profit</th>
                <th className="py-2.5 px-3 text-right">Margin %</th>
                <th className="py-2.5 px-3 text-right">Margin Delta (vs Overall)</th>
                <th className="py-2.5 px-3 text-right">Units</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-black/5">
              {regions.map((r) => {
                const deltaNum = r.marginDeltaPoints ? parseFloat(r.marginDeltaPoints) : null;
                return (
                  <tr key={r.name} className="hover:bg-black/[0.02]">
                    <td className="py-2.5 px-3 font-semibold text-black">{r.name}</td>
                    <td className="py-2.5 px-3 text-right font-mono">{formatMoney(r.revenue, currency)}</td>
                    <td className="py-2.5 px-3 text-right font-mono text-[#757575]">{r.revenueSharePct}%</td>
                    <td className="py-2.5 px-3 text-right font-mono">
                      <span className={parseFloat(r.grossProfit) < 0 ? 'text-red-600' : ''}>
                        {formatMoney(r.grossProfit, currency)}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono">
                      {r.grossMarginPct?.value ? formatPercent(r.grossMarginPct.value) : '—'}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono">
                      {deltaNum !== null ? (
                        <span
                          className={`inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[11px] font-semibold ${
                            deltaNum > 0
                              ? 'bg-emerald-50 text-emerald-700'
                              : deltaNum < 0
                              ? 'bg-red-50 text-red-700'
                              : 'text-[#757575]'
                          }`}
                        >
                          {deltaNum > 0 ? `+${r.marginDeltaPoints}` : r.marginDeltaPoints} pts
                        </span>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono">{r.unitsSold.toLocaleString()}</td>
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
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(0,0,0,0.06)" />
              <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#757575' }} stroke="rgba(0,0,0,0.15)" />
              <YAxis
                tick={{ fontSize: 11, fill: '#757575' }}
                stroke="rgba(0,0,0,0.15)"
                tickFormatter={(val) => (val >= 1000 ? `${(val / 1000).toFixed(0)}k` : val)}
              />
              <Tooltip
                content={({ active, payload, label }) => {
                  if (active && payload && payload.length) {
                    const row = chartData.find((d) => d.name === label);
                    return (
                      <div className="bg-white p-3 rounded-lg border border-black/15 shadow-md text-xs space-y-1">
                        <div className="font-bold text-black pb-1 border-b border-black/10">{label}</div>
                        <div className="text-[#0075de] font-medium flex justify-between gap-4">
                          <span>Revenue:</span>
                          <span className="font-mono">{row ? formatMoney(String(row.revenue), currency) : '—'}</span>
                        </div>
                        <div className="text-emerald-700 font-medium flex justify-between gap-4">
                          <span>Gross Profit:</span>
                          <span className="font-mono">{row ? formatMoney(String(row.grossProfit), currency) : '—'}</span>
                        </div>
                        <div className="text-[#37352f] flex justify-between gap-4">
                          <span>Margin:</span>
                          <span className="font-mono">{row?.marginPct !== null && row?.marginPct !== undefined ? `${row.marginPct}%` : '—'}</span>
                        </div>
                        {row?.marginDelta !== null && row?.marginDelta !== undefined && (
                          <div className="text-[#757575] flex justify-between gap-4">
                            <span>Delta vs Overall:</span>
                            <span className={`font-mono font-semibold ${row.marginDelta > 0 ? 'text-emerald-700' : 'text-red-700'}`}>
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

