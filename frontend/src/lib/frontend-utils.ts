export const PRODUCTS_PATH = "/products";

const SAFE_RETURN_PATH = /^(?:\/products(?:\/[1-9]\d*)?|\/subscriptions(?:\/[1-9]\d*)?)$/;
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

export function formatPetType(value: string): string {
  if (value === "DOG") return "개";
  if (value === "CAT") return "고양이";
  return value;
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
