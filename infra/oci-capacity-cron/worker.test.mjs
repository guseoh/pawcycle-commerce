import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import worker, { tick as rawTick } from './worker.mjs';

const now = Date.parse('2026-11-04T06:00:00Z');
const env = { ENABLED: 'true', GITHUB_DISPATCH_TOKEN: 'test-secret-never-log' };
const event = { scheduledTime: now };
const tick = (event, env, request, log) => rawTick(event, env, request, log, () => now);
const run = (minutes, extra = {}) => ({
  id: 42, head_branch: 'main', event: 'workflow_dispatch', display_title: 'OCI A1 capacity check',
  created_at: new Date(now - minutes * 60000).toISOString(),
  status: 'completed', conclusion: 'success', ...extra,
});
function fake(runs, postStatus = 204, getStatus = 200) {
  const calls = [], logs = [];
  return { calls, logs, log: value => logs.push(JSON.parse(value)), request: async (url, options) => {
    calls.push({ url, ...options });
    return options.method === 'POST'
      ? new Response(null, { status: postStatus })
      : Response.json({ workflow_runs: runs }, { status: getStatus });
  } };
}
test('disabled by default, no HTTP handler or external calls', async () => {
  const f = fake([]);
  await tick(event, {}, f.request, f.log);
  assert.equal(f.calls.length, 0);
  assert.equal(worker.fetch, undefined);
  const config = JSON.parse(readFileSync(new URL('./wrangler.jsonc', import.meta.url)));
  assert.equal(config.vars.ENABLED, 'false');
  assert.deepEqual(config.triggers.crons, ['*/5 * * * *']);
  assert.equal(config.workers_dev, false);
});
test('fixed repository, workflow, main and check; timeout and no redirects', async () => {
  const f = fake([run(6)]);
  await tick(event, env, f.request, f.log);
  assert.equal(f.calls.length, 2);
  assert.equal(f.calls[1].url, 'https://api.github.com/repos/guseoh/pawcycle-commerce/actions/workflows/oci-a1-capacity-watch.yml/dispatches');
  assert.deepEqual(JSON.parse(f.calls[1].body), { ref: 'main', inputs: { mode: 'check' } });
  assert.equal(f.calls[0].redirect, 'manual');
  assert.equal(f.calls[1].redirect, 'manual');
  assert.ok(f.calls[1].signal instanceof AbortSignal);
  assert.deepEqual(f.logs.map(x => x.status), ['dispatched', 'healthy']);
});
for (const status of ['queued', 'in_progress', 'waiting', 'pending', 'requested']) {
  test(`skip overlapping ${status} check`, async () => {
    const f = fake([run(1, { status, conclusion: null }), run(6)]);
    await tick(event, env, f.request, f.log);
    assert.equal(f.calls.length, 1);
    assert.equal(f.logs[0].reason, 'active_check');
  });
}
test('recent schedule suppresses dispatch during dual scheduler rollout', async () => {
  const f = fake([run(2, { event: 'schedule', display_title: 'legacy schedule' })]);
  await tick(event, env, f.request, f.log);
  assert.equal(f.calls.length, 1);
  assert.equal(f.logs[0].reason, 'recent_check');
});
test('GitHub dispatch creation lag does not skip the next five-minute Cron slot', async () => {
  // A run registered 30 seconds after the previous tick is 4m30s old on the next tick.
  const f = fake([run(4.5)]);
  await tick(event, env, f.request, f.log);
  assert.equal(f.calls.length, 2);
  assert.equal(f.logs[0].status, 'dispatched');
});

test('creation-lag allowance retains a four-minute minimum gap', async () => {
  for (const ageMinutes of [0.5, 2, 3.99]) {
    const f = fake([run(ageMinutes)]);
    await tick(event, env, f.request, f.log);
    assert.equal(f.calls.length, 1);
    assert.equal(f.logs[0].reason, 'recent_check');
  }
});

test('creation-lag allowance does not override active-check guard', async () => {
  const f = fake([run(4.5, { status: 'in_progress', conclusion: null }), run(8)]);
  await tick(event, env, f.request, f.log);
  assert.equal(f.calls.length, 1);
  assert.equal(f.logs[0].reason, 'active_check');
});

test('self_test, legacy manual and branch runs cannot mask stale checks', async () => {
  const f = fake([run(1, { display_title: 'OCI A1 capacity self_test' }),
    run(1, { display_title: 'OCI Tokyo A1 Capacity Watch' }), run(1, { head_branch: 'ops/test' })]);
  await assert.rejects(tick(event, env, f.request, f.log), /stale_check/);
  assert.equal(f.calls.length, 2); // Still attempts repair dispatch before reporting stale.
});
test('stale busy job reports failure without dispatching or cancellation', async () => {
  const f = fake([run(1, { status: 'in_progress', conclusion: null }), run(11)]);
  await assert.rejects(tick(event, env, f.request, f.log), /stale_check/);
  assert.equal(f.calls.length, 1);
});
test('ten minutes exactly is healthy; failed runs never renew health', async () => {
  const f = fake([run(10)]);
  await tick(event, env, f.request, f.log);
  const failed = fake([run(6, { conclusion: 'failure' })]);
  await assert.rejects(tick(event, env, failed.request, failed.log), /stale_check/);
});
for (const code of [301, 302, 303, 307, 308, 401, 403, 404, 422, 429, 500]) {
  test(`dispatch ${code} fails without response-body logs or retry`, async () => {
    const f = fake([run(6)], code);
    await assert.rejects(tick(event, env, f.request, f.log), new RegExp(`dispatch_http_${code}`));
    assert.equal(f.calls.length, 2);
    assert.equal(f.logs.at(-1).code, `dispatch_http_${code}`);
    assert.ok(!JSON.stringify(f.logs).includes(env.GITHUB_DISPATCH_TOKEN));
  });
}
for (const code of [301, 302, 303, 307, 308]) {
  test(`history redirect ${code} stops before dispatch without following Location`, async () => {
    const calls = [], logs = [];
    const request = async (url, options) => {
      calls.push({ url, ...options });
      return new Response(null, { status: code, headers: { Location: 'https://example.invalid/' } });
    };
    await assert.rejects(tick(event, env, request, value => logs.push(value)),
      new RegExp(`history_http_${code}`));
    assert.equal(calls.length, 1);
    assert.equal(calls[0].redirect, 'manual');
    assert.ok(!logs.join('').includes(env.GITHUB_DISPATCH_TOKEN));
  });
}

test('history failure is fail closed', async () => {
  const f = fake([], 204, 403);
  await assert.rejects(tick(event, env, f.request, f.log), /history_http_403/);
  assert.equal(f.calls.length, 1);
});
test('missing token and malformed history fail closed', async () => {
  const f = fake([]);
  await assert.rejects(tick(event, { ENABLED: 'true' }, f.request, f.log), /missing_token/);
  assert.equal(f.calls.length, 0);
  await assert.rejects(tick(event, env, async () => Response.json({}), f.log), /invalid_history/);
  const badTime = fake([run(1, { created_at: 'bad' })]);
  await assert.rejects(tick(event, env, badTime.request, badTime.log), /invalid_run_time/);
  assert.equal(badTime.calls.length, 1);
});
test('network errors redact secrets and do not retry ambiguous POSTs', async () => {
  const f = fake([run(6)]);
  const request = async (url, options) => {
    if (options.method === 'POST') throw new Error(env.GITHUB_DISPATCH_TOKEN);
    return f.request(url, options);
  };
  await assert.rejects(tick(event, env, request, f.log), /github_network_or_timeout/);
  assert.ok(!JSON.stringify(f.logs).includes(env.GITHUB_DISPATCH_TOKEN));
});

test('delayed Cron tick measures recent check against actual invocation time', async () => {
  const f = fake([run(6)]);
  await tick({ scheduledTime: now - 30 * 60000 }, env, f.request, f.log);
  assert.equal(f.calls.length, 2);
  assert.equal(f.logs[0].status, 'dispatched');
  assert.equal(f.logs[1].status, 'healthy');
});
test('delayed Cron tick detects stale actual check, not its former schedule slot', async () => {
  const f = fake([run(11)]);
  await assert.rejects(
    tick({ scheduledTime: now - 30 * 60000 }, env, f.request, f.log),
    /stale_check/,
  );
  assert.equal(f.calls.length, 2); // Still attempts dispatch before raising stale.
});
test('future GitHub timestamps beyond clock skew fail closed', async () => {
  const f = fake([run(-2)]);
  await assert.rejects(tick(event, env, f.request, f.log), /invalid_run_time/);
  assert.equal(f.calls.length, 1);
});
