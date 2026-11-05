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

interface CategoryChartProps {
  categories: BreakdownRow[];
  currency?: string;
  isLoading?: boolean;
}

export const CategoryChart: React.FC<CategoryChartProps> = ({
  categories,
  currency = 'INR',
  isLoading,
}) => {
  const [viewAsTable, setViewAsTable] = useState<boolean>(false);
  const [metric, setMetric] = useState<'revenue' | 'grossProfit' | 'unitsSold'>('revenue');

  if (isLoading) {
    return (
      <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 h-80 flex items-center justify-center animate-pulse">
        <div className="text-xs text-[#757575]">Loading category performance...</div>
      </div>
    );
  }

  if (!categories || categories.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 h-80 flex flex-col items-center justify-center text-center">
        <BarChart2 className="w-8 h-8 text-[#757575] mb-2" />
        <span className="text-sm font-semibold text-black">No category data</span>
        <span className="text-xs text-[#757575] mt-1">Adjust filters to see category breakdown.</span>
      </div>
    );
  }

  const chartData = categories.map((c) => ({
    name: c.name,
    revenue: parseFloat(c.revenue) || 0,
    grossProfit: parseFloat(c.grossProfit) || 0,
    unitsSold: c.unitsSold,
    sharePct: parseFloat(c.revenueSharePct) || 0,
    marginPct: c.grossMarginPct?.value ? parseFloat(c.grossMarginPct.value) : null,
  }));

  return (
    <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h2 className="text-base font-bold text-black">Categories</h2>
          <p className="text-xs text-[#757575] mt-0.5">Performance breakdown by product category.</p>
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
          <table className="w-full text-left text-xs" aria-label="Category Sales Table">
            <thead>
              <tr className="bg-[#faf9f8] border-b border-black/8 text-[#757575] font-semibold uppercase tracking-wider">
                <th className="py-2.5 px-3">Category</th>
                <th className="py-2.5 px-3 text-right">Revenue</th>
                <th className="py-2.5 px-3 text-right">Revenue Share</th>
                <th className="py-2.5 px-3 text-right">Gross Profit</th>
                <th className="py-2.5 px-3 text-right">Margin %</th>
                <th className="py-2.5 px-3 text-right">Units</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-black/5">
              {categories.map((c) => (
                <tr key={c.name} className="hover:bg-black/[0.02]">
                  <td className="py-2.5 px-3 font-semibold text-black">{c.name}</td>
                  <td className="py-2.5 px-3 text-right font-mono">{formatMoney(c.revenue, currency)}</td>
                  <td className="py-2.5 px-3 text-right font-mono text-[#757575]">{c.revenueSharePct}%</td>
                  <td className="py-2.5 px-3 text-right font-mono">
                    <span className={parseFloat(c.grossProfit) < 0 ? 'text-red-600' : ''}>
                      {formatMoney(c.grossProfit, currency)}
                    </span>
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono">
                    {c.grossMarginPct?.value ? formatPercent(c.grossMarginPct.value) : '—'}
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono">{c.unitsSold.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="h-64 w-full pt-2" aria-label="Category Breakdown Chart">
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
                        <div className="text-[#757575] flex justify-between gap-4">
                          <span>Revenue Share:</span>
                          <span className="font-mono">{row ? `${row.sharePct}%` : '—'}</span>
                        </div>
                        <div className="text-emerald-700 font-medium flex justify-between gap-4">
                          <span>Gross Profit:</span>
                          <span className="font-mono">{row ? formatMoney(String(row.grossProfit), currency) : '—'}</span>
                        </div>
                        <div className="text-[#37352f] flex justify-between gap-4">
                          <span>Margin:</span>
                          <span className="font-mono">{row?.marginPct !== null && row?.marginPct !== undefined ? `${row.marginPct}%` : '—'}</span>
                        </div>
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
