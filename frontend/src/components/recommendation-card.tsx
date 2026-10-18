"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { CatalogImage, CatalogPrice } from "./catalog-product-card";
import { loadRecommendationMetadata, RECOMMENDATION_VISIBLE_LIMIT, type RecommendationMetadata } from "@/lib/recommendation-metadata";
import type { RecommendationItem } from "@/lib/recommendation-api";
import { userFacingCatalogLabel } from "@/lib/frontend-utils";
import { recommendationStrategyLabel } from "@/lib/recommendation";

export function RecommendationCard({ item, metadata, metadataLoaded = false, onClick }: { item: RecommendationItem; metadata?: RecommendationMetadata; metadataLoaded?: boolean; onClick?: () => void }) {
  const name = userFacingCatalogLabel(item.name, "반려동물 상품");
  const category = item.category ? userFacingCatalogLabel(item.category.name, "상품") : null;
  const badge = recommendationStrategyLabel(item.strategy);
  const reason = item.reason?.trim();
  const showReason = Boolean(reason && reason !== "현재 구매 가능한 상품입니다.");
  return <article className="catalog-product-card recommendation-card" data-product-id={item.productId}><Link className="recommendation-card-link" href={`/products/${item.productId}`} onClick={onClick}>
    <div className="product-card-media recommendation-image"><CatalogImage src={item.thumbnailUrl} alt="" className="product-thumbnail" /></div>
    <div className="catalog-card-copy recommendation-card-copy">
      {metadata?.brandName ? <p className="catalog-brand">{metadata.brandName}</p> : null}
      <h3>{name}</h3>
      <div className="catalog-price-block"><CatalogPrice price={metadata?.price ?? null} />{metadata?.priceCaption ? <span className="catalog-price-caption">{metadata.priceCaption}</span> : null}</div>
      {metadata && metadata.reviewCount > 0 && metadata.averageRating !== null ? <p className="catalog-rating"><span aria-hidden="true">★ </span><span className="sr-only">평점 </span>{metadata.averageRating}<span className="catalog-rating-count"> · 리뷰 {metadata.reviewCount.toLocaleString()}개</span></p> : null}
      <div className="card-meta">{metadata?.subscribable ? <span className="tag">정기배송 가능</span> : null}{category ? <span className="tag">{category}</span> : null}{badge ? <span className="tag tag-positive">{badge}</span> : null}</div>
      {!metadata ? <p className="recommendation-metadata-status">{metadataLoaded ? "가격·브랜드 정보는 상세에서 확인해 주세요." : "상품 정보를 확인 중입니다."}</p> : null}
      {showReason ? <p className="recommendation-reason">{reason}</p> : null}
    </div>
  </Link></article>;
}

export function RecommendationGrid({ requestId, products, onClick }: { requestId: string; products: RecommendationItem[]; onClick?: (item: RecommendationItem, requestId: string) => void }) {
  const visible = useMemo(() => products.slice(0, RECOMMENDATION_VISIBLE_LIMIT), [products]);
  const key = JSON.stringify([requestId, visible.map((item) => item.productId)]);
  const [metadata, setMetadata] = useState<{ key: string; values: Map<number, RecommendationMetadata> } | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    void loadRecommendationMetadata(visible.map((item) => item.productId), controller.signal).then((values) => {
      if (!controller.signal.aborted) setMetadata({ key, values });
    });
    return () => controller.abort();
  }, [visible, key]);
  return <div className="recommendation-grid" data-request-id={requestId}>{visible.map((item) => <RecommendationCard key={item.productId} item={item} metadata={metadata?.key === key ? metadata.values.get(item.productId) : undefined} metadataLoaded={metadata?.key === key} onClick={() => onClick?.(item, requestId)} />)}</div>;
}
