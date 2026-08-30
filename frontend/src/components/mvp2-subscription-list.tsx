"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatIsoLocalDate, formatPrice, formatSubscriptionStatus } from "@/lib/frontend-utils";
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

  const groups = (["ACTIVE", "PAUSED", "CANCELED"] as const).map((status) => ({ status, items: result?.items.filter((subscription) => subscription.status === status) ?? [] })).filter((group) => group.items.length);
  return <>
    <header className="page-heading"><p className="eyebrow">정기배송</p><h1>내 정기배송</h1><p>목록에서는 현재 상태, 주기와 다음 예정일을 확인하고 변경 작업은 상세에서 진행할 수 있어요.</p><Link className="button button-secondary" href="/subscriptions/new">새 정기배송 직접 시작</Link></header>
    {result?.items.length === 0 ? <section className="empty-state-panel subscription-empty"><p className="eyebrow">Regular delivery</p><h2>아직 시작한 정기배송이 없어요.</h2><p>일반 구매와 달리 선택한 플랜을 정해진 주기에 맞춰 받을 수 있어요.</p><Link className="button button-primary" href="/subscriptions/new">새 정기배송 시작</Link><Link className="button button-secondary" href="/products">상품 둘러보기</Link></section> : <>{groups.map((group) => <section className="subscription-group" key={group.status} aria-labelledby={`subscription-group-${group.status}`}><h2 id={`subscription-group-${group.status}`}>{group.status === "ACTIVE" ? "진행 중" : group.status === "PAUSED" ? "일시정지" : "종료"}</h2><div className="subscription-list">{group.items.map((subscription) => <article className="subscription-row" key={subscription.subscriptionId}><div className="subscription-row-main"><span className="status-badge">{formatSubscriptionStatus(subscription.status)}</span><h3>{subscription.pet?.name ?? "기존 정기배송"}</h3></div><div><span className="list-label">다음 예정일</span><strong>{subscription.nextScheduledDate ? formatIsoLocalDate(subscription.nextScheduledDate) : "예정 없음"}</strong></div><div><span className="list-label">주기 · 패키지 금액</span><strong>{subscription.currentSnapshot.deliveryCycleWeeks}주마다 · {formatPrice(subscription.currentSnapshot.packagePriceKrw)}</strong></div><Link className="button button-secondary" aria-label={`${subscription.pet?.name ?? "정기배송"} 상세 보기`} href={`${basePath}/${subscription.subscriptionId}`}>상세 보기</Link></article>)}</div></section>)}<nav className="button-row" aria-label="구독 목록 페이지"><button className="button button-secondary" type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>이전 페이지</button><span>{page + 1} / {Math.max(1, Math.ceil((result?.totalElements ?? 0) / (result?.size || 20)))}</span><button className="button button-secondary" type="button" disabled={!result || (page + 1) * result.size >= result.totalElements} onClick={() => setPage((current) => current + 1)}>다음 페이지</button></nav></>}
  </>;
}
