import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { fileURLToPath } from "node:url";

const frontendRoot = fileURLToPath(new URL("../../", import.meta.url));

test("frontend test command uses the recursive stable runner", () => {
  const packageJson = JSON.parse(readFileSync(`${frontendRoot}/package.json`, "utf8")) as { scripts: { test: string } };
  const runner = readFileSync(`${frontendRoot}/scripts/run-tests.mjs`, "utf8");
  assert.equal(packageJson.scripts.test, "node scripts/run-tests.mjs");
  assert.match(runner, /readdirSync/);
  assert.match(runner, /sort\(/);
  assert.match(runner, /\.test\.mts/);
});
