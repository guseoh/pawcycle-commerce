"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { formatPrice } from "@/lib/frontend-utils";
import { readTossCheckoutContext, validateTossSuccess } from "@/lib/toss-payment";

function SuccessContent() {
  const auth = useAuth();
  const { status, executeWithCsrf, markAnonymous } = auth;
  const params = useSearchParams();
  const attemptedRef = useRef(false);
  const paymentKey = params.get("paymentKey");
  const providerOrderId = params.get("orderId");
  const urlAmount = params.get("amount");
  const [state, setState] = useState<{ status: "loading" } | { status: "error"; message: string } | { status: "done"; paymentId: number; orderId: number; result: "SUCCEEDED" | "FAILED" | "UNKNOWN"; amount: number }>({ status: "loading" });

  useEffect(() => {
    if (status !== "authenticated" || attemptedRef.current) return;
    attemptedRef.current = true;
    const expected = readTossCheckoutContext();
    const validationError = validateTossSuccess({ paymentKey, providerOrderId, urlAmount }, expected);
    window.history.replaceState({}, "", "/checkout/success");
    if (validationError || !paymentKey || !providerOrderId || !expected) {
      const timer = window.setTimeout(() => setState({ status: "error", message: validationError ?? "결제 주문 정보를 확인할 수 없습니다." }), 0);
      return () => window.clearTimeout(timer);
    }
    let active = true;
    void executeWithCsrf((csrf) => commerceFinalApi.confirmToss(paymentKey, providerOrderId, expected.amount, csrf)).then((result) => {
      if (active) setState({ status: "done", paymentId: result.paymentId, orderId: result.orderId, result: result.status, amount: expected.amount });
    }).catch((reason: unknown) => {
      if (!active) return;
      if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") markAnonymous();
      setState({ status: "error", message: reason instanceof ApiError ? reason.message : "결제 확인을 완료하지 못했습니다." });
    });
    return () => { active = false; };
  }, [executeWithCsrf, markAnonymous, paymentKey, providerOrderId, status, urlAmount]);

  if (status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="결제 확인을 위해 로그인해 주세요."><Link className="button button-primary" href="/login?returnTo=%2Fcheckout%2Fsuccess">로그인</Link></ErrorState>;
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
