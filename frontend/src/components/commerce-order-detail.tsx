"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi, type OrderDetail } from "@/lib/commerce-final-api";
import { buildLoginHref, formatDateTime, formatDeliveryStatus, formatOrderStatus, formatPaymentStatus, formatPrice, notifyCommerceChanged, userFacingCatalogLabel } from "@/lib/frontend-utils";
import { finalProductApi, type OrderSubscriptionOption } from "@/lib/final-product-api";
import { newIdempotencyKey } from "@/lib/v2-api";
import { v2Api, type Pet } from "@/lib/v2-api";

type RequestAction = "cancel" | "return";
const ACTION_LABEL: Record<RequestAction, string> = { cancel: "주문 취소", return: "반품 요청" };

function methodLabel(payment: OrderDetail["payment"]): string {
  if (!payment) return "결제 정보 준비 중";
  return payment.provider === "TOSS" ? "온라인 결제" : "결제 수단 확인 중";
}

function refundLabel(status: string): string {
  return { PROCESSING: "환불 처리 중", SUCCEEDED: "환불 완료", FAILED: "환불 실패", UNKNOWN: "환불 확인 필요" }[status] ?? "환불 상태 확인 중";
}

function cancellationLabel(status: string): string {
  return { REQUESTED: "취소 요청", REFUND_PENDING: "환불 대기", COMPLETED: "취소 완료" }[status] ?? "취소 상태 확인 중";
}

function returnLabel(status: string): string {
  return { REQUESTED: "반품 요청", APPROVED: "반품 승인", REJECTED: "반품 거절", REFUND_PENDING: "환불 대기", COMPLETED: "반품 완료" }[status] ?? "반품 상태 확인 중";
}

export function CommerceOrderDetail({ orderId }: { orderId: string }) {
  const auth = useAuth();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [messageKind, setMessageKind] = useState<"success" | "error" | null>(null);
  const [pending, setPending] = useState(false);
  const [reordering, setReordering] = useState(false);
  const [quickReorderResult, setQuickReorderResult] = useState<{ orderId: string; skipped: number } | null>(null);
  const [requestAction, setRequestAction] = useState<RequestAction | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);
  const quickReorderAttempt = useRef<{ orderId: string; key: string } | null>(null);
  const requestOpener = useRef<HTMLElement | null>(null);
  const [subscriptionOptions, setSubscriptionOptions] = useState<OrderSubscriptionOption[] | null>(null);
  const [subscriptionOptionPets, setSubscriptionOptionPets] = useState<Pet[] | null>(null);
  const [subscriptionOptionsStatus, setSubscriptionOptionsStatus] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [subscriptionOptionsError, setSubscriptionOptionsError] = useState<string | null>(null);
  const [subscriptionOptionPetError, setSubscriptionOptionPetError] = useState<string | null>(null);
  const [subscriptionOptionsRetry, setSubscriptionOptionsRetry] = useState(0);

  const load = useCallback(async (clearMessage = true): Promise<boolean> => {
    if (clearMessage) { setMessage(null); setMessageKind(null); }
    try {
      setOrder(await commerceFinalApi.order(orderId));
      return true;
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); return false; }
      setMessageKind("error");
      setMessage("주문을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
      return false;
    }
  }, [auth, orderId]);

  useEffect(() => {
    quickReorderAttempt.current = null;
  }, [orderId]);

  useEffect(() => {
    const loadedOrderId = order?.orderId ?? null;
    if (loadedOrderId === null) return;
    let active = true;
    const timer = window.setTimeout(() => {
      if (!active) return;
      setSubscriptionOptionsStatus("loading"); setSubscriptionOptionsError(null); setSubscriptionOptionPetError(null); setSubscriptionOptionPets(null); setSubscriptionOptions(null);
      void finalProductApi.orderSubscriptionOptions(loadedOrderId).then(async (result) => {
        if (!active) return;
        if (!result.options.length) { setSubscriptionOptions([]); setSubscriptionOptionsStatus("ready"); return; }
        try {
          const pets = await v2Api.pets.list();
          if (active) { setSubscriptionOptionPets(pets.body.items); setSubscriptionOptions(result.options); setSubscriptionOptionsStatus("ready"); }
        } catch (error) {
          if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); return; }
          if (active) { setSubscriptionOptions(result.options); setSubscriptionOptionsStatus("ready"); setSubscriptionOptionPetError("반려동물 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."); }
        }
      }).catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError && error.code === "ORDER_NOT_FOUND") { setSubscriptionOptions([]); setSubscriptionOptionsStatus("ready"); return; }
        setSubscriptionOptionsError("정기배송 전환 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."); setSubscriptionOptionsStatus("error");
      });
    }, 0);
    return () => { active = false; window.clearTimeout(timer); };
  }, [auth, order?.orderId, subscriptionOptionsRetry]);

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
    } catch {
      setReasonError("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setPending(false);
    }
  }

  async function quickReorder() {
    if (!order || reordering) return;
    setReordering(true);
    setMessage(null);
    setMessageKind(null);
    setQuickReorderResult(null);
    try {
      const attempt = quickReorderAttempt.current?.orderId === orderId
        ? quickReorderAttempt.current
        : { orderId, key: newIdempotencyKey() };
      quickReorderAttempt.current = attempt;
      const response = await auth.executeWithCsrf((csrf) => commerceFinalApi.quickReorder(orderId, csrf, attempt.key));
      setQuickReorderResult({ orderId, skipped: response.skippedItems.length });
      if (response.addedItems.length > 0) notifyCommerceChanged();
      if (response.addedItems.length > 0 && response.skippedItems.length === 0) {
        setMessageKind("success");
        setMessage("주문 상품을 장바구니에 다시 담았습니다.");
      } else if (response.addedItems.length > 0) {
        setMessageKind("success");
        setMessage(`${response.addedItems.length}개 상품을 담았고 ${response.skippedItems.length}개 상품은 건너뛰었습니다.`);
      } else if (response.skippedItems.length > 0) {
        setMessageKind("error");
        setMessage("현재 다시 담을 수 있는 상품이 없습니다. 상품 상태를 확인해 주세요.");
      } else {
        setMessageKind("success");
        setMessage("다시 담을 상품이 없습니다.");
      }
      quickReorderAttempt.current = null;
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") {
        auth.markAnonymous();
        return;
      }
      if (error instanceof ApiError && error.code === "IDEMPOTENCY_KEY_CONFLICT") quickReorderAttempt.current = null;
      setMessageKind("error");
      setMessage("재주문 결과를 확인하지 못했습니다. 같은 요청으로 다시 시도해 주세요.");
    } finally {
      setReordering(false);
    }
  }

  const quickReorderSkipped = quickReorderResult?.orderId === orderId ? quickReorderResult.skipped : 0;
  const refunds = order?.refunds ?? [];
  const availableActions = order?.availableActions ?? [];

  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="주문 상세를 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref(`/orders/${orderId}`)}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (!order && !message) return <LoadingState>주문을 불러오고 있습니다.</LoadingState>;
  if (!order) return <ErrorState title="주문을 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={() => void load()}><Link className="button button-secondary" href="/orders">주문 내역으로</Link></ErrorState>;

  return <section className="order-detail-stack"><Link className="breadcrumb" href="/orders">← 주문 내역</Link><header className="page-heading"><p className="eyebrow">Order</p><h1>주문 상세</h1><p>주문 번호 {order.orderNumber} · {formatDateTime(order.createdAt)}</p></header>{message ? <p className={messageKind === "error" ? "error-summary" : "notice-success"} role={messageKind === "error" ? "alert" : "status"}>{message}</p> : null}
    <section className="section-card order-overview" aria-labelledby="order-summary-title"><div className="section-title"><h2 id="order-summary-title">주문 상태</h2><span className="status-badge">{formatOrderStatus(order.status)}</span></div><dl className="order-detail-grid"><div><dt>주문 일시</dt><dd>{formatDateTime(order.createdAt)}</dd></div><div><dt>결제 상태</dt><dd>{formatPaymentStatus(order.payment?.status)}</dd></div><div><dt>결제 수단</dt><dd>{methodLabel(order.payment)}</dd></div><div><dt>배송 상태</dt><dd>{formatDeliveryStatus(order.delivery?.status)}</dd></div>{order.delivery?.trackingNumber ? <div><dt>운송장 번호</dt><dd>{order.delivery.trackingNumber}</dd></div> : null}</dl></section>
    <section className="section-card" aria-labelledby="order-items-title"><div className="section-title"><h2 id="order-items-title">주문 상품</h2><span className="count-badge">{order.items.length}개</span></div><ul className="order-item-list">{order.items.map((item) => <li key={item.skuId}><div><strong>{userFacingCatalogLabel(item.productNameSnapshot, "반려동물 상품")}</strong><span>{userFacingCatalogLabel(item.skuNameSnapshot, "상품 옵션")} · {item.quantity}개</span></div><span>{formatPrice(item.unitPrice)} × {item.quantity}</span><strong>{formatPrice(item.lineAmount)}</strong></li>)}</ul></section>
    <section className="section-card" aria-labelledby="order-price-title"><h2 id="order-price-title">결제 금액</h2><dl className="price-summary"><div><dt>상품 금액</dt><dd>{formatPrice(order.originalAmount)}</dd></div><div><dt>할인</dt><dd>{order.discountAmount ? `-${formatPrice(order.discountAmount)}` : formatPrice(0)}</dd></div><div><dt>배송비</dt><dd>{order.shippingFee ? formatPrice(order.shippingFee) : "무료"}</dd></div><div className="price-summary-total"><dt>최종 결제 금액</dt><dd>{formatPrice(order.paymentAmount)}</dd></div></dl></section>
    <section className="section-card after-sales-panel" aria-labelledby="after-sales-title"><div className="section-title"><h2 id="after-sales-title">취소·반품·환불</h2><span className="field-help">주문을 다시 열어도 최신 상태를 표시합니다.</span></div>{order.cancellation || order.return || refunds.length ? <dl className="after-sales-list">{order.cancellation ? <div><dt>취소 상태</dt><dd><span className="status-badge">{cancellationLabel(order.cancellation.status)}</span></dd></div> : null}{order.return ? <div><dt>반품 상태</dt><dd><span className="status-badge">{returnLabel(order.return.status)}</span>{order.return.rejectionReason ? <span className="field-help">거절 사유: {order.return.rejectionReason}</span> : null}</dd></div> : null}{refunds.map((refund) => <div key={refund.refundId}><dt>환불 #{refund.refundId}</dt><dd><span className="status-badge">{refundLabel(refund.status)}</span><span>{formatPrice(refund.amount)}</span></dd></div>)}</dl> : <p className="field-help">현재 취소·반품·환불 내역이 없습니다.</p>}</section>
    <section className="section-card" aria-labelledby="order-address-title"><h2 id="order-address-title">배송지</h2><address className="order-address"><strong>{order.recipientName ?? "배송지 정보 없음"}</strong><span>{order.recipientPhone ?? ""}</span><span>{order.postalCode ? `(${order.postalCode}) ` : ""}{order.addressLine1 ?? ""} {order.addressLine2 ?? ""}</span></address></section>
    <section className="section-card order-actions"><h2>주문 관리</h2><div className="button-row"><button className="button button-primary" type="button" disabled={reordering || pending} onClick={() => void quickReorder()}>{reordering ? "장바구니에 담는 중" : "다시 담기"}</button>{availableActions.includes("REQUEST_CANCELLATION") ? <button className="button button-danger" disabled={pending || reordering} onClick={() => openRequest("cancel")}>주문 취소</button> : null}{availableActions.includes("REQUEST_RETURN") ? <button className="button button-secondary" disabled={pending || reordering} onClick={() => openRequest("return")}>반품 요청</button> : null}</div>{quickReorderSkipped > 0 ? <p className="field-help">판매 중지·품절 상품 {quickReorderSkipped}개는 현재 판매 상태에 따라 건너뛰었습니다.</p> : null}{refunds.some((refund) => refund.status === "UNKNOWN") ? <p className="inline-alert">환불 상태를 확인 중입니다. 중복 환불 요청은 할 수 없습니다.</p> : null}</section>
    <OrderSubscriptionOptionsPanel orderId={order.orderId} status={subscriptionOptionsStatus} options={subscriptionOptions} pets={subscriptionOptionPets} error={subscriptionOptionsError} petError={subscriptionOptionPetError} onRetry={() => setSubscriptionOptionsRetry((value) => value + 1)} />
    {requestAction ? <div className="dialog-backdrop" role="presentation"><div className="request-dialog" role="dialog" aria-modal="true" aria-labelledby="request-dialog-title" onKeyDown={handleDialogKeyDown}><h2 id="request-dialog-title">{ACTION_LABEL[requestAction]}</h2><p>처리를 위해 사유를 남겨 주세요.</p><form onSubmit={submitRequest}><label className="form-field" htmlFor="request-reason">사유<textarea id="request-reason" className="input textarea" maxLength={500} value={reason} onChange={(event) => { setReason(event.target.value); setReasonError(null); }} autoFocus aria-describedby={reasonError ? "request-reason-error" : undefined} /></label>{reasonError ? <p id="request-reason-error" className="field-error" role="alert">{reasonError}</p> : null}<div className="button-row"><button className="button button-secondary" type="button" disabled={pending} onClick={closeRequest}>닫기</button><button className="button button-primary" type="submit" disabled={pending}>{pending ? "접수 중" : "접수하기"}</button></div></form></div></div> : null}
  </section>;
}

function OrderSubscriptionOptionsPanel({ orderId, status, options, pets, error, petError, onRetry }: { orderId: number; status: "idle" | "loading" | "ready" | "error"; options: OrderSubscriptionOption[] | null; pets: Pet[] | null; error: string | null; petError: string | null; onRetry: () => void }) {
  const [selectedPets, setSelectedPets] = useState<Record<number, string>>({});
  const [selectedCycles, setSelectedCycles] = useState<Record<number, string>>({});
  if (status === "idle" || (status === "ready" && !options?.length)) return null;
  if (status === "loading") return <section className="section-card" aria-labelledby="subscription-option-title"><h2 id="subscription-option-title">정기배송으로 이어가기</h2><p role="status">전환 가능한 정기배송을 확인하고 있습니다.</p></section>;
  if (status === "error") return <section className="section-card" aria-labelledby="subscription-option-title"><h2 id="subscription-option-title">정기배송으로 이어가기</h2><div className="inline-alert" role="alert"><span>{error}</span><button className="button button-secondary" type="button" onClick={onRetry}>다시 시도</button></div></section>;
  if (petError) return <section className="section-card order-subscription-options" aria-labelledby="subscription-option-title"><div className="section-title"><div><p className="eyebrow">Repeat commerce</p><h2 id="subscription-option-title">정기배송으로 이어가기</h2><p>이 주문 상품과 맞는 현재 판매 플랜을 확인해 보세요.</p></div></div><div className="inline-alert" role="alert"><span>반려동물 정보를 확인하지 못했습니다. 호환 여부는 불명이며, 반려동물 정보를 확인한 뒤 시작할 수 있습니다. {petError}</span><button className="button button-secondary" type="button" onClick={onRetry}>다시 시도</button></div><div className="order-option-list">{options!.map((option) => <article className="order-option-card" key={option.planVersionId}><h3>{option.planName}</h3><p>{option.matchingProductIds.length}개 주문 상품과 일치 · {formatPrice(option.packagePriceKrw)}</p><p className="field-help">호환 여부: 불명 · 반려동물 정보를 확인한 뒤 시작할 수 있습니다.</p></article>)}</div></section>;
  if (!pets) return <section className="section-card" aria-labelledby="subscription-option-title"><h2 id="subscription-option-title">정기배송으로 이어가기</h2><p role="status">반려동물 정보를 확인하고 있습니다.</p></section>;
  return <section className="section-card order-subscription-options" aria-labelledby="subscription-option-title"><div className="section-title"><div><p className="eyebrow">Repeat commerce</p><h2 id="subscription-option-title">정기배송으로 이어가기</h2><p>이 주문 상품과 맞는 현재 판매 플랜을 확인해 보세요.</p></div></div><div className="order-option-list">{options!.map((option) => { const compatiblePets = pets.filter((pet) => option.compatibleOwnedPetIds.includes(pet.petId)); const petId = Number(selectedPets[option.planVersionId]); const cycle = Number(selectedCycles[option.planVersionId]); const valid = compatiblePets.some((pet) => pet.petId === petId) && option.allowedDeliveryCycleWeeks.includes(cycle); const href = `/subscriptions/new?petId=${petId}&planVersionId=${option.planVersionId}&deliveryCycleWeeks=${cycle}&fromOrderId=${orderId}`; return <article className="order-option-card" key={option.planVersionId}><h3>{option.planName}</h3><p>{option.matchingProductIds.length}개 주문 상품과 일치 · {formatPrice(option.packagePriceKrw)}</p>{compatiblePets.length ? <><label className="form-field">반려동물<select className="input" value={selectedPets[option.planVersionId] ?? ""} onChange={(event) => setSelectedPets((current) => ({ ...current, [option.planVersionId]: event.target.value }))}><option value="">선택하세요</option>{compatiblePets.map((pet) => <option key={pet.petId} value={pet.petId}>{pet.name}</option>)}</select></label><label className="form-field">배송 주기<select className="input" value={selectedCycles[option.planVersionId] ?? ""} onChange={(event) => setSelectedCycles((current) => ({ ...current, [option.planVersionId]: event.target.value }))}><option value="">선택하세요</option>{option.allowedDeliveryCycleWeeks.map((weeks) => <option key={weeks} value={weeks}>{weeks}주마다</option>)}</select></label><Link className={`button button-primary${valid ? "" : " is-disabled"}`} aria-disabled={!valid} href={valid ? href : "#subscription-option-title"} onClick={(event) => { if (!valid) event.preventDefault(); }}>정기배송 시작하기</Link></> : <><p className="field-help">호환되는 내 반려동물이 없습니다.</p><Link className="button button-secondary" href="/pets">반려동물 프로필 등록하기</Link></>}</article>; })}</div></section>;
}