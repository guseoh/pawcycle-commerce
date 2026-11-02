"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { productApi, type ProductSummary } from "@/lib/api";
import { CatalogProductCard } from "./catalog-product-card";
import { ErrorState, CatalogSkeleton } from "./async-state";

/** The catalog owns brand/price/availability; recommendation payloads do not include prices. */
export function HomeCatalog() {
  const [products, setProducts] = useState<ProductSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    void productApi.list({ page: 0, size: 8, sort: "RECOMMENDED" }).then(response => {
      if (active) setProducts((response.items ?? response.products ?? []).slice(0, 8));
    }).catch(() => { if (active) setError("상품을 불러오지 못했습니다."); });
    return () => { active = false; };
  }, [retry]);
  return <section className="home-catalog" aria-labelledby="home-catalog-title"><div className="section-title"><div><p className="eyebrow">상품 탐색</p><h2 id="home-catalog-title">상품 살펴보기</h2></div><Link className="text-link" href="/products">전체 상품 보기 →</Link></div>
    {error ? <ErrorState headingLevel={3} title="상품을 불러오지 못했습니다." message={error} onRetry={() => { setError(null); setProducts(null); setRetry(value => value + 1); }} /> : products === null ? <CatalogSkeleton /> : products.length ? <div className="catalog-products-grid" data-layout={products.length <= 6 ? "balanced" : "regular"}>{products.map(product => <CatalogProductCard key={product.productId} product={product} />)}</div> : <div className="empty-callout">상품 살펴보기을 준비하고 있어요. <Link href="/products">전체 상품 보기</Link></div>}
  </section>;
}
