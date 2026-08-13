"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { commerceFinalApi, type OrderSummary } from "@/lib/commerce-final-api";

export function CommerceOrderList() {
  const [orders, setOrders] = useState<OrderSummary[] | null>(null); const [error, setError] = useState<string | null>(null);
  const load = () => commerceFinalApi.orders().then(setOrders).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "주문을 불러오지 못했습니다."));
  useEffect(() => { void load(); }, []);
  if (!orders && !error) return <LoadingState>주문을 불러오고 있습니다.</LoadingState>;
  if (!orders) return <ErrorState title="주문을 불러오지 못했습니다." message={error ?? "다시 시도해 주세요."} onRetry={() => void load()} />;
  return <section className="section-card"><h1>주문 내역</h1>{orders.length === 0 ? <p>주문 내역이 없습니다.</p> : <ul className="history-list">{orders.map((order) => <li key={order.orderId}><strong>{order.orderNumber}</strong><span>{order.status} · {order.paymentAmount.toLocaleString()}원</span><Link className="button button-secondary" href={`/orders/${order.orderId}`}>상세</Link></li>)}</ul>}</section>;
}
