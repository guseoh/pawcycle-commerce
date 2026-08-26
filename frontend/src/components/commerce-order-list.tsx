"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { commerceFinalApi, type OrderSummary } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatDateTime, formatOrderStatus, formatPrice } from "@/lib/frontend-utils";

export function CommerceOrderList() {
  const auth = useAuth();
  const [orders, setOrders] = useState<OrderSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(() => {
    void commerceFinalApi.orders().then(setOrders).catch((reason: unknown) => {
      if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") { auth.markAnonymous(); return; }
      setError(reason instanceof ApiError ? reason.message : "주문을 불러오지 못했습니다.");
    });
  }, [auth]);
  useEffect(() => { if (auth.status === "authenticated") load(); }, [auth.status, load]);

  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="주문 내역을 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/orders")}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (!orders && !error) return <LoadingState>주문을 불러오고 있습니다.</LoadingState>;
  if (!orders) return <ErrorState title="주문을 불러오지 못했습니다." message={error ?? "다시 시도해 주세요."} onRetry={load} />;
  return <section className="order-panel"><div className="section-title"><div><p className="eyebrow">Orders</p><h1>주문 내역</h1><p>주문 상품과 배송 상태는 주문 상세에서 확인할 수 있어요.</p></div></div>{orders.length === 0 ? <div className="empty-state-panel"><p className="eyebrow">아직 시작 전</p><h2>아직 주문한 상품이 없어요.</h2><p>우리 아이에게 필요한 상품을 찾아보세요. 주문 후 배송 상태를 여기에서 확인할 수 있습니다.</p><Link className="button button-primary" href="/products">상품 둘러보기</Link></div> : <ul className="history-list order-history-list">{orders.map((order) => <li className="order-row" key={order.orderId}><div><strong>주문 {order.orderNumber}</strong><span>{formatDateTime(order.createdAt)}</span></div><span className="status-badge">{formatOrderStatus(order.status)}</span><strong>{formatPrice(order.paymentAmount)}</strong><Link className="button button-secondary" href={`/orders/${order.orderId}`}>상세 보기</Link></li>)}</ul>}</section>;
}
