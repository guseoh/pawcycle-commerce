import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import worker, { tick } from './worker.mjs';

const now = Date.parse('2026-11-04T06:00:00Z');
const pingUrl = 'https://hc-ping.com/12345678-1234-4234-a234-1234567890ab';
const env = { ENABLED: 'true', GITHUB_DISPATCH_TOKEN: 'test-secret-never-log',
  HEALTHCHECKS_SCHEDULER_PING_URL: pingUrl };
const event = { scheduledTime: now };
const run = (minutes, extra = {}) => ({
  id: 42, head_branch: 'main', event: 'workflow_dispatch', display_title: 'OCI A1 capacity check',
  created_at: new Date(now - minutes * 60000).toISOString(),
  status: 'completed', conclusion: 'success', ...extra,
});
function fake(runs, { postStatus = 204, getStatus = 200, pingStatus = 200, pingBody = 'OK' } = {}) {
  const calls = [], logs = [];
  return {
    calls, logs, log: value => logs.push(JSON.parse(value)),
    request: async (url, options = {}) => {
      calls.push({ url, ...options });
      if (url.startsWith(pingUrl)) return new Response(pingBody, { status: pingStatus });
      return options.method === 'POST'
        ? new Response(null, { status: postStatus })
        : Response.json({ workflow_runs: runs }, { status: getStatus });
    },
  };
}
const invoke = (f, overrides = env, e = event) =>
  tick(e, overrides, f.request, f.log, () => now);

test('disabled by default, no external requests, HTTP handler or routes', async () => {
  const f = fake([]);
  await invoke(f, {});
  assert.equal(f.calls.length, 0);
  assert.equal(worker.fetch, undefined);
  const config = JSON.parse(readFileSync(new URL('./wrangler.jsonc', import.meta.url)));
  assert.equal(config.vars.ENABLED, 'false');
  assert.deepEqual(config.triggers.crons, ['*/5 * * * *']);
  assert.equal(config.workers_dev, false);
});
test('fixed repository/main/check; scheduler heartbeat has no authorization header', async () => {
  const f = fake([run(6)]);
  await invoke(f);
  assert.equal(f.calls.length, 3);
  assert.equal(f.calls[0].url, 'https://api.github.com/repos/guseoh/pawcycle-commerce/actions/workflows/oci-a1-capacity-watch.yml/runs?branch=main&per_page=100');
  assert.equal(f.calls[1].url, 'https://api.github.com/repos/guseoh/pawcycle-commerce/actions/workflows/oci-a1-capacity-watch.yml/dispatches');
  assert.deepEqual(JSON.parse(f.calls[1].body), { ref: 'main', inputs: { mode: 'check' } });
  assert.equal(f.calls[1].redirect, 'error');
  assert.ok(f.calls[1].signal instanceof AbortSignal);
  assert.equal(f.calls[2].url, pingUrl);
  assert.equal(f.calls[2].method, 'POST');
  assert.equal(f.calls[2].redirect, 'error');
  assert.equal(f.calls[2].headers.Authorization, undefined);
  assert.deepEqual(f.logs.map(x => x.status), ['dispatched', 'healthy']);
  assert.equal(f.logs[1].action, 'dispatched');
  assert.ok(!JSON.stringify(f.logs).includes(env.GITHUB_DISPATCH_TOKEN));
  assert.ok(!JSON.stringify(f.logs).includes(pingUrl));
});
for (const status of ['queued', 'in_progress', 'waiting', 'pending', 'requested']) {
  test(`skip active ${status} check and still record scheduler heartbeat`, async () => {
    const f = fake([run(1, { status, conclusion: null }), run(6)]);
    await invoke(f);
    assert.equal(f.calls.length, 2);
    assert.equal(f.calls[1].url, pingUrl);
    assert.equal(f.logs[0].reason, 'active_check');
    assert.equal(f.logs[1].action, 'skipped');
  });
}
test('recent GitHub schedule during dual-run suppresses dispatch, not scheduler heartbeat', async () => {
  const f = fake([run(2, { event: 'schedule', display_title: 'legacy schedule' })]);
  await invoke(f);
  assert.equal(f.calls.length, 2);
  assert.equal(f.logs[0].reason, 'recent_check');
  assert.equal(f.logs[1].status, 'healthy');
});
test('self_test, legacy manual and branch runs do not suppress a live check', async () => {
  const f = fake([run(1, { display_title: 'OCI A1 capacity self_test' }),
    run(1, { display_title: 'OCI Tokyo A1 Capacity Watch' }),
    run(1, { head_branch: 'ops/test' })]);
  await invoke(f);
  assert.equal(f.calls.length, 3);
  assert.equal(f.logs[0].status, 'dispatched');
});
test('no previous OCI success still dispatches; OCI success has independent heartbeat', async () => {
  const f = fake([]);
  await invoke(f);
  assert.equal(f.calls.length, 3);
  assert.equal(f.logs.at(-1).status, 'healthy');
});
test('real invocation time, not delayed scheduled slot, controls recent suppression', async () => {
  const f = fake([run(6)]);
  await invoke(f, env, { scheduledTime: now - 30 * 60000 });
  assert.equal(f.calls.length, 3);
  assert.equal(f.logs[0].status, 'dispatched');
});
test('future timestamps beyond one minute fail closed without dispatch', async () => {
  const f = fake([run(-2)]);
  await assert.rejects(invoke(f), /invalid_run_time/);
  assert.equal(f.calls.length, 2);
  assert.equal(f.calls[1].url, pingUrl + '/fail');
});
for (const code of [401, 403, 404, 422, 429, 500]) {
  test(`GitHub dispatch ${code}: independent failure signal and redacted logs`, async () => {
    const f = fake([run(6)], { postStatus: code });
    await assert.rejects(invoke(f), new RegExp(`dispatch_http_${code}`));
    assert.equal(f.calls.length, 3);
    assert.equal(f.calls[2].url, pingUrl + '/fail');
    assert.equal(f.calls[2].headers.Authorization, undefined);
    assert.equal(f.logs.at(-1).code, `dispatch_http_${code}`);
    assert.ok(!JSON.stringify(f.logs).includes(pingUrl));
  });
}
test('GitHub history failure signals /fail and never dispatches', async () => {
  const f = fake([], { getStatus: 403 });
  await assert.rejects(invoke(f), /history_http_403/);
  assert.equal(f.calls.length, 2);
  assert.equal(f.calls[1].url, pingUrl + '/fail');
});
test('missing heartbeat URL prevents live GitHub calls', async () => {
  const f = fake([]);
  await assert.rejects(invoke(f, { ENABLED: 'true', GITHUB_DISPATCH_TOKEN: env.GITHUB_DISPATCH_TOKEN }), /invalid_heartbeat_url/);
  assert.equal(f.calls.length, 0);
});
test('a malformed or redirected heartbeat target is rejected before GitHub calls', async () => {
  const f = fake([]);
  for (const url of ['http://hc-ping.com/12345678-1234-4234-a234-1234567890ab',
    pingUrl + '/fail', pingUrl + '?token=x', 'https://evil.example/path', pingUrl + '#fragment']) {
    await assert.rejects(invoke(f, { ...env, HEALTHCHECKS_SCHEDULER_PING_URL: url }), /invalid_heartbeat_url/);
  }
  assert.equal(f.calls.length, 0);
});
test('missing GitHub token emits failure heartbeat and never calls GitHub', async () => {
  const f = fake([]);
  await assert.rejects(invoke(f, { ENABLED: 'true', HEALTHCHECKS_SCHEDULER_PING_URL: pingUrl }), /missing_token/);
  assert.deepEqual(f.calls.map(c => c.url), [pingUrl + '/fail']);
});
test('malformed or invalid history fails closed with failure signal', async () => {
  const f = fake([]);
  await assert.rejects(tick(event, env, async (url, opts) => {
    if (url.startsWith(pingUrl)) return new Response('OK');
    return Response.json({});
  }, f.log, () => now), /invalid_history/);
  const f2 = fake([run(1, { created_at: 'not-a-date' })]);
  await assert.rejects(invoke(f2), /invalid_run_time/);
  assert.equal(f2.calls.at(-1).url, pingUrl + '/fail');
});
test('200 OK with not-found or rate-limit body is not a successful heartbeat', async () => {
  for (const pingBody of ['OK (not found)', 'OK (rate limited)']) {
    const f = fake([run(2)], { pingBody });
    await assert.rejects(invoke(f), /heartbeat_unavailable/);
    assert.equal(f.calls.length, 2);
    assert.equal(f.logs.at(-1).code, 'heartbeat_unavailable');
  }
});
test('heartbeat HTTP failure does not hide the fact that a dispatch was accepted', async () => {
  const f = fake([run(6)], { pingStatus: 500 });
  await assert.rejects(invoke(f), /heartbeat_unavailable/);
  assert.equal(f.calls[1].url.endsWith('/dispatches'), true);
  assert.equal(f.calls.length, 3);
});
test('network error after ambiguous dispatch is not retried or logged with credentials', async () => {
  const f = fake([run(6)]);
  let posts = 0;
  const request = async (url, options) => {
    if (url.endsWith('/dispatches')) { posts++; throw new Error(env.GITHUB_DISPATCH_TOKEN); }
    return f.request(url, options);
  };
  await assert.rejects(tick(event, env, request, f.log, () => now), /github_network_or_timeout/);
  assert.equal(posts, 1);
  assert.equal(f.calls.at(-1).url, pingUrl + '/fail');
  assert.ok(!JSON.stringify(f.logs).includes(env.GITHUB_DISPATCH_TOKEN));
  assert.ok(!JSON.stringify(f.logs).includes(pingUrl));
});
