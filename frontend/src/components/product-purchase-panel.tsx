"use client";

import Link from "next/link";
import { useEffect, useRef, type ReactNode } from "react";
import type { ProductDetail, ProductSku } from "@/lib/api";
import { isProductOptionValueAvailable, type OptionSelection } from "@/lib/product-selection";
import { formatPrice, userFacingCatalogLabel } from "@/lib/frontend-utils";
import { CatalogPrice } from "./catalog-product-card";

interface PurchasePanelProps {
  id: string;
  product: ProductDetail;
  selectedSku: ProductSku | null;
  selection: OptionSelection;
  onSelect: (selection: OptionSelection) => void;
  onLegacySelect: (skuId: number | null) => void;
  quantity: string;
  onQuantityChange: (quantity: string) => void;
  quantityError: string | null;
  busy: boolean;
  wishlisted: boolean;
  wishlistStatus: "loading" | "ready" | "error" | null;
  onWishlistRetry: () => void;
  onWishlist: () => void;
  onCart: () => void;
  wishlistLoginHref: string | null;
  message: string | null;
  messageKind: "success" | "error";
}

export function ProductPurchasePanel(props: PurchasePanelProps) {
  const { id, product, selectedSku, selection, quantityError, busy } = props;
  return <section className="purchase-panel" aria-labelledby={`${id}-title`}>
    <h2 id={`${id}-title`}>옵션과 수량 선택</h2>
    {product.optionGroups.length ? product.optionGroups.map((group) => { const unavailable = group.values.filter((value) => !isProductOptionValueAvailable(product.optionGroups, product.skus, selection, group.optionGroupId, value.optionValueId)); return <fieldset className="product-option-group" key={group.optionGroupId}><legend>{group.name}</legend><div className="catalog-toggle-row">{group.values.map((value) => { const available = isProductOptionValueAvailable(product.optionGroups, product.skus, selection, group.optionGroupId, value.optionValueId); return <button type="button" disabled={busy || !available} key={value.optionValueId} aria-pressed={selection[group.optionGroupId] === value.optionValueId} title={available ? undefined : "현재 조합에서는 선택할 수 없음"} onClick={() => props.onSelect({ ...selection, [group.optionGroupId]: value.optionValueId })}>{selection[group.optionGroupId] === value.optionValueId ? "✓ " : ""}{value.value}{available ? "" : " · 선택 불가"}</button>; })}</div>{unavailable.length ? <p className="field-help">선택 불가로 표시된 값은 현재 조합과 함께 구매할 수 없어요.</p> : null}</fieldset>; }) : product.skus.length > 0 ? <label className="form-field">상품 옵션<select className="input" value={selectedSku?.skuId ?? ""} disabled={busy} onChange={(event) => props.onLegacySelect(Number(event.target.value) || null)}><option value="">선택하세요</option>{product.skus.map((sku) => <option key={sku.skuId} value={sku.skuId} disabled={!sku.purchasable}>{userFacingCatalogLabel(sku.skuName, "상품 옵션")} · {formatPrice(sku.price)}{sku.purchasable ? "" : " · 구매 불가"}</option>)}</select></label> : null}
    <div className="selection-summary" aria-live="polite" aria-atomic="true">{selectedSku ? <><p>{userFacingCatalogLabel(selectedSku.skuName, "선택한 옵션")}</p><CatalogPrice price={selectedSku.price} compareAtPrice={selectedSku.compareAtPrice} discountRate={selectedSku.discountRate} /><div className="selection-availability">{selectedSku.purchasable ? <><span className="product-availability is-available">구매 가능</span><span className="inventory-note">재고 {selectedSku.availableQuantity}개</span></> : <span className="product-availability is-unavailable">현재 품절 · 구매 불가</span>}</div></> : <p>{product.skus.length === 0 ? "현재 구매 가능한 옵션이 없습니다." : product.optionGroups.length > 0 && Object.keys(selection).length === product.optionGroups.length ? "선택한 옵션 조합은 준비되어 있지 않습니다. 다른 옵션을 선택해 주세요." : "옵션을 모두 선택하면 가격과 구매 가능 여부를 확인할 수 있어요."}</p>}</div>
    {selectedSku?.subscribable ? <aside className="subscription-callout" aria-label="정기배송 안내"><strong>정기배송 가능한 옵션</strong><span>일반 구매 후 승인된 정기배송 흐름에서 다시 확인할 수 있어요.</span></aside> : null}
    <label className="form-field" htmlFor={`${id}-quantity`}>수량<input id={`${id}-quantity`} className="input" type="number" min="1" step="1" max={selectedSku?.availableQuantity} disabled={busy || !selectedSku?.purchasable} aria-invalid={Boolean(quantityError)} aria-describedby={quantityError ? `${id}-quantity-error` : undefined} value={props.quantity} onChange={(event) => props.onQuantityChange(event.target.value)} /></label>
    {quantityError ? <p id={`${id}-quantity-error`} className="field-error" role="alert">{quantityError}</p> : null}
    {props.wishlistLoginHref ? <div className="wishlist-login-notice"><p>위시리스트는 로그인한 계정에 저장돼요. 로그인 후 이 상품을 다시 확인해 주세요.</p><Link className="button button-secondary" href={props.wishlistLoginHref}>로그인하기</Link></div> : <div className="button-row purchase-actions"><button className="button button-secondary" type="button" aria-pressed={props.wishlistStatus === "ready" ? props.wishlisted : undefined} disabled={busy || props.wishlistStatus === "loading" || props.wishlistStatus === "error"} onClick={props.onWishlist}>{props.wishlistStatus === "loading" ? "찜 확인 중…" : props.wishlistStatus === "error" ? "찜 상태 확인 불가" : props.wishlisted ? "찜 해제" : "위시리스트에 담기"}</button><button className="button button-primary" type="button" disabled={busy || !selectedSku?.purchasable || Boolean(quantityError)} onClick={props.onCart}>{busy ? "담는 중" : "장바구니에 담기"}</button></div>}
    {props.wishlistLoginHref ? <button className="button button-primary" type="button" disabled={busy || !selectedSku?.purchasable || Boolean(quantityError)} onClick={props.onCart}>{busy ? "담는 중" : "장바구니에 담기"}</button> : null}
    {props.wishlistStatus === "error" ? <div role="alert"><p className="field-error">위시리스트 상태를 확인하지 못했습니다. 장바구니는 계속 사용할 수 있습니다.</p><button className="button button-secondary" type="button" disabled={busy} onClick={props.onWishlistRetry}>찜 상태 다시 확인</button></div> : null}
    {props.message ? <p className={props.messageKind === "success" ? "notice-success" : "error-summary"} role={props.messageKind === "success" ? "status" : "alert"}>{props.message}{props.messageKind === "success" && props.message.includes("장바구니") ? <> <Link href="/cart">장바구니 보기</Link></> : null}</p> : null}
  </section>;
}

export function ProductPurchaseSheet({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const dialog = dialogRef.current!;
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    dialog.showModal();
    document.body.style.overflow = "hidden";
    closeRef.current?.focus();
    return () => { dialog.close(); document.body.style.overflow = previousOverflow; opener?.focus(); };
  }, []);
  return <dialog className="purchase-sheet" ref={dialogRef} aria-label="상품 옵션과 구매" onPointerDown={(event) => { if (event.target === event.currentTarget) { const box = event.currentTarget.getBoundingClientRect(); if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) onClose(); } }} onCancel={(event) => { event.preventDefault(); onClose(); }}>
    <div className="purchase-sheet-heading"><strong>상품 구매</strong><button className="button button-secondary" ref={closeRef} onClick={onClose}>닫기</button></div>{children}
  </dialog>;
}
