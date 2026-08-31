// Thin HTTP client for the SentinelX API gateway.
// Auth headers come from ../auth (bearer token stored in localStorage).

import { authHeaders, getToken } from '../auth';

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

interface RequestOpts extends RequestInit {
  token?: boolean;
}

async function parseError(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as { message?: string; error?: string };
    if (body?.message) return body.message;
    if (body?.error) return body.error;
  } catch {
    /* non-json body */
  }
  switch (res.status) {
    case 401:
      return 'Unauthorized — your session may have expired.';
    case 403:
      return 'Forbidden — you do not have permission.';
    case 404:
      return 'Endpoint not found for the requested resource.';
    case 429:
      return 'Rate limit exceeded — try again shortly.';
    default:
      return `Request failed with status ${res.status}.`;
  }
}

/** Perform an authenticated JSON request against the gateway. */
export async function apiFetch<T>(path: string, opts: RequestOpts = {}): Promise<T> {
  const { token = true, headers, ...rest } = opts;
  const tokenValue = getToken();

  if (token && !tokenValue) {
    throw new ApiError('No active session. Please sign in.', 401);
  }

  const mergedHeaders: Record<string, string> = token
    ? { ...authHeaders(tokenValue) }
    : { 'Content-Type': 'application/json' };

  const res = await fetch(path, {
    ...rest,
    headers: { ...mergedHeaders, ...(headers as Record<string, string>) },
  });

  if (!res.ok) {
    throw new ApiError(await parseError(res), res.status);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}