import { detailRequest, handleSummaryFor, optionsFor, selectPublicProductId } from "./lib/baseline.js";

export const options = optionsFor("api-product-detail");

export function setup() {
  return selectPublicProductId();
}

export function warmup(productId) {
  detailRequest(productId, false);
}

export function measure(productId) {
  detailRequest(productId, true);
}

export function handleSummary(data) {
  return handleSummaryFor("api-product-detail", data);
}
