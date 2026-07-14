"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError, ProductSummary, productApi } from "@/lib/api";
import { formatPetType, formatPrice } from "@/lib/frontend-utils";

type LoadState =
  | { status: "loading" }
  | { status: "success"; products: ProductSummary[] }
  | { status: "error"; message: string };

export default function ProductsPage() {
  const [retryKey, setRetryKey] = useState(0);
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let active = true;
    productApi
      .list()
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
  }, [retryKey]);

  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">Public catalog</p>
        <h1>함께 오래 먹을 사료를 찾아보세요.</h1>
        <p>
          상품과 SKU별 표시 가격, 구독 가능한 옵션을 로그인 없이 확인할 수 있습니다.
          검색·필터 없이 공개 상품을 승인된 순서로 보여드립니다.
        </p>
      </header>

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
                <span className="tag">상품 #{product.productId}</span>
                <span className="tag">대상: {formatPetType(product.petType)}</span>
              </div>
              <h2>{product.name}</h2>
              <p>{product.shortDescription}</p>
              {product.thumbnailUrl ? (
                <a className="image-link" href={product.thumbnailUrl} target="_blank" rel="noreferrer">
                  대표 이미지 보기
                </a>
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
