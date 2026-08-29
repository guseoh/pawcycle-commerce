"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi, type Address, type AddressRequest } from "@/lib/commerce-final-api";
import { buildLoginHref } from "@/lib/frontend-utils";

const EMPTY_ADDRESS: AddressRequest = { name: "", recipientName: "", recipientPhone: "", postalCode: "", addressLine1: "", addressLine2: "" };

export default function AddressesPage() {
  const auth = useAuth();
  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="배송지를 관리하려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/addresses")}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (auth.status !== "authenticated" || auth.memberId === null) return <LoadingState>배송지를 불러오고 있습니다.</LoadingState>;
  return <AddressesForMember key={auth.memberId} />;
}

function AddressesForMember() {
  const auth = useAuth();
  const { markAnonymous } = auth;
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [form, setForm] = useState<AddressRequest>(EMPTY_ADDRESS);
  const [editId, setEditId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const activeRef = useRef(false);
  const requestRef = useRef(0);

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
      setError("배송지를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
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
      load();
    } catch {
      if (activeRef.current) setError("배송지를 저장하지 못했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요.");
    } finally {
      if (activeRef.current) setBusy(false);
    }
  }

  if (addresses === null && !error) return <LoadingState>배송지를 불러오고 있습니다.</LoadingState>;
  if (error && addresses === null) return <ErrorState title="배송지를 불러오지 못했습니다." message={error} onRetry={load} />;
  return <section><header className="page-heading"><p className="eyebrow">배송지</p><h1>배송지를 관리하세요.</h1><p>자주 사용하는 배송지를 저장하고 기본 주소를 선택할 수 있어요.</p></header><div className="address-layout"><section className="section-card saved-addresses"><h2>저장된 배송지</h2>{error ? <p className="field-error" role="alert">{error}</p> : null}<ul className="history-list">{addresses?.map((address) => <li className={address.isDefault ? "default-address" : ""} key={address.addressId}><strong>{address.name || address.recipientName}{address.isDefault ? <span className="status-badge">기본 배송지</span> : null}</strong><span>{address.recipientName} · {address.addressLine1}</span><div className="button-row"><button className="button button-secondary" type="button" onClick={() => { setForm(address); setEditId(address.addressId); }}>수정</button>{!address.isDefault ? <button className="button button-secondary" type="button" onClick={() => void auth.executeWithCsrf((csrf) => commerceFinalApi.defaultAddress(address.addressId, csrf)).then(load).catch((reason: unknown) => setError(reason instanceof ApiError ? reason.message : "기본 배송지를 변경하지 못했습니다."))}>기본 설정</button> : null}<button className="button button-danger" type="button" onClick={() => void auth.executeWithCsrf((csrf) => commerceFinalApi.deleteAddress(address.addressId, csrf)).then(load).catch((reason: unknown) => setError(reason instanceof ApiError ? reason.message : "배송지를 삭제하지 못했습니다."))}>삭제</button></div></li>)}</ul></section><section className="section-card address-form-card"><h2>{editId === null ? "배송지 추가" : "배송지 수정"}</h2><form className="form-section" onSubmit={submit}>{([["name", "배송지 이름", true], ["recipientName", "받는 분", true], ["recipientPhone", "연락처", true], ["postalCode", "우편번호", true], ["addressLine1", "주소", true], ["addressLine2", "상세 주소", false]] as const).map(([field, label, required]) => <label className="form-field" key={field}>{label}<input className="input" required={required} value={form[field]} onChange={(event) => setForm({ ...form, [field]: event.target.value })} /></label>)}<div className="button-row"><button className="button button-primary" type="submit" disabled={busy}>{busy ? "저장 중" : "저장"}</button>{editId !== null ? <button className="button button-secondary" type="button" onClick={() => { setEditId(null); setForm(EMPTY_ADDRESS); }}>취소</button> : null}</div></form></section></div></section>;
}
