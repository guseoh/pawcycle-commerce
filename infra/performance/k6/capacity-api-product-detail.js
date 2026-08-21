import { handleSummaryForCapacity, optionsForCapacity, request, selectPublicProductId } from "./lib/capacity.js";
export const options = optionsForCapacity("capacity-api-product-detail");
export function setup() { return selectPublicProductId(); }
export function warmup(productId) { request("capacity-api-product-detail", `/api/products/${encodeURIComponent(productId)}`, false); }
export function measure(productId) { request("capacity-api-product-detail", `/api/products/${encodeURIComponent(productId)}`, true); }
export function handleSummary(data) { return handleSummaryForCapacity("capacity-api-product-detail", data); }
