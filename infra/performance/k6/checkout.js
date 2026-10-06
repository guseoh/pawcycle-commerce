import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';

const origin = __ENV.BASE_URL;
const collector = __ENV.CHECKOUT_COLLECTOR;
if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(origin || '') ||
    !/^http:\/\/127\.0\.0\.1:\d+$/.test(collector || '')) {
  throw new Error('Checkout requires local loopback origins.');
}
const pool = JSON.parse(__ENV.CHECKOUT_POOL || '[]');
if (pool.length !== 120) throw new Error('Checkout requires exactly 120 dedicated members.');
const duration = __ENV.CHECKOUT_PHASE === 'warmup' ? '30s' : '120s';
const rate = Number(__ENV.CHECKOUT_RPS || '20');
if (![5, 10, 15, 20].includes(rate)) throw new Error('Unsupported Checkout stage rate.');
const requests = new Counter('checkout_requests');
const statusErrors = new Counter('checkout_status_errors');
const errors = new Rate('checkout_expected_status_errors');
const latency = new Trend('checkout_latency', true);
http.setResponseCallback(http.expectedStatuses(200));
export const options = {
  scenarios: { checkout: { executor: 'constant-arrival-rate', rate, timeUnit: '1s',
    duration, preAllocatedVUs: 120, maxVUs: 120, gracefulStop: '5s' } },
  thresholds: { checkout_expected_status_errors: ['rate==0'] },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: true,
};
export function setup() {
  if (http.post(`${collector}/start`).status !== 200) throw new Error('Snapshot gate failed.');
}
export default function () {
  const member = pool[__VU - 1];
  const response = http.post(`${origin}/api/checkout`,
    JSON.stringify({ addressId: member.addressId, cartVersion: 0 }), {
      redirects: 0,
      headers: { Cookie: member.cookie, 'X-CSRF-TOKEN': member.csrf,
        'Content-Type': 'application/json',
        'Idempotency-Key': `${__ENV.CHECKOUT_RUN}-${__ENV.CHECKOUT_PHASE}-${__VU}-${__ITER}` },
      tags: { name: 'POST /api/checkout' },
    });
  requests.add(1);
  errors.add(response.status !== 200);
  if (response.status !== 200) statusErrors.add(1, { status: String(response.status) });
  latency.add(response.timings.duration);
}
export function teardown() {
  if (http.post(`${collector}/end`).status !== 200) throw new Error('Snapshot gate failed.');
}
export function handleSummary(data) {
  return { [__ENV.CHECKOUT_SUMMARY]: JSON.stringify({ metrics: data.metrics, state: data.state }, null, 2) };
}
