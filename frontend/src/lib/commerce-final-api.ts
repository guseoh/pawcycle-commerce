import { ApiError } from "./api.ts";

export type AvailableAction = "REQUEST_CANCELLATION" | "REQUEST_RETURN";
export interface OrderDetail { orderId:number; orderNumber:string; status:string; paymentAmount:number; delivery:{deliveryId:number;status:string;carrierCode?:string;trackingNumber?:string}|null; cancellation:{cancellationId:number;status:string}|null; return:{returnId:number;status:string;rejectionReason?:string}|null; refunds:Array<{refundId:number;status:string;attemptNo:number}>; availableActions:AvailableAction[]; }
export interface Notification { notificationId:number; type:string; referenceType:string; referenceId:number; readAt:string|null; createdAt:string; }
export interface OrderSummary { orderId:number; orderNumber:string; status:string; paymentAmount:number; createdAt:string; }
export interface Operation { type:string; referenceId:number; createdAt:string; availableActions:string[]; }
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
};
