export const TOSS_CHECKOUT_CONTEXT_KEY = "pawcycle.toss.checkout.v1";

export interface TossCheckoutContext {
  providerOrderId: string;
  orderName: string;
  amount: number;
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
