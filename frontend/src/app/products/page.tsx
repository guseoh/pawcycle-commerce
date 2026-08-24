"use client";

import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError, ProductSummary, productApi } from "@/lib/api";
import { formatPetType, formatPrice } from "@/lib/frontend-utils";

type LoadState =
  | { status: "loading" }
  | { status: "success"; products: ProductSummary[] }
  | { status: "error"; message: string };

function ProductsContent() {
  const searchParams = useSearchParams();
  const q = searchParams.get("q") ?? ""; const petType = searchParams.get("petType") ?? ""; const category = searchParams.get("category") ?? "";
  const [retryKey, setRetryKey] = useState(0);
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let active = true;
    productApi
      .list({ q, petType, category })
      .then((response) => {
        if (active) setState({ status: "success", products: response.products });
      })
      .catch((error: unknown) => {
        if (!active) return;
        setState({
          status: "error",
          message:
            error instanceof ApiError
              ? error.message
              : "상품 목록을 불러오지 못했습니다.",
        });
      });
    return () => {
      active = false;
    };
  }, [retryKey, q, petType, category]);

  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">Public catalog</p>
        <h1>함께 오래 먹을 사료를 찾아보세요.</h1>
        <p>
          상품과 SKU별 표시 가격, 구독 가능한 옵션을 로그인 없이 확인할 수 있습니다.
          상품명·설명, 반려동물 타입, 카테고리로 함께 검색할 수 있습니다.
        </p>
      </header>

      <ProductFilters key={`${q}\u0000${petType}\u0000${category}`} q={q} petType={petType} category={category} />

      {state.status === "loading" ? (
        <LoadingState>상품 목록을 불러오고 있습니다.</LoadingState>
      ) : null}

      {state.status === "error" ? (
        <ErrorState
          title="상품 목록을 불러오지 못했습니다."
          message={state.message}
          onRetry={() => {
            setState({ status: "loading" });
            setRetryKey((value) => value + 1);
          }}
        />
      ) : null}

      {state.status === "success" && state.products.length === 0 ? (
        <section className="state-panel empty-state">
          <p className="eyebrow">Empty</p>
          <h1>지금 확인할 수 있는 상품이 없습니다.</h1>
          <p>새로운 공개 상품이 준비되면 이곳에 표시됩니다.</p>
        </section>
      ) : null}

      {state.status === "success" && state.products.length > 0 ? (
        <section aria-label="상품 목록" className="product-grid">
          {state.products.map((product) => (
            <article className="product-card" key={product.productId}>
              <div className="card-meta">
                <span className="tag">대상: {formatPetType(product.petType)}</span>
                <span className="tag">{product.category.name}</span>
              </div>
              <h2>{product.name}</h2>
              <p>{product.shortDescription}</p>
              {product.thumbnailUrl ? (
                <img className="product-thumbnail" src={product.thumbnailUrl} alt="" />
              ) : (
                <p className="field-help">대표 이미지가 준비되지 않았습니다.</p>
              )}
              <div>
                <strong>SKU별 표시 가격</strong>
                {product.skuPriceSummary.skuPrices.length > 0 ? (
                  <ul className="price-list">
                    {product.skuPriceSummary.skuPrices.map((sku) => (
                      <li key={sku.skuId}>
                        {sku.skuName} · {formatPrice(sku.price)}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="field-help">표시할 SKU가 없습니다.</p>
                )}
              </div>
              <p>
                <span className={`tag ${product.hasSubscribableSku ? "tag-positive" : "tag-muted"}`}>
                  {product.hasSubscribableSku
                    ? "구독 가능한 옵션 있음"
                    : "현재 구독 가능한 옵션 없음"}
                </span>
              </p>
              <div className="card-actions">
                <Link className="button button-primary" href={`/products/${product.productId}`}>
                  상세 보기
                </Link>
              </div>
            </article>
          ))}
        </section>
      ) : null}
    </>
  );
}

function ProductFilters({ q, petType, category }: { q: string; petType: string; category: string }) {
  const router = useRouter();
  const [draftQ, setDraftQ] = useState(q);
  const [draftPetType, setDraftPetType] = useState(petType);
  const [draftCategory, setDraftCategory] = useState(category);
  function applyFilters(event: React.FormEvent<HTMLFormElement>) { event.preventDefault(); const next = new URLSearchParams(); if (draftQ.trim()) next.set("q", draftQ.trim()); if (draftPetType) next.set("petType", draftPetType); if (draftCategory.trim()) next.set("category", draftCategory.trim()); router.push(`/products${next.size ? `?${next}` : ""}`); }
  return <form className="section-card" onSubmit={applyFilters}>
    <label className="form-field">검색어<input className="input" value={draftQ} onChange={(event) => setDraftQ(event.target.value)} /></label>
    <label className="form-field">반려동물 타입<select className="input" value={draftPetType} onChange={(event) => setDraftPetType(event.target.value)}><option value="">전체</option><option value="DOG">강아지</option><option value="CAT">고양이</option></select></label>
    <label className="form-field">카테고리 slug<input className="input" value={draftCategory} onChange={(event) => setDraftCategory(event.target.value)} /></label>
    <div className="button-row"><button className="button button-primary" type="submit">검색</button><button className="button button-secondary" type="button" onClick={() => router.push("/products")}>초기화</button></div>
  </form>;
}

export default function ProductsPage() {
  return <Suspense fallback={<LoadingState>상품 목록을 준비하고 있습니다.</LoadingState>}><ProductsContent /></Suspense>;
}
