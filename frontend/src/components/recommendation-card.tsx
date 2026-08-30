"use client";

import Link from "next/link";
import { CatalogImage } from "./catalog-product-card";
import type { RecommendationItem } from "@/lib/final-product-api";
import { userFacingCatalogLabel } from "@/lib/frontend-utils";
import { recommendationStrategyLabel } from "@/lib/recommendation";

export function RecommendationCard({ item, onClick }: { item: RecommendationItem; onClick?: () => void }) {
  const name = userFacingCatalogLabel(item.name, "반려동물 상품");
  const category = item.category ? userFacingCatalogLabel(item.category.name, "상품") : null;
  const badge = recommendationStrategyLabel(item.strategy);
  const reason = item.reason?.trim();
  const showReason = Boolean(reason && reason !== "현재 구매 가능한 상품입니다.");
  return <Link className="recommendation-card" href={`/products/${item.productId}`} onClick={onClick}>
    <div className="recommendation-image"><CatalogImage src={item.thumbnailUrl} alt={`${name} 상품 이미지`} /></div>
    <div>
      <div className="card-meta">{category ? <span className="tag">{category}</span> : null}{badge ? <span className="tag tag-positive">{badge}</span> : null}</div>
      <h3>{name}</h3>
      {item.shortDescription ? <p>{item.shortDescription}</p> : null}
      {showReason ? <p className="recommendation-reason">{reason}</p> : null}
    </div>
  </Link>;
}

export function RecommendationGrid({ requestId, products, onClick }: { requestId: string; products: RecommendationItem[]; onClick?: (item: RecommendationItem, requestId: string) => void }) {
  return <div className="recommendation-grid">{products.map((item) => <RecommendationCard key={item.productId} item={item} onClick={() => onClick?.(item, requestId)} />)}</div>;
}
