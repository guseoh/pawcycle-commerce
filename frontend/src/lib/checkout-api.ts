import { requestJson } from "./http/client.ts";
import type { CheckoutResult } from "./commerce-types.ts";

export const checkoutApi = {
  create: (addressId: number, csrf: string, idempotencyKey: string, memberCouponId?: number, cartVersion?: number) => requestJson<CheckoutResult>("/api/checkout", { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf, "Idempotency-Key": idempotencyKey }, body: JSON.stringify({ addressId, ...(memberCouponId === undefined ? {} : { memberCouponId }), ...(cartVersion === undefined ? {} : { cartVersion }) }) }),
};

export type { CheckoutResult } from "./commerce-types.ts";
