import { requestJson } from "./http/client.ts";
import type { CancellationResult, OrderDetail, OrderSummary, QuickReorderResult, ReturnResult } from "./commerce-types.ts";

type OrderDetailWire = OrderDetail & { returnRequest?: OrderDetail["return"] };

function normalizeOrderDetail(response: OrderDetailWire): OrderDetail {
  const { returnRequest, ...detail } = response;
  if (Object.hasOwn(detail, "return") || !Object.hasOwn(response, "returnRequest")) return detail;
  return { ...detail, return: returnRequest };
}

export const orderApi = {
  detail: async (id: string): Promise<OrderDetail> => normalizeOrderDetail(await requestJson<OrderDetailWire>(`/api/orders/${encodeURIComponent(id)}`)),
  list: () => requestJson<OrderSummary[]>("/api/orders"),
  cancellation: (id: string, reason: string, csrf: string) => requestJson<CancellationResult>(`/api/orders/${encodeURIComponent(id)}/cancellations`, { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify({ reason }) }),
  returnRequest: (id: string, reason: string, csrf: string) => requestJson<ReturnResult>(`/api/orders/${encodeURIComponent(id)}/returns`, { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify({ reason }) }),
  quickReorder: (orderId: string, csrf: string, idempotencyKey: string) => requestJson<QuickReorderResult>(`/api/orders/${encodeURIComponent(orderId)}/reorder`, { method: "POST", headers: { "X-CSRF-TOKEN": csrf, "Idempotency-Key": idempotencyKey } }),
};

export type { CancellationResult, OrderDetail, OrderSummary, QuickReorderResult, ReturnResult } from "./commerce-types.ts";
