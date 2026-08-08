import axios, { AxiosError } from 'axios';

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080/api';

/**
 * Default request timeout.
 *
 * <p>Previously unset, which meant a hung backend left the UI spinning forever with no error state and
 * no way to recover short of a reload. Long-running work (analysis, load tests) is started with a 202
 * and polled, so no normal request needs longer than this.
 */
const DEFAULT_TIMEOUT_MS = 45_000;

export const httpClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: DEFAULT_TIMEOUT_MS,
});

const TOKEN_STORAGE_KEY = 'qpilot.token';
const USER_STORAGE_KEY = 'qpilot.user';

/**
 * Keys used by an earlier build. Read once on startup so an existing session survives the rename
 * instead of silently logging the user out.
 */
const LEGACY_TOKEN_KEY = 'ai-testpilot.token';
const LEGACY_USER_KEY = 'ai-testpilot.user';

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY) ?? localStorage.getItem(LEGACY_TOKEN_KEY);
}

export function setStoredToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(LEGACY_TOKEN_KEY);
  }
}

export function getStoredUserRaw(): string | null {
  return localStorage.getItem(USER_STORAGE_KEY) ?? localStorage.getItem(LEGACY_USER_KEY);
}

export function setStoredUserRaw(value: string | null): void {
  if (value) {
    localStorage.setItem(USER_STORAGE_KEY, value);
  } else {
    localStorage.removeItem(USER_STORAGE_KEY);
    localStorage.removeItem(LEGACY_USER_KEY);
  }
}

export function clearSession(): void {
  setStoredToken(null);
  setStoredUserRaw(null);
}

httpClient.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      // Only force a redirect when a token existed and has now been rejected — that is a genuinely
      // expired session. Without the check, a 401 on a public page would bounce the user pointlessly.
      const hadToken = !!getStoredToken();
      clearSession();
      const path = window.location.pathname;
      if (hadToken && !path.startsWith('/login') && !path.startsWith('/register')) {
        window.location.href = '/login?expired=1';
      }
    }
    return Promise.reject(error);
  },
);

/** Shape the backend uses for every response, success or failure. */
interface ApiErrorBody {
  success?: boolean;
  message?: string;
  data?: unknown;
}

/**
 * Turns any thrown value into a message worth showing a user.
 *
 * <p>Handles the cases a naive `error.message` misses:
 * <ul>
 *   <li><b>Blob responses</b> — a failed file download arrives as a Blob containing JSON, so
 *       `error.response.data.message` is unreadable. Those are detected here and reported as a
 *       download failure rather than as `[object Object]`.</li>
 *   <li><b>Validation maps</b> — field-error objects are flattened into readable text.</li>
 *   <li><b>Timeouts and network failures</b> — distinguished from server errors, because "the server
 *       is unreachable" and "the server rejected this" call for different user actions.</li>
 * </ul>
 */
export function extractErrorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (!axios.isAxiosError(error)) {
    if (error instanceof Error && error.message) {
      return error.message;
    }
    return fallback;
  }

  if (error.code === 'ECONNABORTED' || error.message?.toLowerCase().includes('timeout')) {
    return 'The request timed out. The server may be busy — please retry in a moment.';
  }
  if (!error.response) {
    return `Cannot reach the QPilot API at ${API_BASE_URL}. Check that the backend is running and that VITE_API_BASE_URL points at it.`;
  }

  const data = error.response.data;

  // A blob body means this was a download request; its JSON error text is not accessible
  // synchronously, so report the status meaningfully instead of stringifying an object.
  if (data instanceof Blob) {
    return error.response.status === 404
      ? 'Nothing is available to download yet. Run an analysis first, then try again.'
      : `The download failed (HTTP ${error.response.status}). Please try again.`;
  }

  const body = data as ApiErrorBody | undefined;
  if (body && typeof body.message === 'string' && body.message.length > 0) {
    // Validation failures carry a field->message map in `data`; surface those fields rather than
    // just the generic "Validation failed".
    if (body.data && typeof body.data === 'object' && !Array.isArray(body.data)) {
      const fields = Object.entries(body.data as Record<string, unknown>)
        .map(([field, message]) => `${field}: ${String(message)}`)
        .join('; ');
      return fields.length > 0 ? `${body.message} — ${fields}` : body.message;
    }
    return body.message;
  }

  if (typeof data === 'string' && data.length > 0 && data.length < 500) {
    return data;
  }

  return `${fallback} (HTTP ${error.response.status})`;
}

/**
 * Reads the JSON error message out of a failed blob download.
 *
 * <p>Download requests use `responseType: 'blob'`, so an error body arrives as a Blob rather than
 * parsed JSON. Reading it is asynchronous, which is why this is separate from
 * {@link extractErrorMessage}: callers that can await get the server's actual explanation instead of
 * a generic status message.
 */
export async function extractBlobErrorMessage(error: unknown, fallback: string): Promise<string> {
  if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
    try {
      const text = await error.response.data.text();
      const parsed = JSON.parse(text) as ApiErrorBody;
      if (typeof parsed.message === 'string' && parsed.message.length > 0) {
        return parsed.message;
      }
    } catch {
      // Not JSON, or unreadable — fall through to the generic message below.
    }
  }
  return extractErrorMessage(error, fallback);
}
