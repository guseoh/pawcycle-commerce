export const PRODUCTS_PATH = "/products";

const SAFE_RETURN_PATH = /^(?:\/|\/products(?:\/[1-9]\d*)?|\/subscriptions(?:\/(?:new|[1-9]\d*))?|\/mvp2\/subscriptions(?:\/(?:new|[1-9]\d*))?|\/orders(?:\/[1-9]\d*)?|\/notifications|\/wishlist|\/cart|\/checkout(?:\/success)?|\/addresses|\/billing-methods|\/my)$/;
const ISO_LOCAL_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

export function sanitizeReturnTo(value: string | null | undefined): string {
  return value && SAFE_RETURN_PATH.test(value) ? value : PRODUCTS_PATH;
}

export function buildLoginHref(returnTo: string): string {
  return `/login?returnTo=${encodeURIComponent(sanitizeReturnTo(returnTo))}`;
}

export function formatIsoLocalDate(value: string): string {
  const match = ISO_LOCAL_DATE.exec(value);
  if (!match) {
    return value;
  }
  const [, year, month, day] = match;
  return `${Number(year)}. ${Number(month)}. ${Number(day)}.`;
}

export function formatPrice(value: number): string {
  return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

const ORDER_STATUS_LABELS: Record<string, string> = {
  CREATED: "주문 준비",
  PAYMENT_PENDING: "결제 대기",
  PAID: "결제 완료",
  PAYMENT_FAILED: "결제 실패",
  PAYMENT_ACTION_REQUIRED: "결제 확인 필요",
  EXPIRED: "주문 만료",
};

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  READY: "결제 대기",
  PROCESSING: "결제 확인 중",
  SUCCEEDED: "결제 완료",
  FAILED: "결제 실패",
  UNKNOWN: "결제 확인 필요",
};

const DELIVERY_STATUS_LABELS: Record<string, string> = {
  PREPARING: "배송 준비 중",
  SHIPPED: "배송 중",
  DELIVERED: "배송 완료",
  FAILED: "배송 확인 필요",
};

export function formatOrderStatus(value: string): string { return ORDER_STATUS_LABELS[value] ?? "주문 상태 확인 중"; }
export function formatPaymentStatus(value: string | undefined): string { return value ? (PAYMENT_STATUS_LABELS[value] ?? "결제 상태 확인 중") : "결제 정보 준비 중"; }
export function formatDeliveryStatus(value: string | undefined): string { return value ? (DELIVERY_STATUS_LABELS[value] ?? "배송 상태 확인 중") : "배송 준비 전"; }

const INTERNAL_DEMO_LABEL = /(^|[\s_-])(?:qa|test|fixture|demo|foundation|v\d+|commerce|concurrent|cleanup)(?=$|[\s_-])/i;

export function isInternalDemoLabel(value: string | null | undefined): boolean {
  return typeof value === "string" && INTERNAL_DEMO_LABEL.test(value);
}

export function userFacingCatalogLabel(value: string | null | undefined, fallback: string): string {
  return value && !isInternalDemoLabel(value) ? value : fallback;
}

export function notifyCommerceChanged(): void {
  if (typeof window !== "undefined") window.dispatchEvent(new Event("pawcycle-commerce-changed"));
}

export interface RecentProduct {
  productId: number;
  name: string;
  thumbnailUrl: string | null;
  price: number | null;
}

const RECENT_PRODUCTS_KEY = "pawcycle.recent-products.v1";
const RECENT_PRODUCTS_LIMIT = 6;

export function getRecentProducts(): RecentProduct[] {
  if (typeof window === "undefined") return [];
  try {
    const value: unknown = JSON.parse(window.localStorage.getItem(RECENT_PRODUCTS_KEY) ?? "[]");
    if (!Array.isArray(value)) return [];
    return value.filter((item): item is RecentProduct => Boolean(item && typeof item === "object" && typeof item.productId === "number" && typeof item.name === "string" && !isInternalDemoLabel(item.name)));
  } catch {
    return [];
  }
}

export function rememberRecentProduct(product: RecentProduct): RecentProduct[] {
  if (isInternalDemoLabel(product.name)) return getRecentProducts();
  const next = [product, ...getRecentProducts().filter((item) => item.productId !== product.productId)].slice(0, RECENT_PRODUCTS_LIMIT);
  try { window.localStorage.setItem(RECENT_PRODUCTS_KEY, JSON.stringify(next)); } catch { /* localStorage is an optional convenience */ }
  return next;
}

export function formatPetType(value: string): string {
  if (value === "DOG") return "개";
  if (value === "CAT") return "고양이";
  return value;
}

export function formatSubscriptionStatus(value: string): string {
  if (value === "ACTIVE") return "이용 중";
  if (value === "PAUSED") return "일시정지";
  if (value === "CANCELED") return "해지됨";
  return value;
}

export function cartQuantityError(value: string): string | null {
  const quantity = Number(value);
  if (!Number.isInteger(quantity) || quantity < 1) return "수량은 1 이상의 정수여야 합니다.";
  return null;
}

export function cartQuantityErrorForMaximum(value: string, maximum: number): string | null {
  return cartQuantityError(value) ?? (Number(value) > maximum ? `현재 재고 ${maximum.toLocaleString()}개 이하로 선택해 주세요.` : null);
}

export function cartQuantityForUpdate(value: string, maximum?: number): number | null {
  return (maximum === undefined ? cartQuantityError(value) : cartQuantityErrorForMaximum(value, maximum)) === null ? Number(value) : null;
}

export interface SubscriptionDraft {
  skuId: number | null;
  quantity: string;
  deliveryCycleWeeks: number | null;
}

export type SubscriptionDraftErrors = Partial<
  Record<"skuId" | "quantity" | "deliveryCycleWeeks", string>
>;

export function validateSubscriptionDraft(
  draft: SubscriptionDraft,
  selectableSkuIds: readonly number[],
  selectableCycles: readonly number[],
): SubscriptionDraftErrors {
  const errors: SubscriptionDraftErrors = {};

  if (draft.skuId === null || !selectableSkuIds.includes(draft.skuId)) {
    errors.skuId = "구독할 옵션을 선택해 주세요.";
  }

  if (!draft.quantity.trim()) {
    errors.quantity = "수량을 입력해 주세요.";
  } else if (!/^\d+$/.test(draft.quantity)) {
    errors.quantity = "수량은 정수로 입력해 주세요.";
  } else {
    const quantity = Number(draft.quantity);
    if (quantity < 1) {
      errors.quantity = "수량은 1개 이상이어야 합니다.";
    } else if (quantity > 10) {
      errors.quantity = "수량은 최대 10개까지 선택할 수 있습니다.";
    }
  }

  if (draft.deliveryCycleWeeks === null) {
    errors.deliveryCycleWeeks = "배송 주기를 선택해 주세요.";
  } else if (!selectableCycles.includes(draft.deliveryCycleWeeks)) {
    errors.deliveryCycleWeeks = "배송 주기는 제공된 선택지 중에서 선택해 주세요.";
  }

  return errors;
}
