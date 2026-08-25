"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi, type OrderDetail } from "@/lib/commerce-final-api";
import { buildLoginHref, formatDateTime, formatDeliveryStatus, formatOrderStatus, formatPaymentStatus, formatPrice, notifyCommerceChanged } from "@/lib/frontend-utils";

type RequestAction = "cancel" | "return";
const ACTION_LABEL: Record<RequestAction, string> = { cancel: "주문 취소", return: "반품 요청" };

function methodLabel(payment: OrderDetail["payment"]): string {
  if (!payment) return "결제 정보 준비 중";
  return payment.provider === "TOSS" ? "토스 결제" : "결제 수단 확인 중";
}

function refundLabel(status: string): string {
  return { PROCESSING: "환불 처리 중", SUCCEEDED: "환불 완료", FAILED: "환불 실패", UNKNOWN: "환불 확인 필요" }[status] ?? "환불 상태 확인 중";
}

export function CommerceOrderDetail({ orderId }: { orderId: string }) {
  const auth = useAuth();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [messageKind, setMessageKind] = useState<"success" | "error" | null>(null);
  const [pending, setPending] = useState(false);
  const [reordering, setReordering] = useState(false);
  const [requestAction, setRequestAction] = useState<RequestAction | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);
  const reorderedSkuIds = useRef<Set<number>>(new Set());
  const requestOpener = useRef<HTMLElement | null>(null);

  const load = useCallback(async (clearMessage = true): Promise<boolean> => {
    if (clearMessage) { setMessage(null); setMessageKind(null); }
    try {
      setOrder(await commerceFinalApi.order(orderId));
      return true;
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); return false; }
      setMessageKind("error");
      setMessage(error instanceof ApiError ? error.message : "주문을 불러오지 못했습니다.");
      return false;
    }
  }, [auth, orderId]);

  useEffect(() => {
    reorderedSkuIds.current.clear();
  }, [orderId]);

  useEffect(() => {
    if (auth.status !== "authenticated") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [auth.status, load]);

  function openRequest(action: RequestAction) {
    requestOpener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setReason("");
    setReasonError(null);
    setRequestAction(action);
  }

  function closeRequest() {
    setRequestAction(null);
    setReasonError(null);
    window.requestAnimationFrame(() => requestOpener.current?.focus());
  }

  function handleDialogKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape" && !pending) {
      event.preventDefault();
      closeRequest();
      return;
    }
    if (event.key !== "Tab") return;
    const focusable = Array.from(event.currentTarget.querySelectorAll<HTMLElement>("textarea, button:not([disabled]), input:not([disabled]), select:not([disabled]), a[href], [tabindex]:not([tabindex='-1'])"));
    if (!focusable.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }

  async function submitRequest(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = reason.trim();
    if (!value) { setReasonError("사유를 입력해 주세요."); return; }
    if (value.length > 500) { setReasonError("사유는 500자 이내로 입력해 주세요."); return; }
    if (!requestAction) return;
    const action = requestAction;
    setPending(true);
    setReasonError(null);
    try {
      await auth.executeWithCsrf((csrf) => action === "cancel" ? commerceFinalApi.cancellation(orderId, value, csrf) : commerceFinalApi.returnRequest(orderId, value, csrf));
      setReason("");
      closeRequest();
      const refreshed = await load(false);
      if (refreshed) {
        setMessageKind("success");
        setMessage(`${ACTION_LABEL[action]}이 접수되었습니다.`);
      }
    } catch (error) {
      setReasonError(error instanceof ApiError ? error.message : "요청을 처리하지 못했습니다.");
    } finally {
      setPending(false);
    }
  }

  async function quickReorder() {
    if (!order || reordering) return;
    setReordering(true);
    setMessage(null);
    setMessageKind(null);
    let addedThisAttempt = 0;
    try {
      await auth.executeWithCsrf(async (csrf) => {
        for (const item of order.items) {
          if (reorderedSkuIds.current.has(item.skuId)) continue;
          try {
            await commerceFinalApi.addCart(item.skuId, item.quantity, csrf);
            reorderedSkuIds.current.add(item.skuId);
            addedThisAttempt += 1;
          } catch {
            // Continue so independent items can still be restored to the cart.
          }
        }
      });
      if (addedThisAttempt > 0) notifyCommerceChanged();
      const remaining = order.items.filter((item) => !reorderedSkuIds.current.has(item.skuId));
      if (!remaining.length) {
        setMessageKind("success");
        setMessage("주문 상품을 장바구니에 다시 담았습니다.");
      } else if (reorderedSkuIds.current.size > 0) {
        setMessageKind("error");
        setMessage(`${reorderedSkuIds.current.size}개 상품은 담았고 ${remaining.length}개 상품은 담지 못했습니다. 다시 시도하면 성공한 상품은 중복으로 담지 않습니다.`);
      } else {
        setMessageKind("error");
        setMessage("주문 상품을 다시 담지 못했습니다. 구매 가능 상태를 확인해 주세요.");
      }
    } catch (error) {
      if (addedThisAttempt > 0) notifyCommerceChanged();
      setMessageKind("error");
      setMessage(error instanceof ApiError ? error.message : "주문 상품을 다시 담지 못했습니다. 구매 가능 상태를 확인해 주세요.");
    } finally {
      setReordering(false);
    }
  }

  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="주문 상세를 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref(`/orders/${orderId}`)}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (!order && !message) return <LoadingState>주문을 불러오고 있습니다.</LoadingState>;
  if (!order) return <ErrorState title="주문을 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={() => void load()}><Link className="button button-secondary" href="/orders">주문 내역으로</Link></ErrorState>;

  return <section className="order-detail-stack"><Link className="breadcrumb" href="/orders">← 주문 내역</Link><header className="page-heading"><p className="eyebrow">Order</p><h1>주문 상세</h1><p>주문 번호 {order.orderNumber} · {formatDateTime(order.createdAt)}</p></header>{message ? <p className={messageKind === "error" ? "error-summary" : "notice-success"} role={messageKind === "error" ? "alert" : "status"}>{message}</p> : null}
    <section className="section-card order-overview" aria-labelledby="order-summary-title"><div className="section-title"><h2 id="order-summary-title">주문 상태</h2><span className="status-badge">{formatOrderStatus(order.status)}</span></div><dl className="order-detail-grid"><div><dt>주문 일시</dt><dd>{formatDateTime(order.createdAt)}</dd></div><div><dt>결제 상태</dt><dd>{formatPaymentStatus(order.payment?.status)}</dd></div><div><dt>결제 수단</dt><dd>{methodLabel(order.payment)}</dd></div><div><dt>배송 상태</dt><dd>{formatDeliveryStatus(order.delivery?.status)}</dd></div>{order.delivery?.trackingNumber ? <div><dt>운송장 번호</dt><dd>{order.delivery.trackingNumber}</dd></div> : null}</dl></section>
    <section className="section-card" aria-labelledby="order-items-title"><div className="section-title"><h2 id="order-items-title">주문 상품</h2><span className="count-badge">{order.items.length}개</span></div><ul className="order-item-list">{order.items.map((item) => <li key={item.skuId}><div><strong>{item.productNameSnapshot}</strong><span>{item.skuNameSnapshot} · {item.quantity}개</span></div><span>{formatPrice(item.unitPrice)} × {item.quantity}</span><strong>{formatPrice(item.lineAmount)}</strong></li>)}</ul></section>
    <section className="section-card" aria-labelledby="order-price-title"><h2 id="order-price-title">결제 금액</h2><dl className="price-summary"><div><dt>상품 금액</dt><dd>{formatPrice(order.originalAmount)}</dd></div><div><dt>할인</dt><dd>{order.discountAmount ? `-${formatPrice(order.discountAmount)}` : formatPrice(0)}</dd></div><div><dt>배송비</dt><dd>{order.shippingFee ? formatPrice(order.shippingFee) : "무료"}</dd></div><div className="price-summary-total"><dt>최종 결제 금액</dt><dd>{formatPrice(order.paymentAmount)}</dd></div></dl>{order.refunds.length ? <p className="field-help">{order.refunds.map((refund) => `${refundLabel(refund.status)} · ${formatPrice(refund.amount)}`).join(" / ")}</p> : null}</section>
    <section className="section-card" aria-labelledby="order-address-title"><h2 id="order-address-title">배송지</h2><address className="order-address"><strong>{order.recipientName ?? "배송지 정보 없음"}</strong><span>{order.recipientPhone ?? ""}</span><span>{order.postalCode ? `(${order.postalCode}) ` : ""}{order.addressLine1 ?? ""} {order.addressLine2 ?? ""}</span></address></section>
    <section className="section-card order-actions"><h2>주문 관리</h2><div className="button-row"><button className="button button-primary" type="button" disabled={reordering || pending} onClick={() => void quickReorder()}>{reordering ? "장바구니에 담는 중" : "다시 담기"}</button>{order.availableActions.includes("REQUEST_CANCELLATION") ? <button className="button button-danger" disabled={pending || reordering} onClick={() => openRequest("cancel")}>주문 취소</button> : null}{order.availableActions.includes("REQUEST_RETURN") ? <button className="button button-secondary" disabled={pending || reordering} onClick={() => openRequest("return")}>반품 요청</button> : null}</div>{order.refunds.some((refund) => refund.status === "UNKNOWN") ? <p className="provider-block">환불 상태를 확인 중입니다. 중복 환불 요청은 할 수 없습니다.</p> : null}</section>
    {requestAction ? <div className="dialog-backdrop" role="presentation"><div className="request-dialog" role="dialog" aria-modal="true" aria-labelledby="request-dialog-title" onKeyDown={handleDialogKeyDown}><h2 id="request-dialog-title">{ACTION_LABEL[requestAction]}</h2><p>처리를 위해 사유를 남겨 주세요.</p><form onSubmit={submitRequest}><label className="form-field" htmlFor="request-reason">사유<textarea id="request-reason" className="input textarea" maxLength={500} value={reason} onChange={(event) => { setReason(event.target.value); setReasonError(null); }} autoFocus aria-describedby={reasonError ? "request-reason-error" : undefined} /></label>{reasonError ? <p id="request-reason-error" className="field-error" role="alert">{reasonError}</p> : null}<div className="button-row"><button className="button button-secondary" type="button" disabled={pending} onClick={closeRequest}>닫기</button><button className="button button-primary" type="submit" disabled={pending}>{pending ? "접수 중" : "접수하기"}</button></div></form></div></div> : null}
  </section>;
}
