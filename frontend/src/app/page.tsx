"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref } from "@/lib/frontend-utils";
import { useCatalogDiscovery } from "@/components/catalog-discovery";
import { RecommendationSection } from "@/components/recommendation-section";
import { selectedPersonalizedPetId } from "@/lib/recommendation";
import { v2Api, type Pet } from "@/lib/v2-api";

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
      <RoutineValue />
      <RecommendationSection id="home-popular-title" title="인기 상품" description="많은 반려가족이 선택한 상품을 둘러보세요." source="home-popular" request={{ kind: "popular", limit: 4 }} />

      {auth.status === "loading" ? <LoadingState>회원 정보를 확인하고 있습니다.</LoadingState> : null}
      {auth.status === "error" ? <ErrorState headingLevel={3} title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()}><Link className="button button-secondary" href={buildLoginHref("/")}>로그인</Link></ErrorState> : null}
      {auth.status === "authenticated" && auth.memberId !== null ? <PersonalizedRecommendations key={auth.memberId} /> : null}
      {auth.status === "anonymous" ? <section className="home-routine-entry" aria-labelledby="personalized-entry-title"><div><p className="eyebrow">맞춤 추천</p><h2 id="personalized-entry-title">반려동물에 맞는 상품을 찾고 있나요?</h2><p>로그인한 뒤 반려동물 프로필을 등록하면 실제 프로필 정보를 바탕으로 추천을 확인할 수 있어요.</p></div><Link className="button button-secondary" href={buildLoginHref("/pets")}>로그인하기</Link></section> : null}
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
    </> : null}
  </section>;
}

function RoutineValue() {
  return <section className="home-routine-entry" aria-labelledby="routine-value-title">
    <div><p className="eyebrow">필요한 시점에 다시</p><h2 id="routine-value-title">반복되는 준비를 한눈에 관리하세요</h2><p>주문 상세에서 정기배송 가능한 상품을 확인하고, 시작한 뒤에는 다음 배송일과 주기를 각각 관리할 수 있어요.</p></div>
    <Link className="button button-secondary" href="/subscriptions">내 정기배송 보기</Link>
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
      {petError ? null : pets === null ? <LoadingState>반려동물 목록을 불러오고 있습니다.</LoadingState> : pets.length === 0 ? <div className="empty-callout"><strong>아직 맞춤 추천을 만들 정보가 부족해요.</strong><Link href="/pets">반려동물 프로필 등록하기</Link></div> : <>
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
