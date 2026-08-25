"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "./async-state";
import { ApiError, type ProductDetail, type ProductSummary, productApi } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { buildLoginHref, formatPetType, formatPrice, notifyCommerceChanged, rememberRecentProduct, type RecentProduct } from "@/lib/frontend-utils";

type ProductState = { status: "loading" } | { status: "success"; product: ProductDetail } | { status: "not-found" } | { status: "error"; message: string };
export const CANONICAL_SUBSCRIPTION_START_HREF = "/subscriptions/new";

export function ProductDetailScreen({ productId }: { productId: string }) {
  const auth = useAuth();
  const [state, setState] = useState<ProductState>({ status: "loading" });
  const [retry, setRetry] = useState(0);
  const [relatedRetry, setRelatedRetry] = useState(0);
  const [cartSkuId, setCartSkuId] = useState<number | null>(null);
  const [subscriptionSkuId, setSubscriptionSkuId] = useState<number | null>(null);
  const [quantity, setQuantity] = useState("1");
  const [wishlisted, setWishlisted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [messageKind, setMessageKind] = useState<"success" | "error">("error");
  const [recentProducts, setRecentProducts] = useState<RecentProduct[]>([]);
  const [relatedProducts, setRelatedProducts] = useState<ProductSummary[] | null>(null);
  const [relatedError, setRelatedError] = useState<string | null>(null);
  const [relatedLoading, setRelatedLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setState({ status: "loading" });
    setRelatedProducts(null);
    setRelatedError(null);
    setRelatedLoading(false);
    void productApi.detail(productId).then((product) => {
      if (!active) return;
      setState({ status: "success", product });
      setSubscriptionSkuId(product.skus.find((sku) => sku.subscribable)?.skuId ?? null);
      const recent = rememberRecentProduct({ productId: product.productId, name: product.name, thumbnailUrl: product.thumbnailUrl, price: product.skus[0]?.price ?? null });
      setRecentProducts(recent.filter((item) => item.productId !== product.productId).slice(0, 4));
    }).catch((error: unknown) => {
      if (!active) return;
      if (error instanceof ApiError && error.code === "PRODUCT_NOT_FOUND") setState({ status: "not-found" });
      else setState({ status: "error", message: error instanceof ApiError ? error.message : "상품 정보를 불러오지 못했습니다." });
    });
    return () => { active = false; };
  }, [productId, retry]);

  const product = state.status === "success" ? state.product : null;
  const relatedProductId = product?.productId ?? null;
  const relatedCategorySlug = product?.category.slug ?? null;
  const relatedPetType = product?.petType ?? null;

  useEffect(() => {
    if (relatedProductId === null || relatedCategorySlug === null || relatedPetType === null) {
      setRelatedProducts(null);
      setRelatedError(null);
      setRelatedLoading(false);
      return;
    }
    let active = true;
    setRelatedProducts(null);
    setRelatedError(null);
    setRelatedLoading(true);
    void productApi.list({ category: relatedCategorySlug, petType: relatedPetType, size: 5, sort: "NEWEST" }).then((response) => {
      if (!active) return;
      setRelatedProducts((response.items ?? response.products ?? []).filter((item) => item.productId !== relatedProductId).slice(0, 4));
      setRelatedError(null);
    }).catch((error: unknown) => {
      if (!active) return;
      setRelatedError(error instanceof ApiError ? error.message : "관련 상품을 불러오지 못했습니다.");
    }).finally(() => {
      if (active) setRelatedLoading(false);
    });
    return () => { active = false; };
  }, [relatedCategorySlug, relatedPetType, relatedProductId, relatedRetry]);

  useEffect(() => {
    if (auth.status !== "authenticated") return;
    void commerceFinalApi.wishlist().then((result) => setWishlisted(result.items.some((item) => item.productId === Number(productId)))).catch(() => undefined);
  }, [auth.status, productId]);

  const selectedSku = product?.skus.find((sku) => sku.skuId === cartSkuId) ?? null;
  const selectedSubscriptionSkuId = product?.skus.find((sku) => sku.skuId === cartSkuId && sku.subscribable)?.skuId ?? subscriptionSkuId;

  function canMutate() {
    if (auth.status === "anonymous" && product) { location.assign(buildLoginHref(`/products/${product.productId}`)); return false; }
    if (auth.status !== "authenticated") { setMessageKind("error"); setMessage(auth.status === "loading" ? "로그인 상태를 확인하고 있습니다. 잠시 후 다시 시도해 주세요." : "로그인 상태를 확인하지 못했습니다. 다시 확인해 주세요."); return false; }
    return true;
  }

  async function toggleWishlist() {
    if (!product || busy || !canMutate()) return;
    setBusy(true); setMessage(null);
    try {
      await auth.executeWithCsrf((csrf) => wishlisted ? commerceFinalApi.deleteWishlist(product.productId, csrf) : commerceFinalApi.addWishlist(product.productId, csrf));
      setWishlisted((current) => !current); setMessageKind("success"); setMessage(wishlisted ? "위시리스트에서 삭제했습니다." : "위시리스트에 담았습니다.");
      notifyCommerceChanged();
    } catch (error) { setMessageKind("error"); setMessage(error instanceof ApiError ? error.message : "위시리스트를 변경하지 못했습니다."); }
    finally { setBusy(false); }
  }

  async function addCart() {
    if (!product || !cartSkuId || !selectedSku?.purchasable || busy) { setMessageKind("error"); setMessage("구매 가능한 옵션을 선택해 주세요."); return; }
    if (!canMutate()) return;
    const parsed = Number(quantity);
    if (!Number.isInteger(parsed) || parsed < 1) { setMessageKind("error"); setMessage("수량을 확인해 주세요."); return; }
    setBusy(true); setMessage(null);
    try { await auth.executeWithCsrf((csrf) => commerceFinalApi.addCart(cartSkuId, parsed, csrf)); notifyCommerceChanged(); setMessageKind("success"); setMessage("장바구니에 담았습니다."); }
    catch (error) { setMessageKind("error"); setMessage(error instanceof ApiError ? error.message : "장바구니에 담지 못했습니다."); }
    finally { setBusy(false); }
  }

  if (state.status === "loading") return <LoadingState>상품 정보를 불러오고 있습니다.</LoadingState>;
  if (state.status === "not-found") return <ErrorState title="상품을 확인할 수 없습니다." message="존재하지 않거나 공개되지 않은 상품입니다."><Link className="button button-secondary" href="/products">상품 목록으로</Link></ErrorState>;
  if (state.status === "error") return <ErrorState title="상품을 불러오지 못했습니다." message={state.message} onRetry={() => { setState({ status: "loading" }); setRetry((value) => value + 1); }}><Link className="button button-secondary" href="/products">상품 목록으로</Link></ErrorState>;

  return <>
    <Link className="breadcrumb" href="/products">← 상품 목록</Link>
    <div className="product-detail-layout">
      <section className="product-gallery">{product!.thumbnailUrl ? <img className="product-hero-image" src={product!.thumbnailUrl} alt="" /> : <span className="image-placeholder">PawCycle</span>}</section>
      <div className="product-purchase-stack">
        <section className="product-summary"><div className="card-meta"><span className="tag">{formatPetType(product!.petType)}</span><span className="tag">{product!.category.name}</span></div><h1>{product!.name}</h1><p className="description">{product!.description ?? "상세 설명이 준비되지 않았습니다."}</p><p className={`tag ${product!.purchasable ? "tag-positive" : "tag-muted"}`}>{product!.purchasable ? "구매 가능한 상품" : "현재 품절"}</p></section>
        <section className="purchase-panel"><div className="section-title"><div><p className="eyebrow">일반 구매</p><h2>옵션과 수량 선택</h2></div></div>{message ? <p className={messageKind === "success" ? "notice-success" : "error-summary"} role={messageKind === "success" ? "status" : "alert"}>{message}</p> : null}<label className="form-field">상품 옵션<select className="input" value={cartSkuId ?? ""} onChange={(event) => setCartSkuId(Number(event.target.value) || null)}><option value="">선택하세요</option>{product!.skus.map((sku) => <option key={sku.skuId} value={sku.skuId} disabled={!sku.purchasable}>{sku.skuName} · {formatPrice(sku.price)}{sku.purchasable ? ` · 재고 ${sku.availableQuantity}개` : " · 구매 불가"}</option>)}</select></label><label className="form-field">수량<input className="input" type="number" min="1" max={selectedSku?.availableQuantity} value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label><div className="button-row"><button className="button button-secondary" type="button" aria-pressed={wishlisted} disabled={busy} onClick={() => void toggleWishlist()}>{wishlisted ? "위시리스트에서 삭제" : "위시리스트에 담기"}</button><button className="button button-primary" type="button" disabled={busy || !selectedSku?.purchasable} onClick={() => void addCart()}>장바구니에 담기</button></div></section>
        <section className="commerce-notes" aria-label="배송 및 교환·반품 안내"><p><strong>배송</strong><br />주문 확인 후 영업일 기준 배송이 시작됩니다. <Link href="/shipping">배송 정책 보기</Link></p><p><strong>교환·반품</strong><br />주문 조건과 상품 상태에 따라 달라질 수 있어요. <Link href="/returns">교환·반품 정책 보기</Link></p></section>
        <section className="subscription-entry"><div><strong>정기배송으로 더 편하게</strong><p>{product!.skus.some((sku) => sku.subscribable) ? "상품과 옵션 맥락을 유지한 채 서버가 제공하는 플랜을 선택해 보세요." : "현재 정기배송 가능한 옵션이 없습니다."}</p></div>{product!.skus.some((sku) => sku.subscribable) ? <Link className="button button-secondary" href={`${CANONICAL_SUBSCRIPTION_START_HREF}?productId=${product!.productId}&skuId=${selectedSubscriptionSkuId ?? ""}`}>정기배송 시작</Link> : null}</section>
      </div>
    </div>
    {recentProducts.length ? <section className="section-card product-context-section" aria-labelledby="recent-products-title"><div className="section-title"><h2 id="recent-products-title">최근 본 상품</h2><Link className="text-link" href="/products">상품 더 보기</Link></div><div className="mini-product-grid">{recentProducts.map((item) => <Link className="mini-product-card" href={`/products/${item.productId}`} key={item.productId}>{item.thumbnailUrl ? <img src={item.thumbnailUrl} alt="" /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}<strong>{item.name}</strong>{item.price !== null ? <span>{formatPrice(item.price)}</span> : null}</Link>)}</div></section> : null}
    {relatedLoading ? <section className="section-card"><LoadingState>관련 상품을 불러오고 있습니다.</LoadingState></section> : relatedError ? <section className="section-card"><ErrorState title="관련 상품을 불러오지 못했습니다." message={relatedError} onRetry={() => setRelatedRetry((value) => value + 1)} /></section> : relatedProducts?.length ? <section className="section-card product-context-section" aria-labelledby="related-products-title"><div className="section-title"><h2 id="related-products-title">함께 살펴보기</h2><span className="field-help">같은 카테고리의 공개 상품</span></div><div className="mini-product-grid">{relatedProducts.map((item) => <Link className="mini-product-card" href={`/products/${item.productId}`} key={item.productId}>{item.thumbnailUrl ? <img src={item.thumbnailUrl} alt="" /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}<strong>{item.name}</strong><span>{item.representativePrice === null ? "가격 준비 중" : formatPrice(item.representativePrice)}</span></Link>)}</div></section> : relatedProducts ? <section className="section-card"><div className="empty-callout">같은 카테고리의 다른 상품이 아직 없습니다.</div></section> : null}
  </>;
}
