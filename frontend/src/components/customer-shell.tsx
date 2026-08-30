"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { AppHeaderShell } from "./app-header";
import { AppFooter } from "./app-footer";
import { LegacyAdminHeaderShell } from "./legacy-admin-header";
import { LegacyAdminFooter } from "./legacy-admin-footer";
import { OrbitMark } from "./orbit-mark";

const accountLinks = [["/my", "내 정보"], ["/orders", "주문 내역"], ["/subscriptions", "정기배송"], ["/wishlist", "찜한 상품"], ["/pets", "반려동물"], ["/addresses", "배송지"], ["/billing-methods", "결제수단"], ["/notifications", "알림"]];

export function CustomerShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [small, setSmall] = useState(false);
  useEffect(() => { const media = window.matchMedia("(max-width: 1023px)"); const update = () => setSmall(media.matches); update(); media.addEventListener("change", update); return () => media.removeEventListener("change", update); }, []);
  const admin = pathname.startsWith("/admin");
  const login = pathname === "/login";
  const compact = login || pathname.startsWith("/checkout") || /^\/(?:mvp2\/)?subscriptions\/.+/.test(pathname) || /^\/orders\/.+/.test(pathname);
  const account = accountLinks.some(([href]) => href === pathname);
  return <div className={admin ? "admin-theme" : `customer-theme${login ? " auth-theme" : ""}`}>
    <a className="skip-link" href="#main-content">본문으로 건너뛰기</a>
    {admin ? <LegacyAdminHeaderShell /> : compact ? <header className="compact-header"><Link className="brand" href="/" aria-label="PawCycle 홈"><OrbitMark /><strong>PawCycle</strong></Link><Link href="/products">상품으로 돌아가기</Link></header> : <AppHeaderShell />}
    <main id="main-content" className={`page-shell${account ? " account-layout" : ""}`}>
      {account ? <details key={String(small)} className="account-navigation" open={!small}><summary onClick={event => { if (!small) event.preventDefault(); }}>내 정보 메뉴</summary><nav aria-label="내 정보">{accountLinks.map(([href, label]) => <Link key={href} href={href} aria-current={pathname === href ? "page" : undefined}>{label}</Link>)}</nav></details> : null}
      <div className="page-content">{children}</div>
    </main>
    {admin ? <LegacyAdminFooter /> : <AppFooter compact={login} />}
  </div>;
}
