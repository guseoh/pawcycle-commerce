"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError, productApi, type ProductSummary, type ProductSort } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref } from "@/lib/frontend-utils";
import { CatalogProductCard } from "@/components/catalog-product-card";
import { useCatalogDiscovery } from "@/components/catalog-discovery";
import { catalogHref } from "@/lib/catalog-filters";
import { RecommendationSection } from "@/components/recommendation-section";
import { selectedPersonalizedPetId } from "@/lib/recommendation";
import { v2Api, type Pet } from "@/lib/v2-api";

type ProductPreviewState = { status: "loading" } | { status: "success"; products: ProductSummary[] } | { status: "error"; message: string };

export default function Home() {
  const auth = useAuth();

  return (
    <div className="home-stack shopping-home">
      <section className="home-hero" aria-labelledby="home-title">
        <div className="home-hero-copy">
          <p className="eyebrow">PawCycle commerce</p>
          <h1 id="home-title">매일의 반려생활을<br />더 편안하게 이어가요.</h1>
          <p>필요한 상품을 찾고, 우리 아이에게 맞는 정기배송을 한 곳에서 간단하게 관리해 보세요.</p>
          <div className="button-row" aria-label="홈 주요 action">
            <Link className="button button-primary" href="/products">상품 둘러보기</Link>
            <Link className="button button-secondary" href="/subscriptions/new">정기배송 시작하기</Link>
            {auth.status === "anonymous" ? <Link className="button button-secondary" href={buildLoginHref("/")}>로그인</Link> : null}
            {auth.status === "authenticated" ? <Link className="button button-secondary" href="/subscriptions">내 정기배송</Link> : null}
          </div>
        <nav className="hero-pet-links" aria-label="빠른 상품 탐색"><Link href="/products?petType=DOG">DOG · 강아지 →</Link><Link href="/products?petType=CAT">CAT · 고양이 →</Link></nav>
        </div>
        <aside className="hero-note" aria-label="정기배송 안내">
          <span className="hero-note-kicker">MY ROUTINE</span>
          <strong>다음 배송을 한눈에</strong>
          <p>필요한 주기에 맞춰 배송 일정과 변경 사항을 확인하세요.</p>
          <Link href="/subscriptions">정기배송 보기 <span aria-hidden="true">↗</span></Link>
        </aside>
      </section>

      <CompactDiscovery />
      <HomeProductPreview id="new" title="새로 들어온 상품" description="새로운 일상의 즐거움을 찾아보세요." sort="NEWEST" />
      <HomeProductPreview id="routine" title="꾸준히 필요한 것들" description="자주 쓰는 상품은 정기배송으로 편안하게." sort="NEWEST" subscribable />
      <RecommendationSection id="home-popular-title" title="많이 선택한 상품" description="많은 반려가족이 최근에 찾은 상품을 만나보세요." source="home-popular" request={{ kind: "popular", limit: 4 }} />
      <RecommendationSection id="home-trending-title" title="지금 주목받는 상품" description="최근 반려생활에서 관심이 커지고 있는 상품이에요." source="home-trending" request={{ kind: "trending", limit: 4 }} />
      <SubscriptionValue />

      {auth.status === "loading" ? <LoadingState>회원 정보를 확인하고 있습니다.</LoadingState> : null}
      {auth.status === "error" ? <ErrorState headingLevel={3} title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()}><Link className="button button-secondary" href={buildLoginHref("/")}>로그인</Link></ErrorState> : null}
      {auth.status === "authenticated" && auth.memberId !== null ? <PersonalizedRecommendations key={auth.memberId} /> : null}
      <TrustLinks />
    </div>
  );
}

function CompactDiscovery() {
  const { state, retry } = useCatalogDiscovery();
  return <section className="home-discovery" aria-labelledby="home-discovery-title">
    <div className="section-title"><div><p className="eyebrow">Shop by category</p><h2 id="home-discovery-title">필요한 것부터, 가볍게</h2></div><Link className="text-link" href="/products">전체 상품 →</Link></div>
    {state.status === "loading" ? <p className="discovery-status" role="status">카테고리와 브랜드를 불러오는 중입니다.</p> : null}
    {state.status === "error" ? <ErrorState headingLevel={3} title="탐색 정보를 불러오지 못했습니다." message={state.message} onRetry={retry}><Link href="/products">상품 검색으로 이동</Link></ErrorState> : null}
    {state.status === "success" ? <>
      {state.data.categories.length ? <nav className="home-category-grid" aria-label="상품 카테고리">{state.data.categories.map((category) => <Link key={category.categoryId} href={`/products?category=${encodeURIComponent(category.slug)}`}>{category.name}<span aria-hidden="true">→</span></Link>)}</nav> : <p className="empty-callout">카테고리를 준비하고 있어요. 전체 상품에서 먼저 만나보세요.</p>}
      <div className="home-brand-section"><p className="eyebrow">Our brands</p><h3>함께하는 브랜드</h3>{state.data.brands.length ? <nav className="home-brand-grid" aria-label="브랜드별 상품">{state.data.brands.map((brand) => <Link href={catalogHref({ brand: brand.slug })} key={brand.brandId}>{brand.logoUrl ? <img src={brand.logoUrl} alt="" loading="lazy" onError={(event) => { event.currentTarget.hidden = true; }} /> : null}<span>{brand.name}</span></Link>)}</nav> : <p className="field-help">브랜드를 준비하고 있습니다.</p>}</div>
    </> : null}
  </section>;
}

function HomeProductPreview({ id, title, description, sort, subscribable }: { id: string; title: string; description: string; sort: ProductSort; subscribable?: boolean }) {
  const [state, setState] = useState<ProductPreviewState>({ status: "loading" });
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    void productApi.list({ page: 0, size: 4, sort, subscribable }).then((response) => {
      if (active) setState({ status: "success", products: (response.items ?? response.products ?? []).slice(0, 4) });
    }).catch((error: unknown) => {
      if (active) setState({ status: "error", message: error instanceof ApiError ? error.message : "상품을 불러오지 못했습니다." });
    });
    return () => { active = false; };
  }, [retry, sort, subscribable]);

  return <section className="home-product-preview" aria-labelledby={`home-products-${id}`}>
    <div className="section-title"><div><h2 id={`home-products-${id}`}>{title}</h2><p>{description}</p></div><Link className="text-link" href={catalogHref({ sort, subscribable })}>더 보기 →</Link></div>
    {state.status === "loading" ? <LoadingState>상품을 불러오고 있습니다.</LoadingState> : null}
    {state.status === "error" ? <ErrorState headingLevel={3} title="상품을 불러오지 못했습니다." message={state.message} onRetry={() => { setState({ status: "loading" }); setRetry((value) => value + 1); }} /> : null}
    {state.status === "success" && state.products.length === 0 ? <div className="empty-callout"><strong>새로운 상품을 준비하고 있어요.</strong><Link href="/products">다른 상품 둘러보기</Link></div> : null}
    {state.status === "success" && state.products.length > 0 ? <div className="catalog-products-grid">{state.products.map((product) => <CatalogProductCard product={product} key={product.productId} />)}</div> : null}
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
  const [selected, setSelected] = useState<number | null>(null);
  const [petError, setPetError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    void v2Api.pets.list().then(({ body }) => {
       if (active) setPets(body.items);
    }).catch(() => {
      if (active) setPetError("반려동물 목록을 불러오지 못했습니다.");
    });
    return () => {
      active = false;
    };
  }, [retry]);
  const selectedPetId = selectedPersonalizedPetId(pets ?? [], selected);
  return (
    <section className="recommendation-section" aria-labelledby="recommendation-title">
      <div className="section-title">
        <div>
          <p className="eyebrow">맞춤 추천</p>
          <h2 id="recommendation-title">우리 아이에게 맞는 상품을 찾아볼까요?</h2>
          <p id="recommendation-help">반려동물을 선택하면 현재 판매 중인 상품을 추천해 드려요.</p>
        </div>
      </div>
      {petError ? <ErrorState headingLevel={3} title="반려동물 목록을 불러오지 못했습니다." message={petError} onRetry={() => { setPets(null); setPetError(null); setRetry((value) => value + 1); }} /> : null}
      {petError ? null : pets === null ? <LoadingState>반려동물 목록을 불러오고 있습니다.</LoadingState> : pets.length === 0 ? <div className="empty-callout"><strong>등록된 반려동물이 없습니다.</strong><Link href="/subscriptions/new">반려동물 등록하고 정기배송 시작하기</Link></div> : <>
        <div className="recommendation-controls">
          <label className="form-field" htmlFor="recommendation-pet">반려동물 선택
            <select id="recommendation-pet" className="input" aria-describedby="recommendation-help" value={selectedPetId ?? ""} onChange={(event) => setSelected(event.target.value ? Number(event.target.value) : null)}>
              <option value="">선택하세요</option>
              {pets.map((pet) => <option key={pet.petId} value={pet.petId}>{pet.name} · {pet.petType}</option>)}
            </select>
          </label>
          {!selectedPetId ? <span className="field-help">반려동물을 선택하면 맞춤 추천을 확인할 수 있어요.</span> : null}
        </div>
        {selectedPetId ? <RecommendationSection key={selectedPetId} id="home-personalized-title" title="우리 아이에게 맞는 상품" description="선택한 반려동물의 유형에 맞춰 현재 구매 가능한 상품을 추천해 드려요." source="home-personalized" request={{ kind: "personalized", petId: selectedPetId }} /> : null}
      </>}
    </section>
  );
}
