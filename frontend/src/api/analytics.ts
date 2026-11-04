import { apiFetch } from './apiFetch';
import type { SummaryResponse } from './types';

export async function fetchSummary(
  datasetId: string,
  from?: string,
  to?: string,
  signal?: AbortSignal
): Promise<SummaryResponse> {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);

  const query = params.toString();
  const url = `/api/datasets/${datasetId}/analytics/summary${query ? `?${query}` : ''}`;

  return apiFetch<SummaryResponse>(url, { signal });
}
