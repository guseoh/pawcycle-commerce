import { requestVoid } from "./http/client.ts";
import type { ProductSort } from "./api.ts";

export type InteractionType = "PRODUCT_IMPRESSION" | "PRODUCT_VIEW" | "SEARCH" | "FILTER" | "RECOMMENDATION_IMPRESSION" | "RECOMMENDATION_CLICK";
export interface InteractionContext { hasTextQuery?: boolean; petType?: "DOG" | "CAT"; category?: string; subcategory?: string; brand?: string; facets?: string[]; minPrice?: number; maxPrice?: number; sort?: ProductSort }
export interface InteractionEvent { eventId: string; type: InteractionType; productId?: number; petId?: number; recommendationRequestId?: string; source: string; context?: InteractionContext }

export function newInteractionEventId(): string | null {
  if (typeof crypto === "undefined") return null;
  if (typeof crypto.randomUUID === "function") {
    try { return crypto.randomUUID(); } catch { /* fall through to getRandomValues */ }
  }
  if (typeof crypto.getRandomValues !== "function") return null;
  try {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  } catch { return null; }
}

export function createInteractionEvent(event: Omit<InteractionEvent, "eventId">): InteractionEvent | null {
  const eventId = newInteractionEventId();
  return eventId === null ? null : { eventId, ...event };
}

export const interactionApi = {
  send: (events: InteractionEvent[], csrfToken: string) => events.length === 0 ? Promise.resolve() : requestVoid("/api/interactions", { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken }, body: JSON.stringify({ events }) }),
};

export type { ProductSort } from "./api.ts";
