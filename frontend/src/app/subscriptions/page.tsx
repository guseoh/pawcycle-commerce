"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError, subscriptionApi, type SubscriptionSummary } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatIsoLocalDate } from "@/lib/frontend-utils";

export default function SubscriptionsPage() {
  const router = useRouter();
  const auth = useAuth();
  const { errorMessage: authErrorMessage, markAnonymous, refresh, status } = auth;
  const [subscriptions, setSubscriptions] = useState<SubscriptionSummary[] | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [requestKey, setRequestKey] = useState(0);

  const load = useCallback(() => {
    setSubscriptions(null);
    setErrorMessage(null);
    setRequestKey((current) => current + 1);
  }, []);

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(buildLoginHref("/subscriptions"));
    }
  }, [status, router]);

  useEffect(() => {
    if (status !== "authenticated") {
      return;
    }

    let active = true;

    void subscriptionApi.list()
      .then((response) => {
        if (active) {
          setSubscriptions(response.subscriptions);
        }
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }
        if (error instanceof ApiError && error.code === "AUTH_REQUIRED") {
          markAnonymous();
          router.replace(buildLoginHref("/subscriptions"));
          return;
        }
        setErrorMessage(error instanceof ApiError ? error.message : "구독 목록을 불러오지 못했습니다.");
      });

    return () => {
      active = false;
    };
  }, [markAnonymous, requestKey, router, status]);

  if (status === "loading" || status === "anonymous") {
    return <LoadingState>로그인 상태를 확인하고 있습니다.</LoadingState>;
  }

  if (status === "error") {
    return (
      <ErrorState
        title="로그인 상태를 확인할 수 없습니다."
        message={authErrorMessage ?? "잠시 후 다시 시도해 주세요."}
        onRetry={() => void refresh()}
      />
    );
  }

  if (errorMessage) {
    return <ErrorState title="구독 목록을 불러오지 못했습니다." message={errorMessage} onRetry={load} />;
  }

  if (!subscriptions) {
    return <LoadingState>내 구독을 불러오고 있습니다.</LoadingState>;
  }

  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">My subscriptions</p>
        <h1>내 구독</h1>
        <p>서버에 등록된 구독을 최신 생성 순서대로 확인합니다.</p>
      </header>

      {subscriptions.length === 0 ? (
        <section className="state-panel empty-state">
          <p className="eyebrow">No subscriptions</p>
          <h2>아직 구독이 없습니다.</h2>
          <p>상품을 살펴보고 첫 정기배송을 시작해 보세요.</p>
          <Link className="button button-primary" href="/products">상품 둘러보기</Link>
        </section>
      ) : (
        <div className="subscription-grid">
          {subscriptions.map((subscription) => (
            <article className="subscription-card" key={subscription.subscriptionId}>
              <div className="card-meta">
                <span className="tag">구독 #{subscription.subscriptionId}</span>
                <span className="tag tag-positive">{subscription.deliveryCycleWeeks}주 주기</span>
              </div>
              <h2>{subscription.product.name}</h2>
              <p>{subscription.sku.skuName} · 수량 {subscription.quantity}</p>
              <p>다음 주문일 {formatIsoLocalDate(subscription.nextOrderDate)}</p>
              <div className="card-actions">
                <Link className="button button-secondary" href={`/subscriptions/${subscription.subscriptionId}`}>
                  구독 상세
                </Link>
              </div>
            </article>
          ))}
        </div>
      )}
    </>
  );
}
