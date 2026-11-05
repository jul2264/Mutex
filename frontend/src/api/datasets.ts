import { apiFetch } from './apiFetch';
import type { DatasetDetailResponse, DatasetResponse, PageResponse } from './types';

export interface ListDatasetsParams {
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'asc' | 'desc';
}

export async function listDatasets(params: ListDatasetsParams = {}): Promise<PageResponse<DatasetResponse>> {
  const query = new URLSearchParams();
  if (params.page !== undefined) query.set('page', params.page.toString());
  if (params.size !== undefined) query.set('size', params.size.toString());
  if (params.sort) query.set('sort', params.sort);
  if (params.direction) query.set('direction', params.direction);

  const qs = query.toString();
  return apiFetch<PageResponse<DatasetResponse>>(`/api/datasets${qs ? `?${qs}` : ''}`);
}

export async function getDataset(id: string): Promise<DatasetDetailResponse> {
  return apiFetch<DatasetDetailResponse>(`/api/datasets/${id}`);
}

export async function renameDataset(id: string, name: string): Promise<DatasetResponse> {
  return apiFetch<DatasetResponse>(`/api/datasets/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ name }),
  });
}

export async function deleteDataset(id: string, confirmName: string): Promise<void> {
  const query = new URLSearchParams({ confirmName });
  return apiFetch<void>(`/api/datasets/${id}?${query.toString()}`, {
    method: 'DELETE',
  });
}

