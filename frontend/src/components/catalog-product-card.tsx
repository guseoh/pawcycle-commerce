"use client";

import Link from "next/link";
import { useState } from "react";
import type { ProductSummary } from "@/lib/api";
import { formatPetType, formatPrice } from "@/lib/frontend-utils";

export function CatalogImage({ src, alt, className = "", eager = false }: { src: string | null; alt: string; className?: string; eager?: boolean }) {
  const [failedSource, setFailedSource] = useState<string | null>(null);
  return src && failedSource !== src
    ? <img src={src} alt={alt} className={className} loading={eager ? "eager" : "lazy"} onError={() => setFailedSource(src)} />
    : <span className={`image-placeholder ${className}`} role="img" aria-label={`${alt} — 이미지 준비 중`}>PawCycle</span>;
}

export function CatalogPrice({ price, compareAtPrice, discountRate }: { price: number | null; compareAtPrice?: number | null; discountRate?: number | null }) {
  return <span className="catalog-price">
    {discountRate != null && discountRate > 0 ? <span className="catalog-discount"><span className="sr-only">할인율 </span>{discountRate}%</span> : null}
    <strong>{price === null ? "가격 준비 중" : formatPrice(price)}</strong>
    {compareAtPrice != null ? <del><span className="sr-only">기존 가격 </span>{formatPrice(compareAtPrice)}</del> : null}
  </span>;
}

export function CatalogProductCard({ product, compareSelected = false, onCompare }: { product: ProductSummary; compareSelected?: boolean; onCompare?: () => void }) {
  return <article className="catalog-product-card">
    <Link href={`/products/${product.productId}`} aria-label={`${product.name} 상품 상세 보기`}>
      <div className="product-card-media"><CatalogImage src={product.thumbnailUrl} alt={`${product.name} 상품 이미지`} className="product-thumbnail" />{!product.purchasable ? <span className="catalog-sold-out">품절</span> : null}</div>
      <div className="catalog-card-copy">
        {product.brand ? <p className="catalog-brand">{product.brand.name}</p> : null}
        <p className="product-card-meta">{formatPetType(product.petType)} · {product.category.name}</p>
        <h3>{product.name}</h3>
        <CatalogPrice price={product.representativePrice} compareAtPrice={product.compareAtPrice} discountRate={product.discountRate} />
        <p className="catalog-rating">{product.averageRating != null ? <><span aria-hidden="true">★ </span>평점 {product.averageRating} · 리뷰 {product.reviewCount}</> : "리뷰 없음"}</p>
        <div className="card-meta">{product.hasSubscribableSku ? <span className="tag">정기배송 가능</span> : null}<span className={`product-availability ${product.purchasable ? "is-available" : "is-unavailable"}`}>{product.purchasable ? "구매 가능" : "현재 품절"}</span></div>
      </div>
    </Link>
    {onCompare ? <button className={`compare-toggle${compareSelected ? " is-selected" : ""}`} type="button" aria-pressed={compareSelected} onClick={onCompare}>{compareSelected ? "비교에서 빼기" : "비교하기"}</button> : null}
  </article>;
}
