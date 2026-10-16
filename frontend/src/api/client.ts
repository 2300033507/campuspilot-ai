import type {
  ApiError,
  AttendanceRecord,
  AuthResponse,
  DashboardRow,
  Subject,
} from './types';
import { ApiRequestError } from './types';

const BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/**
 * Reads the JWT out of localStorage.
 *
 * WHY localStorage, HONESTLY:
 *   It is the simple choice and the usual one in tutorials. It is also
 *   readable by any JavaScript on the page, so a successful XSS attack
 *   can steal the token. An httpOnly cookie is not readable from
 *   JavaScript, and is the better answer.
 *
 *   Why not do that here? Because it requires CSRF protection on the
 *   backend and changes how the API authenticates. That is a real piece
 *   of work, and pretending localStorage is safe would be worse than
 *   naming the trade-off. Stated so it can be discussed, not hidden.
 */
function getToken(): string | null {
  return localStorage.getItem('campuspilot.token');
}

export function setToken(token: string): void {
  localStorage.setItem('campuspilot.token', token);
}

export function clearToken(): void {
  localStorage.removeItem('campuspilot.token');
}

export function isLoggedIn(): boolean {
  return getToken() !== null;
}

async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getToken();

  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');

  if (options.body) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(`${BASE}${path}`, { ...options, headers });
  } catch {
    // A network failure produces a TypeError with an unhelpful message
    // like "Failed to fetch". Turning it into something a user can act
    // on is the whole point of catching it here.
    throw new ApiRequestError(0, {
      timestamp: new Date().toISOString(),
      status: 0,
      error: 'Network error',
      message: 'Cannot reach the server. Is the backend running on port 8080?',
    });
  }

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  const data: unknown = text ? JSON.parse(text) : null;

  if (!response.ok) {
    // An expired or revoked token should log the user out rather than
    // leaving the UI stuck showing empty states that never populate.
    if (response.status === 401 && token) {
      clearToken();
      window.dispatchEvent(new Event('campuspilot:unauthorized'));
    }
    throw new ApiRequestError(response.status, data as ApiError);
  }

  return data as T;
}

export const api = {
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  register: (fullName: string, email: string, password: string) =>
    request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ fullName, email, password }),
    }),

  dashboard: () => request<DashboardRow[]>('/api/dashboard'),

  summary: () =>
    request<Record<string, number>>('/api/dashboard/summary'),

  subjects: () => request<Subject[]>('/api/subjects'),

  createSubject: (body: {
    name: string;
    code?: string | null;
    semester: number;
    targetAttendance?: number;
    remainingClassesEstimate?: number;
  }) =>
    request<Subject>('/api/subjects', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  updateSubject: (
    id: string,
    body: {
      name: string;
      code?: string | null;
      semester: number;
      targetAttendance?: number;
      remainingClassesEstimate?: number;
    },
  ) =>
    request<Subject>(`/api/subjects/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  deleteSubject: (id: string) =>
    request<void>(`/api/subjects/${id}`, { method: 'DELETE' }),

  attendance: (subjectId: string) =>
    request<AttendanceRecord[]>(`/api/subjects/${subjectId}/attendance`),

  markAttendance: (
    subjectId: string,
    body: { heldOn: string; status: 'present' | 'absent'; note?: string },
  ) =>
    request<AttendanceRecord>(`/api/subjects/${subjectId}/attendance`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  deleteAttendance: (subjectId: string, recordId: string) =>
    request<void>(`/api/subjects/${subjectId}/attendance/${recordId}`, {
      method: 'DELETE',
    }),
};