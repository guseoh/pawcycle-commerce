"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";
import { ApiError, Category, categoryApi } from "@/lib/api";
import { buildLoginHref } from "@/lib/frontend-utils";

type CategoryLoadState = { status: "loading" } | { status: "success"; categories: Category[] } | { status: "error"; message: string };

export function AppHeader() {
  const pathname = usePathname();
  const { status, memberId } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [cartCount, setCartCount] = useState(0);
  const [wishlistCount, setWishlistCount] = useState(0);
  const [categoryState, setCategoryState] = useState<CategoryLoadState>({ status: "loading" });
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

  useEffect(() => {
    let active = true;
    void categoryApi.list().then((response) => {
      if (active) setCategoryState({ status: "success", categories: response.items });
    }).catch((error: unknown) => {
      if (active) setCategoryState({ status: "error", message: error instanceof ApiError ? error.message : "카테고리를 불러오지 못했습니다." });
    });
    return () => { active = false; };
  }, []);

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
            <details className="category-navigation">
              <summary>카테고리 탐색</summary>
              <div className="category-navigation-menu">
                {categoryState.status === "loading" ? <span className="nav-status" role="status">카테고리를 불러오는 중</span> : null}
                {categoryState.status === "success" ? categoryState.categories.map((category) => <Link key={category.categoryId} onClick={closeMenu} href={`/products?category=${encodeURIComponent(category.slug)}`}>{category.name}</Link>) : null}
                {categoryState.status === "error" ? <p role="alert">{categoryState.message} 상품 목록에서 검색할 수 있습니다.</p> : null}
              </div>
            </details>
            <Link onClick={closeMenu} className={pathname.startsWith("/subscriptions") ? "nav-active" : undefined} href="/subscriptions">정기배송</Link>
            <Link onClick={closeMenu} className={pathname.startsWith("/orders") ? "nav-active" : undefined} href="/orders">주문</Link>
          </div>
          <div className="nav-utility-group">
            {status === "authenticated" ? <><Link onClick={closeMenu} className={`nav-utility${pathname === "/wishlist" ? " nav-active" : ""}`} href="/wishlist">찜 <span className="nav-badge" aria-label={`찜 ${wishlistCount}개`}>{wishlistCount > 99 ? "99+" : wishlistCount}</span></Link><Link onClick={closeMenu} className={`nav-utility${pathname === "/cart" ? " nav-active" : ""}`} href="/cart">장바구니 <span className="nav-badge" aria-label={`장바구니 ${cartCount}개`}>{cartCount > 99 ? "99+" : cartCount}</span></Link><Link onClick={closeMenu} className={`nav-utility${pathname.startsWith("/notifications") ? " nav-active" : ""}`} href="/notifications">알림</Link><Link onClick={closeMenu} className={pathname.startsWith("/my") ? "nav-active" : undefined} href="/my">내 정보</Link></> : null}
            {status === "loading" ? <span className="nav-status" role="status">회원 정보 확인 중</span> : status === "anonymous" || status === "error" ? <Link onClick={closeMenu} href={buildLoginHref(pathname)}>로그인</Link> : null}
          </div>
        </nav>
      </div>
    </header>
  );
}
