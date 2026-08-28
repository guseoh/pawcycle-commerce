"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { LoadingState } from "@/components/async-state";
import { ComparisonScreen } from "@/components/comparison-screen";

function CompareContent() {
  const searchParams = useSearchParams();
  return <ComparisonScreen productIdValues={searchParams.getAll("productId")} />;
}

export default function ComparePage() {
  return <Suspense fallback={<LoadingState>상품 비교를 준비하고 있습니다.</LoadingState>}><CompareContent /></Suspense>;
}
