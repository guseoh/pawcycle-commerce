#!/usr/bin/env python3
"""Deterministic linked synthetic dataset for PawCycle stage-4 performance work.

Members, catalog (brands, categories, products, SKUs, inventory ledger), ONE_TIME
orders with payments/deliveries/checkout idempotency, delivered-purchase reviews,
and runtime-managed subscriptions (plans, pets, billing methods, snapshots,
schedules, shipping snapshots) are generated inside MySQL with INSERT ... SELECT.
Every value is a function of (--seed, --scale, --as-of, --schedule-start), so the
same inputs reproduce identical tables (see `fingerprint`).

Safety boundary:
  * Only containers created by `up` (label pawcycle.purpose=perf-synthetic-data,
    name pawcycle-perfgen-*) are accepted, and /var/lib/mysql must be an anonymous
    volume or tmpfs. Named volumes (e.g. pawcycle-local-integration-*) are refused.
  * No port is published; all access is `docker exec`. The root password is random,
    lives only in the container environment, and is never printed or written.
  * No Flyway migration is added. `up` applies the repository migrations as-is.

Commands:
  up --name N             start disposable MySQL 8.4.10 and apply db/migration V1..Vn
  generate --name N --scale 10k|100k|1m --seed S [--as-of ...] [--schedule-start ...]
  check --name N          domain invariants (invariants.sql) + generator manifest checks
  fingerprint --name N    CHECKSUM TABLE of generated tables (reproducibility evidence)
  reset --name N          drop and re-create the application schema, re-apply migrations
  down --name N           remove container and its anonymous volume, verify removal
"""
import argparse
import datetime as dt
import json
import re
import secrets
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MIGRATIONS = ROOT / 'backend/src/main/resources/db/migration'
INVARIANTS = Path(__file__).with_name('invariants.sql')
IMAGE = 'mysql:8.4.10'
LABEL = 'pawcycle.purpose=perf-synthetic-data'
NAME_PATTERN = re.compile(r'pawcycle-perfgen-[a-z0-9][a-z0-9-]{0,40}')
DATABASE = 'pawcycle'
WORK = 'perfgen'
SCALES = {'10k': 10_000, '100k': 100_000, '1m': 1_000_000}
CHUNK = 20_000
STOCK = 10_000
BRANDS = 50
CATEGORIES = 20
GENERATED_TABLES = [
    'brands', 'categories', 'products', 'skus', 'inventories', 'inventory_movements',
    'members', 'member_addresses', 'reviews', 'orders', 'order_items', 'payments',
    'deliveries', 'checkout_idempotency_results', 'subscription_plans', 'plan_versions',
    'plan_items', 'plan_version_delivery_cycles', 'pets', 'billing_payment_methods',
    'subscriptions', 'subscription_snapshots', 'subscription_snapshot_items',
    'subscription_schedules', 'subscription_shipping_snapshots']


class Stop(RuntimeError):
    pass


def run(args, stdin=None, check=True):
    result = subprocess.run(args, input=stdin, capture_output=True)
    if check and result.returncode:
        detail = result.stderr.decode('utf-8', 'replace').strip().splitlines()[-1:] or ['']
        raise Stop(f'{args[0]} {args[1] if len(args) > 1 else ""} failed (exit {result.returncode}): {detail[0]}')
    return result


def mysql(name, sql, database=DATABASE):
    shell = ('MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -h127.0.0.1 -uroot --batch --skip-column-names '
             f'--default-character-set=utf8mb4 {database}')
    result = run(['docker', 'exec', '-i', name, 'sh', '-c', shell], stdin=sql.encode('utf-8'))
    return result.stdout.decode('utf-8').strip()


def rows(output):
    return [line.split('\t') for line in output.splitlines() if line]


def guard(name):
    if not NAME_PATTERN.fullmatch(name):
        raise Stop('container name must match pawcycle-perfgen-<suffix>')
    raw = run(['docker', 'inspect', name]).stdout
    info = json.loads(raw)[0]
    if info['Config']['Labels'].get('pawcycle.purpose') != 'perf-synthetic-data':
        raise Stop('container is not a perfgen disposable database (label missing)')
    for mount in info.get('Mounts', []):
        if mount.get('Destination') == '/var/lib/mysql':
            anonymous = mount.get('Type') == 'volume' and re.fullmatch(r'[0-9a-f]{64}', mount.get('Name', ''))
            if not anonymous:
                raise Stop('/var/lib/mysql must be an anonymous volume; named volumes are refused')
    for mount in info['HostConfig'].get('Tmpfs') or {}:
        if mount != '/var/lib/mysql':
            raise Stop('unexpected tmpfs mount')
    if info['HostConfig'].get('PortBindings'):
        raise Stop('perfgen database must not publish ports')
    return info


def migration_files():
    files = sorted(MIGRATIONS.glob('V*__*.sql'), key=lambda path: int(path.name[1:].split('__')[0]))
    if not files:
        raise Stop('no migrations found')
    return files


def apply_migrations(name):
    files = migration_files()
    for path in files:
        mysql(name, path.read_text(encoding='utf-8'))
    return int(files[-1].name[1:].split('__')[0])


def wait_ready(name, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        # TCP succeeds only after the entrypoint's skip-networking init server is gone.
        probe = subprocess.run(['docker', 'exec', name, 'sh', '-c',
                                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT 1" ' + DATABASE],
                               capture_output=True)
        if probe.returncode == 0:
            return
        time.sleep(2)
    raise Stop('MySQL did not become ready')


def cmd_up(args):
    if not NAME_PATTERN.fullmatch(args.name):
        raise Stop('container name must match pawcycle-perfgen-<suffix>')
    started = time.monotonic()
    run(['docker', 'run', '-d', '--name', args.name, '--label', LABEL,
         '--cpus', str(args.cpus), '--memory', args.memory,
         '--mount', 'type=volume,dst=/var/lib/mysql',
         '-e', f'MYSQL_ROOT_PASSWORD={secrets.token_urlsafe(24)}', '-e', f'MYSQL_DATABASE={DATABASE}',
         IMAGE, '--character-set-server=utf8mb4', '--collation-server=utf8mb4_0900_ai_ci'])
    guard(args.name)
    wait_ready(args.name)
    version = apply_migrations(args.name)
    tables = mysql(args.name, f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{DATABASE}'")
    print(json.dumps({'container': args.name, 'image': IMAGE, 'migration_max': version,
                      'tables': int(tables), 'seconds': round(time.monotonic() - started, 1)}))


def cmd_reset(args):
    guard(args.name)
    mysql(args.name, f'DROP DATABASE IF EXISTS {WORK}; DROP DATABASE {DATABASE}; '
                     f'CREATE DATABASE {DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;', 'mysql')
    version = apply_migrations(args.name)
    print(json.dumps({'container': args.name, 'reset': True, 'migration_max': version}))


def cmd_down(args):
    info = guard(args.name)
    volumes = [m['Name'] for m in info.get('Mounts', []) if m.get('Type') == 'volume']
    run(['docker', 'rm', '-f', '-v', args.name])
    container_gone = run(['docker', 'inspect', args.name], check=False).returncode != 0
    volumes_gone = all(run(['docker', 'volume', 'inspect', v], check=False).returncode != 0 for v in volumes)
    print(json.dumps({'container': args.name, 'container_removed': container_gone,
                      'anonymous_volumes_removed': volumes_gone, 'volume_count': len(volumes)}))
    if not (container_gone and volumes_gone):
        raise Stop('cleanup incomplete')


def counts_for(n):
    products = n // 10
    return {'members': n, 'member_addresses': n, 'products': products, 'skus': products * 2,
            'inventories': products * 2, 'orders': n, 'payments': n, 'checkout_idempotency_results': n,
            'subscriptions': n // 2, 'subscription_snapshots': n // 2, 'subscription_snapshot_items': n // 2,
            'subscription_schedules': n // 2, 'subscription_shipping_snapshots': n // 2, 'pets': n // 2,
            'billing_payment_methods': n // 2, 'subscription_plans': n // 1000, 'plan_versions': n // 1000,
            'plan_items': n // 1000, 'plan_version_delivery_cycles': 3 * (n // 1000),
            'brands': BRANDS, 'categories': CATEGORIES}


def chunks(total):
    for start in range(1, total + 1, CHUNK):
        yield start, min(start + CHUNK - 1, total)


def h(seed, tag, expr):
    return f"CRC32(CONCAT('{seed}:{tag}:', {expr}))"


def steps(seed, n, as_of, schedule_start):
    """Yield (step, sql) in FK order. All randomness is CRC32(seed:tag:key)."""
    p = n // 10
    s = 2 * p
    plans = n // 1000
    subs = n // 2
    history = f"TIMESTAMP('{as_of}')"
    old = f"{history} - INTERVAL 400 DAY"
    seq = f'{WORK}.seq'
    review_hash = h(seed, 'review', "CONCAT(o.member_id, ':', k.product_id)")

    yield 'brands', (
        "INSERT INTO brands(name, slug, logo_url, active, display_order) "
        f"SELECT CONCAT('합성 브랜드 ', n), CONCAT('syn-brand-', n), NULL, 1, n FROM {seq} WHERE n <= {BRANDS} ORDER BY n;")
    yield 'categories', (
        "INSERT INTO categories(parent_id, name, slug, display_order, active) "
        f"SELECT NULL, CONCAT('합성 카테고리 ', n), CONCAT('syn-category-', n), n, 1 FROM {seq} WHERE n <= {CATEGORIES} ORDER BY n;")
    for a, b in chunks(p):
        yield 'products', (
            "INSERT INTO products(id, brand_id, category_id, name, short_description, description, pet_type, "
            "thumbnail_url, display_status, catalog_key) "
            "SELECT x.n, br.id, ca.id, CONCAT('합성 상품 ', x.n), CONCAT('합성 데이터 상품 ', x.n), NULL, "
            "IF(MOD(x.h, 2) = 0, 'DOG', 'CAT'), NULL, IF(MOD(x.h DIV 3, 20) = 0, 'INACTIVE', 'PUBLIC'), "
            "CONCAT('syn-product-', x.n) "
            f"FROM (SELECT n, {h(seed, 'product', 'n')} h FROM {seq} WHERE n BETWEEN {a} AND {b}) x "
            f"JOIN brands br ON br.slug = CONCAT('syn-brand-', 1 + MOD(x.h DIV 7, {BRANDS})) "
            f"JOIN categories ca ON ca.slug = CONCAT('syn-category-', 1 + MOD(x.h DIV 401, {CATEGORIES})) ORDER BY x.n;")
    for a, b in chunks(s):
        yield 'skus', (
            "INSERT INTO skus(id, product_id, sku_code, name, price, compare_at_price, subscribable, display_order, status) "
            "SELECT x.n, (x.n + 1) DIV 2, CONCAT('SYN-SKU-', x.n), CONCAT('옵션 ', x.v), x.price, "
            "IF(MOD(x.h DIV 5, 4) = 0, x.price + 1000 * (1 + MOD(x.h DIV 11, 10)), NULL), x.v = 1, x.v - 1, "
            "IF(x.v = 2 AND MOD(x.h DIV 13, 10) = 0, 'INACTIVE', 'ACTIVE') "
            f"FROM (SELECT n, 2 - MOD(n, 2) v, {h(seed, 'sku', 'n')} h, "
            f"(50 + MOD({h(seed, 'sku', 'n')}, 750)) * 100 price FROM {seq} WHERE n BETWEEN {a} AND {b}) x ORDER BY x.n;")
    for a, b in chunks(n):
        yield 'members', (
            "INSERT INTO members(id, email, password_hash, role, default_address_id) "
            f"SELECT n, CONCAT('syn-member-', n, '@perf.invalid'), 'synthetic-no-login', 'USER', NULL "
            f"FROM {seq} WHERE n BETWEEN {a} AND {b} ORDER BY n;")
        yield 'member_addresses', (
            "INSERT INTO member_addresses(id, member_id, name, recipient_name, recipient_phone, postal_code, "
            "address_line1, address_line2, created_at, updated_at) "
            f"SELECT n, n, '기본 배송지', CONCAT('합성 수령인 ', n), '000-0000-0000', "
            f"LPAD(MOD({h(seed, 'address', 'n')}, 100000), 5, '0'), CONCAT('합성 주소 ', n), NULL, {old}, {old} "
            f"FROM {seq} WHERE n BETWEEN {a} AND {b} ORDER BY n;")
        yield 'members.default_address', (
            f"UPDATE members SET default_address_id = id WHERE id BETWEEN {a} AND {b};")
    # Order lines are staged once so items, amounts and the inventory ledger agree.
    for a, b in chunks(n):
        yield 'order_lines', (
            f"INSERT INTO {WORK}.lines(order_id, line_no, sku_id, quantity) "
            "SELECT x.n, 1, 1 + MOD(x.h, {s}), 1 + MOD(x.h DIV 13, 3) FROM "
            f"(SELECT n, {h(seed, 'order', 'n')} h FROM {seq} WHERE n BETWEEN {a} AND {b}) x "
            "UNION ALL "
            f"SELECT x.n, 2, 1 + MOD(MOD(x.h, {s}) + {s // 2}, {s}), 1 + MOD(x.h DIV 17, 3) FROM "
            f"(SELECT n, {h(seed, 'order', 'n')} h FROM {seq} WHERE n BETWEEN {a} AND {b}) x "
            "WHERE MOD(x.h DIV 11, 3) = 0;".replace('{s}', str(s)))
        yield 'orders', (
            "INSERT INTO orders(id, order_number, member_id, source, status, original_amount, discount_amount, "
            "shipping_fee, payment_amount, recipient_name, recipient_phone, postal_code, address_line1, address_line2, "
            "created_at, paid_at) "
            "SELECT x.n, CONCAT('SYN-', x.n), x.member_id, 'ONE_TIME', x.status, t.amount, 0, 0, t.amount, "
            "ad.recipient_name, ad.recipient_phone, ad.postal_code, ad.address_line1, ad.address_line2, x.created_at, "
            "IF(x.status = 'PAID', x.created_at + INTERVAL 60 SECOND, NULL) "
            f"FROM (SELECT n, 1 + MOD({h(seed, 'order', 'n')} DIV 101, {n}) member_id, "
            f"CASE WHEN MOD({h(seed, 'order', 'n')} DIV 7, 100) < 85 THEN 'PAID' "
            f"WHEN MOD({h(seed, 'order', 'n')} DIV 7, 100) < 95 THEN 'PAYMENT_FAILED' ELSE 'EXPIRED' END status, "
            f"{history} - INTERVAL FLOOR(({n} - n) * 31536000 / {n}) SECOND - INTERVAL 1 DAY created_at "
            f"FROM {seq} WHERE n BETWEEN {a} AND {b}) x "
            f"JOIN (SELECT l.order_id, SUM(k.price * l.quantity) amount FROM {WORK}.lines l JOIN skus k ON k.id = l.sku_id "
            f"WHERE l.order_id BETWEEN {a} AND {b} GROUP BY l.order_id) t ON t.order_id = x.n "
            "JOIN member_addresses ad ON ad.id = x.member_id ORDER BY x.n;")
        yield 'order_items', (
            "INSERT INTO order_items(order_id, sku_id, snapshot_quality, sku_code_snapshot, product_name_snapshot, "
            "sku_name_snapshot, unit_price, quantity, line_amount) "
            "SELECT l.order_id, l.sku_id, 'FULL', k.sku_code, pr.name, k.name, k.price, l.quantity, k.price * l.quantity "
            f"FROM {WORK}.lines l JOIN skus k ON k.id = l.sku_id JOIN products pr ON pr.id = k.product_id "
            f"WHERE l.order_id BETWEEN {a} AND {b} ORDER BY l.order_id, l.line_no;")
        yield 'payments', (
            "INSERT INTO payments(id, order_id, type, provider, status, provider_status, amount, provider_order_id, "
            "payment_key, idempotency_key, attempt_no, failure_code, failure_message, requested_at, approved_at, "
            "failed_at, expires_at, created_at, reconciliation_attempts, last_reconciled_at) "
            "SELECT o.id, o.id, 'NORMAL', 'TOSS', IF(o.status = 'PAID', 'SUCCEEDED', 'FAILED'), "
            "CASE o.status WHEN 'PAID' THEN 'DONE' WHEN 'PAYMENT_FAILED' THEN 'ABORTED' ELSE 'EXPIRED' END, "
            "o.payment_amount, CONCAT('SYN-TOSS-', o.id), IF(o.status = 'PAID', CONCAT('syn-paykey-', o.id), NULL), "
            "CONCAT('syn-pay-', o.id), 1, "
            "CASE o.status WHEN 'PAID' THEN NULL WHEN 'PAYMENT_FAILED' THEN 'TOSS_REJECTED' ELSE 'CHECKOUT_EXPIRED' END, "
            "NULL, o.created_at, o.paid_at, "
            "CASE o.status WHEN 'PAID' THEN NULL WHEN 'PAYMENT_FAILED' THEN o.created_at + INTERVAL 60 SECOND "
            "ELSE o.created_at + INTERVAL 30 MINUTE END, o.created_at + INTERVAL 30 MINUTE, o.created_at, 0, NULL "
            f"FROM orders o WHERE o.id BETWEEN {a} AND {b} ORDER BY o.id;")
        yield 'checkout_idempotency_results', (
            "INSERT INTO checkout_idempotency_results(member_id, idempotency_key, order_id, payment_id, created_at, "
            "request_fingerprint, request_cart_version) "
            "SELECT o.member_id, CONCAT('syn-checkout-', o.id), o.id, o.id, o.created_at, NULL, NULL "
            f"FROM orders o WHERE o.id BETWEEN {a} AND {b} ORDER BY o.id;")
        yield 'deliveries', (
            "INSERT INTO deliveries(order_id, status, carrier_code, tracking_number, failure_reason, shipped_at, "
            "delivered_at, failed_at, cancelled_at) "
            "SELECT x.id, x.status, IF(x.status = 'PREPARING', NULL, 'SYN'), "
            "IF(x.status = 'PREPARING', NULL, CONCAT('SYN-TRK-', x.id)), NULL, "
            "IF(x.status = 'PREPARING', NULL, x.paid_at + INTERVAL 1 DAY), "
            "IF(x.status = 'DELIVERED', x.paid_at + INTERVAL 3 DAY, NULL), NULL, NULL "
            "FROM (SELECT o.id, o.paid_at, CASE "
            f"WHEN o.paid_at + INTERVAL 3 DAY <= {history} AND MOD({h(seed, 'delivery', 'o.id')}, 10) >= 3 THEN 'DELIVERED' "
            f"WHEN o.paid_at + INTERVAL 1 DAY <= {history} AND MOD({h(seed, 'delivery', 'o.id')}, 10) >= 1 THEN 'SHIPPED' "
            f"ELSE 'PREPARING' END status FROM orders o WHERE o.status = 'PAID' AND o.id BETWEEN {a} AND {b}) x ORDER BY x.id;")
    # Inventory ledger per SKU: INBOUND from zero, then each order line in order-id order
    # reserves and is settled (DEDUCT on success, RELEASE on failure/expiry) before the next.
    for a, b in chunks(s):
        yield 'inventory_movements', (
            "INSERT INTO inventory_movements(sku_id, payment_id, type, quantity, available_before, available_after, "
            "reserved_before, reserved_after, created_at, cancellation_id, return_id, source_id) "
            "WITH l AS ("
            "SELECT i.order_id, i.sku_id, i.quantity q, o.status, o.created_at, o.paid_at, p.failed_at, p.id pid, "
            f"{STOCK} - COALESCE(SUM(IF(o.status = 'PAID', i.quantity, 0)) OVER ("
            "PARTITION BY i.sku_id ORDER BY i.order_id ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0) avail "
            "FROM order_items i JOIN orders o ON o.id = i.order_id JOIN payments p ON p.order_id = o.id "
            f"WHERE i.sku_id BETWEEN {a} AND {b}), "
            "e AS ("
            f"SELECT k.id sku_id, NULL pid, 'INBOUND' type, {STOCK} q, 0 ab, {STOCK} aa, 0 rb, 0 ra, {old} at_time, "
            f"0 ord, 0 rank_no FROM skus k WHERE k.id BETWEEN {a} AND {b} "
            "UNION ALL SELECT sku_id, pid, 'RESERVE', q, avail, avail - q, 0, q, created_at, order_id, 1 FROM l "
            "UNION ALL SELECT sku_id, pid, IF(status = 'PAID', 'DEDUCT', 'RELEASE'), q, avail - q, "
            "IF(status = 'PAID', avail - q, avail), q, 0, IF(status = 'PAID', paid_at, failed_at), order_id, 2 FROM l) "
            "SELECT sku_id, pid, type, q, ab, aa, rb, ra, at_time, NULL, NULL, NULL FROM e "
            "ORDER BY sku_id, ord, rank_no;")
        yield 'inventories', (
            "INSERT INTO inventories(sku_id, available_quantity, reserved_quantity, version) "
            "SELECT m.sku_id, SUM(m.available_after - m.available_before), SUM(m.reserved_after - m.reserved_before), "
            "SUM(m.type <> 'INBOUND') FROM inventory_movements m "
            f"WHERE m.sku_id BETWEEN {a} AND {b} GROUP BY m.sku_id ORDER BY m.sku_id;")
    # Reviews only for DELIVERED purchases (one per member/product), a deterministic 60% subset.
    for a, b in chunks(n):
        yield 'reviews', (
            "INSERT INTO reviews(product_id, member_id, rating, content, visible, created_at, updated_at) "
            "SELECT x.product_id, x.member_id, "
            "ELT(1 + MOD(x.h DIV 3, 10), 1, 2, 3, 4, 4, 4, 5, 5, 5, 5), CONCAT('합성 리뷰 ', x.member_id, '-', x.product_id), "
            f"MOD(x.h DIV 17, 20) <> 0, LEAST(x.delivered_at + INTERVAL 1 DAY + INTERVAL MOD(x.h DIV 19, 604800) SECOND, {history}), "
            f"LEAST(x.delivered_at + INTERVAL 1 DAY + INTERVAL MOD(x.h DIV 19, 604800) SECOND, {history}) "
            "FROM (SELECT o.member_id, k.product_id, MIN(d.delivered_at) delivered_at, "
            f"{review_hash} h "
            "FROM orders o JOIN order_items i ON i.order_id = o.id JOIN skus k ON k.id = i.sku_id "
            "JOIN deliveries d ON d.order_id = o.id AND d.status = 'DELIVERED' "
            f"WHERE o.member_id BETWEEN {a} AND {b} GROUP BY o.member_id, k.product_id) x "
            "WHERE MOD(x.h, 10) < 6 ORDER BY x.member_id, x.product_id;")
    # Subscription plans: one per evenly spaced PUBLIC product, subscribable first SKU, cycles 2/4/8.
    yield 'subscription_plans', (
        f"INSERT INTO {WORK}.plan_source(plan_id, sku_id, pet_type, price) "
        "SELECT x.k, 2 * pub.id - 1, pub.pet_type, k.price FROM "
        f"(SELECT n k FROM {seq} WHERE n <= {plans}) x "
        "JOIN (SELECT id, pet_type, ROW_NUMBER() OVER (ORDER BY id) rn, COUNT(*) OVER () cnt "
        "FROM products WHERE display_status = 'PUBLIC') pub "
        f"ON pub.rn = 1 + (x.k - 1) * FLOOR(pub.cnt / {plans}) "
        "JOIN skus k ON k.id = 2 * pub.id - 1 ORDER BY x.k; "
        "INSERT INTO subscription_plans(id, name, target_pet_type, on_sale, sale_starts_on, sale_ends_on, "
        "current_plan_version_id, plan_key) "
        f"SELECT plan_id, CONCAT('합성 정기배송 플랜 ', plan_id), pet_type, 1, NULL, NULL, NULL, CONCAT('syn-plan-', plan_id) "
        f"FROM {WORK}.plan_source ORDER BY plan_id; "
        "INSERT INTO plan_versions(id, plan_id, package_price_krw, is_migration_only) "
        f"SELECT plan_id, plan_id, price, 0 FROM {WORK}.plan_source ORDER BY plan_id; "
        "INSERT INTO plan_items(plan_version_id, sku_id, quantity) "
        f"SELECT plan_id, sku_id, 1 FROM {WORK}.plan_source ORDER BY plan_id; "
        "INSERT INTO plan_version_delivery_cycles(plan_version_id, delivery_cycle_weeks) "
        f"SELECT ps.plan_id, c.w FROM {WORK}.plan_source ps "
        "JOIN (SELECT 2 w UNION ALL SELECT 4 UNION ALL SELECT 8) c ORDER BY ps.plan_id, c.w; "
        "UPDATE subscription_plans SET current_plan_version_id = id WHERE plan_key LIKE 'syn-plan-%';")
    for a, b in chunks(subs):
        sub = (f"(SELECT n, ps.sku_id, ps.pet_type, ps.price, ps.plan_id, "
               f"ELT(1 + MOD(z.h DIV 5, 3), 2, 4, 8) cycle, "
               f"CASE MOD(z.h DIV 11, 10) WHEN 0 THEN 'PAUSED' WHEN 1 THEN 'CANCELED' ELSE 'ACTIVE' END status, z.h "
               f"FROM (SELECT n, {h(seed, 'subscription', 'n')} h FROM {seq} WHERE n BETWEEN {a} AND {b}) z "
               f"JOIN {WORK}.plan_source ps ON ps.plan_id = 1 + MOD(z.h, {plans}))")
        yield 'pets', (
            "INSERT INTO pets(id, member_id, name, pet_type, breed, weight_kg) "
            f"SELECT n, n, '합성 반려동물', pet_type, NULL, NULL FROM {sub} y ORDER BY n;")
        yield 'billing_payment_methods', (
            "INSERT INTO billing_payment_methods(id, member_id, provider, customer_key, billing_key, status, created_at, revoked_at) "
            f"SELECT n, n, 'TOSS', CONCAT('syn-customer-', n), CONCAT('syn-billing-', n), 'ACTIVE', {old}, NULL "
            f"FROM {seq} WHERE n BETWEEN {a} AND {b} ORDER BY n;")
        yield 'subscriptions', (
            "INSERT INTO subscriptions(id, member_id, sku_id, quantity, delivery_cycle_weeks, created_date, next_order_date, "
            "pet_id, status, version, current_snapshot_id, legacy_api_visible, runtime_managed) "
            "SELECT n, n, sku_id, 1, cycle, "
            f"DATE('{schedule_start}') + INTERVAL MOD(h DIV 7, cycle * 7) DAY - INTERVAL cycle WEEK, "
            f"DATE('{schedule_start}') + INTERVAL MOD(h DIV 7, cycle * 7) DAY, n, status, 0, NULL, 0, 1 "
            f"FROM {sub} y ORDER BY n;")
        # Same as SubscriptionCreationApplicationService.create(): quantity 1, snapshot total =
        # PlanVersion.package_price_krw, snapshot items copied from plan_items.
        yield 'subscription_snapshots', (
            "INSERT INTO subscription_snapshots(id, subscription_id, source_plan_version_id, package_total_krw, delivery_cycle_weeks) "
            "SELECT y.n, y.n, y.plan_id, pv.package_price_krw, y.cycle "
            f"FROM {sub} y JOIN plan_versions pv ON pv.id = y.plan_id ORDER BY y.n; "
            "INSERT INTO subscription_snapshot_items(snapshot_id, sku_id, quantity) "
            "SELECT y.n, pi.sku_id, pi.quantity "
            f"FROM {sub} y JOIN plan_items pi ON pi.plan_version_id = y.plan_id ORDER BY y.n, pi.sku_id; "
            f"UPDATE subscriptions SET current_snapshot_id = id WHERE id BETWEEN {a} AND {b};")
        yield 'subscription_schedules', (
            "INSERT INTO subscription_schedules(id, subscription_id, scheduled_date, status, effective_snapshot_id, hold_reason) "
            "SELECT s.id, s.id, s.next_order_date, "
            "CASE s.status WHEN 'ACTIVE' THEN 'SCHEDULED' WHEN 'PAUSED' THEN 'HELD' ELSE 'CANCELED' END, NULL, NULL "
            f"FROM subscriptions s WHERE s.id BETWEEN {a} AND {b} ORDER BY s.id; "
            "INSERT INTO subscription_shipping_snapshots(subscription_id, recipient_name, recipient_phone, postal_code, "
            "address_line1, address_line2, updated_at) "
            "SELECT s.id, ad.recipient_name, ad.recipient_phone, ad.postal_code, ad.address_line1, ad.address_line2, "
            f"{old} FROM subscriptions s JOIN member_addresses ad ON ad.id = s.member_id "
            f"WHERE s.id BETWEEN {a} AND {b} ORDER BY s.id;")


PRECONDITION_TABLES = ['members', 'products', 'skus', 'orders', 'payments', 'reviews', 'subscriptions',
                       'subscription_plans', 'inventory_movements']


def cmd_generate(args):
    guard(args.name)
    n = SCALES[args.scale]
    as_of = dt.datetime.strptime(args.as_of, '%Y-%m-%d %H:%M:%S').strftime('%Y-%m-%d %H:%M:%S')
    schedule_start = dt.date.fromisoformat(args.schedule_start).isoformat()
    if dt.date.fromisoformat(schedule_start) <= dt.datetime.fromisoformat(as_of).date():
        raise Stop('--schedule-start must be after --as-of so no subscription schedule is already due')
    occupied = [t for t in PRECONDITION_TABLES
                if mysql(args.name, f'SELECT EXISTS(SELECT 1 FROM {t})') != '0']
    if occupied:
        raise Stop(f'target schema is not empty: {", ".join(occupied)}')
    migration = int(migration_files()[-1].name[1:].split('__')[0])
    sha = run(['git', '-C', str(ROOT), 'rev-parse', 'HEAD']).stdout.decode().strip()
    mysql(args.name, (
        f"CREATE DATABASE IF NOT EXISTS {WORK}; "
        f"CREATE TABLE IF NOT EXISTS {WORK}.runs(id INT AUTO_INCREMENT PRIMARY KEY, seed BIGINT NOT NULL, "
        "scale VARCHAR(8) NOT NULL, n INT NOT NULL, as_of DATETIME NOT NULL, schedule_start DATE NOT NULL, "
        "migration_max INT NOT NULL, source_sha CHAR(40) NOT NULL, started_at DATETIME(6) NOT NULL, finished_at DATETIME(6) NULL); "
        f"CREATE TABLE {WORK}.digits(d TINYINT PRIMARY KEY); "
        f"INSERT INTO {WORK}.digits VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9); "
        f"CREATE TABLE {WORK}.seq(n INT UNSIGNED PRIMARY KEY); "
        f"INSERT INTO {WORK}.seq SELECT 1 + a.d + 10*b.d + 100*c.d + 1000*d.d + 10000*e.d + 100000*f.d "
        f"FROM {WORK}.digits a, {WORK}.digits b, {WORK}.digits c, {WORK}.digits d, {WORK}.digits e, {WORK}.digits f "
        f"WHERE 1 + a.d + 10*b.d + 100*c.d + 1000*d.d + 10000*e.d + 100000*f.d <= {n}; "
        f"CREATE TABLE {WORK}.lines(order_id INT UNSIGNED, line_no TINYINT, sku_id INT UNSIGNED, quantity INT, "
        "PRIMARY KEY(order_id, line_no)); "
        f"CREATE TABLE {WORK}.plan_source(plan_id INT PRIMARY KEY, sku_id BIGINT, pet_type VARCHAR(20), price BIGINT); "
        f"INSERT INTO {WORK}.runs(seed, scale, n, as_of, schedule_start, migration_max, source_sha, started_at) "
        f"VALUES ({args.seed}, '{args.scale}', {n}, '{as_of}', '{schedule_start}', {migration}, '{sha}', NOW(6));"))
    timings = {}
    started = time.monotonic()
    for step, sql in steps(args.seed, n, as_of, schedule_start):
        begin = time.monotonic()
        mysql(args.name, sql)
        timings[step] = round(timings.get(step, 0) + time.monotonic() - begin, 2)
    begin = time.monotonic()
    mysql(args.name, f"ANALYZE TABLE {', '.join(GENERATED_TABLES)};")
    timings['analyze'] = round(time.monotonic() - begin, 2)
    mysql(args.name, (
        f"DROP TABLE {WORK}.seq, {WORK}.digits, {WORK}.lines, {WORK}.plan_source; "
        f"UPDATE {WORK}.runs SET finished_at = NOW(6) ORDER BY id DESC LIMIT 1;"))
    counts = table_counts(args.name)
    print(json.dumps({'container': args.name, 'seed': args.seed, 'scale': args.scale, 'n': n, 'as_of': as_of,
                      'schedule_start': schedule_start, 'migration_max': migration, 'source_sha': sha,
                      'seconds': round(time.monotonic() - started, 1), 'step_seconds': timings,
                      'rows': counts}, ensure_ascii=False))


def table_counts(name):
    sql = ' UNION ALL '.join(f"SELECT '{t}', COUNT(*) FROM {t}" for t in GENERATED_TABLES) + ';'
    return {table: int(count) for table, count in rows(mysql(name, sql))}


def manifest_checks(name, run_row):
    n = int(run_row['n'])
    expected = counts_for(n)
    synthetic = {
        'members': "SELECT COUNT(*) FROM members WHERE email LIKE 'syn-member-%@perf.invalid'",
        'brands': "SELECT COUNT(*) FROM brands WHERE slug LIKE 'syn-brand-%'",
        'categories': "SELECT COUNT(*) FROM categories WHERE slug LIKE 'syn-category-%'",
        'products': "SELECT COUNT(*) FROM products WHERE catalog_key LIKE 'syn-product-%'",
        'skus': "SELECT COUNT(*) FROM skus WHERE sku_code LIKE 'SYN-SKU-%'",
        'orders': "SELECT COUNT(*) FROM orders WHERE order_number LIKE 'SYN-%'",
        'subscription_plans': "SELECT COUNT(*) FROM subscription_plans WHERE plan_key LIKE 'syn-plan-%'",
    }
    results = []
    for table, want in expected.items():
        query = synthetic.get(table, f'SELECT COUNT(*) FROM {table}')
        got = int(mysql(name, query))
        results.append((f'manifest_count_{table}', 0 if got == want else abs(got - want), f'{got}/{want}'))
    extra = {
        'manifest_member_default_address_owned':
            "SELECT COUNT(*) FROM members m LEFT JOIN member_addresses a ON a.id = m.default_address_id "
            "WHERE m.email LIKE 'syn-member-%' AND (a.id IS NULL OR a.member_id <> m.id)",
        'manifest_order_has_1_to_2_items':
            "SELECT COUNT(*) FROM orders o WHERE o.order_number LIKE 'SYN-%' AND "
            "(SELECT COUNT(*) FROM order_items i WHERE i.order_id = o.id) NOT BETWEEN 1 AND 2",
        'manifest_inbound_per_sku':
            "SELECT COUNT(*) FROM skus k WHERE k.sku_code LIKE 'SYN-SKU-%' AND "
            "(SELECT COUNT(*) FROM inventory_movements m WHERE m.sku_id = k.id AND m.type = 'INBOUND') <> 1",
        'manifest_no_open_checkout_after_generation':
            "SELECT COUNT(*) FROM orders WHERE order_number LIKE 'SYN-%' AND status = 'PAYMENT_PENDING'",
        'manifest_runtime_subscription_has_pet_billing_shipping':
            "SELECT COUNT(*) FROM subscriptions s LEFT JOIN pets p ON p.id = s.pet_id AND p.member_id = s.member_id "
            "LEFT JOIN billing_payment_methods b ON b.active_member_id = s.member_id "
            "LEFT JOIN subscription_shipping_snapshots sh ON sh.subscription_id = s.id "
            "WHERE p.id IS NULL OR b.id IS NULL OR sh.subscription_id IS NULL",
        'manifest_no_schedule_due_at_generation':
            f"SELECT COUNT(*) FROM subscription_schedules WHERE scheduled_date <= DATE('{run_row['as_of']}')",
        'manifest_reviews_present':
            "SELECT (SELECT COUNT(*) FROM reviews) = 0",
        'manifest_work_tables_dropped':
            f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '{WORK}' AND table_name <> 'runs'",
    }
    for check, query in extra.items():
        results.append((check, int(mysql(name, query)), None))
    return results


def cmd_check(args):
    guard(args.name)
    invariant_rows = rows(mysql(args.name, INVARIANTS.read_text(encoding='utf-8')))
    results = [(row[0], int(row[1]), None) for row in invariant_rows]
    expected_invariants = len(re.findall(r"^SELECT '", INVARIANTS.read_text(encoding='utf-8'), re.M))
    if len(results) != expected_invariants:
        raise Stop(f'invariant output incomplete: {len(results)}/{expected_invariants}')
    if args.manifest:
        run_rows = rows(mysql(args.name, f"SELECT seed, scale, n, as_of FROM {WORK}.runs ORDER BY id DESC LIMIT 1"))
        if not run_rows:
            raise Stop('no generation run recorded; use --no-manifest for a schema without perfgen data')
        seed, scale, n, as_of = run_rows[0]
        results += manifest_checks(args.name, {'n': n, 'as_of': as_of})
    failed = [r for r in results if r[1] != 0]
    for check, violations, detail in results:
        print(json.dumps({'check': check, 'violations': violations, **({'detail': detail} if detail else {})}))
    print(json.dumps({'checks': len(results), 'failed': [r[0] for r in failed],
                      'verdict': 'PASS' if not failed else 'FAIL'}))
    if failed:
        raise SystemExit(1)


def cmd_fingerprint(args):
    guard(args.name)
    output = mysql(args.name, f"CHECKSUM TABLE {', '.join(GENERATED_TABLES)} EXTENDED;")
    checksums = {row[0].split('.', 1)[1]: row[1] for row in rows(output)}
    run_rows = rows(mysql(args.name, f"SELECT seed, scale, as_of, schedule_start FROM {WORK}.runs ORDER BY id DESC LIMIT 1"))
    print(json.dumps({'run': run_rows[0] if run_rows else None, 'checksums': checksums}, sort_keys=True))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='command', required=True)
    for command in ['up', 'generate', 'check', 'fingerprint', 'reset', 'down']:
        p = sub.add_parser(command)
        p.add_argument('--name', required=True)
        if command == 'up':
            p.add_argument('--cpus', type=float, default=2.0)
            p.add_argument('--memory', default='2g')
        if command == 'generate':
            p.add_argument('--scale', choices=sorted(SCALES), required=True)
            p.add_argument('--seed', type=int, required=True)
            p.add_argument('--as-of', default='2026-11-01 00:00:00')
            p.add_argument('--schedule-start', default='2030-01-01')
        if command == 'check':
            p.add_argument('--no-manifest', dest='manifest', action='store_false')
    args = parser.parse_args(argv)
    try:
        {'up': cmd_up, 'generate': cmd_generate, 'check': cmd_check, 'fingerprint': cmd_fingerprint,
         'reset': cmd_reset, 'down': cmd_down}[args.command](args)
    except Stop as error:
        print(json.dumps({'command': args.command, 'stop': str(error)}), file=sys.stderr)
        raise SystemExit(2)


if __name__ == '__main__':
    main()
