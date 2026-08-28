import type { Notification } from "./commerce-final-api.ts";

export function notificationCopy(item: Pick<Notification, "type">): string {
  return {
    ORDER_PAID: "주문 결제가 완료됐어요.",
    ORDER_SHIPPED: "주문 상품이 배송을 시작했어요.",
    ORDER_DELIVERED: "주문 상품이 배송 완료됐어요.",
    SUBSCRIPTION_HELD: "정기배송 처리가 잠시 보류됐어요.",
    SUBSCRIPTION_DELIVERY_REMINDER: "정기배송이 곧 예정되어 있어요.",
  }[item.type] ?? "주문과 정기배송에 새로운 소식이 있어요.";
}

export function notificationHref(item: Pick<Notification, "referenceType" | "referenceId" | "subscriptionId">): string {
  if (item.referenceType === "ORDER") return `/orders/${item.referenceId}`;
  if (item.referenceType === "SUBSCRIPTION") return `/subscriptions/${item.referenceId}`;
  if (item.referenceType === "SCHEDULE") return item.subscriptionId ? `/subscriptions/${item.subscriptionId}` : "/subscriptions";
  return "/orders";
}
