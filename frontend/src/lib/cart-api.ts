import { requestVoid, requestJson } from "./http/client.ts";
import type { CartResult } from "./commerce-types.ts";

export const cartApi = {
  get: () => requestJson<CartResult>("/api/cart"),
  add: (skuId: number, quantity: number, csrf: string) => requestVoid("/api/cart/items", { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify({ skuId, quantity }) }),
  update: (skuId: number, quantity: number, csrf: string) => requestVoid(`/api/cart/items/${encodeURIComponent(skuId)}`, { method: "PATCH", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify({ quantity }) }),
  remove: (skuId: number, csrf: string) => requestVoid(`/api/cart/items/${encodeURIComponent(skuId)}`, { method: "DELETE", headers: { "X-CSRF-TOKEN": csrf } }),
};

export type { CartItem, CartResult, PricingBreakdown } from "./commerce-types.ts";
