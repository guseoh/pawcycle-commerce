import { requestJson } from "./http/client.ts";

export interface OrderSubscriptionOption { planVersionId: number; planName: string; matchingProductIds: number[]; compatibleOwnedPetIds: number[]; allowedDeliveryCycleWeeks: number[]; packagePriceKrw: number }
export interface OrderSubscriptionOptionsResponse { orderId: number; options: OrderSubscriptionOption[] }

export const orderSubscriptionApi = {
  options: (orderId: number | string) => requestJson<OrderSubscriptionOptionsResponse>(`/api/orders/${encodeURIComponent(orderId)}/subscription-options`),
};
