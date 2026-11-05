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

function getCookie(name: string): string | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[2]) : null;
}

let csrfBootstrapPromise: Promise<string | null> | null = null;

async function getOrFetchCsrfToken(): Promise<string | null> {
  let token = getCookie('XSRF-TOKEN');
  if (token) return token;

  if (!csrfBootstrapPromise) {
    csrfBootstrapPromise = fetch('/api/auth/csrf', { credentials: 'same-origin' })
      .then((res) => (res.ok ? res.json() : null))
      .then((data: { token?: string } | null) => {
        csrfBootstrapPromise = null;
        return data?.token || getCookie('XSRF-TOKEN') || null;
      })
      .catch(() => {
        csrfBootstrapPromise = null;
        return null;
      });
  }
  return csrfBootstrapPromise;
}

export async function apiFetch<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers || {});

  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }

  // Auto attach CSRF token on unsafe requests
  if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method) && !headers.has('X-XSRF-TOKEN')) {
    const token = await getOrFetchCsrfToken();
    if (token) {
      headers.set('X-XSRF-TOKEN', token);
    }
  }

  if (options.body && typeof options.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
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
