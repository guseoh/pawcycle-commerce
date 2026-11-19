#!/usr/bin/env python3
"""PERF-SCALE-002 G-2: one mixed-load measurement on a disposable scale environment.

  python infra/performance/scale/run-mixed.py --scale 10k --seed 42 [--rps 20] [--warmup 120]
                                              [--steady 600] [--out DIR] [--run ID] [--keep]

Steps: run-scale.py --keep (MySQL + Flyway + perfgen + backend) -> fresh cohort password ->
log in one member per k6 VU -> k6 warm-up run -> reset DB digests, take baselines -> k6 steady
run while sampling /actuator/prometheus -> DB digest top 10, row-lock delta -> perfgen check
--no-manifest -> remove the environment (unless --keep and everything passed).

The report (stdout and --out/<run>.json) holds counts, timings, metric deltas and normalized SQL
digests only. The cohort password stays in this process and the k6 environment. The session
cookies go to k6 through a temp file in the system temp directory (never the repository), which
is deleted when the run ends; they are valid only against the disposable backend.

Exit codes: 0 every gate passed, 3 measured but a gate failed (k6 contract, invariants), 2 stop.
"""
import argparse
import datetime as dt
import http.cookiejar
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.request import HTTPCookieProcessor, Request, build_opener

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
K6_SCRIPT = ROOT / 'infra/performance/k6/scale-mixed.js'
VUS = {'browse': 60, 'member': 30, 'write': 40, 'login': 20}   # upper bound per bundle; k6 reads SCALE_VUS
COHORT = 1000
SAMPLE_SECONDS = 10

spec = importlib.util.spec_from_file_location('run_scale', HERE / 'run-scale.py')
run_scale = importlib.util.module_from_spec(spec)
spec.loader.exec_module(run_scale)
perfgen = run_scale.perfgen
Stop = run_scale.Stop

# Prometheus series summarized per measurement. Label filters keep the report bounded.
GAUGES = {
    'hikari_active_max': ('hikaricp_connections_active', {}),
    'hikari_pending_max': ('hikaricp_connections_pending', {}),
    'heap_used_max_bytes': ('jvm_memory_used_bytes', {'area': 'heap'}),
    'process_cpu_max': ('process_cpu_usage', {}),
}
COUNTERS = {
    'hikari_timeouts': ('hikaricp_connections_timeout_total', {}),
    'gc_pause_count': ('jvm_gc_pause_seconds_count', {}),
    'gc_pause_seconds': ('jvm_gc_pause_seconds_sum', {}),
    'http_requests': ('http_server_requests_seconds_count', {}),
}
SAMPLE = re.compile(r'^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(.*)\})?\s+(\S+)')
LABEL = re.compile(r'(\w+)="((?:[^"\\]|\\.)*)"')


# ---------- Prometheus text parsing (pure; unit-tested) ----------
def parse_prometheus(text):
    """[(name, labels, value)] for every sample line; comments and NaN samples are skipped."""
    samples = []
    for line in text.splitlines():
        if not line or line.startswith('#'):
            continue
        match = SAMPLE.match(line)
        if not match:
            continue
        try:
            value = float(match.group(3))
        except ValueError:
            continue
        if value != value:   # NaN
            continue
        samples.append((match.group(1), dict(LABEL.findall(match.group(2) or '')), value))
    return samples


def total(samples, name, labels=None):
    """Sum of every series of `name` whose labels include `labels`."""
    labels = labels or {}
    return sum(v for n, ls, v in samples if n == name and all(ls.get(k) == want for k, want in labels.items()))


def by_label(samples, name, *keys):
    """{(label values...): value} for `name`, summed over the other labels."""
    out = {}
    for n, ls, v in samples:
        if n == name:
            key = tuple(ls.get(k, '') for k in keys)
            out[key] = out.get(key, 0) + v
    return out


def server_summary(before, after, sampled):
    """Deltas over the steady window and maxima of the sampled gauges."""
    summary = {key: round(total(after, n, ls) - total(before, n, ls), 4) for key, (n, ls) in COUNTERS.items()}
    for key, (name, labels) in GAUGES.items():
        values = [total(s, name, labels) for s in sampled] or [0]
        summary[key] = round(max(values), 4)
    status_before = by_label(before, 'http_server_requests_seconds_count', 'status')
    status_after = by_label(after, 'http_server_requests_seconds_count', 'status')
    summary['http_5xx'] = round(sum(v - status_before.get(k, 0) for k, v in status_after.items()
                                    if k[0].startswith('5')), 4)
    jobs_before = by_label(before, 'pawcycle_job_executions_total', 'job_name', 'outcome')
    jobs_after = by_label(after, 'pawcycle_job_executions_total', 'job_name', 'outcome')
    summary['jobs'] = {f'{job}:{outcome}': int(v - jobs_before.get((job, outcome), 0))
                       for (job, outcome), v in sorted(jobs_after.items()) if v - jobs_before.get((job, outcome), 0)}
    return summary


def gate_failures(report):
    failed = []
    if report['k6']['steady']['k6_exit'] != 0:
        failed.append('k6_exit')   # interrupted or aborted k6 can still write a passing-looking summary
    if not report['k6']['steady']['verdict']['passed']:
        failed.append('k6_contract')
    if report['k6']['steady']['accounting']['droppedIterations']:
        failed.append('dropped_iterations')
    if report['k6']['steady']['interrupted_iterations']:
        failed.append('interrupted_iterations')
    if report['invariants'] != 'PASS':
        failed.append('invariants')
    return failed


# ---------- environment helpers ----------
def http_get(url, timeout=10):
    with build_opener().open(url, timeout=timeout) as response:
        return response.read().decode('utf-8')


def scrape(base):
    return parse_prometheus(http_get(f'{base}/actuator/prometheus'))


def session_for(base, member_id, password):
    jar = http.cookiejar.CookieJar()
    client = build_opener(HTTPCookieProcessor(jar))

    def call(path, body=None, headers=None):
        req = Request(base + path, None if body is None else json.dumps(body).encode(), headers or {})
        with client.open(req, timeout=15) as response:
            return json.load(response)

    csrf = call('/api/auth/csrf')['token']
    call('/api/auth/login', {'email': f'syn-member-{member_id}@perf.invalid', 'password': password},
         {'X-CSRF-TOKEN': csrf, 'Content-Type': 'application/json'})
    session = next((c.value for c in jar if c.name == 'JSESSIONID'), None)
    if not session:
        raise Stop(f'login for session member {member_id} gave no session')
    return {'memberId': member_id, 'sessionId': session, 'csrf': call('/api/auth/csrf')['token']}


def k6(env, phase, seconds, summary_path):
    run_env = dict(env, SCALE_PHASE=phase, SCALE_DURATION_SECONDS=str(seconds), SCALE_SUMMARY=str(summary_path))
    result = subprocess.run(['k6', 'run', '--quiet', '--no-color', str(K6_SCRIPT)], env=run_env,
                            capture_output=True, text=True, encoding='utf-8')
    if not summary_path.exists():
        tail = (result.stderr or '').strip().splitlines()[-3:]
        raise Stop(f'k6 {phase} produced no summary (exit {result.returncode}): {" | ".join(tail)}')
    summary = json.loads(summary_path.read_text(encoding='utf-8'))
    summary['k6_exit'] = result.returncode
    return summary


DIGEST_SQL = (
    "SELECT REPLACE(REPLACE(LEFT(DIGEST_TEXT, 220), '\\t', ' '), '\\n', ' '), COUNT_STAR, "
    "ROUND(SUM_TIMER_WAIT / 1e9, 1), ROUND(AVG_TIMER_WAIT / 1e9, 3), SUM_ROWS_EXAMINED, SUM_ROWS_SENT "
    "FROM performance_schema.events_statements_summary_by_digest "
    f"WHERE SCHEMA_NAME = '{perfgen.DATABASE}' AND DIGEST_TEXT IS NOT NULL "
    "ORDER BY SUM_TIMER_WAIT DESC LIMIT 10")
ROW_LOCK_SQL = "SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_row_lock_waits', 'Innodb_row_lock_time')"


def digests(container):
    keys = ['digest', 'count', 'total_ms', 'avg_ms', 'rows_examined', 'rows_sent']
    rows = perfgen.rows(perfgen.mysql(container, DIGEST_SQL))
    return [dict(zip(keys, [r[0]] + [float(x) for x in r[1:]])) for r in rows]


def row_locks(container):
    return {k: int(v) for k, v in perfgen.rows(perfgen.mysql(container, ROW_LOCK_SQL))}


class Sampler(threading.Thread):
    """Scrapes /actuator/prometheus every SAMPLE_SECONDS until stopped."""

    def __init__(self, base):
        super().__init__(daemon=True)
        self.base, self.samples, self.failures, self.done = base, [], 0, threading.Event()

    def run(self):
        while not self.done.wait(SAMPLE_SECONDS):
            try:
                self.samples.append(scrape(self.base))
            except (OSError, URLError, HTTPError):
                self.failures += 1


# ---------- main flow ----------
def run_mixed(args):
    run = args.run or f'mix-{args.scale}-{dt.datetime.now():%m%d%H%M}'
    project, container = run_scale.names(run)
    base = f'http://127.0.0.1:{args.port}'
    out = Path(args.out) if args.out else None
    work = (out or HERE) / f'.{run}-k6'
    report = {'run': run, 'scale': args.scale, 'seed': args.seed, 'rps_iterations': args.rps,
              'warmup_seconds': args.warmup, 'steady_seconds': args.steady, 'vus': VUS,
              'started_at': dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds')}
    if subprocess.run(['docker', 'inspect', container], capture_output=True).returncode == 0:
        # Never touch an environment this run did not create (for example one kept by --keep).
        raise Stop(f'{container} already exists; remove it with run-scale.py --down {run} first')
    created = False
    sessions_file = None
    try:
        up = subprocess.run([sys.executable, str(HERE / 'run-scale.py'), '--scale', args.scale, '--seed', str(args.seed),
                             '--run', run, '--port', str(args.port), '--cohort', str(COHORT), '--keep'],
                            cwd=ROOT, capture_output=True, text=True, encoding='utf-8')
        if up.returncode:
            # run-scale removes what it created when it fails, and refuses an existing environment.
            raise Stop(f'run-scale failed (exit {up.returncode}): {(up.stderr or up.stdout).strip()[-300:]}')
        created = True   # only now does this run own a kept environment
        env_summary = json.loads(up.stdout)
        report['environment'] = {k: env_summary[k] for k in ('backend', 'mysql', 'scheduler_flags', 'flyway_version',
                                                               'database_bytes', 'seconds')}
        report['environment']['generate_seconds'] = env_summary['generate']['seconds']
        report['environment']['source_sha'] = env_summary['generate']['source_sha']

        password = secrets.token_urlsafe(18)
        run_scale.cohort(container, COHORT, password)
        begin = time.monotonic()
        sessions = [session_for(base, i, password) for i in range(1, sum(VUS.values()) + 1)]
        report['session_setup_seconds'] = round(time.monotonic() - begin, 1)

        work.mkdir(parents=True, exist_ok=True)
        handle, sessions_file = tempfile.mkstemp(prefix='pawcycle-scale-sessions-', suffix='.json')
        with os.fdopen(handle, 'w', encoding='utf-8') as file:
            json.dump(sessions, file)
        env = dict(os.environ, SCALE_BASE_URL=base, SCALE_RPS=str(args.rps), SCALE_COHORT=str(COHORT),
                   SCALE_COHORT_PASSWORD=password, SCALE_SESSIONS_FILE=sessions_file, SCALE_VUS=json.dumps(VUS))
        report['k6'] = {'warmup': k6(env, 'warmup', args.warmup, work / 'warmup.json')}

        # Measurement window starts here: digests and counters count only the steady run.
        perfgen.mysql(container, 'TRUNCATE TABLE performance_schema.events_statements_summary_by_digest')
        locks_before = row_locks(container)
        before = scrape(base)
        sampler = Sampler(base)
        sampler.start()
        report['k6']['steady'] = k6(env, 'steady', args.steady, work / 'steady.json')
        sampler.done.set()
        sampler.join()
        after = scrape(base)
        locks_after = row_locks(container)

        report['server'] = server_summary(before, after, sampler.samples + [after])
        report['server']['samples'] = len(sampler.samples)
        report['server']['sample_failures'] = sampler.failures
        report['db'] = {'row_lock_waits': locks_after['Innodb_row_lock_waits'] - locks_before['Innodb_row_lock_waits'],
                        'row_lock_time_ms': locks_after['Innodb_row_lock_time'] - locks_before['Innodb_row_lock_time'],
                        'top_digests': digests(container)}
        report['orders_created'] = dict(perfgen.rows(perfgen.mysql(
            container, "SELECT status, COUNT(*) FROM orders WHERE order_number NOT LIKE 'SYN-%' GROUP BY status")))
        check = subprocess.run([sys.executable, str(ROOT / 'infra/performance/synthetic-data/perfgen.py'), 'check',
                                '--name', container, '--no-manifest'], capture_output=True, text=True, encoding='utf-8')
        verdict = json.loads(check.stdout.strip().splitlines()[-1])
        report['invariants'] = verdict['verdict']
        report['invariant_failures'] = verdict['failed']
        failed = gate_failures(report)
        if failed:
            report['gate_failed'] = failed
    except BaseException as error:
        report['error'] = str(error) if isinstance(error, Stop) else type(error).__name__
        raise
    finally:
        if sessions_file:
            Path(sessions_file).unlink(missing_ok=True)
        for name in ('warmup.json', 'steady.json'):
            (work / name).unlink(missing_ok=True)
        if work.exists():
            work.rmdir()
        if created:
            if args.keep and 'error' not in report and 'gate_failed' not in report:
                report['kept'] = True
            else:
                report['cleanup'] = run_scale.down(run_scale.environment(run, args.port, {}), project, container)
        report['finished_at'] = dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds')
        text = json.dumps(report, ensure_ascii=False, indent=2)
        print(text)
        if out:
            out.mkdir(parents=True, exist_ok=True)
            (out / f'{run}.json').write_text(text + '\n', encoding='utf-8')
    if 'gate_failed' in report:
        raise SystemExit(3)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--scale', choices=sorted(perfgen.SCALES), required=True)
    parser.add_argument('--seed', type=int, default=42)
    parser.add_argument('--rps', type=int, default=20, help='iterations per second over all bundles')
    parser.add_argument('--warmup', type=int, default=120)
    parser.add_argument('--steady', type=int, default=600)
    parser.add_argument('--run')
    parser.add_argument('--port', type=int, default=18090)
    parser.add_argument('--out')
    parser.add_argument('--keep', action='store_true', help='leave a passing environment running')
    args = parser.parse_args(argv)
    try:
        run_mixed(args)
    except Stop as error:
        print(json.dumps({'stop': str(error)}), file=sys.stderr)
        raise SystemExit(2)


if __name__ == '__main__':
    main()
