"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
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
    <header className="admin-heading"><p className="eyebrow">ADMIN COMMERCE</p><h1 id="admin-commerce-title">Commerce 관리</h1><p>운영 상태를 먼저 확인하고 필요한 작업만 실행합니다.</p></header>
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
  const [filter, setFilter] = useState("");
  const rows = useMemo(() => resource.data?.filter((item) => `${item.skuCode} ${item.skuId}`.toLowerCase().includes(filter.trim().toLowerCase())) ?? [], [filter, resource.data]);
  return <section className="admin-resource" aria-labelledby="inventory-title"><div className="admin-section-heading"><div><h2 id="inventory-title">재고</h2><span>{resource.data ? `${resource.data.length}개 SKU` : "—"}</span></div></div><ResourceState loading={resource.loading} error={resource.error} onRetry={() => void resource.reload().catch(() => undefined)} />{resource.data && !resource.error ? <><label className="form-field admin-resource-search">SKU 찾기<input className="input" type="search" value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="SKU 코드 또는 ID" /></label>{rows.length ? <ul className="admin-commerce-list">{rows.map((item) => <InventoryRow key={item.skuId} item={item} pending={mutation.pending} onAdjust={onAdjust} />)}</ul> : <div className="empty-callout">조건에 맞는 재고 정보가 없습니다.</div>}</> : null}</section>;
}

function InventoryRow({ item, pending, onAdjust }: { item: AdminInventory; pending: boolean; onAdjust: (skuId: number, delta: number) => void }) {
  const [delta, setDelta] = useState("");
  const [editing, setEditing] = useState(false);
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const parsed = Number(delta); if (!Number.isInteger(parsed) || parsed === 0) return; onAdjust(item.skuId, parsed); setDelta(""); setEditing(false); };
  return <li className="admin-commerce-row"><div><strong>SKU {item.skuCode}</strong><span>#{item.skuId} · 예약 {item.reservedQuantity}개 · 버전 {item.version}</span><p>판매 가능 <strong>{item.availableQuantity}개</strong></p></div><div className="admin-row-actions"><button className="button button-secondary" type="button" disabled={pending} aria-expanded={editing} onClick={() => setEditing((value) => !value)}>{editing ? "조정 닫기" : "재고 조정"}</button></div>{editing ? <div className="admin-inline-editor"><form className="admin-inline-form" onSubmit={submit}><label className="form-field" htmlFor={`inventory-delta-${item.skuId}`}>증감 수량<input id={`inventory-delta-${item.skuId}`} className="input" type="number" step="1" value={delta} onChange={(event) => setDelta(event.target.value)} disabled={pending} placeholder="예: 10 또는 -3" /></label><button className="button button-primary" type="submit" disabled={pending || !delta || Number(delta) === 0}>조정 확인</button></form></div> : null}</li>;
}

function PromotionsPanel({ coupons, grades, mutation, onRefresh }: { coupons: ReturnType<typeof useAdminResource<AdminCoupon[]>>; grades: ReturnType<typeof useAdminResource<AdminMembershipGrade[]>>; mutation: ReturnType<typeof useAdminMutation>; onRefresh: () => Promise<void> }) {
  const [couponCreateOpen, setCouponCreateOpen] = useState(false);
  const [gradeCreateOpen, setGradeCreateOpen] = useState(false);
  const [evaluateOpen, setEvaluateOpen] = useState(false);
  return <div className="admin-commerce-columns"><section className="admin-resource"><div className="admin-section-heading"><div><h2>쿠폰</h2><span>{coupons.data ? `${coupons.data.length}개` : "—"}</span></div><button className="button button-secondary admin-create-toggle" type="button" disabled={mutation.pending} aria-expanded={couponCreateOpen} onClick={() => setCouponCreateOpen((open) => !open)}>{couponCreateOpen ? "생성 닫기" : "+ 쿠폰 생성"}</button></div><ResourceState loading={coupons.loading} error={coupons.error} onRetry={() => void coupons.reload().catch(() => undefined)} />{coupons.data && !coupons.error ? <>{couponCreateOpen ? <div className="admin-create-panel"><CouponCreateForm pending={mutation.pending} onCreate={(input) => { void mutation.run((csrf) => adminCommerceApi.createCoupon(input, csrf), async () => { await onRefresh(); setCouponCreateOpen(false); }); }} /></div> : null}{coupons.data.length ? <ul className="admin-commerce-list">{coupons.data.map((coupon) => <CouponRow key={coupon.couponId} coupon={coupon} pending={mutation.pending} onIssue={(memberId) => void mutation.run((csrf) => adminCommerceApi.issueCoupon(coupon.couponId, memberId, csrf), onRefresh)} onUpdate={(input) => void mutation.run((csrf) => adminCommerceApi.updateCoupon(coupon.couponId, input, csrf), onRefresh)} />)}</ul> : <div className="empty-callout">등록된 쿠폰이 없습니다.</div>}</> : null}</section>
    <section className="admin-resource"><div className="admin-section-heading"><div><h2>멤버십 등급</h2><span>{grades.data ? `${grades.data.length}개` : "—"}</span></div><div className="button-row"><button className="button button-secondary" type="button" disabled={mutation.pending} aria-expanded={evaluateOpen} onClick={() => setEvaluateOpen((open) => !open)}>회원 등급 재평가</button><button className="button button-secondary admin-create-toggle" type="button" disabled={mutation.pending} aria-expanded={gradeCreateOpen} onClick={() => setGradeCreateOpen((open) => !open)}>{gradeCreateOpen ? "생성 닫기" : "+ 등급 생성"}</button></div></div><ResourceState loading={grades.loading} error={grades.error} onRetry={() => void grades.reload().catch(() => undefined)} />{grades.data && !grades.error ? <>{evaluateOpen ? <div className="admin-create-panel"><h3>회원 등급 재평가</h3><p>회원 ID를 입력하면 현재 구매 실적과 등급 기준으로 서버가 등급을 다시 판단합니다.</p><EvaluateMembership pending={mutation.pending} onEvaluate={(memberId) => { void mutation.run((csrf) => adminCommerceApi.evaluateMembership(memberId, csrf), async () => { await onRefresh(); setEvaluateOpen(false); }); }} /></div> : null}{gradeCreateOpen ? <div className="admin-create-panel"><GradeCreateForm pending={mutation.pending} onCreate={(input) => { void mutation.run((csrf) => adminCommerceApi.createMembershipGrade(input, csrf), async () => { await onRefresh(); setGradeCreateOpen(false); }); }} /></div> : null}{grades.data.length ? <ul className="admin-commerce-list">{grades.data.map((grade) => <MembershipGradeRow key={grade.gradeId} grade={grade} />)}</ul> : <div className="empty-callout">등록된 멤버십 등급이 없습니다.</div>}</> : null}</section></div>;
}

function CouponFields({ form, pending, onChange }: { form: AdminCouponInput; pending: boolean; onChange: (key: keyof AdminCouponInput, value: string | boolean) => void }) {
  return <div className="admin-fields"><label className="form-field">이름<input className="input" required value={form.name} onChange={(event) => onChange("name", event.target.value)} disabled={pending} /></label><label className="form-field">할인 방식<select className="input" value={form.discountType} onChange={(event) => onChange("discountType", event.target.value as AdminCouponInput["discountType"])} disabled={pending}><option value="FIXED_AMOUNT">정액</option><option value="PERCENTAGE">정률</option></select></label><label className="form-field">할인 값<input className="input" required type="number" min="0" step="0.01" value={form.discountValue} onChange={(event) => onChange("discountValue", event.target.value)} disabled={pending} /></label><label className="form-field">최소 주문 금액<input className="input" type="number" min="0" step="0.01" value={form.minimumOrderAmount} onChange={(event) => onChange("minimumOrderAmount", event.target.value)} disabled={pending} /></label><label className="form-field">최대 할인 금액<input className="input" type="number" min="0" step="0.01" value={form.maximumDiscountAmount} onChange={(event) => onChange("maximumDiscountAmount", event.target.value)} disabled={pending} placeholder="선택" /></label><label className="form-field">시작 일시<input className="input" required type="datetime-local" value={form.validFrom} onChange={(event) => onChange("validFrom", event.target.value)} disabled={pending} /></label><label className="form-field">종료 일시<input className="input" required type="datetime-local" value={form.validUntil} onChange={(event) => onChange("validUntil", event.target.value)} disabled={pending} /></label><label className="form-field"><input className="admin-checkbox" type="checkbox" checked={form.active} onChange={(event) => onChange("active", event.target.checked)} disabled={pending} /> 활성</label></div>;
}

function CouponCreateForm({ pending, onCreate }: { pending: boolean; onCreate: (input: AdminCouponRequest) => void }) {
  const [form, setForm] = useState(EMPTY_COUPON);
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); if (!form.name.trim() || !form.discountValue || !form.validFrom || !form.validUntil) return; onCreate(toAdminCouponRequest(form)); };
  const change = (key: keyof AdminCouponInput, value: string | boolean) => setForm((current) => ({ ...current, [key]: value }));
  return <form className="admin-form" onSubmit={submit}><h3>쿠폰 생성</h3><CouponFields form={form} pending={pending} onChange={change} /><button className="button button-primary" type="submit" disabled={pending}>쿠폰 생성</button></form>;
}

function CouponEditForm({ coupon, pending, onUpdate, onCancel }: { coupon: AdminCoupon; pending: boolean; onUpdate: (input: AdminCouponRequest) => void; onCancel: () => void }) {
  const [form, setForm] = useState(() => toAdminCouponInput(coupon));
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); if (!form.name.trim() || !form.discountValue || !form.validFrom || !form.validUntil) return; onUpdate(toAdminCouponRequest(form)); };
  const change = (key: keyof AdminCouponInput, value: string | boolean) => setForm((current) => ({ ...current, [key]: value }));
  return <form className="admin-form" onSubmit={submit}><h4>쿠폰 수정</h4><CouponFields form={form} pending={pending} onChange={change} /><div className="button-row"><button className="button button-primary" type="submit" disabled={pending}>변경 저장</button><button className="button button-secondary" type="button" disabled={pending} onClick={onCancel}>수정 취소</button></div></form>;
}

function CouponRow({ coupon, pending, onIssue, onUpdate }: { coupon: AdminCoupon; pending: boolean; onIssue: (memberId: number) => void; onUpdate: (input: AdminCouponRequest) => void }) {
  const [memberId, setMemberId] = useState("");
  const [action, setAction] = useState<"issue" | "edit" | null>(null);
  const issue = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const parsed = Number(memberId); if (!Number.isInteger(parsed) || parsed <= 0) return; onIssue(parsed); setMemberId(""); setAction(null); };
  return <li className="admin-commerce-row"><div><strong>{coupon.name}</strong><span>#{coupon.couponId} · {coupon.discountType === "PERCENTAGE" ? `${coupon.discountValue}%` : formatPrice(coupon.discountValue)} · {coupon.active ? "활성" : "비활성"}</span><span>{formatDateTime(coupon.validFrom)} ~ {formatDateTime(coupon.validUntil)}</span></div><div className="admin-row-actions"><button className="button button-secondary" type="button" disabled={pending} aria-expanded={action === "issue"} onClick={() => setAction((current) => current === "issue" ? null : "issue")}>발급</button><button className="button button-secondary" type="button" disabled={pending} aria-expanded={action === "edit"} onClick={() => setAction((current) => current === "edit" ? null : "edit")}>수정</button></div>{action === "issue" ? <div className="admin-inline-editor"><form className="admin-inline-form" onSubmit={issue}><label className="form-field" htmlFor={`coupon-member-${coupon.couponId}`}>발급할 회원 ID<input id={`coupon-member-${coupon.couponId}`} className="input" type="number" min="1" value={memberId} onChange={(event) => setMemberId(event.target.value)} disabled={pending} placeholder="회원 ID" /></label><button className="button button-primary" type="submit" disabled={pending || !memberId}>쿠폰 발급 확인</button></form></div> : null}{action === "edit" ? <div className="admin-inline-editor"><CouponEditForm coupon={coupon} pending={pending} onUpdate={onUpdate} onCancel={() => setAction(null)} /></div> : null}</li>;
}

function GradeCreateForm({ pending, onCreate }: { pending: boolean; onCreate: (input: Record<string, unknown>) => void }) {
  const [form, setForm] = useState(EMPTY_GRADE);
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); if (!form.code.trim() || !form.name.trim()) return; onCreate({ code: form.code.trim(), name: form.name.trim(), minimumPurchaseAmount: Number(form.minimumPurchaseAmount || 0), displayOrder: Number(form.displayOrder || 0), active: form.active, benefitCouponId: form.benefitCouponId ? Number(form.benefitCouponId) : null }); };
  const change = (key: keyof AdminMembershipGradeInput, value: string | boolean) => setForm((current) => ({ ...current, [key]: value }));
  return <form className="admin-form" onSubmit={submit}><h3>등급 생성</h3><div className="admin-fields"><label className="form-field">코드<input className="input" required maxLength={30} value={form.code} onChange={(event) => change("code", event.target.value)} disabled={pending} /></label><label className="form-field">이름<input className="input" required maxLength={100} value={form.name} onChange={(event) => change("name", event.target.value)} disabled={pending} /></label><label className="form-field">최소 구매 금액<input className="input" type="number" min="0" step="0.01" value={form.minimumPurchaseAmount} onChange={(event) => change("minimumPurchaseAmount", event.target.value)} disabled={pending} /></label><label className="form-field">표시 순서<input className="input" type="number" min="0" step="1" value={form.displayOrder} onChange={(event) => change("displayOrder", event.target.value)} disabled={pending} /></label><label className="form-field">혜택 쿠폰 ID<input className="input" type="number" min="1" value={form.benefitCouponId} onChange={(event) => change("benefitCouponId", event.target.value)} disabled={pending} placeholder="선택" /></label><label className="form-field"><input className="admin-checkbox" type="checkbox" checked={form.active} onChange={(event) => change("active", event.target.checked)} disabled={pending} /> 활성</label></div><button className="button button-primary" type="submit" disabled={pending}>등급 생성</button></form>;
}

function MembershipGradeRow({ grade }: { grade: AdminMembershipGrade }) {
  return <li className="admin-commerce-row"><div><strong>{grade.name} · {grade.code}</strong><span>기준 {formatPrice(grade.minimumPurchaseAmount)} · 표시 순서 {grade.displayOrder}</span><span>{grade.active ? "활성" : "비활성"} · 혜택 쿠폰 {grade.benefitCouponId ?? "없음"}</span></div></li>;
}

function EvaluateMembership({ pending, onEvaluate }: { pending: boolean; onEvaluate: (memberId: number) => void }) {
  const [memberId, setMemberId] = useState("");
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const parsed = Number(memberId); if (!Number.isInteger(parsed) || parsed <= 0) return; onEvaluate(parsed); setMemberId(""); };
  return <form className="admin-inline-form" onSubmit={submit}><label className="form-field" htmlFor="membership-member-id">회원 ID<input id="membership-member-id" className="input" type="number" min="1" value={memberId} onChange={(event) => setMemberId(event.target.value)} disabled={pending} placeholder="회원 ID" /></label><button className="button button-primary" type="submit" disabled={pending || !memberId}>재평가 확인</button></form>;
}

function OrdersPanel({ resource }: { resource: ReturnType<typeof useAdminResource<AdminOrder[]>> }) {
  const [filter, setFilter] = useState("");
  const rows = useMemo(() => resource.data?.filter((order) => `${order.orderNumber} ${order.orderId} ${order.memberId} ${order.status}`.toLowerCase().includes(filter.trim().toLowerCase())) ?? [], [filter, resource.data]);
  return <section className="admin-resource"><div className="admin-section-heading"><div><h2>주문</h2><span>{resource.data ? `${resource.data.length}건` : "—"}</span></div></div><ResourceState loading={resource.loading} error={resource.error} onRetry={() => void resource.reload().catch(() => undefined)} />{resource.data && !resource.error ? <><label className="form-field admin-resource-search">주문 찾기<input className="input" type="search" value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="주문번호, 회원 ID, 상태" /></label>{rows.length ? <ul className="admin-commerce-list">{rows.map((order) => <AdminOrderRow key={order.orderId} order={order} />)}</ul> : <div className="empty-callout">조건에 맞는 주문이 없습니다.</div>}</> : null}</section>;
}

function AdminOrderRow({ order }: { order: AdminOrder }) {
  const [open, setOpen] = useState(false); const [detail, setDetail] = useState<Record<string, unknown> | null>(null); const [error, setError] = useState<string | null>(null);
  useEffect(() => { if (!open || detail) return; let active = true; void adminCommerceApi.order(order.orderId).then((value) => { if (active) setDetail(value); }).catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : "주문 상세를 불러오지 못했습니다."); }); return () => { active = false; }; }, [detail, open, order.orderId]);
  return <li className="admin-commerce-row"><div><strong>주문 {order.orderNumber}</strong><span>#{order.orderId} · 회원 #{order.memberId} · {order.status}</span><span>{formatPrice(order.paymentAmount)} · {formatDateTime(order.createdAt)}</span></div><div className="admin-row-actions"><button className="button button-secondary" type="button" aria-expanded={open} onClick={() => setOpen((value) => !value)}>{open ? "상세 닫기" : "상세 보기"}</button></div>{open ? <div className="admin-inline-editor">{error ? <p className="field-error" role="alert">{error}</p> : detail ? <AdminOrderDetail detail={detail} /> : <p role="status">상세를 불러오는 중입니다.</p>}</div> : null}</li>;
}

const ORDER_DETAIL_LABELS: Record<string, string> = { orderId: "주문 ID", orderNumber: "주문 번호", memberId: "회원 ID", status: "주문 상태", paymentAmount: "결제 금액", createdAt: "주문 일시", updatedAt: "변경 일시", shippingStatus: "배송 상태", recipientName: "받는 분", recipientPhone: "연락처" };
function AdminOrderDetail({ detail }: { detail: Record<string, unknown> }) {
  return <div><h3>주문 상세</h3><dl className="admin-order-detail-grid">{Object.entries(detail).map(([key, value]) => <div key={key}><dt>{ORDER_DETAIL_LABELS[key] ?? key}</dt><dd>{formatAdminDetailValue(key, value)}</dd></div>)}</dl></div>;
}
function formatAdminDetailValue(key: string, value: unknown): string {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "number" && /amount|price|total/i.test(key)) return formatPrice(value);
  if (typeof value === "string" && /(At|Date|Time)$/i.test(key)) { try { return formatDateTime(value); } catch { return value; } }
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function AuditPanel({ resource }: { resource: ReturnType<typeof useAdminResource<AdminAuditLog[]>> }) {
  const [filter, setFilter] = useState("");
  const rows = useMemo(() => resource.data?.filter((item) => `${item.action} ${item.targetType} ${item.targetId} ${item.adminId}`.toLowerCase().includes(filter.trim().toLowerCase())) ?? [], [filter, resource.data]);
  return <section className="admin-resource"><div className="admin-section-heading"><div><h2>감사 기록</h2><span>{resource.data ? `${resource.data.length}건` : "—"}</span></div></div><ResourceState loading={resource.loading} error={resource.error} onRetry={() => void resource.reload().catch(() => undefined)} />{resource.data && !resource.error ? <><label className="form-field admin-resource-search">기록 찾기<input className="input" type="search" value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="작업, 대상 유형, ID" /></label>{rows.length ? <ul className="admin-commerce-list">{rows.map((item) => <li className="admin-commerce-row" key={item.auditLogId}><div><strong>{item.action}</strong><span>관리자 #{item.adminId} · {item.targetType} #{item.targetId}</span><span>{formatDateTime(item.createdAt)}</span></div></li>)}</ul> : <div className="empty-callout">조건에 맞는 감사 기록이 없습니다.</div>}</> : null}</section>;
}
