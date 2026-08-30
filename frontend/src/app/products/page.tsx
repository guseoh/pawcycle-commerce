"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ErrorState, LoadingState, CatalogSkeleton } from "@/components/async-state";
import { CommerceOverlay } from "@/components/commerce-overlay";
import { formatPetType } from "@/lib/frontend-utils";
import { CatalogProductCard } from "@/components/catalog-product-card";
import { useCatalogDiscovery } from "@/components/catalog-discovery";
import { ApiError, type CatalogDiscovery, type ProductFilters, type ProductListResponse, type ProductSort } from "@/lib/api";
import { catalogHref, catalogMetadata, catalogPriceRangeError, catalogQuery, changeCatalogFilters, interactionContext, parseCatalogFilters, PRODUCT_SORTS } from "@/lib/catalog-filters";
import { loadProductResults } from "@/lib/catalog-products";
import { useAuth } from "@/lib/auth-context";
import { comparisonHref, toggleComparisonId } from "@/lib/comparison-selection";
import { createInteractionEvent, finalProductApi } from "@/lib/final-product-api";

type LoadState = { key: string; status: "success"; response: ProductListResponse } | { key: string; status: "error"; message: string };

function ProductsContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const auth = useAuth();
  const filters = parseCatalogFilters(new URLSearchParams(searchParams.toString()));
  const query = catalogQuery(filters);
  const [retryKey, setRetryKey] = useState(0);
  const [state, setState] = useState<LoadState | null>(null);
  const [filterOpen, setFilterOpen] = useState(false);
  const [filterGroup, setFilterGroup] = useState("all");
  const [compareIds, setCompareIds] = useState<number[]>([]);
  const [compareNames, setCompareNames] = useState<Record<number, string>>({});
  const [compareMessage, setCompareMessage] = useState<string | null>(null);
  const filterButton = useRef<HTMLButtonElement>(null);

  const discovery = useCatalogDiscovery();
  const metadata = discovery.state.status === "success" ? discovery.state.data : null;
  const requestKey = `${query}|${retryKey}`;

  useEffect(() => {
    const requestFilters = parseCatalogFilters(new URLSearchParams(query));
    return loadProductResults(requestFilters,
      (response) => setState({ key: requestKey, status: "success", response }),
      (error) => setState({ key: requestKey, status: "error", message: catalogPriceRangeError(requestFilters) ?? (error instanceof ApiError ? error.message : "상품 목록을 불러오지 못했습니다.") }));
  }, [query, requestKey]);

  const current = state?.key === requestKey ? state : null;
  const products = current?.status === "success" ? (current.response.items ?? current.response.products ?? []) : [];
  const renderedProductIds = products.map((product) => product.productId).join(",");
  useEffect(() => {
    if (current?.status !== "success") return;
    const raw = sessionStorage.getItem("pawcycle.catalog-return");
    if (!raw) return;
    try {
      const saved = JSON.parse(raw) as { query: string; productId: number; scrollY: number };
      if (saved.query !== query) return;
      sessionStorage.removeItem("pawcycle.catalog-return");
      requestAnimationFrame(() => {
        window.scrollTo({ top: saved.scrollY });
        document.querySelector<HTMLElement>(`[data-product-id="${saved.productId}"] .catalog-title-link`)?.focus({ preventScroll: true });
      });
    } catch { sessionStorage.removeItem("pawcycle.catalog-return"); }
  }, [current?.status, query]);
  type InteractionEvent = Parameters<typeof finalProductApi.interactions.send>[0][number];
  const track = (events: Array<InteractionEvent | null>) => {
    if (auth.status !== "authenticated") return;
    const validEvents = events.filter((event): event is InteractionEvent => event !== null);
    if (!validEvents.length) return;
    void auth.executeWithCsrf((csrf) => finalProductApi.interactions.send(validEvents, csrf)).catch(() => undefined);
  };
  useEffect(() => {
    if (current?.status !== "success" || !products.length || auth.status !== "authenticated") return;
    track(products.map((product) => createInteractionEvent({ type: "PRODUCT_IMPRESSION", productId: product.productId, source: "catalog", context: interactionContext(filters) })));
    // Product impressions are emitted once for each rendered catalog response.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current?.key, renderedProductIds, auth.status, auth.memberId]);
  function change(patch: Partial<ProductFilters>) {
    const next = changeCatalogFilters(filters, patch);
    track([createInteractionEvent({ type: "FILTER", source: "catalog-filter", context: interactionContext(next) })]);
    router.push(catalogHref(next));
  }
  function toggleCompare(productId: number, name: string) {
    const result = toggleComparisonId(compareIds, productId);
    if (!result.accepted) { setCompareMessage("상품 비교는 최대 3개까지 선택할 수 있어요."); return; }
    setCompareMessage(null);
    setCompareIds(result.ids);
    setCompareNames((names) => result.reason === "removed" ? Object.fromEntries(Object.entries(names).filter(([id]) => Number(id) !== productId)) : { ...names, [productId]: name });
  }

  return <div className="catalog-page">
    <header className="page-heading"><h1 tabIndex={-1} id="catalog-results-title">{filters.q ? `‘${filters.q}’ 검색 결과` : "전체 상품"}</h1><p>필요한 물품을 편하게 찾아보세요.</p></header>
    <div className="catalog-toolbar"><p aria-live="polite" aria-atomic="true">{current?.status === "success" ? `총 ${current.response.totalElements.toLocaleString()}개 상품` : current?.status === "error" ? "목록을 불러오지 못했습니다" : "상품을 찾고 있습니다…"}</p><div className="button-row"><button ref={filterButton} type="button" className="button button-secondary catalog-filter-toggle" aria-expanded={filterOpen} aria-controls="catalog-filter-panel" onClick={() => { setFilterGroup("all"); setFilterOpen(open => !open); }}>필터 {activeFilterCount(filters) ? `${activeFilterCount(filters)}개` : ""}</button><label className="form-field catalog-sort"><span className="sr-only">상품 정렬</span><select className="input" value={filters.sort} onChange={(event) => change({ sort: event.target.value as ProductSort })}>{PRODUCT_SORTS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label></div></div>
    <div className="catalog-filter-triggers" aria-label="상품 필터">{[["pet","반려동물"],["category","카테고리"],["brand","브랜드"],["price","가격"],["condition","구매 조건"]].map(([group,label]) => <button key={group} type="button" aria-expanded={filterOpen && filterGroup === group} aria-controls="catalog-filter-panel" onClick={event => { filterButton.current = event.currentTarget; setFilterGroup(group); setFilterOpen(!(filterOpen && filterGroup === group)); }}>{label} <span aria-hidden="true">⌄</span></button>)}</div>
    <FilterChips filters={filters} metadata={metadata} onChange={change} onReset={() => { track([createInteractionEvent({ type: "FILTER", source: "catalog-filter", context: interactionContext(parseCatalogFilters(new URLSearchParams())) })]); router.push(catalogHref({ q: filters.q, sort: filters.sort, size: filters.size, page: 0 })); }} />
    <div className="catalog-layout">
      {filterOpen ? <CommerceOverlay key={filterGroup} id="catalog-filter-panel" responsive label="상품 필터" className="catalog-filter-panel" onClose={() => setFilterOpen(false)}>
        <div className="catalog-filter-heading"><h2>상세 필터</h2><button type="button" className="catalog-filter-close icon-button" onClick={() => { setFilterOpen(false); filterButton.current?.focus(); }}>닫기</button></div>
        {discovery.state.status === "loading" ? <p role="status">탐색 정보를 불러오는 중입니다.</p> : null}
        {discovery.state.status === "error" ? <div className="inline-alert" role="alert"><p>{discovery.state.message} 검색과 기본 필터는 계속 사용할 수 있습니다.</p><button className="button button-secondary" onClick={discovery.retry}>탐색 정보 재시도</button></div> : null}
        <CatalogFilterForm key={`${query}|${filterGroup}`} group={filterGroup} filters={filters} metadata={metadata} onApply={(next) => { track([createInteractionEvent({ type: "FILTER", source: "catalog-filter", context: interactionContext(next) })]); router.push(catalogHref({ ...next, page: 0 })); setFilterOpen(false); filterButton.current?.focus(); }} />
      </CommerceOverlay> : null}
      <section className="catalog-results" aria-label="상품 검색 결과" aria-busy={!current} onClickCapture={(event) => { const card = (event.target as HTMLElement).closest<HTMLElement>("[data-product-id]"); const link = (event.target as HTMLElement).closest("a[href^='/products/']"); if (!card || !link) return; sessionStorage.setItem("pawcycle.catalog-return", JSON.stringify({ query, productId: Number(card.dataset.productId), scrollY: window.scrollY })); }}>
        <h2 className="sr-only">상품 목록</h2>
        {!current ? <CatalogSkeleton /> : null}
        {current?.status === "error" ? <ErrorState headingLevel={3} title="상품 목록을 불러오지 못했습니다." message={current.message} onRetry={() => setRetryKey((value) => value + 1)} /> : null}
        {current?.status === "success" ? <>
          {products.length ? <div className="catalog-products-grid">{products.map((product) => <CatalogProductCard key={product.productId} product={product} compareSelected={compareIds.includes(product.productId)} onCompare={() => toggleCompare(product.productId, product.name)} />)}</div> : <div className="state-panel"><h3>{activeFilterCount(filters) ? "찾는 상품이 없어요." : "상품을 준비하고 있어요."}</h3><p>필터를 줄이거나 다른 검색어로 찾아보세요.</p><button className="button button-secondary" onClick={() => router.push("/products")}>전체 상품 보기</button></div>}
          {current.response.totalPages > 1 ? <nav className="pagination-row" aria-label="상품 목록 페이지"><button className="button button-secondary" disabled={current.response.page <= 0} onClick={() => router.push(catalogHref({ ...filters, page: current.response.page - 1 }))}>이전</button><span>{current.response.page + 1} / {current.response.totalPages}</span><button className="button button-secondary" disabled={current.response.page + 1 >= current.response.totalPages} onClick={() => router.push(catalogHref({ ...filters, page: current.response.page + 1 }))}>다음</button></nav> : null}
        </> : null}
      </section>
    </div>
    {compareMessage ? <p className="compare-selection-message" role="status">{compareMessage}</p> : null}
    {compareIds.length > 0 ? <aside className="compare-tray" aria-label="상품 비교 선택"><div><strong>상품 비교 {compareIds.length}/3</strong><span>{compareIds.map((id) => compareNames[id] ?? `상품 #${id}`).join(" · ")}</span></div><div className="button-row"><button className="button button-secondary" type="button" onClick={() => { setCompareIds([]); setCompareNames({}); setCompareMessage(null); }}>전체 해제</button>{compareIds.map((id) => <button className="compare-remove" key={id} type="button" onClick={() => toggleCompare(id, compareNames[id] ?? `상품 #${id}`)} aria-label={`${compareNames[id] ?? `상품 #${id}`} 비교에서 제거`}>×</button>)}<button className="button button-primary" type="button" disabled={compareIds.length < 2} onClick={() => router.push(comparisonHref(compareIds))}>비교하기</button></div></aside> : null}
  </div>;
}

function CatalogFilterForm({ filters, metadata, onApply, group }: { filters: ProductFilters; metadata: CatalogDiscovery | null; onApply: (filters: ProductFilters) => void; group: string }) {
  const [draft, setDraft] = useState(filters);
  const priceError = catalogPriceRangeError(draft);
  const { children, facets } = catalogMetadata(metadata, draft);
  const change = (patch: Partial<ProductFilters>) => setDraft((current) => changeCatalogFilters(current, patch));
  return <form className="catalog-filter-form" onSubmit={(event) => { event.preventDefault(); if (priceError) return; onApply(draft); }}>
    <p className="field-help">적용 전 · 변경한 조건은 적용하기를 눌러야 반영됩니다.</p><div hidden={group !== "all" && group !== "pet"}><fieldset><legend>반려동물</legend><div className="catalog-toggle-row">{[{ value: "", label: "전체" }, { value: "DOG", label: "강아지" }, { value: "CAT", label: "고양이" }].map((item) => <button type="button" aria-pressed={(draft.petType ?? "") === item.value} key={item.value} onClick={() => change({ petType: item.value })}>{item.label}</button>)}</div></fieldset></div>
    <div hidden={group !== "all" && group !== "category"}><label className="form-field">카테고리<select className="input" value={draft.category ?? ""} disabled={!metadata} onChange={(event) => change({ category: event.target.value })}><option value="">전체 카테고리</option>{draft.category && !metadata?.categories.some((item) => item.slug === draft.category) ? <option value={draft.category}>현재 URL 카테고리</option> : null}{metadata?.categories.map((category) => <option key={category.categoryId} value={category.slug}>{category.name}</option>)}</select></label>
    {children.length > 0 ? <label className="form-field">세부 카테고리<select className="input" value={draft.subcategory ?? ""} onChange={(event) => change({ subcategory: event.target.value })}><option value="">전체</option>{draft.subcategory && !children.some((item) => item.slug === draft.subcategory) ? <option value={draft.subcategory}>현재 URL 세부 카테고리</option> : null}{children.map((child) => <option key={child.categoryId} value={child.slug}>{child.name}</option>)}</select></label> : null}
    </div><div hidden={group !== "all" && group !== "brand"}><label className="form-field">브랜드<select className="input" value={draft.brand ?? ""} disabled={!metadata} onChange={(event) => change({ brand: event.target.value })}><option value="">전체 브랜드</option>{draft.brand && !metadata?.brands.some((brand) => brand.slug === draft.brand) ? <option value={draft.brand}>현재 URL 브랜드</option> : null}{metadata?.brands.map((brand) => <option key={brand.brandId} value={brand.slug}>{brand.name}</option>)}</select></label>
    </div><div hidden={group !== "all" && group !== "category"}>{facets.map((facet) => <fieldset key={facet.key}><legend>{facet.name}</legend>{facet.options.map((option) => { const value = `${facet.key}:${option.value}`; return <label className="catalog-check" key={option.optionId}><input type="checkbox" checked={draft.facet?.includes(value) ?? false} onChange={(event) => change({ facet: event.target.checked ? [...(draft.facet ?? []), value] : draft.facet?.filter((item) => item !== value) })} />{option.value}</label>; })}</fieldset>)}
    </div><div hidden={group !== "all" && group !== "price"}><fieldset><legend>가격 범위</legend><div className="catalog-price-inputs"><label className="form-field">최소 가격<input className="input" type="number" min="0" aria-invalid={Boolean(priceError)} aria-describedby={priceError ? "catalog-price-error" : undefined} value={draft.minPrice ?? ""} onChange={(event) => change({ minPrice: event.target.value === "" ? undefined : Number(event.target.value) })} /></label><label className="form-field">최대 가격<input className="input" type="number" min="0" aria-invalid={Boolean(priceError)} aria-describedby={priceError ? "catalog-price-error" : undefined} value={draft.maxPrice ?? ""} onChange={(event) => change({ maxPrice: event.target.value === "" ? undefined : Number(event.target.value) })} /></label></div>{priceError ? <p id="catalog-price-error" className="field-error" role="alert">{priceError}</p> : null}</fieldset>
    </div><div hidden={group !== "all" && group !== "condition"}><fieldset><legend>구매 조건</legend><label className="catalog-check"><input type="checkbox" checked={draft.subscribable === true} onChange={(event) => change({ subscribable: event.target.checked ? true : undefined })} />정기배송 가능</label><label className="catalog-check"><input type="checkbox" checked={draft.purchasable === true} onChange={(event) => change({ purchasable: event.target.checked ? true : undefined })} />구매 가능한 상품만</label></fieldset>
    </div><div className="button-row"><button className="button button-primary" type="submit" disabled={Boolean(priceError)}>필터 적용</button><button className="button button-secondary" type="button" onClick={() => { setDraft(current => group === "all" ? { q: current.q, sort: current.sort, size: current.size, page: 0 } : changeCatalogFilters(current, group === "pet" ? { petType: undefined } : group === "category" ? { category: undefined, subcategory: undefined, facet: undefined } : group === "brand" ? { brand: undefined } : group === "price" ? { minPrice: undefined, maxPrice: undefined } : { purchasable: undefined, subscribable: undefined })); }}>초기화</button></div>
  </form>;
}

function FilterChips({ filters, metadata, onChange, onReset }: { filters: ProductFilters; metadata: CatalogDiscovery | null; onChange: (patch: Partial<ProductFilters>) => void; onReset: () => void }) {
  const chips: { key: string; label: string; remove: () => void }[] = [];
  const category = metadata?.categories.find((item) => item.slug === filters.category);
  const labels = { q: "검색", petType: "반려동물", category: "카테고리", subcategory: "세부 카테고리", brand: "브랜드", minPrice: "최소 가격", maxPrice: "최대 가격", subscribable: "정기배송", purchasable: "구매 가능" };
  for (const key of Object.keys(labels) as (keyof typeof labels)[]) {
    const value = filters[key];
    if (value === undefined || value === "") continue;
    const display = key === "petType" ? formatPetType(String(value)) : key === "category" ? category?.name ?? value : key === "subcategory" ? category?.children.find((item) => item.slug === value)?.name ?? value : key === "brand" ? metadata?.brands.find((item) => item.slug === value)?.name ?? value : typeof value === "boolean" ? (value ? "예" : "아니오") : value;
    chips.push({ key, label: `${labels[key]}: ${display}`, remove: () => onChange({ [key]: undefined }) });
  }
  filters.facet?.forEach((value, index) => chips.push({ key: `facet-${index}`, label: value, remove: () => onChange({ facet: filters.facet?.filter((_, itemIndex) => itemIndex !== index) }) }));
  return chips.length ? <div className="catalog-filter-chips" aria-label="선택한 필터">{chips.map((chip) => <button key={chip.key} type="button" onClick={chip.remove} aria-label={`${chip.label} 필터 제거`}>{chip.label} <span aria-hidden="true">×</span></button>)}<button type="button" onClick={onReset}>전체 초기화</button></div> : null;
}

function activeFilterCount(filters: ProductFilters): number {
  return [filters.q, filters.petType, filters.category, filters.subcategory, filters.brand, filters.minPrice, filters.maxPrice, filters.subscribable, filters.purchasable].filter((value) => value !== undefined && value !== "").length + (filters.facet?.length ?? 0);
}

export default function ProductsPage() { return <Suspense fallback={<LoadingState>상품 목록을 준비하고 있습니다.</LoadingState>}><ProductsContent /></Suspense>; }
