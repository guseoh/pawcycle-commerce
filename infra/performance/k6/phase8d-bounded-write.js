import { handleSummaryFor, optionsForBoundedWrite, setup, writeCycle } from "./lib/phase8d.js";
export const options = optionsForBoundedWrite();
export { setup };
export function boundedWrite(data) { writeCycle(data); }
export function handleSummary(data) { return handleSummaryFor("bounded-write", data); }
