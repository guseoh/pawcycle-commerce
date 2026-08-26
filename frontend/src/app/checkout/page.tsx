"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatPrice, notifyCommerceChanged } from "@/lib/frontend-utils";
import { commerceFinalApi, type Address, type CartItem, type CheckoutResult, type MemberCoupon, type PricingBreakdown } from "@/lib/commerce-final-api";
import { newIdempotencyKey } from "@/lib/v2-api";
import { TossPaymentWidget } from "@/components/toss-payment-widget";

function PriceSummary({ pricing, couponSelected }: { pricing: PricingBreakdown; couponSelected: boolean }) {
  return <dl className="price-summary"><div><dt>상품 금액</dt><dd>{formatPrice(pricing.originalAmount)}</dd></div><div><dt>할인</dt><dd>{couponSelected && pricing.discountAmount === 0 ? "주문 시 확정" : pricing.discountAmount ? `-${formatPrice(pricing.discountAmount)}` : formatPrice(0)}</dd></div><div><dt>배송비</dt><dd>{pricing.shippingFee ? formatPrice(pricing.shippingFee) : "무료"}</dd></div><div className="price-summary-total"><dt>최종 결제 금액</dt><dd>{formatPrice(pricing.paymentAmount)}</dd></div></dl>;
}

export default function CheckoutPage() {
  const auth = useAuth();
  const [cart, setCart] = useState<CartItem[] | null>(null);
  const [cartVersion, setCartVersion] = useState<number | null>(null);
  const [pricing, setPricing] = useState<PricingBreakdown | null>(null);
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [coupons, setCoupons] = useState<MemberCoupon[]>([]);
  const [couponError, setCouponError] = useState<string | null>(null);
  const [addressId, setAddressId] = useState<number | null>(null);
  const [couponId, setCouponId] = useState<number | null>(null);
  const [result, setResult] = useState<CheckoutResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState(false);
  const key = useRef<string | null>(null);
  const keyIdentity = useRef<string | null>(null);

  const load = useCallback(async (): Promise<boolean> => {
    if (auth.status !== "authenticated") return false;
    try {
      const [cartResult, addressResult] = await Promise.all([commerceFinalApi.cart(), commerceFinalApi.addresses()]);
      setCart(cartResult.items);
      setCartVersion(cartResult.version);
      setPricing(cartResult.pricing);
      setAddresses(addressResult);
      setAddressId((current) => {
        if (current !== null && addressResult.some((address) => address.addressId === current)) return current;
        return addressResult.find((address) => address.isDefault)?.addressId ?? addressResult[0]?.addressId ?? null;
      });
      setError(null);
      try {
        const couponResult = await commerceFinalApi.coupons();
        const availableCoupons = couponResult.filter((coupon) => coupon.status === "AVAILABLE");
        setCoupons(availableCoupons);
        setCouponId((current) => current !== null && availableCoupons.some((coupon) => coupon.memberCouponId === current) ? current : null);
        setCouponError(null);
      } catch (reason) {
        setCoupons([]);
        setCouponId(null);
        setCouponError(reason instanceof ApiError ? reason.message : "사용 가능한 쿠폰을 확인하지 못했습니다.");
      }
      return true;
    } catch (reason) {
      setCart(null);
      setCartVersion(null);
      setPricing(null);
      if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") {
        auth.markAnonymous();
        return false;
      }
      setError(reason instanceof ApiError ? reason.message : "주문 정보를 불러오지 못했습니다.");
      return false;
    }
  }, [auth]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load, retry]);

  function chooseAddress(value: string) {
    setAddressId(Number(value) || null);
    key.current = null;
    keyIdentity.current = null;
    setResult(null);
  }

  function chooseCoupon(value: string) {
    setCouponId(Number(value) || null);
    key.current = null;
    keyIdentity.current = null;
    setResult(null);
  }

  async function checkout() {
    if (!addressId || !cart?.length || busy) return;
    if (cart.some((item) => !item.purchasable)) {
      setError("구매할 수 없는 상품이 있습니다. 장바구니에서 상품 상태를 확인해 주세요.");
      return;
    }
    if (cartVersion === null) {
      setError("장바구니 버전을 확인하지 못했습니다. 다시 불러와 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const identity = `${addressId}|${couponId ?? "none"}|${cartVersion}`;
      if (keyIdentity.current !== identity) {
        key.current = newIdempotencyKey();
        keyIdentity.current = identity;
      }
      const response = await auth.executeWithCsrf((csrf) => commerceFinalApi.checkout(addressId, csrf, key.current!, couponId ?? undefined, cartVersion));
      setResult(response);
      notifyCommerceChanged();
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === "CART_CHANGED") {
        key.current = null;
        keyIdentity.current = null;
        setResult(null);
        setCartVersion(null);
        const refreshed = await load();
        setError(refreshed
          ? "장바구니가 변경되었습니다. 현재 장바구니를 확인한 뒤 다시 주문해 주세요."
          : "장바구니가 변경되었지만 최신 정보를 다시 불러오지 못했습니다. 장바구니를 다시 불러온 뒤 주문해 주세요.");
      } else if (reason instanceof ApiError && reason.code === "IDEMPOTENCY_KEY_CONFLICT") {
        key.current = null;
        keyIdentity.current = null;
        setResult(null);
        setError("주문 요청 정보가 이전 요청과 달라졌습니다. 새 주문으로 다시 시도해 주세요.");
      } else {
        // Network/unknown failures keep this key so the same request identity can replay safely.
        setError(reason instanceof ApiError ? reason.message : "주문 결과를 확인하지 못했습니다. 같은 요청으로 다시 시도해 주세요.");
        if (reason instanceof ApiError && ["INVENTORY_CONFLICT", "INVENTORY_INSUFFICIENT", "SKU_NOT_PURCHASABLE"].includes(reason.code)) void load();
      }
    } finally {
      setBusy(false);
    }
  }

  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="주문하려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/checkout")}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (error && (!cart || !addresses)) return <ErrorState title="주문 정보를 불러오지 못했습니다." message={error} onRetry={() => setRetry((value) => value + 1)} />;
  if (!cart || !addresses || !pricing) return <LoadingState>주문 정보를 불러오고 있습니다.</LoadingState>;

  if (result) {
    const confirmedPricing = result.pricing ?? { ...pricing, paymentAmount: result.amount, finalAmount: result.amount };
    return <section className="checkout-success section-card"><p className="eyebrow">주문 생성</p><h1>주문이 생성되었습니다.</h1><p>{result.orderName} · 주문 번호 {result.orderNumber}</p><PriceSummary pricing={confirmedPricing} couponSelected={Boolean(couponId)} /><TossPaymentWidget checkout={result} /><Link className="button button-secondary" href={`/orders/${result.orderId}`}>주문 상세 확인</Link></section>;
  }

  const unavailable = cart.filter((item) => !item.purchasable);
  return <section>
    <header className="page-heading"><p className="eyebrow">Checkout</p><h1>주문 확인</h1><p>상품, 배송지, 쿠폰을 확인한 뒤 서버가 최종 금액과 재고를 다시 검증합니다.</p></header>
    {error ? <p className="error-summary" role="alert">{error}</p> : null}
    <div className="checkout-layout"><section className="section-card checkout-items" aria-labelledby="checkout-items-title"><div className="section-title"><h2 id="checkout-items-title">주문 상품</h2><span className="count-badge">{cart.length}개</span></div>{cart.length ? <ul className="checkout-item-list">{cart.map((item) => <li key={item.skuId}><div><strong>{item.productName}</strong><span>{item.skuName} · {item.quantity}개</span><span className={item.purchasable ? "cart-availability is-available" : "cart-availability is-unavailable"}>{item.purchasable ? `구매 가능 · 재고 ${item.availableQuantity}개` : `구매 불가 · 현재 재고 ${item.availableQuantity}개`}</span></div><strong>{formatPrice(item.lineAmount)}</strong></li>)}</ul> : <div className="empty-callout">장바구니가 비어 있어요. <Link href="/products">상품을 먼저 담아주세요.</Link></div>}<Link className="text-link" href="/cart">장바구니에서 수량 변경</Link></section>
      <aside className="section-card checkout-sidebar"><div className="checkout-step"><span className="step-number">1</span><div><h2>배송지 선택</h2><label className="form-field"><span className="field-label">받는 주소</span><select className="input" value={addressId ?? ""} onChange={(event) => chooseAddress(event.target.value)}><option value="">배송지를 선택하세요</option>{addresses.map((address) => <option key={address.addressId} value={address.addressId}>{address.name || address.recipientName} · {address.addressLine1}</option>)}</select></label>{!addresses.length ? <Link href="/addresses">배송지 등록하기 →</Link> : null}</div></div><div className="checkout-step"><span className="step-number">2</span><div><h2>쿠폰 선택</h2>{coupons.length ? <label className="form-field"><span className="field-label">사용할 쿠폰</span><select className="input" value={couponId ?? ""} onChange={(event) => chooseCoupon(event.target.value)}><option value="">쿠폰 사용 안 함</option>{coupons.map((coupon) => <option key={coupon.memberCouponId} value={coupon.memberCouponId}>{coupon.name} · {coupon.discountType === "PERCENTAGE" ? `${coupon.discountValue}% 할인` : formatPrice(coupon.discountValue)}</option>)}</select></label> : <p className="field-help">사용 가능한 쿠폰이 없습니다.</p>}{couponError ? <p className="field-help">{couponError}</p> : null}</div></div><div className="checkout-step"><span className="step-number">3</span><div><h2>금액 확인</h2><PriceSummary pricing={pricing} couponSelected={Boolean(couponId)} /><p className="field-help">쿠폰 할인과 재고 충돌 여부는 주문 요청 시 서버가 확정합니다.</p><button className="button button-primary" type="button" disabled={!cart.length || !addressId || unavailable.length > 0 || busy} onClick={() => void checkout()}>{busy ? "주문 검증 중" : "주문 생성"}</button></div></div></aside>
    </div>
  </section>;
}