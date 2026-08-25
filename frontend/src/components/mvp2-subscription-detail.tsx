"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { commerceFinalApi, type AddressRequest } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";
import { CsrfRefreshError } from "@/lib/csrf-lifecycle";
import { buildLoginHref, formatIsoLocalDate, formatPrice } from "@/lib/frontend-utils";
import { newIdempotencyKey, v2Api, type PlanVersion, type V2SubscriptionDetail } from "@/lib/v2-api";

type Command = "change-plan" | "change-delivery-cycle" | "reschedule-next" | "skip-next" | "pause" | "resume" | "cancel";
type CommandKey = { key: string; fingerprint: string };
const LABEL: Record<Command, string> = { "change-plan": "플랜 변경", "change-delivery-cycle": "배송 주기 변경", "reschedule-next": "다음 배송일 변경", "skip-next": "다음 회차 건너뛰기", pause: "일시정지", resume: "재개", cancel: "해지" };
const EMPTY_ADDRESS: AddressRequest = { name: "", recipientName: "", recipientPhone: "", postalCode: "", addressLine1: "", addressLine2: "" };
const fingerprint = (body: Record<string, unknown>) => JSON.stringify(body, Object.keys(body).sort());

export function Mvp2SubscriptionDetail({ subscriptionId, created, replayed, basePath = "/mvp2/subscriptions" }: { subscriptionId: string; created: boolean; replayed: boolean; basePath?: string }) {
  const auth = useAuth(); const router = useRouter();
  const [subscription, setSubscription] = useState<V2SubscriptionDetail | null>(null); const [etag, setEtag] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null); const [messageKind, setMessageKind] = useState<"success" | "error" | null>(null);
  const [requestKey, setRequestKey] = useState(0); const [pending, setPending] = useState<Command | null>(null);
  const [scheduledDate, setScheduledDate] = useState(""); const [deliveryCycleWeeks, setDeliveryCycleWeeks] = useState("");
  const [plans, setPlans] = useState<PlanVersion[] | null>(null); const [planVersionId, setPlanVersionId] = useState("");
  const [address, setAddress] = useState<AddressRequest>(EMPTY_ADDRESS); const [addressSaving, setAddressSaving] = useState(false);
  const keys = useRef<Partial<Record<Command, CommandKey>>>({}); const errorRef = useRef<HTMLDivElement>(null);
  const returnTo = `${basePath}/${subscriptionId}`; const focusError = () => requestAnimationFrame(() => errorRef.current?.focus());
  const reload = useCallback(() => { setSubscription(null); setMessage(null); setMessageKind(null); setRequestKey((key) => key + 1); }, []);

  useEffect(() => { if (auth.status === "anonymous") router.replace(buildLoginHref(returnTo)); }, [auth.status, returnTo, router]);
  useEffect(() => { if (auth.status !== "authenticated") return; let active = true;
    void v2Api.subscriptions.detail(subscriptionId).then((response) => { if (!active) return; setSubscription(response.body); setEtag(response.etag); setScheduledDate(response.body.nextDelivery?.scheduledDate ?? ""); setDeliveryCycleWeeks(String(response.body.nextDelivery?.deliveryCycleWeeks ?? response.body.currentSnapshot.deliveryCycleWeeks)); }).catch((error: unknown) => { if (!active) return; if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.replace(buildLoginHref(returnTo)); return; } setMessage(error instanceof ApiError ? error.message : "구독 상세를 불러오지 못했습니다."); setMessageKind("error"); });
    return () => { active = false; };
  }, [auth, requestKey, returnTo, router, subscriptionId]);
  useEffect(() => { if (!subscription?.pet || !(subscription.availableActions?.includes("CHANGE_PLAN") || subscription.availableActions?.includes("CHANGE_DELIVERY_CYCLE"))) return; let active = true; void v2Api.plans.list(subscription.pet.petId).then(({ body }) => { if (active) setPlans(body.items); }).catch(() => { if (active) setPlans([]); }); return () => { active = false; }; }, [subscription?.availableActions, subscription?.pet]);

  const hasAction = (action: string) => Boolean(subscription?.availableActions?.includes(action));
  const effectivePlanVersionId = subscription?.pendingChange?.planVersionId ?? subscription?.currentSnapshot.planVersionId;
  const effectiveCycle = subscription?.pendingChange?.deliveryCycleWeeks ?? subscription?.currentSnapshot.deliveryCycleWeeks;
  const effectivePlan = plans?.find((plan) => plan.planVersionId === effectivePlanVersionId);
  const allowedCycles = effectivePlan?.allowedDeliveryCycleWeeks ?? [];
  const changePlanCandidates = plans?.filter((plan) => effectiveCycle !== undefined && plan.allowedDeliveryCycleWeeks.includes(effectiveCycle)) ?? [];
  async function runCommand(command: Command, body: Record<string, unknown> = {}) {
    if (!subscription || !etag || pending) { setMessage("최신 구독 정보를 다시 불러온 뒤 시도해 주세요."); setMessageKind("error"); focusError(); return; }
    const bodyFingerprint = fingerprint(body);
    const stored = keys.current[command];
    const commandKey = stored?.fingerprint === bodyFingerprint ? stored : { key: newIdempotencyKey(), fingerprint: bodyFingerprint };
    keys.current[command] = commandKey;
    setMessage(null); setMessageKind(null); setPending(command);
    try { const response = await auth.executeWithCsrf((csrf) => v2Api.subscriptions.command(subscription.subscriptionId, command, body, csrf, etag, commandKey.key)); setSubscription(response.body); setEtag(response.etag); keys.current[command] = undefined; setScheduledDate(response.body.nextDelivery?.scheduledDate ?? ""); setDeliveryCycleWeeks(String(response.body.nextDelivery?.deliveryCycleWeeks ?? response.body.currentSnapshot.deliveryCycleWeeks)); setMessage(response.replayed ? "이전 성공 결과를 다시 표시했습니다." : `${LABEL[command]} 요청이 반영되었습니다.`); setMessageKind("success"); }
    catch (error) { setMessageKind("error"); if (error instanceof CsrfRefreshError) setMessage("보안 정보를 갱신하지 못했습니다. 같은 요청으로 다시 시도할 수 있습니다."); else if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.push(buildLoginHref(returnTo)); return; } else if (error instanceof ApiError && error.code === "SUBSCRIPTION_VERSION_MISMATCH") { setMessage("다른 변경이 먼저 반영되었습니다. 최신 정보를 확인한 뒤 다시 선택해 주세요."); setRequestKey((key) => key + 1); } else setMessage(error instanceof ApiError ? error.message : "요청을 처리하지 못했습니다."); focusError(); }
    finally { setPending(null); }
  }
  async function updateShippingAddress() { if (!subscription || addressSaving) return; setAddressSaving(true); setMessage(null); try { await auth.executeWithCsrf((csrf) => commerceFinalApi.updateSubscriptionShipping(subscription.subscriptionId, address, csrf)); setMessage("배송지 정보를 반영했습니다. 최신 상태를 확인합니다."); setMessageKind("success"); setRequestKey((key) => key + 1); } catch (error) { setMessage(error instanceof ApiError ? error.message : "배송지 정보를 저장하지 못했습니다."); setMessageKind("error"); focusError(); } finally { setAddressSaving(false); } }

  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (auth.status === "loading" || auth.status === "anonymous" || (!subscription && !message)) return <LoadingState>구독 상세를 불러오고 있습니다.</LoadingState>;
  if (!subscription) return <ErrorState title="구독 상세를 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={reload}><Link className="button button-secondary" href={basePath}>목록으로</Link></ErrorState>;
  return <div className="detail-stack">
    <Link className="breadcrumb" href={basePath}>← 내 구독</Link>
    {created ? <div className="notice-success" role="status">구독이 생성되었습니다. {replayed ? "이전 성공 결과를 다시 표시했습니다." : ""}</div> : null}
    {message ? <div className={messageKind === "error" ? "error-summary" : "notice-success"} ref={errorRef} tabIndex={-1} role={messageKind === "error" ? "alert" : "status"}>{message}</div> : null}
    <section className="section-card"><h1>{subscription.pet?.name ?? "내 정기배송"}</h1><dl className="detail-list"><dt>상태</dt><dd>{subscription.status}</dd><dt>현재 금액</dt><dd>{formatPrice(subscription.currentSnapshot.packagePriceKrw)}</dd><dt>배송 주기</dt><dd>{subscription.currentSnapshot.deliveryCycleWeeks}주마다</dd></dl></section>
    <DeliverySection subscription={subscription} />
    {subscription.pendingChange ? <section className="section-card"><h2>적용 예정 변경</h2><dl className="detail-list"><dt>적용일</dt><dd>{formatIsoLocalDate(subscription.pendingChange.appliesOn)}</dd><dt>변경 금액</dt><dd>{formatPrice(subscription.pendingChange.packagePriceKrw)}</dd><dt>변경 주기</dt><dd>{subscription.pendingChange.deliveryCycleWeeks}주마다</dd></dl><ItemList items={subscription.pendingChange.items} /></section> : null}
    <section className="section-card subscription-actions"><h2>구독 관리</h2><p>표시되는 작업은 서버가 현재 허용한 작업입니다.</p><div className="button-row">{(["skip-next", "pause", "resume", "cancel"] as Command[]).filter((command) => hasAction(command.replaceAll("-", "_").toUpperCase())).map((command) => <button key={command} className={`button ${command === "cancel" ? "button-danger" : "button-secondary"}`} type="button" disabled={Boolean(pending)} onClick={() => void runCommand(command)}>{pending === command ? "처리 중" : LABEL[command]}</button>)}</div>
      {hasAction("RESCHEDULE_NEXT") ? <form className="command-panel" onSubmit={(event) => { event.preventDefault(); if (scheduledDate) void runCommand("reschedule-next", { scheduledDate }); }}><h3>다음 배송일 변경</h3><label className="form-field">배송 예정일<input className="input" type="date" value={scheduledDate} onChange={(event) => setScheduledDate(event.target.value)} required disabled={Boolean(pending)} /></label><button className="button button-secondary" type="submit" disabled={!scheduledDate || Boolean(pending)}>{pending === "reschedule-next" ? "변경 중" : "배송일 변경"}</button></form> : null}
      {hasAction("CHANGE_DELIVERY_CYCLE") && plans === null ? <p className="plan-state" role="status">배송 주기 선택지를 불러오고 있습니다.</p> : null}{hasAction("CHANGE_DELIVERY_CYCLE") && plans !== null && allowedCycles.length === 0 ? <p className="plan-state plan-state-empty">현재 선택할 수 있는 배송 주기가 없습니다.</p> : null}{hasAction("CHANGE_DELIVERY_CYCLE") ? <form className="command-panel" onSubmit={(event) => { event.preventDefault(); const weeks = Number(deliveryCycleWeeks); if (allowedCycles.includes(weeks)) void runCommand("change-delivery-cycle", { deliveryCycleWeeks: weeks }); }}><h3>배송 주기 변경</h3><label className="form-field">배송 주기<select className="input" value={deliveryCycleWeeks} onChange={(event) => setDeliveryCycleWeeks(event.target.value)} disabled={!plans || !allowedCycles.length || Boolean(pending)}>{allowedCycles.map((weeks) => <option key={weeks} value={weeks}>{weeks}주마다</option>)}</select></label><button className="button button-secondary" type="submit" disabled={!allowedCycles.includes(Number(deliveryCycleWeeks)) || Boolean(pending)}>{pending === "change-delivery-cycle" ? "변경 중" : "배송 주기 변경"}</button></form> : null}
      {hasAction("CHANGE_PLAN") ? <form className="command-panel" onSubmit={(event) => { event.preventDefault(); const id = Number(planVersionId); if (changePlanCandidates.some((plan) => plan.planVersionId === id)) void runCommand("change-plan", { planVersionId: id }); }}><h3>플랜 변경</h3><label className="form-field">플랜<select className="input" value={planVersionId} onChange={(event) => setPlanVersionId(event.target.value)} disabled={!plans || Boolean(pending)}><option value="">플랜을 선택하세요</option>{changePlanCandidates.map((plan) => <option key={plan.planVersionId} value={plan.planVersionId}>{plan.planName} · {formatPrice(plan.packagePriceKrw)}</option>)}</select></label><button className="button button-secondary" type="submit" disabled={!changePlanCandidates.some((plan) => plan.planVersionId === Number(planVersionId)) || Boolean(pending)}>{pending === "change-plan" ? "변경 중" : "플랜 변경"}</button></form> : null}
    </section>
    {subscription.issue ? <section className="section-card issue-card"><p className="eyebrow">조치 필요</p><h2>처리가 필요합니다</h2><p>{subscription.issue.message}</p>{subscription.issue.code === "SHIPPING_ADDRESS_REQUIRED" && hasAction("UPDATE_SHIPPING_ADDRESS") ? <AddressForm address={address} setAddress={setAddress} saving={addressSaving} onSubmit={updateShippingAddress} /> : null}{subscription.issue.code === "BILLING_METHOD_REQUIRED" && hasAction("REGISTER_BILLING_METHOD") ? <Link className="button button-primary" href="/billing-methods">결제수단 등록으로 이동</Link> : null}{subscription.issue.code === "PAYMENT_SUPPORT_REQUIRED" || subscription.issue.code === "STOCK_UNAVAILABLE" ? <p className="field-help">이 문제는 고객센터 또는 상품 상태 확인이 필요합니다.</p> : null}</section> : null}
  </div>;
}

function DeliverySection({ subscription }: { subscription: V2SubscriptionDetail }) { return <section className="section-card"><h2>다음 배송</h2>{subscription.nextDelivery ? <><dl className="detail-list"><dt>배송 예정일</dt><dd>{formatIsoLocalDate(subscription.nextDelivery.scheduledDate)}</dd><dt>금액</dt><dd>{formatPrice(subscription.nextDelivery.packagePriceKrw)}</dd><dt>배송 주기</dt><dd>{subscription.nextDelivery.deliveryCycleWeeks}주마다</dd></dl><ItemList items={subscription.nextDelivery.items} /></> : <p>현재 예정된 다음 배송이 없습니다.</p>}</section>; }
function ItemList({ items }: { items: { productName: string; skuName: string; quantity: number }[] }) { return <ul className="history-list">{items.map((item, index) => <li key={`${item.productName}-${item.skuName}-${index}`}><strong>{item.productName}</strong><span>{item.skuName} · {item.quantity}개</span></li>)}</ul>; }
function AddressForm({ address, setAddress, saving, onSubmit }: { address: AddressRequest; setAddress: (address: AddressRequest) => void; saving: boolean; onSubmit: () => Promise<void> }) { const fields: Array<[keyof AddressRequest, string, boolean]> = [["name", "배송지 이름", false], ["recipientName", "받는 분", true], ["recipientPhone", "연락처", true], ["postalCode", "우편번호", true], ["addressLine1", "주소", true], ["addressLine2", "상세 주소", false]]; return <form className="form-section" onSubmit={(event) => { event.preventDefault(); void onSubmit(); }}>{fields.map(([field, label, required]) => <label className="form-field" key={field}>{label}<input className="input" required={required} value={address[field]} onChange={(event) => setAddress({ ...address, [field]: event.target.value })} /></label>)}<button className="button button-primary" type="submit" disabled={saving}>{saving ? "저장 중" : "배송지 저장"}</button></form>; }
