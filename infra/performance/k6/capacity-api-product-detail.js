import { handleSummaryForCapacity, optionsForCapacity, request, selectPublicProductId } from "./lib/capacity.js";
export const options = optionsForCapacity("capacity-api-product-detail");
export function setup() { return selectPublicProductId(); }
export function warmup(productId) { request("capacity-api-product-detail", `/api/products/${encodeURIComponent(productId)}`, false, productId); }
export function measure(productId) { request("capacity-api-product-detail", `/api/products/${encodeURIComponent(productId)}`, true, productId); }
export function handleSummary(data) { return handleSummaryForCapacity("capacity-api-product-detail", data); }
