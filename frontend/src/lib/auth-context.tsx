"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { ApiError, authApi } from "./api";
import { clearAuthentication, runCsrfRequest } from "./csrf-lifecycle";

export type AuthStatus = "loading" | "authenticated" | "anonymous" | "error";

interface AuthContextValue {
  status: AuthStatus;
  memberId: number | null;
  errorMessage: string | null;
  refresh: () => Promise<void>;
  executeWithCsrf: <T>(request: (token: string) => Promise<T>) => Promise<T>;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  markAnonymous: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [memberId, setMemberId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const csrfTokenRef = useRef<string | null>(null);
  const authGenerationRef = useRef(0);

  const setCsrfToken = useCallback((token: string | null) => {
    csrfTokenRef.current = token;
  }, []);

  const acquireCsrfToken = useCallback(async () => (await authApi.csrf()).token, []);

  const nextAuthGeneration = useCallback(() => {
    authGenerationRef.current += 1;
    return authGenerationRef.current;
  }, []);

  const executeWithCsrf = useCallback(
    <T,>(request: (token: string) => Promise<T>) => runCsrfRequest({
      currentToken: csrfTokenRef.current,
      acquireToken: acquireCsrfToken,
      setToken: setCsrfToken,
      request,
      isCsrfInvalid: (error) => error instanceof ApiError && error.code === "CSRF_INVALID",
    }),
    [acquireCsrfToken, setCsrfToken],
  );

  const refresh = useCallback(async () => {
    const generation = nextAuthGeneration();
    setStatus("loading");
    setErrorMessage(null);

    try {
      const currentMember = await authApi.me();
      if (generation !== authGenerationRef.current) return;
      setMemberId(currentMember.memberId);
      setStatus("authenticated");
    } catch (error) {
      if (generation !== authGenerationRef.current) return;
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") {
        clearAuthentication(setMemberId, setCsrfToken, () => setStatus("anonymous"));
      } else {
        setMemberId(null);
        setStatus("error");
        setErrorMessage("로그인 상태를 확인하지 못했습니다.");
      }
    }
  }, [nextAuthGeneration, setCsrfToken]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void refresh(), 0);
    return () => window.clearTimeout(timeoutId);
  }, [refresh]);

  const login = useCallback(
    async (email: string, password: string) => {
      nextAuthGeneration();
      await runCsrfRequest({
        currentToken: csrfTokenRef.current,
        acquireToken: acquireCsrfToken,
        setToken: setCsrfToken,
        request: async (token) => {
          const member = await authApi.login(email, password, token);
          setMemberId(member.memberId);
          setStatus("authenticated");
          setErrorMessage(null);
        },
        isCsrfInvalid: (error) => error instanceof ApiError && error.code === "CSRF_INVALID",
        refreshAfterSuccess: true,
      });
    },
    [acquireCsrfToken, nextAuthGeneration, setCsrfToken],
  );

  const logout = useCallback(async () => {
    nextAuthGeneration();
    try {
      await executeWithCsrf(async (token) => {
        await authApi.logout(token);
        clearAuthentication(setMemberId, setCsrfToken, () => setStatus("anonymous"));
        setErrorMessage(null);
      });
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") {
        clearAuthentication(setMemberId, setCsrfToken, () => setStatus("anonymous"));
      }
      throw error;
    }
  }, [executeWithCsrf, nextAuthGeneration, setCsrfToken]);

  const markAnonymous = useCallback(() => {
    nextAuthGeneration();
    clearAuthentication(setMemberId, setCsrfToken, () => setStatus("anonymous"));
  }, [nextAuthGeneration, setCsrfToken]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      memberId,
      errorMessage,
      refresh,
      executeWithCsrf,
      login,
      logout,
      markAnonymous,
    }),
    [
      status,
      memberId,
      errorMessage,
      refresh,
      executeWithCsrf,
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
