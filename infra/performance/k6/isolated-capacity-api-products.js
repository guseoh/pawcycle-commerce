import {
  handleSummaryForIsolatedCapacity,
  optionsForIsolatedCapacity,
  request,
} from "./lib/isolated-capacity.js";

export const options = optionsForIsolatedCapacity();

export function warmup() {
  request(false);
}

export function measure() {
  request(true);
}

export function handleSummary(data) {
  return handleSummaryForIsolatedCapacity(data);
}
