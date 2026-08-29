"use client";

import { useState } from "react";
import { productApi, type ProductDetail, type ProductSummary } from "@/lib/api";
import type { V2SubscriptionDetail } from "@/lib/v2-api";
import { formatPrice, userFacingCatalogLabel } from "@/lib/frontend-utils";
import { LoadingState } from "./async-state";

export function SubscriptionAddonPicker({ subscription, pending, onSet }: { subscription: V2SubscriptionDetail; pending: boolean; onSet: (body: Record<string, unknown>) => void }) {
  const [query, setQuery] = useState("");
  const [products, setProducts] = useState<ProductSummary[] | null>(null);
  const [selectedProduct, setSelectedProduct] = useState<ProductDetail | null>(null);
  const [selectedSku, setSelectedSku] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function search(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setLoading(true); setError(null); setSelectedProduct(null); setSelectedSku("");
    try { const result = await productApi.list({ purchasable: true, q: query.trim() || undefined, size: 20 }); setProducts(result.items ?? result.products ?? []); }
    catch { setError("추가할 상품을 찾지 못했습니다. 잠시 후 다시 시도해 주세요."); }
    finally { setLoading(false); }
  }

  async function selectProduct(product: ProductSummary) {
    setLoading(true); setError(null); setProducts(null); setSelectedProduct(null); setSelectedSku("");
    try { setSelectedProduct(await productApi.detail(String(product.productId))); }
    catch { setError("상품 옵션을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."); }
    finally { setLoading(false); }
  }

  const sku = selectedProduct?.skus.find((item) => item.skuId === Number(selectedSku));
  const canSet = Boolean(sku?.purchasable && Number.isInteger(Number(quantity)) && Number(quantity) >= 1 && Number(quantity) <= 10);
  if (!subscription.availableActions?.includes("SET_NEXT_DELIVERY_ADDON")) return null;
  return <section className="addon-picker" aria-labelledby="addon-picker-title"><h3 id="addon-picker-title">이번 배송에 추가 상품 담기</h3><p className="field-help">다음 배송에만 적용할 상품을 찾아 옵션과 수량을 선택하세요.</p><form className="addon-search" onSubmit={search}><label className="form-field" htmlFor="addon-product-search">상품 검색<input id="addon-product-search" className="input" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="상품명으로 검색(선택)" disabled={pending || loading} /></label><button className="button button-secondary" type="submit" disabled={pending || loading}>{loading ? "찾는 중" : "상품 찾기"}</button></form>{error ? <p className="field-error" role="alert">{error}</p> : null}{loading && !products && !selectedProduct ? <LoadingState>상품 옵션을 불러오고 있습니다.</LoadingState> : null}{products?.length ? <ul className="addon-product-results">{products.map((product) => <li key={product.productId}><button type="button" className="addon-product-choice" onClick={() => void selectProduct(product)} disabled={pending}><strong>{userFacingCatalogLabel(product.name, "상품")}</strong><span>{product.representativePrice === null ? "가격 준비 중" : formatPrice(product.representativePrice)}</span></button></li>)}</ul> : products ? <p className="empty-callout">검색 결과가 없습니다.</p> : null}{selectedProduct ? <div className="addon-selection"><p><strong>{userFacingCatalogLabel(selectedProduct.name, "상품")}</strong></p><label className="form-field">상품 옵션<select className="input" value={selectedSku} onChange={(event) => setSelectedSku(event.target.value)} disabled={pending}><option value="">옵션을 선택하세요</option>{selectedProduct.skus.map((option) => <option key={option.skuId} value={option.skuId} disabled={!option.purchasable}>{userFacingCatalogLabel(option.skuName, "상품 옵션")} · {formatPrice(option.price)}{option.purchasable ? "" : " · 구매 불가"}</option>)}</select></label><label className="form-field">수량<input className="input" type="number" min="1" max="10" step="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} disabled={pending} /></label><button className="button button-primary" type="button" disabled={!canSet || pending} onClick={() => { if (sku) onSet({ skuId: sku.skuId, quantity: Number(quantity) }); }}>추가 상품 담기</button></div> : null}</section>;
}
