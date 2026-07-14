"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { ApiError, authApi } from "./api";

export type AuthStatus = "loading" | "authenticated" | "anonymous" | "error";

interface AuthContextValue {
  status: AuthStatus;
  memberId: number | null;
  errorMessage: string | null;
  csrfToken: string | null;
  refresh: () => Promise<void>;
  refreshCsrf: () => Promise<string>;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  markAnonymous: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [memberId, setMemberId] = useState<number | null>(null);
  const [csrfToken, setCsrfToken] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const refreshCsrf = useCallback(async () => {
    const response = await authApi.csrf();
    setCsrfToken(response.token);
    return response.token;
  }, []);

  const refresh = useCallback(async () => {
    setStatus("loading");
    setErrorMessage(null);

    const csrfRequest = refreshCsrf().catch(() => null);
    try {
      const currentMember = await authApi.me();
      setMemberId(currentMember.memberId);
      setStatus("authenticated");
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") {
        setMemberId(null);
        setStatus("anonymous");
      } else {
        setMemberId(null);
        setStatus("error");
        setErrorMessage("로그인 상태를 확인하지 못했습니다.");
      }
    }
    await csrfRequest;
  }, [refreshCsrf]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void refresh(), 0);
    return () => window.clearTimeout(timeoutId);
  }, [refresh]);

  const login = useCallback(
    async (email: string, password: string) => {
      const token = csrfToken ?? (await refreshCsrf());
      try {
        const member = await authApi.login(email, password, token);
        setMemberId(member.memberId);
        setStatus("authenticated");
        setErrorMessage(null);
        setCsrfToken(null);
        await refreshCsrf();
      } catch (error) {
        if (error instanceof ApiError && error.code === "CSRF_INVALID") {
          await refreshCsrf();
        }
        throw error;
      }
    },
    [csrfToken, refreshCsrf],
  );

  const logout = useCallback(async () => {
    const token = csrfToken ?? (await refreshCsrf());
    try {
      await authApi.logout(token);
      setCsrfToken(null);
      setMemberId(null);
      setStatus("anonymous");
      setErrorMessage(null);
    } catch (error) {
      if (error instanceof ApiError && error.code === "CSRF_INVALID") {
        await refreshCsrf();
      }
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") {
        setCsrfToken(null);
        setMemberId(null);
        setStatus("anonymous");
      }
      throw error;
    }
  }, [csrfToken, refreshCsrf]);

  const markAnonymous = useCallback(() => {
    setMemberId(null);
    setStatus("anonymous");
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      memberId,
      errorMessage,
      csrfToken,
      refresh,
      refreshCsrf,
      login,
      logout,
      markAnonymous,
    }),
    [
      status,
      memberId,
      errorMessage,
      csrfToken,
      refresh,
      refreshCsrf,
      login,
      logout,
      markAnonymous,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error("useAuth는 AuthProvider 안에서 사용해야 합니다.");
  }
  return value;
}
