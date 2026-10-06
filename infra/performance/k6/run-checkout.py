"""Local-only Checkout runner; credentials and identities stay in memory.

Run after starting the existing local-integration Compose services:
  python infra/performance/k6/run-checkout.py --stage before --attempt v120
PERF-COMMERCE-002: --ladder [--backend-port 18080], stops at first hard failure.
PERF-COMMERCE-003: --perf003 [--backend-port 18080] runs isolated control then shared-SKU ladder.
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
PERF003_EVIDENCE = ROOT / 'docs/reports/PERF-COMMERCE-003/evidence'
COMPOSE = ['docker', 'compose', '--env-file', str(LOCAL / '.env.local'), '-f', str(LOCAL / 'compose.yaml')]
FIELDS = ['count', 'timer_ps', 'lock_ps', 'examined', 'affected', 'sent', 'errors']
BASE_MAIN_SHA = 'a3787d8cd0437b40c4aa6817996d70d2168610b7'
POOL_SIZE = 120
LADDER_RATES = (5, 10, 15, 20)
LADDER_MAIN_SHA = 'cb4e9b46031b42c46f2f4a4dfbab0871acb82b4e'
PERF003_MAIN_SHA = '6ed3cdb09c89ad0db8570621992ebf817ba26497'
BASE_URL = 'http://127.0.0.1:8080'
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
        if bindings != [{'HostIp': '127.0.0.1', 'HostPort': BASE_URL.rsplit(':', 1)[1]}]:
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


def seed(marker, email, fixture_profile='isolated'):
    if fixture_profile not in ['isolated', 'shared']:
        raise RuntimeError('Invalid fixture profile')
    if not re.fullmatch(r'qa-foundation-004@[a-zA-Z0-9.-]+', email):
        raise RuntimeError('Invalid QA bootstrap email contract')
    if sql(f"SELECT COUNT(*) FROM members WHERE email='{email}' AND role='USER';") != '1':
        raise RuntimeError('QA bootstrap member missing')
    if sql(f"SELECT COUNT(*) FROM members WHERE email LIKE '{marker}-%';") != '0':
        raise RuntimeError('STOP: fixture marker already exists')
    queries = ["START TRANSACTION;",
        f"INSERT INTO brands(name,slug,active,display_order) VALUES ('Checkout perf','{marker}',true,0); SET @b=LAST_INSERT_ID();",
        f"INSERT INTO categories(name,slug,active,display_order) VALUES ('Checkout perf','{marker}',true,0); SET @c=LAST_INSERT_ID();"]
    if fixture_profile == 'shared':
        queries += [
            f"INSERT INTO products(brand_id,category_id,catalog_key,name,short_description,description,pet_type,display_status) VALUES (@b,@c,'{marker}-shared','Checkout perf','Synthetic','Local shared-SKU performance fixture','DOG','PUBLIC'); SET @p=LAST_INSERT_ID();",
            f"INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES (@p,'{marker}-shared','Checkout shared SKU',19900,false,0,'ACTIVE'); SET @s=LAST_INSERT_ID();",
            "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (@s,10000,0,0);",
        ]
    for n in range(1, POOL_SIZE + 1):
        queries += [
            f"INSERT INTO members(email,password_hash,role) SELECT '{marker}-{n}@local.invalid',password_hash,'USER' FROM members WHERE email='{email}'; SET @m=LAST_INSERT_ID();",
            "INSERT INTO member_addresses(member_id,name,recipient_name,recipient_phone,postal_code,address_line1,created_at,updated_at) VALUES (@m,'Checkout perf','Synthetic','00000000000','00000','Synthetic local fixture',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));",
            "INSERT INTO carts(member_id,created_at,updated_at,version) VALUES (@m,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0); SET @cart=LAST_INSERT_ID();",
        ]
        if fixture_profile == 'isolated':
            queries += [
                f"INSERT INTO products(brand_id,category_id,catalog_key,name,short_description,description,pet_type,display_status) VALUES (@b,@c,'{marker}-{n}','Checkout perf','Synthetic','Local performance fixture','DOG','PUBLIC'); SET @p=LAST_INSERT_ID();",
                f"INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES (@p,'{marker}-{n}','Checkout perf',19900,false,0,'ACTIVE'); SET @s=LAST_INSERT_ID();",
                "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (@s,10000,0,0);",
            ]
        queries += ["INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (@cart,@s,1);"]
    sql('\n'.join(queries + ['COMMIT;']))
    rows = sql(f"SELECT m.email,a.id FROM members m JOIN member_addresses a ON a.member_id=m.id WHERE m.email LIKE '{marker}-%';")
    return dict(row.split('\t') for row in rows.splitlines())


def cleanup(marker):
    # Exact generated namespace plus marker-owned category/product/SKU joins.
    # No global reset, FK disabling, schema reset, volume deletion or shared QA mutation.
    if not re.fullmatch(r'pc00[13]-[0-9a-f]{12}', marker):
        raise RuntimeError('Invalid cleanup marker')
    emails = ','.join(f"'{marker}-{n}@local.invalid'" for n in range(1, POOL_SIZE + 1))
    keys = ','.join([*(f"'{marker}-{n}'" for n in range(1, POOL_SIZE + 1)), f"'{marker}-shared'"])
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
    if sql(f"""SELECT
 (SELECT COUNT(*) FROM members WHERE email LIKE '{marker}-%')
 +(SELECT COUNT(*) FROM member_addresses a JOIN members m ON m.id=a.member_id WHERE m.email LIKE '{marker}-%@local.invalid')
 +(SELECT COUNT(*) FROM carts c JOIN members m ON m.id=c.member_id WHERE m.email LIKE '{marker}-%@local.invalid')
 +(SELECT COUNT(*) FROM products WHERE catalog_key LIKE '{marker}-%')
 +(SELECT COUNT(*) FROM skus WHERE sku_code LIKE '{marker}-%')
 +(SELECT COUNT(*) FROM categories WHERE slug='{marker}')
 +(SELECT COUNT(*) FROM brands WHERE slug='{marker}')
 +(SELECT COUNT(*) FROM orders o JOIN members m ON m.id=o.member_id WHERE m.email LIKE '{marker}-%@local.invalid')
 +(SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id JOIN members m ON m.id=o.member_id WHERE m.email LIKE '{marker}-%@local.invalid')
 +(SELECT COUNT(*) FROM checkout_idempotency_results r JOIN members m ON m.id=r.member_id WHERE m.email LIKE '{marker}-%@local.invalid')
 +(SELECT COUNT(*) FROM inventory_movements im JOIN skus s ON s.id=im.sku_id WHERE s.sku_code LIKE '{marker}-%');""") != '0':
        raise RuntimeError('STOP: fixture cleanup verification failed')


def fixture_counts(marker):
    rows = sql(f"""SELECT 'members',COUNT(*) FROM members WHERE email LIKE '{marker}-%@local.invalid'
UNION ALL SELECT 'orders',COUNT(*) FROM orders o JOIN members m ON m.id=o.member_id WHERE m.email LIKE '{marker}-%@local.invalid'
UNION ALL SELECT 'payments_ready',COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id JOIN members m ON m.id=o.member_id WHERE m.email LIKE '{marker}-%@local.invalid' AND p.status='READY'
UNION ALL SELECT 'payments',COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id JOIN members m ON m.id=o.member_id WHERE m.email LIKE '{marker}-%@local.invalid'
UNION ALL SELECT 'duplicate_payment_orders',COUNT(*) FROM (SELECT p.order_id FROM payments p JOIN orders o ON o.id=p.order_id JOIN members m ON m.id=o.member_id WHERE m.email LIKE '{marker}-%@local.invalid' GROUP BY p.order_id HAVING COUNT(*)>1) duplicates
UNION ALL SELECT 'idempotency_results',COUNT(*) FROM checkout_idempotency_results r JOIN members m ON m.id=r.member_id WHERE m.email LIKE '{marker}-%@local.invalid'
UNION ALL SELECT 'reservations',COUNT(*) FROM inventory_movements im JOIN skus s ON s.id=im.sku_id WHERE s.sku_code LIKE '{marker}-%' AND im.type='RESERVE'
UNION ALL SELECT 'reserved_quantity',SUM(i.reserved_quantity) FROM inventories i JOIN skus s ON s.id=i.sku_id WHERE s.sku_code LIKE '{marker}-%'
UNION ALL SELECT 'available_quantity',SUM(i.available_quantity) FROM inventories i JOIN skus s ON s.id=i.sku_id WHERE s.sku_code LIKE '{marker}-%'
UNION ALL SELECT 'inventory_version',SUM(i.version) FROM inventories i JOIN skus s ON s.id=i.sku_id WHERE s.sku_code LIKE '{marker}-%'
UNION ALL SELECT 'negative_inventories',COUNT(*) FROM inventories i JOIN skus s ON s.id=i.sku_id WHERE s.sku_code LIKE '{marker}-%' AND (i.available_quantity<0 OR i.reserved_quantity<0)
UNION ALL SELECT 'products',COUNT(*) FROM products WHERE catalog_key LIKE '{marker}-%'
UNION ALL SELECT 'skus',COUNT(*) FROM skus WHERE sku_code LIKE '{marker}-%'
UNION ALL SELECT 'inventories',COUNT(*) FROM inventories i JOIN skus s ON s.id=i.sku_id WHERE s.sku_code LIKE '{marker}-%'
UNION ALL SELECT 'minimum_stock',MIN(i.available_quantity) FROM inventories i JOIN skus s ON s.id=i.sku_id WHERE s.sku_code LIKE '{marker}-%'
UNION ALL SELECT 'cart_items',COUNT(*) FROM cart_items ci JOIN carts c ON c.id=ci.cart_id JOIN members m ON m.id=c.member_id WHERE m.email LIKE '{marker}-%@local.invalid' AND ci.quantity=1 AND c.version=0;""")
    return {key: int(value) for key, value in (row.split('\t') for row in rows.splitlines())}


def login(email, password, address):
    cookies = http.cookiejar.CookieJar()
    client = build_opener(HTTPCookieProcessor(cookies))
    def request(path, body=None, headers=None):
        req = Request(BASE_URL + path,
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
    threads = mysql_threads()
    lock_waits = int(sql('SELECT COUNT(*) FROM performance_schema.data_lock_waits;'))
    return {'digests': digests, 'locks': {k: int(v) for k, v in locks.items()},
            'innodb_deadlocks': int(deadlocks) if deadlocks else None, 'transactions': transactions,
            'threads': threads, 'active_lock_wait_count': lock_waits}


def mysql_threads():
    return {key: int(value) for key, value in (row.split('\t') for row in sql(
        "SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running');").splitlines())}


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
            'innodb_lock_status_before': before['locks'],
            'innodb_lock_status_after': after['locks'],
            'active_lock_wait_count_before_after': [before.get('active_lock_wait_count'), after.get('active_lock_wait_count')],
            'transaction_snapshots': [before['transactions'], after['transactions']],
            'threads_before_after': [before.get('threads', {}), after.get('threads', {})]}


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
        'system CPU': 'system_cpu_usage',
        'JVM heap committed': 'sum(jvm_memory_committed_bytes{area="heap"})',
        'JVM heap max': 'sum(jvm_memory_max_bytes{area="heap"})',
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


def status_error_counts(summary):
    counts = {}
    for name, metric in summary.get('metrics', {}).items():
        if not name.startswith('checkout_status_errors'):
            continue
        status = re.search(r'(?:^|[,{])\s*status\s*[:=]\s*["\']?([^,"\'}]+)', name)
        key = status.group(1).strip().strip('"').strip("'") if status else 'unknown'
        counts[key] = counts.get(key, 0) + int(metric.get('values', {}).get('count', 0))
    return counts


def classify_measurement(dropped, k6_exit, status_error_rate, no_deadlocks,
                        runtime_stable, fixture_contract, digest_complete, scrape_healthy):
    gates = (k6_exit == 0 and status_error_rate == 0 and no_deadlocks and runtime_stable
             and fixture_contract and digest_complete and scrape_healthy)
    if dropped > 0:
        return 'capacity_failure_candidate' if gates else 'invalid_measurement_with_drops'
    return 'valid_before' if gates else 'invalid_measurement'


def memory_bytes(value):
    # Same Docker stats JSON / memory conversion pattern as catalog-isolated collector.
    match = re.fullmatch(r'([\d.]+)([kMGT]?i?B)', value.strip())
    units = {'B': 1, 'kB': 1000, 'MB': 1000**2, 'GB': 1000**3, 'TB': 1000**4,
             'KiB': 1024, 'MiB': 1024**2, 'GiB': 1024**3, 'TiB': 1024**4}
    if not match or match[2] not in units:
        raise ValueError('Docker memory usage format unavailable')
    return float(match[1]) * units[match[2]]


def resource_sample(containers):
    raw = command(['docker', 'stats', '--no-stream', '--format', '{{json .}}', *containers.values()])
    rows = {row['ID']: row for row in map(json.loads, raw.splitlines())}
    result = {'at_utc': utc(), 'mysql_threads': mysql_threads()}
    try:
        result['active_lock_wait_count'] = int(sql('SELECT COUNT(*) FROM performance_schema.data_lock_waits;'))
    except Exception as error:
        result['active_lock_wait_count'] = None
        result['lock_wait_sample_error'] = type(error).__name__
    for service, cid in containers.items():
        row = next(value for key, value in rows.items() if cid.startswith(key))
        used, limit = row['MemUsage'].split(' / ')
        result[service] = {'cpu_percent': float(row['CPUPerc'].rstrip('%')),
                           'memory_used_bytes': memory_bytes(used),
                           'memory_limit_bytes': memory_bytes(limit)}
    return result


def actual_rps(evidence):
    duration = (dt.datetime.fromisoformat(evidence['end_utc'].replace('Z', '+00:00')) -
                dt.datetime.fromisoformat(evidence['start_utc'].replace('Z', '+00:00'))).total_seconds()
    return metric_count(evidence['k6'], 'checkout_requests') / duration if duration > 0 else 0


def stage_failures(evidence, check_actual=True):
    checks = {'status_errors': evidence['status_error_rate'] == 0,
              'dropped': evidence['dropped_iterations'] == 0,
              'deadlock': evidence['mysql'].get('innodb_deadlocks_delta') == 0,
              'restart_oom': evidence['runtime_stable'],
              'fixture_mismatch': evidence['fixture_contract_matches_requests'],
              'backend_scrape': evidence['backend_scrape_healthy'],
              'actual_rps': not check_actual or actual_rps(evidence) >= evidence['target_rps'] * 0.98,
              'digest_loss': evidence['digest_complete'], 'k6_exit': evidence['k6_exit'] == 0}
    return [name for name, passed in checks.items() if not passed]


def consecutive_positive(series):
    longest = 0
    for row in series:
        current = 0
        for _, value in row['values']:
            current = current + 1 if math.isfinite(float(value)) and float(value) > 0 else 0
            longest = max(longest, current)
    return longest


def is_checkout_digest(statement):
    tables = r'\b(members|member_addresses|carts|cart_items|checkout_idempotency_results|products|skus|inventories|inventory_movements|orders|order_items|payments)\b'
    if not re.match(r'^(SELECT|INSERT|UPDATE)\b', statement) or not re.search(tables, statement, re.I):
        return False
    if re.search(r'performance_schema|INFORMATION_SCHEMA', statement, re.I):
        return False
    # Checkout SELECTs target member/cart/SKU/payment/idempotency identities.
    # Merely mentioning products/skus/payments also matches catalog healthchecks,
    # payment expiry scans and aggregate Micrometer gauges; exclude those scans.
    identity_predicate = bool(re.search(
        r'(?:\.|\bWHERE)\s*`?(?:id|member_id|cart_id|sku_id|idempotency_key)`?\s*(?:=\s*\?|IN\s*\(\.\.\.\))',
        statement, re.I))
    composite_id = ('checkout_idempotency_results' in statement and bool(re.search(
        r'WHERE\s*\([^)]*\bidempotency_key\b[^)]*\bmember_id\b[^)]*\)\s*IN\s*\(', statement, re.I)))
    return not statement.startswith('SELECT') or identity_predicate or composite_id


def digest_boundary_summaries(digests):
    boundaries = {'commit': [], 'sku_select_for_update': [], 'inventory_select': [], 'inventory_reserve_update': []}
    for row in digests:
        statement = row['sql'].upper()
        if statement.strip() == 'COMMIT':
            boundaries['commit'].append(row)
        elif statement.startswith('SELECT') and re.search(r'FROM\s+`?SKUS`?', statement) and 'FOR UPDATE' in statement:
            boundaries['sku_select_for_update'].append(row)
        elif statement.startswith('SELECT') and re.search(r'FROM\s+`?INVENTORIES`?', statement):
            boundaries['inventory_select'].append(row)
        elif statement.startswith('UPDATE') and re.search(r'\bINVENTORIES\b', statement):
            boundaries['inventory_reserve_update'].append(row)
    result = {}
    for name, rows in boundaries.items():
        count = sum(row['count'] for row in rows)
        timer_ps = sum(row['timer_ps'] for row in rows)
        result[name] = {'digest_count': len(rows), 'statement_count': count,
                        'total_ms': timer_ps / 1e9,
                        'average_ms': timer_ps / count / 1e9 if count else None,
                        'lock_ms': sum(row['lock_ps'] for row in rows) / 1e9}
    return result


def stage_attribution(evidence, prom):
    stats = {q['title']: q['stats'] for q in prom['queries']}
    samples = evidence.get('resource_samples', [])
    resources = {}
    for service in ['backend', 'mysql']:
        resources[service] = {}
        for field in ['cpu_percent', 'memory_used_bytes']:
            values = [row[service][field] for row in samples]
            resources[service][field] = {'samples': len(values), 'mean': statistics.mean(values),
                                       'max': max(values)} if values else {'samples': 0}
    threads = {}
    for field in ['Threads_connected', 'Threads_running']:
        values = [row['mysql_threads'][field] for row in samples]
        threads[field] = {'samples': len(values), 'min': min(values), 'max': max(values),
                          'mean': statistics.mean(values)} if values else {'samples': 0}
    pending = next(q for q in prom['queries'] if q['title'] == 'Hikari pending')
    digests = evidence['mysql']['digests']
    checkout_digests = [row for row in digests if is_checkout_digest(row['sql'])]
    lock_wait_samples = [row['active_lock_wait_count'] for row in samples
                         if row.get('active_lock_wait_count') is not None]
    sample_times = [dt.datetime.fromisoformat(row['at_utc'].replace('Z', '+00:00')).timestamp()
                    for row in samples]
    sample_intervals = [later - earlier for earlier, later in zip(sample_times, sample_times[1:])]
    return {'actual_rps': actual_rps(evidence),
            'latency_ms': evidence['k6']['metrics']['checkout_latency']['values'],
            'status_error_counts': evidence.get('status_error_counts', {}),
            'prometheus': {name: stats[name] for name in ['Hikari active', 'Hikari idle', 'Hikari pending',
                'Hikari max', 'process CPU', 'system CPU', 'JVM heap used', 'JVM heap committed',
                'JVM heap max', 'JVM live threads', 'JVM peak threads']},
            'counter_deltas': prom['counter_deltas'],
            'pending_longest_positive_samples': consecutive_positive(pending['series']),
            'pending_samples': pending['stats'],
            'docker': resources, 'mysql_threads': threads,
            'data_lock_waits': {'samples': len(lock_wait_samples),
                                'mean': statistics.mean(lock_wait_samples) if lock_wait_samples else None,
                                'max': max(lock_wait_samples) if lock_wait_samples else None,
                                'sample_errors': sum(row.get('active_lock_wait_count') is None for row in samples)},
            'resource_sampling_completion_interval_seconds': {
                'samples': len(sample_intervals),
                'mean': statistics.mean(sample_intervals) if sample_intervals else None,
                'min': min(sample_intervals) if sample_intervals else None,
                'max': max(sample_intervals) if sample_intervals else None,
                'errors': len(evidence.get('resource_sample_errors', []))},
            'commit': next((row for row in digests if row['sql'].strip().upper() == 'COMMIT'), None),
            'sql_boundaries': digest_boundary_summaries(digests),
            'checkout_digests': checkout_digests,
            'background_digests': [row for row in digests if row not in checkout_digests
                                   and row['sql'].strip().upper() != 'COMMIT']}


def run_ladder(backend_port, run_stage=None, stage_name='ladder', fixture_profile='isolated'):
    if run_stage is None:
        baseline = {service: inspect(service) for service in ['backend', 'mysql']}
        def run_stage(rate):
            if any(not runtime_unchanged(baseline[service], inspect(service)) for service in baseline):
                raise RuntimeError('STOP: ladder runtime changed between stages')
            return main(['--stage', stage_name, '--attempt', f'rps-{rate}',
                         '--target-rps', str(rate), '--backend-port', str(backend_port),
                         '--fixture-profile', fixture_profile])
    results = []
    for rate in LADDER_RATES:
        evidence = run_stage(rate)
        stage_actual_rps = actual_rps(evidence) if evidence.get('start_utc') and evidence.get('end_utc') else evidence.get('actual_rps')
        results.append({'target_rps': rate, 'actual_rps': stage_actual_rps,
                        'start_utc': evidence.get('start_utc'), 'end_utc': evidence.get('end_utc'),
                        'phase': evidence['phase'],
                        'classification': evidence['classification'],
                        'gate_failures': evidence['gate_failures']})
        if evidence['classification'] != 'stable':
            break
    return {'stages': results, 'not_run_rps': list(LADDER_RATES[len(results):])}


def run_perf003(backend_port):
    if command(['git', 'branch', '--show-current']) != 'codex/perf-commerce-003' or \
            command(['git', 'rev-parse', 'HEAD']) != PERF003_MAIN_SHA or \
            command(['git', 'rev-parse', 'origin/main']) != PERF003_MAIN_SHA or \
            command(['git', 'status', '--porcelain', '--', 'backend']):
        raise RuntimeError('STOP: PERF-COMMERCE-003 requires unchanged Backend on the fetched issue baseline')
    if PERF003_EVIDENCE.exists() and any(PERF003_EVIDENCE.iterdir()):
        raise RuntimeError('STOP: PERF-COMMERCE-003 evidence exists; do not repeat the load')
    baseline = {service: inspect(service) for service in ['backend', 'mysql']}
    control = main(['--stage', 'control', '--attempt', 'isolated-20', '--target-rps', '20',
                    '--backend-port', str(backend_port), '--fixture-profile', 'isolated'])
    control_result = {'target_rps': 20, 'actual_rps': actual_rps(control),
                      'start_utc': control.get('start_utc'), 'end_utc': control.get('end_utc'),
                      'classification': control['classification'], 'gate_failures': control['gate_failures'],
                      'evidence_prefix': 'control-isolated-20'}
    if control['classification'] != 'stable':
        result = {'control': control_result, 'shared_sku': {'stages': [], 'not_run_rps': list(LADDER_RATES)},
                  'stop_reason': 'isolated control failed; shared-SKU ladder not run'}
    else:
        if any(not runtime_unchanged(baseline[service], inspect(service)) for service in baseline):
            raise RuntimeError('STOP: runtime changed after isolated control')
        shared = run_ladder(backend_port, stage_name='shared', fixture_profile='shared')
        result = {'control': control_result, 'shared_sku': shared,
                  'stop_reason': 'first shared-SKU hard failure' if shared['not_run_rps'] else None}
    PERF003_EVIDENCE.mkdir(parents=True, exist_ok=True)
    (PERF003_EVIDENCE / 'run.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


def main(argv=None):
    global BASE_URL
    parser = argparse.ArgumentParser()
    parser.add_argument('--stage', choices=['before', 'after', 'ladder', 'control', 'shared'])
    parser.add_argument('--ladder', action='store_true')
    parser.add_argument('--perf003', action='store_true')
    parser.add_argument('--target-rps', type=int, choices=LADDER_RATES, default=20)
    parser.add_argument('--backend-port', type=int, default=8080)
    parser.add_argument('--fixture-profile', choices=['isolated', 'shared'], default='isolated')
    parser.add_argument('--attempt')
    parser.add_argument('--cleanup')
    args = parser.parse_args(argv)
    if not 1024 <= args.backend_port <= 65535:
        parser.error('Backend port must be a non-privileged loopback port')
    BASE_URL = f'http://127.0.0.1:{args.backend_port}'
    perf003_stage = args.stage in ['control', 'shared']
    evidence_dir = PERF003_EVIDENCE if perf003_stage or args.perf003 else (
        ROOT / 'docs/reports/PERF-COMMERCE-002/evidence' if args.ladder or args.stage == 'ladder' else EVIDENCE)
    if args.perf003:
        if args.stage or args.ladder or args.cleanup:
            parser.error('--perf003 cannot be combined with --stage, --ladder or --cleanup')
        result = run_perf003(args.backend_port)
        print(json.dumps(result), flush=True)
        return result
    if perf003_stage and (command(['git', 'branch', '--show-current']) != 'codex/perf-commerce-003' or
                          command(['git', 'rev-parse', 'HEAD']) != PERF003_MAIN_SHA or
                          command(['git', 'rev-parse', 'origin/main']) != PERF003_MAIN_SHA):
        raise RuntimeError('STOP: PERF-COMMERCE-003 stages require the issue main baseline')
    if (args.stage == 'control' and (args.target_rps != 20 or args.fixture_profile != 'isolated')) or \
            (args.stage == 'shared' and args.fixture_profile != 'shared'):
        parser.error('control requires isolated 20 RPS; shared requires the shared fixture profile')
    if args.ladder:
        if command(['git', 'branch', '--show-current']) != 'codex/perf-commerce-002' or \
                command(['git', 'rev-parse', 'HEAD']) != LADDER_MAIN_SHA or \
                command(['git', 'rev-parse', 'origin/main']) != LADDER_MAIN_SHA or \
                command(['git', 'status', '--porcelain', '--', 'backend']):
            raise RuntimeError('STOP: ladder requires unmodified Backend on the task main baseline')
        if any(evidence_dir.glob('ladder-*.json')):
            raise RuntimeError('STOP: ladder evidence exists; do not repeat')
        result = run_ladder(args.backend_port)
        (evidence_dir / 'ladder.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result), flush=True)
        return result
    inspect('mysql')
    state_before = inspect('backend')
    if args.cleanup:
        if not re.fullmatch(r'pc00[13]-[0-9a-f]{12}', args.cleanup):
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
    paths = {suffix: evidence_dir / f'{prefix}-{suffix}.json'
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
    marker = ('pc003-' if perf003_stage else 'pc001-') + uuid.uuid4().hex[:12]
    print(f'Fixture cleanup marker: {marker}', flush=True)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    seeded = False
    server = None
    commit_stop = threading.Event()
    commit_lock = threading.Lock()
    commit_samples = []
    commit_sample_errors = []
    resource_stop = threading.Event()
    resource_samples = []
    resource_errors = []
    containers = {service: command(COMPOSE + ['ps', '-q', service]) for service in ['backend', 'mysql']}
    mysql_before = inspect('mysql')
    resource_monitor = None
    try:
        addresses = seed(marker, settings['PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL'], args.fixture_profile)
        seeded = True
        fixture_before = fixture_counts(marker)
        expected_skus = 1 if args.fixture_profile == 'shared' else POOL_SIZE
        if (fixture_before['members'] != POOL_SIZE or fixture_before['cart_items'] != POOL_SIZE or
                fixture_before['products'] != expected_skus or fixture_before['skus'] != expected_skus or
                fixture_before['inventories'] != expected_skus or fixture_before['minimum_stock'] != 10000):
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

        def observe_resources():
            while not resource_stop.wait(0.05) and 'start' not in events:
                pass
            due = time.monotonic()
            while not resource_stop.is_set():
                try:
                    resource_samples.append(resource_sample(containers))
                except Exception as error:
                    resource_errors.append({'at_utc': utc(), 'error': type(error).__name__})
                due += 5
                if resource_stop.wait(max(0, due - time.monotonic())):
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
                        resource_stop.set()
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
        env.update(BASE_URL=BASE_URL, CHECKOUT_POOL=json.dumps(pool), CHECKOUT_RPS=str(args.target_rps),
                   CHECKOUT_COLLECTOR=f'http://127.0.0.1:{server.server_port}', CHECKOUT_RUN=marker)
        summaries = {}
        for phase in ['warmup', 'measurement']:
            events.clear()
            events['phase'] = phase
            phase_fixture_before = fixture_counts(marker)
            env['CHECKOUT_PHASE'] = phase
            monitor = None
            if phase == 'measurement':
                commit_stop.clear()
                resource_stop.clear()
                monitor = threading.Thread(target=observe_commit_timeline, daemon=True)
                monitor.start()
                if args.stage in ['ladder', 'control', 'shared']:
                    resource_monitor = threading.Thread(target=observe_resources, daemon=True)
                    resource_monitor.start()
            with tempfile.TemporaryDirectory(prefix='pc001-') as temporary:
                summary = Path(temporary) / 'summary.json'
                env['CHECKOUT_SUMMARY'] = str(summary)
                profile_label = 'shared-SKU' if args.fixture_profile == 'shared' else 'isolated-SKU'
                print(f'{phase}: {args.target_rps} RPS / {30 if phase == "warmup" else 120}s / {POOL_SIZE} VUs / {profile_label}', flush=True)
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
                    'fixture_profile': args.fixture_profile,
                    'start_utc': events.get('start'), 'end_utc': events.get('end'),
                    'k6_exit': result.returncode, 'status_error_rate': phase_errors,
                    'status_error_counts': status_error_counts(summaries[phase]),
                    'dropped_iterations': phase_dropped, 'k6': summaries[phase],
                    'runtime_before': state_before, 'runtime_after': runtime_after_phase,
                    'fixture_before': phase_fixture_before,
                    'mysql': phase_mysql}
                if args.stage in ['ladder', 'control', 'shared']:
                    counts = fixture_counts(marker)
                    warmup_evidence['fixture_after'] = counts
                    time.sleep(16)
                    prom = prometheus(events['start'], events['end'])
                    scrape = next(q for q in prom['queries'] if q.get('panel_id') == 13)['stats']
                    mysql_after = inspect('mysql')
                    warmup_evidence.update(phase='warmup', target_rps=args.target_rps,
                        fixture_contract_matches_requests=fixture_delta_matches(
                            phase_fixture_before, counts, metric_count(summaries[phase], 'checkout_requests'),
                            args.fixture_profile),
                        runtime_stable=runtime_unchanged(state_before, runtime_after_phase)
                            and runtime_unchanged(mysql_before, mysql_after),
                        backend_scrape_healthy=scrape.get('min') == 1 and scrape.get('samples', 0) >= 2,
                        digest_complete=phase_mysql['innodb_delta'].get('Performance_schema_digest_lost') == 0)
                    # Capacity is measured over 120s; warm-up gates correctness/drops only.
                    warmup_evidence['gate_failures'] = stage_failures(warmup_evidence, check_actual=False)
                    warmup_evidence['classification'] = 'hard_failure' if warmup_evidence['gate_failures'] else 'stable'
                    warmup_evidence['actual_rps'] = actual_rps(warmup_evidence)
                    if warmup_evidence['gate_failures']:
                        paths['prometheus'].write_text(json.dumps(prom, indent=2) + '\n')
                        paths['warmup'].write_text(json.dumps(warmup_evidence, indent=2) + '\n')
                        return warmup_evidence
                paths['warmup'].write_text(json.dumps(warmup_evidence, indent=2) + '\n')
                deadlocks = phase_mysql.get('innodb_deadlocks_delta')
                restarted = runtime_after_phase['started'] != state_before['started'] or runtime_after_phase['restarts'] != state_before['restarts']
                if result.returncode or phase_errors != 0 or deadlocks != 0 or restarted or runtime_after_phase['oom']:
                    raise RuntimeError('STOP: correctness warm-up gate failed; no measurement started')
                continue

            commit_stop.set()
            if monitor:
                monitor.join()
            resource_stop.set()
            if resource_monitor:
                resource_monitor.join()
            counts = fixture_counts(marker)
            warmup_requests = int(summaries['warmup']['metrics']['checkout_requests']['values']['count'])
            measurement_requests = int(summaries['measurement']['metrics']['checkout_requests']['values']['count'])
            expected_requests = warmup_requests + measurement_requests
            fixture_contract = fixture_delta_matches(fixture_before, counts, expected_requests,
                                                      args.fixture_profile)
            evidence = {'source_sha': command(['git', 'rev-parse', 'HEAD']),
                'phase': 'measurement', 'pool_size': POOL_SIZE, 'target_rps': args.target_rps, 'warmup_seconds': 30, 'measurement_seconds': 120,
                'fixture_profile': args.fixture_profile,
                'start_utc': events.get('start'), 'end_utc': events.get('end'),
                'k6_exit': result.returncode, 'status_error_rate': phase_errors,
                'dropped_iterations': phase_dropped, 'k6': summaries[phase],
                'status_error_counts': status_error_counts(summaries[phase]),
                'warmup_requests': warmup_requests, 'measurement_requests': measurement_requests,
                'runtime_before': state_before, 'runtime_after': runtime_after_phase,
                'fixture_before': fixture_before, 'fixture_after': counts,
                'fixture_contract_matches_requests': fixture_contract,
                'measurement_fixture_before': phase_fixture_before,
                'measurement_fixture_delta_matches_requests': fixture_delta_matches(
                    phase_fixture_before, counts, measurement_requests, args.fixture_profile),
                'mysql_runtime_before': mysql_before, 'mysql_runtime_after': inspect('mysql'),
                'resource_interval_seconds': 5,
                'resource_samples': [s for s in resource_samples if events['start'] <= s['at_utc'] <= events['end']],
                'resource_sample_errors': resource_errors,
                'mysql': mysql_delta(events['before'], events['after']),
                'commit_timeline': sorted(commit_samples, key=lambda row: row['at_utc']),
                'commit_timeline_sample_errors': commit_sample_errors}
            evidence['actual_rps'] = actual_rps(evidence)
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
        if args.stage in ['ladder', 'control', 'shared']:
            evidence['runtime_stable'] = runtime_stable and runtime_unchanged(
                mysql_before, evidence['mysql_runtime_after'])
            evidence['fixture_contract_matches_requests'] = fixture_contract and evidence['measurement_fixture_delta_matches_requests']
            evidence['backend_scrape_healthy'] = scrape_healthy and scrape.get('samples', 0) >= 2
            evidence['digest_complete'] = evidence['mysql']['innodb_delta'].get('Performance_schema_digest_lost') == 0
            evidence['gate_failures'] = stage_failures(evidence)
            evidence['classification'] = 'hard_failure' if evidence['gate_failures'] else 'stable'
            evidence['attribution'] = stage_attribution(evidence, prom)
            paths['measurement'].write_text(json.dumps(evidence, indent=2) + '\n')
            print(f"Stage {args.target_rps}: {evidence['classification']}; gates={evidence['gate_failures']}", flush=True)
            return evidence
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
        resource_stop.set()
        if resource_monitor:
            resource_monitor.join()
        if server:
            server.shutdown()
        if seeded:
            cleanup(marker)
            paths['cleanup'].write_text(json.dumps(
                {'verified': True, 'boundary': f'exact {args.fixture_profile} fixture namespace; 120 dedicated members/carts/addresses; shared QA preserved'}, indent=2) + '\n')
            print('Exact fixture cleanup verified', flush=True)


def runtime_unchanged(before, after):
    return (after['running'] and not after['oom'] and before['started'] == after['started']
            and before['restarts'] == after['restarts'] and before['image'] == after['image']
            and all(before[key] == after[key] for key in ['memory_bytes', 'nano_cpus', 'pids']))


def fixture_delta_matches(before, after, requests, fixture_profile='isolated'):
    sku_count = 1 if fixture_profile == 'shared' else POOL_SIZE
    exact_deltas = all(after[key] - before[key] == requests for key in
                       ['orders', 'payments_ready', 'payments', 'idempotency_results',
                        'reservations', 'reserved_quantity', 'inventory_version'])
    return (exact_deltas
            and after['available_quantity'] - before['available_quantity'] == -requests
            and after['negative_inventories'] == 0 and after['duplicate_payment_orders'] == 0
            and after['members'] == POOL_SIZE and after['cart_items'] == POOL_SIZE
            and after['products'] == sku_count and after['skus'] == sku_count
            and after['inventories'] == sku_count and after['minimum_stock'] > 0)


if __name__ == '__main__':
    main()
