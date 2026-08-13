import assert from "node:assert/strict";
import test from "node:test";
import { MEMBER_AVAILABLE_ACTIONS } from "./commerce-final-api.ts";

test("Final Commerce action vocabulary keeps admin-only recovery actions out of the member contract", () => {
  const memberActions: readonly string[] = MEMBER_AVAILABLE_ACTIONS;
  assert.deepEqual([...memberActions], ["REQUEST_CANCELLATION", "REQUEST_RETURN"]);
  assert.equal(memberActions.includes("RETRY_REFUND"), false);
  assert.equal(memberActions.includes("RECONCILE_PAYMENT"), false);
});
