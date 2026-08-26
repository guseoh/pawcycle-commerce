"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect } from "react";
import { LoadingState } from "@/components/async-state";

function FailContent() {
  const params = useSearchParams();
  const code = params.get("code");
  const message = code === "USER_CANCEL" || code === "PAY_PROCESS_CANCELED" ? "결제를 취소했습니다." : "결제가 승인되지 않았습니다.";
  useEffect(() => { window.history.replaceState({}, "", "/checkout/fail"); }, []);
  return <section className="section-card payment-result"><p className="eyebrow">결제 취소·실패</p><h1>{message}</h1><p>주문은 결제 완료로 처리되지 않았습니다. 결제 수단을 확인한 뒤 다시 시도해 주세요.</p><div className="button-row"><Link className="button button-primary" href="/checkout">결제 다시 시도</Link><Link className="button button-secondary" href="/orders">주문 내역</Link></div></section>;
}

export default function CheckoutFailPage() {
  return <Suspense fallback={<LoadingState>결제 결과를 불러오고 있습니다.</LoadingState>}><FailContent /></Suspense>;
}
