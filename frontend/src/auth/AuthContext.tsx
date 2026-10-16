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

interface AuthState {
  isAuthenticated: boolean;
  fullName: string | null;
  role: string | null;
  /** Set when a login or register call fails, cleared on retry. */
  error: string | null;
  /** True while a login request is in flight. Drives the button spinner. */
  busy: boolean;
  login: (email: string, password: string) => Promise<boolean>;
  register: (fullName: string, email: string, password: string) => Promise<boolean>;
  logout: () => void;
  clearError: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(isLoggedIn);
  const [fullName, setFullName] = useState<string | null>(
    () => localStorage.getItem('campuspilot.name'),
  );
  const [role, setRole] = useState<string | null>(
    () => localStorage.getItem('campuspilot.role'),
  );
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // The API client fires this event when the backend rejects a token, so
  // an expired session drops the user to the login screen from anywhere
  // in the app instead of leaving broken empty states on screen.
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
    setRole(data.role);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      setBusy(true);
      setError(null);
      try {
        applyAuth(await api.login(email, password));
        return true;
      } catch (err) {
        setError(
          err instanceof Error ? err.message : 'Could not sign in.',
        );
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
        setError(
          err instanceof Error ? err.message : 'Could not create account.',
        );
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
      login,
      register,
      logout,
      clearError: () => setError(null),
    }),
    [authenticated, fullName, role, error, busy, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Hook form. Throwing when used outside the provider turns a confusing
 * "context is null" crash into a message that names the actual mistake.
 */
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return ctx;
}