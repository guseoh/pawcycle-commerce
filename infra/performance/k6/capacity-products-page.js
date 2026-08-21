import { handleSummaryForCapacity, optionsForCapacity, request } from "./lib/capacity.js";
export const options = optionsForCapacity("capacity-products-page");
export function warmup() { request("capacity-products-page", "/products", false); }
export function measure() { request("capacity-products-page", "/products", true); }
export function handleSummary(data) { return handleSummaryForCapacity("capacity-products-page", data); }
