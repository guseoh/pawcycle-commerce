"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { finalProductApi, type ProductComparisonFact, type ProductComparisonResponse } from "@/lib/final-product-api";
import { comparisonIdsFromKey, comparisonIdsKey, parseComparisonIds } from "@/lib/comparison-selection";
import { formatPrice } from "@/lib/frontend-utils";

function factValue(fact: ProductComparisonFact, row: string): React.ReactNode {
  switch (row) {
    case "brand": return fact.brand;
    case "category": return fact.category;
    case "representativePrice": return fact.representativePrice === null ? "가격 준비 중" : formatPrice(fact.representativePrice);
    case "compareAtPrice": return fact.compareAtPrice === null ? "-" : formatPrice(fact.compareAtPrice);
    case "discountRate": return fact.discountRate === null ? "-" : `${fact.discountRate}%`;
    case "averageRating": return fact.averageRating === null ? "리뷰 없음" : `${fact.averageRating} / 5`;
    case "reviewCount": return `${fact.reviewCount}개`;
    case "subscriptionEligible": return fact.subscriptionEligible ? "가능" : "불가";
    case "purchasable": return fact.purchasable ? "구매 가능" : "현재 구매 불가";
    case "facets": return fact.facets.length ? fact.facets.join(", ") : "-";
    default: return "-";
  }
}

const ROWS = [
  ["brand", "브랜드"], ["category", "카테고리"], ["representativePrice", "대표 가격"], ["compareAtPrice", "정가"],
  ["discountRate", "할인"], ["averageRating", "평점"], ["reviewCount", "리뷰 수"], ["subscriptionEligible", "정기배송"],
  ["purchasable", "구매 가능 여부"], ["facets", "Facet"],
] as const;

export function ComparisonScreen({ productIdValues }: { productIdValues: readonly string[] }) {
  const parsed = parseComparisonIds(productIdValues);
  const productIdKey = comparisonIdsKey(productIdValues);
  const [state, setState] = useState<{ status: "loading" } | { status: "success"; data: ProductComparisonResponse } | { status: "error"; message: string }>(() => parsed.error ? { status: "error", message: parsed.error } : { status: "loading" });

  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(() => {
      if (!active) return;
      const current = parseComparisonIds(comparisonIdsFromKey(productIdKey));
      if (current.error) { setState({ status: "error", message: current.error }); return; }
      setState({ status: "loading" });
      void finalProductApi.compare(current.ids).then((data) => { if (active) setState({ status: "success", data }); }).catch((error: unknown) => {
        if (active) setState({ status: "error", message: error instanceof ApiError && error.code === "PRODUCT_NOT_FOUND" ? "비교할 상품을 찾을 수 없습니다." : error instanceof Error ? error.message : "상품 비교 정보를 불러오지 못했습니다." });
      });
    }, 0);
    return () => { active = false; window.clearTimeout(timer); };
  }, [productIdKey]);

  return <section className="comparison-screen"><header className="page-heading"><p className="eyebrow">Compare products</p><h1>상품 비교</h1><p>상품 정보를 나란히 확인하고 우리 아이에게 맞는 선택을 찾아보세요.</p></header>
    {state.status === "loading" ? <LoadingState>상품 비교 정보를 불러오고 있습니다.</LoadingState> : null}
    {state.status === "error" ? <ErrorState title="상품을 비교할 수 없습니다." message={state.message}><Link className="button button-secondary" href="/products">상품 목록으로</Link></ErrorState> : null}
    {state.status === "success" ? <ComparisonResult data={state.data} /> : null}
  </section>;
}

function ComparisonResult({ data }: { data: ProductComparisonResponse }) {
  return <>
    <div className="comparison-table-wrap"><table className="comparison-table"><caption className="sr-only">선택한 상품의 핵심 정보 비교</caption><thead><tr><th scope="col">비교 항목</th>{data.products.map((product) => <th scope="col" key={product.productId}>{product.thumbnailUrl ? <img src={product.thumbnailUrl} alt={`${product.name} 상품 이미지`} loading="lazy" /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}<Link href={`/products/${product.productId}`}>{product.name}</Link></th>)}</tr></thead><tbody><tr><th scope="row">상품</th>{data.products.map((product) => <td key={product.productId}><Link href={`/products/${product.productId}`}>{product.name}</Link></td>)}</tr>{ROWS.map(([key, label]) => <tr key={key}><th scope="row">{label}</th>{data.products.map((product) => <td key={product.productId}>{factValue(product, key)}</td>)}</tr>)}</tbody></table></div>
    <div className="comparison-mobile">{ROWS.map(([key,label]) => <details key={key} open><summary>{label}</summary><dl>{data.products.map(product => <div key={product.productId}><dt><Link href={`/products/${product.productId}`}>{product.name}</Link></dt><dd>{factValue(product,key)}</dd></div>)}</dl></details>)}</div>
    {data.aiStatus === "AVAILABLE" && data.aiSummary ? <aside className="comparison-ai-summary" aria-labelledby="comparison-ai-title"><p className="eyebrow">AI summary</p><h2 id="comparison-ai-title">비교 요약</h2><p>{data.aiSummary}</p></aside> : <p className="comparison-ai-fallback">비교 요약을 준비하지 못했어요. 위 상품 정보를 기준으로 비교해 주세요.</p>}
  </>;
}
