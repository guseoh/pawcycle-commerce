// Only the scheduler moves to Cloudflare; OCI credentials and notifications stay in Actions.
const WORKFLOW = 'https://api.github.com/repos/guseoh/pawcycle-commerce/actions/workflows/oci-a1-capacity-watch.yml';
const CHECK_NAME = 'OCI A1 capacity check';
const INTERVAL = 5 * 60 * 1000;
const STALE_AFTER = 10 * 60 * 1000;
const CLOCK_SKEW = 60 * 1000;

function isCheck(run) {
  return run.head_branch === 'main' && (run.event === 'schedule' ||
    (run.event === 'workflow_dispatch' && run.display_title === CHECK_NAME));
}

export async function tick(event, env, request = fetch, log = console.log, clock = Date.now) {
  const emit = (status, fields = {}) => log(JSON.stringify({
    component: 'oci-capacity-cron', scheduledTime: event.scheduledTime, status, ...fields,
  }));
  if (env.ENABLED !== 'true') {
    emit('disabled');
    return;
  }
  try {
    if (!env.GITHUB_DISPATCH_TOKEN) throw new Error('missing_token');
    const headers = {
      Authorization: `Bearer ${env.GITHUB_DISPATCH_TOKEN}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'pawcycle-oci-capacity-cron',
      'Content-Type': 'application/json',
    };
    const api = async (url, options = {}) => {
      try {
        return await request(url, { ...options, headers, redirect: 'error',
          signal: AbortSignal.timeout(15000) });
      } catch {
        // Never log exception messages, response bodies, request headers or secrets.
        throw new Error('github_network_or_timeout');
      }
    };
    const history = await api(`${WORKFLOW}/runs?branch=main&per_page=100`);
    if (history.status !== 200) throw new Error(`history_http_${history.status}`);
    let data;
    try { data = await history.json(); } catch { throw new Error('invalid_history'); }
    if (!Array.isArray(data.workflow_runs)) throw new Error('invalid_history');
    const runs = data.workflow_runs.filter(isCheck);
    // Cron execution can be delayed; use the actual invocation time, not its scheduled slot.
    const observedTime = clock();
    const timestamps = runs.map(run => Date.parse(run.created_at));
    if (timestamps.some(value => !Number.isFinite(value) || value > observedTime + CLOCK_SKEW)) {
      throw new Error('invalid_run_time');
    }
    const lastGood = runs.filter(run => run.status === 'completed' && run.conclusion === 'success')
      .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at))[0];
    const stale = !lastGood || observedTime - Date.parse(lastGood.created_at) > STALE_AFTER;
    // Covers queued/pending/waiting/requested runs too. Never cancel an active OCI check.
    const busy = runs.some(run => run.status !== 'completed');
    const recent = runs.some(run => observedTime - Date.parse(run.created_at) < INTERVAL);
    if (busy || recent) {
      emit('skipped', { reason: busy ? 'active_check' : 'recent_check' });
    } else {
      const dispatched = await api(`${WORKFLOW}/dispatches`, {
        method: 'POST', body: JSON.stringify({ ref: 'main', inputs: { mode: 'check' } }),
      });
      if (dispatched.status !== 204) throw new Error(`dispatch_http_${dispatched.status}`);
      emit('dispatched'); // Acceptance only; not evidence of an OCI query or delivery.
    }
    if (stale) throw new Error('stale_check');
    emit('healthy', { lastSuccessfulRunId: lastGood.id });
  } catch (error) {
    // Only errors constructed here reach logs; unexpected runtime errors are redacted.
    const code = /^(missing_token|github_network_or_timeout|invalid_history|invalid_run_time|stale_check|(?:history|dispatch)_http_\d{3})$/.test(error?.message)
      ? error.message : 'unexpected_error';
    emit('error', { code });
    throw new Error(code);
  }
}

export default {
  async scheduled(event, env) { await tick(event, env); },
};
