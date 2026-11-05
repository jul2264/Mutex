import { apiFetch } from './apiFetch';
import type {
  AnalyticsFilterParams,
  CategoriesResponse,
  FilterOptionsResponse,
  RegionsResponse,
  SummaryResponse,
  TopProductsResponse,
  TrendsResponse,
} from './types';

function buildFilterQuery(params?: AnalyticsFilterParams): string {
  if (!params) return '';
  const search = new URLSearchParams();

  if (params.from) search.set('from', params.from);
  if (params.to) search.set('to', params.to);
  if (params.product && params.product.trim()) search.set('product', params.product.trim());
  if (params.compare && params.compare !== 'NONE') search.set('compare', params.compare);
  if (params.sort) search.set('sort', params.sort);
  if (params.direction) search.set('direction', params.direction);
  if (params.limit) search.set('limit', String(params.limit));
  if (params.rankBy) search.set('rankBy', params.rankBy);

  if (params.category && params.category.length > 0) {
    for (const cat of params.category) {
      search.append('category', cat);
    }
  }

  if (params.region && params.region.length > 0) {
    for (const reg of params.region) {
      search.append('region', reg);
    }
  }

  const qs = search.toString();
  return qs ? `?${qs}` : '';
}

export async function fetchSummary(
  datasetId: string,
  filter?: AnalyticsFilterParams,
  signal?: AbortSignal
): Promise<SummaryResponse> {
  const query = buildFilterQuery(filter);
  return apiFetch<SummaryResponse>(`/api/datasets/${datasetId}/analytics/summary${query}`, { signal });
}

export async function fetchTrends(
  datasetId: string,
  filter?: AnalyticsFilterParams,
  signal?: AbortSignal
): Promise<TrendsResponse> {
  const query = buildFilterQuery(filter);
  return apiFetch<TrendsResponse>(`/api/datasets/${datasetId}/analytics/trends${query}`, { signal });
}

export async function fetchCategories(
  datasetId: string,
  filter?: AnalyticsFilterParams,
  signal?: AbortSignal
): Promise<CategoriesResponse> {
  const query = buildFilterQuery(filter);
  return apiFetch<CategoriesResponse>(`/api/datasets/${datasetId}/analytics/categories${query}`, { signal });
}

export async function fetchRegions(
  datasetId: string,
  filter?: AnalyticsFilterParams,
  signal?: AbortSignal
): Promise<RegionsResponse> {
  const query = buildFilterQuery(filter);
  return apiFetch<RegionsResponse>(`/api/datasets/${datasetId}/analytics/regions${query}`, { signal });
}

export async function fetchTopProducts(
  datasetId: string,
  filter?: AnalyticsFilterParams,
  signal?: AbortSignal
): Promise<TopProductsResponse> {
  const query = buildFilterQuery(filter);
  return apiFetch<TopProductsResponse>(`/api/datasets/${datasetId}/analytics/top-products${query}`, { signal });
}

export async function fetchFilterOptions(
  datasetId: string,
  signal?: AbortSignal
): Promise<FilterOptionsResponse> {
  return apiFetch<FilterOptionsResponse>(`/api/datasets/${datasetId}/analytics/filter-options`, { signal });
}
