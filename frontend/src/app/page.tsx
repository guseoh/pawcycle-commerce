"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError, recommendationApi, type RecommendationItem } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { v2Api, type Pet } from "@/lib/v2-api";

export default function Home() {
  const auth = useAuth();
  const [pets, setPets] = useState<Pet[] | null>(null);
  const [petId, setPetId] = useState("");
  const [items, setItems] = useState<RecommendationItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [petError, setPetError] = useState<string | null>(null);
  const [petRetry, setPetRetry] = useState(0);
  const recommendationRequest = useRef(0);

  useEffect(() => {
    recommendationRequest.current += 1;
    setPets(null);
    setPetId("");
    setItems(null);
    setPetError(null);
    setError(null);
    if (auth.status !== "authenticated" || auth.memberId === null) return;
    let active = true;
    void v2Api.pets.list().then(({ body }) => {
      if (active) setPets(body.items);
    }).catch(() => {
      if (active) setPetError("반려동물 목록을 불러오지 못했습니다.");
    });
    return () => { active = false; };
  }, [auth.status, auth.memberId, petRetry]);

  async function load() {
    if (!petId) return;
    const targetPetId = petId;
    const requestId = recommendationRequest.current + 1;
    recommendationRequest.current = requestId;
    setError(null);
    setItems(null);
    try {
      const result = await recommendationApi.products(Number(targetPetId));
      if (recommendationRequest.current !== requestId || petId !== targetPetId) return;
      setItems(result.products);
    } catch (e) {
      if (recommendationRequest.current !== requestId || petId !== targetPetId) return;
      setError(e instanceof ApiError && e.code === "PET_NOT_FOUND" ? "선택한 반려동물을 찾을 수 없습니다. 다시 선택해 주세요." : "추천을 불러오지 못했습니다.");
    }
  }

  return <>
    <header className="page-heading"><h1>반려동물에게 맞는 일상용품을 찾아보세요.</h1><p>상품 탐색, 정기배송, 주문 관리를 한곳에서 시작합니다.</p></header>
    {auth.status === "anonymous" ? <section className="section-card"><h2>상품을 둘러보세요</h2><p>로그인하면 보유 반려동물을 직접 선택해 추천을 확인할 수 있습니다.</p><div className="button-row"><Link className="button button-primary" href="/products">상품 탐색</Link><Link className="button button-secondary" href="/login?returnTo=%2F">로그인</Link></div></section> : null}
    {auth.status === "loading" ? <LoadingState>회원 정보를 확인하고 있습니다.</LoadingState> : null}
    {auth.status === "error" ? <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} /> : null}
    {auth.status === "authenticated" ? <section className="section-card"><h2>반려동물 상품 추천</h2>{petError ? <ErrorState title="반려동물 목록을 불러오지 못했습니다." message={petError} onRetry={() => setPetRetry((value) => value + 1)} /> : pets === null ? <LoadingState>반려동물 목록을 불러오고 있습니다.</LoadingState> : pets.length === 0 ? <p>등록된 반려동물이 없습니다.</p> : <><label className="form-field">반려동물 선택<select className="input" value={petId} onChange={(e) => { recommendationRequest.current += 1; setPetId(e.target.value); setItems(null); setError(null); }}><option value="">선택하세요</option>{pets.map((pet) => <option value={pet.petId} key={pet.petId}>{pet.name} · {pet.petType}</option>)}</select></label><button className="button button-primary" type="button" disabled={!petId} onClick={() => void load()}>추천 보기</button></>}{error ? <ErrorState title="추천을 불러오지 못했습니다." message={error} onRetry={() => void load()} /> : null}{items?.length === 0 ? <p>현재 추천 가능한 상품이 없습니다.</p> : items ? <ul className="history-list">{items.map((item) => <li key={item.productId}><Link href={`/products/${item.productId}`}><strong>{item.name}</strong></Link><span>{item.category.name} · {item.reason}</span></li>)}</ul> : null}</section> : null}
    <section className="section-card"><h2>주요 기능</h2><div className="button-row"><Link className="button button-secondary" href="/products">상품</Link><Link className="button button-secondary" href="/subscriptions">정기배송</Link><Link className="button button-secondary" href="/orders">주문</Link></div></section>
  </>;
}
