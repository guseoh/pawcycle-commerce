"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError, Category, ProductSummary, categoryApi, productApi, recommendationApi, type RecommendationItem } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatPetType, formatPrice } from "@/lib/frontend-utils";
import { v2Api, type Pet } from "@/lib/v2-api";

type CategoryLoadState = { status: "loading" } | { status: "success"; categories: Category[] } | { status: "error"; message: string };
type ProductPreviewState = { status: "loading" } | { status: "success"; products: ProductSummary[] } | { status: "error"; message: string };

export default function Home() {
  const auth = useAuth();

  return (
    <div className="home-stack">
      <section className="home-hero" aria-labelledby="home-title">
        <div className="home-hero-copy">
          <p className="eyebrow">PawCycle commerce</p>
          <h1 id="home-title">매일의 반려생활을<br />더 편안하게 이어가요.</h1>
          <p>필요한 상품을 찾고, 우리 아이에게 맞는 정기배송을 한 곳에서 간단하게 관리해 보세요.</p>
          <div className="button-row" aria-label="홈 주요 action">
            <Link className="button button-primary" href="/products">상품 둘러보기</Link>
            {auth.status === "anonymous" ? <Link className="button button-secondary" href={buildLoginHref("/")}>로그인</Link> : null}
            {auth.status === "authenticated" ? <Link className="button button-secondary" href="/subscriptions">내 정기배송</Link> : null}
          </div>
        </div>
        <aside className="hero-note" aria-label="정기배송 안내">
          <span className="hero-note-kicker">MY ROUTINE</span>
          <strong>다음 배송을 한눈에</strong>
          <p>필요한 주기에 맞춰 배송 일정과 변경 사항을 확인하세요.</p>
          <Link href="/subscriptions">정기배송 보기 <span aria-hidden="true">↗</span></Link>
        </aside>
      </section>

      <CompactDiscovery />
      <HomeProductPreview />
      <SubscriptionValue />

      {auth.status === "loading" ? <LoadingState>회원 정보를 확인하고 있습니다.</LoadingState> : null}
      {auth.status === "error" ? <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()}><Link className="button button-secondary" href={buildLoginHref("/")}>로그인</Link></ErrorState> : null}
      {auth.status === "authenticated" && auth.memberId !== null ? <PersonalizedRecommendations key={auth.memberId} /> : null}
      <TrustLinks />
    </div>
  );
}

function CompactDiscovery() {
  const [state, setState] = useState<CategoryLoadState>({ status: "loading" });

  useEffect(() => {
    let active = true;
    void categoryApi.list().then((response) => {
      if (active) setState({ status: "success", categories: response.items });
    }).catch((error: unknown) => {
      if (active) setState({ status: "error", message: error instanceof ApiError ? error.message : "카테고리를 불러오지 못했습니다." });
    });
    return () => { active = false; };
  }, []);

  return <section className="home-discovery" aria-labelledby="home-discovery-title">
    <div className="section-title">
      <div><p className="eyebrow">Shop by</p><h2 id="home-discovery-title">우리 아이에게 필요한 것부터 찾아보세요</h2><p>반려동물과 카테고리를 고르면 바로 상품 목록으로 이어집니다.</p></div>
    </div>
    <nav className="pet-discovery-links" aria-label="반려동물별 상품 탐색"><Link href="/products?petType=DOG">강아지 상품 <span aria-hidden="true">→</span></Link><Link href="/products?petType=CAT">고양이 상품 <span aria-hidden="true">→</span></Link></nav>
    {state.status === "loading" ? <p className="discovery-status" role="status">카테고리를 불러오는 중입니다.</p> : null}
    {state.status === "error" ? <div className="discovery-status discovery-status-error" role="alert"><span>{state.message}</span><Link href="/products">상품 목록 보기</Link></div> : null}
    {state.status === "success" ? <nav className="home-category-grid" aria-label="상품 카테고리">{state.categories.map((category) => <Link key={category.categoryId} href={`/products?category=${encodeURIComponent(category.slug)}`}>{category.name}<span aria-hidden="true">→</span></Link>)}</nav> : null}
  </section>;
}

function HomeProductPreview() {
  const [state, setState] = useState<ProductPreviewState>({ status: "loading" });
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    void productApi.list({ page: 0, size: 4, sort: "NEWEST" }).then((response) => {
      if (active) setState({ status: "success", products: (response.items ?? response.products ?? []).slice(0, 4) });
    }).catch((error: unknown) => {
      if (active) setState({ status: "error", message: error instanceof ApiError ? error.message : "상품을 불러오지 못했습니다." });
    });
    return () => { active = false; };
  }, [retry]);

  return <section className="home-product-preview" aria-labelledby="home-products-title">
    <div className="section-title">
      <div><p className="eyebrow">New in store</p><h2 id="home-products-title">지금 둘러보기</h2><p>현재 공개된 상품을 바로 살펴보세요.</p></div>
      <Link className="text-link" href="/products">전체 상품 보기</Link>
    </div>
    {state.status === "loading" ? <LoadingState>상품을 불러오고 있습니다.</LoadingState> : null}
    {state.status === "error" ? <ErrorState title="상품을 불러오지 못했습니다." message={state.message} onRetry={() => { setState({ status: "loading" }); setRetry((value) => value + 1); }} /> : null}
    {state.status === "success" && state.products.length === 0 ? <div className="empty-callout"><strong>현재 둘러볼 공개 상품이 없습니다.</strong><Link href="/products">상품 목록 확인하기</Link></div> : null}
    {state.status === "success" && state.products.length > 0 ? <div className="home-product-grid">{state.products.map((product) => <Link className="home-product-card" href={`/products/${product.productId}`} key={product.productId}>
      <span className="home-product-image">{product.thumbnailUrl ? <img src={product.thumbnailUrl} alt={`${product.name} 상품 이미지`} loading="lazy" /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}</span>
      <span className="home-product-copy"><span>{product.category.name} · {formatPetType(product.petType)}</span><strong>{product.name}</strong><b>{product.representativePrice === null ? "가격 준비 중" : formatPrice(product.representativePrice)}</b></span>
    </Link>)}</div> : null}
  </section>;
}

function SubscriptionValue() {
  return <section className="home-subscription-value" aria-labelledby="subscription-value-title">
    <div><p className="eyebrow">Regular delivery</p><h2 id="subscription-value-title">반복 구매는 정기배송으로 더 간편하게</h2><p>필요한 배송 주기를 선택하고, 다음 일정과 상품 구성을 내 정기배송에서 조정하세요.</p></div>
    <ul><li><strong>반복 구매</strong><span>자주 필요한 상품을 놓치지 않아요.</span></li><li><strong>배송 주기</strong><span>우리 아이의 생활 리듬에 맞춰요.</span></li><li><strong>일정 관리</strong><span>다음 배송과 변경 사항을 확인해요.</span></li></ul>
    <Link className="button button-secondary" href="/subscriptions">정기배송 살펴보기</Link>
  </section>;
}

function TrustLinks() {
  return <section className="home-trust-links" aria-labelledby="home-trust-title">
    <div><p className="eyebrow">Help & policy</p><h2 id="home-trust-title">안심하고 이어가는 쇼핑</h2></div>
    <nav aria-label="쇼핑 안내"><Link href="/shipping">배송 안내</Link><Link href="/returns">교환·반품</Link><Link href="/faq">FAQ</Link><Link href="/support">고객지원</Link></nav>
  </section>;
}

function PersonalizedRecommendations() {
  const [pets, setPets] = useState<Pet[] | null>(null);
  const [petId, setPetId] = useState("");
  const [items, setItems] = useState<RecommendationItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [petError, setPetError] = useState<string | null>(null);
  const [recommendationLoading, setRecommendationLoading] = useState(false);
  const [retry, setRetry] = useState(0);
  const request = useRef(0);

  useEffect(() => {
    let active = true;
    void v2Api.pets.list().then(({ body }) => {
      if (active) setPets(body.items);
    }).catch(() => {
      if (active) setPetError("반려동물 목록을 불러오지 못했습니다.");
    });
    return () => {
      active = false;
      request.current += 1;
    };
  }, [retry]);

  async function load() {
    if (!petId) return;
    const id = ++request.current;
    setError(null);
    setItems(null);
    setRecommendationLoading(true);
    try {
      const result = await recommendationApi.products(Number(petId));
      if (request.current === id) setItems(result.products);
    } catch (e) {
      if (request.current === id) {
        setError(e instanceof ApiError && e.code === "PET_NOT_FOUND" ? "선택한 반려동물을 찾을 수 없습니다. 다시 선택해 주세요." : "추천을 불러오지 못했습니다.");
      }
    } finally {
      if (request.current === id) setRecommendationLoading(false);
    }
  }

  return (
    <section className="recommendation-section" aria-labelledby="recommendation-title">
      <div className="section-title">
        <div>
          <p className="eyebrow">맞춤 추천</p>
          <h2 id="recommendation-title">우리 아이에게 맞는 상품을 찾아볼까요?</h2>
          <p id="recommendation-help">반려동물을 선택하면 현재 판매 중인 상품을 추천해 드려요.</p>
        </div>
      </div>
      {petError ? <ErrorState title="반려동물 목록을 불러오지 못했습니다." message={petError} onRetry={() => { setPets(null); setPetError(null); setRetry((value) => value + 1); }} /> : null}
      {petError ? null : pets === null ? <LoadingState>반려동물 목록을 불러오고 있습니다.</LoadingState> : pets.length === 0 ? <div className="empty-callout"><strong>등록된 반려동물이 없습니다.</strong><Link href="/subscriptions/new">반려동물 등록하고 정기배송 시작하기</Link></div> : <>
        <div className="recommendation-controls">
          <label className="form-field" htmlFor="recommendation-pet">반려동물 선택
            <select id="recommendation-pet" className="input" aria-describedby="recommendation-help" value={petId} onChange={(event) => { request.current += 1; setPetId(event.target.value); setItems(null); setError(null); setRecommendationLoading(false); }}>
              <option value="">선택하세요</option>
              {pets.map((pet) => <option key={pet.petId} value={pet.petId}>{pet.name} · {pet.petType}</option>)}
            </select>
          </label>
          <button className="button button-primary" type="button" disabled={!petId || recommendationLoading} onClick={() => void load()}>{recommendationLoading ? "추천 불러오는 중" : "추천 보기"}</button>
        </div>
        {error ? <ErrorState title="추천을 불러오지 못했습니다." message={error} onRetry={() => void load()} /> : null}
        {recommendationLoading ? <LoadingState>맞춤 상품을 찾고 있습니다.</LoadingState> : null}
        {!recommendationLoading && items?.length === 0 ? <div className="empty-callout">현재 추천 가능한 상품이 없습니다.</div> : null}
        {!recommendationLoading && items ? <div className="recommendation-grid">{items.map((item) => <Link className="recommendation-card" key={item.productId} href={`/products/${item.productId}`}>
          {item.thumbnailUrl ? <img src={item.thumbnailUrl} alt={`${item.name} 상품 이미지`} loading="lazy" /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}
          <div><span className="tag">{item.category.name}</span><h3>{item.name}</h3><p>{item.reason}</p><span className="card-link">상품 보기 →</span></div>
        </Link>)}</div> : null}
      </>}
    </section>
  );
}
