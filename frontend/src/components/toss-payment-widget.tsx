"use client";

import { loadTossPayments, type TossPaymentsWidgets } from "@tosspayments/tosspayments-sdk";
import { useEffect, useRef, useState } from "react";
import { formatPrice } from "@/lib/frontend-utils";
import { isTossTestClientKey, saveTossCheckoutContext } from "@/lib/toss-payment";
import type { CheckoutResult } from "@/lib/commerce-final-api";

const clientKey = process.env.NEXT_PUBLIC_TOSS_TEST_CLIENT_KEY;

export function TossPaymentWidget({ checkout }: { checkout: CheckoutResult }) {
  const testPaymentAvailable = checkout.tossTestEnabled && isTossTestClientKey(clientKey);
  const widgetsRef = useRef<TossPaymentsWidgets | null>(null);
  const [phase, setPhase] = useState<"unavailable" | "loading" | "ready" | "error">(() => testPaymentAvailable ? "loading" : "unavailable");
  const [error, setError] = useState<string | null>(null);
  const [requesting, setRequesting] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    saveTossCheckoutContext({ providerOrderId: checkout.providerOrderId, orderName: checkout.orderName, amount: checkout.amount });
    if (!testPaymentAvailable || !isTossTestClientKey(clientKey)) return;
    let active = true;
    let paymentMethodWidget: { destroy: () => void } | null = null;
    let agreementWidget: { destroy: () => void } | null = null;
    const customerKey = `pc_${crypto.randomUUID()}`;
    void loadTossPayments(clientKey).then((tossPayments) => {
      if (!active) return;
      const widgets = tossPayments.widgets({ customerKey });
      widgetsRef.current = widgets;
      return widgets.setAmount({ currency: "KRW", value: checkout.amount }).then(async () => {
        const paymentMethods = await widgets.renderPaymentMethods({ selector: "#toss-payment-methods" });
        const agreement = await widgets.renderAgreement({ selector: "#toss-payment-agreement" });
        paymentMethodWidget = paymentMethods;
        agreementWidget = agreement;
        if (active) setPhase("ready");
      });
    }).catch(() => {
      if (!active) return;
      setPhase("error");
      setError("결제 화면을 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    });
    return () => {
      active = false;
      widgetsRef.current = null;
      paymentMethodWidget?.destroy();
      agreementWidget?.destroy();
    };
  }, [attempt, checkout.amount, checkout.orderName, checkout.providerOrderId, testPaymentAvailable]);

  async function requestPayment() {
    const widgets = widgetsRef.current;
    if (!widgets || requesting) return;
    setRequesting(true);
    setError(null);
    try {
      await widgets.requestPayment({
        orderId: checkout.providerOrderId,
        orderName: checkout.orderName,
        successUrl: `${window.location.origin}/checkout/success`,
        failUrl: `${window.location.origin}/checkout/fail`,
      });
    } catch {
      setRequesting(false);
      setError("결제를 취소했거나 결제 창을 열지 못했습니다. 다시 시도해 주세요.");
    }
  }

  if (phase === "unavailable") {
    return <div className="provider-block" role="status"><strong>결제 준비 전</strong><p>현재 결제를 진행할 수 없습니다. 결제 서비스가 제공되기 전까지는 이 화면에서 결제를 진행할 수 없어요. 주문은 결제 대기 상태로 남습니다.</p></div>;
  }

  return <section className="toss-payment-panel" aria-labelledby="toss-payment-title">
    <div className="section-title"><div><p className="eyebrow">결제</p><h2 id="toss-payment-title">결제 수단 선택</h2></div><strong>{formatPrice(checkout.amount)}</strong></div>
    {phase === "loading" ? <p className="field-help" role="status">결제 화면을 준비하고 있습니다.</p> : null}
    {phase === "error" ? <div className="error-summary" role="alert"><p>{error}</p><button className="button button-secondary" type="button" onClick={() => { setError(null); setPhase("loading"); setAttempt((value) => value + 1); }}>결제 화면 다시 준비</button></div> : null}
    <div id="toss-payment-methods" aria-label="결제 수단" />
    <div id="toss-payment-agreement" aria-label="결제 이용약관" />
    {error && phase === "ready" ? <p className="field-error" role="alert">{error}</p> : null}
    <button className="button button-primary" type="button" disabled={phase !== "ready" || requesting} onClick={() => void requestPayment()}>{requesting ? "결제 요청 중" : "결제하기"}</button>
  </section>;
}
