import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";
import { Counter, Rate, Trend } from "k6/metrics";
import { localBaseUrl } from "./baseline.js";

const SUPPORTED_RATES = [25, 50, 100, 150, 200, 250];
const SUPPORTED_DATASETS = [
  "catalog-core-control-v1",
  "catalog-core-10k-v1",
];
const REQUIRED_ACKNOWLEDGEMENT = "YES";
const WARMUP_DURATION = "30s";
const MEASUREMENT_DURATION = "2m";
const MEASUREMENT_SECONDS = 120;
const PREALLOCATED_VUS = 250;
const MAX_VUS = 1000;

export const measurementIterations = new Counter(
  "isolated_capacity_measurement_iterations"
);
export const warmupExpectedStatusErrorRate = new Rate(
  "isolated_capacity_warmup_expected_status_error_rate"
);
export const expectedStatusErrorRate = new Rate(
  "isolated_capacity_expected_status_error_rate"
);
export const measurementLatency = new Trend(
  "isolated_capacity_measurement_latency",
  true
);
export const measurementClock = new Trend("isolated_capacity_measurement_clock_ms");

function configuredRate() {
  const rate = Number(__ENV.TARGET_RPS);
  if (!SUPPORTED_RATES.includes(rate)) {
    throw new Error("TARGET_RPS must be one of: 25, 50, 100, 150, 200, 250.");
  }
  return rate;
}

export function configuredDatasetId() {
  const datasetId = __ENV.ISOLATED_DATASET_ID || "";
  if (!SUPPORTED_DATASETS.includes(datasetId)) {
    throw new Error(
      "ISOLATED_DATASET_ID must be catalog-core-control-v1 or catalog-core-10k-v1."
    );
  }
  return datasetId;
}

function requireAcknowledgement() {
  if ((__ENV.ISOLATED_LOAD_ACKNOWLEDGEMENT || "") !== REQUIRED_ACKNOWLEDGEMENT) {
    throw new Error("ISOLATED_LOAD_ACKNOWLEDGEMENT must be YES before load generation.");
  }
}

export function optionsForIsolatedCapacity() {
  localBaseUrl();
  configuredDatasetId();
  requireAcknowledgement();
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
        tags: {
          cohort: "isolated-capacity-api-products",
          phase: "warmup",
          dataset_id: configuredDatasetId(),
        },
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
        tags: {
          cohort: "isolated-capacity-api-products",
          phase: "measurement",
          dataset_id: configuredDatasetId(),
        },
      },
    },
    thresholds: {
      isolated_capacity_warmup_expected_status_error_rate: ["rate==0"],
      isolated_capacity_expected_status_error_rate: ["rate==0"],
      dropped_iterations: ["count==0"],
    },
    discardResponseBodies: true,
    summaryTrendStats: ["min", "med", "p(95)", "p(99)", "max"],
  };
}

export function request(measurement) {
  const requestStartedAt = Date.now();
  const response = http.get(`${localBaseUrl()}/api/products`, {
    redirects: 0,
    responseType: "none",
    tags: {
      cohort: "isolated-capacity-api-products",
      name: "isolated-capacity-api-products",
      dataset_id: configuredDatasetId(),
    },
  });
  const requestCompletedAt = Date.now();
  const expected = check(response, {
    "expected status": (result) => result.status === 200,
  });

  if (!measurement) {
    warmupExpectedStatusErrorRate.add(!expected);
    if (!expected) {
      exec.test.abort("Isolated warm-up received a non-200 response.");
    }
    return;
  }

  measurementIterations.add(1);
  measurementClock.add(requestStartedAt);
  measurementClock.add(requestCompletedAt);
  expectedStatusErrorRate.add(!expected);
  measurementLatency.add(response.timings.duration);
}

export function handleSummaryForIsolatedCapacity(data) {
  const values = (metric) => data.metrics[metric]?.values || {};
  const iterations = values("isolated_capacity_measurement_iterations");
  const latency = values("isolated_capacity_measurement_latency");
  const clock = values("isolated_capacity_measurement_clock_ms");

  const summary = {
    datasetId: configuredDatasetId(),
    targetRps: configuredRate(),
    actualRps: (iterations.count || 0) / MEASUREMENT_SECONDS,
    measurementStartUtc: clock.min ? new Date(clock.min).toISOString() : null,
    measurementEndUtc: clock.max ? new Date(clock.max).toISOString() : null,
    droppedIterations: data.metrics.dropped_iterations
      ? data.metrics.dropped_iterations.values.count
      : 0,
    p50Ms: latency.med ?? null,
    p95Ms: latency["p(95)"] ?? null,
    p99Ms: latency["p(99)"] ?? null,
    maxMs: latency.max ?? null,
    expectedStatusErrorRate:
      values("isolated_capacity_expected_status_error_rate").rate ?? 0,
    allocatedVUs: data.metrics.vus_max
      ? data.metrics.vus_max.values.max
      : null,
    activeVUs: data.metrics.vus ? data.metrics.vus.values.max : null,
  };

  const output = `${JSON.stringify(summary)}\n`;
  const resultsDir = __ENV.RESULTS_DIR;
  if (!resultsDir) {
    return { stdout: output };
  }

  return {
    stdout: output,
    [`${resultsDir}/${configuredDatasetId()}-${configuredRate()}rps.json`]: output,
  };
}
