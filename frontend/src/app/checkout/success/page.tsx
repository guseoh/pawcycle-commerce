"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi, type TossConfirmResult } from "@/lib/commerce-final-api";
import { buildLoginHref, formatPrice } from "@/lib/frontend-utils";
import { readTossCheckoutContext, readTossSuccessCallback, saveTossSuccessCallback, validateTossSuccess, type TossSuccessCallback } from "@/lib/toss-payment";

function callbackFromParams(paymentKey: string | null, providerOrderId: string | null, urlAmount: string | null): TossSuccessCallback | null {
  return paymentKey && providerOrderId && urlAmount ? { paymentKey, providerOrderId, urlAmount } : null;
}

function SuccessContent() {
  const auth = useAuth();
  const { status, executeWithCsrf, markAnonymous } = auth;
  const params = useSearchParams();
  const paymentKey = params.get("paymentKey");
  const providerOrderId = params.get("orderId");
  const urlAmount = params.get("amount");
  const confirmPromiseRef = useRef<Promise<TossConfirmResult> | null>(null);
  const confirmKeyRef = useRef<string | null>(null);
  const [callbackReady, setCallbackReady] = useState(false);
  const [hasCallback, setHasCallback] = useState(false);
  const [state, setState] = useState<{ status: "loading" } | { status: "error"; message: string } | { status: "done"; paymentId: number; orderId: number; result: "SUCCEEDED" | "FAILED" | "UNKNOWN"; amount: number }>({ status: "loading" });

  useEffect(() => {
    const callback = callbackFromParams(paymentKey, providerOrderId, urlAmount);
    if (callback) {
      saveTossSuccessCallback(callback);
      window.history.replaceState({}, "", "/checkout/success");
      setHasCallback(true);
    } else {
      setHasCallback(Boolean(readTossSuccessCallback()));
    }
    setCallbackReady(true);
  }, [paymentKey, providerOrderId, urlAmount]);

  useEffect(() => {
    if (!callbackReady || status !== "authenticated") return;
    const callback = callbackFromParams(paymentKey, providerOrderId, urlAmount) ?? readTossSuccessCallback();
    const expected = readTossCheckoutContext();
    const validationError = validateTossSuccess(
      { paymentKey: callback?.paymentKey ?? null, providerOrderId: callback?.providerOrderId ?? null, urlAmount: callback?.urlAmount ?? null },
      expected,
    );
    if (validationError || !callback || !expected) {
      const timer = window.setTimeout(() => setState({ status: "error", message: validationError ?? "결제 주문 정보를 확인할 수 없습니다." }), 0);
      return () => window.clearTimeout(timer);
    }

    const confirmKey = `${callback.paymentKey}\u0000${callback.providerOrderId}\u0000${expected.amount}`;
    if (!confirmPromiseRef.current || confirmKeyRef.current !== confirmKey) {
      confirmKeyRef.current = confirmKey;
      confirmPromiseRef.current = executeWithCsrf((csrf) => commerceFinalApi.confirmToss(callback.paymentKey, callback.providerOrderId, expected.amount, csrf));
    }

    let active = true;
    const confirmation = confirmPromiseRef.current;
    void confirmation.then((result) => {
      if (!active) return;
      setState({ status: "done", paymentId: result.paymentId, orderId: result.orderId, result: result.status, amount: expected.amount });
    }).catch((reason: unknown) => {
      if (!active) return;
      if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") {
        confirmPromiseRef.current = null;
        confirmKeyRef.current = null;
        markAnonymous();
        return;
      }
      setState({ status: "error", message: reason instanceof ApiError ? reason.message : "결제 확인을 완료하지 못했습니다." });
    });
    return () => { active = false; };
  }, [callbackReady, executeWithCsrf, markAnonymous, paymentKey, providerOrderId, status, urlAmount]);

  if (!callbackReady || status === "loading") return <LoadingState>결제를 확인하고 있습니다.</LoadingState>;
  if (status === "anonymous") {
    if (!hasCallback) return <ErrorState title="결제 확인 정보를 찾을 수 없습니다." message="주문 내역에서 결제 상태를 확인해 주세요."><Link className="button button-secondary" href="/orders">주문 내역 확인</Link></ErrorState>;
    return <ErrorState title="로그인이 필요합니다." message="결제 확인을 이어서 처리하려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/checkout/success")}>로그인 후 결제 확인</Link></ErrorState>;
  }
  if (status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (state.status === "loading") return <LoadingState>결제를 확인하고 있습니다.</LoadingState>;
  if (state.status === "error") return <ErrorState title="결제를 확인하지 못했습니다." message={state.message}><Link className="button button-secondary" href="/orders">주문 내역 확인</Link></ErrorState>;
  if (state.result === "SUCCEEDED") return <section className="section-card payment-result"><p className="eyebrow">결제 완료</p><h1>결제가 완료되었습니다.</h1><p>{formatPrice(state.amount)} 결제가 승인되었습니다.</p><p className="field-help">주문 상세에서 결제와 배송 상태를 확인할 수 있어요.</p><Link className="button button-primary" href={`/orders/${state.orderId}`}>주문 상세 보기</Link></section>;
  if (state.result === "UNKNOWN") return <section className="section-card payment-result"><p className="eyebrow">결제 확인 필요</p><h1>결제 결과를 확인하고 있습니다.</h1><p>중복 결제를 막기 위해 결제를 다시 요청하지 않았습니다. 잠시 후 주문 상태를 확인해 주세요.</p><Link className="button button-primary" href={`/orders/${state.orderId}`}>주문 상태 확인</Link></section>;
  return <section className="section-card payment-result"><p className="eyebrow">결제 실패</p><h1>결제가 완료되지 않았습니다.</h1><p>결제에 실패했습니다. 장바구니와 주문 상태를 확인한 뒤 다시 시도해 주세요.</p><Link className="button button-secondary" href={`/orders/${state.orderId}`}>주문 상세 보기</Link></section>;
}

export default function CheckoutSuccessPage() {
  return <Suspense fallback={<LoadingState>결제 결과를 불러오고 있습니다.</LoadingState>}><SuccessContent /></Suspense>;
}
