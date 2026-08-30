"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi, type Address, type AddressRequest } from "@/lib/commerce-final-api";
import { buildAddressLoginHref, sanitizeReturnTo } from "@/lib/frontend-utils";

const EMPTY_ADDRESS: AddressRequest = { name: "", recipientName: "", recipientPhone: "", postalCode: "", addressLine1: "", addressLine2: "" };

export default function AddressesPage() {
  return <Suspense fallback={<LoadingState>배송지 화면을 준비하고 있습니다.</LoadingState>}><AddressesContent /></Suspense>;
}

function AddressesContent() {
  const searchParams = useSearchParams();
  const auth = useAuth();
  const candidateReturnTo = searchParams.get("returnTo");
  const sanitizedReturnTo = sanitizeReturnTo(candidateReturnTo);
  const returnTo = candidateReturnTo === "/checkout" && sanitizedReturnTo === candidateReturnTo ? "/checkout" : null;
  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="배송지를 관리하려면 로그인해 주세요."><Link className="button button-primary" href={buildAddressLoginHref(returnTo)}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (auth.status !== "authenticated" || auth.memberId === null) return <LoadingState>배송지를 불러오고 있습니다.</LoadingState>;
  return <AddressesForMember key={auth.memberId} returnTo={returnTo} />;
}

function AddressesForMember({ returnTo }: { returnTo: string | null }) {
  const auth = useAuth();
  const router = useRouter();
  const { markAnonymous } = auth;
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [form, setForm] = useState<AddressRequest>(EMPTY_ADDRESS);
  const [editId, setEditId] = useState<number | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const activeRef = useRef(false);
  const requestRef = useRef(0);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const formOpener = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!formOpen) return;
    const dialog = dialogRef.current;
    if (!dialog) return;
    const previousOverflow = document.body.style.overflow;
    dialog.showModal(); document.body.style.overflow = "hidden";
    dialog.querySelector<HTMLElement>("input")?.focus();
    return () => { if (dialog.open) dialog.close(); document.body.style.overflow = previousOverflow; formOpener.current?.focus(); };
  }, [formOpen]);

  function openForm(address?: Address, opener?: HTMLElement) {
    formOpener.current = opener ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null);
    setForm(address ?? EMPTY_ADDRESS); setEditId(address?.addressId ?? null); setFormOpen(true); setError(null);
  }

  function closeForm() { if (busy) return; setFormOpen(false); setEditId(null); setForm(EMPTY_ADDRESS); }

  const load = useCallback(() => {
    const request = ++requestRef.current;
    void commerceFinalApi.addresses().then((result) => {
      if (!activeRef.current || request !== requestRef.current) return;
      setAddresses(result);
      setError(null);
    }).catch((reason: unknown) => {
      if (!activeRef.current || request !== requestRef.current) return;
      if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") {
        markAnonymous();
        return;
      }
      setAddresses(null);
      setError(reason instanceof ApiError ? reason.message : "배송지를 불러오지 못했습니다.");
    });
  }, [markAnonymous]);

  useEffect(() => {
    activeRef.current = true;
    const timer = window.setTimeout(load, 0);
    return () => { activeRef.current = false; requestRef.current += 1; window.clearTimeout(timer); };
  }, [load]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    try {
      await auth.executeWithCsrf(async (csrf) => {
        if (editId === null) await commerceFinalApi.createAddress(form, csrf);
        else await commerceFinalApi.updateAddress(editId, form, csrf);
      });
      if (!activeRef.current) return;
      setForm(EMPTY_ADDRESS);
      setEditId(null);
      setFormOpen(false);
      if (returnTo) router.push(returnTo);
      else load();
    } catch (reason) {
      if (activeRef.current) setError(reason instanceof ApiError ? reason.message : "배송지를 저장하지 못했습니다.");
    } finally {
      if (activeRef.current) setBusy(false);
    }
  }

  if (addresses === null && !error) return <LoadingState>배송지를 불러오고 있습니다.</LoadingState>;
  if (error && addresses === null) return <ErrorState title="배송지를 불러오지 못했습니다." message={error} onRetry={load} />;
  return <section><header className="page-heading"><p className="eyebrow">배송지</p><h1>배송지 관리</h1><p>자주 사용하는 배송지를 저장하고 기본 주소를 선택할 수 있어요. 거래 화면으로 돌아가면 다시 선택하고 확인합니다.</p><div className="button-row"><button className="button button-primary" type="button" onClick={(event) => openForm(undefined, event.currentTarget)}>배송지 추가</button>{returnTo ? <Link className="button button-secondary" href={returnTo}>주문으로 돌아가기</Link> : null}</div></header><section className="saved-addresses"><h2>저장된 배송지</h2>{error ? <p className="field-error" role="alert">{error}</p> : null}{addresses?.length ? <ul className="address-list">{addresses.map((address) => <li className={`address-row${address.isDefault ? " default-address" : ""}`} key={address.addressId}><address><strong>{address.name || address.recipientName}{address.isDefault ? <span className="status-badge">기본 배송지</span> : null}</strong><span>{address.recipientName} · {address.recipientPhone}</span><span>({address.postalCode}) {address.addressLine1} {address.addressLine2}</span></address><div className="button-row"><button className="button button-secondary" type="button" onClick={(event) => openForm(address, event.currentTarget)}>수정</button>{!address.isDefault ? <button className="button button-secondary" type="button" onClick={() => void auth.executeWithCsrf((csrf) => commerceFinalApi.defaultAddress(address.addressId, csrf)).then(load).catch((reason: unknown) => setError(reason instanceof ApiError ? reason.message : "기본 배송지를 변경하지 못했습니다."))}>기본 설정</button> : null}<button className="button button-danger" type="button" onClick={() => { if (!window.confirm(`${address.name || address.recipientName} 배송지를 삭제할까요? 삭제 후 되돌릴 수 없습니다.`)) return; void auth.executeWithCsrf((csrf) => commerceFinalApi.deleteAddress(address.addressId, csrf)).then(load).catch((reason: unknown) => setError(reason instanceof ApiError ? reason.message : "배송지를 삭제하지 못했습니다.")); }}>삭제</button></div></li>)}</ul> : <div className="empty-callout"><strong>저장된 배송지가 없어요.</strong><button type="button" className="button button-primary" onClick={(event) => openForm(undefined, event.currentTarget)}>첫 배송지 추가</button></div>}</section>{formOpen ? <dialog ref={dialogRef} className="address-dialog" aria-labelledby="address-dialog-title" onCancel={(event) => { event.preventDefault(); closeForm(); }}><div className="navigation-overlay-heading"><h2 id="address-dialog-title">{editId === null ? "배송지 추가" : "배송지 수정"}</h2><button className="icon-button" type="button" disabled={busy} onClick={closeForm}>닫기</button></div><form className="form-section" onSubmit={submit}>{([["name", "배송지 이름", true], ["recipientName", "받는 분", true], ["recipientPhone", "연락처", true], ["postalCode", "우편번호", true], ["addressLine1", "주소", true], ["addressLine2", "상세 주소", false]] as const).map(([field, label, required]) => <label className="form-field" key={field}>{label}<input className="input" required={required} autoComplete={field === "recipientName" ? "name" : field === "recipientPhone" ? "tel" : field === "postalCode" ? "postal-code" : field === "addressLine1" ? "address-line1" : field === "addressLine2" ? "address-line2" : undefined} value={form[field]} onChange={(event) => setForm({ ...form, [field]: event.target.value })} /></label>)}<div className="button-row"><button className="button button-secondary" type="button" disabled={busy} onClick={closeForm}>취소</button><button className="button button-primary" type="submit" disabled={busy}>{busy ? "저장 중" : "저장"}</button></div></form></dialog> : null}</section>;
}
