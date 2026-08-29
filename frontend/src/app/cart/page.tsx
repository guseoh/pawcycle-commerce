"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi, type CartItem, type PricingBreakdown } from "@/lib/commerce-final-api";
import { buildLoginHref, cartQuantityErrorForMaximum, cartQuantityForUpdate, formatPrice, notifyCommerceChanged, userFacingCatalogLabel } from "@/lib/frontend-utils";

export default function CartPage() {
  const auth = useAuth();
  const [items, setItems] = useState<CartItem[] | null>(null);
  const [pricing, setPricing] = useState<PricingBreakdown | null>(null);
  const [error, setError] = useState<string | null>(null);
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
        setError(null);
      })
      .catch((reason: unknown) => {
        if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") {
          auth.markAnonymous();
          return;
        }
        setError("장바구니를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
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
    try {
      await auth.executeWithCsrf((csrf) => commerceFinalApi.updateCart(item.skuId, quantity, csrf));
      notifyCommerceChanged();
      load();
    } catch {
      setError("수량을 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(null);
    }
  }

  async function remove(skuId: number) {
    if (busy !== null) return;
    setBusy(skuId);
    try {
      await auth.executeWithCsrf((csrf) => commerceFinalApi.deleteCart(skuId, csrf));
      notifyCommerceChanged();
      load();
    } catch {
      setError("상품을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(null);
    }
  }

  const unavailableItems = items?.filter((item) => !item.purchasable) ?? [];

  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="장바구니를 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/cart")}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (items === null && !error) return <LoadingState>장바구니를 불러오고 있습니다.</LoadingState>;
  if (error) return <ErrorState title="장바구니를 불러오지 못했습니다." message={error} onRetry={load} />;

  return <section>
    <header className="page-heading"><p className="eyebrow">Cart</p><h1>장바구니</h1><p>상품 옵션, 수량, 현재 가격과 구매 가능 상태를 확인하세요.</p></header>
    {unavailableItems.length > 0 ? <p className="error-summary" role="alert">구매할 수 없는 상품이 있어요. 수량을 줄이거나 상품을 삭제한 뒤 주문을 진행해 주세요.</p> : null}
    <div className="cart-layout">
      <section className="section-card cart-items-panel" aria-labelledby="cart-items-title">
        <div className="section-title"><h2 id="cart-items-title">담은 상품 <span className="count-badge">{items?.length ?? 0}</span></h2></div>
        {items?.length ? <ul className="cart-item-list">{items.map((item) => {
          const stockMessage = !item.purchasable
            ? item.availableQuantity < item.quantity
              ? `현재 재고 ${item.availableQuantity.toLocaleString()}개 · 수량을 조정해 주세요.`
              : "현재 구매할 수 없는 상품입니다."
            : `재고 ${item.availableQuantity.toLocaleString()}개 · 구매 가능`;
          const productName = userFacingCatalogLabel(item.productName, "상품");
          const skuName = userFacingCatalogLabel(item.skuName, "상품 옵션");
          return <li className="cart-item" key={item.skuId}>
            <div className="cart-item-visual"><span className="image-placeholder" aria-hidden="true">PawCycle</span></div>
            <div className="cart-item-main"><Link href={`/products/${item.productId}`}><strong>{productName}</strong></Link><span className="cart-sku">{skuName} · 옵션 단가 {formatPrice(item.unitPrice ?? item.price)}</span><span className={`cart-availability${item.purchasable ? " is-available" : " is-unavailable"}`}>{stockMessage}</span><strong className="cart-price">상품 금액 {formatPrice(item.lineAmount)}</strong></div>
            <div className="cart-item-controls"><label htmlFor={`cart-quantity-${item.skuId}`}>수량<input id={`cart-quantity-${item.skuId}`} className="input" type="number" min="1" max={item.availableQuantity > 0 ? item.availableQuantity : undefined} inputMode="numeric" aria-describedby={quantityErrors[item.skuId] ? `cart-quantity-error-${item.skuId}` : undefined} aria-invalid={quantityErrors[item.skuId] ? "true" : undefined} value={draft[item.skuId] ?? String(item.quantity)} disabled={busy === item.skuId} onChange={(event) => updateDraft(item.skuId, event.target.value, item.availableQuantity)} /></label>{quantityErrors[item.skuId] ? <p id={`cart-quantity-error-${item.skuId}`} className="field-error" role="alert">{quantityErrors[item.skuId]}</p> : null}<div className="button-row"><button className="button button-secondary" type="button" disabled={busy !== null || quantityErrors[item.skuId] !== undefined} onClick={() => void applyQuantity(item)}>수량 적용</button><button className="button button-danger" type="button" disabled={busy !== null} onClick={() => void remove(item.skuId)}>삭제</button></div></div>
          </li>;
        })}</ul> : <div className="empty-callout"><strong>장바구니가 비어 있어요.</strong><Link href="/products">상품 둘러보기</Link></div>}
      </section>
      <aside className="section-card cart-summary" aria-labelledby="cart-summary-title">
        <p className="eyebrow">주문 준비</p><h2 id="cart-summary-title">현재 주문 금액</h2>
        {pricing ? <dl className="price-summary"><div><dt>상품 금액</dt><dd>{formatPrice(pricing.originalAmount)}</dd></div><div><dt>할인</dt><dd>{pricing.discountAmount ? `-${formatPrice(pricing.discountAmount)}` : formatPrice(0)}</dd></div><div><dt>배송비</dt><dd>{pricing.shippingFee ? formatPrice(pricing.shippingFee) : "무료"}</dd></div><div className="price-summary-total"><dt>예상 결제 금액</dt><dd>{formatPrice(pricing.paymentAmount)}</dd></div></dl> : null}
        <p className="field-help">쿠폰·주소·재고와 최종 결제 금액은 주문 직전에 한 번 더 확인합니다.</p>
        {items?.length && unavailableItems.length === 0 ? <Link className="button button-primary" href="/checkout">주문 확인으로 이동</Link> : <Link className="button button-secondary" href="/products">상품 둘러보기</Link>}
      </aside>
    </div>
  </section>;
}
