"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { CsrfRefreshError } from "@/lib/csrf-lifecycle";
import { buildLoginHref, formatIsoLocalDate, formatPrice } from "@/lib/frontend-utils";
import { newIdempotencyKey, v2Api, type PlanVersion, type V2SubscriptionDetail } from "@/lib/v2-api";

type Command = "change-plan" | "skip-next" | "pause" | "resume" | "cancel";

const COMMAND_LABEL: Record<Command, string> = { "change-plan": "플랜 변경", "skip-next": "다음 회차 건너뛰기", pause: "일시정지", resume: "재개", cancel: "해지" };

export function Mvp2SubscriptionDetail({ subscriptionId, created, replayed }: { subscriptionId: string; created: boolean; replayed: boolean }) {
  const auth = useAuth();
  const router = useRouter();
  const [subscription, setSubscription] = useState<V2SubscriptionDetail | null>(null);
  const [etag, setEtag] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [requestKey, setRequestKey] = useState(0);
  const [pendingCommand, setPendingCommand] = useState<Command | null>(null);
  const [plans, setPlans] = useState<PlanVersion[] | null>(null);
  const [planVersionId, setPlanVersionId] = useState<number | null>(null);
  const [changeCycle, setChangeCycle] = useState<number | null>(null);
  const commandKeys = useRef<Partial<Record<Command, string>>>({});
  const errorRef = useRef<HTMLDivElement>(null);

  const load = useCallback(() => { setSubscription(null); setMessage(null); setRequestKey((key) => key + 1); }, []);
  const focusError = () => requestAnimationFrame(() => errorRef.current?.focus());

  useEffect(() => { if (auth.status === "anonymous") router.replace(buildLoginHref(`/mvp2/subscriptions/${subscriptionId}`)); }, [auth.status, router, subscriptionId]);
  useEffect(() => {
    if (auth.status !== "authenticated") return;
    let active = true;
    void v2Api.subscriptions.detail(subscriptionId).then((response) => {
      if (!active) return;
      setSubscription(response.body); setEtag(response.etag);
    }).catch((error: unknown) => {
      if (!active) return;
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.replace(buildLoginHref(`/mvp2/subscriptions/${subscriptionId}`)); return; }
      setMessage(error instanceof ApiError ? error.message : "구독 상세를 불러오지 못했습니다.");
    });
    return () => { active = false; };
  }, [auth, requestKey, router, subscriptionId]);

  useEffect(() => {
    if (!subscription?.pet || subscription.status !== "ACTIVE") return;
    let active = true;
    void v2Api.plans.list(subscription.pet.petId).then(({ body }) => { if (active) setPlans(body.items); }).catch(() => { if (active) setPlans([]); });
    return () => { active = false; };
  }, [subscription?.pet, subscription?.status]);

  async function runCommand(command: Command, body: Record<string, unknown> = {}) {
    if (!subscription || !etag || pendingCommand) { setMessage("최신 구독 버전을 다시 불러온 뒤 시도해 주세요."); focusError(); return; }
    setMessage(null); setPendingCommand(command);
    const idempotencyKey = commandKeys.current[command] ?? newIdempotencyKey();
    commandKeys.current[command] = idempotencyKey;
    try {
      const response = await auth.executeWithCsrf((csrf) => v2Api.subscriptions.command(subscription.subscriptionId, command, body, csrf, etag, idempotencyKey));
      setSubscription(response.body); setEtag(response.etag);
      commandKeys.current[command] = undefined;
      setMessage(response.replayed ? "이전 성공 결과를 다시 표시했습니다." : `${COMMAND_LABEL[command]} 요청이 반영되었습니다.`);
      if (command === "change-plan") setPlanVersionId(null);
    } catch (error) {
      if (error instanceof CsrfRefreshError) setMessage("보안 정보를 갱신하지 못했습니다. 같은 요청으로 다시 시도할 수 있습니다.");
      else if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.push(buildLoginHref(`/mvp2/subscriptions/${subscriptionId}`)); return; }
      else if (error instanceof ApiError && error.code === "SUBSCRIPTION_VERSION_MISMATCH") { setMessage("다른 변경이 먼저 반영되었습니다. 최신 상태를 다시 불러온 뒤 선택을 확인해 주세요."); load(); }
      else if (error instanceof ApiError && error.code === "IF_MATCH_REQUIRED") setMessage("최신 버전 정보를 다시 불러와야 합니다.");
      else setMessage(error instanceof ApiError ? error.message : "요청을 처리하지 못했습니다. 같은 요청으로 다시 시도할 수 있습니다.");
      focusError();
    } finally { setPendingCommand(null); }
  }

  const selectedPlan = plans?.find((plan) => plan.planVersionId === planVersionId) ?? null;
  const canChange = subscription?.status === "ACTIVE" && subscription.pet !== null;
  if (auth.status === "loading" || auth.status === "anonymous" || !subscription && !message) return <LoadingState>구독 상세를 불러오고 있습니다.</LoadingState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (!subscription) return <ErrorState title="구독 상세를 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={load}><Link className="button button-secondary" href="/mvp2/subscriptions">목록으로</Link></ErrorState>;

  return <div className="detail-stack">
    <Link className="breadcrumb" href="/mvp2/subscriptions">← 내 구독</Link>
    {created ? <div className="notice-success" role="status">구독이 생성되었습니다. {replayed ? "이전 성공 결과를 다시 표시했습니다." : ""}</div> : null}
    {message ? <div className="notice-success" ref={errorRef} tabIndex={-1} role="status">{message}</div> : null}
    <section className="section-card" aria-labelledby="subscription-title"><p className="eyebrow">Subscription #{subscription.subscriptionId}</p><h1 id="subscription-title">{subscription.pet?.name ?? "기존 구독"}</h1><dl className="detail-list"><dt>상태</dt><dd>{subscription.status}</dd><dt>버전</dt><dd>{subscription.version}</dd><dt>현재 플랜</dt><dd>PlanVersion #{subscription.currentSnapshot.planVersionId}</dd><dt>패키지 가격</dt><dd>{formatPrice(subscription.currentSnapshot.packagePriceKrw)}</dd><dt>배송 주기</dt><dd>{subscription.currentSnapshot.deliveryCycleWeeks}주</dd><dt>구성</dt><dd>{subscription.currentSnapshot.items.map((item) => `SKU ${item.skuId} × ${item.quantity}`).join(", ")}</dd><dt>다음 예정일</dt><dd>{subscription.nextScheduledDate ? formatIsoLocalDate(subscription.nextScheduledDate) : "없음"}</dd>{subscription.pendingSnapshot ? <><dt>변경 예정 플랜</dt><dd>PlanVersion #{subscription.pendingSnapshot.planVersionId} · {formatPrice(subscription.pendingSnapshot.packagePriceKrw)}</dd></> : null}</dl></section>
    <section className="section-card" aria-labelledby="commands-title"><h2 id="commands-title">구독 관리</h2><p>허용되는 작업은 현재 서버 상태와 최신 버전에 따라 결정됩니다.</p><div className="button-row">{subscription.status === "ACTIVE" ? <><button className="button button-secondary" type="button" disabled={Boolean(pendingCommand)} onClick={() => void runCommand("skip-next")}>{pendingCommand === "skip-next" ? "처리 중" : "다음 회차 건너뛰기"}</button><button className="button button-secondary" type="button" disabled={Boolean(pendingCommand)} onClick={() => void runCommand("pause")}>{pendingCommand === "pause" ? "처리 중" : "일시정지"}</button></> : null}{subscription.status === "PAUSED" ? <button className="button button-primary" type="button" disabled={Boolean(pendingCommand)} onClick={() => void runCommand("resume")}>{pendingCommand === "resume" ? "처리 중" : "재개"}</button> : null}{subscription.status !== "CANCELED" ? <button className="button button-danger" type="button" disabled={Boolean(pendingCommand)} onClick={() => void runCommand("cancel")}>{pendingCommand === "cancel" ? "처리 중" : "해지"}</button> : null}</div>{canChange ? <div className="command-panel"><h3>플랜 변경</h3>{plans === null ? <LoadingState>호환 플랜을 불러오고 있습니다.</LoadingState> : plans.length === 0 ? <p>현재 변경할 수 있는 호환 플랜이 없습니다.</p> : <><label className="form-field">플랜<select className="input" value={planVersionId ?? ""} onChange={(event) => { const id = Number(event.target.value); const plan = plans.find((item) => item.planVersionId === id) ?? null; setPlanVersionId(plan?.planVersionId ?? null); setChangeCycle(plan?.allowedDeliveryCycleWeeks[0] ?? null); commandKeys.current["change-plan"] = undefined; }}><option value="">플랜을 선택하세요</option>{plans.map((plan) => <option key={plan.planVersionId} value={plan.planVersionId}>{plan.planName} · {formatPrice(plan.packagePriceKrw)}</option>)}</select></label>{selectedPlan ? <fieldset className="form-section"><legend>배송 주기</legend><div className="cycle-row">{selectedPlan.allowedDeliveryCycleWeeks.map((weeks) => <label className="cycle-option" key={weeks}><input type="radio" name="change-cycle" checked={changeCycle === weeks} onChange={() => { setChangeCycle(weeks); commandKeys.current["change-plan"] = undefined; }} />{weeks}주</label>)}</div></fieldset> : null}<button className="button button-primary" type="button" disabled={!selectedPlan || changeCycle === null || Boolean(pendingCommand)} onClick={() => void runCommand("change-plan", { planVersionId, ...(subscription.pet ? { petId: subscription.pet.petId } : {}) })}>{pendingCommand === "change-plan" ? "변경 중" : "다음 회차부터 플랜 변경"}</button></>}</div> : null}</section>
    <section className="section-card" aria-labelledby="schedule-title"><h2 id="schedule-title">예정 회차</h2>{subscription.schedules.items.length ? <ol className="history-list">{subscription.schedules.items.map((item) => <li key={item.scheduleId}><strong>{formatIsoLocalDate(item.scheduledDate)}</strong><span>{item.status} · snapshot #{item.effectiveSnapshotId}</span></li>)}</ol> : <p>표시할 예정 회차가 없습니다.</p>}<p className="field-help">총 {subscription.schedules.totalElements}건 · 현재 {subscription.schedules.page + 1}페이지</p></section>
    <section className="section-card" aria-labelledby="history-title"><h2 id="history-title">명령 이력</h2>{subscription.commandHistory.items.length ? <ol className="history-list">{subscription.commandHistory.items.map((item, index) => <li key={`${item.occurredAt}-${index}`}><strong>{item.commandType}</strong><span>{item.result} · {item.occurredAt}</span></li>)}</ol> : <p>표시할 명령 이력이 없습니다.</p>}<p className="field-help">총 {subscription.commandHistory.totalElements}건 · 현재 {subscription.commandHistory.page + 1}페이지</p></section>
  </div>;
}
