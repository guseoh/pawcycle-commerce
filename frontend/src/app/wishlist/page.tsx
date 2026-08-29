"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi, type WishlistItem } from "@/lib/commerce-final-api";
import { buildLoginHref, formatDateTime, notifyCommerceChanged } from "@/lib/frontend-utils";

export default function WishlistPage() {
  const auth = useAuth();
  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요해요." message="위시리스트는 로그인한 계정에 상품을 저장하는 기능이에요. 로그인 후 다시 저장해 주세요."><Link className="button button-primary" href={buildLoginHref("/wishlist")}>로그인하기</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (auth.status !== "authenticated" || auth.memberId === null) return <LoadingState>위시리스트를 불러오고 있습니다.</LoadingState>;
  return <WishlistForMember key={auth.memberId} />;
}

function WishlistForMember() {
  const auth = useAuth();
  const { markAnonymous } = auth;
  const [items, setItems] = useState<WishlistItem[] | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const [itemErrors, setItemErrors] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState<number | null>(null);
  const [removed, setRemoved] = useState<WishlistItem | null>(null);
  const [undoError, setUndoError] = useState<string | null>(null);
  const activeRef = useRef(false);
  const requestRef = useRef(0);

  const load = useCallback(() => {
    const request = ++requestRef.current;
    void commerceFinalApi.wishlist().then((result) => {
      if (!activeRef.current || request !== requestRef.current) return;
      setItems(result.items); setListError(null);
    }).catch((reason: unknown) => {
      if (!activeRef.current || request !== requestRef.current) return;
      if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") { markAnonymous(); return; }
      setItems(null); setListError(reason instanceof ApiError ? reason.message : "위시리스트를 불러오지 못했습니다.");
    });
  }, [markAnonymous]);

  useEffect(() => {
    activeRef.current = true;
    const timer = window.setTimeout(load, 0);
    return () => { activeRef.current = false; requestRef.current += 1; window.clearTimeout(timer); };
  }, [load]);

  useEffect(() => {
    if (!removed || undoError) return;
    const timer = window.setTimeout(() => setRemoved(null), 6000);
    return () => window.clearTimeout(timer);
  }, [removed, undoError]);

  async function remove(item: WishlistItem) {
    if (busy !== null) return;
    setBusy(item.productId); setItemErrors((current) => { const next = { ...current }; delete next[item.productId]; return next; });
    try {
      await auth.executeWithCsrf((csrf) => commerceFinalApi.deleteWishlist(item.productId, csrf));
      setItems((current) => current?.filter((candidate) => candidate.productId !== item.productId) ?? []);
      setRemoved(item); setUndoError(null); notifyCommerceChanged();
      requestAnimationFrame(() => document.querySelector<HTMLElement>(".wishlist-row .wishlist-remove, #wishlist-title")?.focus());
    } catch (reason) {
      setItemErrors((current) => ({ ...current, [item.productId]: reason instanceof ApiError ? reason.message : "상품을 제거하지 못했어요. 기존 목록은 그대로예요." }));
    } finally { setBusy(null); }
  }

  async function undo() {
    if (!removed || busy !== null) return;
    const item = removed;
    setBusy(item.productId); setUndoError(null);
    try {
      await auth.executeWithCsrf((csrf) => commerceFinalApi.addWishlist(item.productId, csrf));
      setItems((current) => current?.some((candidate) => candidate.productId === item.productId) ? current : [...(current ?? []), item]);
      setRemoved(null); notifyCommerceChanged();
    } catch (reason) {
      setUndoError(reason instanceof ApiError ? reason.message : "다시 저장하지 못했어요. 다시 시도해 주세요.");
    } finally { setBusy(null); }
  }

  if (items === null && !listError) return <LoadingState>위시리스트를 불러오고 있습니다.</LoadingState>;
  if (listError) return <ErrorState title="위시리스트를 불러오지 못했습니다." message={listError} onRetry={load} />;
  return <section className="wishlist-page"><header className="page-heading"><p className="eyebrow">Wishlist</p><h1 id="wishlist-title" tabIndex={-1}>위시리스트</h1><p>저장한 상품 {items?.length ?? 0}개를 확인하고 상품 상세로 이동할 수 있어요.</p></header>
    {items?.length ? <ul className="wishlist-list">{items.map((item) => <li className="wishlist-row" key={item.productId}><div><h2>{item.productName}</h2><p>저장한 시점 <time dateTime={item.createdAt}>{formatDateTime(item.createdAt)}</time></p>{itemErrors[item.productId] ? <p className="field-error" role="alert">{itemErrors[item.productId]} <button type="button" onClick={() => void remove(item)}>다시 시도</button></p> : null}</div><div className="button-row"><Link className="button button-secondary" href={`/products/${item.productId}`}>{item.productName} 상품 보기</Link><button className="button button-danger wishlist-remove" type="button" disabled={busy !== null} aria-label={`${item.productName} 위시리스트에서 제거`} onClick={() => void remove(item)}>{busy === item.productId ? "제거 중" : "위시에서 제거"}</button></div></li>)}</ul> : <div className="empty-callout"><strong>저장한 상품이 없어요.</strong><Link href="/products">상품 둘러보기</Link></div>}
    <Link className="text-link" href="/products">계속 쇼핑하기</Link>
    {removed ? <aside className="wishlist-undo-toast" role={undoError ? "alert" : "status"} aria-live="polite"><p>{undoError ?? `${removed.productName}을 위시리스트에서 제거했어요.`}</p><button className="button button-secondary" type="button" disabled={busy !== null} aria-label={`${removed.productName} 다시 저장`} onClick={() => void undo()}>{busy === removed.productId ? "저장 중" : undoError ? "다시 저장" : "되돌리기"}</button><button className="icon-button" type="button" aria-label="알림 닫기" onClick={() => setRemoved(null)}>닫기</button></aside> : null}
  </section>;
}
