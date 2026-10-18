const CATALOG_RETURN_KEY = "pawcycle.catalog-return-url";
const MAX_AGE_MS = 30 * 60 * 1000;

/** Only catalog paths are valid. Storage is optional and never a navigation authority. */
export function safeCatalogReturn(href: unknown): string | null {
  if (typeof href !== "string" || href.length > 4096 || !href.startsWith("/products") || /[\\\u0000-\u001f]/.test(href)) return null;
  try {
    const url = new URL(href, "https://pawcycle.invalid");
    if (url.origin !== "https://pawcycle.invalid" || url.pathname !== "/products") return null;
    return `${url.pathname}${url.search}`;
  } catch { return null; }
}

export function rememberCatalogReturn(href: string): void {
  const safe = safeCatalogReturn(href);
  if (!safe || typeof window === "undefined") return;
  try { window.sessionStorage.setItem(CATALOG_RETURN_KEY, JSON.stringify({ href: safe, savedAt: Date.now() })); } catch { /* Optional browsing context. */ }
}

export function readCatalogReturn(): string {
  if (typeof window === "undefined") return "/products";
  try {
    const raw = window.sessionStorage.getItem(CATALOG_RETURN_KEY);
    if (!raw) return "/products";
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== "object" || !("href" in value) || !("savedAt" in value)) return "/products";
    const saved = value as { href: unknown; savedAt: unknown };
    if (typeof saved.savedAt !== "number" || !Number.isFinite(saved.savedAt) || saved.savedAt > Date.now() || Date.now() - saved.savedAt > MAX_AGE_MS) return "/products";
    return safeCatalogReturn(saved.href) ?? "/products";
  } catch { return "/products"; }
}
