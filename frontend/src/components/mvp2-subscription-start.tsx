"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { CsrfRefreshError } from "@/lib/csrf-lifecycle";
import { formatPetType, formatPrice, buildLoginHref } from "@/lib/frontend-utils";
import { newIdempotencyKey, v2Api, type Pet, type PlanVersion } from "@/lib/v2-api";

type LoadState = "idle" | "loading" | "error";

const FORM_ERROR_CODES = new Set(["VALIDATION_FAILED", "PLAN_NOT_AVAILABLE", "PLAN_PET_TYPE_MISMATCH", "DELIVERY_CYCLE_NOT_ALLOWED"]);

export function Mvp2SubscriptionStart() {
  const router = useRouter();
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
  const [retryKey, setRetryKey] = useState(0);
  const [planRetryKey, setPlanRetryKey] = useState(0);
  const createKeyRef = useRef<string | null>(null);
  const errorRef = useRef<HTMLDivElement>(null);

  const loadPets = useCallback(() => {
    setState("loading");
    setMessage(null);
    void v2Api.pets.list().then(({ body }) => {
      setPets(body.items);
      setState("idle");
    }).catch((error: unknown) => {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") {
        auth.markAnonymous();
        router.replace(buildLoginHref("/mvp2/subscriptions/new"));
        return;
      }
      setState("error");
      setMessage(error instanceof ApiError ? error.message : "반려동물 목록을 불러오지 못했습니다.");
    });
  }, [auth, router]);

  useEffect(() => {
    if (auth.status === "anonymous") router.replace(buildLoginHref("/mvp2/subscriptions/new"));
    if (auth.status === "authenticated") queueMicrotask(loadPets);
  }, [auth.status, loadPets, router, retryKey]);

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
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.replace(buildLoginHref("/mvp2/subscriptions/new")); return; }
      setMessage(error instanceof ApiError ? error.message : "반려동물을 등록하지 못했습니다. 입력을 유지한 채 다시 시도할 수 있습니다.");
      focusError();
    } finally { setPetSubmitting(false); }
  }

  async function selectPlan(plan: PlanVersion) {
    if (selectedPetId === null) return;
    setMessage(null);
    try {
      const response = await v2Api.plans.detail(plan.planVersionId, selectedPetId);
      setSelectedPlan(response.body);
      setCycle(response.body.allowedDeliveryCycleWeeks[0] ?? null);
      resetCreateKey();
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "플랜 상세를 불러오지 못했습니다.");
      focusError();
    }
  }

  async function createSubscription(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selectedPetId === null || selectedPlan === null || cycle === null || submitting) return;
    if (auth.status !== "authenticated") { router.push(buildLoginHref("/mvp2/subscriptions/new")); return; }
    setMessage(null);
    setSubmitting(true);
    const idempotencyKey = createKeyRef.current ?? newIdempotencyKey();
    createKeyRef.current = idempotencyKey;
    try {
      const response = await auth.executeWithCsrf((csrf) => v2Api.subscriptions.create({ petId: selectedPetId, planVersionId: selectedPlan.planVersionId, deliveryCycleWeeks: cycle }, csrf, idempotencyKey));
      const id = response.body.subscriptionId;
      createKeyRef.current = null;
      router.push(`/mvp2/subscriptions/${id}?created=1${response.replayed ? "&replayed=1" : ""}`);
    } catch (error) {
      if (error instanceof CsrfRefreshError) setMessage("보안 정보를 갱신하지 못했습니다. 입력을 유지한 채 다시 시도해 주세요.");
      else if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); router.push(buildLoginHref("/mvp2/subscriptions/new")); return; }
      else if (error instanceof ApiError && FORM_ERROR_CODES.has(error.code)) setMessage(error.message);
      else setMessage(error instanceof ApiError ? error.message : "구독을 만들지 못했습니다. 같은 요청 키로 다시 시도할 수 있습니다.");
      focusError();
    } finally { setSubmitting(false); }
  }

  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (auth.status === "loading" || auth.status === "anonymous" || state === "loading") return <LoadingState>구독 시작 정보를 불러오고 있습니다.</LoadingState>;
  if (state === "error") return <ErrorState title="반려동물 목록을 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={() => setRetryKey((key) => key + 1)} />;

  return <div className="detail-stack">
    <header className="page-heading"><p className="eyebrow">MVP2 subscription</p><h1>반려동물과 플랜 선택</h1><p>판매 중인 호환 플랜과 서버가 제공한 배송 주기만 선택할 수 있습니다.</p></header>
    {message ? <div className="error-summary" ref={errorRef} tabIndex={-1} role="alert"><h2>확인이 필요합니다.</h2><p>{message}</p></div> : null}
    <section className="section-card" aria-labelledby="pet-select-title">
      <h2 id="pet-select-title">1. 반려동물 선택</h2>
      {pets?.length ? <div className="radio-grid">{pets.map((pet) => <label className="radio-card" key={pet.petId}><input type="radio" name="pet" disabled={submitting} checked={selectedPetId === pet.petId} onChange={() => { setSelectedPetId(pet.petId); resetCreateKey(); }} /><strong>{pet.name}</strong><span>{formatPetType(pet.petType)}</span></label>)}</div> : <p>등록된 반려동물이 없습니다. 아래에서 먼저 등록해 주세요.</p>}
      <form className="inline-form" onSubmit={registerPet} noValidate>
        <label className="form-field">반려동물 이름<input className="input" value={petName} maxLength={50} onChange={(event) => setPetName(event.target.value)} disabled={petSubmitting || submitting} /></label>
        <fieldset className="form-section" disabled={petSubmitting || submitting}><legend>종</legend><label className="cycle-option"><input type="radio" checked={petType === "DOG"} onChange={() => setPetType("DOG")} />개</label><label className="cycle-option"><input type="radio" checked={petType === "CAT"} onChange={() => setPetType("CAT")} />고양이</label></fieldset>
        <button className="button button-secondary" type="submit" disabled={petSubmitting || submitting}>{petSubmitting ? "등록 중" : "반려동물 등록"}</button>
      </form>
    </section>
    {selectedPet ? <section className="section-card" aria-labelledby="plan-select-title"><h2 id="plan-select-title">2. 호환 플랜 선택</h2><p>{selectedPet.name}에게 판매 중인 플랜만 표시합니다.</p>{planState === "loading" ? <LoadingState>호환 플랜을 찾고 있습니다.</LoadingState> : planState === "error" ? <ErrorState title="플랜을 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={() => setPlanRetryKey((key) => key + 1)} /> : plans?.length ? <div className="product-grid">{plans.map((plan) => <article className="product-card" key={plan.planVersionId}><p className="eyebrow">Plan #{plan.planId}</p><h3>{plan.planName}</h3><p>{formatPrice(plan.packagePriceKrw)} · {plan.items.length}개 SKU 구성</p><button className="button button-secondary" type="button" disabled={submitting} onClick={() => void selectPlan(plan)}>상세 선택</button></article>)}</div> : <p>현재 선택할 수 있는 호환 플랜이 없습니다.</p>}</section> : null}
    {selectedPlan ? <form className="section-card" onSubmit={createSubscription} noValidate aria-labelledby="create-title"><h2 id="create-title">3. 배송 주기와 구독 확인</h2><dl className="detail-list"><dt>반려동물</dt><dd>{selectedPet?.name}</dd><dt>플랜</dt><dd>{selectedPlan.planName}</dd><dt>패키지 가격</dt><dd>{formatPrice(selectedPlan.packagePriceKrw)}</dd><dt>구성</dt><dd>{selectedPlan.items.map((item) => `SKU ${item.skuId} × ${item.quantity}`).join(", ")}</dd></dl><fieldset className="form-section" disabled={submitting}><legend>배송 주기</legend><div className="cycle-row">{selectedPlan.allowedDeliveryCycleWeeks.map((weeks) => <label className="cycle-option" key={weeks}><input type="radio" name="cycle" checked={cycle === weeks} onChange={() => { setCycle(weeks); resetCreateKey(); }} />{weeks}주</label>)}</div></fieldset><div className="button-row"><button className="button button-primary" type="submit" disabled={submitting || cycle === null}>{submitting ? "구독 생성 중" : "구독 만들기"}</button><Link className="button button-secondary" href="/mvp2/subscriptions">목록으로</Link></div></form> : null}
  </div>;
}
