// PERF-SCALE-002 G-2: mixed load against the disposable scale environment (infra/performance/scale).
// Driven by infra/performance/scale/run-mixed.py, which runs this script twice (warm-up, then
// steady) so that server metrics and DB digests can be reset exactly between the two windows.
//
// Bundles (iterations): browse 70%, member reads 15%, write 10%, login 5%. Open model
// (constant-arrival-rate per bundle): a slow server does not lower the offered load.
// Contract gate only: a bundle with 1% or more contract failures fails the run (k6 exit 99).
// Latency is recorded, not judged here.
//
// Secrets: the pre-made sessions (SCALE_SESSIONS_FILE, a temp file outside the repository) and
// SCALE_COHORT_PASSWORD (environment) are never written to the summary.
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { validProductList, validProductDetail, thresholdVerdict, arrivalAccounting } from './lib/verdict.js';

// ---- 1. Inputs ----
const BASE = __ENV.SCALE_BASE_URL || 'http://127.0.0.1:18090';
if (!/^http:\/\/127\.0\.0\.1:\d{2,5}$/.test(BASE)) throw new Error('SCALE_BASE_URL must be a 127.0.0.1 origin');
const PHASE = __ENV.SCALE_PHASE || 'steady';
if (!['warmup', 'steady'].includes(PHASE)) throw new Error('SCALE_PHASE must be warmup or steady');
const RPS = Number(__ENV.SCALE_RPS || 20);                       // iterations per second, all bundles
const DURATION_SECONDS = Number(__ENV.SCALE_DURATION_SECONDS || 600);
const COHORT = Number(__ENV.SCALE_COHORT || 1000);                // members 1..COHORT can log in
const PASSWORD = __ENV.SCALE_COHORT_PASSWORD || '';
// index = VU id - 1. A temp file written by run-mixed.py outside the repository (removed after the
// run): the list can exceed the 32,767-character Windows limit for one environment variable.
const SESSIONS = __ENV.SCALE_SESSIONS_FILE ? JSON.parse(open(__ENV.SCALE_SESSIONS_FILE)) : [];
const VUS = JSON.parse(__ENV.SCALE_VUS || '{"browse":60,"member":30,"write":40,"login":20}');
if (!PASSWORD) throw new Error('SCALE_COHORT_PASSWORD is required');

export const MIX = { browse: 0.70, member: 0.15, write: 0.10, login: 0.05 };
const totalVus = Object.values(VUS).reduce((a, b) => a + b, 0);
// VU ids are unique across scenarios, so one session per possible VU id means no two VUs share a
// member (and a cart). The login bundle uses the other half of the cohort.
if (SESSIONS.length < totalVus) throw new Error(`SCALE_SESSIONS needs ${totalVus} sessions`);
if (totalVus > COHORT / 2) throw new Error('cohort too small: sessions and login members would overlap');

// ---- 2. Scenarios: one constant-arrival-rate scenario per bundle ----
// timeUnit 1m keeps small shares integral (20 it/s * 60 * 0.05 = 60 per minute).
function perMinute(share) {
  const rate = Math.round(RPS * 60 * share);
  if (rate < 1) throw new Error(`SCALE_RPS ${RPS} gives no iterations for share ${share}`);
  return rate;
}

const scenarios = {};
for (const [bundle, share] of Object.entries(MIX)) {
  scenarios[bundle] = {
    executor: 'constant-arrival-rate',
    exec: bundle,
    rate: perMinute(share),
    timeUnit: '1m',
    duration: `${DURATION_SECONDS}s`,
    preAllocatedVUs: Math.ceil(VUS[bundle] / 3),
    maxVUs: VUS[bundle],
    gracefulStop: '10s',
  };
}

export const options = {
  scenarios,
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max', 'count'],
  thresholds: Object.fromEntries(Object.keys(MIX).map((b) => [`scale_${b}_errors`, ['rate<0.01']])),
};

// ---- 3. Metrics ----
const metrics = Object.fromEntries(Object.keys(MIX).map((b) => [b, {
  latency: new Trend(`scale_${b}_latency`, true),
  errors: new Rate(`scale_${b}_errors`),
  iterations: new Counter(`scale_${b}_iterations`),
}]));
const statusErrors = new Counter('scale_status_errors');

function record(bundle, response, ok, name) {
  metrics[bundle].latency.add(response.timings.duration, { name });
  metrics[bundle].errors.add(!ok, { name });
  if (!ok) statusErrors.add(1, { name, status: String(response.status) });
}

// ---- 4. Setup: catalog sample only (sessions are made by the runner) ----
export function setup() {
  const productIds = [];
  const skuIds = [];
  for (let page = 0; page < 10; page += 1) {
    const list = http.get(`${BASE}/api/products?page=${page}&size=50`);
    if (!validProductList(list)) throw new Error(`product list page ${page} is not valid`);
    list.json('items').forEach((item) => productIds.push(item.productId));
  }
  for (const productId of productIds.slice(0, 80)) {
    const detail = http.get(`${BASE}/api/products/${productId}`);
    if (!validProductDetail(detail, productId)) continue;
    detail.json('skus')
      .filter((sku) => sku.purchasable && sku.availableQuantity > 1000)
      .forEach((sku) => skuIds.push(sku.skuId));
  }
  if (productIds.length < 100 || skuIds.length < 20) throw new Error('catalog sample too small');
  return { productIds, skuIds, runTag: `${PHASE}-${Date.now()}` };
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

function mySession() {
  return SESSIONS[exec.vu.idInTest - 1];
}

// replace: true keeps a cookie left in the VU jar from overriding this VU's session.
function authParams(session, name, extraHeaders) {
  return {
    cookies: { JSESSIONID: { value: session.sessionId, replace: true } },
    headers: Object.assign({ 'X-CSRF-TOKEN': session.csrf, 'Content-Type': 'application/json' }, extraHeaders || {}),
    tags: { name },
  };
}

// ---- 5. Bundles ----
export function browse(data) {
  const kind = Math.random();
  let response;
  let ok;
  let name;
  if (kind < 0.4) {
    name = 'GET /api/products';
    response = http.get(`${BASE}/api/products?page=${Math.floor(Math.random() * 50)}&size=20`, { tags: { name } });
    ok = validProductList(response);
  } else if (kind < 0.55) {
    name = 'GET /api/products?q';
    const term = encodeURIComponent(`합성 상품 ${1 + Math.floor(Math.random() * 99)}`);
    response = http.get(`${BASE}/api/products?q=${term}`, { tags: { name } });
    ok = validProductList(response);
  } else if (kind < 0.7) {
    name = 'GET /api/products?category';
    response = http.get(`${BASE}/api/products?category=syn-category-${1 + Math.floor(Math.random() * 20)}`, { tags: { name } });
    ok = validProductList(response);
  } else {
    name = 'GET /api/products/{id}';
    const productId = pick(data.productIds);
    response = http.get(`${BASE}/api/products/${productId}`, { tags: { name } });
    ok = validProductDetail(response, productId);
  }
  record('browse', response, ok, name);
  metrics.browse.iterations.add(1);
}

const MEMBER_READS = ['/api/orders', '/api/cart', '/api/subscriptions'];

export function member() {
  const session = mySession();
  const path = MEMBER_READS[exec.scenario.iterationInTest % MEMBER_READS.length];
  const response = http.get(`${BASE}${path}`, authParams(session, `GET ${path}`));
  record('member', response, response.status === 200, `GET ${path}`);
  metrics.member.iterations.add(1);
}

export function write(data) {
  const session = mySession();
  const add = http.post(`${BASE}/api/cart/items`, JSON.stringify({ skuId: pick(data.skuIds), quantity: 1 }),
    authParams(session, 'POST /api/cart/items'));
  record('write', add, add.status === 204, 'POST /api/cart/items');
  if (add.status !== 204) return;

  const cart = http.get(`${BASE}/api/cart`, authParams(session, 'GET /api/cart'));
  record('write', cart, cart.status === 200, 'GET /api/cart');
  if (cart.status !== 200) return;

  // Unique per run, VU and iteration, so a re-run on the same DB never replays an old key.
  const key = `g2-${data.runTag}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`;
  const checkout = http.post(`${BASE}/api/checkout`,
    JSON.stringify({ addressId: session.memberId, cartVersion: cart.json('version') }),
    authParams(session, 'POST /api/checkout', { 'Idempotency-Key': key }));
  const order = checkout.status === 200 ? checkout.json() : null;
  record('write', checkout, !!order && !!order.providerOrderId, 'POST /api/checkout');
  if (!order) return;

  // local-integration sandbox adapter: a key without the unknown_/fail_ prefix is DONE.
  const confirm = http.post(`${BASE}/api/payments/toss/confirm`,
    JSON.stringify({ paymentKey: `perf_${key}`, providerOrderId: order.providerOrderId, amount: order.amount }),
    authParams(session, 'POST /api/payments/toss/confirm'));
  record('write', confirm, confirm.status === 200 && confirm.json('status') === 'SUCCEEDED',
    'POST /api/payments/toss/confirm');
  metrics.write.iterations.add(1);
}

export function login() {
  const id = COHORT / 2 + 1 + Math.floor(Math.random() * (COHORT / 2));
  http.cookieJar().clear(BASE);
  const csrf = http.get(`${BASE}/api/auth/csrf`, { tags: { name: 'GET /api/auth/csrf' } });
  if (csrf.status !== 200) {
    record('login', csrf, false, 'GET /api/auth/csrf');
    return;
  }
  const response = http.post(`${BASE}/api/auth/login`,
    JSON.stringify({ email: `syn-member-${id}@perf.invalid`, password: PASSWORD }),
    { headers: { 'X-CSRF-TOKEN': csrf.json('token'), 'Content-Type': 'application/json' },
      tags: { name: 'POST /api/auth/login' } });
  record('login', response, response.status === 200, 'POST /api/auth/login');
  metrics.login.iterations.add(1);
}

// ---- 6. Summary: per-bundle table + verdict (no sessions, no password) ----
export function handleSummary(data) {
  const value = (metric, stat) => (data.metrics[metric] ? data.metrics[metric].values[stat] : null);
  const bundles = Object.fromEntries(Object.keys(MIX).map((b) => [b, {
    share: MIX[b],
    scheduled_iterations: perMinute(MIX[b]) * DURATION_SECONDS / 60,
    completed_iterations: value(`scale_${b}_iterations`, 'count') || 0,
    requests: value(`scale_${b}_latency`, 'count'),
    p50_ms: value(`scale_${b}_latency`, 'med'),
    p95_ms: value(`scale_${b}_latency`, 'p(95)'),
    p99_ms: value(`scale_${b}_latency`, 'p(99)'),
    max_ms: value(`scale_${b}_latency`, 'max'),
    error_rate: value(`scale_${b}_errors`, 'rate'),
  }]));
  const scheduled = Object.values(bundles).reduce((sum, b) => sum + b.scheduled_iterations, 0);
  const completed = Object.values(bundles).reduce((sum, b) => sum + b.completed_iterations, 0);
  const summary = {
    phase: PHASE,
    rps_iterations: RPS,
    duration_seconds: DURATION_SECONDS,
    bundles,
    accounting: arrivalAccounting(data, scheduled, completed),
    status_errors: value('scale_status_errors', 'count') || 0,
    verdict: thresholdVerdict(data),
  };
  const out = { stdout: `${JSON.stringify(summary)}\n` };
  if (__ENV.SCALE_SUMMARY) out[__ENV.SCALE_SUMMARY] = JSON.stringify(summary, null, 2);
  return out;
}
