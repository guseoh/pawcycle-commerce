"use client";

import Link from "next/link";
import { useEffect, useRef, type ReactNode } from "react";
import type { ProductDetail, ProductSku } from "@/lib/api";
import type { OptionSelection } from "@/lib/product-selection";
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
  onWishlist: () => void;
  onCart: () => void;
  subscriptionHref: string | null;
  message: string | null;
  messageKind: "success" | "error";
}

export function ProductPurchasePanel(props: PurchasePanelProps) {
  const { id, product, selectedSku, selection, quantityError, busy } = props;
  return <section className="purchase-panel" aria-labelledby={`${id}-title`}>
    <p className="eyebrow">Your everyday essentials</p><h2 id={`${id}-title`}>옵션과 수량 선택</h2>
    {product.optionGroups.length ? product.optionGroups.map((group) => <fieldset className="product-option-group" key={group.optionGroupId}><legend>{group.name}</legend><div className="catalog-toggle-row">{group.values.map((value) => <button type="button" disabled={busy} key={value.optionValueId} aria-pressed={selection[group.optionGroupId] === value.optionValueId} onClick={() => props.onSelect({ ...selection, [group.optionGroupId]: value.optionValueId })}>{selection[group.optionGroupId] === value.optionValueId ? "✓ " : ""}{value.value}</button>)}</div></fieldset>) : product.skus.length > 1 ? <label className="form-field">상품 옵션<select className="input" value={selectedSku?.skuId ?? ""} disabled={busy} onChange={(event) => props.onLegacySelect(Number(event.target.value) || null)}><option value="">선택하세요</option>{product.skus.map((sku) => <option key={sku.skuId} value={sku.skuId}>{userFacingCatalogLabel(sku.skuName, "상품 옵션")} · {formatPrice(sku.price)}{sku.purchasable ? "" : " · 구매 불가"}</option>)}</select></label> : null}
    <div className="selection-summary" aria-live="polite" aria-atomic="true">{selectedSku ? <><p>{userFacingCatalogLabel(selectedSku.skuName, "선택한 옵션")}</p><CatalogPrice price={selectedSku.price} compareAtPrice={selectedSku.compareAtPrice} discountRate={selectedSku.discountRate} /><p>{selectedSku.purchasable ? `구매 가능 · 재고 ${selectedSku.availableQuantity}개` : "현재 품절 · 구매 불가"}</p></> : <p>{product.skus.length === 0 ? "현재 구매 가능한 옵션이 없습니다." : product.optionGroups.length > 0 && Object.keys(selection).length === product.optionGroups.length ? "선택한 옵션 조합은 준비되어 있지 않습니다. 다른 옵션을 선택해 주세요." : "옵션을 모두 선택하면 가격과 구매 가능 여부를 확인할 수 있어요."}</p>}</div>
    <label className="form-field" htmlFor={`${id}-quantity`}>수량<input id={`${id}-quantity`} className="input" type="number" min="1" step="1" max={selectedSku?.availableQuantity} disabled={busy || !selectedSku?.purchasable} aria-invalid={Boolean(quantityError)} aria-describedby={quantityError ? `${id}-quantity-error` : undefined} value={props.quantity} onChange={(event) => props.onQuantityChange(event.target.value)} /></label>
    {quantityError ? <p id={`${id}-quantity-error`} className="field-error" role="alert">{quantityError}</p> : null}
    <div className="button-row purchase-actions"><button className="button button-secondary" type="button" aria-pressed={props.wishlisted} disabled={busy} onClick={props.onWishlist}>{props.wishlisted ? "찜 해제" : "위시리스트에 담기"}</button><button className="button button-primary" type="button" disabled={busy || !selectedSku?.purchasable || Boolean(quantityError)} onClick={props.onCart}>{busy ? "처리 중…" : "장바구니에 담기"}</button></div>
    {props.subscriptionHref && selectedSku?.subscribable ? <div className="purchase-subscription"><p>이 옵션은 정기배송이 가능해요.{selectedSku.availableDeliveryCycles.length ? ` 배송 주기: ${selectedSku.availableDeliveryCycles.join(" / ")}주` : ""}</p><Link className="button button-secondary" href={props.subscriptionHref}>이 옵션 정기배송 시작</Link></div> : <p className="field-help">{selectedSku ? "이 옵션은 정기배송을 지원하지 않습니다." : "옵션 선택 후 정기배송 가능 여부를 확인해 주세요."}</p>}
    {props.message ? <p className={props.messageKind === "success" ? "notice-success" : "error-summary"} role={props.messageKind === "success" ? "status" : "alert"}>{props.message}</p> : null}
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
  return <dialog className="purchase-sheet" ref={dialogRef} aria-label="상품 옵션과 구매" onCancel={(event) => { event.preventDefault(); onClose(); }}>
    <div className="purchase-sheet-heading"><strong>상품 구매</strong><button className="button button-secondary" ref={closeRef} onClick={onClose}>닫기</button></div>{children}
  </dialog>;
}
