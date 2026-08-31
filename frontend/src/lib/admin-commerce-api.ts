import { ApiError, type ApiErrorBody } from "./api.ts";

export interface AdminInventory { skuId: number; availableQuantity: number; reservedQuantity: number; version: number; skuCode: string }
export interface AdminCoupon { couponId: number; name: string; discountType: "FIXED_AMOUNT" | "PERCENTAGE"; discountValue: number; minimumOrderAmount: number; maximumDiscountAmount: number | null; validFrom: string; validUntil: string; active: boolean }
export interface AdminMembershipGrade { gradeId: number; code: string; name: string; minimumPurchaseAmount: number; displayOrder: number; active: boolean; benefitCouponId: number | null }
export interface AdminOrder { orderId: number; orderNumber: string; memberId: number; status: string; paymentAmount: number; createdAt: string }
export interface AdminAuditLog { auditLogId: number; adminId: number; action: string; targetType: string; targetId: number; createdAt: string }
export interface AdminCouponInput { name: string; discountType: "FIXED_AMOUNT" | "PERCENTAGE"; discountValue: string; minimumOrderAmount: string; maximumDiscountAmount: string; validFrom: string; validUntil: string; active: boolean }
export interface AdminMembershipGradeInput { code: string; name: string; minimumPurchaseAmount: string; displayOrder: string; active: boolean; benefitCouponId: string }

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<ApiErrorBody>;
  return typeof candidate.code === "string" && typeof candidate.message === "string" && Array.isArray(candidate.fieldErrors);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/admin${path}`, { ...init, credentials: "same-origin", cache: "no-store", headers: { Accept: "application/json", ...init.headers } });
  const text = await response.text();
  let body: unknown = null;
  try { body = text ? JSON.parse(text) : null; } catch { throw new ApiError(response.status, { code: "INVALID_API_RESPONSE", message: "서버 응답을 확인할 수 없습니다.", fieldErrors: [] }); }
  if (!response.ok) {
    if (isApiErrorBody(body)) throw new ApiError(response.status, body);
    throw new ApiError(response.status || 500, { code: "INTERNAL_ERROR", message: "요청을 처리하지 못했습니다.", fieldErrors: [] });
  }
  return body as T;
}

function json(method: string, body: unknown, csrfToken: string): RequestInit {
  return { method, headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken }, body: JSON.stringify(body) };
}

export const adminCommerceApi = {
  inventories: () => request<AdminInventory[]>("/inventories"),
  adjustInventory: (skuId: number, delta: number, csrfToken: string) => request<void>(`/inventories/${encodeURIComponent(skuId)}/adjustments`, json("POST", { delta }, csrfToken)),
  coupons: () => request<AdminCoupon[]>("/coupons"),
  createCoupon: (input: Record<string, unknown>, csrfToken: string) => request<{ couponId: number }>("/coupons", json("POST", input, csrfToken)),
  updateCoupon: (couponId: number, input: Record<string, unknown>, csrfToken: string) => request<void>(`/coupons/${encodeURIComponent(couponId)}`, json("PATCH", input, csrfToken)),
  issueCoupon: (couponId: number, memberId: number, csrfToken: string) => request<void>(`/coupons/${encodeURIComponent(couponId)}/issues`, json("POST", { memberId }, csrfToken)),
  membershipGrades: () => request<AdminMembershipGrade[]>("/membership-grades"),
  createMembershipGrade: (input: Record<string, unknown>, csrfToken: string) => request<{ gradeId: number }>("/membership-grades", json("POST", input, csrfToken)),
  evaluateMembership: (memberId: number, csrfToken: string) => request<void>(`/members/${encodeURIComponent(memberId)}/membership/evaluate`, json("POST", {}, csrfToken)),
  orders: () => request<AdminOrder[]>("/orders"),
  order: (orderId: number) => request<Record<string, unknown>>(`/orders/${encodeURIComponent(orderId)}`),
  auditLogs: () => request<AdminAuditLog[]>("/audit-logs"),
};
