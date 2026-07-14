"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError, subscriptionApi, type SubscriptionDetail } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatIsoLocalDate, formatPrice } from "@/lib/frontend-utils";

interface SubscriptionDetailScreenProps {
  subscriptionId: string;
  created: boolean;
}

export function SubscriptionDetailScreen({ subscriptionId, created }: SubscriptionDetailScreenProps) {
  const router = useRouter();
  const auth = useAuth();
  const { errorMessage: authErrorMessage, markAnonymous, refresh, status } = auth;
  const returnTo = `/subscriptions/${subscriptionId}`;
  const [subscription, setSubscription] = useState<SubscriptionDetail | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [requestKey, setRequestKey] = useState(0);

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(buildLoginHref(returnTo));
    }
  }, [returnTo, router, status]);

  useEffect(() => {
    if (status !== "authenticated") {
      return;
    }

    let active = true;

    void subscriptionApi.detail(subscriptionId)
      .then((response) => {
        if (active) {
          setSubscription(response);
        }
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }
        if (error instanceof ApiError && error.code === "AUTH_REQUIRED") {
          markAnonymous();
          router.replace(buildLoginHref(returnTo));
          return;
        }
        if (error instanceof ApiError && error.code === "SUBSCRIPTION_NOT_FOUND") {
          setNotFound(true);
          return;
        }
        setErrorMessage(error instanceof ApiError ? error.message : "구독 상세를 불러오지 못했습니다.");
      });

    return () => {
      active = false;
    };
  }, [markAnonymous, requestKey, returnTo, router, status, subscriptionId]);

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

  if (notFound) {
    return (
      <ErrorState
        title="구독을 조회할 수 없습니다."
        message="존재하지 않거나 현재 회원이 조회할 수 없는 구독입니다."
      >
        <Link className="button button-primary" href="/subscriptions">내 구독으로</Link>
        <Link className="button button-secondary" href="/products">상품 둘러보기</Link>
      </ErrorState>
    );
  }

  if (errorMessage) {
    return (
      <ErrorState
        title="구독 상세를 불러오지 못했습니다."
        message={errorMessage}
        onRetry={() => {
          setSubscription(null);
          setNotFound(false);
          setErrorMessage(null);
          setRequestKey((current) => current + 1);
        }}
      />
    );
  }

  if (!subscription) {
    return <LoadingState>구독 상세를 불러오고 있습니다.</LoadingState>;
  }

  return (
    <div className="detail-stack">
      {created ? (
        <div className="notice-success" role="status">
          구독이 생성되었습니다. 서버가 확정한 다음 주문일을 확인해 주세요.
        </div>
      ) : null}

      <section className="section-card" aria-labelledby="subscription-title">
        <p className="eyebrow">Subscription #{subscription.subscriptionId}</p>
        <h1 id="subscription-title">{subscription.product.name}</h1>
        <dl className="detail-list">
          <dt>SKU</dt>
          <dd>{subscription.sku.skuName} (#{subscription.sku.skuId})</dd>
          <dt>단가</dt>
          <dd>{formatPrice(subscription.sku.price)}</dd>
          <dt>수량</dt>
          <dd>{subscription.quantity}</dd>
          <dt>배송 주기</dt>
          <dd>{subscription.deliveryCycleWeeks}주</dd>
          <dt>생성일</dt>
          <dd>{formatIsoLocalDate(subscription.createdDate)}</dd>
          <dt>다음 주문일</dt>
          <dd>{formatIsoLocalDate(subscription.nextOrderDate)}</dd>
        </dl>
        <div className="button-row">
          <Link className="button button-primary" href="/subscriptions">내 구독으로</Link>
          <Link className="button button-secondary" href={`/products/${subscription.product.productId}`}>
            상품 상세
          </Link>
        </div>
      </section>
    </div>
  );
}
