"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "./async-state";
import { ProductTrustSections } from "./product-trust-sections";
import { ApiError, type ProductDetail, type ProductSummary, productApi } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { buildLoginHref, formatPetType, formatPrice, notifyCommerceChanged, rememberRecentProduct, userFacingCatalogLabel, type RecentProduct } from "@/lib/frontend-utils";

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
  const [quantityError, setQuantityError] = useState<string | null>(null);
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
    const timer = window.setTimeout(() => {
      if (!active) return;
      setState({ status: "loading" });
      setRelatedProducts(null);
      setRelatedError(null);
      setRelatedLoading(false);
      void productApi.detail(productId).then((product) => {
        if (!active) return;
        setState({ status: "success", product });
        setSubscriptionSkuId(product.skus.find((sku) => sku.subscribable)?.skuId ?? null);
        const recent = rememberRecentProduct({ productId: product.productId, name: userFacingCatalogLabel(product.name, "반려동물 상품"), thumbnailUrl: product.thumbnailUrl, price: product.skus[0]?.price ?? null });
        setRecentProducts(recent.filter((item) => item.productId !== product.productId).slice(0, 4));
      }).catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError && error.code === "PRODUCT_NOT_FOUND") setState({ status: "not-found" });
        else setState({ status: "error", message: error instanceof ApiError ? error.message : "상품 정보를 불러오지 못했습니다." });
      });
    }, 0);
    return () => { active = false; window.clearTimeout(timer); };
  }, [productId, retry]);

  const product = state.status === "success" ? state.product : null;
  const relatedProductId = product?.productId ?? null;
  const relatedCategorySlug = product?.category.slug ?? null;
  const relatedPetType = product?.petType ?? null;

  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(() => {
      if (!active) return;
      if (relatedProductId === null || relatedCategorySlug === null || relatedPetType === null) {
        setRelatedProducts(null);
        setRelatedError(null);
        setRelatedLoading(false);
        return;
      }
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
    }, 0);
    return () => { active = false; window.clearTimeout(timer); };
  }, [relatedCategorySlug, relatedPetType, relatedProductId, relatedRetry]);

  useEffect(() => {
    if (auth.status !== "authenticated") return;
    void commerceFinalApi.wishlist().then((result) => setWishlisted(result.items.some((item) => item.productId === Number(productId)))).catch(() => undefined);
  }, [auth.status, productId]);

  const selectedSku = product?.skus.find((sku) => sku.skuId === cartSkuId) ?? null;
  const selectedSubscriptionSkuId = product?.skus.find((sku) => sku.skuId === cartSkuId && sku.subscribable)?.skuId ?? subscriptionSkuId;
  const productName = product ? userFacingCatalogLabel(product.name, "반려동물 상품") : "";
  const categoryName = product ? userFacingCatalogLabel(product.category.name, "상품") : "";
  const description = product ? userFacingCatalogLabel(product.description, "상세 설명이 준비되지 않았습니다.") : "";
  const hasSubscribableSku = product?.skus.some((sku) => sku.subscribable) ?? false;
  const displayPrice = selectedSku?.price ?? product?.skus[0]?.price ?? null;

  const refreshProductTrust = useCallback(async () => {
    const latest = await productApi.detail(productId);
    setState({ status: "success", product: latest });
  }, [productId]);

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
    if (!Number.isInteger(parsed) || parsed < 1 || (selectedSku.availableQuantity !== null && parsed > selectedSku.availableQuantity)) { setMessageKind("error"); setMessage(quantityError ?? "수량을 확인해 주세요."); return; }
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
    <section className="product-purchase-zone" aria-label="상품 구매 영역">
      <div className="product-detail-layout">
        <section className="product-gallery">{product!.thumbnailUrl ? <img className="product-hero-image" src={product!.thumbnailUrl} alt={productName} /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}</section>
        <div className="product-purchase-stack">
          <section className="product-summary"><div className="card-meta"><span className="tag">{formatPetType(product!.petType)}</span><span className="tag">{categoryName}</span></div><h1>{productName}</h1><p className="description product-summary-description">{product!.shortDescription ?? "상품 설명이 준비되지 않았습니다."}</p><p className="description product-summary-description">{description}</p><p className="purchase-price">{displayPrice === null ? "가격 준비 중" : formatPrice(displayPrice)}</p><p className={`product-availability ${product!.purchasable ? "is-available" : "is-unavailable"}`}>{product!.purchasable ? "구매 가능" : "현재 품절"}</p></section>
          <section className="purchase-panel"><div className="section-title"><div><p className="eyebrow">일반 구매</p><h2>옵션과 수량 선택</h2></div></div>{message ? <p className={messageKind === "success" ? "notice-success" : "error-summary"} role={messageKind === "success" ? "status" : "alert"}>{message}</p> : null}<label className="form-field"><span className="field-label">상품 옵션</span><select className="input" value={cartSkuId ?? ""} onChange={(event) => { setCartSkuId(Number(event.target.value) || null); setQuantityError(null); }}><option value="">선택하세요</option>{product!.skus.map((sku) => <option key={sku.skuId} value={sku.skuId} disabled={!sku.purchasable}>{userFacingCatalogLabel(sku.skuName, "상품 옵션")} · {formatPrice(sku.price)}{sku.purchasable ? ` · 재고 ${sku.availableQuantity}개` : " · 구매 불가"}</option>)}</select></label><label className="form-field" htmlFor="product-quantity"><span className="field-label">수량</span><input id="product-quantity" className="input" type="number" min="1" max={selectedSku?.availableQuantity} aria-invalid={quantityError ? "true" : undefined} aria-describedby={quantityError ? "product-quantity-error" : undefined} value={quantity} onChange={(event) => { const raw = event.target.value; setQuantity(raw); const parsed = Number(raw); setQuantityError(!Number.isInteger(parsed) || parsed < 1 ? "수량은 1 이상의 정수여야 합니다." : selectedSku && parsed > selectedSku.availableQuantity ? `현재 재고 ${selectedSku.availableQuantity}개 이하로 선택해 주세요.` : null); }} /></label>{quantityError ? <p id="product-quantity-error" className="field-error" role="alert">{quantityError}</p> : null}<div className="button-row purchase-actions"><button className="button button-secondary" type="button" aria-pressed={wishlisted} disabled={busy} onClick={() => void toggleWishlist()}>{wishlisted ? "위시리스트에서 삭제" : "위시리스트에 담기"}</button><button className="button button-primary" type="button" disabled={busy || !selectedSku?.purchasable || Boolean(quantityError)} onClick={() => void addCart()}>장바구니에 담기</button></div></section>
          <div className="product-purchase-trust" aria-label="구매 전 안내"><Link href="#product-shipping"><strong>배송 안내</strong><span>주문 확인 후 배송이 시작됩니다.</span></Link><Link href="#product-returns"><strong>교환·반품</strong><span>상품 상태와 주문 조건을 확인해 주세요.</span></Link></div>
        </div>
      </div>
    </section>
    <nav className="product-info-nav" aria-label="상품 정보 바로가기"><a href="#product-intro">상품 소개</a><a href="#product-details">상세 정보</a><a href="#product-detail-sections">상세 내용</a><a href="#product-shipping">배송 안내</a><a href="#product-returns">교환·반품</a></nav>
    <div className="product-information detail-stack">
      <section id="product-intro" className="product-info-section" aria-labelledby="product-intro-title"><p className="eyebrow">Product guide</p><h2 id="product-intro-title">상품 소개</h2><p className="description">{description}</p></section>
      <section id="product-details" className="product-info-section" aria-labelledby="product-details-title"><p className="eyebrow">At a glance</p><h2 id="product-details-title">상세 정보</h2><dl className="product-info-grid"><div><dt>대상</dt><dd>{formatPetType(product!.petType)}</dd></div><div><dt>카테고리</dt><dd>{categoryName}</dd></div><div><dt>옵션</dt><dd>{product!.skus.length}개</dd></div><div><dt>구매 상태</dt><dd>{product!.purchasable ? "구매 가능" : "현재 품절"}</dd></div></dl></section>
      <section id="product-detail-sections" className="product-info-section" aria-labelledby="product-detail-sections-title"><p className="eyebrow">Product detail</p><h2 id="product-detail-sections-title">상세 내용</h2>{product!.detailSections.length ? <div className="product-detail-sections">{product!.detailSections.map((section) => <article className="product-detail-section" key={section.sectionId}><h3>{section.title}</h3><p className="description">{section.body}</p></article>)}</div> : <div className="empty-callout">추가 상세 내용이 아직 없습니다.</div>}</section>
      <section id="product-shipping" className="product-info-section policy-section" aria-labelledby="product-shipping-title"><div><p className="eyebrow">Delivery</p><h2 id="product-shipping-title">배송 안내</h2><p>주문 및 결제 확인 후 배송이 시작됩니다.</p></div><Link className="button button-secondary" href="/shipping">배송 정책 자세히 보기</Link></section>
      <section id="product-returns" className="product-info-section policy-section" aria-labelledby="product-returns-title"><div><p className="eyebrow">Returns</p><h2 id="product-returns-title">교환·반품</h2><p>상품 상태와 주문 조건에 따라 교환·반품 가능 여부가 달라질 수 있습니다.</p></div><Link className="button button-secondary" href="/returns">교환·반품 정책 자세히 보기</Link></section>
      <section id="product-subscription" className="product-subscription-band" aria-labelledby="product-subscription-title"><div><p className="eyebrow">Regular delivery</p><h2 id="product-subscription-title">정기배송</h2><p>{hasSubscribableSku ? "매번 다시 주문하지 않고 원하는 주기로 받아보세요." : "현재 정기배송 가능한 옵션이 없습니다."}</p>{hasSubscribableSku ? <ul><li>원하는 배송 주기</li><li>다음 일정 확인</li><li>배송 일정 변경</li></ul> : null}</div>{hasSubscribableSku ? <Link className="button button-primary" href={`${CANONICAL_SUBSCRIPTION_START_HREF}?productId=${product!.productId}&skuId=${selectedSubscriptionSkuId ?? ""}`}>이 상품 정기배송 시작</Link> : null}</section>
      <ProductTrustSections productId={productId} trust={product!.trust} onTrustRefresh={refreshProductTrust} />
    </div>
    {recentProducts.length ? <section className="section-card product-context-section" aria-labelledby="recent-products-title"><div className="section-title"><h2 id="recent-products-title">최근 본 상품</h2><Link className="text-link" href="/products">상품 더 보기</Link></div><div className="mini-product-grid">{recentProducts.map((item) => <Link className="mini-product-card" href={`/products/${item.productId}`} key={item.productId}>{item.thumbnailUrl ? <img src={item.thumbnailUrl} alt={userFacingCatalogLabel(item.name, "반려동물 상품")} loading="lazy" /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}<strong>{userFacingCatalogLabel(item.name, "반려동물 상품")}</strong>{item.price !== null ? <span>{formatPrice(item.price)}</span> : null}</Link>)}</div></section> : null}
    {relatedLoading ? <section className="section-card"><LoadingState>관련 상품을 불러오고 있습니다.</LoadingState></section> : relatedError ? <section className="section-card"><ErrorState title="관련 상품을 불러오지 못했습니다." message={relatedError} onRetry={() => setRelatedRetry((value) => value + 1)} /></section> : relatedProducts?.length ? <section className="section-card product-context-section" aria-labelledby="related-products-title"><div className="section-title"><h2 id="related-products-title">함께 살펴보기</h2><span className="field-help">같은 카테고리의 공개 상품</span></div><div className="mini-product-grid">{relatedProducts.map((item) => { const relatedName = userFacingCatalogLabel(item.name, "반려동물 상품"); return <Link className="mini-product-card" href={`/products/${item.productId}`} key={item.productId}>{item.thumbnailUrl ? <img src={item.thumbnailUrl} alt={relatedName} loading="lazy" /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}<strong>{relatedName}</strong><span>{item.representativePrice === null ? "가격 준비 중" : formatPrice(item.representativePrice)}</span></Link>; })}</div></section> : relatedProducts ? <section className="section-card"><div className="empty-callout">같은 카테고리의 다른 상품이 아직 없습니다.</div></section> : null}
  </>;
}
