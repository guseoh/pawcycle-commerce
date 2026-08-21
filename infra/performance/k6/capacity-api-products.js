import { handleSummaryForCapacity, optionsForCapacity, request } from "./lib/capacity.js";
export const options = optionsForCapacity("capacity-api-products");
export function warmup() { request("capacity-api-products", "/api/products", false); }
export function measure() { request("capacity-api-products", "/api/products", true); }
export function handleSummary(data) { return handleSummaryForCapacity("capacity-api-products", data); }
