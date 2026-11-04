// Only scheduling lives in Cloudflare: OCI credentials and alerts stay in GitHub Actions.
const WORKFLOW = 'https://api.github.com/repos/guseoh/pawcycle-commerce/actions/workflows/oci-a1-capacity-watch.yml';
const CHECK_NAME = 'OCI A1 capacity check';
const INTERVAL = 5 * 60 * 1000;
const CLOCK_SKEW = 60 * 1000;
const PING_RE = /^https:\/\/hc-ping\.com\/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;

function isCheck(run) {
  return run.head_branch === 'main' && (run.event === 'schedule' ||
    (run.event === 'workflow_dispatch' && run.display_title === CHECK_NAME));
}

function requiredPingUrl(raw) {
  if (typeof raw !== 'string' || !PING_RE.test(raw)) throw new Error('invalid_heartbeat_url');
  return raw;
}

async function sendHeartbeat(request, url, suffix = '') {
  try {
    // No GitHub Authorization header or other token crosses this boundary.
    const response = await request(url + suffix, {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(8000),
      headers: { 'User-Agent': 'pawcycle-oci-capacity-cron' },
    });
    // Healthchecks.io can also return HTTP 200 with a "not found" or "rate limited" body.
    if (response.status !== 200 || (await response.text()).trim() !== 'OK') {
      throw new Error('heartbeat_unavailable');
    }
  } catch {
    throw new Error('heartbeat_unavailable');
  }
}

function errorCode(error) {
  const code = error?.message;
  return /^(missing_token|invalid_heartbeat_url|heartbeat_unavailable|github_network_or_timeout|invalid_history|invalid_run_time|(?:history|dispatch)_http_\d{3})$/.test(code)
    ? code : 'unexpected_error';
}

export async function tick(event, env, request = fetch, log = console.log, clock = Date.now) {
  const emit = (status, fields = {}) => log(JSON.stringify({
    component: 'oci-capacity-cron', scheduledTime: event.scheduledTime,
    observedTime: new Date(clock()).toISOString(), status, ...fields,
  }));
  if (env.ENABLED !== 'true') {
    emit('disabled');
    return;
  }

  let heartbeatUrl;
  try {
    // Validate both operational secrets before making any GitHub request.
    heartbeatUrl = requiredPingUrl(env.HEALTHCHECKS_SCHEDULER_PING_URL);
    if (!env.GITHUB_DISPATCH_TOKEN) throw new Error('missing_token');
    const headers = {
      Authorization: `Bearer ${env.GITHUB_DISPATCH_TOKEN}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'pawcycle-oci-capacity-cron',
      'Content-Type': 'application/json',
    };
    const github = async (url, options = {}) => {
      try {
        return await request(url, { ...options, headers, redirect: 'error',
          signal: AbortSignal.timeout(15000) });
      } catch {
        throw new Error('github_network_or_timeout');
      }
    };

    const history = await github(`${WORKFLOW}/runs?branch=main&per_page=100`);
    if (history.status !== 200) throw new Error(`history_http_${history.status}`);
    let data;
    try { data = await history.json(); } catch { throw new Error('invalid_history'); }
    if (!Array.isArray(data.workflow_runs)) throw new Error('invalid_history');
    const runs = data.workflow_runs.filter(isCheck);
    const observedTime = clock();
    const created = runs.map(run => Date.parse(run.created_at));
    if (created.some(time => !Number.isFinite(time) || time > observedTime + CLOCK_SKEW)) {
      throw new Error('invalid_run_time');
    }

    // Use invocation time, not scheduledTime: delayed Cron delivery must not
    // suppress a check merely because its original scheduled slot is old.
    const busy = runs.some(run => run.status !== 'completed');
    const recent = created.some(time => observedTime - time < INTERVAL);
    let action = 'skipped';
    if (busy || recent) {
      emit('skipped', { reason: busy ? 'active_check' : 'recent_check' });
    } else {
      const dispatched = await github(`${WORKFLOW}/dispatches`, {
        method: 'POST', body: JSON.stringify({ ref: 'main', inputs: { mode: 'check' } }),
      });
      if (dispatched.status !== 204) throw new Error(`dispatch_http_${dispatched.status}`);
      action = 'dispatched';
      emit('dispatched'); // 204 = acceptance only, never OCI success.
    }

    // Scheduler is healthy after a valid dispatch/skip, NOT after OCI completes.
    // The independently configured OCI heartbeat can only be sent by the monitor job.
    await sendHeartbeat(request, heartbeatUrl);
    emit('healthy', { action });
  } catch (error) {
    const code = errorCode(error);
    if (heartbeatUrl && code !== 'heartbeat_unavailable') {
      try { await sendHeartbeat(request, heartbeatUrl, '/fail'); } catch { /* Dead-man timeout still applies. */ }
    }
    emit('error', { code });
    throw new Error(code); // Never include response bodies, URLs or credentials.
  }
}

export default {
  async scheduled(event, env) { await tick(event, env); },
};
