"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { CsrfRefreshError } from "@/lib/csrf-lifecycle";
import { formatPetType, formatPrice, buildLoginHref } from "@/lib/frontend-utils";
import { newIdempotencyKey, v2Api, type Pet, type PlanVersion } from "@/lib/v2-api";
import { parseSubscriptionStartQuery, subscriptionStartQueryKey } from "@/lib/subscription-start-query";

type LoadState = "idle" | "loading" | "error";

const FORM_ERROR_CODES = new Set(["VALIDATION_FAILED", "PLAN_NOT_AVAILABLE", "PLAN_PET_TYPE_MISMATCH", "DELIVERY_CYCLE_NOT_ALLOWED"]);

export function Mvp2SubscriptionStart({ basePath = "/mvp2/subscriptions" }: { basePath?: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const productContext = searchParams.get("productId");
  const skuContext = searchParams.get("skuId");
  const startQuery = parseSubscriptionStartQuery(new URLSearchParams(searchParams.toString()));
  const contextHref = `${basePath}/new${productContext ? `?productId=${encodeURIComponent(productContext)}${skuContext ? `&skuId=${encodeURIComponent(skuContext)}` : ""}` : ""}`;
  const auth = useAuth();
  const [pets, setPets] = useState<Pet[] | null>(null);
  const [plans, setPlans] = useState<PlanVersion[] | null>(null);
  const [selectedPetId, setSelectedPetId] = useState<number | null>(null);
  const [selectedPlan, setSelectedPlan] = useState<PlanVersion | null>(null);
  const [cycle, setCycle] = useState<number | null>(null);
  const [state, setState] = useState<LoadState>("loading");
  const [planState, setPlanState] = useState<LoadState>("idle");
  const [petName, setPetName] = useState("");
  const [petType, setPetType] = useState<"DOG" | "CAT">("DOG");
  const [petSubmitting, setPetSubmitting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [prefillMessage, setPrefillMessage] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const [planRetryKey, setPlanRetryKey] = useState(0);
  const createKeyRef = useRef<string | null>(null);
  const errorRef = useRef<HTMLDivElement>(null);
  const prefillAttempt = useRef(false);
  const prefillKeyRef = useRef<string | undefined>(undefined);
  const prefillKey = subscriptionStartQueryKey(startQuery);

  const loadPets = useCallback(() => {
    setState("loading");
    setMessage(null);
    void v2Api.pets.list().then(({ body }) => {
      setPets(body.items);
      setState("idle");
    }).catch((error: unknown) => {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") {
        auth.markAnonymous();
        router.replace(buildLoginHref(contextHref));
        return;
      }
      setState("error");
      setMessage(error instanceof ApiError ? error.message : "반려동물 목록을 불러오지 못했습니다.");
    });
  }, [auth, contextHref, router]);

  useEffect(() => {
    if (auth.status === "anonymous") router.replace(buildLoginHref(contextHref));
    if (auth.status === "authenticated") queueMicrotask(loadPets);
  }, [auth.status, contextHref, loadPets, router, retryKey]);

  useEffect(() => {
    if (prefillKeyRef.current === prefillKey) return;
    prefillKeyRef.current = prefillKey;
    prefillAttempt.current = false;
    setSelectedPetId(null);
    setSelectedPlan(null);
    setCycle(null);
    setPrefillMessage(null);
  }, [prefillKey]);

  useEffect(() => {
    if (!pets || prefillAttempt.current || startQuery.petId === null) return;
    prefillAttempt.current = true;
    const timer = window.setTimeout(() => {
      if (pets.some((pet) => pet.petId === startQuery.petId)) setSelectedPetId(startQuery.petId);
      else setPrefillMessage("주문에서 이어온 반려동물을 확인할 수 없어 기존 선택 방식으로 진행합니다.");
    }, 0);
    return () => window.clearTimeout(timer);
  }, [pets, startQuery.petId]);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (selectedPetId === null) { if (active) { setPlans(null); setSelectedPlan(null); setCycle(null); } return; }
      setPlanState("loading"); setPlans(null); setSelectedPlan(null); setCycle(null);
      void v2Api.plans.list(selectedPetId).then(({ body }) => { if (active) { setPlans(body.items); setPlanState("idle"); } }).catch((error: unknown) => {
        if (active) { setPlanState("error"); setMessage(error instanceof ApiError ? error.message : "호환 플랜을 불러오지 못했습니다."); }
      });
    });
    return () => { active = false; };
  }, [selectedPetId, planRetryKey]);

  const selectPlan = useCallback(async (plan: PlanVersion, preferredCycle: number | null = null) => {
    if (selectedPetId === null) return;
    setMessage(null); setSelectedPlan(null); setCycle(null);
    try {
      const response = await v2Api.plans.detail(plan.planVersionId, selectedPetId);
      setSelectedPlan(response.body);
      setCycle(preferredCycle !== null && response.body.allowedDeliveryCycleWeeks.includes(preferredCycle) ? preferredCycle : null);
      resetCreateKey();
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "플랜 상세를 불러오지 못했습니다.");
      requestAnimationFrame(() => errorRef.current?.focus());
    }
  }, [selectedPetId]);

  useEffect(() => {
    if (!plans || selectedPetId !== startQuery.petId || startQuery.planVersionId === null || selectedPlan || prefillAttempt.current === false) return;
    const plan = plans.find((candidate) => candidate.planVersionId === startQuery.planVersionId);
    const timer = window.setTimeout(() => {
      if (!plan || (startQuery.deliveryCycleWeeks !== null && !plan.allowedDeliveryCycleWeeks.includes(startQuery.deliveryCycleWeeks))) {
        setPrefillMessage("주문에서 이어온 플랜 또는 배송 주기를 확인할 수 없어 직접 선택해 주세요.");
        return;
      }
      void selectPlan(plan, startQuery.fromOrderId !== null ? startQuery.deliveryCycleWeeks : null);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [plans, selectPlan, selectedPetId, selectedPlan, startQuery.deliveryCycleWeeks, startQuery.fromOrderId, startQuery.petId, startQuery.planVersionId]);

  const selectedPet = useMemo(() => pets?.find((pet) => pet.petId === selectedPetId) ?? null, [pets, selectedPetId]);

  function resetCreateKey() { createKeyRef.current = null; }
  function focusError() { requestAnimationFrame(() => errorRef.current?.focus()); }

  async function registerPet(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = petName.trim();
    setMessage(null);
    if (!name) { setMessage("반려동물 이름을 입력해 주세요."); focusError(); return; }
    setPetSubmitting(true);
    try {
      const response = await auth.executeWithCsrf((csrf) => v2Api.pets.create({ name, petType }, csrf));
      setPets((current) => [...(current ?? []), response.body]);
      setSelectedPetId(response.body.petId);
      setPetName("");
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.replace(buildLoginHref(contextHref)); return; }
      setMessage(error instanceof ApiError ? error.message : "반려동물을 등록하지 못했습니다. 입력을 유지한 채 다시 시도할 수 있습니다.");
      focusError();
    } finally { setPetSubmitting(false); }
  }

  async function createSubscription(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selectedPetId === null || selectedPlan === null || cycle === null || submitting) return;
    if (auth.status !== "authenticated") { router.push(buildLoginHref(contextHref)); return; }
    setMessage(null);
    setSubmitting(true);
    const idempotencyKey = createKeyRef.current ?? newIdempotencyKey();
    createKeyRef.current = idempotencyKey;
    try {
      const response = await auth.executeWithCsrf((csrf) => v2Api.subscriptions.create({ petId: selectedPetId, planVersionId: selectedPlan.planVersionId, deliveryCycleWeeks: cycle }, csrf, idempotencyKey));
      const id = response.body.subscriptionId;
      createKeyRef.current = null;
      router.push(`${basePath}/${id}?created=1${response.replayed ? "&replayed=1" : ""}`);
    } catch (error) {
      if (error instanceof CsrfRefreshError) setMessage("보안 정보를 갱신하지 못했습니다. 입력을 유지한 채 다시 시도해 주세요.");
      else if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.push(buildLoginHref(contextHref)); return; }
      else if (error instanceof ApiError && FORM_ERROR_CODES.has(error.code)) setMessage(error.message);
      else setMessage(error instanceof ApiError ? error.message : "구독을 만들지 못했습니다. 같은 요청 키로 다시 시도할 수 있습니다.");
      focusError();
    } finally { setSubmitting(false); }
  }

  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (auth.status === "loading" || auth.status === "anonymous" || state === "loading") return <LoadingState>구독 시작 정보를 불러오고 있습니다.</LoadingState>;
  if (state === "error") return <ErrorState title="반려동물 목록을 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={() => setRetryKey((key) => key + 1)} />;

  return <div className="subscription-start">
    <header className="page-heading"><Link className="breadcrumb" href={basePath}>← 정기배송 목록</Link><h1>우리 아이의 정기배송 시작</h1><p>반려동물에 맞는 플랜과 주기를 선택해 주세요.</p>{productContext ? <p className="field-help">상품 상세에서 선택한 {skuContext ? "옵션으로 " : "상품으로 "}이어서 선택 중입니다.</p> : null}{startQuery.fromOrderId ? <p className="field-help">주문에서 이어진 추천 플랜입니다. 현재 판매 정보로 다시 확인합니다.</p> : null}{prefillMessage ? <p className="provider-block" role="status">{prefillMessage}</p> : null}</header>
    {message ? <div className="error-summary" ref={errorRef} tabIndex={-1} role="alert"><h2>확인이 필요합니다.</h2><p>{message}</p></div> : null}
    <div className="selection-steps">
      <section className="section-card" aria-labelledby="pet-select-title"><h2 id="pet-select-title">01　반려동물 선택</h2>{pets?.length ? <div className="radio-grid">{pets.map(pet => <label className="radio-card" key={pet.petId}><input type="radio" name="pet" disabled={submitting} checked={selectedPetId === pet.petId} onChange={() => { setSelectedPetId(pet.petId); resetCreateKey(); }} /><strong>{pet.name}</strong><span>{formatPetType(pet.petType)}</span></label>)}</div> : <p>반려동물을 먼저 등록해 주세요.</p>}
        <details open={!pets?.length}><summary>반려동물 등록</summary><form className="inline-form" onSubmit={registerPet} noValidate><label className="form-field">반려동물 이름<input className="input" value={petName} maxLength={50} onChange={event => setPetName(event.target.value)} disabled={petSubmitting || submitting} /></label><fieldset className="form-section" disabled={petSubmitting || submitting}><legend>종</legend><label className="cycle-option"><input name="new-pet-type" type="radio" checked={petType === "DOG"} onChange={() => setPetType("DOG")} />강아지</label><label className="cycle-option"><input name="new-pet-type" type="radio" checked={petType === "CAT"} onChange={() => setPetType("CAT")} />고양이</label></fieldset><button className="button button-secondary" type="submit" disabled={petSubmitting || submitting}>{petSubmitting ? "등록 중" : "반려동물 등록"}</button></form></details>
      </section>
      <section className="section-card" aria-labelledby="plan-select-title"><h2 id="plan-select-title">02　호환 플랜 선택</h2>{!selectedPet ? <p className="field-help">반려동물을 먼저 선택해 주세요.</p> : planState === "loading" ? <LoadingState>호환 플랜을 찾고 있습니다.</LoadingState> : planState === "error" ? <ErrorState headingLevel={3} title="플랜을 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={() => setPlanRetryKey(key => key + 1)} /> : plans?.length ? <div className="product-grid">{plans.map(plan => <article className="product-card" key={plan.planVersionId}><h3>{plan.planName}</h3><p>{formatPrice(plan.packagePriceKrw)} · {plan.items.length}개 구성</p><button className="button button-secondary" type="button" aria-pressed={selectedPlan?.planVersionId === plan.planVersionId} disabled={submitting} onClick={() => void selectPlan(plan)}>{selectedPlan?.planVersionId === plan.planVersionId ? "선택됨" : "상세 선택"}</button></article>)}</div> : <p>현재 선택할 수 있는 호환 플랜이 없습니다.</p>}</section>
      <section className="section-card" aria-labelledby="cycle-select-title"><h2 id="cycle-select-title">03　받는 주기</h2>{selectedPlan ? <fieldset className="form-section" disabled={submitting}><legend>배송 주기</legend><div className="cycle-row">{selectedPlan.allowedDeliveryCycleWeeks.map(weeks => <label className="cycle-option" key={weeks}><input type="radio" name="cycle" checked={cycle === weeks} onChange={() => { setCycle(weeks); resetCreateKey(); }} />{weeks}주</label>)}</div></fieldset> : <p className="field-help">플랜을 선택하면 허용된 주기를 확인할 수 있어요.</p>}</section>
    </div>
    <form className="subscription-review" onSubmit={createSubscription} noValidate aria-labelledby="create-title"><h2 id="create-title">선택한 정기배송</h2><dl className="detail-list"><dt>반려동물</dt><dd>{selectedPet?.name ?? "선택 필요"}</dd><dt>플랜</dt><dd>{selectedPlan?.planName ?? "선택 필요"}</dd><dt>주기</dt><dd>{cycle === null ? "선택 필요" : `${cycle}주마다`}</dd><dt>패키지 가격</dt><dd>{selectedPlan ? formatPrice(selectedPlan.packagePriceKrw) : "플랜 선택 후 확인"}</dd>{selectedPlan ? <><dt>구성</dt><dd>{selectedPlan.items.map((item,index) => <span key={item.skuId}>구성 {index + 1} · {item.quantity}개<br /></span>)}</dd></> : null}</dl><p className="field-help">다음 주문 예정일은 구독을 만든 뒤 확인할 수 있습니다. 장바구니 일반 결제가 아닌 정기배송 구독 생성입니다.</p><button className="button button-primary" type="submit" aria-busy={submitting} disabled={submitting || selectedPetId === null || selectedPlan === null || cycle === null}>{submitting ? "구독 생성 중" : "구독 만들기"}</button></form>
  </div>;
}
