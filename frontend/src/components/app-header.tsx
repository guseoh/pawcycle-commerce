"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref } from "@/lib/frontend-utils";
import { CsrfRefreshError } from "@/lib/csrf-lifecycle";
import { getLogoutFailureFeedback } from "@/lib/logout-feedback";

export function AppHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const { status, memberId, logout } = useAuth();
  const [logoutPending, setLogoutPending] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  async function handleLogout() {
    if (logoutPending) return;
    setLogoutPending(true);
    setNotice(null);
    try {
      await logout();
      router.push("/products");
    } catch (error) {
      const reason = error instanceof CsrfRefreshError
        ? "CSRF_REFRESH_FAILED"
        : error instanceof ApiError && (error.code === "CSRF_INVALID" || error.code === "AUTH_REQUIRED")
          ? error.code
          : "GENERAL";
      const feedback = getLogoutFailureFeedback(reason);
      setNotice(feedback.notice);
      if (feedback.redirectTo) router.push(feedback.redirectTo);
    } finally {
      setLogoutPending(false);
    }
  }

  return (
    <header className="site-header">
      <div className="header-inner">
        <Link className="brand" href="/products" aria-label="PawCycle 상품 목록">
          <span className="brand-mark" aria-hidden="true">P</span>
          <span>
            <strong>PawCycle</strong>
            <small>반려생활의 좋은 순환</small>
          </span>
        </Link>
        <nav className="main-nav" aria-label="주요 메뉴">
          <Link href="/products">상품</Link>
          <Link href="/subscriptions">내 구독</Link>
          {status === "loading" ? (
            <span className="nav-status" role="status">회원 정보 확인 중</span>
          ) : status === "authenticated" ? (
            <button type="button" onClick={handleLogout} disabled={logoutPending}>
              {logoutPending ? "로그아웃 중" : `로그아웃 · 회원 ${memberId}`}
            </button>
          ) : (
            <Link href={buildLoginHref(pathname)}>로그인</Link>
          )}
        </nav>
      </div>
      {notice ? <p className="header-notice" role="status">{notice}</p> : null}
    </header>
  );
}
