import http from "k6/http";
import { check } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { localBaseUrl } from "./baseline.js";
import { arrivalAccounting, thresholdVerdict, validHtmlPage, validProductDetail, validProductList } from "./verdict.js";

const SUPPORTED_RATES = [250, 500, 750, 1000];
const MEASUREMENT_SECONDS = 120;

function catalogCardinality() {
  return String(__ENV.CATALOG_CARDINALITY || "unknown");
}

export const measurementIterations = new Counter("capacity_measurement_iterations");
export const expectedStatusErrorRate = new Rate("capacity_expected_status_error_rate");
export const measurementLatency = new Trend("capacity_measurement_latency", true);
export const responseContractErrorRate = new Rate("capacity_response_contract_error_rate");

const CONTRACTS = {
  "capacity-api-products": (response) => validProductList(response),
  "capacity-products-page": (response) => validHtmlPage(response),
  "capacity-api-product-detail": (response, productId) => validProductDetail(response, productId),
};

function configuredRate() {
  const rate = Number(__ENV.TARGET_RPS || 250);
  if (!SUPPORTED_RATES.includes(rate)) {
    throw new Error("TARGET_RPS must be one of: 250, 500, 750, 1000.");
  }
  return rate;
}

export function optionsForCapacity(cohort) {
  return {
    scenarios: {
      warmup: {
        executor: "constant-vus",
        exec: "warmup",
        vus: 1,
        duration: "30s",
        gracefulStop: "0s",
        tags: { cohort, phase: "warmup" },
      },
      measurement: {
        executor: "constant-arrival-rate",
        exec: "measure",
        rate: configuredRate(),
        timeUnit: "1s",
        duration: "2m",
        startTime: "30s",
        gracefulStop: "0s",
        preAllocatedVUs: 250,
        maxVUs: 1000,
        tags: { cohort, phase: "measurement" },
      },
    },
    thresholds: {
      capacity_expected_status_error_rate: ["rate==0"],
      capacity_response_contract_error_rate: ["rate==0"],
      dropped_iterations: ["count==0"],
    },
    summaryTrendStats: ["med", "p(95)", "p(99)", "max"],
  };
}

export function request(cohort, path, measurement, productId) {
  const response = http.get(`${localBaseUrl()}${path}`, {
    redirects: 0,
    tags: { cohort, name: cohort, catalog_cardinality: catalogCardinality() },
  });
  const expected = check(response, { "expected status": (result) => result.status === 200 });
  const contract = check(response, {
    "response contract": (result) => CONTRACTS[cohort](result, productId),
  });
  if (measurement) {
    measurementIterations.add(1);
    expectedStatusErrorRate.add(!expected);
    responseContractErrorRate.add(!contract);
    measurementLatency.add(response.timings.duration);
  }
}

export function selectPublicProductId() {
  const response = http.get(`${localBaseUrl()}/api/products`, {
    redirects: 0,
    tags: { cohort: "capacity-api-product-detail", name: "capacity-api-product-list-setup", catalog_cardinality: catalogCardinality() },
  });
  if (response.status !== 200) throw new Error("Public product list is unavailable for the detail cohort.");
  const products = response.json("items") || response.json("products");
  if (!Array.isArray(products) || products.length === 0 || !products[0].productId) {
    throw new Error("Public product list has no product available for the detail cohort.");
  }
  return String(products[0].productId);
}

export function handleSummaryForCapacity(cohort, data) {
  const values = (metric) => data.metrics[metric].values;
  const iterations = values("capacity_measurement_iterations");
  const summary = {
    cohort,
    catalogCardinality: catalogCardinality(),
    targetRps: configuredRate(),
    actualRps: iterations.count / MEASUREMENT_SECONDS,
    droppedIterations: data.metrics.dropped_iterations ? data.metrics.dropped_iterations.values.count : 0,
    droppedIterationsPerSecond: (data.metrics.dropped_iterations ? data.metrics.dropped_iterations.values.count : 0) / MEASUREMENT_SECONDS,
    iterations: iterations.count,
    allocatedVUs: data.metrics.vus_max ? data.metrics.vus_max.values.max : null,
    activeVUsMax: data.metrics.vus ? data.metrics.vus.values.max : null,
    latencyMs: { p50: values("capacity_measurement_latency").med, p95: values("capacity_measurement_latency")["p(95)"], p99: values("capacity_measurement_latency")["p(99)"], max: values("capacity_measurement_latency").max },
    expectedStatusErrorRate: values("capacity_expected_status_error_rate").rate,
    responseContractErrorRate: values("capacity_response_contract_error_rate").rate,
    accounting: arrivalAccounting(data, configuredRate() * MEASUREMENT_SECONDS, iterations.count),
    verdict: thresholdVerdict(data),
  };
  return { stdout: `${JSON.stringify(summary)}\n` };
}
