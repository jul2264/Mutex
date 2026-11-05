import { apiFetch, ApiError, getOrFetchCsrfToken } from './apiFetch';
import type {
  ConfirmImportRequest,
  ConfirmImportResponse,
  ImportJobStatusResponse,
  ImportJobSummaryDto,
  PreviewResponse,
  UploadResponse,
} from './types';

export interface GetPreviewParams {
  source?: 'STAGED' | 'COMMITTED';
  status?: 'ALL' | 'VALID' | 'INVALID' | 'DUPLICATE_CANDIDATE';
  page?: number;
  size?: number;
}

export interface UploadDatasetPayload {
  name: string;
  file: File;
  dateFormat?: 'ISO' | 'DMY' | 'MDY';
  policy?: 'IMPORT_VALID_ONLY' | 'REJECT_FILE_ON_ANY_ERROR';
  currency?: string;
  onProgress?: (percent: number) => void;
}

export async function uploadDatasetFile(payload: UploadDatasetPayload): Promise<UploadResponse> {
  const token = await getOrFetchCsrfToken();
  const formData = new FormData();
  formData.append('file', payload.file);
  formData.append('name', payload.name.trim());
  formData.append('dateFormat', payload.dateFormat || 'ISO');
  formData.append('policy', payload.policy || 'IMPORT_VALID_ONLY');
  formData.append('currency', payload.currency || 'INR');

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/datasets/upload');
    xhr.withCredentials = true;
    xhr.setRequestHeader('Accept', 'application/json');

    if (token) {
      xhr.setRequestHeader('X-XSRF-TOKEN', token);
    }

    if (xhr.upload && payload.onProgress) {
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          const percent = Math.round((event.loaded / event.total) * 100);
          payload.onProgress?.(percent);
        }
      };
    }

    xhr.onload = () => {
      try {
        const json = xhr.responseText ? JSON.parse(xhr.responseText) : {};
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(json as UploadResponse);
        } else {
          reject(new ApiError(xhr.status, json));
        }
      } catch {
        reject(new ApiError(xhr.status, { title: 'Invalid JSON response from server' }));
      }
    };

    xhr.onerror = () => {
      reject(new ApiError(0, { title: 'Network error occurred during file upload' }));
    };

    xhr.send(formData);
  });
}

export async function getJobStatus(datasetId: string, jobId: string): Promise<ImportJobStatusResponse> {
  return apiFetch<ImportJobStatusResponse>(`/api/datasets/${datasetId}/imports/${jobId}`);
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
