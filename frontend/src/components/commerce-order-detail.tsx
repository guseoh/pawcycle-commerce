"use client";

import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { commerceFinalApi, type OrderDetail } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";

export function CommerceOrderDetail({ orderId }: { orderId: string }) {
  const { executeWithCsrf } = useAuth();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const load = () => { setMessage(null); return commerceFinalApi.order(orderId).then(setOrder).catch((error: unknown) => setMessage(error instanceof Error ? error.message : "주문을 불러오지 못했습니다.")); };
  useEffect(() => { void commerceFinalApi.order(orderId).then(setOrder).catch((error: unknown) => setMessage(error instanceof Error ? error.message : "주문을 불러오지 못했습니다.")); }, [orderId]);
  async function submit(action: "cancel" | "return") { const reason = window.prompt(action === "cancel" ? "취소 사유를 입력하세요." : "반품 사유를 입력하세요."); if (!reason) return; setPending(true); try { await executeWithCsrf((csrf) => action === "cancel" ? commerceFinalApi.cancellation(orderId, reason, csrf) : commerceFinalApi.returnRequest(orderId, reason, csrf)); load(); } catch (error) { setMessage(error instanceof Error ? error.message : "요청을 처리하지 못했습니다."); } finally { setPending(false); } }
  if (!order && !message) return <LoadingState>주문을 불러오고 있습니다.</LoadingState>;
  if (!order) return <ErrorState title="주문을 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={load} />;
  return <section className="section-card order-detail-panel"><p className="eyebrow">Order</p><h1>주문 {order.orderNumber}</h1><div className="order-detail-grid"><div><span>주문 상태</span><strong className="status-badge">{order.status}</strong></div><div><span>결제 금액</span><strong>{order.paymentAmount.toLocaleString()}원</strong></div><div><span>배송 상태</span><strong>{order.delivery?.status ?? "결제 대기"}</strong></div>{order.delivery?.trackingNumber ? <div><span>운송장</span><strong>{order.delivery.trackingNumber}</strong></div> : null}<div><span>환불</span><strong>{order.refunds.length ? order.refunds.map((refund) => `${refund.status} (${refund.attemptNo}차)`).join(", ") : "없음"}</strong></div></div>{message ? <p className="error-summary" role="alert">{message}</p> : null}<div className="button-row">{order.availableActions.includes("REQUEST_CANCELLATION") ? <button className="button button-danger" disabled={pending} onClick={() => void submit("cancel")}>주문 취소</button> : null}{order.availableActions.includes("REQUEST_RETURN") ? <button className="button button-secondary" disabled={pending} onClick={() => void submit("return")}>반품 요청</button> : null}</div>{order.refunds.some((refund) => refund.status === "UNKNOWN") ? <p className="provider-block">환불 상태를 확인 중입니다. 중복 환불 요청은 할 수 없습니다.</p> : null}</section>;
}
