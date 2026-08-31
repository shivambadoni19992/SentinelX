// SentinelX frontend auth helpers.
// Talks to the auth-service through the API gateway (/api/auth/**).

export interface CurrentUser {
  id: string;
  username: string;
  email: string;
  role: string;
  accountStatus: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  user: CurrentUser;
}

const TOKEN_KEY = 'sentinelx.access-token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/** Build fetch headers, attaching the bearer token (and JSON content-type). */
export function authHeaders(token?: string | null): Record<string, string> {
  const t = token ?? getToken();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (t) headers.Authorization = `Bearer ${t}`;
  return headers;
}

/** POST /api/auth/login → AuthResponse. Throws with a human-readable message. */
export async function login(username: string, password: string): Promise<AuthResponse> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    let message = 'Login failed. Please try again.';
    try {
      const body = (await res.json()) as { message?: string };
      if (body?.message) message = body.message;
    } catch {
      /* fall through to default */
    }
    if (res.status === 401) message = 'Invalid username or password.';
    if (res.status === 403) message = 'This account is blocked. Contact support.';
    throw new Error(message);
  }
  return (await res.json()) as AuthResponse;
}

/** GET /api/auth/me → CurrentUser. Throws when the token is rejected. */
export async function fetchMe(token: string): Promise<CurrentUser> {
  const res = await fetch('/api/auth/me', { headers: authHeaders(token) });
  if (!res.ok) {
    throw new Error(res.status === 401 ? 'unauthorized' : 'failed to load profile');
  }
  return (await res.json()) as CurrentUser;
}