import type { AnalyticsFilterParams, ApiProblemDetail } from './types';
import { ApiError } from './apiFetch';

export function buildExportUrl(
  datasetId: string,
  format: 'csv' | 'xlsx',
  type: 'records' | 'summary',
  filter?: AnalyticsFilterParams
): string {
  const search = new URLSearchParams();
  search.set('format', format);
  search.set('type', type);

  if (filter) {
    if (filter.from) search.set('from', filter.from);
    if (filter.to) search.set('to', filter.to);
    if (filter.product && filter.product.trim()) search.set('product', filter.product.trim());
    if (filter.compare && filter.compare !== 'NONE') search.set('compare', filter.compare);

    if (filter.category && filter.category.length > 0) {
      for (const cat of filter.category) {
        search.append('category', cat);
      }
    }
    if (filter.region && filter.region.length > 0) {
      for (const reg of filter.region) {
        search.append('region', reg);
      }
    }
  }

  return `/api/datasets/${datasetId}/reports/export?${search.toString()}`;
}

export async function downloadExport(
  datasetId: string,
  format: 'csv' | 'xlsx',
  type: 'records' | 'summary',
  filter?: AnalyticsFilterParams
): Promise<void> {
  const url = buildExportUrl(datasetId, format, type, filter);

  const response = await fetch(url, {
    method: 'GET',
    credentials: 'include',
  });

  if (!response.ok) {
    let errorDetail: ApiProblemDetail | null = null;
    try {
      errorDetail = (await response.json()) as ApiProblemDetail;
    } catch {
      // not JSON
    }

    if (errorDetail && errorDetail.code) {
      throw new ApiError(response.status, errorDetail);
    }

    throw new ApiError(response.status, {
      code: 'EXPORT_FAILED',
      detail: `Export request failed with status ${response.status}`,
    });
  }

  // Determine filename from Content-Disposition header if available
  let filename = `export_${datasetId}_${type}.${format}`;
  const disposition = response.headers.get('Content-Disposition');
  if (disposition && disposition.includes('filename=')) {
    const match = disposition.match(/filename="?([^";]+)"?/);
    if (match && match[1]) {
      filename = match[1];
    }
  }

  if (typeof window !== 'undefined' && typeof document !== 'undefined') {
    const blob = await response.blob();
    const downloadUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(downloadUrl);
  }
}
