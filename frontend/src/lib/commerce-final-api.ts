import { ApiError } from "./api.ts";

export const MEMBER_AVAILABLE_ACTIONS = ["REQUEST_CANCELLATION", "REQUEST_RETURN"] as const;
export type AvailableAction = typeof MEMBER_AVAILABLE_ACTIONS[number];
export interface PricingBreakdown { originalAmount:number; subtotalAmount:number; discountAmount:number; shippingFee:number; finalAmount:number; paymentAmount:number; }
export interface OrderItem { skuId:number; skuCodeSnapshot?:string|null; productNameSnapshot:string; skuNameSnapshot:string; unitPrice:number; quantity:number; lineAmount:number; }
export interface OrderDetail { orderId:number; orderNumber:string; source:string; status:string; originalAmount:number; discountAmount:number; shippingFee:number; paymentAmount:number; recipientName:string|null; recipientPhone:string|null; postalCode:string|null; addressLine1:string|null; addressLine2:string|null; createdAt:string; paidAt:string|null; items:OrderItem[]; payment:{paymentId:number;type:string;provider:string;status:string;amount:number;attemptNo:number;providerStatus?:string|null}|null; delivery:{deliveryId:number;status:string;carrierCode?:string;trackingNumber?:string;shippedAt?:string|null;deliveredAt?:string|null}|null; cancellation:{cancellationId:number;status:string}|null; return:{returnId:number;status:string;rejectionReason?:string}|null; refunds:Array<{refundId:number;status:string;attemptNo:number;amount:number}>; availableActions:AvailableAction[]; }
export interface Notification { notificationId:number; type:string; referenceType:string; referenceId:number; readAt:string|null; createdAt:string; }
export interface OrderSummary { orderId:number; orderNumber:string; status:string; paymentAmount:number; createdAt:string; }
export interface Operation { type:string; referenceId:number; createdAt:string; availableActions:string[]; }
export interface CartItem { skuId:number; quantity:number; skuCode:string; skuName:string; price:number; unitPrice:number; lineAmount:number; productId:number; productName:string; availableQuantity:number; purchasable:boolean; }
export interface CartResult { items:CartItem[]; pricing:PricingBreakdown; }
export interface MemberCoupon { memberCouponId:number; couponId:number; name:string; discountType:"FIXED_AMOUNT"|"PERCENTAGE"; discountValue:number; status:"AVAILABLE"|"RESERVED"|"USED"; validFrom:string; validUntil:string; }
export interface WishlistItem { productId:number; productName:string; createdAt:string; }
export interface AddressRequest { name:string; recipientName:string; recipientPhone:string; postalCode:string; addressLine1:string; addressLine2:string; }
export interface Address extends AddressRequest { addressId:number; isDefault:boolean; }
export interface CheckoutResult { orderId:number; orderNumber:string; paymentId:number; providerOrderId:string; orderName:string; amount:number; pricing?:PricingBreakdown; tossTestEnabled:boolean; }
export interface TossConfirmResult { paymentId:number; orderId:number; status:"SUCCEEDED"|"FAILED"|"UNKNOWN"; }
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
  cart:()=>request<CartResult>("/api/cart"),
  addCart:(skuId:number,quantity:number,csrf:string)=>request<void>("/api/cart/items",{method:"POST",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify({skuId,quantity})}),
  updateCart:(skuId:number,quantity:number,csrf:string)=>request<void>(`/api/cart/items/${encodeURIComponent(skuId)}`,{method:"PATCH",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify({quantity})}),
  deleteCart:(skuId:number,csrf:string)=>request<void>(`/api/cart/items/${encodeURIComponent(skuId)}`,{method:"DELETE",headers:{"X-CSRF-TOKEN":csrf}}),
  wishlist:()=>request<{items:WishlistItem[]}>("/api/wishlist"),
  addWishlist:(productId:number,csrf:string)=>request<void>(`/api/wishlist/${encodeURIComponent(productId)}`,{method:"POST",headers:{"X-CSRF-TOKEN":csrf}}),
  deleteWishlist:(productId:number,csrf:string)=>request<void>(`/api/wishlist/${encodeURIComponent(productId)}`,{method:"DELETE",headers:{"X-CSRF-TOKEN":csrf}}),
  coupons:()=>request<MemberCoupon[]>("/api/coupons"),
  addresses:()=>request<Address[]>("/api/addresses"),
  createAddress:(address:AddressRequest,csrf:string)=>request<{addressId:number}>("/api/addresses",{method:"POST",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify(address)}),
  updateAddress:(addressId:number,address:AddressRequest,csrf:string)=>request<void>(`/api/addresses/${encodeURIComponent(addressId)}`,{method:"PATCH",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify(address)}),
  deleteAddress:(addressId:number,csrf:string)=>request<void>(`/api/addresses/${encodeURIComponent(addressId)}`,{method:"DELETE",headers:{"X-CSRF-TOKEN":csrf}}),
  defaultAddress:(addressId:number,csrf:string)=>request<void>(`/api/addresses/${encodeURIComponent(addressId)}/default`,{method:"PUT",headers:{"X-CSRF-TOKEN":csrf}}),
  updateSubscriptionShipping:(subscriptionId:number,address:AddressRequest,csrf:string)=>request<void>(`/api/subscriptions/${encodeURIComponent(subscriptionId)}/shipping-address`,{method:"PUT",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify(address)}),
  checkout:(addressId:number,csrf:string,idempotencyKey:string,memberCouponId?:number)=>request<CheckoutResult>("/api/checkout",{method:"POST",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf,"Idempotency-Key":idempotencyKey},body:JSON.stringify(memberCouponId === undefined ? {addressId} : {addressId,memberCouponId})}),
  confirmToss:(paymentKey:string,providerOrderId:string,amount:number,csrf:string)=>request<TossConfirmResult>("/api/payments/toss/confirm",{method:"POST",headers:{"Content-Type":"application/json","X-CSRF-TOKEN":csrf},body:JSON.stringify({paymentKey,providerOrderId,amount})}),
  billingMethod:()=>request<BillingMethodStatus>("/api/payment-methods/toss/billing"),
  prepareBilling:(csrf:string)=>request<{prepareToken:string}>("/api/payment-methods/toss/billing/prepare",{method:"POST",headers:{"X-CSRF-TOKEN":csrf}}),
};
