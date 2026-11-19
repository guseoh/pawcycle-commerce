#!/usr/bin/env python3
"""PERF-SCALE-002 G-1: one disposable scale environment from empty DB to a logged-in backend.

  python infra/performance/scale/run-scale.py --scale 10k --seed 42 [--due-count 0] [--cohort 1000]
                                              [--keep] [--port 18090] [--out DIR]

Steps: build backend image -> start MySQL -> backend first start (Flyway creates schema and
history) -> stop backend -> perfgen generate + check -> start backend -> login cohort (hash copy)
-> verify login -> down -v (unless --keep) -> verify nothing of the project is left.

Secrets: the MySQL root/app passwords and the cohort password are random per run, live only in
this process's environment and the containers' environment, and are never printed or written.
The summary JSON (stdout and --out) holds counts, timings and status codes only.

--keep leaves the environment running for a load test; the summary prints the project name and
backend URL, and `--down RUN` removes it later. The cohort password is not kept anywhere, so a
later load step that must log in sets a fresh one with `cohort()` before it starts.
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
import time
from urllib.error import HTTPError
from urllib.request import HTTPCookieProcessor, Request, build_opener

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
COMPOSE_FILE = HERE / 'compose.yaml'
JDK_IMAGE = 'eclipse-temurin:25.0.3_9-jdk-noble'  # same pinned base as backend.Dockerfile's build stage
RUN_PATTERN = re.compile(r'[a-z0-9][a-z0-9-]{0,30}')
BCRYPT = re.compile(r'\$2[aby]\$10\$[./0-9A-Za-z]{53}')

spec = importlib.util.spec_from_file_location('perfgen', ROOT / 'infra/performance/synthetic-data/perfgen.py')
perfgen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(perfgen)
Stop = perfgen.Stop


def names(run):
    if not RUN_PATTERN.fullmatch(run):
        raise Stop('run id must match [a-z0-9][a-z0-9-]{0,30}')
    return f'pawcycle-perf-{run}', f'pawcycle-perfgen-{run}'


def environment(run, port, flags, secret=None):
    project, container = names(run)
    env = dict(os.environ)
    env.update({
        'PAWCYCLE_SCALE_PROJECT': project,
        'PAWCYCLE_SCALE_DB_CONTAINER': container,
        'PAWCYCLE_SCALE_BACKEND_PORT': str(port),
        'PAWCYCLE_SCALE_DB_USER': 'pawcycle_perf',
        # Compose interpolation needs a value even for commands that never create MySQL.
        'PAWCYCLE_SCALE_DB_PASSWORD': (secret or {}).get('db', 'unused'),
        'PAWCYCLE_SCALE_DB_ROOT_PASSWORD': (secret or {}).get('root', 'unused'),
        'PAWCYCLE_SCALE_CHECKOUT_EXPIRATION': str(flags.get('checkout_expiration', False)).lower(),
        'PAWCYCLE_SCALE_SUBSCRIPTION_AUTOMATION': str(flags.get('subscription_automation', False)).lower(),
        'PAWCYCLE_SCALE_BILLING': str(flags.get('billing', False)).lower(),
    })
    return env


def compose(env, *args, check=True):
    result = subprocess.run(['docker', 'compose', '-f', str(COMPOSE_FILE), *args], env=env, cwd=ROOT,
                            capture_output=True)
    if check and result.returncode:
        tail = result.stderr.decode('utf-8', 'replace').strip().splitlines()[-3:]
        raise Stop(f'docker compose {args[0]} failed (exit {result.returncode}): {" | ".join(tail)}')
    return result.stdout.decode('utf-8', 'replace')


def perfgen_json(*args):
    result = subprocess.run([sys.executable, str(ROOT / 'infra/performance/synthetic-data/perfgen.py'), *args],
                            cwd=ROOT, capture_output=True)
    out = result.stdout.decode('utf-8', 'replace').strip().splitlines()
    if result.returncode:
        err = result.stderr.decode('utf-8', 'replace').strip().splitlines()[-1:] or ['']
        failed = [line for line in out if '"violations": 0' not in line]
        raise Stop(f'perfgen {args[0]} failed (exit {result.returncode}): {err[0]} {failed[-3:]}')
    return [json.loads(line) for line in out if line.startswith('{')]


def bcrypt_hash(password):
    """BCrypt (cost 10, same as the backend's default BCryptPasswordEncoder) computed with Spring
    Security's own BCrypt class taken from the backend jar, inside the pinned JDK image."""
    cid = subprocess.run(['docker', 'create', 'pawcycle-perf-scale-backend:local'], capture_output=True,
                         check=True).stdout.decode().strip()
    try:
        jar = subprocess.run(['docker', 'cp', f'{cid}:/app/app.jar', '-'], capture_output=True, check=True).stdout
    finally:
        subprocess.run(['docker', 'rm', '-f', cid], capture_output=True)
    script = (
        'set -e; cd /tmp; tar -xf - app.jar; '
        'jar xf app.jar $(jar tf app.jar | grep "BOOT-INF/lib/spring-security-crypto-.*\\.jar$"); '
        'cat > Hash.java <<EOF\n'
        'public class Hash { public static void main(String[] a) {\n'
        '  System.out.println(org.springframework.security.crypto.bcrypt.BCrypt.hashpw(\n'
        '    System.getenv("PERF_COHORT_PASSWORD"), org.springframework.security.crypto.bcrypt.BCrypt.gensalt(10))); } }\n'
        'EOF\n'
        'java -cp "$(ls BOOT-INF/lib/spring-security-crypto-*.jar)" Hash.java')
    env = dict(os.environ, PERF_COHORT_PASSWORD=password)
    result = subprocess.run(['docker', 'run', '--rm', '-i', '--network', 'none', '-e', 'PERF_COHORT_PASSWORD',
                             JDK_IMAGE, 'sh', '-c', script], input=jar, env=env, capture_output=True)
    value = result.stdout.decode().strip().splitlines()[-1:] or ['']
    if result.returncode or not BCRYPT.fullmatch(value[0]):
        raise Stop(f'cohort hash failed (exit {result.returncode})')
    return value[0]


def cohort(container, size, password):
    """Give synthetic members 1..size the hash of `password`; they keep their generated history."""
    perfgen.guard(container)
    if size <= 0:
        return 0
    hashed = bcrypt_hash(password)
    perfgen.mysql(container, f"UPDATE members SET password_hash = '{hashed}' "
                             f"WHERE id BETWEEN 1 AND {int(size)} AND email LIKE 'syn-member-%@perf.invalid';")
    return int(perfgen.mysql(container, f"SELECT COUNT(*) FROM members WHERE password_hash = '{hashed}'"))


def login(base, email, password):
    jar = http.cookiejar.CookieJar()
    client = build_opener(HTTPCookieProcessor(jar))

    def call(path, body=None, headers=None):
        req = Request(base + path, None if body is None else json.dumps(body).encode(), headers or {})
        try:
            with client.open(req, timeout=15) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            return error.code, None

    status, csrf = call('/api/auth/csrf')
    if status != 200:
        return {'csrf': status}
    login_status, _ = call('/api/auth/login', {'email': email, 'password': password},
                           {'X-CSRF-TOKEN': csrf['token'], 'Content-Type': 'application/json'})
    me_status, _ = call('/api/auth/me')
    return {'login': login_status, 'me': me_status}


def wait_health(base, timeout=300):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with build_opener().open(base + '/actuator/health', timeout=5) as response:
                if json.load(response).get('status') == 'UP':
                    return
        except (OSError, ValueError):
            pass
        time.sleep(3)
    raise Stop('backend did not become healthy')


def leftovers(project, volumes):
    containers = subprocess.run(['docker', 'ps', '-aq', '--filter', f'label=com.docker.compose.project={project}'],
                                capture_output=True).stdout.decode().split()
    networks = subprocess.run(['docker', 'network', 'ls', '-q', '--filter', f'label=com.docker.compose.project={project}'],
                              capture_output=True).stdout.decode().split()
    alive = [v for v in volumes if subprocess.run(['docker', 'volume', 'inspect', v], capture_output=True).returncode == 0]
    return {'containers': len(containers), 'networks': len(networks), 'volumes': len(alive)}


def down(env, project, container):
    volumes = []
    if subprocess.run(['docker', 'inspect', container], capture_output=True).returncode == 0:
        info = perfgen.guard(container)  # refuse to remove anything that is not a perfgen database
        volumes = [m['Name'] for m in info.get('Mounts', []) if m.get('Type') == 'volume']
    compose(env, 'down', '-v', '--remove-orphans')
    left = leftovers(project, volumes)
    if any(left.values()):
        raise Stop(f'cleanup incomplete: {left}')
    return left


def timed(timings, key, fn, *args):
    begin = time.monotonic()
    value = fn(*args)
    timings[key] = round(time.monotonic() - begin, 1)
    return value


def run_scale(args):
    run = args.run or f'{args.scale}-{dt.datetime.now():%m%d%H%M}'
    project, container = names(run)
    flags = {'checkout_expiration': False, 'subscription_automation': False, 'billing': False}
    secret = {'root': secrets.token_urlsafe(24), 'db': secrets.token_urlsafe(24)}
    env = environment(run, args.port, flags, secret)
    base = f'http://127.0.0.1:{args.port}'
    if subprocess.run(['docker', 'inspect', container], capture_output=True).returncode == 0:
        raise Stop(f'{container} already exists; use --down {run} first')
    summary = {'run': run, 'project': project, 'scale': args.scale, 'seed': args.seed, 'due_count': args.due_count,
               'backend': {'url': base, 'mem_limit': '640m', 'cpus': 0.75, 'profile': 'local-integration'},
               'mysql': {'image': perfgen.IMAGE, 'cpus': 2.0, 'mem_limit': '2g', 'binlog': False},
               'scheduler_flags': flags, 'started_at': dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds')}
    timings = summary['seconds'] = {}
    try:
        timed(timings, 'build', compose, env, 'build', 'backend')
        timed(timings, 'mysql_up', compose, env, 'up', '-d', '--wait', 'mysql')
        perfgen.guard(container)
        timed(timings, 'flyway_first_start', compose, env, 'up', '-d', '--wait', 'backend')
        compose(env, 'stop', 'backend')
        summary['flyway_version'] = int(perfgen.mysql(
            container, 'SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history'))
        generated = timed(timings, 'generate', perfgen_json, 'generate', '--name', container, '--scale', args.scale,
                          '--seed', str(args.seed), '--due-count', str(args.due_count))[-1]
        summary['generate'] = {k: generated[k] for k in
                               ('schema_source', 'migration_max', 'source_sha', 'seconds', 'step_seconds', 'rows')}
        verdict = timed(timings, 'check', perfgen_json, 'check', '--name', container)[-1]
        summary['check'] = {'checks': verdict['checks'], 'verdict': verdict['verdict']}
        password = secrets.token_urlsafe(18)
        summary['cohort'] = {'size': timed(timings, 'cohort', cohort, container, args.cohort, password)}
        timed(timings, 'backend_start', compose, env, 'up', '-d', '--wait', 'backend')
        wait_health(base)
        samples = [1, max(1, args.cohort // 2), args.cohort] if args.cohort else []
        summary['cohort']['login'] = {str(i): login(base, f'syn-member-{i}@perf.invalid', password) for i in samples}
        outsider = args.cohort + 1
        summary['cohort']['outside_cohort_login'] = login(base, f'syn-member-{outsider}@perf.invalid', password)['login']
        summary['database_bytes'] = int(perfgen.mysql(
            container, "SELECT SUM(data_length + index_length) FROM information_schema.tables "
                       f"WHERE table_schema = '{perfgen.DATABASE}'"))
        failed = verification_failures(summary)
        if failed:
            summary['verification_failed'] = failed
    except BaseException as error:
        summary['error'] = str(error) if isinstance(error, Stop) else type(error).__name__
        raise
    finally:
        # Keep only a verified environment; a failed one is removed like an interrupted one.
        if args.keep and 'error' not in summary and 'verification_failed' not in summary:
            summary['kept'] = True
        else:
            summary['cleanup'] = down(env, project, container)
        summary['finished_at'] = dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds')
        text = json.dumps(summary, ensure_ascii=False, indent=2)
        print(text)
        if args.out:
            out = Path(args.out)
            out.mkdir(parents=True, exist_ok=True)
            (out / f'{run}.json').write_text(text + '\n', encoding='utf-8')
    if 'verification_failed' in summary:
        raise SystemExit(3)


def verification_failures(summary):
    failed = []
    if summary['check']['verdict'] != 'PASS':
        failed.append('invariants')
    if any(r != {'login': 200, 'me': 200} for r in summary['cohort']['login'].values()):
        failed.append('cohort_login')
    if summary['cohort']['outside_cohort_login'] == 200:
        failed.append('outside_cohort_login')
    return failed


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--scale', choices=sorted(perfgen.SCALES))
    parser.add_argument('--seed', type=int, default=42)
    parser.add_argument('--due-count', type=int, default=0)
    parser.add_argument('--cohort', type=int, default=1000)
    parser.add_argument('--run', help='run id (default <scale>-<MMDDHHmm>)')
    parser.add_argument('--port', type=int, default=18090)
    parser.add_argument('--keep', action='store_true', help='leave the environment running')
    parser.add_argument('--out', help='directory for the summary JSON (no secrets)')
    parser.add_argument('--down', metavar='RUN', help='remove a kept environment and verify cleanup')
    args = parser.parse_args(argv)
    try:
        if args.down:
            project, container = names(args.down)
            print(json.dumps({'run': args.down, 'cleanup': down(environment(args.down, args.port, {}), project, container)}))
        elif args.scale:
            run_scale(args)
        else:
            parser.error('--scale or --down is required')
    except Stop as error:
        print(json.dumps({'stop': str(error)}), file=sys.stderr)
        raise SystemExit(2)


if __name__ == '__main__':
    main()
