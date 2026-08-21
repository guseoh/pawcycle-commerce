import http from "k6/http";
import { check } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const SUPPORTED_VUS = [1, 5, 10, 20];
const WARMUP_DURATION = "30s";
const MEASUREMENT_DURATION = "2m";
const MEASUREMENT_SECONDS = 120;

export const measurementRequests = new Counter("baseline_measurement_requests");
export const expectedStatusErrorRate = new Rate("baseline_expected_status_error_rate");
export const measurementLatency = new Trend("baseline_measurement_latency", true);

export function localBaseUrl() {
  const raw = __ENV.BASE_URL || "http://127.0.0.1:8080";
  const match = raw.match(
    /^http:\/\/(127\.0\.0\.1|localhost|\[::1\])(?::([0-9]{1,5}))?\/?$/
  );

  if (!match) {
    throw new Error(
      "BASE_URL must be an http loopback origin without credentials, query, fragment, or path."
    );
  }

  if (match[2]) {
    const port = Number(match[2]);
    if (port < 1 || port > 65535) {
      throw new Error("BASE_URL port must be between 1 and 65535.");
    }
  }

  return raw.endsWith("/") ? raw.slice(0, -1) : raw;
}

function configuredVus() {
  const vus = Number(__ENV.VUS || 1);
  if (!SUPPORTED_VUS.includes(vus)) {
    throw new Error("VUS must be one of: 1, 5, 10, 20.");
  }
  return vus;
}

export function optionsFor(cohort) {
  const vus = configuredVus();
  return {
    scenarios: {
      warmup: {
        executor: "constant-vus",
        exec: "warmup",
        vus: 1,
        duration: WARMUP_DURATION,
        gracefulStop: "0s",
        tags: { cohort, phase: "warmup" },
      },
      measurement: {
        executor: "constant-vus",
        exec: "measure",
        vus,
        duration: MEASUREMENT_DURATION,
        startTime: WARMUP_DURATION,
        gracefulStop: "0s",
        tags: { cohort, phase: "measurement" },
      },
    },
    summaryTrendStats: ["med", "p(95)", "p(99)", "max"],
  };
}

export function request(cohort, path, measurement) {
  const response = http.get(`${localBaseUrl()}${path}`, {
    redirects: 0,
    tags: { cohort, name: cohort },
  });
  const expected = check(response, { "expected status": (result) => result.status === 200 });

  if (measurement) {
    measurementRequests.add(1);
    expectedStatusErrorRate.add(!expected);
    measurementLatency.add(response.timings.duration);
  }
}

export function selectPublicProductId() {
  const response = http.get(`${localBaseUrl()}/api/products`, {
    redirects: 0,
    tags: { cohort: "api-product-detail", name: "api-product-list-setup" },
  });
  if (response.status !== 200) {
    throw new Error("Public product list is unavailable for the detail cohort.");
  }

  const products = response.json("products");
  if (!Array.isArray(products) || products.length === 0 || !products[0].productId) {
    throw new Error("Public product list has no product available for the detail cohort.");
  }
  return String(products[0].productId);
}

export function detailRequest(productId, measurement) {
  request("api-product-detail", `/api/products/${encodeURIComponent(productId)}`, measurement);
}

export function handleSummaryFor(cohort, data) {
  const values = (metric) => data.metrics[metric].values;
  const summary = {
    cohort,
    load: {
      warmup: "1 VU / 30s (excluded)",
      measurement: `${configuredVus()} VU / 2m`,
    },
    throughput: values("baseline_measurement_requests").count / MEASUREMENT_SECONDS,
    latencyMs: {
      p50: values("baseline_measurement_latency").med,
      p95: values("baseline_measurement_latency")["p(95)"],
      p99: values("baseline_measurement_latency")["p(99)"],
      max: values("baseline_measurement_latency").max,
    },
    expectedStatusErrorRate: values("baseline_expected_status_error_rate").rate,
  };
  const output = `${JSON.stringify(summary)}\n`;
  const resultsDir = __ENV.RESULTS_DIR;

  if (!resultsDir) {
    return { stdout: output };
  }
  return {
    stdout: output,
    [`${resultsDir}/${cohort}-${configuredVus()}vu.json`]: output,
  };
}
