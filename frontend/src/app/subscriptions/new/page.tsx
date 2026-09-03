import { Suspense } from "react";
import { SubscriptionStart } from "@/components/subscription-start";
import { LoadingState } from "@/components/async-state";

export default function SubscriptionStartPage() {
  return <Suspense fallback={<LoadingState>구독 시작 정보를 준비하고 있습니다.</LoadingState>}><SubscriptionStart basePath="/subscriptions" /></Suspense>;
}
