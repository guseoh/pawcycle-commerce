"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatIsoLocalDate, formatPrice } from "@/lib/frontend-utils";
import { v2Api, type Page, type V2SubscriptionSummary } from "@/lib/v2-api";

export function Mvp2SubscriptionList({ basePath = "/mvp2/subscriptions" }: { basePath?: string }) {
  const auth = useAuth();
  const router = useRouter();
  const [result, setResult] = useState<Page<V2SubscriptionSummary> | null>(null);
  const [page, setPage] = useState(0);
  const [message, setMessage] = useState<string | null>(null);
  const [requestKey, setRequestKey] = useState(0);

  const load = useCallback(() => {
    setResult(null); setMessage(null); setRequestKey((key) => key + 1);
  }, []);

  useEffect(() => { if (auth.status === "anonymous") router.replace(buildLoginHref(basePath)); }, [auth.status, router, basePath]);
  useEffect(() => {
    if (auth.status !== "authenticated") return;
    let active = true;
    void v2Api.subscriptions.list(page).then(({ body }) => { if (active) setResult(body); }).catch((error: unknown) => {
      if (!active) return;
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.replace(buildLoginHref(basePath)); return; }
      setMessage(error instanceof ApiError ? error.message : "구독 목록을 불러오지 못했습니다.");
    });
    return () => { active = false; };
  }, [auth, basePath, page, requestKey, router]);

  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (auth.status === "loading" || auth.status === "anonymous" || !result && !message) return <LoadingState>내 구독을 불러오고 있습니다.</LoadingState>;
  if (message) return <ErrorState title="구독 목록을 불러오지 못했습니다." message={message} onRetry={load} />;

  return <>
    <header className="page-heading"><h1>내 구독 관리</h1><p>다음 배송과 변경 예정 사항은 서버의 최신 결과입니다.</p></header>
    {result?.items.length === 0 ? <section className="state-panel empty-state"><h2>아직 구독이 없습니다.</h2><p>반려동물과 호환 플랜을 선택해 구독을 시작해 보세요.</p><Link className="button button-primary" href={`${basePath}/new`}>구독 시작</Link></section> : <><div className="subscription-grid">{result?.items.map((subscription) => <article className="subscription-card" key={subscription.subscriptionId}><div className="card-meta"><span className="status-badge">{subscription.status}</span><span className="tag tag-positive">{subscription.currentSnapshot.deliveryCycleWeeks}주마다</span></div><h2>{subscription.pet?.name ?? "기존 구독"}</h2><p>{formatPrice(subscription.currentSnapshot.packagePriceKrw)}</p><p>다음 배송일 <strong>{subscription.nextScheduledDate ? formatIsoLocalDate(subscription.nextScheduledDate) : "예정 없음"}</strong></p><Link className="button button-secondary" href={`${basePath}/${subscription.subscriptionId}`}>구독 상세 보기</Link></article>)}</div><nav className="button-row" aria-label="구독 목록 페이지"><button className="button button-secondary" type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>이전 페이지</button><span>{page + 1} / {Math.max(1, Math.ceil((result?.totalElements ?? 0) / (result?.size || 20)))}</span><button className="button button-secondary" type="button" disabled={!result || (page + 1) * result.size >= result.totalElements} onClick={() => setPage((current) => current + 1)}>다음 페이지</button></nav></>}
  </>;
}
