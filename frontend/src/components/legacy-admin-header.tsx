"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";
import { useCatalogDiscovery } from "./catalog-discovery";
import { buildLoginHref } from "@/lib/frontend-utils";

const FOCUSABLE = "a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), summary, [tabindex]:not([tabindex='-1'])";

export function LegacyAdminHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const { status, memberId } = useAuth();
  const discovery = useCatalogDiscovery();
  const [menuOpen, setMenuOpen] = useState(false);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [compact, setCompact] = useState(false);
  const [searchDraft, setSearchDraft] = useState("");
  const [activeCategory, setActiveCategory] = useState("");
  const [categoryExpanded, setCategoryExpanded] = useState<Record<number, boolean>>({});
  const [cartCount, setCartCount] = useState(0);
  const [wishlistCount, setWishlistCount] = useState(0);
  const menuButton = useRef<HTMLButtonElement>(null);
  const categoryButton = useRef<HTMLButtonElement>(null);
  const menuPanel = useRef<HTMLDivElement>(null);
  const categoryPanel = useRef<HTMLDivElement>(null);
  const requestRef = useRef(0);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSearchDraft(pathname === "/products" ? searchParams.get("q") ?? "" : "");
      setActiveCategory(searchParams.get("category") ?? "");
    }, 0);
    return () => window.clearTimeout(timer);
  }, [pathname, searchParams]);

  useEffect(() => {
    const onViewportChange = () => {
      const nextCompact = window.scrollY > 48;
      setCompact(nextCompact);
      if (window.innerWidth <= 1023 || nextCompact) setCategoryOpen(false);
    };
    onViewportChange();
    window.addEventListener("scroll", onViewportChange, { passive: true });
    window.addEventListener("resize", onViewportChange);
    return () => { window.removeEventListener("scroll", onViewportChange); window.removeEventListener("resize", onViewportChange); };
  }, []);

  useEffect(() => {
    const panel = menuOpen ? menuPanel.current : categoryOpen ? categoryPanel.current : null;
    const trigger = menuOpen ? menuButton.current : categoryButton.current;
    if (!panel || (categoryOpen && (compact || window.innerWidth <= 1023))) return;
    const previousOverflow = document.body.style.overflow;
    if (menuOpen) document.body.style.overflow = "hidden";
    panel.querySelector<HTMLElement>(FOCUSABLE)?.focus();
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (panel.contains(target) || trigger?.contains(target)) return;
      setMenuOpen(false);
      setCategoryOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setMenuOpen(false);
        setCategoryOpen(false);
        if (menuOpen || (!compact && window.innerWidth > 1023)) trigger?.focus();
        return;
      }
      if (event.key !== "Tab") return;
      const focusable = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((node) => !node.hidden);
      if (!focusable.length) return;
      const firstNode = focusable[0];
      const lastNode = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === firstNode) { event.preventDefault(); lastNode.focus(); }
      if (!event.shiftKey && document.activeElement === lastNode) { event.preventDefault(); firstNode.focus(); }
    };
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [categoryOpen, compact, menuOpen]);

  useEffect(() => {
    requestRef.current += 1;
    let active = true;
    if (status !== "authenticated") {
      const resetTimer = window.setTimeout(() => {
        if (!active) return;
        setCartCount(0);
        setWishlistCount(0);
      }, 0);
      return () => { active = false; requestRef.current += 1; window.clearTimeout(resetTimer); };
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
    const timer = window.setTimeout(refreshBadges, 0);
    window.addEventListener("pawcycle-commerce-changed", refreshBadges);
    return () => { active = false; requestRef.current += 1; window.clearTimeout(timer); window.removeEventListener("pawcycle-commerce-changed", refreshBadges); };
  }, [memberId, status]);

  const closeNavigation = () => { setMenuOpen(false); setCategoryOpen(false); };
  const categories = discovery.state.status === "success" ? discovery.state.data.categories : [];
  const categoryGroups = <>
    {discovery.state.status === "loading" ? <p role="status">카테고리를 불러오는 중입니다.</p> : null}
    {discovery.state.status === "error" ? <div role="alert"><p>{discovery.state.message}</p><button type="button" className="button button-secondary" onClick={discovery.retry}>다시 시도</button></div> : null}
    {categories.length ? <div className="category-groups">{categories.map((category) => <details key={category.categoryId} open={Boolean(categoryExpanded[category.categoryId])} onToggle={(event) => { const open = (event.currentTarget as HTMLDetailsElement).open; setCategoryExpanded((current) => ({ ...current, [category.categoryId]: open })); }}><summary aria-current={category.slug === activeCategory ? "page" : undefined}>{category.name}</summary><div><Link onClick={closeNavigation} aria-current={category.slug === activeCategory ? "page" : undefined} href={`/products?category=${encodeURIComponent(category.slug)}`}>전체 보기</Link>{category.children.map((child) => <Link key={child.categoryId} onClick={closeNavigation} href={`/products?category=${encodeURIComponent(category.slug)}&subcategory=${encodeURIComponent(child.slug)}`}>{child.name}</Link>)}</div></details>)}</div> : null}
  </>;

  return <header className={`site-header shopping-header${compact ? " is-compact" : ""}`}>
    <div className="header-primary">
      <button ref={menuButton} className="header-menu-toggle icon-button" type="button" aria-expanded={menuOpen} aria-controls="mobile-navigation" onClick={() => { setCategoryOpen(false); setMenuOpen((open) => !open); }}>메뉴</button>
      <Link className="brand" href="/" aria-label="PawCycle 홈"><span className="brand-mark" aria-hidden="true">P</span><span><strong>PawCycle</strong><small>반려생활의 좋은 순환</small></span></Link>
      <form className="header-search" role="search" onSubmit={(event) => { event.preventDefault(); closeNavigation(); const query = searchDraft.trim(); router.push(query ? `/products?q=${encodeURIComponent(query)}` : "/products"); }}>
        <label className="sr-only" htmlFor="header-search">상품 검색</label>
        <input id="header-search" name="q" type="search" value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} placeholder="상품명 또는 설명 검색" />
        {searchDraft ? <button className="search-clear" type="button" onClick={() => setSearchDraft("")}>지우기</button> : null}
        <button type="submit">검색</button>
      </form>
      <nav className="header-actions" aria-label="계정과 장바구니">
        {status === "authenticated" ? <Link className={pathname.startsWith("/my") ? "nav-active" : undefined} href="/my">내 정보</Link> : status === "loading" ? <span className="nav-status" role="status">계정 확인 중</span> : <Link href={buildLoginHref(pathname)}>로그인</Link>}
        <Link className={pathname === "/cart" ? "nav-active" : undefined} href={status === "authenticated" ? "/cart" : buildLoginHref("/cart")}>장바구니 <span className="nav-badge" aria-label={`장바구니 ${cartCount}개`}>{cartCount > 99 ? "99+" : cartCount}</span></Link>
      </nav>
    </div>
    <nav className="header-navigation" aria-label="주요 메뉴">
      <div className="header-navigation-primary"><button ref={categoryButton} className="category-trigger" type="button" aria-expanded={categoryOpen} aria-controls="category-navigation" onClick={() => { setMenuOpen(false); setCategoryOpen((open) => !open); }}>카테고리</button>
        <Link aria-current={pathname === "/products" ? "page" : undefined} href="/products">상품</Link>
        <Link aria-current={pathname.startsWith("/subscriptions") ? "page" : undefined} href="/subscriptions">정기배송</Link>
        <Link aria-current={pathname.startsWith("/orders") ? "page" : undefined} href="/orders">주문</Link>
        <Link href="/support">고객지원</Link>
      </div>
      {status === "authenticated" ? <div className="header-navigation-utility"><Link aria-current={pathname === "/wishlist" ? "page" : undefined} href="/wishlist">위시리스트 <span className="nav-badge" aria-label={`위시리스트 ${wishlistCount}개`}>{wishlistCount > 99 ? "99+" : wishlistCount}</span></Link><Link href="/notifications">알림</Link></div> : null}
    </nav>
    {categoryOpen ? <div id="category-navigation" ref={categoryPanel} className="category-navigation-overlay" role="dialog" aria-modal="true" aria-label="상품 카테고리"><div className="navigation-overlay-heading"><strong>카테고리</strong><button type="button" className="icon-button" aria-label="카테고리 닫기" onClick={() => { closeNavigation(); if (!compact && window.innerWidth > 1023) categoryButton.current?.focus(); }}>닫기</button></div>{categoryGroups}</div> : null}
    {menuOpen ? <div className="navigation-backdrop"><div id="mobile-navigation" ref={menuPanel} className="mobile-navigation-drawer" role="dialog" aria-modal="true" aria-label="전체 메뉴"><div className="navigation-overlay-heading"><strong>전체 메뉴</strong><button type="button" className="icon-button" onClick={() => { setMenuOpen(false); menuButton.current?.focus(); }}>닫기</button></div><nav aria-label="모바일 주요 메뉴"><Link onClick={closeNavigation} href="/products">상품</Link><Link onClick={closeNavigation} href="/subscriptions">정기배송</Link><Link onClick={closeNavigation} href="/orders">주문</Link><Link onClick={closeNavigation} href="/wishlist">위시리스트</Link><Link onClick={closeNavigation} href="/my">내 정보</Link><Link onClick={closeNavigation} href="/support">고객지원</Link></nav><section aria-labelledby="mobile-category-title"><h2 id="mobile-category-title">카테고리</h2>{categoryGroups}</section></div></div> : null}
  </header>;
}

export function LegacyAdminHeaderShell() {
  return <Suspense fallback={<header className="site-header shopping-header" aria-hidden="true" />}><LegacyAdminHeader /></Suspense>;
}
