"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { LogoutControl } from "@/components/logout-control";
import { ApiError } from "@/lib/api";
import { commerceFinalApi, type OrderSummary } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatDateTime, formatIsoLocalDate, formatOrderStatus, formatPrice } from "@/lib/frontend-utils";
import { v2Api, type V2SubscriptionSummary } from "@/lib/v2-api";

type CommerceSnapshot = { orders: OrderSummary[]; subscriptions: V2SubscriptionSummary[]; cartQuantity: number; unreadNotifications: number };

async function loadAllSubscriptions(): Promise<V2SubscriptionSummary[]> {
  const pageSize = 100;
  const first = await v2Api.subscriptions.list(0, pageSize);
  const subscriptions = [...first.body.items];
  for (let page = 1; subscriptions.length < first.body.totalElements; page += 1) {
    const next = await v2Api.subscriptions.list(page, pageSize);
    if (!next.body.items.length) break;
    subscriptions.push(...next.body.items);
  }
  return subscriptions;
}

function CommerceSnapshotPanel({ snapshot, error, onRetry }: { snapshot: CommerceSnapshot | null; error: string | null; onRetry: () => void }) {
  if (error) return <section className="section-card"><ErrorState title="Commerce 요약을 불러오지 못했습니다." message={error} onRetry={onRetry} /></section>;
  if (!snapshot) return <section className="section-card"><LoadingState>내 Commerce 요약을 불러오고 있습니다.</LoadingState></section>;
  const latestOrder = snapshot.orders[0];
  const activeSubscriptions = snapshot.subscriptions.filter((item) => item.status === "ACTIVE");
  const nextSubscription = activeSubscriptions
    .filter((item) => item.nextScheduledDate)
    .sort((left, right) => left.nextScheduledDate!.localeCompare(right.nextScheduledDate!))[0];
  return <section className="section-card my-commerce-snapshot" aria-labelledby="commerce-snapshot-title"><div className="section-title"><div><p className="eyebrow">My Commerce</p><h2 id="commerce-snapshot-title">최근 활동</h2></div><Link className="text-link" href="/orders">전체 주문 보기</Link></div><div className="snapshot-grid"><Link className="snapshot-tile" href="/orders"><strong>{snapshot.orders.length}건</strong><span>주문 내역</span></Link><Link className="snapshot-tile" href="/subscriptions"><strong>{activeSubscriptions.length}개</strong><span>이용 중인 정기배송</span></Link><Link className="snapshot-tile" href="/cart"><strong>{snapshot.cartQuantity}개</strong><span>장바구니 상품</span></Link><Link className="snapshot-tile" href="/notifications"><strong>{snapshot.unreadNotifications}건</strong><span>읽지 않은 알림</span></Link></div>{latestOrder ? <div className="snapshot-detail"><span>최근 주문</span><Link href={`/orders/${latestOrder.orderId}`}><strong>주문 {latestOrder.orderNumber}</strong><span>{formatOrderStatus(latestOrder.status)} · {formatPrice(latestOrder.paymentAmount)} · {formatDateTime(latestOrder.createdAt)}</span></Link></div> : <div className="empty-callout"><span>아직 주문이 없습니다.</span><Link href="/products">상품 둘러보기 →</Link></div>}{nextSubscription ? <div className="snapshot-detail"><span>다음 정기배송</span><Link href={`/subscriptions/${nextSubscription.subscriptionId}`}><strong>{nextSubscription.pet?.name ?? "정기배송"}</strong><span>{formatIsoLocalDate(nextSubscription.nextScheduledDate!)} · {formatPrice(nextSubscription.currentSnapshot.packagePriceKrw)}</span></Link></div> : null}</section>;
}

export default function MyPage() {
  const auth = useAuth();
  const [snapshot, setSnapshot] = useState<CommerceSnapshot | null>(null);
  const [snapshotError, setSnapshotError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  const loadSnapshot = useCallback(async () => {
    if (auth.status !== "authenticated") return;
    try {
      const [orders, subscriptions, cart, notifications] = await Promise.all([commerceFinalApi.orders(), loadAllSubscriptions(), commerceFinalApi.cart(), commerceFinalApi.notifications()]);
      setSnapshot({ orders, subscriptions, cartQuantity: cart.items.reduce((total, item) => total + item.quantity, 0), unreadNotifications: notifications.filter((item) => !item.readAt).length });
      setSnapshotError(null);
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") { auth.markAnonymous(); return; }
      setSnapshotError(reason instanceof ApiError ? reason.message : "내 Commerce 요약을 불러오지 못했습니다.");
    }
  }, [auth]);
  useEffect(() => {
    const timer = window.setTimeout(() => void loadSnapshot(), 0);
    return () => window.clearTimeout(timer);
  }, [loadSnapshot, retry]);

  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="내 정보를 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/my")}>로그인</Link></ErrorState>;
  if (auth.status === "loading") return <LoadingState>회원 정보를 확인하고 있습니다.</LoadingState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "로그인 상태를 확인할 수 없습니다."} onRetry={() => void auth.refresh()} />;
  const links = [["/subscriptions", "정기배송", "다음 배송과 구독 변경을 관리해요"], ["/orders", "주문", "주문과 배송 상태를 확인해요"], ["/wishlist", "위시리스트", "나중에 볼 상품을 모아뒀어요"], ["/cart", "장바구니", "선택한 상품을 주문해요"], ["/addresses", "배송지", "배송지와 기본 주소를 관리해요"], ["/billing-methods", "결제수단", "등록 상태를 확인해요"], ["/notifications", "알림", "주문과 배송 소식을 확인해요"]] as const;
  return <section><header className="page-heading"><p className="eyebrow">내 정보</p><h1>반려생활을 한곳에서 관리하세요.</h1><p>최근 주문, 정기배송, 장바구니 상태를 한눈에 확인할 수 있어요.</p></header><CommerceSnapshotPanel snapshot={snapshot} error={snapshotError} onRetry={() => { setSnapshotError(null); setSnapshot(null); setRetry((value) => value + 1); }} /><nav className="my-settings-list" aria-label="내 정보 메뉴">{links.map(([href, title, description]) => <Link key={href} href={href}><span><strong>{title}</strong><small>{description}</small></span><span className="settings-arrow" aria-hidden="true">→</span></Link>)}</nav><section className="account-section" aria-labelledby="account-title"><div><p className="eyebrow">계정</p><h2 id="account-title">로그인 상태 관리</h2><p>로그아웃하면 현재 회원 상태와 보호 화면 접근 정보가 정리됩니다.</p></div><LogoutControl /></section></section>;
}
