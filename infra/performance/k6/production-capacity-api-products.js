import {
  handleSummaryForProductionCapacity,
  optionsForProductionCapacity,
  request,
} from "./lib/production-capacity.js";

export const options = optionsForProductionCapacity();

export function warmup() {
  request(false);
}

export function measure() {
  request(true);
}

export function handleSummary(data) {
  return handleSummaryForProductionCapacity(data);
}
