"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { commerceFinalApi, type Address, type AddressRequest } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";
import { CsrfRefreshError } from "@/lib/csrf-lifecycle";
import { buildLoginHref, formatIsoLocalDate, formatPetType, formatPrice, formatScheduleStatus, formatSubscriptionStatus, subscriptionIssueCopy, userFacingCatalogLabel } from "@/lib/frontend-utils";
import { newIdempotencyKey, v2Api, type CycleSuggestionResponse, type PlanVersion, type SubscriptionItemDetail, type V2SubscriptionDetail } from "@/lib/v2-api";
import { addonErrorCopy } from "@/lib/subscription-addon";
import { cycleSuggestionCopy } from "@/lib/subscription-cycle-suggestion";
import { SubscriptionAddonPicker } from "./subscription-addon-picker";

type Command = "change-plan" | "change-delivery-cycle" | "reschedule-next" | "skip-next" | "pause" | "resume" | "cancel" | "set-next-delivery-addon" | "remove-next-delivery-addon";
type CommandKey = { key: string; fingerprint: string };
const LABEL: Record<Command, string> = { "change-plan": "플랜 변경", "change-delivery-cycle": "배송 주기 변경", "reschedule-next": "다음 배송일 변경", "skip-next": "다음 회차 건너뛰기", pause: "일시정지", resume: "재개", cancel: "해지", "set-next-delivery-addon": "추가 상품 담기", "remove-next-delivery-addon": "추가 상품 제거" };
const EMPTY_ADDRESS: AddressRequest = { name: "", recipientName: "", recipientPhone: "", postalCode: "", addressLine1: "", addressLine2: "" };
const fingerprint = (body: Record<string, unknown>) => JSON.stringify(body, Object.keys(body).sort());

export function Mvp2SubscriptionDetail({ subscriptionId, created, replayed, basePath = "/mvp2/subscriptions" }: { subscriptionId: string; created: boolean; replayed: boolean; basePath?: string }) {
  const auth = useAuth(); const router = useRouter();
  const [subscription, setSubscription] = useState<V2SubscriptionDetail | null>(null); const [etag, setEtag] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null); const [messageKind, setMessageKind] = useState<"success" | "error" | null>(null);
  const [requestKey, setRequestKey] = useState(0); const [pending, setPending] = useState<Command | null>(null);
  const [scheduledDate, setScheduledDate] = useState(""); const [deliveryCycleWeeks, setDeliveryCycleWeeks] = useState("");
  const [plans, setPlans] = useState<PlanVersion[] | null>(null); const [plansError, setPlansError] = useState<string | null>(null); const [plansRetryKey, setPlansRetryKey] = useState(0); const [planVersionId, setPlanVersionId] = useState("");
  const [cycleSuggestion, setCycleSuggestion] = useState<CycleSuggestionResponse | null>(null); const [cycleSuggestionLoading, setCycleSuggestionLoading] = useState(false); const [cycleSuggestionError, setCycleSuggestionError] = useState<string | null>(null); const [cycleSuggestionRetry, setCycleSuggestionRetry] = useState(0);
  const [address, setAddress] = useState<AddressRequest>(EMPTY_ADDRESS); const [addressSaving, setAddressSaving] = useState(false); const [shippingEditorOpen, setShippingEditorOpen] = useState(false);
  const keys = useRef<Partial<Record<Command, CommandKey>>>({}); const errorRef = useRef<HTMLDivElement>(null); const cycleInputRef = useRef<HTMLSelectElement>(null);
  const returnTo = `${basePath}/${subscriptionId}`; const focusError = () => requestAnimationFrame(() => errorRef.current?.focus());
  const reload = useCallback(() => { setSubscription(null); setMessage(null); setMessageKind(null); setRequestKey((key) => key + 1); }, []);

  useEffect(() => { if (auth.status === "anonymous") router.replace(buildLoginHref(returnTo)); }, [auth.status, returnTo, router]);
  useEffect(() => { if (auth.status !== "authenticated") return; let active = true;
    void v2Api.subscriptions.detail(subscriptionId).then((response) => { if (!active) return; setSubscription(response.body); setEtag(response.etag); setPlans(null); setPlansError(null); setScheduledDate(response.body.nextDelivery?.scheduledDate ?? ""); setDeliveryCycleWeeks(String(response.body.nextDelivery?.deliveryCycleWeeks ?? response.body.currentSnapshot.deliveryCycleWeeks)); }).catch((error: unknown) => { if (!active) return; if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.replace(buildLoginHref(returnTo)); return; } setMessage(error instanceof ApiError ? error.message : "구독 상세를 불러오지 못했습니다."); setMessageKind("error"); });
    return () => { active = false; };
  }, [auth, requestKey, returnTo, router, subscriptionId]);
  const canLoadPlans = Boolean(subscription?.pet && (subscription.availableActions?.includes("CHANGE_PLAN") || subscription.availableActions?.includes("CHANGE_DELIVERY_CYCLE")));
  const planPetId = subscription?.pet?.petId ?? null;
  useEffect(() => { if (!canLoadPlans || planPetId === null) return; let active = true; void v2Api.plans.list(planPetId).then(({ body }) => { if (!active) return; setPlans(body.items); setPlansError(null); }).catch((error: unknown) => { if (!active) return; setPlans(null); setPlansError(error instanceof ApiError ? error.message : "플랜을 불러오지 못했습니다."); }); return () => { active = false; }; }, [canLoadPlans, planPetId, plansRetryKey]);
  const subscriptionStatus = subscription?.status;
  const loadedSubscriptionId = subscription?.subscriptionId ?? null;
  const subscriptionVersion = subscription?.version;
  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(() => {
      if (!active) return;
      if (subscriptionStatus !== "ACTIVE" || loadedSubscriptionId === null) { setCycleSuggestion(null); setCycleSuggestionError(null); setCycleSuggestionLoading(false); return; }
      setCycleSuggestionLoading(true); setCycleSuggestionError(null);
      void v2Api.subscriptions.cycleSuggestion(loadedSubscriptionId).then(({ body }) => { if (active) setCycleSuggestion(body); }).catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError && error.code === "CYCLE_SUGGESTION_INSUFFICIENT_HISTORY") { setCycleSuggestion(null); setCycleSuggestionError(null); }
        else { setCycleSuggestion(null); setCycleSuggestionError(error instanceof ApiError ? error.message : "배송 주기 제안을 불러오지 못했습니다."); }
      }).finally(() => { if (active) setCycleSuggestionLoading(false); });
    }, 0);
    return () => { active = false; window.clearTimeout(timer); };
  }, [cycleSuggestionRetry, loadedSubscriptionId, subscriptionStatus, subscriptionVersion]);

  const hasAction = (action: string) => Boolean(subscription?.availableActions?.includes(action));
  const effectivePlanVersionId = subscription?.pendingChange?.planVersionId ?? subscription?.currentSnapshot.planVersionId;
  const effectiveCycle = subscription?.pendingChange?.deliveryCycleWeeks ?? subscription?.currentSnapshot.deliveryCycleWeeks;
  const effectivePlan = plans?.find((plan) => plan.planVersionId === effectivePlanVersionId);
  const allowedCycles = effectivePlan?.allowedDeliveryCycleWeeks ?? [];
  const changePlanCandidates = plans?.filter((plan) => effectiveCycle !== undefined && plan.allowedDeliveryCycleWeeks.includes(effectiveCycle)) ?? [];
  const selectedChangePlan = changePlanCandidates.find((plan) => plan.planVersionId === Number(planVersionId)) ?? null;
  const plansReady = plans !== null && plansError === null;
  const plansLoading = plans === null && plansError === null;
  async function runCommand(command: Command, body: Record<string, unknown> = {}) {
    if (!subscription || !etag || pending) { setMessage("최신 구독 정보를 다시 불러온 뒤 시도해 주세요."); setMessageKind("error"); focusError(); return; }
    const bodyFingerprint = fingerprint(body);
    const stored = keys.current[command];
    const commandKey = stored?.fingerprint === bodyFingerprint ? stored : { key: newIdempotencyKey(), fingerprint: bodyFingerprint };
    keys.current[command] = commandKey;
    setMessage(null); setMessageKind(null); setPending(command);
    try { const response = await auth.executeWithCsrf((csrf) => v2Api.subscriptions.command(subscription.subscriptionId, command, body, csrf, etag, commandKey.key)); setSubscription(response.body); setEtag(response.etag); setPlans(null); setPlansError(null); keys.current[command] = undefined; setScheduledDate(response.body.nextDelivery?.scheduledDate ?? ""); setDeliveryCycleWeeks(String(response.body.nextDelivery?.deliveryCycleWeeks ?? response.body.currentSnapshot.deliveryCycleWeeks)); setMessage(response.replayed ? "이전 성공 결과를 다시 표시했습니다." : `${LABEL[command]} 요청이 반영되었습니다.`); setMessageKind("success"); }
    catch (error) { setMessageKind("error"); if (error instanceof CsrfRefreshError) setMessage("보안 정보를 갱신하지 못했습니다. 같은 요청으로 다시 시도할 수 있습니다."); else if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.push(buildLoginHref(returnTo)); return; } else if (error instanceof ApiError && error.code === "SUBSCRIPTION_VERSION_MISMATCH") { setMessage("다른 변경이 먼저 반영되었습니다. 최신 정보를 확인한 뒤 다시 선택해 주세요."); setRequestKey((key) => key + 1); } else if (addonErrorCopy(error)) setMessage(addonErrorCopy(error)!); else setMessage(error instanceof ApiError ? error.message : "요청을 처리하지 못했습니다."); focusError(); }
    finally { setPending(null); }
  }
  async function updateShippingAddress() { if (!subscription || addressSaving) return; setAddressSaving(true); setMessage(null); try { await auth.executeWithCsrf((csrf) => commerceFinalApi.updateSubscriptionShipping(subscription.subscriptionId, address, csrf)); setMessage("배송지 정보를 반영했습니다. 최신 상태를 확인합니다."); setMessageKind("success"); setShippingEditorOpen(false); setRequestKey((key) => key + 1); } catch (error) { if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.push(buildLoginHref(returnTo)); return; } setMessage(error instanceof ApiError ? error.message : "배송지 정보를 저장하지 못했습니다."); setMessageKind("error"); focusError(); } finally { setAddressSaving(false); } }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (subscription?.issue?.code === "SHIPPING_ADDRESS_REQUIRED" && subscription.availableActions?.includes("UPDATE_SHIPPING_ADDRESS")) setShippingEditorOpen(true);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [subscription?.availableActions, subscription?.issue?.code]);

  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (auth.status === "loading" || auth.status === "anonymous" || (!subscription && !message)) return <LoadingState>구독 상세를 불러오고 있습니다.</LoadingState>;
  if (!subscription) return <ErrorState title="구독 상세를 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={reload}><Link className="button button-secondary" href={basePath}>목록으로</Link></ErrorState>;
  return <div className="detail-stack">
    <Link className="breadcrumb" href={basePath}>← 내 구독</Link>
    {created ? <div className="notice-success" role="status">구독이 생성되었습니다. {replayed ? "이전 성공 결과를 다시 표시했습니다." : ""}</div> : null}
    {message ? <div className={messageKind === "error" ? "error-summary" : "notice-success"} ref={errorRef} tabIndex={-1} role={messageKind === "error" ? "alert" : "status"}>{message}</div> : null}
    <section className="section-card subscription-overview"><p className="eyebrow">정기배송 관리</p><h1>{subscription.pet?.name ?? "내 정기배송"}</h1><dl className="detail-list"><dt>상태</dt><dd><span className="status-badge">{formatSubscriptionStatus(subscription.status)}</span></dd><dt>현재 금액</dt><dd>{formatPrice(subscription.currentSnapshot.packagePriceKrw)}</dd><dt>배송 주기</dt><dd>{subscription.currentSnapshot.deliveryCycleWeeks}주마다</dd></dl></section>
    <DeliverySection subscription={subscription} pending={pending !== null} onRemove={(skuId) => void runCommand("remove-next-delivery-addon", { skuId })} onSet={(body) => void runCommand("set-next-delivery-addon", body)} />
    {subscription.pendingChange ? <section className="section-card"><h2>적용 예정 변경</h2><dl className="detail-list"><dt>적용일</dt><dd>{formatIsoLocalDate(subscription.pendingChange.appliesOn)}</dd><dt>변경 금액</dt><dd>{formatPrice(subscription.pendingChange.packagePriceKrw)}</dd><dt>변경 주기</dt><dd>{subscription.pendingChange.deliveryCycleWeeks}주마다</dd></dl><ItemList items={subscription.pendingChange.items} /></section> : null}
    <section className="section-card subscription-actions"><h2>구독 관리</h2><p>표시되는 작업은 서버가 현재 허용한 작업입니다.</p><div className="button-row">{(["skip-next", "pause", "resume", "cancel"] as Command[]).filter((command) => hasAction(command.replaceAll("-", "_").toUpperCase())).map((command) => <button key={command} className={`button ${command === "cancel" ? "button-danger" : "button-secondary"}`} type="button" disabled={Boolean(pending)} onClick={() => void runCommand(command)}>{pending === command ? "처리 중" : LABEL[command]}</button>)}</div>
      {hasAction("RESCHEDULE_NEXT") ? <form className="command-panel" onSubmit={(event) => { event.preventDefault(); if (scheduledDate) void runCommand("reschedule-next", { scheduledDate }); }}><h3>다음 배송일 변경</h3><label className="form-field">배송 예정일<input className="input" type="date" value={scheduledDate} onChange={(event) => setScheduledDate(event.target.value)} required disabled={Boolean(pending)} /></label><button className="button button-secondary" type="submit" disabled={!scheduledDate || Boolean(pending)}>{pending === "reschedule-next" ? "변경 중" : "배송일 변경"}</button></form> : null}
      {hasAction("CHANGE_DELIVERY_CYCLE") && plansLoading ? <p className="plan-state" role="status">배송 주기 선택지를 불러오고 있습니다.</p> : null}
      {plansError ? <ErrorState title="플랜을 불러오지 못했습니다." message={plansError} onRetry={() => { setPlansError(null); setPlansRetryKey((key) => key + 1); }} /> : null}
      {hasAction("CHANGE_DELIVERY_CYCLE") && plansReady && allowedCycles.length === 0 ? <p className="plan-state plan-state-empty">현재 선택할 수 있는 배송 주기가 없습니다.</p> : null}
      {hasAction("CHANGE_DELIVERY_CYCLE") && cycleSuggestionError ? <div className="inline-alert" role="alert"><span>배송 주기 제안을 불러오지 못했습니다. 구독 관리는 계속 사용할 수 있습니다.</span><button className="button button-secondary" type="button" onClick={() => setCycleSuggestionRetry((value) => value + 1)}>제안 다시 시도</button></div> : null}
      {hasAction("CHANGE_DELIVERY_CYCLE") && cycleSuggestion && cycleSuggestion.suggestion && !cycleSuggestionLoading ? <div className="cycle-suggestion"><div><strong>배송 주기 제안</strong><p>{cycleSuggestionCopy(cycleSuggestion)}</p></div><button className="button button-secondary" type="button" disabled={Boolean(pending)} onClick={() => { setDeliveryCycleWeeks(String(cycleSuggestion.suggestion!.deliveryCycleWeeks)); requestAnimationFrame(() => cycleInputRef.current?.focus()); }}>제안 적용</button></div> : null}
      {hasAction("CHANGE_DELIVERY_CYCLE") && plansReady && allowedCycles.length > 0 ? <form className="command-panel" onSubmit={(event) => { event.preventDefault(); const weeks = Number(deliveryCycleWeeks); if (allowedCycles.includes(weeks)) void runCommand("change-delivery-cycle", { deliveryCycleWeeks: weeks }); }}><h3>배송 주기 변경</h3><label className="form-field">배송 주기<select ref={cycleInputRef} className="input" value={deliveryCycleWeeks} onChange={(event) => setDeliveryCycleWeeks(event.target.value)} disabled={Boolean(pending)}>{allowedCycles.map((weeks) => <option key={weeks} value={weeks}>{weeks}주마다</option>)}</select></label><button className="button button-secondary" type="submit" disabled={!allowedCycles.includes(Number(deliveryCycleWeeks)) || Boolean(pending)}>{pending === "change-delivery-cycle" ? "변경 중" : "배송 주기 변경"}</button></form> : null}
      {hasAction("CHANGE_PLAN") && plansReady ? <form className="command-panel" onSubmit={(event) => event.preventDefault()}><h3>플랜 변경</h3><p className="field-help">현재 적용 정보와 서버가 제공한 후보를 비교한 뒤 명시적으로 확정합니다. 상품 구성 수량은 여기서 수정할 수 없습니다.</p><label className="form-field">플랜<select className="input" value={planVersionId} onChange={(event) => setPlanVersionId(event.target.value)} disabled={Boolean(pending)}><option value="">플랜을 선택하세요</option>{changePlanCandidates.map((plan) => <option key={plan.planVersionId} value={plan.planVersionId}>{plan.planName} · {formatPrice(plan.packagePriceKrw)}</option>)}</select></label>{selectedChangePlan ? <div className="plan-comparison" aria-label="플랜 변경 비교"><h4>변경 내용 확인</h4><dl className="detail-list"><dt>현재 플랜</dt><dd>{effectivePlan?.planName ?? "현재 적용 플랜"} · {formatPrice(subscription.pendingChange?.packagePriceKrw ?? subscription.currentSnapshot.packagePriceKrw)}</dd><dt>선택 플랜</dt><dd>{selectedChangePlan.planName} · {formatPrice(selectedChangePlan.packagePriceKrw)}</dd><dt>적용 배송 주기</dt><dd>{effectiveCycle}주마다 (서버 허용 범위)</dd><dt>대상 반려동물</dt><dd>{formatPetType(selectedChangePlan.targetPetType)}</dd><dt>판매 기간</dt><dd>{formatIsoLocalDate(selectedChangePlan.sale.startsOn)} ~ {selectedChangePlan.sale.endsOn ? formatIsoLocalDate(selectedChangePlan.sale.endsOn) : "제한 없음"}</dd></dl><p className="field-help">현재 구성: {(subscription.pendingChange?.items ?? subscription.currentSnapshot.items).map((item, index) => `구성 ${index + 1} · ${item.quantity}개`).join(" · ") || "구성 없음"}</p><p className="field-help">선택 구성: {selectedChangePlan.items.map((item, index) => `구성 ${index + 1} · ${item.quantity}개`).join(" · ") || "구성 없음"}</p><button className="button button-primary" type="button" disabled={Boolean(pending)} onClick={() => void runCommand("change-plan", { planVersionId: selectedChangePlan.planVersionId })}>{pending === "change-plan" ? "변경 중" : "이 플랜으로 변경 확정"}</button></div> : null}</form> : null}
    </section>
    {hasAction("UPDATE_SHIPPING_ADDRESS") ? <section className={`section-card shipping-address-panel${subscription.issue?.code === "SHIPPING_ADDRESS_REQUIRED" ? " issue-card" : ""}`} aria-labelledby="shipping-address-title"><p className="eyebrow">배송지</p><h2 id="shipping-address-title">{subscription.issue?.code === "SHIPPING_ADDRESS_REQUIRED" ? "배송지를 확인해 주세요." : "정기배송 배송지 변경"}</h2>{subscription.issue?.code === "SHIPPING_ADDRESS_REQUIRED" ? <p role="alert">{subscription.issue.message}</p> : <p className="field-help">현재 정기배송에 사용할 배송지를 입력하고 저장하세요.</p>}{shippingEditorOpen ? <AddressForm address={address} setAddress={setAddress} saving={addressSaving} onSubmit={updateShippingAddress} /> : <button className="button button-secondary" type="button" onClick={() => setShippingEditorOpen(true)}>배송지 변경</button>}</section> : null}
    {subscription.issue && subscription.issue.code !== "SHIPPING_ADDRESS_REQUIRED" ? <section className="section-card issue-card"><p className="eyebrow">조치 필요</p><h2>{subscriptionIssueCopy(subscription.issue.code)}</h2><p>{subscription.issue.message}</p>{subscription.issue.code === "BILLING_METHOD_REQUIRED" && hasAction("REGISTER_BILLING_METHOD") ? <Link className="button button-primary" href="/billing-methods">결제수단 등록 상태 확인</Link> : null}{subscription.issue.code === "PAYMENT_SUPPORT_REQUIRED" ? <p className="field-help">결제 상태 확인은 고객지원에서 이어서 진행합니다. 이 화면에서는 결제를 재시도하지 않습니다.</p> : null}{subscription.issue.code === "STOCK_UNAVAILABLE" ? <p className="field-help">재고가 확보되면 서버가 다음 배송을 다시 판단합니다. 임의 대체나 자동 재시도는 제공하지 않습니다.</p> : null}</section> : null}
    <section className="contextual-support"><h2>정기배송 도움이 필요하신가요?</h2><p>현재 정기배송 정보를 확인한 뒤 고객지원에서 해결 방법을 살펴보세요.</p><Link className="button button-secondary" href="/support">정기배송 고객지원</Link></section>
  </div>;
}

function DeliverySection({ subscription, pending, onRemove, onSet }: { subscription: V2SubscriptionDetail; pending: boolean; onRemove: (skuId: number) => void; onSet: (body: Record<string, unknown>) => void }) {
  const canRemove = subscription.availableActions?.includes("REMOVE_NEXT_DELIVERY_ADDON") ?? false;
  const hasStockIssue = subscription.issue?.code === "STOCK_UNAVAILABLE" && canRemove;
  return <section className="section-card subscription-delivery-section"><h2>다음 배송</h2>{subscription.nextDelivery ? <><dl className="detail-list"><dt>배송 예정일</dt><dd>{formatIsoLocalDate(subscription.nextDelivery.scheduledDate)}</dd><dt>배송 상태</dt><dd>{formatScheduleStatus(subscription.nextDelivery.status)}</dd><dt>기본 금액</dt><dd>{formatPrice(subscription.nextDelivery.packagePriceKrw)}</dd><dt>배송 주기</dt><dd>{subscription.nextDelivery.deliveryCycleWeeks}주마다</dd></dl><h3>기본 구성</h3><ItemList items={subscription.nextDelivery.items} />{subscription.nextDelivery.addOns.length ? <div className="subscription-addons"><h3>이번 배송 추가 상품</h3>{hasStockIssue ? <p className="provider-block" role="alert">재고 문제로 이번 배송이 보류되었습니다. 추가 상품을 제거한 뒤 다음 자동 처리에서 재고를 다시 확인합니다.</p> : null}<ul className="history-list">{subscription.nextDelivery.addOns.map((addon) => <li key={addon.skuId}><div><Link href={`/products/${addon.productId}`}><strong>{userFacingCatalogLabel(addon.productName, "상품")}</strong></Link><span>{userFacingCatalogLabel(addon.skuName, "상품 옵션")} · {addon.quantity}개 · {formatPrice(addon.unitPriceKrw)}</span></div><strong>{formatPrice(addon.lineAmountKrw)}</strong>{canRemove ? <button className="button button-danger" type="button" disabled={pending} onClick={() => onRemove(addon.skuId)}>추가 상품 제거</button> : null}</li>)}</ul></div> : null}<dl className="detail-list subscription-delivery-totals"><dt>추가 상품 금액</dt><dd>{formatPrice(subscription.nextDelivery.addOnTotalKrw)}</dd><dt>예상 주문 금액</dt><dd>{formatPrice(subscription.nextDelivery.orderTotalKrw)}</dd></dl><SubscriptionAddonPicker subscription={subscription} pending={pending} onSet={onSet} /></> : <p>현재 예정된 다음 배송이 없습니다.</p>}</section>;
}
function ItemList({ items }: { items: SubscriptionItemDetail[] }) { return <ul className="history-list subscription-item-list">{items.map((item, index) => { const productName = userFacingCatalogLabel(item.productName, "상품"); const skuName = userFacingCatalogLabel(item.skuName, "상품 옵션"); return <li key={`${productName}-${skuName}-${index}`}>{item.thumbnailUrl ? <img src={item.thumbnailUrl} alt={productName} loading="lazy" /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}<div>{item.productId ? <Link href={`/products/${item.productId}`}><strong>{productName}</strong></Link> : <strong>{productName}</strong>}<span>{skuName} · {item.quantity}개</span></div></li>; })}</ul>; }
type SavedAddressState = { status: "loading" } | { status: "success"; items: Address[] } | { status: "error"; message: string };

function AddressForm({ address, setAddress, saving, onSubmit }: { address: AddressRequest; setAddress: (address: AddressRequest) => void; saving: boolean; onSubmit: () => Promise<void> }) {
  const auth = useAuth();
  const [savedState, setSavedState] = useState<SavedAddressState>({ status: "loading" });
  const [retryKey, setRetryKey] = useState(0);
  const loadSavedAddresses = useCallback(() => {
    let active = true;
    setSavedState({ status: "loading" });
    void commerceFinalApi.addresses().then((items) => { if (active) setSavedState({ status: "success", items }); }).catch((error: unknown) => {
      if (!active) return;
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); return; }
      setSavedState({ status: "error", message: error instanceof ApiError ? error.message : "저장 주소를 불러오지 못했습니다." });
    });
    return () => { active = false; };
  }, [auth]);
  useEffect(() => {
    let cancel: () => void = () => undefined;
    const timer = window.setTimeout(() => { cancel = loadSavedAddresses() ?? (() => undefined); }, 0);
    return () => { window.clearTimeout(timer); cancel(); };
  }, [loadSavedAddresses, retryKey]);
  const fields: Array<[keyof AddressRequest, string, boolean]> = [["name", "배송지 이름", false], ["recipientName", "받는 분", true], ["recipientPhone", "연락처", true], ["postalCode", "우편번호", true], ["addressLine1", "주소", true], ["addressLine2", "상세 주소", false]];
  const savedAddresses = savedState.status === "success" ? savedState.items : [];
  return <form className="form-section" onSubmit={(event) => { event.preventDefault(); void onSubmit(); }}><p className="field-help">저장 주소를 선택하면 아래 입력 초안에 복사됩니다. 선택만으로 정기배송 배송지는 바뀌지 않아요.</p>{savedState.status === "loading" ? <p role="status">저장 주소를 불러오는 중입니다.</p> : null}{savedState.status === "error" ? <div className="inline-alert" role="alert"><span>{savedState.message} 직접 입력으로 계속할 수 있어요.</span><button className="button button-secondary" type="button" onClick={() => setRetryKey((value) => value + 1)}>저장 주소 다시 시도</button></div> : null}{savedState.status === "success" && savedAddresses.length === 0 ? <p className="field-help">저장된 주소가 없어 직접 입력합니다.</p> : null}{savedAddresses.length ? <label className="form-field">저장 주소에서 복사<select className="input" defaultValue="" onChange={(event) => { const saved = savedAddresses.find((item) => item.addressId === Number(event.target.value)); if (!saved) return; const { name, recipientName, recipientPhone, postalCode, addressLine1, addressLine2 } = saved; setAddress({ name, recipientName, recipientPhone, postalCode, addressLine1, addressLine2 }); }}><option value="">직접 입력</option>{savedAddresses.map((item) => <option key={item.addressId} value={item.addressId}>{item.name || item.recipientName} · {item.addressLine1}</option>)}</select></label> : null}{fields.map(([field, label, required]) => <label className="form-field" key={field}>{label}<input className="input" required={required} value={address[field]} onChange={(event) => setAddress({ ...address, [field]: event.target.value })} /></label>)}<button className="button button-primary" type="submit" disabled={saving}>{saving ? "변경 중" : "확인한 주소로 배송지 변경"}</button></form>;
}
