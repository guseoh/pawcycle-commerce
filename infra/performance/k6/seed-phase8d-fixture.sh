#!/usr/bin/env bash
set -euo pipefail

usage() { printf '%s\n' 'Usage: seed-phase8d-fixture.sh --member-email qa-foundation-004@<local-domain> --acknowledge-local-fixture YES [--reset-only]'; }
member_email=''; acknowledgement=''; reset_only=false
while (($#)); do
  case "$1" in
    --member-email) member_email="${2:-}"; shift 2 ;;
    --acknowledge-local-fixture) acknowledgement="${2:-}"; shift 2 ;;
    --reset-only) reset_only=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 64 ;;
  esac
done
if [[ "$acknowledgement" != 'YES' || ! "$member_email" =~ ^qa-foundation-004@[A-Za-z0-9.-]+$ ]]; then
  usage; exit 64
fi
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
local_root="$(cd -- "$script_dir/../.." && pwd)/local-integration"
if [[ ! -f "$local_root/.env.local" ]]; then
  printf '%s\n' 'Phase 8-D seed requires the local-integration .env.local file.' >&2; exit 1
fi
compose=(docker compose --env-file "$local_root/.env.local" -f "$local_root/compose.yaml")
member_count="$("${compose[@]}" exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM members WHERE email=\"'"$member_email"'\""')"
if [[ "$member_count" != '1' ]]; then
  printf '%s\n' 'Phase 8-D seed requires exactly one existing local QA-bootstrap member; no data was changed.' >&2; exit 1
fi
read -r -d '' reset_sql <<'SQL' || true
SET @member_id := (SELECT id FROM members WHERE email = '__MEMBER_EMAIL__');
SET @sku_id := (SELECT sku.id FROM skus sku JOIN products product ON product.id = sku.product_id WHERE product.name = '[QA FOUNDATION-004] 정기배송 사료' AND sku.name = '[QA FOUNDATION-004] 2kg');
DELETE item FROM order_items item JOIN orders o ON o.id = item.order_id WHERE o.member_id = @member_id AND o.order_number LIKE 'PERF-PH8-003-%';
DELETE FROM orders WHERE member_id = @member_id AND order_number LIKE 'PERF-PH8-003-%';
DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE member_id = @member_id) AND sku_id = @sku_id;
DELETE FROM wishlist_items WHERE member_id = @member_id AND product_id = (SELECT product_id FROM skus WHERE id = @sku_id);
DELETE FROM subscriptions WHERE member_id = @member_id AND sku_id = @sku_id AND created_date = '2000-01-01' AND next_order_date = '2030-01-01';
SQL
reset_sql="${reset_sql//__MEMBER_EMAIL__/$member_email}"
if [[ "$reset_only" == true ]]; then
  "${compose[@]}" exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' <<< "$reset_sql"
  printf '%s\n' 'Phase 8-D local fixture reset completed without printing credentials, sessions, tokens, rows, or IDs.'
  exit 0
fi
read -r -d '' seed_sql <<'SQL' || true
INSERT INTO carts(member_id,created_at,updated_at)
SELECT @member_id,'2000-01-01 00:00:00.000000','2000-01-01 00:00:00.000000'
WHERE NOT EXISTS (SELECT 1 FROM carts WHERE member_id=@member_id);
INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date)
SELECT @member_id,@sku_id,1,2,'2000-01-01','2030-01-01'
WHERE NOT EXISTS (SELECT 1 FROM subscriptions WHERE member_id=@member_id AND sku_id=@sku_id AND created_date='2000-01-01' AND next_order_date='2030-01-01');
INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,created_at,paid_at)
SELECT 'PERF-PH8-003-ORDER',@member_id,'ONE_TIME','PAID',19900.00,0.00,0.00,19900.00,'2000-01-01 00:00:00.000000','2000-01-01 00:00:00.000000'
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number='PERF-PH8-003-ORDER');
INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount)
SELECT o.id,@sku_id,'FULL',sku.sku_code,product.name,sku.name,sku.price,1,sku.price
FROM orders o JOIN skus sku ON sku.id=@sku_id JOIN products product ON product.id=sku.product_id
WHERE o.order_number='PERF-PH8-003-ORDER' AND NOT EXISTS (SELECT 1 FROM order_items WHERE order_id=o.id AND sku_id=@sku_id);
SQL
seed_sql="${seed_sql//__MEMBER_EMAIL__/$member_email}"
"${compose[@]}" exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' <<< "$reset_sql$seed_sql"
printf '%s\n' 'Phase 8-D local fixture seed completed without printing credentials, sessions, tokens, rows, or IDs.'
