import React, { useState } from 'react';
import type { TrendPoint } from '../../api/types';
import { formatMoney, formatPercent } from '../../lib/format';
import {
  ResponsiveContainer,
  ComposedChart,
  Bar,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
} from 'recharts';
import { Table, BarChart2 } from 'lucide-react';

interface TrendChartProps {
  points: TrendPoint[];
  currency?: string;
  isLoading?: boolean;
}

export const TrendChart: React.FC<TrendChartProps> = ({
  points,
  currency = 'INR',
  isLoading,
}) => {
  const [viewAsTable, setViewAsTable] = useState<boolean>(false);

  if (isLoading) {
    return (
      <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 h-80 flex items-center justify-center animate-pulse">
        <div className="text-xs text-[#757575]">Loading monthly trend data...</div>
      </div>
    );
  }

  if (!points || points.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-black/8 shadow-xs p-6 h-80 flex flex-col items-center justify-center text-center">
        <BarChart2 className="w-8 h-8 text-[#757575] mb-2" />
        <span className="text-sm font-semibold text-black">No trend data available</span>
        <span className="text-xs text-[#757575] mt-1">Adjust filters to see monthly performance.</span>
      </div>
    );
  }

  // Format data for Recharts (parse number only for plotting)
  const chartData = points.map((p) => ({
    month: p.month,
    revenue: parseFloat(p.revenue) || 0,
    grossProfit: parseFloat(p.grossProfit) || 0,
    unitsSold: p.unitsSold,
    marginPct: p.grossMarginPct?.value ? parseFloat(p.grossMarginPct.value) : null,
    growthPct: p.revenueGrowthPct?.value ? parseFloat(p.revenueGrowthPct.value) : null,
  }));

  return (
    <div className="bg-white dark:bg-[#121214] rounded-xl border border-black/8 dark:border-white/10 shadow-xs p-6">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-base font-bold text-black dark:text-white">Monthly Trends</h2>
          <p className="text-xs text-[#757575] dark:text-zinc-400 mt-0.5">
            Gross revenue bars and gross profit line across the selected time period.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setViewAsTable(!viewAsTable)}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-black/10 dark:border-white/15 text-xs font-semibold text-[#37352f] dark:text-zinc-300 hover:bg-black/5 dark:hover:bg-white/5 transition-colors cursor-pointer"
        >
          {viewAsTable ? (
            <>
              <BarChart2 className="w-3.5 h-3.5" /> View Chart
            </>
          ) : (
            <>
              <Table className="w-3.5 h-3.5" /> View as Table
            </>
          )}
        </button>
      </div>

      {viewAsTable ? (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs" aria-label="Monthly Sales and Profit Table">
            <thead>
              <tr className="bg-[#faf9f8] dark:bg-[#18181b] border-b border-black/8 dark:border-white/10 text-[#757575] dark:text-zinc-400 font-semibold uppercase tracking-wider">
                <th className="py-2.5 px-3">Month</th>
                <th className="py-2.5 px-3 text-right">Revenue</th>
                <th className="py-2.5 px-3 text-right">Gross Profit</th>
                <th className="py-2.5 px-3 text-right">Units</th>
                <th className="py-2.5 px-3 text-right">Margin %</th>
                <th className="py-2.5 px-3 text-right">MoM Growth</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-black/5 dark:divide-white/10">
              {points.map((p) => (
                <tr key={p.month} className="hover:bg-black/[0.02] dark:hover:bg-white/[0.04]">
                  <td className="py-2.5 px-3 font-semibold text-black dark:text-white">{p.month}</td>
                  <td className="py-2.5 px-3 text-right font-mono text-black dark:text-white">{formatMoney(p.revenue, currency)}</td>
                  <td className="py-2.5 px-3 text-right font-mono">
                    <span className={parseFloat(p.grossProfit) < 0 ? 'text-red-600 dark:text-red-400' : 'text-black dark:text-white'}>
                      {formatMoney(p.grossProfit, currency)}
                    </span>
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono text-black dark:text-white">{p.unitsSold.toLocaleString()}</td>
                  <td className="py-2.5 px-3 text-right font-mono text-black dark:text-white">
                    {p.grossMarginPct?.value ? formatPercent(p.grossMarginPct.value) : '—'}
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono">
                    {p.revenueGrowthPct?.value ? (
                      <span className={parseFloat(p.revenueGrowthPct.value) < 0 ? 'text-red-600 dark:text-red-400' : 'text-emerald-700 dark:text-emerald-400'}>
                        {parseFloat(p.revenueGrowthPct.value) > 0 ? `+${p.revenueGrowthPct.value}%` : `${p.revenueGrowthPct.value}%`}
                      </span>
                    ) : (
                      <span className="text-[#999] dark:text-zinc-500">{p.revenueGrowthPct?.unavailableReason || '—'}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="h-72 w-full pt-2" aria-label="Monthly Revenue and Gross Profit Chart">
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={chartData} margin={{ top: 10, right: 10, left: 10, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(128,128,128,0.15)" />
              <XAxis dataKey="month" tick={{ fontSize: 11, fill: '#888' }} stroke="rgba(128,128,128,0.25)" />
              <YAxis
                yAxisId="left"
                tick={{ fontSize: 11, fill: '#888' }}
                stroke="rgba(128,128,128,0.25)"
                tickFormatter={(val) => (val >= 1000 ? `${(val / 1000).toFixed(0)}k` : val)}
              />
              <Tooltip
                content={({ active, payload, label }) => {
                  if (active && payload && payload.length) {
                    const rev = payload.find((p) => p.dataKey === 'revenue')?.value as number | undefined;
                    const profit = payload.find((p) => p.dataKey === 'grossProfit')?.value as number | undefined;
                    return (
                      <div className="bg-white dark:bg-[#18181b] p-3 rounded-lg border border-black/15 dark:border-white/15 shadow-md text-xs space-y-1">
                        <div className="font-bold text-black dark:text-white pb-1 border-b border-black/10 dark:border-white/10">{label}</div>
                        <div className="text-[#0075de] dark:text-[#38bdf8] font-medium flex justify-between gap-4">
                          <span>Revenue:</span>
                          <span className="font-mono">{rev !== undefined ? formatMoney(String(rev), currency) : '—'}</span>
                        </div>
                        <div className="text-emerald-700 dark:text-emerald-400 font-medium flex justify-between gap-4">
                          <span>Gross Profit:</span>
                          <span className="font-mono">{profit !== undefined ? formatMoney(String(profit), currency) : '—'}</span>
                        </div>
                      </div>
                    );
                  }
                  return null;
                }}
              />
              <Legend
                verticalAlign="top"
                align="right"
                wrapperStyle={{ fontSize: 11, paddingBottom: 10 }}
              />
              <Bar
                yAxisId="left"
                dataKey="revenue"
                name="Revenue"
                fill="#0075de"
                radius={[4, 4, 0, 0]}
                maxBarSize={48}
              />
              <Line
                yAxisId="left"
                type="monotone"
                dataKey="grossProfit"
                name="Gross Profit"
                stroke="#10b981"
                strokeWidth={2.5}
                dot={{ r: 4, fill: '#10b981' }}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
};

