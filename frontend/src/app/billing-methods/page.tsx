"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi, type BillingMethodStatus } from "@/lib/commerce-final-api";
import { buildLoginHref } from "@/lib/frontend-utils";

export default function BillingMethodsPage() {
  const auth = useAuth();
  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="결제수단을 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/billing-methods")}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (auth.status !== "authenticated" || auth.memberId === null) return <LoadingState>결제수단 정보를 불러오고 있습니다.</LoadingState>;
  return <BillingMethodForMember key={auth.memberId} />;
}

function BillingMethodForMember() {
  const auth = useAuth();
  const { markAnonymous } = auth;
  const [status, setStatus] = useState<BillingMethodStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [prepared, setPrepared] = useState(false);
  const [busy, setBusy] = useState(false);
  const activeRef = useRef(false);
  const requestRef = useRef(0);

  const load = useCallback(() => {
    const request = ++requestRef.current;
    void commerceFinalApi.billingMethod().then((result) => {
      if (!activeRef.current || request !== requestRef.current) return;
      setStatus(result);
      setError(null);
    }).catch((reason: unknown) => {
      if (!activeRef.current || request !== requestRef.current) return;
      if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") {
        markAnonymous();
        return;
      }
      setStatus(null);
      setError("결제수단 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    });
  }, [markAnonymous]);

  useEffect(() => {
    activeRef.current = true;
    const timer = window.setTimeout(load, 0);
    return () => { activeRef.current = false; requestRef.current += 1; window.clearTimeout(timer); };
  }, [load]);

  async function prepare() {
    if (busy || !status?.configured) return;
    const request = ++requestRef.current;
    setBusy(true);
    try {
      await auth.executeWithCsrf((csrf) => commerceFinalApi.prepareBilling(csrf));
      if (activeRef.current && request === requestRef.current) setPrepared(true);
    } catch {
      if (activeRef.current && request === requestRef.current) setError("결제수단 등록 준비를 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      if (activeRef.current && request === requestRef.current) setBusy(false);
    }
  }

  if (status === null && !error) return <LoadingState>결제수단 정보를 불러오고 있습니다.</LoadingState>;
  if (status === null) return <ErrorState title="결제수단 정보를 불러오지 못했습니다." message={error ?? "다시 시도해 주세요."} onRetry={load} />;
  return <section><header className="page-heading"><p className="eyebrow">결제수단</p><h1>결제수단 상태</h1><p>등록 상태를 확인하고 필요한 경우 등록을 시작할 수 있어요.</p></header><section className="section-card"><div className="billing-status-card"><div className="status-tile"><strong>등록 상태</strong><span>{status.registered ? "등록된 결제수단이 있어요." : "등록된 결제수단이 없어요."}</span></div><div className="status-tile"><strong>서비스 상태</strong><span>{status.configured ? "등록을 시작할 수 있어요." : "현재 등록을 시작할 수 없어요."}</span></div></div>{error ? <p className="field-error" role="alert">{error}</p> : null}<button className="button button-primary" type="button" disabled={busy || prepared || !status.configured} onClick={() => void prepare()}>{busy ? "준비 중" : "결제수단 등록 준비"}</button>{prepared ? <div className="inline-alert"><strong>등록 준비가 완료되었습니다.</strong><p>결제수단 등록 화면은 준비가 끝난 뒤 안내드릴 예정입니다.</p></div> : null}</section></section>;
}
