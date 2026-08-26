"use client";

import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError, Category, ProductListResponse, ProductSummary, categoryApi, productApi } from "@/lib/api";
import { formatPetType, formatPrice } from "@/lib/frontend-utils";

type LoadState = { status: "loading" } | { status: "success"; response: ProductListResponse } | { status: "error"; message: string };
type CategoryLoadState = { status: "loading" } | { status: "success"; categories: Category[] } | { status: "error"; message: string };
const DEFAULT_SIZE = 12;

function ProductsContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const q = searchParams.get("q") ?? "";
  const petType = searchParams.get("petType") ?? "";
  const category = searchParams.get("category") ?? "";
  const sort = searchParams.get("sort") ?? "NEWEST";
  const page = Math.max(0, Number(searchParams.get("page") ?? "0") || 0);
  const [retryKey, setRetryKey] = useState(0);
  const [state, setState] = useState<LoadState>({ status: "loading" });
  const [categoryState, setCategoryState] = useState<CategoryLoadState>({ status: "loading" });

  useEffect(() => {
    let active = true;
    productApi.list({ q, petType, category, page, size: DEFAULT_SIZE, sort }).then((response) => {
      if (active) setState({ status: "success", response });
    }).catch((error: unknown) => {
      if (active) setState({ status: "error", message: error instanceof ApiError ? error.message : "상품 목록을 불러오지 못했습니다." });
    });
    return () => { active = false; };
  }, [retryKey, q, petType, category, page, sort]);

  useEffect(() => {
    let active = true;
    void categoryApi.list().then((response) => {
      if (active) setCategoryState({ status: "success", categories: response.items });
    }).catch((error: unknown) => {
      if (active) setCategoryState({ status: "error", message: error instanceof ApiError ? error.message : "카테고리를 불러오지 못했습니다." });
    });
    return () => { active = false; };
  }, []);

  function movePage(nextPage: number) {
    const next = new URLSearchParams(searchParams.toString());
    if (nextPage > 0) next.set("page", String(nextPage)); else next.delete("page");
    router.push(`/products${next.size ? `?${next}` : ""}`);
  }

  const products = state.status === "success" ? (state.response.items ?? state.response.products ?? []) : [];

  return <>
    <header className="page-heading"><p className="eyebrow">상품 탐색</p><h1>우리 아이에게 필요한 상품을 찾아보세요.</h1><p>상품명과 설명을 검색하거나 반려동물·카테고리·정렬로 좁혀볼 수 있어요.</p></header>
    <ProductFilters key={`${q}\u0000${petType}\u0000${category}\u0000${sort}`} q={q} petType={petType} category={category} sort={sort} categoryState={categoryState} />
    {state.status === "loading" ? <LoadingState>상품 목록을 불러오고 있습니다.</LoadingState> : null}
    {state.status === "error" ? <ErrorState title="상품 목록을 불러오지 못했습니다." message={state.message} onRetry={() => { setState({ status: "loading" }); setRetryKey((value) => value + 1); }} /> : null}
    {state.status === "success" ? <>
      <p className="catalog-count" aria-live="polite">총 {state.response.totalElements.toLocaleString()}개 상품</p>
      {products.length === 0 ? <section className="state-panel empty-state"><p className="eyebrow">Empty</p><h1>조건에 맞는 상품이 없습니다.</h1><p>검색어와 필터를 바꾸거나 공개 상품이 준비될 때까지 기다려 주세요.</p></section> : <ProductGrid products={products} />}
      {state.response.totalPages > 1 ? <nav className="pagination-row" aria-label="상품 목록 페이지"><button className="button button-secondary" type="button" disabled={state.response.page <= 0} onClick={() => movePage(state.response.page - 1)}>이전</button><span>{state.response.page + 1} / {state.response.totalPages}</span><button className="button button-secondary" type="button" disabled={state.response.page + 1 >= state.response.totalPages} onClick={() => movePage(state.response.page + 1)}>다음</button></nav> : null}
    </> : null}
  </>;
}

function ProductGrid({ products }: { products: ProductSummary[] }) {
  return <section aria-label="상품 목록" className="product-grid">{products.map((product) => {
    return <article className="product-card" key={product.productId}>
      <Link className="product-card-media" href={`/products/${product.productId}`} aria-label={`${product.name} 상품 상세 보기`}>
        {product.thumbnailUrl ? <img className="product-thumbnail" src={product.thumbnailUrl} alt={`${product.name} 상품 이미지`} loading="lazy" /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}
      </Link>
      <div className="product-card-copy">
        <p className="product-card-meta">{formatPetType(product.petType)} · {product.category.name}</p>
        <h2><Link href={`/products/${product.productId}`}>{product.name}</Link></h2>
        <strong className="price-heading">{product.representativePrice === null ? "가격 준비 중" : formatPrice(product.representativePrice)}</strong>
        <p className={`product-availability ${product.purchasable ? "is-available" : "is-unavailable"}`}>{product.purchasable ? "구매 가능" : "현재 품절"}</p>
        <p className="product-description">{product.shortDescription}</p>
        <div className="card-actions"><Link className="button button-secondary" href={`/products/${product.productId}`}>상세 보기</Link></div>
      </div>
    </article>;
  })}</section>;
}

function ProductFilters({ q, petType, category, sort, categoryState }: { q: string; petType: string; category: string; sort: string; categoryState: CategoryLoadState }) {
  const router = useRouter();
  const [draftQ, setDraftQ] = useState(q); const [draftPetType, setDraftPetType] = useState(petType); const [draftCategory, setDraftCategory] = useState(category); const [draftSort, setDraftSort] = useState(sort);
  function applyFilters(event: React.FormEvent<HTMLFormElement>) { event.preventDefault(); const next = new URLSearchParams(); if (draftQ.trim()) next.set("q", draftQ.trim()); if (draftPetType) next.set("petType", draftPetType); if (draftCategory) next.set("category", draftCategory); if (draftSort !== "NEWEST") next.set("sort", draftSort); router.push(`/products${next.size ? `?${next}` : ""}`); }
  return <form className="catalog-filters" onSubmit={applyFilters}>
    <label className="form-field search-field">상품 검색<input className="input" placeholder="상품명 또는 설명으로 검색" value={draftQ} onChange={(event) => setDraftQ(event.target.value)} /></label>
    <label className="form-field">반려동물<select className="input" value={draftPetType} onChange={(event) => setDraftPetType(event.target.value)}><option value="">전체</option><option value="DOG">강아지</option><option value="CAT">고양이</option></select></label>
    <label className="form-field">카테고리<select className="input" value={draftCategory} disabled={categoryState.status !== "success"} onChange={(event) => setDraftCategory(event.target.value)}>{categoryState.status === "success" ? <><option value="">전체</option>{categoryState.categories.map((item) => <option key={item.categoryId} value={item.slug}>{item.name}</option>)}</> : <option value={draftCategory}>{draftCategory ? "현재 선택 카테고리" : categoryState.status === "loading" ? "카테고리를 불러오는 중" : "카테고리를 불러올 수 없습니다"}</option>}</select>{categoryState.status === "loading" ? <span className="field-help" role="status">카테고리를 불러오는 중입니다.</span> : null}{categoryState.status === "error" ? <span className="field-error" role="alert">{categoryState.message} 카테고리 필터를 사용할 수 없습니다.</span> : null}</label>
    <label className="form-field">정렬<select className="input" value={draftSort} onChange={(event) => setDraftSort(event.target.value)}><option value="NEWEST">최신순</option><option value="PRICE_ASC">낮은 가격순</option><option value="PRICE_DESC">높은 가격순</option></select></label>
    <div className="button-row"><button className="button button-primary" type="submit">검색</button><button className="button button-secondary" type="button" onClick={() => router.push("/products")}>초기화</button></div>
  </form>;
}

export default function ProductsPage() { return <Suspense fallback={<LoadingState>상품 목록을 준비하고 있습니다.</LoadingState>}><ProductsContent /></Suspense>; }
