import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { jwtDecode } from 'jwt-decode';
import { getStoredToken, setStoredToken } from '../api/httpClient';
import * as authApi from '../api/authApi';
import type { LoginRequest, RegisterRequest, UserSummary } from '../types/auth';

interface AuthContextValue {
  user: UserSummary | null;
  isAuthenticated: boolean;
  isInitializing: boolean;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

interface DecodedToken {
  exp: number;
}

function isTokenExpired(token: string): boolean {
  try {
    const decoded = jwtDecode<DecodedToken>(token);
    return decoded.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}

function loadUserFromStorage(): UserSummary | null {
  const raw = localStorage.getItem('ai-testpilot.user');
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserSummary;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(() => {
    const token = getStoredToken();
    if (!token || isTokenExpired(token)) {
      setStoredToken(null);
      return null;
    }
    return loadUserFromStorage();
  });

  const persistSession = useCallback((token: string, sessionUser: UserSummary) => {
    setStoredToken(token);
    localStorage.setItem('ai-testpilot.user', JSON.stringify(sessionUser));
    setUser(sessionUser);
  }, []);

  const login = useCallback(
    async (request: LoginRequest) => {
      const response = await authApi.login(request);
      persistSession(response.token, response.user);
    },
    [persistSession],
  );

  const register = useCallback(
    async (request: RegisterRequest) => {
      const response = await authApi.register(request);
      persistSession(response.token, response.user);
    },
    [persistSession],
  );

  const logout = useCallback(() => {
    setStoredToken(null);
    localStorage.removeItem('ai-testpilot.user');
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: user !== null, isInitializing: false, login, register, logout }),
    [user, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
