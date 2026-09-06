import { requestJson, requestVoid } from "./http/client.ts";
import type { WishlistItem } from "./commerce-types.ts";

export const wishlistApi = {
  list: () => requestJson<{ items: WishlistItem[] }>("/api/wishlist"),
  add: (productId: number, csrf: string) => requestVoid(`/api/wishlist/${encodeURIComponent(productId)}`, { method: "POST", headers: { "X-CSRF-TOKEN": csrf } }),
  remove: (productId: number, csrf: string) => requestVoid(`/api/wishlist/${encodeURIComponent(productId)}`, { method: "DELETE", headers: { "X-CSRF-TOKEN": csrf } }),
};

export type { WishlistItem } from "./commerce-types.ts";
