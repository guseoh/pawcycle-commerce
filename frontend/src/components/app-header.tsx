"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";
import { useCatalogDiscovery } from "./catalog-discovery";
import { buildLoginHref } from "@/lib/frontend-utils";

export function AppHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const { status, memberId } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [cartCount, setCartCount] = useState(0);
  const [wishlistCount, setWishlistCount] = useState(0);
  const discovery = useCatalogDiscovery();
  const menuButton = useRef<HTMLButtonElement>(null);
  const requestRef = useRef(0);
  const categoryNavigationRef = useRef<HTMLDetailsElement>(null);
  const closeNavigation = () => {
    setMenuOpen(false);
    categoryNavigationRef.current?.removeAttribute("open");
  };

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
    <div className="app-header-shell" onKeyDown={(event) => {
      if (event.key !== "Escape") return;
      if (!menuOpen && !categoryNavigationRef.current?.open) return;
      closeNavigation();
      if (menuOpen) menuButton.current?.focus();
      else categoryNavigationRef.current?.querySelector("summary")?.focus();
    }}>
      <header className="site-header shopping-header">
      <div className="shopping-header-primary">
      <div className="header-inner">
        <Link className="brand" href="/" aria-label="PawCycle 홈">
          <span className="brand-mark" aria-hidden="true">P</span>
          <span>
            <strong>PawCycle</strong>
            <small>반려생활의 좋은 순환</small>
          </span>
        </Link>
        <button
          ref={menuButton}
          className="header-menu-toggle"
          type="button"
          aria-label={menuOpen ? "메뉴 닫기" : "메뉴 열기"}
          aria-expanded={menuOpen}
          aria-controls="main-navigation"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span aria-hidden="true">{menuOpen ? "닫기" : "메뉴"}</span>
        </button>
        <form className="header-search" role="search" onSubmit={(event) => { event.preventDefault(); const query = String(new FormData(event.currentTarget).get("q") ?? "").trim(); closeNavigation(); router.push(query ? `/products?q=${encodeURIComponent(query)}` : "/products"); }}>
          <label className="sr-only" htmlFor="header-search">전체 상품 검색</label><input id="header-search" name="q" type="search" placeholder="우리 아이에게 필요한 상품 찾기" /><button type="submit">검색</button>
        </form>
      </div>
      </div>
      </header>
      <div className="shopping-header-secondary">
      <div className="header-inner">
        <nav id="main-navigation" className={`main-nav${menuOpen ? " is-open" : ""}`} aria-label="주요 메뉴">
          <div className="nav-primary">
            <Link onClick={closeNavigation} className={pathname.startsWith("/products") ? "nav-active" : undefined} href="/products">상품</Link>
            <Link onClick={closeNavigation} href="/products?petType=DOG">DOG</Link>
            <Link onClick={closeNavigation} href="/products?petType=CAT">CAT</Link>
            <details className="category-navigation" ref={categoryNavigationRef}>
              <summary>카테고리</summary>
              <div className="category-navigation-menu">
                {discovery.state.status === "loading" ? <span className="nav-status" role="status">카테고리를 불러오는 중</span> : null}
                {discovery.state.status === "success" ? discovery.state.data.categories.length ? discovery.state.data.categories.map((category) => <div className="category-navigation-group" key={category.categoryId}><Link onClick={closeNavigation} href={`/products?category=${encodeURIComponent(category.slug)}`}><strong>{category.name}</strong></Link>{category.children.map((child) => <Link key={child.categoryId} onClick={closeNavigation} href={`/products?category=${encodeURIComponent(category.slug)}&subcategory=${encodeURIComponent(child.slug)}`}>{child.name}</Link>)}</div>) : <p>준비된 카테고리가 없습니다. 전체 상품을 확인해 주세요.</p> : null}
                {discovery.state.status === "error" ? <div role="alert"><p>카테고리 정보를 불러오지 못했습니다. 상품 목록에서 검색할 수 있습니다.</p><button type="button" onClick={discovery.retry}>다시 시도</button></div> : null}
              </div>
            </details>
            <Link onClick={closeNavigation} className={pathname.startsWith("/subscriptions") ? "nav-active" : undefined} href="/subscriptions">정기배송</Link>
            <Link onClick={closeNavigation} className={pathname.startsWith("/orders") ? "nav-active" : undefined} href="/orders">주문</Link>
          </div>
          <div className="nav-utility-group">
            {status === "authenticated" ? <><Link onClick={closeNavigation} className={`nav-utility${pathname === "/wishlist" ? " nav-active" : ""}`} href="/wishlist">찜 <span className="nav-badge" aria-label={`찜 ${wishlistCount}개`}>{wishlistCount > 99 ? "99+" : wishlistCount}</span></Link><Link onClick={closeNavigation} className={`nav-utility${pathname === "/cart" ? " nav-active" : ""}`} href="/cart">장바구니 <span className="nav-badge" aria-label={`장바구니 ${cartCount}개`}>{cartCount > 99 ? "99+" : cartCount}</span></Link><Link onClick={closeNavigation} className={`nav-utility${pathname.startsWith("/notifications") ? " nav-active" : ""}`} href="/notifications">알림</Link><Link onClick={closeNavigation} className={pathname.startsWith("/my") ? "nav-active" : undefined} href="/my">내 정보</Link></> : null}
            {status === "loading" ? <span className="nav-status" role="status">회원 정보 확인 중</span> : status === "anonymous" || status === "error" ? <Link onClick={closeNavigation} href={buildLoginHref(pathname)}>로그인</Link> : null}
          </div>
      </nav>
      </div>
      </div>
      <MobileBottomNavigation pathname={pathname} />
    </div>
  );
}

function MobileBottomNavigation({ pathname }: { pathname: string }) {
  const items = [
    { href: "/", label: "홈", icon: "home" },
    { href: "/products", label: "상품", icon: "grid" },
    { href: "/subscriptions", label: "정기배송", icon: "repeat" },
    { href: "/orders", label: "주문", icon: "order" },
    { href: "/my", label: "내 정보", icon: "person" },
  ] as const;
  return <nav className="mobile-bottom-nav" aria-label="빠른 이동">{items.map((item) => {
    const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
    return <Link key={item.href} className={active ? "is-active" : undefined} href={item.href} aria-current={active ? "page" : undefined}><NavigationIcon name={item.icon} /><span>{item.label}</span></Link>;
  })}</nav>;
}

function NavigationIcon({ name }: { name: "home" | "grid" | "repeat" | "order" | "person" }) {
  const paths = {
    home: <><path d="m3 10 9-7 9 7" /><path d="M5 9v10h14V9" /><path d="M9 19v-6h6v6" /></>,
    grid: <><rect x="4" y="4" width="6" height="6" rx="1" /><rect x="14" y="4" width="6" height="6" rx="1" /><rect x="4" y="14" width="6" height="6" rx="1" /><rect x="14" y="14" width="6" height="6" rx="1" /></>,
    repeat: <><path d="M17 2l4 4-4 4" /><path d="M3 6h18" /><path d="m7 22-4-4 4-4" /><path d="M21 18H3" /></>,
    order: <><path d="M6 3h12v18H6z" /><path d="M9 7h6M9 11h6M9 15h4" /></>,
    person: <><circle cx="12" cy="8" r="3" /><path d="M5 21a7 7 0 0 1 14 0" /></>,
  }[name];
  return <svg className="navigation-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths}</svg>;
}
