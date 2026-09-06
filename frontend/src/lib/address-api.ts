import { requestJson, requestVoid } from "./http/client.ts";
import type { Address, AddressRequest, SubscriptionShippingAddressRequest } from "./commerce-types.ts";

export const addressApi = {
  list: () => requestJson<Address[]>("/api/addresses"),
  create: (address: AddressRequest, csrf: string) => requestJson<{ addressId: number }>("/api/addresses", { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify(address) }),
  update: (addressId: number, address: AddressRequest, csrf: string) => requestVoid(`/api/addresses/${encodeURIComponent(addressId)}`, { method: "PATCH", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify(address) }),
  remove: (addressId: number, csrf: string) => requestVoid(`/api/addresses/${encodeURIComponent(addressId)}`, { method: "DELETE", headers: { "X-CSRF-TOKEN": csrf } }),
  setDefault: (addressId: number, csrf: string) => requestVoid(`/api/addresses/${encodeURIComponent(addressId)}/default`, { method: "PUT", headers: { "X-CSRF-TOKEN": csrf } }),
  updateSubscriptionShipping: (subscriptionId: number, address: SubscriptionShippingAddressRequest, csrf: string) => requestVoid(`/api/subscriptions/${encodeURIComponent(subscriptionId)}/shipping-address`, { method: "PUT", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf }, body: JSON.stringify(address) }),
};

export type { Address, AddressRequest, SubscriptionShippingAddressRequest } from "./commerce-types.ts";
