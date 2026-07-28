#!/usr/bin/env bash
set -Eeuo pipefail
set +x

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
MYSQL_IMAGE="mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d"
TEST_ID="ops020-test-${RANDOM}-$$"
NETWORK="${TEST_ID}-data"
VOLUME="${TEST_ID}-mysql"
MYSQL_CONTAINER="${TEST_ID}-mysql"
INIT_CONTAINER="${TEST_ID}-schema-init"
IMAGE_TAG="${TEST_ID}-backend"
LABEL="com.pawcycle.ops020.test=$TEST_ID"
DATABASE="ops020_fixture"
DB_USER="ops020_fixture_user"
DB_PASSWORD="ops020_fixture_db_password"
DB_ROOT_PASSWORD="ops020_fixture_root_password"
MEMBER_EMAIL="ops020-lifecycle@example.test"
MEMBER_PASSWORD="ops020-lifecycle-password-not-secret"
PASS_MESSAGE="PASS: production auth smoke member created"
ERROR_MESSAGE="ERROR: production auth smoke member maintenance failed"

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  set +e
  mapfile -t owned_containers < <(docker ps --all --quiet --filter "label=$LABEL")
  if (( ${#owned_containers[@]} > 0 )); then
    docker rm --force "${owned_containers[@]}" >/dev/null 2>&1
  fi
  docker image rm --force "$IMAGE_TAG" >/dev/null 2>&1
  docker volume rm "$VOLUME" >/dev/null 2>&1
  docker network rm "$NETWORK" >/dev/null 2>&1
  exit "$status"
}

die() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

require_output_absent() {
  local output="$1"
  local sensitive_value="$2"
  local category="$3"

  [[ "$output" != *"$sensitive_value"* ]] \
    || die "isolated one-shot output exposed $category"
}

mysql_query() {
  docker exec \
    --env "MYSQL_PWD=$DB_ROOT_PASSWORD" \
    "$MYSQL_CONTAINER" \
    mysql --batch --skip-column-names \
    --user=root "$DATABASE" --execute="$1"
}

wait_for_mysql() {
  local attempt
  for attempt in {1..60}; do
    if mysql_query "SELECT 1;" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  die "isolated MySQL did not become ready"
}

wait_for_backend() {
  local attempt
  for attempt in {1..120}; do
    if docker exec "$INIT_CONTAINER" curl --fail --silent \
      http://127.0.0.1:8080/api/products >/dev/null 2>&1; then
      return 0
    fi
    [[ "$(docker inspect --format '{{.State.Running}}' "$INIT_CONTAINER" 2>/dev/null)" == "true" ]] \
      || die "isolated schema initializer exited early"
    sleep 1
  done
  die "isolated schema initializer did not become healthy"
}

schema_fingerprint() {
  mysql_query "
    SELECT SHA2(GROUP_CONCAT(
      CONCAT(TABLE_NAME, ':', COLUMN_NAME, ':', COLUMN_TYPE, ':', IS_NULLABLE)
      ORDER BY TABLE_NAME, ORDINAL_POSITION SEPARATOR '|'
    ), 256)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = '$DATABASE';
  "
}

flyway_fingerprint() {
  mysql_query "
    SELECT CONCAT(COUNT(*), ':', COALESCE(MAX(installed_rank), 0), ':', COALESCE(SUM(checksum), 0))
    FROM flyway_schema_history
    WHERE success = 1;
  "
}

domain_counts() {
  mysql_query "
    SELECT CONCAT(
      (SELECT COUNT(*) FROM products), ':',
      (SELECT COUNT(*) FROM skus), ':',
      (SELECT COUNT(*) FROM subscriptions)
    );
  "
}

run_member_command() {
  local name="$1"
  local input="$2"
  local output
  local status
  set +e
  output="$(
    printf '%s' "$input" |
      docker run --rm --interactive \
        --name "$name" \
        --label "$LABEL" \
        --network "$NETWORK" \
        --env "SPRING_DATASOURCE_URL=jdbc:mysql://$MYSQL_CONTAINER:3306/$DATABASE" \
        --env "SPRING_DATASOURCE_USERNAME=$DB_USER" \
        --env "SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD" \
        --env SPRING_PROFILES_ACTIVE=production \
        --read-only \
        --tmpfs /tmp:size=64m,mode=1777 \
        --user pawcycle \
        --security-opt no-new-privileges:true \
        --cap-drop ALL \
        --memory 640m \
        --cpus 0.75 \
        --pids-limit 256 \
        --log-driver none \
        "$IMAGE_ID" \
        --spring.main.web-application-type=none \
        --pawcycle.maintenance.create-auth-smoke-member.enabled=true \
        --spring.flyway.enabled=false \
        2>&1
  )"
  status=$?
  set -e
  require_output_absent "$output" "$MEMBER_EMAIL" "member email"
  require_output_absent "$output" "$MEMBER_PASSWORD" "member password"
  require_output_absent "$output" "$DB_PASSWORD" "database password"
  require_output_absent "$output" "$DB_USER" "database username"
  require_output_absent "$output" "jdbc:mysql" "JDBC identifier"
  require_output_absent "$output" "$TEST_ID" "test resource identifier"
  require_output_absent "$output" "$NETWORK" "network identifier"
  require_output_absent "$output" "$IMAGE_ID" "image identifier"
  if docker container inspect "$name" >/dev/null 2>&1; then
    die "one-shot lifecycle Container remained after exit"
  fi
  COMMAND_OUTPUT="$output"
  COMMAND_STATUS="$status"
}

command -v docker >/dev/null 2>&1 || die "Docker is required"
[[ "$TEST_ID" == ops020-test-* ]] || die "isolated resource prefix is invalid"
trap cleanup EXIT INT TERM

docker network create --internal --label "$LABEL" "$NETWORK" >/dev/null
docker volume create --label "$LABEL" "$VOLUME" >/dev/null
docker run --detach \
  --name "$MYSQL_CONTAINER" \
  --label "$LABEL" \
  --network "$NETWORK" \
  --env "MYSQL_DATABASE=$DATABASE" \
  --env "MYSQL_USER=$DB_USER" \
  --env "MYSQL_PASSWORD=$DB_PASSWORD" \
  --env "MYSQL_ROOT_PASSWORD=$DB_ROOT_PASSWORD" \
  --volume "$VOLUME:/var/lib/mysql" \
  --log-driver none \
  "$MYSQL_IMAGE" >/dev/null
wait_for_mysql

RELEASE_SHA="$(git -C "$ROOT_DIR" rev-parse HEAD)"
docker build \
  --file "$SCRIPT_DIR/backend.Dockerfile" \
  --label "org.opencontainers.image.revision=$RELEASE_SHA" \
  --label "$LABEL" \
  --tag "$IMAGE_TAG" \
  "$ROOT_DIR" >/dev/null
IMAGE_ID="$(docker image inspect --format '{{.Id}}' "$IMAGE_TAG")"
[[ "$IMAGE_ID" == sha256:* ]] || die "isolated Backend image ID is invalid"

docker run --detach \
  --name "$INIT_CONTAINER" \
  --label "$LABEL" \
  --network "$NETWORK" \
  --env "SPRING_DATASOURCE_URL=jdbc:mysql://$MYSQL_CONTAINER:3306/$DATABASE" \
  --env "SPRING_DATASOURCE_USERNAME=$DB_USER" \
  --env "SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD" \
  --env SPRING_PROFILES_ACTIVE=production \
  --read-only \
  --tmpfs /tmp:size=64m,mode=1777 \
  --user pawcycle \
  --security-opt no-new-privileges:true \
  --cap-drop ALL \
  --memory 640m \
  --cpus 0.75 \
  --pids-limit 256 \
  --log-driver none \
  "$IMAGE_ID" >/dev/null
wait_for_backend
docker rm --force "$INIT_CONTAINER" >/dev/null

SCHEMA_BEFORE="$(schema_fingerprint)"
FLYWAY_BEFORE="$(flyway_fingerprint)"
[[ "$(domain_counts)" == "0:0:0" ]] || die "isolated fixture contains unexpected domain data"

run_member_command "${TEST_ID}-success" "$MEMBER_EMAIL"$'\n'"$MEMBER_PASSWORD"$'\n'
[[ "$COMMAND_STATUS" == "0" && "$COMMAND_OUTPUT" == "$PASS_MESSAGE" ]] \
  || die "isolated one-shot success contract failed"
MEMBER_STATE="$(mysql_query "SELECT CONCAT(id, ':', password_hash) FROM members WHERE email = '$MEMBER_EMAIL';")"
[[ -n "$MEMBER_STATE" && "$(mysql_query "SELECT COUNT(*) FROM members;")" == "1" ]] \
  || die "isolated one-shot did not create exactly one member"

run_member_command "${TEST_ID}-duplicate" "$MEMBER_EMAIL"$'\n'"different-fixture-password"$'\n'
[[ "$COMMAND_STATUS" != "0" && "$COMMAND_OUTPUT" == "$ERROR_MESSAGE" ]] \
  || die "duplicate member execution did not fail generically"
[[ "$(mysql_query "SELECT CONCAT(id, ':', password_hash) FROM members WHERE email = '$MEMBER_EMAIL';")" == "$MEMBER_STATE" ]] \
  || die "duplicate execution changed the existing member"

run_member_command "${TEST_ID}-input-failure" "$MEMBER_EMAIL"$'\n'
[[ "$COMMAND_STATUS" != "0" && "$COMMAND_OUTPUT" == "$ERROR_MESSAGE" ]] \
  || die "incomplete input did not fail generically"

[[ "$(schema_fingerprint)" == "$SCHEMA_BEFORE" ]] || die "one-shot lifecycle changed the schema"
[[ "$(flyway_fingerprint)" == "$FLYWAY_BEFORE" ]] || die "one-shot lifecycle changed Flyway history"
[[ "$(domain_counts)" == "0:0:0" ]] || die "one-shot lifecycle created non-member domain data"
[[ "$(mysql_query "SELECT COUNT(*) FROM members;")" == "1" ]] || die "failed lifecycle changed member count"

printf '%s\n' "OPS-020 isolated MySQL 8.4 and Backend image lifecycle passed"
