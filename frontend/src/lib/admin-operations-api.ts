import { requestJson, requestVoid } from "./http/client.ts";
import type { Operation } from "./commerce-types.ts";

export interface RejectReturnRequest { reason: string }
export interface ShipDeliveryRequest { carrierCode: string; trackingNumber: string }
export interface FailDeliveryRequest { reason: string }
export interface ReceiveReturnRequest { restock: boolean }

export const adminOperationsApi = {
  list: () => requestJson<Operation[]>("/api/admin/operations"),
  approveReturn: (id: number, csrf: string) => requestVoid(`/api/admin/returns/${id}/approve`, { method: "POST", headers: { "X-CSRF-TOKEN": csrf } }),
  rejectReturn: (id: number, body: RejectReturnRequest, csrf: string) => requestVoid(`/api/admin/returns/${id}/reject`, { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify(body) }),
  processRefund: (id: number, csrf: string) => requestVoid(`/api/admin/refunds/${id}/process`, { method: "POST", headers: { "X-CSRF-TOKEN": csrf } }),
  retryRefund: (id: number, csrf: string) => requestVoid(`/api/admin/refunds/${id}/retry`, { method: "POST", headers: { "X-CSRF-TOKEN": csrf } }),
  reconcileRefund: (id: number, csrf: string) => requestVoid(`/api/admin/refunds/${id}/reconcile`, { method: "POST", headers: { "X-CSRF-TOKEN": csrf } }),
  reconcilePayment: (id: number, csrf: string) => requestVoid(`/api/admin/payments/${id}/reconcile`, { method: "POST", headers: { "X-CSRF-TOKEN": csrf } }),
  retryBilling: (id: number, csrf: string) => requestVoid(`/api/admin/payments/${id}/retry-billing`, { method: "POST", headers: { "X-CSRF-TOKEN": csrf } }),
  shipDelivery: (id: number, body: ShipDeliveryRequest, csrf: string) => requestVoid(`/api/admin/deliveries/${id}/ship`, { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify(body) }),
  completeDelivery: (id: number, csrf: string) => requestVoid(`/api/admin/deliveries/${id}/complete`, { method: "POST", headers: { "X-CSRF-TOKEN": csrf } }),
  failDelivery: (id: number, body: FailDeliveryRequest, csrf: string) => requestVoid(`/api/admin/deliveries/${id}/fail`, { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify(body) }),
  receiveReturn: (id: number, body: ReceiveReturnRequest, csrf: string) => requestVoid(`/api/admin/returns/${id}/receive`, { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify(body) }),
};

export type { Operation } from "./commerce-types.ts";
