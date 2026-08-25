"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "./async-state";
import { ApiError, type ProductDetail, productApi } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { buildLoginHref, formatPetType, formatPrice } from "@/lib/frontend-utils";

type ProductState = { status: "loading" } | { status: "success"; product: ProductDetail } | { status: "not-found" } | { status: "error"; message: string };
export const CANONICAL_SUBSCRIPTION_START_HREF = "/subscriptions/new";

export function ProductDetailScreen({ productId }: { productId: string }) {
  const auth = useAuth();
  const [state, setState] = useState<ProductState>({ status: "loading" });
  const [retry, setRetry] = useState(0);
  const [cartSkuId, setCartSkuId] = useState<number | null>(null);
  const [subscriptionSkuId, setSubscriptionSkuId] = useState<number | null>(null);
  const [quantity, setQuantity] = useState("1");
  const [wishlisted, setWishlisted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [messageKind, setMessageKind] = useState<"success" | "error">("error");

  useEffect(() => {
    let active = true;
    void productApi.detail(productId).then((product) => {
      if (!active) return;
      setState({ status: "success", product });
      setSubscriptionSkuId(product.skus.find((sku) => sku.subscribable)?.skuId ?? null);
    }).catch((error: unknown) => {
      if (!active) return;
      if (error instanceof ApiError && error.code === "PRODUCT_NOT_FOUND") setState({ status: "not-found" });
      else setState({ status: "error", message: error instanceof ApiError ? error.message : "상품 정보를 불러오지 못했습니다." });
    });
    return () => { active = false; };
  }, [productId, retry]);

  useEffect(() => {
    if (auth.status !== "authenticated") return;
    void commerceFinalApi.wishlist().then((result) => setWishlisted(result.items.some((item) => item.productId === Number(productId)))).catch(() => undefined);
  }, [auth.status, productId]);

  const product = state.status === "success" ? state.product : null;
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
    } catch (error) { setMessageKind("error"); setMessage(error instanceof ApiError ? error.message : "위시리스트를 변경하지 못했습니다."); }
    finally { setBusy(false); }
  }

  async function addCart() {
    if (!product || !cartSkuId || !selectedSku?.purchasable || busy) { setMessageKind("error"); setMessage("구매 가능한 옵션을 선택해 주세요."); return; }
    if (!canMutate()) return;
    const parsed = Number(quantity);
    if (!Number.isInteger(parsed) || parsed < 1) { setMessageKind("error"); setMessage("수량을 확인해 주세요."); return; }
    setBusy(true); setMessage(null);
    try { await auth.executeWithCsrf((csrf) => commerceFinalApi.addCart(cartSkuId, parsed, csrf)); setMessageKind("success"); setMessage("장바구니에 담았습니다."); }
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
        <section className="purchase-panel"><div className="section-title"><div><p className="eyebrow">일반 구매</p><h2>옵션과 수량 선택</h2></div></div>{message ? <p className={messageKind === "success" ? "notice-success" : "error-summary"} role={messageKind === "success" ? "status" : "alert"}>{message}</p> : null}<label className="form-field">상품 옵션<select className="input" value={cartSkuId ?? ""} onChange={(event) => setCartSkuId(Number(event.target.value) || null)}><option value="">선택하세요</option>{product!.skus.map((sku) => <option key={sku.skuId} value={sku.skuId} disabled={!sku.purchasable}>{sku.skuName} · {formatPrice(sku.price)}{sku.purchasable ? "" : " · 품절"}</option>)}</select></label><label className="form-field">수량<input className="input" type="number" min="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label><div className="button-row"><button className="button button-secondary" type="button" aria-pressed={wishlisted} disabled={busy} onClick={() => void toggleWishlist()}>{wishlisted ? "위시리스트에서 삭제" : "위시리스트에 담기"}</button><button className="button button-primary" type="button" disabled={busy || !selectedSku?.purchasable} onClick={() => void addCart()}>장바구니에 담기</button></div></section>
        <section className="commerce-notes" aria-label="배송 및 교환·반품 안내"><p><strong>배송</strong><br />주문 확인 후 영업일 기준 배송이 시작되며, 배송 일정은 결제와 주소 확인 결과에 따라 달라질 수 있습니다.</p><p><strong>교환·반품</strong><br />상품 상태와 주문 조건에 따라 교환·반품 가능 여부가 달라집니다. 주문 전 안내와 고객센터 정책을 확인해 주세요.</p></section>
        <section className="subscription-entry"><div><strong>정기배송으로 더 편하게</strong><p>{product!.skus.some((sku) => sku.subscribable) ? "상품과 옵션 맥락을 유지한 채 서버가 제공하는 플랜을 선택해 보세요." : "현재 정기배송 가능한 옵션이 없습니다."}</p></div>{product!.skus.some((sku) => sku.subscribable) ? <Link className="button button-secondary" href={`${CANONICAL_SUBSCRIPTION_START_HREF}?productId=${product!.productId}&skuId=${selectedSubscriptionSkuId ?? ""}`}>정기배송 시작</Link> : null}</section>
      </div>
    </div>
  </>;
}
