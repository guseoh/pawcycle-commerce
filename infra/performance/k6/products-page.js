import { handleSummaryFor, optionsFor, request } from "./lib/baseline.js";

export const options = optionsFor("products-page");

export function warmup() {
  request("products-page", "/products", false);
}

export function measure() {
  request("products-page", "/products", true);
}

export function handleSummary(data) {
  return handleSummaryFor("products-page", data);
}
