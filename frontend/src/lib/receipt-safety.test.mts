import assert from "node:assert/strict";
import test from "node:test";
import { createReceiptSafety } from "./receipt-safety.ts";
import { createOperationExecutor } from "./admin-operation-executor.ts";
import { ApiError } from "./http/api-error.ts";
import { CsrfRefreshError } from "./csrf-lifecycle.ts";

test("lost receipt response never infers success or sends the POST again", async () => {
  const safety = createReceiptSafety();
  let posts = 0;
  let committed = false;
  const post = async () => { posts++; committed = true; throw new TypeError("response lost"); };
  assert.equal(await safety.run(101, post), "unknown");
  assert.equal(committed, true);
  assert.equal(safety.isUncertain(101), true);
  assert.equal(await safety.run(101, post), "unknown");
  assert.equal(posts, 1);
});

test("5xx and timeout are uncertain; confirmed rejection can be explicitly retried", async () => {
  for (const status of [500, 503, 408]) {
    const safety = createReceiptSafety();
    assert.equal(await safety.run(1, async () => { throw new ApiError(status, { code: "ERROR", message: "failed" }); }), "unknown");
  }
  const safety = createReceiptSafety();
  const execute = createOperationExecutor();
  await assert.rejects(execute(async () => { await safety.run(2, async () => { throw new ApiError(403, { code: "CSRF_INVALID", message: "rejected" }); }); }));
  assert.equal(execute.isBusy(), false);
  assert.equal(safety.isUncertain(2), false);
  assert.equal(await safety.run(2, async () => {}), "success");
});

test("CSRF acquisition failure occurs before POST and does not imply a committed receipt", async () => {
  const safety = createReceiptSafety();
  await assert.rejects(safety.run(3, async () => { throw new CsrfRefreshError(); }));
  assert.equal(safety.isUncertain(3), false);
});

test("the synchronous busy boundary blocks cancel/Escape/overlay dismissal until settled", async () => {
  const execute = createOperationExecutor();
  let release!: () => void;
  let closes = 0;
  const close = () => { if (!execute.isBusy()) closes++; };
  const running = execute(() => new Promise<void>(resolve => { release = resolve; }));
  assert.equal(execute.isBusy(), true);
  close(); close(); close();
  assert.equal(closes, 0);
  assert.equal(await execute(null), false);
  release();
  await running;
  close();
  assert.equal(closes, 1);
});
