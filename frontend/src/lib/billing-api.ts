import { requestJson } from "./http/client.ts";
import type { BillingMethodStatus } from "./commerce-types.ts";

export const billingApi = {
  method: () => requestJson<BillingMethodStatus>("/api/payment-methods/toss/billing"),
  prepare: (csrf: string) => requestJson<{ prepareToken: string }>("/api/payment-methods/toss/billing/prepare", { method: "POST", headers: { "X-CSRF-TOKEN": csrf } }),
};

export type { BillingMethodStatus } from "./commerce-types.ts";
