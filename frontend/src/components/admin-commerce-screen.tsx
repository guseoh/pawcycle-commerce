"use client";

import { FormEvent, useEffect, useState } from "react";
import { formatDateTime, formatPrice } from "@/lib/frontend-utils";
import { adminCommerceApi, toAdminCouponInput, toAdminCouponRequest, type AdminAuditLog, type AdminCoupon, type AdminCouponRequest, type AdminInventory, type AdminMembershipGrade, type AdminOrder, type AdminCouponInput, type AdminMembershipGradeInput } from "@/lib/admin-commerce-api";
import { errorMessage, MutationFeedback, ResourceState, useAdminMutation, useAdminResource } from "@/components/admin-catalog/shared";
import { AdminGate } from "@/components/admin-catalog/shared";

type CommerceTab = "inventory" | "promotions" | "orders" | "audit";
const EMPTY_COUPON: AdminCouponInput = { name: "", discountType: "FIXED_AMOUNT", discountValue: "", minimumOrderAmount: "0", maximumDiscountAmount: "", validFrom: "", validUntil: "", active: true };
const EMPTY_GRADE: AdminMembershipGradeInput = { code: "", name: "", minimumPurchaseAmount: "0", displayOrder: "0", active: true, benefitCouponId: "" };

export function AdminCommerceScreen() {
  return <AdminGate><AdminCommerceContent /></AdminGate>;
}

function AdminCommerceContent() {
  const [tab, setTab] = useState<CommerceTab>("inventory");
  const inventory = useAdminResource(adminCommerceApi.inventories);
  const coupons = useAdminResource(adminCommerceApi.coupons);
  const grades = useAdminResource(adminCommerceApi.membershipGrades);
  const orders = useAdminResource(adminCommerceApi.orders);
  const audit = useAdminResource(adminCommerceApi.auditLogs);
  const mutation = useAdminMutation();

  const refresh = async () => {
    if (tab === "inventory") return inventory.reload();
    if (tab === "promotions") { await Promise.all([coupons.reload(), grades.reload()]); return; }
    if (tab === "orders") return orders.reload();
    return audit.reload();
  };

  return <section className="admin-commerce" aria-labelledby="admin-commerce-title">
    <header className="admin-heading"><p className="eyebrow">ADMIN COMMERCE</p><h1 id="admin-commerce-title">Commerce 관리</h1><p>재고, 혜택, 주문과 감사 기록을 확인하고 기존 Commerce 계약의 운영 작업을 실행합니다.</p></header>
    <nav className="admin-sections" aria-label="Commerce 관리 영역">{([["inventory", "재고"], ["promotions", "쿠폰·멤버십"], ["orders", "주문"], ["audit", "감사 기록"]] as const).map(([key, label]) => <button key={key} type="button" aria-pressed={tab === key} onClick={() => setTab(key)}>{label}</button>)}</nav>
    {mutation.error ? <div className="admin-feedback" role="alert"><p>{errorMessage(mutation.error)}</p></div> : null}
    <MutationFeedback mutation={mutation} retry={() => void refresh().then(mutation.reset).catch(() => undefined)} />
    {tab === "inventory" ? <InventoryPanel resource={inventory} mutation={mutation} onAdjust={(skuId, delta) => void mutation.run((csrf) => adminCommerceApi.adjustInventory(skuId, delta, csrf), inventory.reload)} /> : null}
    {tab === "promotions" ? <PromotionsPanel coupons={coupons} grades={grades} mutation={mutation} onRefresh={() => Promise.all([coupons.reload(), grades.reload()]).then(() => undefined)} /> : null}
    {tab === "orders" ? <OrdersPanel resource={orders} /> : null}
    {tab === "audit" ? <AuditPanel resource={audit} /> : null}
  </section>;
}

function InventoryPanel({ resource, mutation, onAdjust }: { resource: ReturnType<typeof useAdminResource<AdminInventory[]>>; mutation: ReturnType<typeof useAdminMutation>; onAdjust: (skuId: number, delta: number) => void }) {
  return <section className="admin-resource" aria-labelledby="inventory-title"><div className="admin-section-heading"><h2 id="inventory-title">재고</h2><span>{resource.data ? `${resource.data.length}개 SKU` : "—"}</span></div><ResourceState loading={resource.loading} error={resource.error} onRetry={() => void resource.reload().catch(() => undefined)} />{resource.data && !resource.error ? resource.data.length ? <ul className="admin-commerce-list">{resource.data.map((item) => <InventoryRow key={item.skuId} item={item} pending={mutation.pending} onAdjust={onAdjust} />)}</ul> : <div className="empty-callout">재고 정보가 없습니다.</div> : null}</section>;
}

function InventoryRow({ item, pending, onAdjust }: { item: AdminInventory; pending: boolean; onAdjust: (skuId: number, delta: number) => void }) {
  const [delta, setDelta] = useState("");
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const parsed = Number(delta); if (!Number.isInteger(parsed) || parsed === 0) return; onAdjust(item.skuId, parsed); setDelta(""); };
  return <li className="admin-commerce-row"><div><strong>SKU {item.skuCode}</strong><span>SKU ID #{item.skuId} · 예약 {item.reservedQuantity}개 · 버전 {item.version}</span><p>판매 가능 재고 <strong>{item.availableQuantity}개</strong></p></div><form className="admin-inline-form" onSubmit={submit}><label className="form-field" htmlFor={`inventory-delta-${item.skuId}`}>조정 수량<input id={`inventory-delta-${item.skuId}`} className="input" type="number" step="1" value={delta} onChange={(event) => setDelta(event.target.value)} disabled={pending} placeholder="±수량" /></label><button className="button button-secondary" type="submit" disabled={pending || !delta || Number(delta) === 0}>재고 조정</button></form></li>;
}

function PromotionsPanel({ coupons, grades, mutation, onRefresh }: { coupons: ReturnType<typeof useAdminResource<AdminCoupon[]>>; grades: ReturnType<typeof useAdminResource<AdminMembershipGrade[]>>; mutation: ReturnType<typeof useAdminMutation>; onRefresh: () => Promise<void> }) {
  return <div className="admin-commerce-columns"><section className="admin-resource"><div className="admin-section-heading"><h2>쿠폰</h2><span>{coupons.data ? `${coupons.data.length}개` : "—"}</span></div><ResourceState loading={coupons.loading} error={coupons.error} onRetry={() => void coupons.reload().catch(() => undefined)} />{coupons.data && !coupons.error ? <><CouponCreateForm pending={mutation.pending} onCreate={(input) => void mutation.run((csrf) => adminCommerceApi.createCoupon(input, csrf), onRefresh)} />{coupons.data.length ? <ul className="admin-commerce-list">{coupons.data.map((coupon) => <CouponRow key={coupon.couponId} coupon={coupon} pending={mutation.pending} onIssue={(memberId) => void mutation.run((csrf) => adminCommerceApi.issueCoupon(coupon.couponId, memberId, csrf), onRefresh)} onUpdate={(input) => void mutation.run((csrf) => adminCommerceApi.updateCoupon(coupon.couponId, input, csrf), onRefresh)} />)}</ul> : <div className="empty-callout">등록된 쿠폰이 없습니다.</div>}</> : null}</section><section className="admin-resource"><div className="admin-section-heading"><h2>멤버십 등급</h2><span>{grades.data ? `${grades.data.length}개` : "—"}</span></div><ResourceState loading={grades.loading} error={grades.error} onRetry={() => void grades.reload().catch(() => undefined)} />{grades.data && !grades.error ? <><GradeCreateForm pending={mutation.pending} onCreate={(input) => void mutation.run((csrf) => adminCommerceApi.createMembershipGrade(input, csrf), onRefresh)} />{grades.data.length ? <ul className="admin-commerce-list">{grades.data.map((grade) => <li className="admin-commerce-row" key={grade.gradeId}><div><strong>{grade.name} · {grade.code}</strong><span>기준 {formatPrice(grade.minimumPurchaseAmount)} · 순서 {grade.displayOrder} · {grade.active ? "활성" : "비활성"}</span><span>혜택 쿠폰 {grade.benefitCouponId ?? "없음"}</span></div><EvaluateMembership pending={mutation.pending} onEvaluate={(memberId) => void mutation.run((csrf) => adminCommerceApi.evaluateMembership(memberId, csrf), onRefresh)} /></li>)}</ul> : <div className="empty-callout">등록된 멤버십 등급이 없습니다.</div>}</> : null}</section></div>;
}

function CouponFields({ form, pending, onChange }: { form: AdminCouponInput; pending: boolean; onChange: (key: keyof AdminCouponInput, value: string | boolean) => void }) {
  return <div className="admin-fields"><label className="form-field">이름<input className="input" required value={form.name} onChange={(event) => onChange("name", event.target.value)} disabled={pending} /></label><label className="form-field">할인 방식<select className="input" value={form.discountType} onChange={(event) => onChange("discountType", event.target.value as AdminCouponInput["discountType"])} disabled={pending}><option value="FIXED_AMOUNT">정액</option><option value="PERCENTAGE">정률</option></select></label><label className="form-field">할인 값<input className="input" required type="number" min="0" step="0.01" value={form.discountValue} onChange={(event) => onChange("discountValue", event.target.value)} disabled={pending} /></label><label className="form-field">최소 주문 금액<input className="input" type="number" min="0" step="0.01" value={form.minimumOrderAmount} onChange={(event) => onChange("minimumOrderAmount", event.target.value)} disabled={pending} /></label><label className="form-field">최대 할인 금액<input className="input" type="number" min="0" step="0.01" value={form.maximumDiscountAmount} onChange={(event) => onChange("maximumDiscountAmount", event.target.value)} disabled={pending} placeholder="선택" /></label><label className="form-field">시작 일시<input className="input" required type="datetime-local" value={form.validFrom} onChange={(event) => onChange("validFrom", event.target.value)} disabled={pending} /></label><label className="form-field">종료 일시<input className="input" required type="datetime-local" value={form.validUntil} onChange={(event) => onChange("validUntil", event.target.value)} disabled={pending} /></label><label className="form-field"><input className="admin-checkbox" type="checkbox" checked={form.active} onChange={(event) => onChange("active", event.target.checked)} disabled={pending} /> 활성</label></div>;
}

function CouponCreateForm({ pending, onCreate }: { pending: boolean; onCreate: (input: AdminCouponRequest) => void }) {
  const [form, setForm] = useState(EMPTY_COUPON);
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); if (!form.name.trim() || !form.discountValue || !form.validFrom || !form.validUntil) return; onCreate(toAdminCouponRequest(form)); setForm(EMPTY_COUPON); };
  const change = (key: keyof AdminCouponInput, value: string | boolean) => setForm((current) => ({ ...current, [key]: value }));
  return <form className="admin-form" onSubmit={submit}><h3>쿠폰 생성</h3><CouponFields form={form} pending={pending} onChange={change} /><button className="button button-primary" type="submit" disabled={pending}>쿠폰 생성</button></form>;
}

function CouponEditForm({ coupon, pending, onUpdate, onCancel }: { coupon: AdminCoupon; pending: boolean; onUpdate: (input: AdminCouponRequest) => void; onCancel: () => void }) {
  const [form, setForm] = useState(() => toAdminCouponInput(coupon));
  useEffect(() => { setForm(toAdminCouponInput(coupon)); }, [coupon]);
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); if (!form.name.trim() || !form.discountValue || !form.validFrom || !form.validUntil) return; onUpdate(toAdminCouponRequest(form)); };
  const change = (key: keyof AdminCouponInput, value: string | boolean) => setForm((current) => ({ ...current, [key]: value }));
  return <form className="admin-form" onSubmit={submit}><h4>쿠폰 수정</h4><CouponFields form={form} pending={pending} onChange={change} /><div className="button-row"><button className="button button-primary" type="submit" disabled={pending}>변경 저장</button><button className="button button-secondary" type="button" disabled={pending} onClick={onCancel}>수정 취소</button></div></form>;
}

function CouponRow({ coupon, pending, onIssue, onUpdate }: { coupon: AdminCoupon; pending: boolean; onIssue: (memberId: number) => void; onUpdate: (input: AdminCouponRequest) => void }) {
  const [memberId, setMemberId] = useState("");
  const [editing, setEditing] = useState(false);
  const issue = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const parsed = Number(memberId); if (!Number.isInteger(parsed) || parsed <= 0) return; onIssue(parsed); setMemberId(""); };
  return <li className="admin-commerce-row"><div><strong>{coupon.name}</strong><span>#{coupon.couponId} · {coupon.discountType === "PERCENTAGE" ? `${coupon.discountValue}%` : formatPrice(coupon.discountValue)} · {coupon.active ? "활성" : "비활성"}</span><span>{coupon.validFrom} ~ {coupon.validUntil}</span>{editing ? <CouponEditForm coupon={coupon} pending={pending} onUpdate={onUpdate} onCancel={() => setEditing(false)} /> : null}</div><aside className="admin-inline-form"><form className="admin-inline-form" onSubmit={issue}><label className="form-field" htmlFor={`coupon-member-${coupon.couponId}`}>회원 ID<input id={`coupon-member-${coupon.couponId}`} className="input" type="number" min="1" value={memberId} onChange={(event) => setMemberId(event.target.value)} disabled={pending} placeholder="회원 ID" /></label><button className="button button-secondary" type="submit" disabled={pending || !memberId}>쿠폰 발급</button></form><button className="button button-secondary" type="button" disabled={pending} aria-expanded={editing} onClick={() => setEditing((value) => !value)}>{editing ? "수정 닫기" : "쿠폰 수정"}</button></aside></li>;
}

function GradeCreateForm({ pending, onCreate }: { pending: boolean; onCreate: (input: Record<string, unknown>) => void }) {
  const [form, setForm] = useState(EMPTY_GRADE);
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); if (!form.code.trim() || !form.name.trim()) return; onCreate({ code: form.code.trim(), name: form.name.trim(), minimumPurchaseAmount: Number(form.minimumPurchaseAmount || 0), displayOrder: Number(form.displayOrder || 0), active: form.active, benefitCouponId: form.benefitCouponId ? Number(form.benefitCouponId) : null }); setForm(EMPTY_GRADE); };
  const change = (key: keyof AdminMembershipGradeInput, value: string | boolean) => setForm((current) => ({ ...current, [key]: value }));
  return <form className="admin-form" onSubmit={submit}><h3>등급 생성</h3><div className="admin-fields"><label className="form-field">코드<input className="input" required maxLength={30} value={form.code} onChange={(event) => change("code", event.target.value)} disabled={pending} /></label><label className="form-field">이름<input className="input" required maxLength={100} value={form.name} onChange={(event) => change("name", event.target.value)} disabled={pending} /></label><label className="form-field">최소 구매 금액<input className="input" type="number" min="0" step="0.01" value={form.minimumPurchaseAmount} onChange={(event) => change("minimumPurchaseAmount", event.target.value)} disabled={pending} /></label><label className="form-field">표시 순서<input className="input" type="number" min="0" step="1" value={form.displayOrder} onChange={(event) => change("displayOrder", event.target.value)} disabled={pending} /></label><label className="form-field">혜택 쿠폰 ID<input className="input" type="number" min="1" value={form.benefitCouponId} onChange={(event) => change("benefitCouponId", event.target.value)} disabled={pending} placeholder="선택" /></label><label className="form-field"><input className="admin-checkbox" type="checkbox" checked={form.active} onChange={(event) => change("active", event.target.checked)} disabled={pending} /> 활성</label></div><button className="button button-primary" type="submit" disabled={pending}>등급 생성</button></form>;
}

function EvaluateMembership({ pending, onEvaluate }: { pending: boolean; onEvaluate: (memberId: number) => void }) {
  const [memberId, setMemberId] = useState("");
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const parsed = Number(memberId); if (!Number.isInteger(parsed) || parsed <= 0) return; onEvaluate(parsed); setMemberId(""); };
  return <form className="admin-inline-form" onSubmit={submit}><label className="form-field" htmlFor="membership-member-id">회원 ID<input id="membership-member-id" className="input" type="number" min="1" value={memberId} onChange={(event) => setMemberId(event.target.value)} disabled={pending} placeholder="회원 ID" /></label><button className="button button-secondary" type="submit" disabled={pending || !memberId}>등급 재평가</button></form>;
}

function OrdersPanel({ resource }: { resource: ReturnType<typeof useAdminResource<AdminOrder[]>> }) {
  return <section className="admin-resource"><div className="admin-section-heading"><h2>주문</h2><span>{resource.data ? `${resource.data.length}건` : "—"}</span></div><ResourceState loading={resource.loading} error={resource.error} onRetry={() => void resource.reload().catch(() => undefined)} />{resource.data && !resource.error ? resource.data.length ? <ul className="admin-commerce-list">{resource.data.map((order) => <AdminOrderRow key={order.orderId} order={order} />)}</ul> : <div className="empty-callout">주문이 없습니다.</div> : null}</section>;
}

function AdminOrderRow({ order }: { order: AdminOrder }) {
  const [open, setOpen] = useState(false); const [detail, setDetail] = useState<Record<string, unknown> | null>(null); const [error, setError] = useState<string | null>(null);
  useEffect(() => { if (!open || detail) return; let active = true; void adminCommerceApi.order(order.orderId).then((value) => { if (active) setDetail(value); }).catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : "주문 상세를 불러오지 못했습니다."); }); return () => { active = false; }; }, [detail, open, order.orderId]);
  return <li className="admin-commerce-row"><div><strong>주문 {order.orderNumber}</strong><span>#{order.orderId} · 회원 #{order.memberId} · {order.status}</span><span>{formatPrice(order.paymentAmount)} · {formatDateTime(order.createdAt)}</span>{open ? error ? <p className="field-error" role="alert">{error}</p> : detail ? <pre className="admin-json-preview">{JSON.stringify(detail, null, 2)}</pre> : <p role="status">상세를 불러오는 중입니다.</p> : null}</div><button className="button button-secondary" type="button" aria-expanded={open} onClick={() => setOpen((value) => !value)}>{open ? "상세 닫기" : "상세 보기"}</button></li>;
}

function AuditPanel({ resource }: { resource: ReturnType<typeof useAdminResource<AdminAuditLog[]>> }) {
  return <section className="admin-resource"><div className="admin-section-heading"><h2>감사 기록</h2><span>{resource.data ? `${resource.data.length}건` : "—"}</span></div><ResourceState loading={resource.loading} error={resource.error} onRetry={() => void resource.reload().catch(() => undefined)} />{resource.data && !resource.error ? resource.data.length ? <ul className="admin-commerce-list">{resource.data.map((item) => <li className="admin-commerce-row" key={item.auditLogId}><div><strong>{item.action}</strong><span>#{item.auditLogId} · 관리자 #{item.adminId}</span><span>{item.targetType} #{item.targetId} · {formatDateTime(item.createdAt)}</span></div></li>)}</ul> : <div className="empty-callout">감사 기록이 없습니다.</div> : null}</section>;
}
