"use client";

import Link from "next/link";
import { CommerceOverlay } from "@/components/commerce-overlay";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi, type CartItem, type PricingBreakdown } from "@/lib/commerce-final-api";
import { buildLoginHref, cartQuantityErrorForMaximum, cartQuantityForUpdate, formatPrice, notifyCommerceChanged } from "@/lib/frontend-utils";

type CartMutationError = { operation: "update" | "delete"; message: string };

export default function CartPage() {
  const auth = useAuth();
  const [deleteCandidate, setDeleteCandidate] = useState<CartItem | null>(null);
  const [items, setItems] = useState<CartItem[] | null>(null);
  const [pricing, setPricing] = useState<PricingBreakdown | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [itemErrors, setItemErrors] = useState<Record<number, CartMutationError>>({});
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState<number | null>(null);
  const [draft, setDraft] = useState<Record<number, string>>({});
  const [quantityErrors, setQuantityErrors] = useState<Record<number, string>>({});

  const load = useCallback(() => {
    void commerceFinalApi.cart()
      .then((result) => {
        setItems(result.items);
        setPricing(result.pricing);
        setDraft(Object.fromEntries(result.items.map((item) => [item.skuId, String(item.quantity)])));
        setQuantityErrors({});
        setLoadError(null);
      })
      .catch((reason: unknown) => {
        if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") {
          auth.markAnonymous();
          return;
        }
        setLoadError(reason instanceof ApiError ? reason.message : "장바구니를 불러오지 못했습니다.");
      });
  }, [auth]);

  useEffect(() => {
    if (auth.status === "authenticated") load();
  }, [auth.status, load]);

  function updateDraft(skuId: number, raw: string, maximum: number) {
    setDraft((value) => ({ ...value, [skuId]: raw }));
    const validationError = cartQuantityErrorForMaximum(raw, maximum);
    setQuantityErrors((value) => {
      if (validationError) return { ...value, [skuId]: validationError };
      const next = { ...value };
      delete next[skuId];
      return next;
    });
  }

  async function applyQuantity(item: CartItem) {
    if (busy !== null) return;
    const raw = draft[item.skuId] ?? String(item.quantity);
    const quantity = cartQuantityForUpdate(raw, item.availableQuantity);
    if (quantity === null) {
      setQuantityErrors((value) => ({ ...value, [item.skuId]: cartQuantityErrorForMaximum(raw, item.availableQuantity)! }));
      return;
    }
    setBusy(item.skuId);
    setItemErrors((current) => { const next = { ...current }; delete next[item.skuId]; return next; });
    try {
      await auth.executeWithCsrf((csrf) => commerceFinalApi.updateCart(item.skuId, quantity, csrf));
      notifyCommerceChanged();
      load();
      setStatusMessage(`${item.productName} 수량을 ${quantity}개로 바꿨어요. 금액을 다시 계산했습니다.`);
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === "CART_CHANGED") {
        load();
        setStatusMessage("다른 화면에서 장바구니가 변경됐어요. 최신 상품과 금액을 다시 불러왔습니다. 다시 확인해 주세요.");
      } else setItemErrors((current) => ({ ...current, [item.skuId]: { operation: "update", message: reason instanceof ApiError ? reason.message : "수량을 바꾸지 못했어요. 기존 수량은 그대로예요." } }));
    } finally {
      setBusy(null);
    }
  }

  async function remove(item: CartItem) {
    if (busy !== null) return;
    setBusy(item.skuId);
    setItemErrors((current) => { const next = { ...current }; delete next[item.skuId]; return next; });
    try {
      await auth.executeWithCsrf((csrf) => commerceFinalApi.deleteCart(item.skuId, csrf));
      notifyCommerceChanged();
      load();
      setStatusMessage(`${item.productName}을 장바구니에서 삭제했어요. 금액을 다시 계산했습니다.`);
    } catch (reason) {
      setItemErrors((current) => ({ ...current, [item.skuId]: { operation: "delete", message: reason instanceof ApiError ? reason.message : "상품을 삭제하지 못했어요. 기존 장바구니는 그대로예요." } }));
    } finally {
      setBusy(null);
    }
  }

  const hasUnappliedQuantity = items?.some(item => (draft[item.skuId] ?? String(item.quantity)) !== String(item.quantity)) ?? false;
  const unavailableItems = items?.filter((item) => !item.purchasable) ?? [];

  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="장바구니를 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/cart")}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (items === null && !loadError) return <LoadingState>장바구니를 불러오고 있습니다.</LoadingState>;
  if (loadError) return <ErrorState title="장바구니를 불러오지 못했습니다." message={loadError} onRetry={load} />;

  return <section>
    <header className="page-heading"><p className="eyebrow">Cart</p><h1>장바구니</h1><p>상품 옵션, 수량, 현재 가격과 구매 가능 상태를 확인하세요.</p></header>
    {unavailableItems.length > 0 ? <p className="error-summary" role="alert">구매할 수 없는 상품이 있어요. 수량을 줄이거나 상품을 삭제한 뒤 주문을 진행해 주세요.</p> : null}
    {statusMessage ? <p className="notice-success" role="status">{statusMessage}</p> : null}
    {hasUnappliedQuantity ? <p className="provider-block" role="status">변경한 수량을 적용해 주세요. 표시 금액은 마지막으로 확인한 값입니다.</p> : null}
    <div className="cart-layout">
      <section className="section-card cart-items-panel" aria-labelledby="cart-items-title">
        <div className="section-title"><h2 id="cart-items-title">담은 상품 <span className="count-badge">{items?.length ?? 0}</span></h2></div>
        {items?.length ? <ul className="cart-item-list">{items.map((item) => {
          const stockMessage = !item.purchasable
            ? item.availableQuantity < item.quantity
              ? `현재 재고 ${item.availableQuantity.toLocaleString()}개 · 수량을 조정해 주세요.`
              : "현재 구매할 수 없는 상품입니다."
            : `재고 ${item.availableQuantity.toLocaleString()}개 · 구매 가능`;
          return <li className="cart-item" key={item.skuId}>
            <div className="cart-item-main"><Link href={`/products/${item.productId}`}><strong>{item.productName}</strong></Link><span className="cart-sku">{item.skuName} · 옵션 단가 {formatPrice(item.unitPrice ?? item.price)}</span><span className={`cart-availability${item.purchasable ? " is-available" : " is-unavailable"}`}>{stockMessage}</span><strong className="cart-price">상품 금액 {formatPrice(item.lineAmount)}</strong></div>
            <div className="cart-item-controls"><label htmlFor={`cart-quantity-${item.skuId}`}>수량<input id={`cart-quantity-${item.skuId}`} className="input" type="number" min="1" max={item.availableQuantity > 0 ? item.availableQuantity : undefined} inputMode="numeric" aria-describedby={quantityErrors[item.skuId] || itemErrors[item.skuId] ? `cart-quantity-error-${item.skuId}` : undefined} aria-invalid={quantityErrors[item.skuId] || itemErrors[item.skuId] ? "true" : undefined} value={draft[item.skuId] ?? String(item.quantity)} disabled={busy === item.skuId} onChange={(event) => updateDraft(item.skuId, event.target.value, item.availableQuantity)} /></label>{quantityErrors[item.skuId] || itemErrors[item.skuId] ? <p id={`cart-quantity-error-${item.skuId}`} className="field-error" role="alert">{quantityErrors[item.skuId] ?? itemErrors[item.skuId]?.message} {itemErrors[item.skuId] ? <button type="button" onClick={() => void (itemErrors[item.skuId].operation === "delete" ? remove(item) : applyQuantity(item))}>다시 시도</button> : null}</p> : null}<div className="button-row"><button className="button button-secondary" type="button" disabled={busy !== null || quantityErrors[item.skuId] !== undefined} onClick={() => void applyQuantity(item)}>{busy === item.skuId ? "계산 중" : "수량 적용"}</button><button className="button button-danger" type="button" disabled={busy !== null} aria-label={`${item.productName} 삭제`} onClick={() => setDeleteCandidate(item)}>삭제</button></div></div>
          </li>;
        })}</ul> : <div className="empty-callout"><strong>장바구니가 비어 있어요.</strong><Link href="/products">상품 둘러보기</Link></div>}
      </section>
      {items?.length ? <aside className="section-card cart-summary" aria-labelledby="cart-summary-title">
        <p className="eyebrow">주문 준비</p><h2 id="cart-summary-title">현재 주문 금액</h2>
        {pricing ? <dl className="price-summary"><div><dt>상품 금액</dt><dd>{formatPrice(pricing.originalAmount)}</dd></div><div><dt>할인</dt><dd>{pricing.discountAmount ? `-${formatPrice(pricing.discountAmount)}` : formatPrice(0)}</dd></div><div><dt>배송비</dt><dd>{pricing.shippingFee ? formatPrice(pricing.shippingFee) : "무료"}</dd></div><div className="price-summary-total"><dt>예상 결제 금액</dt><dd>{formatPrice(pricing.paymentAmount)}</dd></div></dl> : null}
        <p className="field-help">쿠폰·주소·재고와 최종 결제 금액은 주문 직전에 다시 확인합니다.</p>
        {items?.length && unavailableItems.length === 0 && !hasUnappliedQuantity ? <Link className="button button-primary" href="/checkout">장바구니 전체 주문하기</Link> : <Link className="button button-secondary" href="/products">상품 둘러보기</Link>}
      </aside> : null}
    </div>
    {items?.length && unavailableItems.length === 0 && !hasUnappliedQuantity && pricing ? <div className="mobile-transaction-bar"><strong>총 {items.reduce((total, item) => total + item.quantity, 0)}개 · {formatPrice(pricing.paymentAmount)}</strong><Link className="button button-primary" href="/checkout">전체 주문하기</Link></div> : null}
    {deleteCandidate ? <CommerceOverlay label="장바구니 상품 삭제" className="confirmation-dialog" onClose={() => setDeleteCandidate(null)}><h2>상품을 삭제할까요?</h2><p>{deleteCandidate.productName}</p><div className="button-row"><button className="button button-secondary" autoFocus onClick={() => setDeleteCandidate(null)}>취소</button><button className="button button-danger" onClick={() => { const item = deleteCandidate; setDeleteCandidate(null); void remove(item); }}>삭제</button></div></CommerceOverlay> : null}
  </section>;
}
