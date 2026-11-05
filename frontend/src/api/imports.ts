import { apiFetch } from './apiFetch';
import type {
  ConfirmImportRequest,
  ConfirmImportResponse,
  ImportJobSummaryDto,
  PreviewResponse,
} from './types';

export interface GetPreviewParams {
  source?: 'STAGED' | 'COMMITTED';
  status?: 'ALL' | 'VALID' | 'INVALID' | 'DUPLICATE_CANDIDATE';
  page?: number;
  size?: number;
}

export async function getPreview(datasetId: string, params: GetPreviewParams = {}): Promise<PreviewResponse> {
  const query = new URLSearchParams();
  if (params.source) query.set('source', params.source);
  if (params.status) query.set('status', params.status);
  if (params.page !== undefined) query.set('page', params.page.toString());
  if (params.size !== undefined) query.set('size', params.size.toString());

  const qs = query.toString();
  return apiFetch<PreviewResponse>(`/api/datasets/${datasetId}/preview${qs ? `?${qs}` : ''}`);
}

export async function confirmImport(
  datasetId: string,
  request: ConfirmImportRequest
): Promise<ConfirmImportResponse> {
  return apiFetch<ConfirmImportResponse>(`/api/datasets/${datasetId}/imports/confirm`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function cancelImport(datasetId: string, jobId: string): Promise<void> {
  return apiFetch<void>(`/api/datasets/${datasetId}/imports/${jobId}/cancel`, {
    method: 'POST',
  });
}

export async function getImportHistory(datasetId: string): Promise<ImportJobSummaryDto[]> {
  return apiFetch<ImportJobSummaryDto[]>(`/api/datasets/${datasetId}/imports`);
}
