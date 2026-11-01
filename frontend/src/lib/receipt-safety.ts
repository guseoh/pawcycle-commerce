import { ApiError } from "./http/api-error.ts";
import { CsrfRefreshError } from "./csrf-lifecycle.ts";

/** A lost response cannot prove whether the receipt transaction committed. */
export function createReceiptSafety() {
  const uncertain = new Set<number>();
  return {
    isUncertain: (id: number) => uncertain.has(id),
    async run(id: number, request: () => Promise<void>): Promise<"success" | "unknown"> {
      if (uncertain.has(id)) return "unknown";
      try {
        await request();
        return "success";
      } catch (error) {
        // Token acquisition failed before POST, or the server explicitly rejected the command.
        if (error instanceof CsrfRefreshError || (error instanceof ApiError && error.status >= 400 && error.status < 500 && error.status !== 408)) throw error;
        uncertain.add(id);
        return "unknown";
      }
    },
  };
}
