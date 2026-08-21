import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";
import { Counter, Rate, Trend } from "k6/metrics";

const SUPPORTED_RATES = [25, 50, 100, 150, 200, 250];
const WARMUP_DURATION = "30s";
const MEASUREMENT_DURATION = "2m";
const MEASUREMENT_SECONDS = 120;
const REQUIRED_ACKNOWLEDGEMENT = "YES";
const PREALLOCATED_VUS = 250;
const MAX_VUS = 1000;

export const measurementIterations = new Counter("production_capacity_measurement_iterations");
export const warmupExpectedStatusErrorRate = new Rate("production_capacity_warmup_expected_status_error_rate");
export const expectedStatusErrorRate = new Rate("production_capacity_expected_status_error_rate");
export const measurementLatency = new Trend("production_capacity_measurement_latency", true);

function configuredRate() {
  const rate = Number(__ENV.TARGET_RPS);
  if (!SUPPORTED_RATES.includes(rate)) {
    throw new Error("TARGET_RPS must be one of: 25, 50, 100, 150, 200, 250.");
  }
  return rate;
}

export function productionTargetUrl() {
  const targetUrl = __ENV.PRODUCTION_TARGET_URL || "";
  const targetHost = __ENV.PRODUCTION_TARGET_HOST || "";
  const acknowledgement = __ENV.PRODUCTION_LOAD_ACKNOWLEDGEMENT || "";
  const match = targetUrl.match(/^https:\/\/([^\/?#:@]+)(?::([0-9]{1,5}))?\/?$/i);

  if (!match) {
    throw new Error("PRODUCTION_TARGET_URL must be an HTTPS origin without credentials, query, fragment, or path.");
  }
  if (!targetHost || targetHost.toLowerCase() !== match[1].toLowerCase()) {
    throw new Error("PRODUCTION_TARGET_HOST must explicitly match the HTTPS target host.");
  }
  if (acknowledgement !== REQUIRED_ACKNOWLEDGEMENT) {
    throw new Error("PRODUCTION_LOAD_ACKNOWLEDGEMENT must be YES before load generation.");
  }
  if (match[2] && (Number(match[2]) < 1 || Number(match[2]) > 65535)) {
    throw new Error("PRODUCTION_TARGET_URL port must be between 1 and 65535.");
  }
  return targetUrl.endsWith("/") ? targetUrl.slice(0, -1) : targetUrl;
}

export function optionsForProductionCapacity() {
  productionTargetUrl();
  const targetRate = configuredRate();
  return {
    scenarios: {
      warmup: {
        executor: "constant-arrival-rate",
        exec: "warmup",
        rate: targetRate,
        timeUnit: "1s",
        duration: WARMUP_DURATION,
        gracefulStop: "0s",
        preAllocatedVUs: PREALLOCATED_VUS,
        maxVUs: MAX_VUS,
        tags: { cohort: "production-capacity-api-products", phase: "warmup" },
      },
      measurement: {
        executor: "constant-arrival-rate",
        exec: "measure",
        rate: targetRate,
        timeUnit: "1s",
        duration: MEASUREMENT_DURATION,
        startTime: WARMUP_DURATION,
        gracefulStop: "0s",
        preAllocatedVUs: PREALLOCATED_VUS,
        maxVUs: MAX_VUS,
        tags: { cohort: "production-capacity-api-products", phase: "measurement" },
      },
    },
    thresholds: {
      production_capacity_warmup_expected_status_error_rate: ["rate==0"],
      production_capacity_expected_status_error_rate: ["rate==0"],
      dropped_iterations: ["count==0"],
    },
    discardResponseBodies: true,
    summaryTrendStats: ["med", "p(95)", "p(99)", "max"],
  };
}

export function request(measurement) {
  const response = http.get(`${productionTargetUrl()}/api/products`, {
    redirects: 0,
    responseType: "none",
    tags: { cohort: "production-capacity-api-products", name: "production-capacity-api-products" },
  });
  const expected = check(response, { "expected status": (result) => result.status === 200 });

  if (!measurement) {
    warmupExpectedStatusErrorRate.add(!expected);
    if (!expected) {
      exec.test.abort("Production warm-up received a non-200 response.");
    }
    return;
  }

  measurementIterations.add(1);
  expectedStatusErrorRate.add(!expected);
  measurementLatency.add(response.timings.duration);
}

export function handleSummaryForProductionCapacity(data) {
  const values = (metric) => data.metrics[metric]?.values || {};
  const iterations = values("production_capacity_measurement_iterations");
  const latency = values("production_capacity_measurement_latency");
  const summary = {
    targetRps: configuredRate(),
    actualRps: (iterations.count || 0) / MEASUREMENT_SECONDS,
    droppedIterations: data.metrics.dropped_iterations ? data.metrics.dropped_iterations.values.count : 0,
    p50Ms: latency.med ?? null,
    p95Ms: latency["p(95)"] ?? null,
    p99Ms: latency["p(99)"] ?? null,
    maxMs: latency.max ?? null,
    expectedStatusErrorRate: values("production_capacity_expected_status_error_rate").rate ?? 0,
    allocatedVUs: data.metrics.vus_max ? data.metrics.vus_max.values.max : null,
    activeVUs: data.metrics.vus ? data.metrics.vus.values.max : null,
  };
  return { stdout: `${JSON.stringify(summary)}\n` };
}
