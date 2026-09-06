import { requestJson } from "./http/client.ts";

export interface ReorderTimingItem { productId: number; productName: string; lastPurchasedDate: string; expectedReorderDate: string; state: "OVERDUE" | "DUE_SOON"; purchaseCount: number }
export interface ReorderTimingResponse { items: ReorderTimingItem[] }

export function reorderTimingItems(response: ReorderTimingResponse): ReorderTimingItem[] { return response.items; }

export const reorderApi = {
  timing: () => requestJson<ReorderTimingResponse>("/api/recommendations/reorder-timing"),
};
