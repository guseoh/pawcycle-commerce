import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { runInNewContext } from "node:vm";

const source = readFileSync(
  new URL("../k6/lib/isolated-capacity.js", import.meta.url),
  "utf8"
);

function harness(targetRate = 100, statuses = []) {
  class Metric {
    points = [];
    add(value) { this.points.push(value); }
  }
  const requests = [];
  // Stub k6 imports only; execute the actual options/request implementation.
  const executable = source
    .replace(/^import .*;\r?$/gm, "")
    .replace(/^export /gm, "");
  const api = runInNewContext(`${executable}\n({
    optionsForIsolatedCapacity, request, measurementIterations,
    warmupExpectedStatusErrorRate, expectedStatusErrorRate,
    measurementLatency, measurementClock
  })`, {
    __ENV: {
      TARGET_RPS: String(targetRate),
      ISOLATED_DATASET_ID: "catalog-core-10k-v1",
      ISOLATED_LOAD_ACKNOWLEDGEMENT: "YES",
    },
    localBaseUrl: () => "http://127.0.0.1:8080",
    http: {
      get(url, options) {
        requests.push({ url, options });
        return { status: statuses.shift() ?? 200, timings: { duration: 12 } };
      },
    },
    check: (response, checks) => Object.values(checks).every(fn => fn(response)),
    exec: { test: { abort() { throw new Error("Unexpected warm-up abort"); } } },
    Counter: Metric,
    Rate: Metric,
    Trend: Metric,
  });
  return { api, requests };
}

for (const [targetRate, warmupRate] of [[25, 25], [50, 50], [100, 50]]) {
  test(`target ${targetRate}: warm-up ${warmupRate}, measurement unchanged`, () => {
    const options = harness(targetRate).api.optionsForIsolatedCapacity();
    assert.equal(options.scenarios.warmup.rate, warmupRate);
    assert.equal(options.scenarios.warmup.duration, "30s");
    assert.equal(options.scenarios.measurement.rate, targetRate);
    assert.equal(options.scenarios.measurement.startTime, "30s");
    assert.equal(options.scenarios.measurement.duration, "2m");
  });
}

test("warm-up non-200 records a diagnostic error without preventing measurement", () => {
  const { api, requests } = harness(100, [503, 200]);
  assert.doesNotThrow(() => api.request(false));
  assert.deepEqual(Array.from(api.warmupExpectedStatusErrorRate.points), [true]);
  assert.equal(api.measurementIterations.points.length, 0);
  assert.equal(api.expectedStatusErrorRate.points.length, 0);
  assert.equal(api.measurementClock.points.length, 0);
  api.request(true);
  assert.deepEqual(Array.from(api.measurementIterations.points), [1]);
  assert.deepEqual(Array.from(api.expectedStatusErrorRate.points), [false]);
  assert.equal(api.measurementClock.points.length, 2);
  assert.equal(requests.length, 2);
  assert.ok(requests.every(({ options }) => options.redirects === 0));
  assert.doesNotMatch(source, /k6\/execution|exec\.test\.abort/);
});

test("measurement non-200 still records error, iteration, clock and latency", () => {
  const { api } = harness(100, [503]);
  api.request(true);
  assert.deepEqual(Array.from(api.expectedStatusErrorRate.points), [true]);
  assert.deepEqual(Array.from(api.measurementIterations.points), [1]);
  assert.deepEqual(Array.from(api.measurementLatency.points), [12]);
  assert.equal(api.measurementClock.points.length, 2);
  assert.equal(api.warmupExpectedStatusErrorRate.points.length, 0);
});

test("only warm-up error threshold is removed; error/drop thresholds remain", () => {
  const { thresholds } = harness().api.optionsForIsolatedCapacity();
  assert.deepEqual(Object.keys(thresholds).sort(), [
    "dropped_iterations", "isolated_capacity_expected_status_error_rate",
  ]);
  assert.deepEqual(Array.from(thresholds.isolated_capacity_expected_status_error_rate), ["rate==0"]);
  assert.deepEqual(Array.from(thresholds.dropped_iterations), ["count==0"]);
});
