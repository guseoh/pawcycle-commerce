import assert from "node:assert/strict";
import test from "node:test";
import { createOperationExecutor } from "./admin-operation-executor.ts";

test("cancel sends no request; duplicate submission is ignored until completion", async () => {
  const execute = createOperationExecutor();
  let requests = 0;
  let release!: () => void;
  const operation = async () => { requests++; await new Promise<void>(resolve => { release = resolve; }); };
  assert.equal(await execute(null), false);
  assert.equal(requests, 0);
  const first = execute(operation);
  assert.equal(await execute(operation), false);
  assert.equal(requests, 1);
  release();
  assert.equal(await first, true);
  assert.equal(await execute(async () => { requests++; }), true);
  assert.equal(requests, 2);
});

test("a failed operation releases the lock for an explicit retry", async () => {
  const execute = createOperationExecutor();
  await assert.rejects(execute(async () => { throw new Error("failed"); }));
  assert.equal(await execute(async () => {}), true);
});
