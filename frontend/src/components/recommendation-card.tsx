"use client";

import Link from "next/link";
import type { RecommendationItem } from "@/lib/final-product-api";
import { userFacingCatalogLabel } from "@/lib/frontend-utils";
import { recommendationStrategyLabel } from "@/lib/recommendation";

export function RecommendationCard({ item, onClick }: { item: RecommendationItem; onClick?: () => void }) {
  const name = userFacingCatalogLabel(item.name, "반려동물 상품");
  const category = item.category ? userFacingCatalogLabel(item.category.name, "상품") : null;
  const badge = recommendationStrategyLabel(item.strategy);
  return <Link className="recommendation-card" href={`/products/${item.productId}`} onClick={onClick}>
    {item.thumbnailUrl ? <img src={item.thumbnailUrl} alt={`${name} 상품 이미지`} loading="lazy" /> : <span className="image-placeholder" aria-hidden="true"><span className="image-placeholder-mark" aria-hidden="true">P</span></span>}
    <div>
      <div className="card-meta">{category ? <span className="tag">{category}</span> : null}{badge ? <span className="tag tag-positive">{badge}</span> : null}</div>
      <h3>{name}</h3>
      {item.shortDescription ? <p>{userFacingCatalogLabel(item.shortDescription, "상품 특징을 확인해 보세요.")}</p> : null}
      <p className="recommendation-reason">{userFacingCatalogLabel(item.reason, "현재 상품 정보에 맞는 추천입니다.")}</p>
      <span className="card-link">상품 보기 →</span>
    </div>
  </Link>;
}

export function RecommendationGrid({ requestId, products, onClick }: { requestId: string; products: RecommendationItem[]; onClick?: (item: RecommendationItem, requestId: string) => void }) {
  return <div className="recommendation-grid">{products.map((item) => <RecommendationCard key={item.productId} item={item} onClick={() => onClick?.(item, requestId)} />)}</div>;
}
