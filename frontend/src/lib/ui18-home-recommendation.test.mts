import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import type { ProductDetail } from "./api.ts";
import { createRecommendationMetadataLoader, recommendationMetadata } from "./recommendation-metadata.ts";

function detail(productId: number): ProductDetail {
  return { productId, name: "사료", petType: "DOG", shortDescription: null, description: null, thumbnailUrl: null, category: { categoryId: 1, name: "사료", slug: "food" }, brand: { brandId: 1, name: "실제 브랜드", slug: "brand", logoUrl: null }, images: [], optionGroups: [], detailSections: [], trust: { averageRating: 4.2, reviewCount: 3, questionCount: 0 }, purchasable: true, skus: [{ skuId: 5, skuName: "서버 옵션", price: 17200, compareAtPrice: null, discountRate: null, subscribable: true, purchasable: true, availableDeliveryCycles: [4], availableQuantity: 2, selectedOptions: [] }] };
}

test("recommendation metadata uses exact labelled server SKU, brand and trust, without synthetic data", () => {
  assert.deepEqual(recommendationMetadata(detail(1)), { brandName: "실제 브랜드", price: 17200, priceCaption: "서버 옵션 기준", averageRating: 4.2, reviewCount: 3, subscribable: true });
  assert.deepEqual(recommendationMetadata({ ...detail(1), skus: [], brand: null, trust: { averageRating: null, reviewCount: 0, questionCount: 0 } }), { brandName: null, price: null, priceCaption: null, averageRating: null, reviewCount: 0, subscribable: false });
});

test("visible-eight loading preserves requested order and caps concurrency at three", async () => {
  let running = 0;
  let peak = 0;
  const calls: number[] = [];
  const loader = createRecommendationMetadataLoader(async (id) => {
    calls.push(Number(id)); running += 1; peak = Math.max(peak, running);
    await new Promise((resolve) => setTimeout(resolve, 1));
    running -= 1; return detail(Number(id));
  });
  const ids = [8, 2, 6, 1, 3, 4, 7, 5, 9, 10];
  const result = await loader(ids, new AbortController().signal);
  assert.deepEqual([...result.keys()], ids.slice(0, 8));
  assert.equal(calls.length, 8);
  assert.equal(peak, 3);
});

test("cache reuses successful metadata, expires, and retains at most eight entries", async () => {
  let time = 0;
  const calls: string[] = [];
  const loader = createRecommendationMetadataLoader(async (id) => { calls.push(id); return detail(Number(id)); }, () => time);
  const signal = new AbortController().signal;
  await loader([1, 2, 3, 4, 5, 6, 7, 8], signal);
  await loader([1], signal);
  assert.equal(calls.length, 8);
  await loader([9], signal);
  await loader([2], signal);
  assert.equal(calls.length, 10);
  time = 60_001;
  await loader([1], signal);
  assert.equal(calls.length, 11);
});

test("aborted generations ignore inflight results and skip queued requests; failures remain isolated", async () => {
  const calls: string[] = [];
  const pending: (() => void)[] = [];
  const loader = createRecommendationMetadataLoader((id) => new Promise((resolve) => { calls.push(id); pending.push(() => resolve(detail(Number(id)))); }));
  const controller = new AbortController();
  const stale = loader([1, 2, 3, 4, 5], controller.signal);
  await Promise.resolve();
  controller.abort();
  pending.forEach((resolve) => resolve());
  assert.equal((await stale).size, 0);
  assert.deepEqual(calls, ["1", "2", "3"]);
  const failureTolerant = createRecommendationMetadataLoader(async (id) => { if (id === "2") throw new Error("unavailable"); return detail(Number(id)); });
  assert.deepEqual([...(await failureTolerant([1, 2, 3], new AbortController().signal)).keys()], [1, 3]);
});

test("home and card wiring retains server filtering, member CSRF quickadd and labelled comparison", async () => {
  const home = await readFile(new URL("../components/home-catalog.tsx", import.meta.url), "utf8");
  assert.match(home, /subscribable: true, page: 0, size: 8/);
  const card = await readFile(new URL("../components/catalog-product-card.tsx", import.meta.url), "utf8");
  assert.match(card, /aria-label=\{`\$\{productName\} 비교에 담기`\}/);
  assert.match(card, /auth.executeWithCsrf\(\(csrf\) => cartApi.add\(singleSku.skuId, 1, csrf\)\)/);
  assert.match(card, /if \(auth.status !== "authenticated"\) return null/);
  const section = await readFile(new URL("../components/recommendation-section.tsx", import.meta.url), "utf8");
  assert.match(section, /recommendationRequestId: response.requestId/);
  assert.match(section, /recommendationRequestId: requestId/);
  assert.match(section, /filter\(\(item\) => filterRef.current\?\.\(item\) \?\? true\).slice\(0, RECOMMENDATION_VISIBLE_LIMIT\)/);
});

test("stalled metadata requests time out, abort their detail fetch, and release queue slots without caching late results", async () => {
  let firstAttempt = true;
  let resolveLate: ((value: ProductDetail) => void) | undefined;
  const signals: AbortSignal[] = [];
  const loader = createRecommendationMetadataLoader((id, signal) => {
    if (signal) signals.push(signal);
    if (id === "1" && firstAttempt) {
      firstAttempt = false;
      return new Promise((resolve) => { resolveLate = resolve; });
    }
    return Promise.resolve(detail(Number(id)));
  }, Date.now, 50);

  const result = await loader([1, 2, 3, 4], new AbortController().signal);
  assert.deepEqual([...result.keys()], [2, 3, 4]);
  assert.equal(signals[0].aborted, true);
  resolveLate?.(detail(1));
  await Promise.resolve();
  await Promise.resolve();
  const retried = await loader([1], new AbortController().signal);
  assert.deepEqual([...retried.keys()], [1]);
  assert.equal(signals.length, 5);
});

test("caller abort releases active requests and drops queued metadata without awaiting ignored fetches", async () => {
  const signals: AbortSignal[] = [];
  const calls: string[] = [];
  const loader = createRecommendationMetadataLoader((id, signal) => {
    calls.push(id);
    if (signal) signals.push(signal);
    return new Promise(() => {});
  }, Date.now, 100);
  const controller = new AbortController();
  const pending = loader([1, 2, 3, 4], controller.signal);
  await Promise.resolve();
  controller.abort();
  assert.equal((await pending).size, 0);
  assert.deepEqual(calls, ["1", "2", "3"]);
  assert.equal(signals.every((signal) => signal.aborted), true);
});
