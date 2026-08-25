"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { CsrfRefreshError } from "@/lib/csrf-lifecycle";
import { getLogoutFailureFeedback } from "@/lib/logout-feedback";

export function LogoutControl() {
  const router = useRouter();
  const { logout } = useAuth();
  const [pending, setPending] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  async function handleLogout() {
    if (pending) return;
    setPending(true);
    setNotice(null);
    try {
      await logout();
      router.push("/");
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
      setPending(false);
    }
  }

  return (
    <div className="logout-control">
      <button className="button button-secondary" type="button" onClick={handleLogout} disabled={pending}>
        {pending ? "로그아웃 중" : "로그아웃"}
      </button>
      {notice ? <p className="field-error" role="status">{notice}</p> : null}
    </div>
  );
}
