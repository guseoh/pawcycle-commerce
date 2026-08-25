import assert from "node:assert/strict";
import test from "node:test";
import { commerceFinalApi } from "./commerce-final-api.ts";
import { categoryApi } from "./api.ts";

test("public category API uses the readonly category authority", async () => {
  const original = globalThis.fetch;
  let path = "";
  let method = "";
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    path = String(input);
    method = String(init?.method ?? "GET");
    return new Response(JSON.stringify({ items: [{ categoryId: 1, name: "사료", slug: "food" }] }), { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;
  try {
    assert.deepEqual(await categoryApi.list(), { items: [{ categoryId: 1, name: "사료", slug: "food" }] });
    assert.equal(path, "/api/categories");
    assert.equal(method, "GET");
  } finally {
    globalThis.fetch = original;
  }
});

test("admin operation uses the provided endpoint once with CSRF", async () => {
  const original = globalThis.fetch;
  let path = "";
  let csrf = "";
  let method = "";
  let calls = 0;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls += 1;
    path = String(input);
    method = String(init?.method ?? "GET");
    csrf = String(init?.headers && new Headers(init.headers).get("X-CSRF-TOKEN"));
    return new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;
  try {
    await commerceFinalApi.operation("refunds/9/process", "csrf-token");
    assert.equal(calls, 1);
    assert.equal(method, "POST");
    assert.equal(path, "/api/admin/refunds/9/process");
    assert.equal(csrf, "csrf-token");
  } finally {
    globalThis.fetch = original;
  }
});

test("admin billing retry uses its explicit recovery endpoint", async () => {
  const original = globalThis.fetch;
  let path = "";
  let method = "";
  let csrf = "";
  let calls = 0;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls += 1;
    path = String(input);
    method = String(init?.method ?? "GET");
    csrf = String(init?.headers && new Headers(init.headers).get("X-CSRF-TOKEN"));
    return new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;
  try {
    await commerceFinalApi.operation("payments/12/retry-billing", "csrf-token");
    assert.equal(calls, 1);
    assert.equal(path, "/api/admin/payments/12/retry-billing");
    assert.equal(method, "POST");
    assert.equal(csrf, "csrf-token");
  } finally {
    globalThis.fetch = original;
  }
});

test("admin delivery and return operations serialize required bodies", async () => {
  const original = globalThis.fetch;
  const requests: Array<{ path: string; body: string }> = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    requests.push({ path: String(input), body: String(init?.body ?? "") });
    return new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;
  try {
    await commerceFinalApi.operation("deliveries/3/ship", "csrf-token", { carrierCode: "CJ", trackingNumber: "T-3" });
    await commerceFinalApi.operation("returns/4/receive", "csrf-token", { restock: true });
    assert.deepEqual(requests, [
      { path: "/api/admin/deliveries/3/ship", body: JSON.stringify({ carrierCode: "CJ", trackingNumber: "T-3" }) },
      { path: "/api/admin/returns/4/receive", body: JSON.stringify({ restock: true }) },
    ]);
  } finally {
    globalThis.fetch = original;
  }
});

test("cart, wishlist, address, checkout and shipping recovery use the Commerce contract", async () => {
  const original = globalThis.fetch; const requests: Array<{ path:string; method:string; body:string; csrf:string; key:string }> = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => { const headers=new Headers(init?.headers); requests.push({path:String(input),method:String(init?.method),body:String(init?.body??""),csrf:headers.get("X-CSRF-TOKEN")??"",key:headers.get("Idempotency-Key")??""}); return new Response("{}", {status:200,headers:{"Content-Type":"application/json"}}); }) as typeof fetch;
  try { const address={name:"집",recipientName:"보호자",recipientPhone:"010",postalCode:"1",addressLine1:"서울",addressLine2:""}; await commerceFinalApi.addCart(5,2,"csrf"); await commerceFinalApi.addWishlist(3,"csrf"); await commerceFinalApi.createAddress(address,"csrf"); await commerceFinalApi.updateSubscriptionShipping(8,address,"csrf"); await commerceFinalApi.checkout(2,"csrf","checkout-key"); assert.deepEqual(requests.map(r=>[r.method,r.path,r.csrf,r.key]), [["POST","/api/cart/items","csrf",""],["POST","/api/wishlist/3","csrf",""],["POST","/api/addresses","csrf",""],["PUT","/api/subscriptions/8/shipping-address","csrf",""],["POST","/api/checkout","csrf","checkout-key"]]); assert.equal(requests[0].body,JSON.stringify({skuId:5,quantity:2})); assert.equal(requests[4].body,JSON.stringify({addressId:2})); } finally { globalThis.fetch=original; }
});

test("billing method accepts the Backend active fixture without a wrapper", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = (async () => new Response(JSON.stringify({ provider: "TOSS", configured: true, registered: false }), { status: 200, headers: { "Content-Type": "application/json" } })) as typeof fetch;
  try { assert.deepEqual(await commerceFinalApi.billingMethod(), { provider: "TOSS", configured: true, registered: false }); } finally { globalThis.fetch = original; }
});
