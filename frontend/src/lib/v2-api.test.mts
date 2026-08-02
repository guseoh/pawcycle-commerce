import assert from "node:assert/strict";
import test from "node:test";
import { newIdempotencyKey } from "./v2-api.ts";

test("V2 mutating actions receive a non-empty idempotency key", () => {
  const key = newIdempotencyKey();
  assert.ok(key.length > 0);
  assert.match(key, /^[A-Za-z0-9._-]+$/);
});
