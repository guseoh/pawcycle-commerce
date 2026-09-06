import { requestJson } from "./http/client.ts";
import type { TossConfirmResult } from "./commerce-types.ts";

export const paymentApi = {
  confirmToss: (paymentKey: string, providerOrderId: string, amount: number, csrf: string) => requestJson<TossConfirmResult>("/api/payments/toss/confirm", { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify({ paymentKey, providerOrderId, amount }) }),
};

export type { TossConfirmResult } from "./commerce-types.ts";
