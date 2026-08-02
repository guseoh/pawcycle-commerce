"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatIsoLocalDate, formatPrice } from "@/lib/frontend-utils";
import { v2Api, type V2SubscriptionSummary } from "@/lib/v2-api";

export function Mvp2SubscriptionList() {
  const auth = useAuth();
  const router = useRouter();
  const [items, setItems] = useState<V2SubscriptionSummary[] | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [requestKey, setRequestKey] = useState(0);

  const load = useCallback(() => {
    setItems(null); setMessage(null); setRequestKey((key) => key + 1);
  }, []);

  useEffect(() => { if (auth.status === "anonymous") router.replace(buildLoginHref("/mvp2/subscriptions")); }, [auth.status, router]);
  useEffect(() => {
    if (auth.status !== "authenticated") return;
    let active = true;
    void v2Api.subscriptions.list().then(({ body }) => { if (active) setItems(body.items); }).catch((error: unknown) => {
      if (!active) return;
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.replace(buildLoginHref("/mvp2/subscriptions")); return; }
      setMessage(error instanceof ApiError ? error.message : "구독 목록을 불러오지 못했습니다.");
    });
    return () => { active = false; };
  }, [auth, requestKey, router]);

  if (auth.status === "loading" || auth.status === "anonymous" || !items && !message) return <LoadingState>내 구독을 불러오고 있습니다.</LoadingState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (message) return <ErrorState title="구독 목록을 불러오지 못했습니다." message={message} onRetry={load} />;

  return <>
    <header className="page-heading"><p className="eyebrow">MVP2 subscription</p><h1>내 구독 관리</h1><p>현재 snapshot과 다음 예정일은 서버가 계산한 결과입니다.</p><Link className="button button-primary" href="/mvp2/subscriptions/new">새 구독 만들기</Link></header>
    {items?.length === 0 ? <section className="state-panel empty-state"><p className="eyebrow">No subscriptions</p><h2>아직 2차 MVP 구독이 없습니다.</h2><p>반려동물과 호환 플랜을 선택해 구독을 시작해 보세요.</p><Link className="button button-primary" href="/mvp2/subscriptions/new">구독 시작</Link></section> : <div className="subscription-grid">{items?.map((subscription) => <article className="subscription-card" key={subscription.subscriptionId}><div className="card-meta"><span className="tag">{subscription.status}</span><span className="tag tag-positive">{subscription.currentSnapshot.deliveryCycleWeeks}주</span></div><h2>{subscription.pet?.name ?? "기존 구독"}</h2><p>{formatPrice(subscription.currentSnapshot.packagePriceKrw)} · PlanVersion #{subscription.currentSnapshot.planVersionId}</p><p>다음 예정일 {subscription.nextScheduledDate ? formatIsoLocalDate(subscription.nextScheduledDate) : "없음"}</p><Link className="button button-secondary" href={`/mvp2/subscriptions/${subscription.subscriptionId}`}>구독 상세</Link></article>)}</div>}
  </>;
}
