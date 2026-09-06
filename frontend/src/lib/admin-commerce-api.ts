import { requestJson, requestVoid } from "./http/client.ts";

export interface AdminInventory { skuId: number; availableQuantity: number; reservedQuantity: number; version: number; skuCode: string }
export interface AdminCoupon { couponId: number; name: string; discountType: "FIXED_AMOUNT" | "PERCENTAGE"; discountValue: number; minimumOrderAmount: number; maximumDiscountAmount: number | null; validFrom: string; validUntil: string; active: boolean }
export interface AdminMembershipGrade { gradeId: number; code: string; name: string; minimumPurchaseAmount: number; displayOrder: number; active: boolean; benefitCouponId: number | null }
export interface AdminOrder { orderId: number; orderNumber: string; memberId: number; status: string; paymentAmount: number; createdAt: string }
export interface AdminAuditLog { auditLogId: number; adminId: number; action: string; targetType: string; targetId: number; createdAt: string }
export interface AdminCouponInput { name: string; discountType: "FIXED_AMOUNT" | "PERCENTAGE"; discountValue: string; minimumOrderAmount: string; maximumDiscountAmount: string; validFrom: string; validUntil: string; active: boolean }
export interface AdminCouponRequest { name: string; discountType: "FIXED_AMOUNT" | "PERCENTAGE"; discountValue: number; minimumOrderAmount: number; maximumDiscountAmount: number | null; validFrom: string; validUntil: string; active: boolean }
export interface AdminMembershipGradeInput { code: string; name: string; minimumPurchaseAmount: string; displayOrder: string; active: boolean; benefitCouponId: string }
export interface AdminMembershipGradeRequest { code: string; name: string; minimumPurchaseAmount: number; displayOrder: number; active: boolean; benefitCouponId: number | null }

export function toAdminCouponRequest(input: AdminCouponInput): AdminCouponRequest {
  return {
    name: input.name.trim(),
    discountType: input.discountType,
    discountValue: Number(input.discountValue),
    minimumOrderAmount: Number(input.minimumOrderAmount || 0),
    maximumDiscountAmount: input.maximumDiscountAmount ? Number(input.maximumDiscountAmount) : null,
    validFrom: input.validFrom,
    validUntil: input.validUntil,
    active: input.active,
  };
}

export function toAdminCouponInput(coupon: AdminCoupon): AdminCouponInput {
  const localDateTime = (value: string) => value.replace(" ", "T").slice(0, 16);
  return {
    name: coupon.name,
    discountType: coupon.discountType,
    discountValue: String(coupon.discountValue),
    minimumOrderAmount: String(coupon.minimumOrderAmount),
    maximumDiscountAmount: coupon.maximumDiscountAmount === null ? "" : String(coupon.maximumDiscountAmount),
    validFrom: localDateTime(coupon.validFrom),
    validUntil: localDateTime(coupon.validUntil),
    active: coupon.active,
  };
}

function json(method: string, body: unknown, csrfToken: string): RequestInit {
  return { method, headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken }, body: JSON.stringify(body) };
}

export const adminCommerceApi = {
  inventories: () => requestJson<AdminInventory[]>("/api/admin/inventories"),
  adjustInventory: (skuId: number, delta: number, csrfToken: string) => requestVoid(`/api/admin/inventories/${encodeURIComponent(skuId)}/adjustments`, json("POST", { delta }, csrfToken)),
  coupons: () => requestJson<AdminCoupon[]>("/api/admin/coupons"),
  createCoupon: (input: AdminCouponRequest, csrfToken: string) => requestJson<{ couponId: number }>("/api/admin/coupons", json("POST", input, csrfToken)),
  updateCoupon: (couponId: number, input: AdminCouponRequest, csrfToken: string) => requestVoid(`/api/admin/coupons/${encodeURIComponent(couponId)}`, json("PATCH", input, csrfToken)),
  issueCoupon: (couponId: number, memberId: number, csrfToken: string) => requestVoid(`/api/admin/coupons/${encodeURIComponent(couponId)}/issues`, json("POST", { memberId }, csrfToken)),
  membershipGrades: () => requestJson<AdminMembershipGrade[]>("/api/admin/membership-grades"),
  createMembershipGrade: (input: AdminMembershipGradeRequest, csrfToken: string) => requestJson<{ gradeId: number }>("/api/admin/membership-grades", json("POST", input, csrfToken)),
  evaluateMembership: (memberId: number, csrfToken: string) => requestVoid(`/api/admin/members/${encodeURIComponent(memberId)}/membership/evaluate`, json("POST", {}, csrfToken)),
  orders: () => requestJson<AdminOrder[]>("/api/admin/orders"),
  order: (orderId: number) => requestJson<AdminOrder>(`/api/admin/orders/${encodeURIComponent(orderId)}`),
  auditLogs: () => requestJson<AdminAuditLog[]>("/api/admin/audit-logs"),
};
