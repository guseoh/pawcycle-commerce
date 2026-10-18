import assert from "node:assert/strict";
import test from "node:test";
import { safeCatalogReturn, rememberCatalogReturn, readCatalogReturn } from "./catalog-return.ts";

test("catalog return preserves search, filters, sorting and pagination", () => {
  assert.equal(safeCatalogReturn("/products?q=%EC%97%B0%EC%96%B4&category=food&sort=PRICE_ASC&page=2#results"), "/products?q=%EC%97%B0%EC%96%B4&category=food&sort=PRICE_ASC&page=2");
});
test("catalog return rejects external URLs, other routes and malformed paths", () => {
  for (const path of [null, undefined, "https://evil.example/products", "//evil.example/products", "/products/1", "/products-other", "/products\\evil", "/products\n", "/cart"]) assert.equal(safeCatalogReturn(path), null);
});
test("optional storage helpers are safe during server rendering", () => {
  assert.doesNotThrow(() => rememberCatalogReturn("/products?q=food"));
  assert.equal(readCatalogReturn(), "/products");
});

test("catalog context restores safe fresh state and ignores expired or corrupted storage", () => {
  const previous = Object.getOwnPropertyDescriptor(globalThis, "window");
  const values = new Map<string, string>();
  const storage = { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value) };
  Object.defineProperty(globalThis, "window", { configurable: true, value: { sessionStorage: storage } });
  try {
    rememberCatalogReturn("/products?category=food&page=2");
    assert.equal(readCatalogReturn(), "/products?category=food&page=2");
    const key = [...values.keys()][0];
    values.set(key, JSON.stringify({ href: "/products?q=old", savedAt: 0 }));
    assert.equal(readCatalogReturn(), "/products");
    values.set(key, JSON.stringify({ href: "//evil.example/products", savedAt: Date.now() }));
    assert.equal(readCatalogReturn(), "/products");
    values.set(key, "invalid JSON");
    assert.equal(readCatalogReturn(), "/products");
  } finally {
    if (previous) Object.defineProperty(globalThis, "window", previous);
    else Reflect.deleteProperty(globalThis, "window");
  }
});

test("blocked browsing storage does not break navigation", () => {
  const previous = Object.getOwnPropertyDescriptor(globalThis, "window");
  Object.defineProperty(globalThis, "window", { configurable: true, value: { get sessionStorage() { throw new Error("blocked"); } } });
  try { assert.doesNotThrow(() => rememberCatalogReturn("/products")); assert.equal(readCatalogReturn(), "/products"); }
  finally { if (previous) Object.defineProperty(globalThis, "window", previous); else Reflect.deleteProperty(globalThis, "window"); }
});
