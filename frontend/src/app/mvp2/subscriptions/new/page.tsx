import { Suspense } from "react";
import { Mvp2SubscriptionStart } from "@/components/mvp2-subscription-start";
import { LoadingState } from "@/components/async-state";

export default function NewMvp2SubscriptionPage() { return <Suspense fallback={<LoadingState>구독 시작 정보를 준비하고 있습니다.</LoadingState>}><Mvp2SubscriptionStart /></Suspense>; }
