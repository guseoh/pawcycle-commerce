import { ApiError } from "./api.ts";

export const MEMBER_AVAILABLE_ACTIONS = ["REQUEST_CANCELLATION", "REQUEST_RETURN"] as const;
export type AvailableAction = typeof MEMBER_AVAILABLE_ACTIONS[number];
export interface OrderDetail { orderId:number; orderNumber:string; status:string; paymentAmount:number; delivery:{deliveryId:number;status:string;carrierCode?:string;trackingNumber?:string}|null; cancellation:{cancellationId:number;status:string}|null; return:{returnId:number;status:string;rejectionReason?:string}|null; refunds:Array<{refundId:number;status:string;attemptNo:number}>; availableActions:AvailableAction[]; }
export interface Notification { notificationId:number; type:string; referenceType:string; referenceId:number; readAt:string|null; createdAt:string; }
export interface OrderSummary { orderId:number; orderNumber:string; status:string; paymentAmount:number; createdAt:string; }
export interface Operation { type:string; referenceId:number; createdAt:string; availableActions:string[]; }
export interface CartItem { skuId:number; quantity:number; skuCode:string; skuName:string; price:number; productId:number; productName:string; }
export interface WishlistItem { productId:number; productName:string; createdAt:string; }
export interface AddressRequest { name:string; recipientName:string; recipientPhone:string; postalCode:string; addressLine1:string; addressLine2:string; }
export interface Address extends AddressRequest { addressId:number; isDefault:boolean; }
export interface CheckoutResult { orderId:number; orderNumber:string; paymentId:number; providerOrderId:string; orderName:string; amount:number; }
export interface BillingMethodStatus { provider:"TOSS"; configured:boolean; registered:boolean; }
async function request<T>(path:string,init?:RequestInit):Promise<T>{const response=await fetch(path,{...init,cache:"no-store",credentials:"same-origin",headers:{Accept:"application/json",...init?.headers}});const body=await response.json().catch(()=>null);if(!response.ok)throw new ApiError(response.status,body&&typeof body.code==="string"?body:{code:"INTERNAL_ERROR",message:"요청을 처리하지 못했습니다.",fieldErrors:[]});return body as T;}
export const commerceFinalApi={
  order:(id:string)=>request<OrderDetail>(`/api/orders/${encodeURIComponent(id)}`),
  orders:()=>request<OrderSummary[]>("/api/orders"),
  cancellation:(id:string,reason:string,csrf:string)=>request(`/api/orders/${encodeURIComponent(id)}/cancellations`,{method:"POST",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify({reason})}),
  returnRequest:(id:string,reason:string,csrf:string)=>request(`/api/orders/${encodeURIComponent(id)}/returns`,{method:"POST",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify({reason})}),
  notifications:()=>request<Notification[]>("/api/notifications"),
  readNotification:(id:number,csrf:string)=>request<void>(`/api/notifications/${id}/read`,{method:"PATCH",headers:{"X-CSRF-TOKEN":csrf}}),
  readAll:(csrf:string)=>request<void>("/api/notifications/read-all",{method:"PATCH",headers:{"X-CSRF-TOKEN":csrf}}),
  operations:()=>request<Operation[]>("/api/admin/operations"),
  operation:(endpoint:string,csrf:string,body?:Record<string,unknown>)=>request(`/api/admin/${endpoint}`,{method:"POST",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:body?JSON.stringify(body):undefined}),
  cart:()=>request<{items:CartItem[]}>("/api/cart"),
  addCart:(skuId:number,quantity:number,csrf:string)=>request<void>("/api/cart/items",{method:"POST",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify({skuId,quantity})}),
  updateCart:(skuId:number,quantity:number,csrf:string)=>request<void>(`/api/cart/items/${encodeURIComponent(skuId)}`,{method:"PATCH",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify({quantity})}),
  deleteCart:(skuId:number,csrf:string)=>request<void>(`/api/cart/items/${encodeURIComponent(skuId)}`,{method:"DELETE",headers:{"X-CSRF-TOKEN":csrf}}),
  wishlist:()=>request<{items:WishlistItem[]}>("/api/wishlist"),
  addWishlist:(productId:number,csrf:string)=>request<void>(`/api/wishlist/${encodeURIComponent(productId)}`,{method:"POST",headers:{"X-CSRF-TOKEN":csrf}}),
  deleteWishlist:(productId:number,csrf:string)=>request<void>(`/api/wishlist/${encodeURIComponent(productId)}`,{method:"DELETE",headers:{"X-CSRF-TOKEN":csrf}}),
  addresses:()=>request<Address[]>("/api/addresses"),
  createAddress:(address:AddressRequest,csrf:string)=>request<{addressId:number}>("/api/addresses",{method:"POST",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify(address)}),
  updateAddress:(addressId:number,address:AddressRequest,csrf:string)=>request<void>(`/api/addresses/${encodeURIComponent(addressId)}`,{method:"PATCH",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify(address)}),
  deleteAddress:(addressId:number,csrf:string)=>request<void>(`/api/addresses/${encodeURIComponent(addressId)}`,{method:"DELETE",headers:{"X-CSRF-TOKEN":csrf}}),
  defaultAddress:(addressId:number,csrf:string)=>request<void>(`/api/addresses/${encodeURIComponent(addressId)}/default`,{method:"PUT",headers:{"X-CSRF-TOKEN":csrf}}),
  updateSubscriptionShipping:(subscriptionId:number,address:AddressRequest,csrf:string)=>request<void>(`/api/subscriptions/${encodeURIComponent(subscriptionId)}/shipping-address`,{method:"PUT",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify(address)}),
  checkout:(addressId:number,csrf:string,idempotencyKey:string)=>request<CheckoutResult>("/api/checkout",{method:"POST",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf,"Idempotency-Key":idempotencyKey},body:JSON.stringify({addressId})}),
  billingMethod:()=>request<BillingMethodStatus>("/api/payment-methods/toss/billing"),
  prepareBilling:(csrf:string)=>request<{prepareToken:string}>("/api/payment-methods/toss/billing/prepare",{method:"POST",headers:{"X-CSRF-TOKEN":csrf}}),
};
