import { handleSummaryFor, optionsFor, request } from "./lib/baseline.js";

export const options = optionsFor("api-products");

export function warmup() {
  request("api-products", "/api/products", false);
}

export function measure() {
  request("api-products", "/api/products", true);
}

export function handleSummary(data) {
  return handleSummaryFor("api-products", data);
}
