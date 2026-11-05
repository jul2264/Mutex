import type { AnalyticsFilterParams } from '../api/types';

export function parseFilterParams(searchParams: URLSearchParams): AnalyticsFilterParams {
  const from = searchParams.get('from') || undefined;
  const to = searchParams.get('to') || undefined;
  const product = searchParams.get('product') || undefined;
  const compare = searchParams.get('compare') as 'NONE' | 'PREVIOUS_PERIOD' | null;
  const sort = searchParams.get('sort') || undefined;
  const direction = (searchParams.get('direction') as 'asc' | 'desc') || undefined;
  const limitStr = searchParams.get('limit');
  const limit = limitStr ? parseInt(limitStr, 10) : undefined;
  const rankBy = (searchParams.get('rankBy') as 'REVENUE' | 'PROFIT') || undefined;

  const category = searchParams.getAll('category').filter(Boolean);
  const region = searchParams.getAll('region').filter(Boolean);

  return {
    from,
    to,
    product,
    compare: compare === 'PREVIOUS_PERIOD' ? 'PREVIOUS_PERIOD' : undefined,
    sort,
    direction,
    limit,
    rankBy,
    category: category.length > 0 ? category : undefined,
    region: region.length > 0 ? region : undefined,
  };
}

export function serializeFilterParams(filter: AnalyticsFilterParams): URLSearchParams {
  const params = new URLSearchParams();

  if (filter.from) params.set('from', filter.from);
  if (filter.to) params.set('to', filter.to);
  if (filter.product && filter.product.trim()) params.set('product', filter.product.trim());
  if (filter.compare && filter.compare !== 'NONE') params.set('compare', filter.compare);
  if (filter.sort) params.set('sort', filter.sort);
  if (filter.direction) params.set('direction', filter.direction);
  if (filter.limit) params.set('limit', String(filter.limit));
  if (filter.rankBy) params.set('rankBy', filter.rankBy);

  if (filter.category) {
    for (const cat of filter.category) {
      params.append('category', cat);
    }
  }

  if (filter.region) {
    for (const reg of filter.region) {
      params.append('region', reg);
    }
  }

  return params;
}

export type DatePreset = 'all' | 'last30' | 'lastMonth' | 'custom';

export function getDatePreset(from?: string, to?: string): DatePreset {
  if (!from && !to) return 'all';

  const today = new Date();
  const d30 = new Date(today);
  d30.setDate(d30.getDate() - 30);
  const d30Str = d30.toISOString().slice(0, 10);
  const todayStr = today.toISOString().slice(0, 10);

  if (from === d30Str && to === todayStr) return 'last30';

  const firstOfThisMonth = new Date(today.getFullYear(), today.getMonth(), 1);
  const lastOfPrevMonth = new Date(firstOfThisMonth.getTime() - 86400000);
  const firstOfPrevMonth = new Date(lastOfPrevMonth.getFullYear(), lastOfPrevMonth.getMonth(), 1);

  const prevMonthStart = firstOfPrevMonth.toISOString().slice(0, 10);
  const prevMonthEnd = lastOfPrevMonth.toISOString().slice(0, 10);

  if (from === prevMonthStart && to === prevMonthEnd) return 'lastMonth';

  return 'custom';
}

export function computePresetDates(preset: DatePreset): { from?: string; to?: string } {
  if (preset === 'all') return { from: undefined, to: undefined };

  const today = new Date();

  if (preset === 'last30') {
    const d30 = new Date(today);
    d30.setDate(d30.getDate() - 30);
    return {
      from: d30.toISOString().slice(0, 10),
      to: today.toISOString().slice(0, 10),
    };
  }

  if (preset === 'lastMonth') {
    const firstOfThisMonth = new Date(today.getFullYear(), today.getMonth(), 1);
    const lastOfPrevMonth = new Date(firstOfThisMonth.getTime() - 86400000);
    const firstOfPrevMonth = new Date(lastOfPrevMonth.getFullYear(), lastOfPrevMonth.getMonth(), 1);
    return {
      from: firstOfPrevMonth.toISOString().slice(0, 10),
      to: lastOfPrevMonth.toISOString().slice(0, 10),
    };
  }

  return {};
}
