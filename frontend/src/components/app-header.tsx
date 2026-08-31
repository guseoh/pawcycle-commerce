"use client";

import Link from "next/link";
import { OrbitMark } from "./orbit-mark";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";
import { useCatalogDiscovery } from "./catalog-discovery";
import { buildLoginHref } from "@/lib/frontend-utils";
import { interactionContext, parseCatalogFilters } from "@/lib/catalog-filters";
import { createInteractionEvent, finalProductApi } from "@/lib/final-product-api";

const FOCUSABLE = "a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), summary, [tabindex]:not([tabindex='-1'])";
const RECENT_SEARCHES_KEY = "pawcycle.recent-searches";
const MAX_RECENT_SEARCHES = 6;

function drawerFocusable(panel: HTMLElement) {
  return Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((node) => {
    const closedDetails = node.closest("details:not([open])");
    const style = window.getComputedStyle(node);
    return !node.hidden
      && !node.closest("[hidden]")
      && !node.matches(":disabled")
      && node.tabIndex >= 0
      && (!closedDetails || node.tagName === "SUMMARY")
      && style.display !== "none"
      && style.visibility !== "hidden"
      && node.getClientRects().length > 0;
  });
}

function readRecentSearches(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_SEARCHES_KEY);
    const value: unknown = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(value)) return [];
    return value.filter((item): item is string => typeof item === "string" && Boolean(item.trim())).slice(0, MAX_RECENT_SEARCHES);
  } catch {
    return [];
  }
}

export function AppHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const auth = useAuth();
  const { status, memberId } = auth;
  const discovery = useCatalogDiscovery();
  const [menuOpen, setMenuOpen] = useState(false);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [recentSearches, setRecentSearches] = useState<string[]>([]);

  const [searchDraft, setSearchDraft] = useState("");
  const [activeCategory, setActiveCategory] = useState("");
  const [categoryExpanded, setCategoryExpanded] = useState<Record<number, boolean>>({});
  const [cartCount, setCartCount] = useState<number | null>(null);
  const [badgeFailed, setBadgeFailed] = useState(false);
  const [wishlistCount, setWishlistCount] = useState(0);
  const menuButton = useRef<HTMLButtonElement>(null);
  const categoryButton = useRef<HTMLButtonElement>(null);
  const menuPanel = useRef<HTMLDivElement>(null);
  const categoryPanel = useRef<HTMLDivElement>(null);
  const searchShell = useRef<HTMLDivElement>(null);
  const restoreMenuFocus = useRef(false);
  const requestRef = useRef(0);

  const closeMobileMenu = (returnFocus = false) => {
    restoreMenuFocus.current = returnFocus;
    setMenuOpen(false);
  };

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSearchDraft(pathname === "/products" ? searchParams.get("q") ?? "" : "");
      setActiveCategory(searchParams.get("category") ?? "");
      setRecentSearches(readRecentSearches());
    }, 0);
    return () => window.clearTimeout(timer);
  }, [pathname, searchParams]);

  useEffect(() => {
    const media = window.matchMedia("(max-width: 1023px)");
    const resize = () => { setCategoryOpen(false); setMenuOpen(false); setSearchOpen(false); };
    media.addEventListener("change", resize); return () => media.removeEventListener("change", resize);
  }, []);

  useEffect(() => {
    if (!searchOpen) return;
    const closeWhenOutside = (event: Event) => {
      if (!searchShell.current?.contains(event.target as Node)) setSearchOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      setSearchOpen(false);
      requestAnimationFrame(() => document.getElementById("header-search")?.focus());
    };
    document.addEventListener("pointerdown", closeWhenOutside);
    document.addEventListener("focusin", closeWhenOutside);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", closeWhenOutside);
      document.removeEventListener("focusin", closeWhenOutside);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [searchOpen]);

  useEffect(() => {
    const panel = menuOpen ? menuPanel.current : categoryOpen ? categoryPanel.current : null;
    const trigger = menuOpen ? menuButton.current : categoryButton.current;
    if (!panel || (categoryOpen && window.innerWidth <= 1023)) return;
    const previousOverflow = document.body.style.overflow;
    const background = Array.from(document.querySelectorAll<HTMLElement>("main, .site-footer, .header-primary"));
    if (menuOpen) { document.body.style.overflow = "hidden"; background.forEach(node => node.inert = true); }
    const onFocus = (event: FocusEvent) => { if (!menuOpen && !panel.contains(event.target as Node)) setCategoryOpen(false); };
    document.addEventListener("focusin", onFocus);
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
        if (menuOpen) restoreMenuFocus.current = true;
        setMenuOpen(false);
        setCategoryOpen(false);
        if (!menuOpen && window.innerWidth > 1023) requestAnimationFrame(() => trigger?.focus());
        return;
      }
      if (event.key !== "Tab" || !menuOpen) return;
      const focusable = drawerFocusable(panel);
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
      background.forEach(node => node.inert = false);
      document.removeEventListener("focusin", onFocus);
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
      if (menuOpen && restoreMenuFocus.current && trigger?.isConnected) {
        restoreMenuFocus.current = false;
        requestAnimationFrame(() => trigger.focus());
      }
    };
  }, [categoryOpen, menuOpen]);

  useEffect(() => {
    requestRef.current += 1;
    let active = true;
    if (status !== "authenticated") {
      const resetTimer = window.setTimeout(() => {
        if (!active) return;
        setCartCount(null);
        setWishlistCount(0);
      }, 0);
      return () => { active = false; requestRef.current += 1; window.clearTimeout(resetTimer); };
    }
    const refreshBadges = () => {
      setBadgeFailed(false);
      const request = ++requestRef.current;
      void Promise.all([commerceFinalApi.cart(), commerceFinalApi.wishlist()]).then(([cart, wishlist]) => {
        if (!active || request !== requestRef.current) return;
        setCartCount(cart.items.reduce((total, item) => total + item.quantity, 0));
        setWishlistCount(wishlist.items.length);
      }).catch(() => {
        if (!active || request !== requestRef.current) return;
        setCartCount(null);
        setBadgeFailed(true);
        setWishlistCount(0);
      });
    };
    const timer = window.setTimeout(refreshBadges, 0);
    window.addEventListener("pawcycle-commerce-changed", refreshBadges);
    return () => { active = false; requestRef.current += 1; window.clearTimeout(timer); window.removeEventListener("pawcycle-commerce-changed", refreshBadges); };
  }, [memberId, status]);

  const closeNavigation = () => { setMenuOpen(false); setCategoryOpen(false); };
  const rememberSearch = (query: string) => {
    if (!query) return;
    const next = [query, ...recentSearches.filter((item) => item !== query)].slice(0, MAX_RECENT_SEARCHES);
    setRecentSearches(next);
    try { localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(next)); } catch { /* Browsing storage is optional. */ }
  };
  const submitSearch = (candidate?: string) => {
    closeNavigation();
    setSearchOpen(false);
    const query = (candidate ?? searchDraft).trim();
    if (query) rememberSearch(query);
    const params = new URLSearchParams(query ? { q: query } : {});
    const event = createInteractionEvent({ type: "SEARCH", source: "catalog-search", context: interactionContext(parseCatalogFilters(params)) });
    if (status === "authenticated" && event) void auth.executeWithCsrf(csrf => finalProductApi.interactions.send([event], csrf)).catch(() => undefined);
    router.push(query ? `/products?q=${encodeURIComponent(query)}` : "/products");
  };
  const categories = discovery.state.status === "success" ? discovery.state.data.categories : [];
  const brands = discovery.state.status === "success" ? discovery.state.data.brands : [];
  const categoryGroups = <>
    {discovery.state.status === "loading" ? <p role="status">카테고리를 불러오는 중입니다.</p> : null}
    {discovery.state.status === "error" ? <div role="alert"><p>{discovery.state.message}</p><button type="button" className="button button-secondary" onClick={discovery.retry}>다시 시도</button></div> : null}
    {categories.length ? <div className="category-groups">{categories.map((category) => <details key={category.categoryId} open={Boolean(categoryExpanded[category.categoryId])} onToggle={(event) => { const open = (event.currentTarget as HTMLDetailsElement).open; setCategoryExpanded((current) => ({ ...current, [category.categoryId]: open })); }}><summary aria-current={category.slug === activeCategory ? "page" : undefined}>{category.name}</summary><div><Link onClick={closeNavigation} aria-current={category.slug === activeCategory ? "page" : undefined} href={`/products?category=${encodeURIComponent(category.slug)}`}>전체 보기</Link>{category.children.map((child) => <Link key={child.categoryId} onClick={closeNavigation} href={`/products?category=${encodeURIComponent(category.slug)}&subcategory=${encodeURIComponent(child.slug)}`}>{child.name}</Link>)}</div></details>)}</div> : null}
    {discovery.state.status === "success" && !categories.length ? <p>카테고리를 준비하고 있어요. <Link href="/products" onClick={closeNavigation}>전체 상품 보기</Link></p> : null}
  </>;

  return <header className="site-header shopping-header">
    <div className="header-primary">
      <button ref={menuButton} className="header-menu-toggle icon-button" type="button" aria-expanded={menuOpen} aria-controls="mobile-navigation" onClick={() => { setCategoryOpen(false); setSearchOpen(false); setMenuOpen((open) => !open); }}>메뉴</button>
      <Link className="brand" href="/" aria-label="PawCycle 홈"><OrbitMark /><strong>PawCycle</strong></Link>
      <nav className="header-catalog" aria-label="상품 탐색"><Link href="/products" aria-current={pathname === "/products" ? "page" : undefined}>상품</Link><button ref={categoryButton} type="button" aria-expanded={categoryOpen} aria-controls="category-navigation" onClick={() => { setMenuOpen(false); setSearchOpen(false); setCategoryOpen(open => !open); }}>카테고리</button><Link href="/subscriptions" aria-current={pathname.startsWith("/subscriptions") ? "page" : undefined}>정기배송</Link></nav>
      <div className="header-search-shell" ref={searchShell}>
        <form className="header-search" role="search" onSubmit={(event) => { event.preventDefault(); submitSearch(); }}>
          <label className="sr-only" htmlFor="header-search">상품 검색</label>
          <input id="header-search" name="q" type="search" value={searchDraft} onFocus={() => { setMenuOpen(false); setCategoryOpen(false); setSearchOpen(true); }} onChange={(event) => { setSearchDraft(event.target.value); setSearchOpen(true); }} placeholder="상품명, 브랜드, 필요한 물품을 검색하세요" aria-expanded={searchOpen} aria-controls="header-search-discovery" />
          {searchDraft ? <button className="search-clear" type="button" aria-label="검색어 지우기" onClick={() => { setSearchDraft(""); setSearchOpen(true); document.getElementById("header-search")?.focus(); }}>×</button> : null}
          <button type="submit">검색</button>
        </form>
        {searchOpen ? <div id="header-search-discovery" className="search-discovery-panel" aria-label="상품 검색 탐색">
          {searchDraft.trim() ? <button className="search-query-action" type="button" onClick={() => submitSearch(searchDraft)}><span>‘{searchDraft.trim()}’ 검색하기</span><span aria-hidden="true">→</span></button> : null}
          {recentSearches.length ? <section aria-labelledby="recent-search-title"><div className="search-discovery-heading"><h2 id="recent-search-title">최근 검색</h2><button type="button" onClick={() => { setRecentSearches([]); try { localStorage.removeItem(RECENT_SEARCHES_KEY); } catch { /* optional */ } }}>전체 삭제</button></div><div className="search-recent-list">{recentSearches.map((item) => <button type="button" key={item} onClick={() => { setSearchDraft(item); submitSearch(item); }}>{item}</button>)}</div></section> : null}
          <section aria-labelledby="search-category-title"><h2 id="search-category-title">카테고리로 찾기</h2>{discovery.state.status === "loading" ? <p className="search-discovery-status" role="status">카테고리를 불러오는 중입니다.</p> : null}{discovery.state.status === "error" ? <p className="search-discovery-status">탐색 정보를 불러오지 못했지만 검색은 계속 사용할 수 있어요.</p> : null}<div className="search-discovery-links">{categories.slice(0, 8).map((category) => <Link key={category.categoryId} href={`/products?category=${encodeURIComponent(category.slug)}`} onClick={() => setSearchOpen(false)}>{category.name}</Link>)}</div></section>
          {brands.length ? <section aria-labelledby="search-brand-title"><h2 id="search-brand-title">브랜드로 찾기</h2><div className="search-discovery-links">{brands.slice(0, 8).map((brand) => <Link key={brand.brandId} href={`/products?brand=${encodeURIComponent(brand.slug)}`} onClick={() => setSearchOpen(false)}>{brand.name}</Link>)}</div></section> : null}
        </div> : null}
      </div>
      <nav className="header-actions" aria-label="계정과 장바구니">
        {status === "authenticated" ? <Link aria-current={pathname === "/my" ? "page" : undefined} href="/my">내 정보</Link> : status === "loading" ? <span role="status">계정 확인 중</span> : <Link href={buildLoginHref(pathname)}>로그인</Link>}
        <Link className="header-wishlist" href={status === "authenticated" ? "/wishlist" : buildLoginHref("/wishlist")} aria-label={status === "authenticated" ? `찜한 상품 ${wishlistCount}개` : "찜한 상품, 로그인 필요"}>찜</Link>
        <Link aria-current={pathname === "/cart" ? "page" : undefined} href={status === "authenticated" ? "/cart" : buildLoginHref("/cart")} aria-label={status !== "authenticated" ? "장바구니, 로그인 필요" : cartCount === null ? badgeFailed ? "장바구니, 개수 확인 실패" : "장바구니, 개수 확인 중" : `장바구니 ${cartCount}개`}>장바구니 {status === "authenticated" && !badgeFailed ? <span className="nav-badge" aria-hidden="true">{cartCount === null ? "–" : cartCount > 99 ? "99+" : cartCount}</span> : null}</Link>
      </nav>
    </div>
    {categoryOpen ? <div id="category-navigation" ref={categoryPanel} className="category-navigation-overlay" role="dialog" aria-label="상품 카테고리"><div className="navigation-overlay-heading"><strong>카테고리</strong><button type="button" className="icon-button" aria-label="카테고리 닫기" onClick={() => { closeNavigation(); if (window.innerWidth > 1023) categoryButton.current?.focus(); }}>닫기</button></div>{categoryGroups}</div> : null}
    {menuOpen ? <div className="navigation-backdrop"><div id="mobile-navigation" ref={menuPanel} className="mobile-navigation-drawer" role="dialog" aria-modal="true" aria-label="전체 메뉴"><div className="navigation-overlay-heading"><strong>전체 메뉴</strong><button type="button" className="icon-button" onClick={() => closeMobileMenu(true)}>닫기</button></div><nav aria-label="모바일 주요 메뉴"><Link onClick={closeNavigation} href="/products">상품</Link><Link onClick={closeNavigation} href="/subscriptions">정기배송</Link><Link onClick={closeNavigation} href="/orders">주문</Link><Link onClick={closeNavigation} href="/wishlist">위시리스트</Link><Link onClick={closeNavigation} href="/my">내 정보</Link>{[["/pets","반려동물"],["/addresses","배송지"],["/billing-methods","결제수단"],["/notifications","알림"]].map(([href,label]) => <Link key={href} onClick={closeNavigation} href={href}>{label}</Link>)}<Link onClick={closeNavigation} href="/support">고객지원</Link></nav><section aria-labelledby="mobile-category-title"><h2 id="mobile-category-title">카테고리</h2>{categoryGroups}</section></div></div> : null}
  </header>;
}

export function AppHeaderShell() {
  return <Suspense fallback={<header className="site-header shopping-header" aria-hidden="true" />}><AppHeader /></Suspense>;
}
