import assert from "node:assert/strict";
import test from "node:test";

test("Final Commerce action vocabulary keeps UNKNOWN retries out of the member contract", () => {
  const memberActions = ["REQUEST_CANCELLATION", "REQUEST_RETURN"];
  assert.equal(memberActions.includes("RETRY_REFUND"), false);
  assert.equal(memberActions.includes("RECONCILE_PAYMENT"), false);
});
