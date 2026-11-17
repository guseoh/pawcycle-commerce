// Short self-test of lib/verdict.js against the loopback stub only.
// It is not a measurement cohort and is never used for capacity evidence.
import http from "k6/http";
import { check } from "k6";
import { Counter, Rate } from "k6/metrics";
import { localBaseUrl } from "../lib/baseline.js";
import { arrivalAccounting, thresholdVerdict, validProductList } from "../lib/verdict.js";

const RATE = 20;
const SECONDS = 5;
const completed = new Counter("selftest_completed_iterations");
const contractErrors = new Rate("selftest_response_contract_error_rate");

export const options = {
  scenarios: {
    selftest: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: `${SECONDS}s`,
      preAllocatedVUs: 20,
      maxVUs: 20,
      gracefulStop: "0s",
    },
  },
  thresholds: {
    selftest_response_contract_error_rate: ["rate==0"],
    dropped_iterations: ["count==0"],
  },
};

export default function () {
  const response = http.get(`${localBaseUrl()}/api/products`, { redirects: 0 });
  const ok = check(response, { "response contract": (result) => validProductList(result) });
  completed.add(1);
  contractErrors.add(!ok);
}

export function handleSummary(data) {
  const summary = {
    responseContractErrorRate: data.metrics.selftest_response_contract_error_rate
      ? data.metrics.selftest_response_contract_error_rate.values.rate
      : null,
    accounting: arrivalAccounting(
      data,
      RATE * SECONDS,
      data.metrics.selftest_completed_iterations ? data.metrics.selftest_completed_iterations.values.count : 0
    ),
    verdict: thresholdVerdict(data),
  };
  return { stdout: `${JSON.stringify(summary)}\n` };
}
