export const TOSS_CHECKOUT_CONTEXT_KEY = "pawcycle.toss.checkout.v1";
export const TOSS_SUCCESS_CALLBACK_KEY = "pawcycle.toss.success-callback.v1";

export interface TossCheckoutContext {
  providerOrderId: string;
  orderName: string;
  amount: number;
}

export interface TossSuccessCallback {
  paymentKey: string;
  providerOrderId: string;
  urlAmount: string;
}

export function isTossTestClientKey(value: string | undefined): value is string {
  return Boolean(value && value.startsWith("test_ck_"));
}

export function validateTossSuccess(
  params: { paymentKey: string | null; providerOrderId: string | null; urlAmount: string | null },
  expected: TossCheckoutContext | null,
): string | null {
  if (!params.paymentKey || !params.providerOrderId || !params.urlAmount) return "결제 인증 결과가 완전하지 않습니다.";
  if (!expected || expected.providerOrderId !== params.providerOrderId) return "결제 주문 정보를 확인할 수 없습니다.";
  const urlAmount = Number(params.urlAmount);
  if (!Number.isSafeInteger(urlAmount) || urlAmount < 0 || urlAmount !== expected.amount) return "결제 금액을 확인할 수 없습니다.";
  return null;
}

export function readTossCheckoutContext(): TossCheckoutContext | null {
  if (typeof window === "undefined") return null;
  try {
    const parsed: unknown = JSON.parse(window.sessionStorage.getItem(TOSS_CHECKOUT_CONTEXT_KEY) ?? "null");
    if (!parsed || typeof parsed !== "object") return null;
    const value = parsed as Partial<TossCheckoutContext>;
    return typeof value.providerOrderId === "string" && typeof value.orderName === "string" && typeof value.amount === "number" && Number.isSafeInteger(value.amount)
      ? { providerOrderId: value.providerOrderId, orderName: value.orderName, amount: value.amount }
      : null;
  } catch {
    return null;
  }
}

export function saveTossCheckoutContext(context: TossCheckoutContext): void {
  try { window.sessionStorage.setItem(TOSS_CHECKOUT_CONTEXT_KEY, JSON.stringify(context)); } catch { /* optional browser state */ }
}

export function readTossSuccessCallback(): TossSuccessCallback | null {
  if (typeof window === "undefined") return null;
  try {
    const parsed: unknown = JSON.parse(window.sessionStorage.getItem(TOSS_SUCCESS_CALLBACK_KEY) ?? "null");
    if (!parsed || typeof parsed !== "object") return null;
    const value = parsed as Partial<TossSuccessCallback>;
    return typeof value.paymentKey === "string" && value.paymentKey.length > 0 && value.paymentKey.length <= 200
      && typeof value.providerOrderId === "string" && value.providerOrderId.length > 0 && value.providerOrderId.length <= 128
      && typeof value.urlAmount === "string" && /^\d{1,18}$/.test(value.urlAmount)
      ? { paymentKey: value.paymentKey, providerOrderId: value.providerOrderId, urlAmount: value.urlAmount }
      : null;
  } catch {
    return null;
  }
}

export function saveTossSuccessCallback(callback: TossSuccessCallback): void {
  if (!callback.paymentKey || callback.paymentKey.length > 200 || !callback.providerOrderId || callback.providerOrderId.length > 128 || !/^\d{1,18}$/.test(callback.urlAmount)) return;
  try { window.sessionStorage.setItem(TOSS_SUCCESS_CALLBACK_KEY, JSON.stringify(callback)); } catch { /* local Toss Test resume state */ }
}
