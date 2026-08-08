import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { jwtDecode } from 'jwt-decode';
import { clearSession, getStoredToken, getStoredUserRaw, setStoredToken, setStoredUserRaw } from '../api/httpClient';
import * as authApi from '../api/authApi';
import type { LoginRequest, RegisterRequest, UserSummary } from '../types/auth';

interface AuthContextValue {
  user: UserSummary | null;
  isAuthenticated: boolean;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

interface DecodedToken {
  exp: number;
}

/**
 * True when the stored token has expired.
 *
 * <p>Checked before restoring a session so an expired token never produces a UI that looks logged in but
 * fails every request. Any decode failure is treated as expired: an unparseable token is unusable either
 * way, and assuming the worst is the safe direction.
 */
function isTokenExpired(token: string): boolean {
  try {
    const decoded = jwtDecode<DecodedToken>(token);
    return decoded.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}

function loadStoredUser(): UserSummary | null {
  const raw = getStoredUserRaw();
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserSummary;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // Resolved synchronously from localStorage, so the first render already knows whether the user is
  // authenticated. An async check would flash the login page for anyone with a valid session.
  const [user, setUser] = useState<UserSummary | null>(() => {
    const token = getStoredToken();
    if (!token || isTokenExpired(token)) {
      clearSession();
      return null;
    }
    return loadStoredUser();
  });

  const persistSession = useCallback((token: string, sessionUser: UserSummary) => {
    setStoredToken(token);
    setStoredUserRaw(JSON.stringify(sessionUser));
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
    clearSession();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: user !== null, login, register, logout }),
    [user, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
