"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatPetType } from "@/lib/frontend-utils";
import { petDraft, petPatch, petProfileLoadState, petWeightError, type PetDraft } from "@/lib/pet-profile";
import { v2Api, type Pet } from "@/lib/v2-api";

export function PetProfileScreen() {
  const auth = useAuth();
  const [pets, setPets] = useState<Pet[] | null>(null);
  const [loadingError, setLoadingError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [draft, setDraft] = useState<PetDraft | null>(null);
  const [creating, setCreating] = useState(false);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [createName, setCreateName] = useState("");
  const [createType, setCreateType] = useState<Pet["petType"]>("DOG");
  const [message, setMessage] = useState<string | null>(null);
  const [messageKind, setMessageKind] = useState<"success" | "error">("error");

  const load = useCallback(async () => {
    if (auth.status !== "authenticated") return;
    setLoadingError(null);
    try {
      setPets((await v2Api.pets.list()).body.items);
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); return; }
      setLoadingError(error instanceof ApiError ? error.message : "반려동물 목록을 불러오지 못했습니다.");
    }
  }, [auth]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load, retry]);

  function beginEdit(pet: Pet) { setEditingId(pet.petId); setDraft(petDraft(pet)); setMessage(null); }
  function updateDraft(patch: Partial<PetDraft>) { setDraft((current) => current ? { ...current, ...patch } : current); }

  async function createPet(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = createName.trim();
    if (!name) { setMessageKind("error"); setMessage("반려동물 이름을 입력해 주세요."); return; }
    setCreating(true); setMessage(null);
    try {
      const pet = (await auth.executeWithCsrf((csrf) => v2Api.pets.create({ name, petType: createType }, csrf))).body;
      setPets((current) => [...(current ?? []), pet]); setCreateName(""); setMessageKind("success"); setMessage("반려동물 프로필을 등록했습니다.");
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") auth.markAnonymous();
      else { setMessageKind("error"); setMessage(error instanceof ApiError ? error.message : "반려동물을 등록하지 못했습니다. 입력을 유지한 채 다시 시도해 주세요."); }
    } finally { setCreating(false); }
  }

  async function savePet(pet: Pet) {
    if (!draft || savingId !== null) return;
    const weightError = petWeightError(draft.weightKg);
    if (weightError) { setMessageKind("error"); setMessage(weightError); return; }
    const changes = petPatch(pet, draft);
    if (Object.keys(changes).length === 0) { setMessageKind("success"); setMessage("변경된 내용이 없습니다."); setEditingId(null); setDraft(null); return; }
    if (!draft.name.trim()) { setMessageKind("error"); setMessage("반려동물 이름을 입력해 주세요."); return; }
    setSavingId(pet.petId); setMessage(null);
    try {
      const updated = (await auth.executeWithCsrf((csrf) => v2Api.pets.patch(pet.petId, changes, csrf))).body;
      setPets((current) => current?.map((item) => item.petId === updated.petId ? updated : item) ?? current); setEditingId(null); setDraft(null); setMessageKind("success"); setMessage("반려동물 프로필을 수정했습니다.");
    } catch (error) {
      if (error instanceof ApiError && error.code === "PET_NOT_FOUND") { await load(); setMessage("반려동물을 찾을 수 없어 목록을 새로 확인했습니다."); }
      else if (error instanceof ApiError && error.code === "AUTH_REQUIRED") auth.markAnonymous();
      else setMessage(error instanceof ApiError ? error.message : "반려동물 프로필을 저장하지 못했습니다. 입력을 유지한 채 다시 시도해 주세요.");
      setMessageKind("error");
    } finally { setSavingId(null); }
  }

  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="반려동물 프로필을 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/pets")}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  const loadState = petProfileLoadState(auth.status, pets, loadingError);
  if (loadState === "error") return <ErrorState title="반려동물 프로필을 불러오지 못했습니다." message={loadingError!} onRetry={() => { setLoadingError(null); setPets(null); setRetry((value) => value + 1); }} />;
  if (loadState === "auth-loading" || loadState === "loading") return <LoadingState>반려동물 프로필을 불러오고 있습니다.</LoadingState>;
  if (!pets) return <LoadingState>반려동물 프로필을 불러오고 있습니다.</LoadingState>;

  return <section className="pet-profile-screen"><header className="page-heading"><p className="eyebrow">Pet profile</p><h1>반려동물 프로필</h1><p>등록한 반려동물의 기본 정보를 확인하고 필요한 부분만 수정할 수 있어요.</p></header>{message ? <p className={messageKind === "error" ? "error-summary" : "notice-success"} role={messageKind === "error" ? "alert" : "status"}>{message}</p> : null}
    <section className="section-card" aria-labelledby="pet-list-title"><div className="section-title"><h2 id="pet-list-title">등록된 반려동물</h2><span className="count-badge">{pets.length}마리</span></div>{pets.length ? <div className="pet-profile-grid">{pets.map((pet) => <PetCard key={pet.petId} pet={pet} editing={editingId === pet.petId} draft={editingId === pet.petId ? draft : null} weightError={editingId === pet.petId && draft ? petWeightError(draft.weightKg) : null} busy={savingId === pet.petId} onEdit={() => beginEdit(pet)} onCancel={() => { setEditingId(null); setDraft(null); }} onDraft={updateDraft} onSave={() => void savePet(pet)} />)}</div> : <div className="empty-callout"><strong>등록된 반려동물이 없습니다.</strong><span>아래에서 첫 프로필을 등록해 보세요.</span></div>}</section>
    <section className="section-card" aria-labelledby="pet-create-title"><h2 id="pet-create-title">반려동물 등록</h2><form className="pet-create-form" onSubmit={createPet}><label className="form-field" htmlFor="new-pet-name">이름<input id="new-pet-name" className="input" value={createName} maxLength={50} onChange={(event) => setCreateName(event.target.value)} disabled={creating} /></label><fieldset className="form-section"><legend>종</legend><label className="cycle-option"><input type="radio" name="new-pet-type" checked={createType === "DOG"} onChange={() => setCreateType("DOG")} disabled={creating} />개</label><label className="cycle-option"><input type="radio" name="new-pet-type" checked={createType === "CAT"} onChange={() => setCreateType("CAT")} disabled={creating} />고양이</label></fieldset><button className="button button-primary" type="submit" disabled={creating}>{creating ? "등록 중" : "반려동물 등록"}</button></form></section>
  </section>;
}

function PetCard({ pet, editing, draft, weightError, busy, onEdit, onCancel, onDraft, onSave }: { pet: Pet; editing: boolean; draft: PetDraft | null; weightError: string | null; busy: boolean; onEdit: () => void; onCancel: () => void; onDraft: (patch: Partial<PetDraft>) => void; onSave: () => void }) {
  if (!editing || !draft) return <article className="pet-profile-card"><div className="section-title"><h3>{pet.name}</h3><span className="status-badge">{pet.profileComplete ? "프로필 작성 완료" : "프로필 정보 보완 필요"}</span></div><dl className="detail-list"><dt>종</dt><dd>{formatPetType(pet.petType)}</dd><dt>품종</dt><dd>{pet.breed ?? "미입력"}</dd><dt>몸무게</dt><dd>{pet.weightKg === null ? "미입력" : `${pet.weightKg}kg`}</dd></dl><button className="button button-secondary" type="button" onClick={onEdit}>프로필 수정</button></article>;
  return <article className="pet-profile-card" aria-labelledby={`pet-edit-${pet.petId}`}><h3 id={`pet-edit-${pet.petId}`}>{pet.name} 프로필 수정</h3><div className="pet-edit-form"><label className="form-field">이름<input className="input" value={draft.name} maxLength={50} onChange={(event) => onDraft({ name: event.target.value })} disabled={busy} /></label><label className="form-field">종<input className="input" value={formatPetType(pet.petType)} readOnly /></label><label className="form-field">품종<input className="input" value={draft.breed} maxLength={80} onChange={(event) => onDraft({ breed: event.target.value })} disabled={busy} /></label><label className="form-field">몸무게(kg)<input className="input" type="number" min="0.01" max="200" step="0.01" value={draft.weightKg} onChange={(event) => onDraft({ weightKg: event.target.value })} disabled={busy} /></label>{weightError ? <p className="field-error" role="alert">{weightError}</p> : null}</div><div className="button-row"><button className="button button-primary" type="button" onClick={onSave} disabled={busy}>{busy ? "저장 중" : "변경 저장"}</button><button className="button button-secondary" type="button" onClick={onCancel} disabled={busy}>취소</button></div></article>;
}
