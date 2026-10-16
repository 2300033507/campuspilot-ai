import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { api, clearToken, isLoggedIn, setToken } from '../api/client';
import type { AuthResponse } from '../api/types';

export type Role = 'student' | 'faculty' | 'admin';

interface AuthState {
  isAuthenticated: boolean;
  fullName: string | null;
  role: Role | null;
  error: string | null;
  busy: boolean;
  /**
   * Whether this account may record attendance.
   *
   * The UI hides controls the caller cannot use. That is convenience,
   * NOT security — the server rejects a student's marking attempt with
   * 403 regardless of what the browser renders. Keeping this here just
   * avoids showing a button that is guaranteed to fail.
   */
  canMarkAttendance: boolean;
  login: (email: string, password: string) => Promise<boolean>;
  register: (fullName: string, email: string, password: string) => Promise<boolean>;
  logout: () => void;
  clearError: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

function readRole(): Role | null {
  const raw = localStorage.getItem('campuspilot.role');
  return raw === 'faculty' || raw === 'admin' || raw === 'student' ? raw : null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(isLoggedIn);
  const [fullName, setFullName] = useState<string | null>(() =>
    localStorage.getItem('campuspilot.name'),
  );
  const [role, setRole] = useState<Role | null>(readRole);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // The API client fires this event when the backend rejects a token, so
  // an expired session drops to the login screen from anywhere in the
  // app rather than leaving broken empty states on screen.
  useEffect(() => {
    const onUnauthorized = () => {
      setAuthenticated(false);
      setFullName(null);
      setRole(null);
    };
    window.addEventListener('campuspilot:unauthorized', onUnauthorized);
    return () =>
      window.removeEventListener('campuspilot:unauthorized', onUnauthorized);
  }, []);

  const applyAuth = useCallback((data: AuthResponse) => {
    setToken(data.token);
    localStorage.setItem('campuspilot.name', data.fullName);
    localStorage.setItem('campuspilot.role', data.role);
    setAuthenticated(true);
    setFullName(data.fullName);
    setRole(data.role as Role);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      setBusy(true);
      setError(null);
      try {
        applyAuth(await api.login(email, password));
        return true;
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not sign in.');
        return false;
      } finally {
        setBusy(false);
      }
    },
    [applyAuth],
  );

  const register = useCallback(
    async (name: string, email: string, password: string) => {
      setBusy(true);
      setError(null);
      try {
        applyAuth(await api.register(name, email, password));
        return true;
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not create account.');
        return false;
      } finally {
        setBusy(false);
      }
    },
    [applyAuth],
  );

  const logout = useCallback(() => {
    clearToken();
    localStorage.removeItem('campuspilot.name');
    localStorage.removeItem('campuspilot.role');
    setAuthenticated(false);
    setFullName(null);
    setRole(null);
  }, []);

  const value = useMemo(
    () => ({
      isAuthenticated: authenticated,
      fullName,
      role,
      error,
      busy,
      canMarkAttendance: role === 'faculty' || role === 'admin',
      login,
      register,
      logout,
      clearError: () => setError(null),
    }),
    [authenticated, fullName, role, error, busy, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** Throwing names the actual mistake instead of "context is null". */
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return ctx;
}