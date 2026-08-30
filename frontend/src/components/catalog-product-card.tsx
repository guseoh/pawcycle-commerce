"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ApiError, type ProductSummary } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { buildLoginHref, formatPrice, notifyCommerceChanged, userFacingCatalogLabel } from "@/lib/frontend-utils";

type WishlistCacheEntry = { ids: Set<number> | null; request: Promise<Set<number>> | null };
const wishlistByMember = new Map<number, WishlistCacheEntry>();

function wishlistEntry(memberId: number): WishlistCacheEntry {
  const existing = wishlistByMember.get(memberId);
  if (existing) return existing;
  const created: WishlistCacheEntry = { ids: null, request: null };
  wishlistByMember.set(memberId, created);
  return created;
}

function loadWishlist(memberId: number): Promise<Set<number>> {
  const entry = wishlistEntry(memberId);
  if (entry.ids) return Promise.resolve(entry.ids);
  if (!entry.request) {
    const request = commerceFinalApi.wishlist().then((result) => {
      const current = wishlistByMember.get(memberId);
      const ids = new Set(result.items.map((item) => item.productId));
      if (current?.request === request) { current.ids = ids; current.request = null; }
      return ids;
    }).catch((error: unknown) => {
      const current = wishlistByMember.get(memberId);
      if (current?.request === request) current.request = null;
      throw error;
    });
    entry.request = request;
  }
  return entry.request;
}

let changeListenerInstalled = false;
function ensureWishlistInvalidationListener() {
  if (changeListenerInstalled || typeof window === "undefined") return;
  changeListenerInstalled = true;
  window.addEventListener("pawcycle-commerce-changed", () => {
    wishlistByMember.forEach((entry) => { entry.ids = null; entry.request = null; });
  });
}

export function CatalogImage({ src, alt, className = "", eager = false }: { src: string | null; alt: string; className?: string; eager?: boolean }) {
  const [failedSource, setFailedSource] = useState<string | null>(null);
  return src && failedSource !== src
    ? <img src={src} alt={alt} className={className} loading={eager ? "eager" : "lazy"} onError={() => setFailedSource(src)} />
    : <span className={`image-placeholder ${className}`} role={alt ? "img" : undefined} aria-label={alt ? `${alt} — 상품 이미지를 준비 중입니다` : undefined} aria-hidden={alt ? undefined : true}><span>이미지 준비 중</span></span>;
}

export function CatalogPrice({ price, compareAtPrice, discountRate }: { price: number | null; compareAtPrice?: number | null; discountRate?: number | null }) {
  return <span className="catalog-price">
    <strong>{price === null ? "가격 확인 필요" : formatPrice(price)}</strong>
    {compareAtPrice != null && price != null && compareAtPrice > price ? <del><span className="sr-only">원가 </span>{formatPrice(compareAtPrice)}</del> : null}
    {discountRate != null && discountRate > 0 ? <span className="catalog-discount"><span className="sr-only">할인율 </span>{discountRate}%</span> : null}
  </span>;
}

function CatalogWishlistButton({ productId, productName }: { productId: number; productName: string }) {
  const auth = useAuth();
  const [saved, setSaved] = useState(false);
  const [loadedFor, setLoadedFor] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const refreshVersion = useRef(0);

  useEffect(() => {
    ensureWishlistInvalidationListener();
    const onCommerceChanged = () => { refreshVersion.current += 1; setLoadedFor(null); setRefreshKey((value) => value + 1); };
    window.addEventListener("pawcycle-commerce-changed", onCommerceChanged);
    return () => window.removeEventListener("pawcycle-commerce-changed", onCommerceChanged);
  }, []);

  useEffect(() => {
    if (auth.status !== "authenticated" || auth.memberId === null) return;
    let active = true;
    const memberId = auth.memberId;
    const observedVersion = refreshVersion.current;
    void loadWishlist(memberId).then((ids) => { if (active && refreshVersion.current === observedVersion && auth.memberId === memberId) { setSaved(ids.has(productId)); setLoadedFor(memberId); } }).catch((error: unknown) => { if (!active || refreshVersion.current !== observedVersion || auth.memberId !== memberId) return; if (error instanceof ApiError && error.code === "AUTH_REQUIRED") { auth.markAnonymous(); return; } setMessage("위시 상태를 확인하지 못했어요."); setLoadedFor(memberId); });
    return () => { active = false; };
  }, [auth, auth.memberId, auth.status, productId, refreshKey]);

  if (auth.status === "anonymous") return <Link href={buildLoginHref(`/products/${productId}`)} aria-label={`${productName} 찜, 로그인 필요`}><HeartIcon saved={false} /></Link>;
  return <>
    <button type="button" aria-pressed={loadedFor === auth.memberId ? saved : undefined} aria-label={`${productName} ${saved ? "위시리스트에서 제거" : "위시리스트에 저장"}`} disabled={loadedFor !== auth.memberId || busy || auth.status !== "authenticated"} onClick={() => {
      if (auth.status !== "authenticated") return;
      setBusy(true); setMessage(null);
      const memberId = auth.memberId;
      void auth.executeWithCsrf((csrf) => saved ? commerceFinalApi.deleteWishlist(productId, csrf) : commerceFinalApi.addWishlist(productId, csrf)).then(() => {
        const next = !saved;
        setSaved(next);
        const entry = memberId === null ? null : wishlistEntry(memberId);
        if (entry?.ids) { if (next) entry.ids.add(productId); else entry.ids.delete(productId); }
        setMessage(next ? "위시리스트에 저장했어요." : "위시리스트에서 제거했어요.");
        notifyCommerceChanged();
      }).catch((error: unknown) => { if (error instanceof ApiError && error.code === "AUTH_REQUIRED") auth.markAnonymous(); setMessage(error instanceof ApiError ? error.message : "위시리스트를 변경하지 못했어요."); }).finally(() => setBusy(false));
    }}>{loadedFor !== auth.memberId || busy ? <span aria-hidden="true">…</span> : <HeartIcon saved={saved} />}</button>
    {message ? <span className="catalog-card-message" role="status">{message}</span> : null}
  </>;
}

function HeartIcon({ saved }: { saved: boolean }) { return <svg viewBox="0 0 24 24" aria-hidden="true" fill={saved ? "currentColor" : "none"} stroke="currentColor" strokeWidth="1.75"><path d="M12 20s-8-4.8-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 9c0 6.2-8 11-8 11Z" /></svg>; }

export function CatalogProductCard({ product, compareSelected = false, onCompare }: { product: ProductSummary; compareSelected?: boolean; onCompare?: () => void }) {
  const productName = userFacingCatalogLabel(product.name, "상품");
  const href = `/products/${product.productId}`;
  return <article className="catalog-product-card" data-product-id={product.productId}>
    <Link className="catalog-image-link" href={href} aria-label={`${productName} 상품 상세 보기`}><div className="product-card-media"><CatalogImage src={product.thumbnailUrl} alt="" className="product-thumbnail" />{!product.purchasable ? <span className="catalog-sold-out">현재 구매 불가</span> : null}</div></Link>
    <div className="catalog-wishlist-control"><CatalogWishlistButton productId={product.productId} productName={productName} /></div>
    <div className="catalog-card-copy">
      {product.brand ? <p className="catalog-brand" title={product.brand.name}>{product.brand.name}</p> : null}
      <h3><Link className="catalog-title-link" href={href}>{productName}</Link></h3>
      <CatalogPrice price={product.representativePrice} compareAtPrice={product.compareAtPrice} discountRate={product.discountRate} />
      <p className="catalog-rating">{product.reviewCount > 0 && product.averageRating != null ? `5점 만점에 ${product.averageRating}점, 리뷰 ${product.reviewCount}개` : "아직 리뷰가 없어요"}</p>
      <div className="card-meta">{product.hasSubscribableSku ? <span className="tag">정기배송 가능</span> : null}<span className={`product-availability ${product.purchasable ? "is-available" : "is-unavailable"}`}>{product.purchasable ? "구매 가능" : "현재 구매할 수 없음"}</span></div>
    </div>
    <div className="catalog-card-actions">{onCompare ? <label className="compare-toggle"><input type="checkbox" checked={compareSelected} onChange={onCompare} />비교 담기</label> : null}</div>
  </article>;
}
