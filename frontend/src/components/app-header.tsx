"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref } from "@/lib/frontend-utils";

export function AppHeader() {
  const pathname = usePathname();
  const { status, memberId } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [cartCount, setCartCount] = useState(0);
  const [wishlistCount, setWishlistCount] = useState(0);
  const requestRef = useRef(0);
  const closeMenu = () => setMenuOpen(false);

  useEffect(() => {
    requestRef.current += 1;
    let active = true;
    if (status !== "authenticated") {
      const resetTimer = window.setTimeout(() => {
        if (!active) return;
        setCartCount(0);
        setWishlistCount(0);
      }, 0);
      return () => {
        active = false;
        requestRef.current += 1;
        window.clearTimeout(resetTimer);
      };
    }
    const refreshBadges = () => {
      const request = ++requestRef.current;
      void Promise.all([commerceFinalApi.cart(), commerceFinalApi.wishlist()]).then(([cart, wishlist]) => {
        if (!active || request !== requestRef.current) return;
        setCartCount(cart.items.reduce((total, item) => total + item.quantity, 0));
        setWishlistCount(wishlist.items.length);
      }).catch(() => {
        if (!active || request !== requestRef.current) return;
        setCartCount(0);
        setWishlistCount(0);
      });
    };
    const timer = window.setTimeout(() => {
      if (!active) return;
      setCartCount(0);
      setWishlistCount(0);
      refreshBadges();
    }, 0);
    window.addEventListener("pawcycle-commerce-changed", refreshBadges);
    return () => {
      active = false;
      requestRef.current += 1;
      window.clearTimeout(timer);
      window.removeEventListener("pawcycle-commerce-changed", refreshBadges);
    };
  }, [memberId, status]);

  return (
    <header className="site-header">
      <div className="header-inner">
        <Link className="brand" href="/" aria-label="PawCycle 홈">
          <span className="brand-mark" aria-hidden="true">P</span>
          <span>
            <strong>PawCycle</strong>
            <small>반려생활의 좋은 순환</small>
          </span>
        </Link>
        <button
          className="header-menu-toggle"
          type="button"
          aria-expanded={menuOpen}
          aria-controls="main-navigation"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span className="sr-only">메뉴</span>
          <span aria-hidden="true">{menuOpen ? "닫기" : "메뉴"}</span>
        </button>
        <nav id="main-navigation" className={`main-nav${menuOpen ? " is-open" : ""}`} aria-label="주요 메뉴">
          <div className="nav-primary">
            <Link onClick={closeMenu} className={pathname.startsWith("/products") ? "nav-active" : undefined} href="/products">상품</Link>
            <Link onClick={closeMenu} className={pathname.startsWith("/subscriptions") ? "nav-active" : undefined} href="/subscriptions">정기배송</Link>
            <Link onClick={closeMenu} className={pathname.startsWith("/orders") ? "nav-active" : undefined} href="/orders">주문</Link>
          </div>
          <div className="nav-utility-group">
            {status === "authenticated" ? <><Link onClick={closeMenu} className={`nav-utility${pathname === "/wishlist" ? " nav-active" : ""}`} href="/wishlist">찜 <span className="nav-badge" aria-label={`찜 ${wishlistCount}개`}>{wishlistCount > 99 ? "99+" : wishlistCount}</span></Link><Link onClick={closeMenu} className={`nav-utility${pathname === "/cart" ? " nav-active" : ""}`} href="/cart">장바구니 <span className="nav-badge" aria-label={`장바구니 ${cartCount}개`}>{cartCount > 99 ? "99+" : cartCount}</span></Link><Link onClick={closeMenu} className={`nav-utility${pathname.startsWith("/notifications") ? " nav-active" : ""}`} href="/notifications">알림</Link><Link onClick={closeMenu} className={pathname.startsWith("/my") ? "nav-active" : undefined} href="/my">내 정보</Link></> : null}
            {status === "loading" ? <span className="nav-status" role="status">회원 정보 확인 중</span> : status === "anonymous" ? <Link onClick={closeMenu} href={buildLoginHref(pathname)}>로그인</Link> : null}
          </div>
        </nav>
      </div>
    </header>
  );
}
