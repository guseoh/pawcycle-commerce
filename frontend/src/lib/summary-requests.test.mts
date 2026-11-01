import assert from "node:assert/strict";
import test from "node:test";
import { runSummaryRequests } from "./summary-requests.ts";

const authExpired = new Error("auth expired");
type Summary = { orders: string[]; subscriptions: string[]; cartQuantity: number; unreadNotifications: number };
const keys: (keyof Summary)[] = ["orders", "subscriptions", "cartQuantity", "unreadNotifications"];
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function harness() {
  let snapshot: Partial<Summary> = {};
  const errors = new Set<keyof Summary>();
  let expired = false;
  const handlers = {
    value: <K extends keyof Summary>(key: K, value: Summary[K]) => { snapshot[key] = value; errors.delete(key); },
    error: (key: keyof Summary) => { errors.add(key); },
    expired: () => { expired = true; snapshot = {}; errors.clear(); },
    settled: () => {},
    isAuthRequired: (reason: unknown) => reason === authExpired,
  };
  return { handlers, errors, snapshot: () => snapshot, expired: () => expired };
}
const successLoaders = () => ({ orders: async () => ["order"], subscriptions: async () => ["subscription"], cartQuantity: async () => 3, unreadNotifications: async () => 2 });

test("an order failure preserves subscription, cart and notification summaries", async () => {
  const state = harness();
  await runSummaryRequests<Summary>({ ...successLoaders(), orders: async () => { throw new Error("503"); } }, keys, state.handlers).done;
  assert.deepEqual(state.snapshot(), { subscriptions: ["subscription"], cartQuantity: 3, unreadNotifications: 2 });
  assert.deepEqual([...state.errors], ["orders"]);
});

test("retry requests only the failed summary and restores its value", async () => {
  const state = harness();
  await runSummaryRequests<Summary>({ ...successLoaders(), orders: async () => { throw new Error("503"); } }, keys, state.handlers).done;
  const called: string[] = [];
  const loaders = successLoaders();
  await runSummaryRequests<Summary>({ ...loaders, orders: async () => { called.push("orders"); return ["recovered"]; }, subscriptions: async () => { called.push("subscriptions"); return []; } }, ["orders"], state.handlers).done;
  assert.deepEqual(called, ["orders"]);
  assert.equal(state.errors.size, 0);
  assert.deepEqual(state.snapshot(), { orders: ["recovered"], subscriptions: ["subscription"], cartQuantity: 3, unreadNotifications: 2 });
});

test("auth expiry clears protected values and invalidates late results", async () => {
  const state = harness();
  const order = deferred<string[]>();
  const cart = deferred<number>();
  const pending = runSummaryRequests<Summary>({ ...successLoaders(), orders: () => order.promise, cartQuantity: () => cart.promise }, keys, state.handlers);
  await Promise.resolve();
  assert.deepEqual(state.snapshot().subscriptions, ["subscription"]);
  order.reject(authExpired);
  await Promise.resolve();
  cart.resolve(99);
  await pending.done;
  assert.equal(state.expired(), true);
  assert.deepEqual(state.snapshot(), {});
});

test("unmounted lifetime cannot contaminate the new member's summary", async () => {
  const oldOrder = deferred<string[]>();
  const oldState = harness();
  const old = runSummaryRequests<Summary>({ ...successLoaders(), orders: () => oldOrder.promise }, ["orders"], oldState.handlers);
  old.cancel();
  const newState = harness();
  await runSummaryRequests<Summary>(successLoaders(), keys, newState.handlers).done;
  oldOrder.resolve(["old private order"]);
  await old.done;
  assert.deepEqual(oldState.snapshot(), {});
  assert.deepEqual(newState.snapshot().orders, ["order"]);
});
