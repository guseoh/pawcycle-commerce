"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref } from "@/lib/frontend-utils";
import { CsrfRefreshError } from "@/lib/csrf-lifecycle";

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
      if (error instanceof CsrfRefreshError) {
        setNotice("보안 정보를 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      } else if (error instanceof ApiError && error.code === "CSRF_INVALID") {
        setNotice("보안 정보를 새로 받았습니다. 로그아웃을 다시 눌러 주세요.");
      } else {
        setNotice("로그아웃하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      }
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
          {status === "authenticated" ? (
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
