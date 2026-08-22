import http from "k6/http";
import { check } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const UNITS = { products: 8, productDetail: 5, subscriptions: 2, cart: 1, wishlist: 1, orders: 2, member: 1 };
const TOTAL_UNITS = 20;
const WRITE_REQUESTS_PER_CYCLE = 5;
const SYNTHETIC_MEMBER_EMAIL = /^qa-foundation-004@[A-Za-z0-9.-]+$/;
const FIXTURE_PRODUCT_NAME = "[QA FOUNDATION-004] 정기배송 사료";
const FIXTURE_SKU_NAME = "[QA FOUNDATION-004] 2kg";
const FIXTURE_ORDER_NUMBER = "PERF-PH8-003-ORDER";
const FIXTURE_SUBSCRIPTION_NEXT_ORDER_DATE = "2030-01-01";
const PROFILES = {
  steady: { duration: "2m", multiplier: 1 },
  burst: { duration: "30s", multiplier: 2 },
  sustained: { duration: "10m", multiplier: 1 },
};

export const completed = new Counter("phase8d_completed_requests");
export const expectedStatusErrorRate = new Rate("phase8d_expected_status_error_rate");
export const latency = new Trend("phase8d_request_latency", true);
const operationMetrics = Object.fromEntries(
  Object.keys(UNITS)
    .concat(["cartAdd", "cartUpdate", "cartDelete", "wishlistAdd", "wishlistDelete"])
    .map((operation) => [operation, new Counter(`phase8d_${operation}_requests`)]),
);

function baseUrl() {
  const raw = __ENV.BASE_URL || "http://127.0.0.1:8080";
  if (!/^http:\/\/(127\.0\.0\.1|localhost|\[::1\])(?::[0-9]{1,5})?$/.test(raw)) {
    throw new Error("Phase 8-D BASE_URL must be an http loopback origin; Production and Cloud targets are forbidden.");
  }
  return raw;
}

function targetRps() {
  const value = Number(__ENV.TARGET_RPS || 20);
  if (!Number.isInteger(value) || value < 20 || value > 200 || value % TOTAL_UNITS !== 0) {
    throw new Error("TARGET_RPS must be an integer from 20 through 200 and divisible by 20.");
  }
  return value;
}

function profile(name) {
  if (!PROFILES[name]) throw new Error(`Unsupported Phase 8-D profile: ${name}`);
  return PROFILES[name];
}

function authenticatedRequest(data, operation, method, path, body, expectedStatus) {
  const response = http.request(method, `${baseUrl()}${path}`, body, {
    redirects: 0,
    headers: {
      Cookie: `JSESSIONID=${data.sessionId}`,
      "X-CSRF-TOKEN": data.csrfToken,
      "Content-Type": "application/json",
    },
    tags: { cohort: "phase8d", name: operation },
  });
  const expected = check(response, { "expected status": (result) => result.status === expectedStatus });
  completed.add(1);
  operationMetrics[operation].add(1);
  expectedStatusErrorRate.add(!expected);
  latency.add(response.timings.duration, { operation });
}

export function setup() {
  const email = __ENV.PERF_PHASE8D_MEMBER_EMAIL || "";
  const password = __ENV.PERF_PHASE8D_MEMBER_PASSWORD || "";
  if (!SYNTHETIC_MEMBER_EMAIL.test(email) || !password) {
    throw new Error("Phase 8-D requires qa-foundation-004@<local-domain> and PERF_PHASE8D_MEMBER_PASSWORD.");
  }

  const csrf = http.get(`${baseUrl()}/api/auth/csrf`, { redirects: 0 });
  if (csrf.status !== 200) throw new Error("Phase 8-D setup could not obtain the pre-login CSRF token.");
  const token = csrf.json("token");
  if (!token) throw new Error("Phase 8-D setup received an empty pre-login CSRF token.");

  const login = http.post(`${baseUrl()}/api/auth/login`, JSON.stringify({ email, password }), {
    redirects: 0,
    headers: { "X-CSRF-TOKEN": token, "Content-Type": "application/json" },
  });
  if (login.status !== 200 || !login.cookies.JSESSIONID || !login.cookies.JSESSIONID[0]) {
    throw new Error("Phase 8-D setup login failed.");
  }

  const sessionId = login.cookies.JSESSIONID[0].value;
  const sessionHeaders = { Cookie: `JSESSIONID=${sessionId}` };
  const postLoginCsrf = http.get(`${baseUrl()}/api/auth/csrf`, { redirects: 0, headers: sessionHeaders });
  if (postLoginCsrf.status !== 200) throw new Error("Phase 8-D setup could not refresh the post-login CSRF token.");
  const csrfToken = postLoginCsrf.json("token");
  if (!csrfToken) throw new Error("Phase 8-D setup received an empty post-login CSRF token.");

  const list = http.get(`${baseUrl()}/api/products`, { redirects: 0 });
  if (list.status !== 200) throw new Error("Phase 8-D setup product fixture list is unavailable.");
  const products = list.json("products");
  const fixtureProduct = Array.isArray(products)
    ? products.find((product) => product.name === FIXTURE_PRODUCT_NAME)
    : null;
  const productId = fixtureProduct ? fixtureProduct.productId : null;
  if (!productId) throw new Error("Phase 8-D setup product fixture is unavailable.");

  const detail = http.get(`${baseUrl()}/api/products/${encodeURIComponent(productId)}`, { redirects: 0 });
  if (detail.status !== 200) throw new Error("Phase 8-D setup product-detail fixture is unavailable.");
  const skus = detail.json("skus");
  const fixtureSku = Array.isArray(skus) ? skus.find((sku) => sku.skuName === FIXTURE_SKU_NAME) : null;
  const skuId = fixtureSku ? fixtureSku.skuId : null;
  if (!skuId) throw new Error("Phase 8-D setup requires the exact QA fixture SKU.");

  const subscriptionsResponse = http.get(`${baseUrl()}/api/subscriptions`, {
    redirects: 0,
    headers: sessionHeaders,
  });
  if (subscriptionsResponse.status !== 200) throw new Error("Phase 8-D marker subscription is unavailable.");
  const subscriptions = subscriptionsResponse.json("subscriptions");
  const markerSubscription = Array.isArray(subscriptions)
    ? subscriptions.some((subscription) =>
      String(subscription.sku?.skuId) === String(skuId)
      && subscription.quantity === 1
      && subscription.deliveryCycleWeeks === 2
      && subscription.nextOrderDate === FIXTURE_SUBSCRIPTION_NEXT_ORDER_DATE)
    : false;

  const ordersResponse = http.get(`${baseUrl()}/api/orders`, { redirects: 0, headers: sessionHeaders });
  if (ordersResponse.status !== 200) throw new Error("Phase 8-D marker order is unavailable.");
  const orders = ordersResponse.json();
  const markerOrder = Array.isArray(orders)
    ? orders.some((order) => order.orderNumber === FIXTURE_ORDER_NUMBER)
    : false;

  if (!markerSubscription || !markerOrder) {
    throw new Error("Phase 8-D marker subscription/order fixture is missing; run the approved local seed first.");
  }

  return { sessionId, csrfToken, productId: String(productId), skuId: String(skuId) };
}

export function optionsForReadProfile(name) {
  baseUrl();
  const selected = profile(name);
  const total = targetRps() * selected.multiplier;
  const scenarios = {};
  Object.entries(UNITS).forEach(([operation, units]) => {
    scenarios[operation] = {
      executor: "constant-arrival-rate",
      exec: operation,
      rate: (total * units) / TOTAL_UNITS,
      timeUnit: "1s",
      duration: selected.duration,
      preAllocatedVUs: 20,
      maxVUs: 200,
      gracefulStop: "0s",
      tags: { phase: name, operation },
    };
  });
  return {
    scenarios,
    thresholds: {
      phase8d_expected_status_error_rate: ["rate==0"],
      dropped_iterations: ["count==0"],
    },
    summaryTrendStats: ["med", "p(95)", "p(99)", "max"],
  };
}

export const operations = {
  products: (data) => authenticatedRequest(data, "products", "GET", "/api/products", null, 200),
  productDetail: (data) => authenticatedRequest(data, "productDetail", "GET", `/api/products/${encodeURIComponent(data.productId)}`, null, 200),
  subscriptions: (data) => authenticatedRequest(data, "subscriptions", "GET", "/api/subscriptions", null, 200),
  cart: (data) => authenticatedRequest(data, "cart", "GET", "/api/cart", null, 200),
  wishlist: (data) => authenticatedRequest(data, "wishlist", "GET", "/api/wishlist", null, 200),
  orders: (data) => authenticatedRequest(data, "orders", "GET", "/api/orders", null, 200),
  member: (data) => authenticatedRequest(data, "member", "GET", "/api/auth/me", null, 200),
};

export function writeCycle(data) {
  authenticatedRequest(data, "cartAdd", "POST", "/api/cart/items", JSON.stringify({ skuId: Number(data.skuId), quantity: 1 }), 204);
  authenticatedRequest(data, "cartUpdate", "PATCH", `/api/cart/items/${encodeURIComponent(data.skuId)}`, JSON.stringify({ quantity: 1 }), 204);
  authenticatedRequest(data, "cartDelete", "DELETE", `/api/cart/items/${encodeURIComponent(data.skuId)}`, null, 204);
  authenticatedRequest(data, "wishlistAdd", "POST", `/api/wishlist/${encodeURIComponent(data.productId)}`, null, 204);
  authenticatedRequest(data, "wishlistDelete", "DELETE", `/api/wishlist/${encodeURIComponent(data.productId)}`, null, 204);
}

export function optionsForBoundedWrite() {
  baseUrl();
  return {
    scenarios: {
      boundedWrite: {
        executor: "constant-arrival-rate",
        exec: "boundedWrite",
        rate: targetRps() / WRITE_REQUESTS_PER_CYCLE,
        timeUnit: "1s",
        duration: "2m",
        preAllocatedVUs: 1,
        maxVUs: 1,
        gracefulStop: "0s",
        tags: { phase: "bounded-write", operation: "boundedWrite" },
      },
    },
    thresholds: {
      phase8d_expected_status_error_rate: ["rate==0"],
      dropped_iterations: ["count==0"],
    },
    summaryTrendStats: ["med", "p(95)", "p(99)", "max"],
  };
}

export function handleSummaryFor(profileName, data) {
  const metric = (name) => data.metrics[name]?.values || {};
  const requests = metric("phase8d_completed_requests").count || 0;
  const durationSeconds = profileName === "burst" ? 30 : profileName === "sustained" ? 600 : 120;
  const perOperation = Object.fromEntries(Object.keys(operationMetrics).map((operation) => {
    const count = metric(`phase8d_${operation}_requests`).count || 0;
    return [operation, { rps: count / durationSeconds, ratio: requests ? count / requests : 0 }];
  }));
  const trend = metric("phase8d_request_latency");
  const requestedRps = targetRps() * (profileName === "burst" ? 2 : 1);
  const summary = {
    profile: profileName,
    targetRps: requestedRps,
    actualRps: requests / durationSeconds,
    operations: perOperation,
    droppedIterations: metric("dropped_iterations").count || 0,
    expectedStatusErrorRate: metric("phase8d_expected_status_error_rate").rate || 0,
    latencyMs: {
      p50: trend.med ?? null,
      p95: trend["p(95)"] ?? null,
      p99: trend["p(99)"] ?? null,
      max: trend.max ?? null,
    },
    allocatedVUs: metric("vus_max").max ?? null,
    activeVUs: metric("vus").max ?? null,
  };
  return { stdout: `${JSON.stringify(summary)}\n` };
}
