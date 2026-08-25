"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi, type WishlistItem } from "@/lib/commerce-final-api";
import { buildLoginHref } from "@/lib/frontend-utils";

export default function WishlistPage() {
  const auth = useAuth();
  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="위시리스트를 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/wishlist")}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (auth.status !== "authenticated" || auth.memberId === null) return <LoadingState>위시리스트를 불러오고 있습니다.</LoadingState>;
  return <WishlistForMember key={auth.memberId} />;
}

function WishlistForMember() {
  const auth = useAuth();
  const { markAnonymous } = auth;
  const [items, setItems] = useState<WishlistItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const activeRef = useRef(false);
  const requestRef = useRef(0);

  const load = useCallback(() => {
    const request = ++requestRef.current;
    void commerceFinalApi.wishlist().then((result) => {
      if (!activeRef.current || request !== requestRef.current) return;
      setItems(result.items);
      setError(null);
    }).catch((reason: unknown) => {
      if (!activeRef.current || request !== requestRef.current) return;
      if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") {
        markAnonymous();
        return;
      }
      setItems(null);
      setError(reason instanceof ApiError ? reason.message : "위시리스트를 불러오지 못했습니다.");
    });
  }, [markAnonymous]);

  useEffect(() => {
    activeRef.current = true;
    const timer = window.setTimeout(load, 0);
    return () => { activeRef.current = false; requestRef.current += 1; window.clearTimeout(timer); };
  }, [load]);

  if (items === null && !error) return <LoadingState>위시리스트를 불러오고 있습니다.</LoadingState>;
  if (error) return <ErrorState title="위시리스트를 불러오지 못했습니다." message={error} onRetry={load} />;
  return <section><header className="page-heading"><p className="eyebrow">Wishlist</p><h1>다시 보고 싶은 상품</h1><p>상품을 눌러 상세를 확인하거나 상품 탐색으로 돌아갈 수 있어요.</p></header><section className="wishlist-panel section-card">{items?.length ? <div className="wishlist-grid">{items.map((item) => <article className="wishlist-card" key={item.productId}><span className="image-placeholder">PawCycle</span><div><Link href={`/products/${item.productId}`}><h2>{item.productName}</h2></Link><p>저장한 상품</p><button className="button button-secondary" type="button" onClick={() => void auth.executeWithCsrf((csrf) => commerceFinalApi.deleteWishlist(item.productId, csrf)).then(load).catch((reason: unknown) => setError(reason instanceof ApiError ? reason.message : "삭제하지 못했습니다."))}>위시리스트에서 삭제</button></div></article>)}</div> : <div className="empty-callout"><strong>저장한 상품이 없습니다.</strong><Link href="/products">상품 탐색으로 이동 →</Link></div>}</section></section>;
}
