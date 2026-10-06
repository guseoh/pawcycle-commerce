"""Local-only PERF-COMMERCE-001 runner; credentials and identities stay in memory.

Run after starting the existing local-integration Compose services:
  python infra/performance/k6/run-checkout.py --stage before --attempt v120
The runner refuses to overwrite evidence. --cleanup RUN_MARKER recovers its own
fixture after an interruption; the non-secret marker is printed before seeding.
"""
import argparse
import datetime as dt
import http.cookiejar
import json
import math
import os
from pathlib import Path
import re
import statistics
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlencode
from urllib.request import Request, build_opener, HTTPCookieProcessor, urlopen
import uuid

ROOT = Path(__file__).resolve().parents[3]
LOCAL = ROOT / 'infra/local-integration'
EVIDENCE = ROOT / 'docs/reports/PERF-COMMERCE-001/evidence'
COMPOSE = ['docker', 'compose', '--env-file', str(LOCAL / '.env.local'), '-f', str(LOCAL / 'compose.yaml')]
FIELDS = ['count', 'timer_ps', 'lock_ps', 'examined', 'affected', 'sent', 'errors']
BASE_MAIN_SHA = 'a3787d8cd0437b40c4aa6817996d70d2168610b7'
POOL_SIZE = 120
BEFORE_CORRECTNESS_FILES = {
    'backend/src/main/java/com/pawcycle/backend/commerce/CheckoutIdempotencyRepository.java',
    'backend/src/main/java/com/pawcycle/backend/commerce/checkout/persistence/CheckoutPersistenceAdapter.java',
    'backend/src/test/java/com/pawcycle/backend/commerce/CheckoutIdempotencyIntegrationTests.java',
}


def command(args, input=None):
    result = subprocess.run(args, input=input, text=True, encoding='utf-8', capture_output=True, cwd=ROOT)
    if result.returncode:
        # Do not reproduce SQL, environment, raw rows or login response on failure.
        raise RuntimeError(f'Local command failed: {args[0]} (exit {result.returncode})')
    return result.stdout.strip()


def sql(query):
    return command(COMPOSE + ['exec', '-T', 'mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -B -uroot "$MYSQL_DATABASE"'], query)


def get_json(url):
    with urlopen(url, timeout=15) as response:
        return json.load(response)


def utc():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z')


def inspect(service):
    cid = command(COMPOSE + ['ps', '-q', service])
    if not cid:
        raise RuntimeError(f'Local {service} unavailable')
    item = json.loads(command(['docker', 'inspect', cid]))[0]
    if item['Config']['Labels'].get('com.docker.compose.project') != 'pawcycle-local-integration':
        raise RuntimeError('STOP: wrong Compose project')
    if service == 'backend':
        env = dict(v.split('=', 1) for v in item['Config']['Env'] if '=' in v)
        if env.get('SPRING_PROFILES_ACTIVE') != 'local-integration' or \
                not env.get('SPRING_DATASOURCE_URL', '').startswith('jdbc:mysql://mysql:3306/') or \
                env.get('PAWCYCLE_TOSS_TEST_ENABLED', 'false') != 'false' or \
                env.get('PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS', 'false') != 'false':
            raise RuntimeError('STOP: local runtime/Toss/reset gate failed')
        bindings = item['HostConfig']['PortBindings'].get('8080/tcp', [])
        if bindings != [{'HostIp': '127.0.0.1', 'HostPort': '8080'}]:
            raise RuntimeError('STOP: Backend must publish only the fixed loopback port')
    if service == 'mysql' and not any(m.get('Name') == 'pawcycle-local-integration-mysql-data'
                                     and m['Destination'] == '/var/lib/mysql' for m in item['Mounts']):
        raise RuntimeError('STOP: wrong local MySQL volume')
    return {'image': item['Image'], 'started': item['State']['StartedAt'],
            'restarts': item['RestartCount'], 'oom': item['State']['OOMKilled'],
            'running': item['State']['Running'], 'memory_bytes': item['HostConfig']['Memory'],
            'nano_cpus': item['HostConfig']['NanoCpus'], 'pids': item['HostConfig']['PidsLimit']}


def require_before_source_state():
    if command(['git', 'branch', '--show-current']) != 'codex/perf-commerce-001' or \
            command(['git', 'rev-parse', 'HEAD']) != BASE_MAIN_SHA or \
            command(['git', 'rev-parse', 'origin/main']) != BASE_MAIN_SHA:
        raise RuntimeError('STOP: Before requires the task branch at the fetched main baseline')
    changed = set(command(['git', 'diff', '--name-only', 'HEAD', '--', 'backend']).splitlines())
    untracked = command(['git', 'ls-files', '--others', '--exclude-standard', '--', 'backend'])
    if changed != BEFORE_CORRECTNESS_FILES or untracked:
        raise RuntimeError('STOP: Before allows only the approved idempotency correctness correction')


def seed(marker, email):
    if not re.fullmatch(r'qa-foundation-004@[a-zA-Z0-9.-]+', email):
        raise RuntimeError('Invalid QA bootstrap email contract')
    if sql(f"SELECT COUNT(*) FROM members WHERE email='{email}' AND role='USER';") != '1':
        raise RuntimeError('QA bootstrap member missing')
    if sql(f"SELECT COUNT(*) FROM members WHERE email LIKE '{marker}-%';") != '0':
        raise RuntimeError('STOP: fixture marker already exists')
    queries = ["START TRANSACTION;",
        f"INSERT INTO brands(name,slug,active,display_order) VALUES ('Checkout perf','{marker}',true,0); SET @b=LAST_INSERT_ID();",
        f"INSERT INTO categories(name,slug,active,display_order) VALUES ('Checkout perf','{marker}',true,0); SET @c=LAST_INSERT_ID();"]
    for n in range(1, POOL_SIZE + 1):
        queries += [
            f"INSERT INTO members(email,password_hash,role) SELECT '{marker}-{n}@local.invalid',password_hash,'USER' FROM members WHERE email='{email}'; SET @m=LAST_INSERT_ID();",
            "INSERT INTO member_addresses(member_id,name,recipient_name,recipient_phone,postal_code,address_line1,created_at,updated_at) VALUES (@m,'Checkout perf','Synthetic','00000000000','00000','Synthetic local fixture',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));",
            f"INSERT INTO products(brand_id,category_id,catalog_key,name,short_description,description,pet_type,display_status) VALUES (@b,@c,'{marker}-{n}','Checkout perf','Synthetic','Local performance fixture','DOG','PUBLIC'); SET @p=LAST_INSERT_ID();",
            f"INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES (@p,'{marker}-{n}','Checkout perf',19900,false,0,'ACTIVE'); SET @s=LAST_INSERT_ID();",
            "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (@s,10000,0,0);",
            "INSERT INTO carts(member_id,created_at,updated_at,version) VALUES (@m,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0); SET @cart=LAST_INSERT_ID();",
            "INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (@cart,@s,1);"]
    sql('\n'.join(queries + ['COMMIT;']))
    rows = sql(f"SELECT m.email,a.id FROM members m JOIN member_addresses a ON a.member_id=m.id WHERE m.email LIKE '{marker}-%';")
    return dict(row.split('\t') for row in rows.splitlines())


def cleanup(marker):
    # Exact generated namespace plus marker-owned category/product/SKU joins.
    # No global reset, FK disabling, schema reset, volume deletion or shared QA mutation.
    if not re.fullmatch(r'pc001-[0-9a-f]{12}', marker):
        raise RuntimeError('Invalid cleanup marker')
    emails = ','.join(f"'{marker}-{n}@local.invalid'" for n in range(1, POOL_SIZE + 1))
    keys = ','.join(f"'{marker}-{n}'" for n in range(1, POOL_SIZE + 1))
    m = f"SELECT id FROM members WHERE email IN ({emails})"
    sql(f"""START TRANSACTION;
DELETE im FROM inventory_movements im JOIN payments p ON p.id=im.payment_id JOIN orders o ON o.id=p.order_id WHERE o.member_id IN ({m});
DELETE FROM checkout_idempotency_results WHERE member_id IN ({m});
DELETE p FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.member_id IN ({m});
DELETE oi FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.member_id IN ({m});
DELETE FROM orders WHERE member_id IN ({m});
DELETE ci FROM cart_items ci JOIN carts c ON c.id=ci.cart_id WHERE c.member_id IN ({m});
DELETE FROM carts WHERE member_id IN ({m});
DELETE FROM member_addresses WHERE member_id IN ({m});
DELETE FROM members WHERE email IN ({emails});
DELETE i FROM inventories i JOIN skus s ON s.id=i.sku_id JOIN products p ON p.id=s.product_id WHERE p.catalog_key IN ({keys}) AND s.sku_code IN ({keys});
DELETE s FROM skus s JOIN products p ON p.id=s.product_id WHERE p.catalog_key IN ({keys}) AND s.sku_code IN ({keys});
DELETE FROM products WHERE catalog_key IN ({keys});
DELETE FROM categories WHERE slug='{marker}';
DELETE FROM brands WHERE slug='{marker}';
COMMIT;""")
    if sql(f"SELECT (SELECT COUNT(*) FROM members WHERE email LIKE '{marker}-%') + (SELECT COUNT(*) FROM products WHERE catalog_key LIKE '{marker}-%') + (SELECT COUNT(*) FROM skus WHERE sku_code LIKE '{marker}-%') + (SELECT COUNT(*) FROM categories WHERE slug='{marker}') + (SELECT COUNT(*) FROM brands WHERE slug='{marker}');") != '0':
        raise RuntimeError('STOP: fixture cleanup verification failed')


def fixture_counts(marker):
    rows = sql(f"""SELECT 'members',COUNT(*) FROM members WHERE email LIKE '{marker}-%@local.invalid'
UNION ALL SELECT 'orders',COUNT(*) FROM orders o JOIN members m ON m.id=o.member_id WHERE m.email LIKE '{marker}-%@local.invalid'
UNION ALL SELECT 'payments_ready',COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id JOIN members m ON m.id=o.member_id WHERE m.email LIKE '{marker}-%@local.invalid' AND p.status='READY'
UNION ALL SELECT 'reservations',COUNT(*) FROM inventory_movements im JOIN skus s ON s.id=im.sku_id WHERE s.sku_code LIKE '{marker}-%' AND im.type='RESERVE'
UNION ALL SELECT 'reserved_quantity',SUM(i.reserved_quantity) FROM inventories i JOIN skus s ON s.id=i.sku_id WHERE s.sku_code LIKE '{marker}-%'
UNION ALL SELECT 'minimum_stock',MIN(i.available_quantity) FROM inventories i JOIN skus s ON s.id=i.sku_id WHERE s.sku_code LIKE '{marker}-%'
UNION ALL SELECT 'cart_items',COUNT(*) FROM cart_items ci JOIN carts c ON c.id=ci.cart_id JOIN members m ON m.id=c.member_id WHERE m.email LIKE '{marker}-%@local.invalid' AND ci.quantity=1 AND c.version=0;""")
    return {key: int(value) for key, value in (row.split('\t') for row in rows.splitlines())}


def login(email, password, address):
    cookies = http.cookiejar.CookieJar()
    client = build_opener(HTTPCookieProcessor(cookies))
    def request(path, body=None, headers=None):
        req = Request('http://127.0.0.1:8080' + path,
                      None if body is None else json.dumps(body).encode(), headers or {})
        with client.open(req, timeout=15) as response:
            return json.load(response)
    csrf = request('/api/auth/csrf')['token']
    request('/api/auth/login', {'email': email, 'password': password},
            {'X-CSRF-TOKEN': csrf, 'Content-Type': 'application/json'})
    csrf = request('/api/auth/csrf')['token']
    return {'addressId': int(address), 'csrf': csrf,
            'cookie': '; '.join(f'{c.name}={c.value}' for c in cookies)}


def snapshot():
    raw = sql("""SELECT DIGEST,DIGEST_TEXT,COUNT_STAR,SUM_TIMER_WAIT,SUM_LOCK_TIME,
SUM_ROWS_EXAMINED,SUM_ROWS_AFFECTED,SUM_ROWS_SENT,SUM_ERRORS
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME=DATABASE() AND DIGEST IS NOT NULL;""")
    digests = {}
    for row in raw.splitlines():
        columns = row.split('\t')
        digests[columns[0]] = {'sql': columns[1], **dict(zip(FIELDS, map(int, columns[2:])))}
    locks = dict(row.split('\t') for row in sql("SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_row_lock_waits','Innodb_row_lock_time','Innodb_row_lock_time_max','Innodb_deadlocks','Performance_schema_digest_lost');").splitlines())
    deadlocks = sql("SELECT COUNT FROM INFORMATION_SCHEMA.INNODB_METRICS WHERE NAME='lock_deadlocks';")
    transactions = sql("SELECT EVENT_NAME,COUNT_STAR,SUM_TIMER_WAIT,COUNT_READ_WRITE,SUM_TIMER_READ_WRITE FROM performance_schema.events_transactions_summary_global_by_event_name;")
    return {'digests': digests, 'locks': {k: int(v) for k, v in locks.items()},
            'innodb_deadlocks': int(deadlocks) if deadlocks else None, 'transactions': transactions}


def commit_counters(snapshot_data):
    row = next((value for value in snapshot_data['digests'].values()
                if value['sql'].strip().upper() == 'COMMIT'), None)
    return {'count': row['count'] if row else 0, 'timer_ps': row['timer_ps'] if row else 0}


def commit_sample():
    row = sql("""SELECT COALESCE(SUM(COUNT_STAR),0),COALESCE(SUM(SUM_TIMER_WAIT),0)
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME=DATABASE() AND UPPER(DIGEST_TEXT)='COMMIT';""")
    count, timer_ps = map(int, row.split('\t'))
    return {'count': count, 'timer_ps': timer_ps}


def mysql_delta(before, after):
    rows = []
    for digest, latest in after['digests'].items():
        old = before['digests'].get(digest, {key: 0 for key in FIELDS})
        delta = {key: latest[key] - old[key] for key in FIELDS}
        if delta['count'] and 'performance_schema' not in latest['sql']:
            rows.append({'digest': digest, 'sql': latest['sql'], **delta,
                         'total_ms': delta['timer_ps'] / 1e9,
                         'average_ms': delta['timer_ps'] / delta['count'] / 1e9,
                         'lock_ms': delta['lock_ps'] / 1e9})
    return {'digests': sorted(rows, key=lambda r: r['timer_ps'], reverse=True),
            'innodb_delta': {k: v - before['locks'].get(k, 0) for k, v in after['locks'].items()},
            'innodb_deadlocks_delta': (after['innodb_deadlocks'] - before['innodb_deadlocks']
                                       if before.get('innodb_deadlocks') is not None
                                       and after.get('innodb_deadlocks') is not None else None),
            'transaction_snapshots': [before['transactions'], after['transactions']]}


def prometheus(start, end):
    dashboard = get_json('http://127.0.0.1:3001/api/dashboards/uid/pawcycle-local-observability')['dashboard']
    queries = []
    for panel in dashboard['panels']:
        if panel['id'] in [1, 2, 3, 4, 5, 6, 13, 17, 18]:
            for target in panel['targets']:
                queries.append({'panel_id': panel['id'], 'title': panel['title'],
                                'query': target['expr'].replace('$__rate_interval', '1m')})
    checkout_filter = '{uri="/api/checkout"}'
    extra = {
        'checkout request rate': f'sum(rate(http_server_requests_seconds_count{checkout_filter}[1m]))',
        'checkout p95': f'histogram_quantile(0.95, sum by(le)(rate(http_server_requests_seconds_bucket{checkout_filter}[1m])))',
        'checkout p99': f'histogram_quantile(0.99, sum by(le)(rate(http_server_requests_seconds_bucket{checkout_filter}[1m])))',
        'checkout 4xx': 'sum(rate(http_server_requests_seconds_count{uri="/api/checkout",status=~"4.."}[1m]))',
        'checkout 5xx': 'sum(rate(http_server_requests_seconds_count{uri="/api/checkout",status=~"5.."}[1m]))',
        'GC pause sum': 'sum(jvm_gc_pause_seconds_sum)',
        'GC pause count': 'sum(jvm_gc_pause_seconds_count)',
        'Hikari max': 'hikaricp_connections_max',
        'Hikari acquire count': 'hikaricp_connections_acquire_seconds_count',
        'Hikari acquire sum': 'hikaricp_connections_acquire_seconds_sum',
        'Hikari usage count': 'hikaricp_connections_usage_seconds_count',
        'Hikari usage sum': 'hikaricp_connections_usage_seconds_sum',
        'Hikari active': 'hikaricp_connections_active',
        'Hikari idle': 'hikaricp_connections_idle',
        'Hikari pending': 'hikaricp_connections_pending',
        'process CPU': 'process_cpu_usage',
        'JVM heap used': 'sum(jvm_memory_used_bytes{area="heap"})',
        'JVM live threads': 'jvm_threads_live_threads',
        'JVM peak threads': 'jvm_threads_peak_threads',
    }
    queries += [{'title': name, 'query': q} for name, q in extra.items()]
    for query in queries:
        query['series'] = get_json('http://127.0.0.1:9090/api/v1/query_range?' + urlencode(
            {'query': query['query'], 'start': start, 'end': end, 'step': '15s'}))['data']['result']
        values = [float(value) for series in query['series'] for _, value in series['values']
                  if math.isfinite(float(value))]
        query['stats'] = {'samples': len(values), 'min': min(values), 'max': max(values),
                          'mean': statistics.mean(values)} if values else {'samples': 0}
    def counter_delta(title):
        query = next(q for q in queries if q.get('title') == title)
        totals = {}
        for series in query['series']:
            for timestamp, value in series['values']:
                totals[float(timestamp)] = totals.get(float(timestamp), 0.0) + float(value)
        points = sorted(totals.items())
        if len(points) < 2:
            return {'samples': len(points)}
        first, last = points[0], points[-1]
        return {'samples': len(points), 'from_utc': dt.datetime.fromtimestamp(
                    first[0], dt.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z'),
                'to_utc': dt.datetime.fromtimestamp(
                    last[0], dt.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z'),
                'delta': last[1] - first[1]}
    acquire_count = counter_delta('Hikari acquire count')
    acquire_sum = counter_delta('Hikari acquire sum')
    usage_count = counter_delta('Hikari usage count')
    usage_sum = counter_delta('Hikari usage sum')
    gc_count = counter_delta('GC pause count')
    gc_sum = counter_delta('GC pause sum')
    def average_ms(total, count):
        return round(total * 1000 / count, 6) if count > 0 else None
    return {'dashboard_uid': dashboard['uid'], 'start_utc': start, 'end_utc': end,
            'step_seconds': 15, 'rate_interval': '1m', 'queries': queries,
            'counter_deltas': {
                'hikari_acquire': {'count': acquire_count, 'sum_seconds': acquire_sum,
                                   'average_ms': average_ms(acquire_sum.get('delta', 0), acquire_count.get('delta', 0))},
                'hikari_usage_connection_hold': {'count': usage_count, 'sum_seconds': usage_sum,
                                                 'average_ms': average_ms(usage_sum.get('delta', 0), usage_count.get('delta', 0))},
                'gc_pause': {'count': gc_count, 'sum_seconds': gc_sum,
                             'average_ms': average_ms(gc_sum.get('delta', 0), gc_count.get('delta', 0))},
            }}


def metric_count(summary, metric):
    return int(summary.get('metrics', {}).get(metric, {}).get('values', {}).get('count', 0))


def classify_measurement(dropped, k6_exit, status_error_rate, no_deadlocks,
                        runtime_stable, fixture_contract, digest_complete, scrape_healthy):
    gates = (k6_exit == 0 and status_error_rate == 0 and no_deadlocks and runtime_stable
             and fixture_contract and digest_complete and scrape_healthy)
    if dropped > 0:
        return 'capacity_failure_candidate' if gates else 'invalid_measurement_with_drops'
    return 'valid_before' if gates else 'invalid_measurement'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--stage', choices=['before', 'after'])
    parser.add_argument('--attempt')
    parser.add_argument('--cleanup')
    args = parser.parse_args()
    inspect('mysql')
    state_before = inspect('backend')
    if args.cleanup:
        if not re.fullmatch(r'pc001-[0-9a-f]{12}', args.cleanup):
            raise RuntimeError('Invalid cleanup marker')
        cleanup(args.cleanup)
        print('Exact fixture cleanup verified')
        return
    if not args.stage or not args.attempt:
        parser.error('--stage/--attempt or --cleanup required')
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,31}', args.attempt):
        raise RuntimeError('Invalid evidence attempt name')
    if args.stage == 'before':
        require_before_source_state()
    prefix = f'{args.stage}-{args.attempt}'
    paths = {suffix: EVIDENCE / f'{prefix}-{suffix}.json'
             for suffix in ['warmup', 'measurement', 'prometheus', 'cleanup']}
    if any(path.exists() for path in paths.values()):
        raise RuntimeError('STOP: this attempt already has evidence; do not repeat it')
    if sql('SELECT @@performance_schema;') != '1':
        raise RuntimeError('STOP: performance_schema unavailable')
    targets = get_json('http://127.0.0.1:9090/api/v1/targets')['data']['activeTargets']
    if not any(t['labels'].get('job') == 'pawcycle-backend' and t['health'] == 'up'
               and t['scrapeUrl'] == 'http://backend:8080/actuator/prometheus' for t in targets):
        raise RuntimeError('STOP: local backend scrape unavailable')
    settings = dict(line.split('=', 1) for line in (LOCAL / '.env.local').read_text().splitlines()
                    if '=' in line and not line.startswith('#'))
    marker = 'pc001-' + uuid.uuid4().hex[:12]
    print(f'Fixture cleanup marker: {marker}', flush=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    seeded = False
    server = None
    commit_stop = threading.Event()
    commit_lock = threading.Lock()
    commit_samples = []
    commit_sample_errors = []
    try:
        addresses = seed(marker, settings['PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL'])
        seeded = True
        fixture_before = fixture_counts(marker)
        if fixture_before['members'] != POOL_SIZE or fixture_before['cart_items'] != POOL_SIZE or fixture_before['minimum_stock'] != 10000:
            raise RuntimeError('STOP: fixture identity/stock gate failed')
        pool = [login(f'{marker}-{n}@local.invalid', settings['PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD'],
                      addresses[f'{marker}-{n}@local.invalid']) for n in range(1, POOL_SIZE + 1)]
        events = {}

        def append_commit_sample(at, counters):
            with commit_lock:
                commit_samples.append({'at_utc': at, **counters})

        def observe_commit_timeline():
            while not commit_stop.wait(0.2) and 'start' not in events:
                pass
            while not commit_stop.wait(15):
                if events.get('end'):
                    return
                try:
                    append_commit_sample(utc(), commit_sample())
                except Exception as error:
                    commit_sample_errors.append(type(error).__name__)
                    return

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass
            def do_POST(self):
                try:
                    if self.path == '/start':
                        events['before'] = snapshot()
                        events['start'] = utc()
                        if events.get('phase') == 'measurement':
                            append_commit_sample(events['start'], commit_counters(events['before']))
                    elif self.path == '/end':
                        events['end'] = utc()
                        events['after'] = snapshot()
                        if events.get('phase') == 'measurement':
                            append_commit_sample(events['end'], commit_counters(events['after']))
                            commit_stop.set()
                    else:
                        raise RuntimeError('Unexpected collector event')
                    self.send_response(200)
                except Exception:
                    self.send_response(500)
                self.end_headers()

        server = HTTPServer(('127.0.0.1', 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        env = os.environ.copy()
        env.update(BASE_URL='http://127.0.0.1:8080', CHECKOUT_POOL=json.dumps(pool),
                   CHECKOUT_COLLECTOR=f'http://127.0.0.1:{server.server_port}', CHECKOUT_RUN=marker)
        summaries = {}
        for phase in ['warmup', 'measurement']:
            events.clear()
            events['phase'] = phase
            env['CHECKOUT_PHASE'] = phase
            monitor = None
            if phase == 'measurement':
                commit_stop.clear()
                monitor = threading.Thread(target=observe_commit_timeline, daemon=True)
                monitor.start()
            with tempfile.TemporaryDirectory(prefix='pc001-') as temporary:
                summary = Path(temporary) / 'summary.json'
                env['CHECKOUT_SUMMARY'] = str(summary)
                print(f'{phase}: 20 RPS / {30 if phase == "warmup" else 120}s / {POOL_SIZE} isolated VUs', flush=True)
                result = subprocess.run(['k6', 'run', '--quiet', str(Path(__file__).with_name('checkout.js'))],
                                        env=env, capture_output=True, text=True, encoding='utf-8')
                if not summary.exists():
                    raise RuntimeError('STOP: k6 did not complete; response/log suppressed')
                summaries[phase] = json.loads(summary.read_text())
            runtime_after_phase = inspect('backend')
            phase_mysql = mysql_delta(events['before'], events['after'])
            phase_errors = summaries[phase]['metrics']['checkout_expected_status_errors']['values']['rate']
            phase_dropped = metric_count(summaries[phase], 'dropped_iterations')
            if phase == 'warmup':
                warmup_evidence = {'phase': 'correctness-warmup', 'pool_size': POOL_SIZE,
                    'start_utc': events.get('start'), 'end_utc': events.get('end'),
                    'k6_exit': result.returncode, 'status_error_rate': phase_errors,
                    'dropped_iterations': phase_dropped, 'k6': summaries[phase],
                    'runtime_before': state_before, 'runtime_after': runtime_after_phase,
                    'mysql': phase_mysql}
                paths['warmup'].write_text(json.dumps(warmup_evidence, indent=2) + '\n')
                deadlocks = phase_mysql.get('innodb_deadlocks_delta')
                restarted = runtime_after_phase['started'] != state_before['started'] or runtime_after_phase['restarts'] != state_before['restarts']
                if result.returncode or phase_errors != 0 or deadlocks != 0 or restarted or runtime_after_phase['oom']:
                    raise RuntimeError('STOP: correctness warm-up gate failed; no measurement started')
                continue

            commit_stop.set()
            if monitor:
                monitor.join(timeout=2)
            counts = fixture_counts(marker)
            warmup_requests = int(summaries['warmup']['metrics']['checkout_requests']['values']['count'])
            measurement_requests = int(summaries['measurement']['metrics']['checkout_requests']['values']['count'])
            expected_requests = warmup_requests + measurement_requests
            fixture_contract = (
                all(counts[key] == expected_requests for key in
                    ['orders', 'payments_ready', 'reservations', 'reserved_quantity'])
                and counts['cart_items'] == POOL_SIZE and counts['minimum_stock'] > 0)
            evidence = {'source_sha': command(['git', 'rev-parse', 'HEAD']),
                'pool_size': POOL_SIZE, 'target_rps': 20, 'warmup_seconds': 30, 'measurement_seconds': 120,
                'start_utc': events.get('start'), 'end_utc': events.get('end'),
                'k6_exit': result.returncode, 'status_error_rate': phase_errors,
                'dropped_iterations': phase_dropped, 'k6': summaries[phase],
                'warmup_requests': warmup_requests, 'measurement_requests': measurement_requests,
                'runtime_before': state_before, 'runtime_after': runtime_after_phase,
                'fixture_before': fixture_before, 'fixture_after': counts,
                'fixture_contract_matches_requests': fixture_contract,
                'mysql': mysql_delta(events['before'], events['after']),
                'commit_timeline': sorted(commit_samples, key=lambda row: row['at_utc']),
                'commit_timeline_sample_errors': commit_sample_errors}
            paths['measurement'].write_text(json.dumps(evidence, indent=2) + '\n')
            break

        commit_stop.set()
        server.shutdown()
        if 'measurement' not in summaries:
            raise RuntimeError('STOP: no measurement was started')
        # Allow the last scrape to arrive; all range queries use the exact k6 measurement window.
        time.sleep(16)
        prom = prometheus(evidence['start_utc'], evidence['end_utc'])
        paths['prometheus'].write_text(json.dumps(prom, indent=2) + '\n')
        latest = evidence['runtime_after']
        scrape = next(q for q in prom['queries'] if q.get('panel_id') == 13)['stats']
        runtime_stable = (not latest['oom'] and latest['restarts'] == state_before['restarts']
                          and latest['started'] == state_before['started'])
        scrape_healthy = scrape.get('min') == 1
        digest_complete = not evidence['mysql']['innodb_delta'].get('Performance_schema_digest_lost', 0)
        no_status_errors = evidence['status_error_rate'] == 0
        no_deadlocks = evidence['mysql'].get('innodb_deadlocks_delta') == 0
        classification = classify_measurement(
            evidence['dropped_iterations'], evidence['k6_exit'], evidence['status_error_rate'],
            no_deadlocks, runtime_stable, fixture_contract, digest_complete, scrape_healthy)
        evidence.update({'classification': classification, 'runtime_stable': runtime_stable,
                         'backend_scrape_healthy': scrape_healthy, 'digest_complete': digest_complete,
                         'prometheus_counter_deltas': prom['counter_deltas']})
        paths['measurement'].write_text(json.dumps(evidence, indent=2) + '\n')
        if not runtime_stable:
            raise RuntimeError('STOP: Backend restart/OOM during measurement')
        if not digest_complete:
            raise RuntimeError('STOP: SQL digest loss during measurement')
        if not scrape_healthy:
            raise RuntimeError('STOP: Backend scrape missing in measurement range')
        if not fixture_contract:
            raise RuntimeError('STOP: fixture mutation boundary/contract gate failed')
        print(f"Measurement evidence saved; classification={classification}; dropped={phase_dropped}", flush=True)
    finally:
        commit_stop.set()
        if server:
            server.shutdown()
        if seeded:
            cleanup(marker)
            paths['cleanup'].write_text(json.dumps(
                {'verified': True, 'boundary': f'exact run namespace; {POOL_SIZE} dedicated members/products/SKUs; shared QA preserved'}, indent=2) + '\n')
            print('Exact fixture cleanup verified', flush=True)


if __name__ == '__main__':
    main()
