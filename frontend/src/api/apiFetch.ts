import type { ApiProblemDetail } from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly requestId?: string;
  readonly detail?: string;
  readonly fieldErrors?: Array<{ field: string; code: string; message: string }>;

  constructor(status: number, problem?: ApiProblemDetail) {
    super(problem?.detail || problem?.title || `HTTP Error ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem?.code;
    this.requestId = problem?.requestId;
    this.detail = problem?.detail;
    this.fieldErrors = problem?.errors;
  }
}

export async function apiFetch<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }

  const response = await fetch(endpoint, {
    ...options,
    headers,
    credentials: 'same-origin',
  });

  if (!response.ok) {
    let problem: ApiProblemDetail | undefined;
    try {
      const json = await response.json();
      problem = json as ApiProblemDetail;
    } catch {
      // non-JSON response body
    }
    throw new ApiError(response.status, problem);
  }

  if (response.status === 204) {
    return null as T;
  }

  return response.json() as Promise<T>;
}
