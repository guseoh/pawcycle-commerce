"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "./async-state";
import { CatalogPrice } from "./catalog-product-card";
import { ProductGallery } from "./product-gallery";
import { ProductPurchasePanel, ProductPurchaseSheet } from "./product-purchase-panel";
import { productQuantityError, selectProductSku, type OptionSelection } from "@/lib/product-selection";
import { currentProductWishlist, loadProductWishlist, type ProductWishlistState } from "@/lib/product-wishlist";
import { ProductTrustSections } from "./product-trust-sections";
import { ApiError, type ProductDetail, productApi } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { buildLoginHref, formatPetType, formatPrice, notifyCommerceChanged, rememberRecentProduct, userFacingCatalogLabel, type RecentProduct } from "@/lib/frontend-utils";
import { createInteractionEvent, finalProductApi } from "@/lib/final-product-api";
import { productRouteMatches } from "@/lib/product-view";
import { RecommendationSection } from "./recommendation-section";

type ProductState = { status: "loading" } | { status: "success"; product: ProductDetail } | { status: "not-found" } | { status: "error"; message: string };

export function ProductDetailScreen({ productId }: { productId: string }) {
  const auth = useAuth();
  const [state, setState] = useState<ProductState>({ status: "loading" });
  const [retry, setRetry] = useState(0);
  const [cartSkuId, setCartSkuId] = useState<number | null>(null);
  const [selection, setSelection] = useState<OptionSelection>({});
  const [purchaseVisible, setPurchaseVisible] = useState(true);
  const [sheetOpen, setSheetOpen] = useState(false);
  const authRequest = useRef(0);
  const wishlistRequest = useRef(0);
  const [wishlistRetry, setWishlistRetry] = useState(0);
  const [quantity, setQuantity] = useState("1");
  const [wishlistState, setWishlistState] = useState<ProductWishlistState>({ memberId: null, productId, status: "loading", value: false });
  const wishlist = currentProductWishlist(wishlistState, auth.status === "authenticated" ? auth.memberId : null, productId);
  const wishlisted = wishlist.status === "ready" && wishlist.value;
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [messageKind, setMessageKind] = useState<"success" | "error">("error");
  const [recentProducts, setRecentProducts] = useState<RecentProduct[]>([]);
  const viewedProduct = useRef<number | null>(null);

  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(() => {
      if (!active) return;
      setState({ status: "loading" });
      void productApi.detail(productId).then((product) => {
        if (!active) return;
        setState({ status: "success", product });
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
  useEffect(() => { viewedProduct.current = null; }, [productId]);
  useEffect(() => {
    if (state.status !== "success" || !productRouteMatches(productId, state.product.productId) || auth.status !== "authenticated" || viewedProduct.current === state.product.productId) return;
    viewedProduct.current = state.product.productId;
    const event = createInteractionEvent({ type: "PRODUCT_VIEW", productId: state.product.productId, source: "product-detail" });
    if (!event) return;
    void auth.executeWithCsrf((csrf) => finalProductApi.interactions.send([event], csrf)).catch(() => undefined);
  }, [auth, productId, state]);

  useEffect(() => {
    authRequest.current += 1;
    return () => { authRequest.current += 1; };
  }, [auth.status, auth.memberId, productId]);

  const { markAnonymous } = auth;
  useEffect(() => {
    if (auth.status !== "authenticated" || auth.memberId === null) return;
    const memberId = auth.memberId;
    let cancel = () => {};
    const timer = window.setTimeout(() => {
      cancel = loadProductWishlist(wishlistRequest, memberId, productId,
        async () => (await commerceFinalApi.wishlist()).items.some((item) => item.productId === Number(productId)),
        setWishlistState,
        (error) => { if (error instanceof ApiError && error.code === "AUTH_REQUIRED") markAnonymous(); });
    }, 0);
    return () => { window.clearTimeout(timer); cancel(); };
  }, [auth.status, auth.memberId, productId, wishlistRetry, markAnonymous]);

  const selectedSku = product ? selectProductSku(product.optionGroups, product.skus, selection, cartSkuId) : null;
  const quantityError = productQuantityError(quantity, selectedSku);
  const productName = product ? userFacingCatalogLabel(product.name, "반려동물 상품") : "";
  const categoryName = product ? userFacingCatalogLabel(product.category.name, "상품") : "";
  const description = product ? userFacingCatalogLabel(product.description, "상세 설명이 준비되지 않았습니다.") : "";
  const displayPrice = selectedSku?.price ?? null;

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
    if (wishlist.status !== "ready") return;
    setBusy(true); setMessage(null);
    const request = ++authRequest.current;
    try {
      await auth.executeWithCsrf((csrf) => wishlisted ? commerceFinalApi.deleteWishlist(product.productId, csrf) : commerceFinalApi.addWishlist(product.productId, csrf));
      if (request !== authRequest.current) return;
      setWishlistState({ memberId: auth.memberId, productId, status: "ready", value: !wishlisted }); setMessageKind("success"); setMessage(wishlisted ? `${productName}을 위시리스트에서 제거했어요.` : `${productName}을 위시리스트에 저장했어요.`);
      notifyCommerceChanged();
    } catch (error) { if (request !== authRequest.current) return; if (error instanceof ApiError && error.code === "AUTH_REQUIRED") auth.markAnonymous(); setMessageKind("error"); setMessage(error instanceof ApiError ? error.message : "위시리스트를 변경하지 못했습니다."); }
    finally { setBusy(false); }
  }

  async function addCart() {
    if (!product || !selectedSku?.purchasable || busy) { setMessageKind("error"); setMessage("구매 가능한 옵션을 선택해 주세요."); return; }
    if (!canMutate()) return;
    const parsed = Number(quantity);
    if (quantityError !== null) { setMessageKind("error"); setMessage(quantityError ?? "수량을 확인해 주세요."); return; }
    setBusy(true); setMessage(null);
    const request = ++authRequest.current;
    try { await auth.executeWithCsrf((csrf) => commerceFinalApi.addCart(selectedSku.skuId, parsed, csrf)); if (request !== authRequest.current) return; notifyCommerceChanged(); setMessageKind("success"); setMessage(`${productName} ${parsed}개를 장바구니에 담았어요.`); }
    catch (error) { if (request !== authRequest.current) return; if (error instanceof ApiError && error.code === "AUTH_REQUIRED") auth.markAnonymous(); setMessageKind("error"); setMessage(error instanceof ApiError ? error.message : "장바구니에 담지 못했습니다."); }
    finally { setBusy(false); }
  }

  function selectOptions(nextSelection: OptionSelection) {
    setSelection(nextSelection);
    if (!product) return;
    const nextSku = selectProductSku(product.optionGroups, product.skus, nextSelection, cartSkuId);
    const currentQuantity = Number(quantity);
    if (!nextSku?.purchasable || !Number.isInteger(currentQuantity) || currentQuantity <= nextSku.availableQuantity) return;
    const adjusted = Math.max(1, nextSku.availableQuantity);
    setQuantity(String(adjusted)); setMessageKind("success"); setMessage(`선택한 옵션의 재고에 맞춰 수량을 ${adjusted}개로 조정했어요.`);
  }

  function selectLegacySku(skuId: number | null) {
    setCartSkuId(skuId);
    const nextSku = product?.skus.find((sku) => sku.skuId === skuId) ?? null;
    const currentQuantity = Number(quantity);
    if (!nextSku?.purchasable || !Number.isInteger(currentQuantity) || currentQuantity <= nextSku.availableQuantity) return;
    const adjusted = Math.max(1, nextSku.availableQuantity);
    setQuantity(String(adjusted)); setMessageKind("success"); setMessage(`선택한 옵션의 재고에 맞춰 수량을 ${adjusted}개로 조정했어요.`);
  }

  useEffect(() => {
    if (state.status !== "success") return;
    const target = document.querySelector(".desktop-purchase-panel .purchase-panel");
    if (!target) return;
    const observer = new IntersectionObserver(([entry]) => setPurchaseVisible(entry.isIntersecting), { threshold: 0 });
    observer.observe(target); return () => observer.disconnect();
  }, [state.status, productId]);

  function purchasePanel(id: string) {
    return <ProductPurchasePanel id={id} product={product!} selectedSku={selectedSku} selection={selection} onSelect={selectOptions} onLegacySelect={selectLegacySku} quantity={quantity} onQuantityChange={setQuantity} quantityError={quantityError} busy={busy} wishlisted={wishlisted} wishlistStatus={auth.status === "authenticated" ? wishlist.status : null} onWishlistRetry={() => { setWishlistState({ memberId: auth.memberId, productId, status: "loading", value: false }); setWishlistRetry((value) => value + 1); }} onWishlist={() => void toggleWishlist()} onCart={() => void addCart()} wishlistLoginHref={auth.status === "anonymous" ? buildLoginHref(`/products/${product!.productId}`) : null} message={message} messageKind={messageKind} />;
  }

  if (state.status === "loading") return <LoadingState>상품 정보를 불러오고 있습니다.</LoadingState>;
  if (state.status === "not-found") return <ErrorState title="상품을 확인할 수 없습니다." message="존재하지 않거나 공개되지 않은 상품입니다."><Link className="button button-secondary" href="/products">상품 목록으로</Link></ErrorState>;
  if (state.status === "error") return <ErrorState title="상품을 불러오지 못했습니다." message={state.message} onRetry={() => { setState({ status: "loading" }); setRetry((value) => value + 1); }}><Link className="button button-secondary" href="/products">상품 목록으로</Link></ErrorState>;

  return <div className="shopping-detail">
    <Link className="breadcrumb" href="/products">← 상품 목록</Link>
    <section className="product-purchase-zone" aria-label="상품 구매 영역">
      <div className="product-detail-layout">
          <section className="product-summary">
            {product!.brand ? <Link className="catalog-brand" href={`/products?brand=${encodeURIComponent(product!.brand.slug)}`}>{product!.brand.name}</Link> : null}
            <div className="card-meta"><span className="tag">{formatPetType(product!.petType)}</span><span className="tag">{categoryName}</span></div>
            <h1>{productName}</h1><p className="description product-summary-description">{product!.shortDescription ?? "상품 설명이 준비되지 않았습니다."}</p>
            <a className="catalog-rating" href="#product-reviews-title">{product!.trust.averageRating !== null ? `★ 평점 ${product!.trust.averageRating} · 리뷰 ${product!.trust.reviewCount}개` : "리뷰 없음 · 첫 리뷰 남기기"}</a>
            <p className="purchase-price">{selectedSku ? <CatalogPrice price={selectedSku.price} compareAtPrice={selectedSku.compareAtPrice} discountRate={selectedSku.discountRate} /> : "옵션 선택 필요"}</p>
            <p className={`product-availability ${(selectedSku?.purchasable ?? product!.purchasable) ? "is-available" : "is-unavailable"}`}>{selectedSku ? selectedSku.purchasable ? "선택한 옵션 구매 가능" : "선택한 옵션 품절" : product!.purchasable ? "옵션을 선택해 주세요" : "현재 품절"}</p>
            {selectedSku?.subscribable ? <p className="product-subscription-note">정기배송 가능한 옵션</p> : null}
          </section>
        <ProductGallery product={product!} />
        <div className="product-purchase-stack">
          <div className="desktop-purchase-panel">{purchasePanel("desktop-purchase")}</div>
          <div className="product-purchase-trust" aria-label="구매 전 안내"><Link href="#product-shipping"><strong>배송 안내</strong><span>주문 확인 후 배송이 시작됩니다.</span></Link><Link href="#product-returns"><strong>교환·반품</strong><span>상품 상태와 주문 조건을 확인해 주세요.</span></Link></div>
        </div>
      </div>
    </section>
    <nav className="product-info-nav" aria-label="상품 정보 바로가기"><a href="#product-intro">상품 소개</a><a href="#product-details">상세 정보</a><a href="#product-detail-sections">상세 내용</a><a href="#product-shipping">배송 안내</a><a href="#product-returns">교환·반품</a></nav>
    <div className="product-information detail-stack">
      <section id="product-intro" className="product-info-section" aria-labelledby="product-intro-title"><h2 id="product-intro-title">상품 소개</h2><p className="description">{description}</p></section>
      <section id="product-details" className="product-info-section" aria-labelledby="product-details-title"><h2 id="product-details-title">상세 정보</h2><dl className="product-info-grid"><div><dt>대상</dt><dd>{formatPetType(product!.petType)}</dd></div><div><dt>카테고리</dt><dd>{categoryName}</dd></div><div><dt>옵션</dt><dd>{product!.skus.length}개</dd></div><div><dt>구매 상태</dt><dd>{product!.purchasable ? "구매 가능" : "현재 품절"}</dd></div></dl></section>
      <section id="product-detail-sections" className="product-info-section" aria-labelledby="product-detail-sections-title"><h2 id="product-detail-sections-title">상세 내용</h2>{product!.detailSections.length ? <div className="product-detail-sections">{product!.detailSections.map((section) => <article className="product-detail-section" key={section.sectionId}><h3>{section.title}</h3><p className="description">{section.body}</p></article>)}</div> : <div className="empty-callout">추가 상세 내용이 아직 없습니다.</div>}</section>
      <section id="product-shipping" className="product-info-section policy-section" aria-labelledby="product-shipping-title"><div><h2 id="product-shipping-title">배송 안내</h2><p>주문 및 결제 확인 후 배송이 시작됩니다.</p></div><Link className="button button-secondary" href="/shipping">배송 정책 자세히 보기</Link></section>
      <section id="product-returns" className="product-info-section policy-section" aria-labelledby="product-returns-title"><div><h2 id="product-returns-title">교환·반품</h2><p>상품 상태와 주문 조건에 따라 교환·반품 가능 여부가 달라질 수 있습니다.</p></div><Link className="button button-secondary" href="/returns">교환·반품 정책 자세히 보기</Link></section>
      <ProductTrustSections productId={productId} trust={product!.trust} onTrustRefresh={refreshProductTrust} />
    </div>
    {recentProducts.length ? <section className="section-card product-context-section" aria-labelledby="recent-products-title"><div className="section-title"><h2 id="recent-products-title">최근 본 상품</h2><Link className="text-link" href="/products">상품 더 보기</Link></div><div className="mini-product-grid">{recentProducts.map((item) => <Link className="mini-product-card" href={`/products/${item.productId}`} key={item.productId}>{item.thumbnailUrl ? <img src={item.thumbnailUrl} alt={userFacingCatalogLabel(item.name, "반려동물 상품")} loading="lazy" /> : <span className="image-placeholder" aria-hidden="true">PawCycle</span>}<strong>{userFacingCatalogLabel(item.name, "반려동물 상품")}</strong>{item.price !== null ? <span>{formatPrice(item.price)}</span> : null}</Link>)}</div></section> : null}
    <RecommendationSection key={`related-${product!.productId}`} id="related-products-title" title="비슷한 상품" description="이 상품과 함께 비교해 보기 좋은 상품이에요." source="product-related" request={{ kind: "related", productId: product!.productId }} />
    <RecommendationSection key={`complementary-${product!.productId}`} id="complementary-products-title" title="함께 보기 좋은 상품" description="함께 살펴보면 좋은 상품을 모았어요." source="product-complementary" request={{ kind: "complementary", productId: product!.productId }} />
    {!purchaseVisible ? <div className="mobile-purchase-bar"><div><span className="field-help">선택한 옵션</span><strong>{displayPrice === null ? "옵션 선택 필요" : formatPrice(displayPrice)}</strong></div><button className="button button-primary" onClick={() => setSheetOpen(true)}>{selectedSku ? "구매 옵션 보기" : "옵션 선택"}</button></div> : null}
    {sheetOpen ? <ProductPurchaseSheet onClose={() => setSheetOpen(false)}>{purchasePanel("mobile-purchase")}</ProductPurchaseSheet> : null}
  </div>;
}
