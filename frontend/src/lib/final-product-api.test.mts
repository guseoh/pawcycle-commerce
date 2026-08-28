import assert from "node:assert/strict";
import test from "node:test";
import { finalProductApi, newInteractionEventId, reorderTimingItems } from "./final-product-api.ts";

function capture(responses: Response[]) {
  const original = globalThis.fetch;
  const calls: { path: string; method: string; headers: Headers; body: unknown }[] = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls.push({
      path: String(input),
      method: init?.method ?? "GET",
      headers: new Headers(init?.headers),
      body: init?.body ? JSON.parse(String(init.body)) : undefined,
    });
    return (responses.shift() ?? Response.json({}))!.clone();
  }) as typeof fetch;
  return { calls, restore: () => { globalThis.fetch = original; } };
}

const recommendation = { requestId: "req-1", products: [{ productId: 301, name: "사료", shortDescription: null, thumbnailUrl: null, category: { categoryId: 1, name: "사료", slug: "food" }, reason: "인기 상품", strategy: "POPULAR" as const }] };

test("최종 추천 API는 전략별 canonical endpoint와 응답을 보존한다", async () => {
  const mock = capture([Response.json(recommendation), Response.json(recommendation), Response.json(recommendation), Response.json(recommendation)]);
  try {
    assert.deepEqual((await finalProductApi.recommendations.popular(4, "DOG")).products, recommendation.products);
    await finalProductApi.recommendations.trending();
    await finalProductApi.recommendations.related(301);
    await finalProductApi.recommendations.complementary(301);
    assert.deepEqual(mock.calls.map((call) => call.path), [
      "/api/recommendations/popular?limit=4&petType=DOG",
      "/api/recommendations/trending",
      "/api/products/301/related",
      "/api/products/301/complementary",
    ]);
  } finally { mock.restore(); }
});

test("상품 상호작용은 CSRF와 event envelope를 사용하고 raw 검색어를 보내지 않는다", async () => {
  const mock = capture([new Response(null, { status: 204 })]);
  try {
    await finalProductApi.interactions.send([{
      eventId: "event-1", type: "SEARCH", source: "catalog",
      context: { hasTextQuery: true, petType: "DOG", category: "food", facets: ["size:small"], sort: "RECOMMENDED" },
    }], "csrf-token");
    assert.equal(mock.calls[0].path, "/api/interactions");
    assert.equal(mock.calls[0].method, "POST");
    assert.equal(mock.calls[0].headers.get("X-CSRF-TOKEN"), "csrf-token");
    assert.deepEqual(mock.calls[0].body, { events: [{ eventId: "event-1", type: "SEARCH", source: "catalog", context: { hasTextQuery: true, petType: "DOG", category: "food", facets: ["size:small"], sort: "RECOMMENDED" } }] });
    assert.doesNotMatch(JSON.stringify(mock.calls[0].body), /raw-query/);
  } finally { mock.restore(); }
});

test("상호작용 event id는 UUID 형식이고 빈 batch는 요청하지 않는다", async () => {
  const eventId = newInteractionEventId();
  assert.match(eventId ?? "", /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  const mock = capture([]);
  try {
    await finalProductApi.interactions.send([], "csrf-token");
    assert.equal(mock.calls.length, 0);
  } finally { mock.restore(); }
});

test("비추천 보조 기능과 compare는 명시된 경로·반복 query를 사용한다", async () => {
  const mock = capture([Response.json({ items: [] }), Response.json({ orderId: 9, options: [] }), Response.json({ status: "INSUFFICIENT_REVIEWS", summary: null, reviewCount: 1, averageRating: 4 }), Response.json({ products: [], aiStatus: "UNAVAILABLE", aiSummary: null })]);
  try {
    assert.deepEqual(reorderTimingItems(await finalProductApi.reorderTiming()), []);
    await finalProductApi.orderSubscriptionOptions(9);
    await finalProductApi.reviewSummary(301);
    await finalProductApi.compare([301, 302]);
    assert.deepEqual(mock.calls.map((call) => call.path), [
      "/api/recommendations/reorder-timing",
      "/api/orders/9/subscription-options",
      "/api/products/301/reviews/summary",
      "/api/products/compare?productId=301&productId=302",
    ]);
  } finally { mock.restore(); }
});
