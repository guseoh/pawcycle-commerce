"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ApiError, type ProductSummary } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { buildLoginHref, formatPetType, formatPrice, notifyCommerceChanged, userFacingCatalogLabel } from "@/lib/frontend-utils";

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
    : <span className={`image-placeholder ${className}`} role={alt ? "img" : undefined} aria-label={alt ? `${alt} — 상품 이미지를 준비 중입니다` : undefined} aria-hidden={alt ? undefined : true}><span aria-hidden="true">P</span><span className="sr-only">상품 이미지 준비 중</span></span>;
}

export function CatalogPrice({ price, compareAtPrice, discountRate }: { price: number | null; compareAtPrice?: number | null; discountRate?: number | null }) {
  return <span className="catalog-price">
    <strong>{price === null ? "현재 구매할 수 없음" : formatPrice(price)}</strong>
    {compareAtPrice != null ? <del><span className="sr-only">원가 </span>{formatPrice(compareAtPrice)}</del> : null}
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

  if (auth.status === "anonymous") return <span className="catalog-wishlist-gate"><span>저장하려면 로그인이 필요해요.</span><Link href={buildLoginHref(`/products/${productId}`)}>로그인하기</Link></span>;
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
    }}>{loadedFor !== auth.memberId ? "확인 중" : busy ? "저장 중" : saved ? "저장됨" : "위시"}</button>
    {message ? <span className="catalog-card-message" role="status">{message}</span> : null}
  </>;
}

export function CatalogProductCard({ product, compareSelected = false, onCompare }: { product: ProductSummary; compareSelected?: boolean; onCompare?: () => void }) {
  const productName = userFacingCatalogLabel(product.name, "상품");
  const href = `/products/${product.productId}`;
  return <article className="catalog-product-card" data-product-id={product.productId}>
    <Link className="catalog-image-link" href={href} aria-label={`${productName} 상품 상세 보기`}><div className="product-card-media"><CatalogImage src={product.thumbnailUrl} alt="" className="product-thumbnail" />{!product.purchasable ? <span className="catalog-sold-out">품절 · 현재 구매할 수 없음</span> : null}</div></Link>
    <div className="catalog-card-copy">
      {product.brand ? <p className="catalog-brand">{product.brand.name}</p> : null}
      <p className="product-card-meta">{formatPetType(product.petType)} · {product.category.name}</p>
      <h3><Link className="catalog-title-link" href={href}>{productName}</Link></h3>
      {product.shortDescription ? <p className="catalog-description">{product.shortDescription}</p> : null}
      <CatalogPrice price={product.purchasable ? product.representativePrice : null} compareAtPrice={product.compareAtPrice} discountRate={product.discountRate} />
      <p className="catalog-rating">{product.averageRating != null ? `5점 만점에 ${product.averageRating}점, 리뷰 ${product.reviewCount}개` : "아직 리뷰가 없어요"}</p>
      <div className="card-meta">{product.hasSubscribableSku ? <span className="tag">정기배송 가능</span> : null}<span className={`product-availability ${product.purchasable ? "is-available" : "is-unavailable"}`}>{product.purchasable ? "구매 가능" : "현재 구매할 수 없음"}</span></div>
    </div>
    <div className="catalog-card-actions"><CatalogWishlistButton productId={product.productId} productName={productName} />{onCompare ? <button className="compare-toggle" type="button" aria-pressed={compareSelected} onClick={onCompare}>{compareSelected ? "비교 해제" : "비교"}</button> : null}</div>
  </article>;
}
