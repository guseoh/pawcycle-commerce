# OPS-OBS-001 Production Observability

## 상태와 경계

작업 ID `OPS-OCI-005B`, 등급 `고위험`, 실행 구분 `저장소 변경`의 지속 Runbook이다. 이 저장소 변경은 OCI·Production·Secret·Managed MySQL을 실행하지 않았으므로 Production Observability는 **Production Verified가 아니다**. Managed Observability, Alertmanager, centralized logging/Loki와 OpenTelemetry 전환은 범위 밖이다.

현재 Production은 OCI `app01`의 Backend/Frontend/Nginx와 별도 OCI Managed MySQL이다. 초기 Observability는 같은 `app01`에서 다음 세 lifecycle을 별도 Compose project로 운영한다.

- Application: 기존 `backend`, `frontend`, `proxy` release project
- Metrics proxy: `infra/production-metrics-proxy`의 `metrics-proxy` 하나
- Observability: `infra/production-observability`의 Prometheus와 Grafana

Metrics proxy와 Observability는 Application release를 recreate·wait·stop하지 않는다. Prometheus는 Application Docker `app` network에서 `metrics-proxy:9464`를 scrape하며 metrics-proxy host port는 없다. Prometheus와 Grafana는 별도 internal `observability` network를 공유하고, Grafana는 Application `app` network에 직접 연결하지 않는다. Prometheus와 Grafana UI만 `127.0.0.1`에 publish한다.

별도 Observability Compute는 **Deferred — Evidence Triggered**다. 실제 적용 후 CPU·memory·disk·application HTTP latency/error-rate overhead가 측정되거나 monitoring failure-domain 분리가 필요할 때 별도 결정으로 재검토한다.

## Repository 검증

저장소 root에서 다음을 실행한다. 이 명령은 OCI/Production에 연결하지 않는다.

```bash
bash infra/production-observability/validate-observability.sh
bash infra/production-metrics-proxy/test-metrics-proxy.sh
bash infra/production/test-observability-application-identity.sh
bash infra/production/test-diagnose-backend-state.sh
python infra/production/validate-production-contracts.py
bash infra/production/test-production-nginx.sh
bash infra/production/test-production-compose.sh
```

첫 두 명령은 pinned image, Observability stack, same-host Docker network, endpoint-only metrics-proxy와 Backend container 교체 후 동적 DNS 재해석을 검증한다. Application identity regression은 Runbook이 worktree와 root-owned control path를 다시 사용하지 않고, `opc`가 접근 가능한 archive source를 사용하는지 검증한다. Production Compose 검증은 active Compose가 `backend`/`frontend`/`proxy`만 소유하고 database service를 포함하지 않는지와 application release·HTTPS·volume lifecycle을 검증한다.

## 실제 운영 실행 전 조건

별도 고위험 실제 운영 승인이 있고, 승인된 merge SHA와 현재 `app01` 상태를 확인한 뒤에만 다음 절차를 준비한다. 이 Runbook은 `opc` SSH user가 실행하며 Docker/Compose와 root-owned runtime state를 읽을 때만 필요한 명령에 `sudo`를 붙인다.

- `pawcycle-production-app` network가 이미 존재하며 Application project가 소유한다. Observability project와 metrics-proxy project는 이 external network를 삭제하지 않는다.
- Application Compose의 service set은 `backend`, `frontend`, `proxy`이고 database service는 없다. OCI Managed MySQL lifecycle은 이 Runbook의 대상이 아니다.
- Backend는 host `:8080`을 publish하지 않고 Docker network에서만 `backend:8080`으로 접근한다.
- Metrics proxy는 host port를 publish하지 않고 `app` network에만 연결한다. `PAWCYCLE_METRICS_TARGET`은 `metrics-proxy:9464`로 runtime 주입한다.
- Prometheus는 `app`과 internal `observability` network에 연결하고 Grafana는 internal `observability` network에만 연결한다.
- Prometheus/Grafana published UI는 각각 `127.0.0.1`만 사용한다. Grafana admin user/password file은 보호된 runtime 경로에서만 읽고 값은 명령·환경 출력·Git에 넣지 않는다.
- Grafana credential file은 다음 경로의 regular non-symlink file이며 mode `0400`, numeric owner/group `472:472`여야 한다.

  ```text
  /opt/pawcycle/runtime/observability/grafana-admin-user
  /opt/pawcycle/runtime/observability/grafana-admin-password
  ```

- Prometheus/Grafana named volume은 기존 값을 유지하고 일반 `down`에서 삭제하지 않는다.
- Prometheus, Grafana, metrics-proxy image reference는 repository의 pinned digest와 일치해야 한다. local image가 없을 때만 정확한 `@sha256` reference를 pull하고 `linux/amd64`와 RepoDigest를 확인한 뒤 실제 Compose 적용은 `--pull never`로 수행한다.

Application control checkout `/opt/pawcycle/source/repo`의 HEAD·working tree를 관측성 적용 때문에 checkout/pull/reset/rebase하지 않는다. 승인 merge SHA object 준비를 위한 `git fetch --prune origin main`만 허용하며, fetch 전후 HEAD와 working tree 상태가 동일해야 한다. fetch 후에도 exact 승인 SHA object가 없으면 중단한다.

## 안전한 실행 source 준비

기존 root-owned sibling worktree를 만들지 않는다. worktree 생성이나 광범위한 소유권 일괄 변경은 사용하지 않는다. `opc`가 새로 만든 scoped runtime directory에 승인 SHA의 관측성 경로만 `git archive`로 추출하고, 추출 후 `opc`가 수정할 수 없으면서 non-owner container user가 directory를 traverse하고 regular file을 읽을 수 있도록 source 권한을 정규화한다. 이 source directory는 실행 중인 container의 bind mount가 유지되도록 두 project를 내릴 때까지 보존한다.

아래 블록은 독립적으로 실행할 수 있다. 이후 블록도 `APPROVED_SHA`와 `SOURCE_ROOT`를 다시 선언하므로 앞선 SSH shell의 local variable을 전제로 하지 않는다. `APPROVED_SHA`는 검토·병합이 끝난 **40자리 전체 merge commit SHA**를 넣는다.

```bash
set -Eeuo pipefail
umask 077

APP_CONTROL=/opt/pawcycle/source/repo
APPROVED_SHA='<approved-merge-sha>'
SOURCE_ROOT="/opt/pawcycle/observability-source/$APPROVED_SHA"

[[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ ]] || {
  printf 'invalid approved merge SHA\n' >&2
  exit 64
}

APP_HEAD_BEFORE="$(sudo git -C "$APP_CONTROL" rev-parse HEAD)"
APP_STATUS_BEFORE="$(sudo git -C "$APP_CONTROL" status --porcelain)"
sudo git -C "$APP_CONTROL" fetch --prune origin main

test "$(sudo git -C "$APP_CONTROL" rev-parse HEAD)" = "$APP_HEAD_BEFORE"
test "$(sudo git -C "$APP_CONTROL" status --porcelain)" = "$APP_STATUS_BEFORE"
sudo git -C "$APP_CONTROL" cat-file -e "${APPROVED_SHA}^{commit}"

sudo docker network inspect pawcycle-production-app >/dev/null
sudo test -f /opt/pawcycle/runtime/observability/grafana-admin-user
sudo test -f /opt/pawcycle/runtime/observability/grafana-admin-password
sudo test ! -L /opt/pawcycle/runtime/observability/grafana-admin-user
sudo test ! -L /opt/pawcycle/runtime/observability/grafana-admin-password
test "$(sudo stat -c '%u:%g %a' /opt/pawcycle/runtime/observability/grafana-admin-user)" = '472:472 400'
test "$(sudo stat -c '%u:%g %a' /opt/pawcycle/runtime/observability/grafana-admin-password)" = '472:472 400'

PROMETHEUS_IMAGE='prom/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69'
GRAFANA_IMAGE='grafana/grafana:13.1.3@sha256:ab5cb380e3ff3172d6c8bd2e7cfd31cce977d2881b260e1f5bc089bf0b759b43'
METRICS_PROXY_IMAGE='nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1'
for image in "$PROMETHEUS_IMAGE" "$GRAFANA_IMAGE" "$METRICS_PROXY_IMAGE"; do
  [[ "$image" =~ @sha256:[0-9a-f]{64}$ ]] || {
    printf 'image is not pinned by sha256 digest: %s\n' "$image" >&2
    exit 1
  }
  expected_digest="${image##*@}"
  if ! sudo docker image inspect "$image" >/dev/null 2>&1; then
    sudo docker pull "$image"
  fi
  test "$(sudo docker image inspect "$image" --format '{{.Os}}/{{.Architecture}}')" = 'linux/amd64'
  sudo docker image inspect "$image" --format '{{range .RepoDigests}}{{println .}}{{end}}' |
    grep -Fq "@$expected_digest"
done

if sudo test -e "$SOURCE_ROOT"; then
  test -d "$SOURCE_ROOT"
  test -r "$SOURCE_ROOT/.approved-sha"
  test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
  test "$(sudo stat -c '%U %a' "$SOURCE_ROOT")" = 'opc 555'
  test -z "$(find "$SOURCE_ROOT" -type d ! -perm 0555 -print -quit)"
  test -z "$(find "$SOURCE_ROOT" -type f ! -perm 0444 -print -quit)"
else
  if ! sudo test -d /opt/pawcycle/observability-source; then
    sudo install -d -o opc -g opc -m 0750 /opt/pawcycle/observability-source
  fi
  test -x /opt/pawcycle/observability-source
  sudo install -d -o opc -g opc -m 0750 "$SOURCE_ROOT"
  test -z "$(find "$SOURCE_ROOT" -mindepth 1 -maxdepth 1 -print -quit)"
  sudo git -C "$APP_CONTROL" archive --format=tar "$APPROVED_SHA" \
    infra/production-metrics-proxy infra/production-observability \
    infra/production/verify-observability-application-identity.sh | \
    tar -x -C "$SOURCE_ROOT"
  printf '%s\n' "$APPROVED_SHA" > "$SOURCE_ROOT/.approved-sha"
  find "$SOURCE_ROOT" -type d -exec chmod 0555 {} +
  find "$SOURCE_ROOT" -type f -exec chmod 0444 {} +
fi

test "$(sudo stat -c '%U %a' "$SOURCE_ROOT")" = 'opc 555'
test -z "$(find "$SOURCE_ROOT" -type d ! -perm 0555 -print -quit)"
test -z "$(find "$SOURCE_ROOT" -type f ! -perm 0444 -print -quit)"
test -r "$SOURCE_ROOT/infra/production-metrics-proxy/compose.yaml"
test -r "$SOURCE_ROOT/infra/production-metrics-proxy/metrics-proxy.conf"
test -r "$SOURCE_ROOT/infra/production-observability/compose.yaml"
test -r "$SOURCE_ROOT/infra/production-observability/prometheus/prometheus.yml.tpl"
test -r "$SOURCE_ROOT/infra/production/verify-observability-application-identity.sh"
test ! -e "$SOURCE_ROOT/.git"
IDENTITY_SCRIPT="$SOURCE_ROOT/infra/production/verify-observability-application-identity.sh"
if ! EXPECTED_IDENTITY_SHA256="$(sudo git -C "$APP_CONTROL" show \
  "${APPROVED_SHA}:infra/production/verify-observability-application-identity.sh" |
  sha256sum | awk '{print $1}')"; then
  printf 'approved identity verifier checksum source unavailable; stopping\n' >&2
  exit 1
fi
if ! test "$(sha256sum "$IDENTITY_SCRIPT" | awk '{print $1}')" = "$EXPECTED_IDENTITY_SHA256"; then
  printf 'approved identity verifier checksum mismatch; stopping\n' >&2
  exit 1
fi
```

`SOURCE_ROOT`가 존재하지만 marker가 없거나 source의 directory/regular file 권한이 각각 `0555`/`0444` 계약과 다르면 partial materialization으로 간주하고 실행하지 않는다. 실행 중 session이 끊겨도 이전 shell의 local variable로 재개하지 않는다. 새 session에서 read-only container 상태를 확인한 뒤 아래 rollback을 수행하거나, source marker와 application identity를 처음부터 다시 확인하고 전체 self-contained block을 재실행한다.

## Same-host 적용

다음 두 적용 블록은 각각 짧고 독립적인 실행 단위다. 각 블록은 Application의 container ID·image ID·configured image reference·Compose project/service label·expected Docker network attachment를 적용 전후 비교한다. 이 비교가 실패하면 다음 블록으로 진행하지 않고 rollback으로 이동한다. Application `backend`, `frontend`, `proxy`와 Managed MySQL에는 Compose 명령을 실행하지 않는다.

### 1. Metrics proxy

```bash
set -Eeuo pipefail

APPROVED_SHA='<approved-merge-sha>'
SOURCE_ROOT="/opt/pawcycle/observability-source/$APPROVED_SHA"
test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
test "$(sudo stat -c '%U %a' "$SOURCE_ROOT")" = 'opc 555'
test -z "$(find "$SOURCE_ROOT" -type d ! -perm 0555 -print -quit)"
test -z "$(find "$SOURCE_ROOT" -type f ! -perm 0444 -print -quit)"

IDENTITY_SCRIPT="$SOURCE_ROOT/infra/production/verify-observability-application-identity.sh"
RUNTIME_IDENTITY="$(mktemp /tmp/pawcycle-application-runtime-identity.XXXXXX)"
trap 'rm -f -- "$RUNTIME_IDENTITY"' EXIT
if ! sudo bash "$IDENTITY_SCRIPT" snapshot > "$RUNTIME_IDENTITY"; then
  rm -f -- "$RUNTIME_IDENTITY"
  printf 'Application runtime identity snapshot failed; stopping before metrics-proxy mutation\n' >&2
  exit 1
fi
chmod 600 "$RUNTIME_IDENTITY"
sudo bash "$IDENTITY_SCRIPT" verify --snapshot "$RUNTIME_IDENTITY"

sudo env PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  docker compose \
  --project-name pawcycle-production-metrics-proxy \
  --project-directory "$SOURCE_ROOT/infra/production-metrics-proxy" \
  --file "$SOURCE_ROOT/infra/production-metrics-proxy/compose.yaml" \
  config --quiet
sudo env PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  docker compose \
  --project-name pawcycle-production-metrics-proxy \
  --project-directory "$SOURCE_ROOT/infra/production-metrics-proxy" \
  --file "$SOURCE_ROOT/infra/production-metrics-proxy/compose.yaml" \
  up --detach --no-deps --wait --wait-timeout 60 --pull never --remove-orphans

if ! sudo bash "$IDENTITY_SCRIPT" verify --snapshot "$RUNTIME_IDENTITY"; then
  printf 'Application runtime identity changed during metrics-proxy apply; failing closed\n' >&2
  exit 1
fi

METRICS_PROXY_ID="$(sudo docker ps --quiet \
  --filter label=com.docker.compose.project=pawcycle-production-metrics-proxy \
  --filter label=com.docker.compose.service=metrics-proxy)"
test -n "$METRICS_PROXY_ID"
test -z "$(sudo docker port "$METRICS_PROXY_ID" 9464/tcp)"
sudo docker exec "$METRICS_PROXY_ID" wget --quiet --output-document=/dev/null \
  http://127.0.0.1:9464/actuator/prometheus

```

### 2. Prometheus and Grafana

```bash
set -Eeuo pipefail

APPROVED_SHA='<approved-merge-sha>'
SOURCE_ROOT="/opt/pawcycle/observability-source/$APPROVED_SHA"
GRAFANA_USER_FILE=/opt/pawcycle/runtime/observability/grafana-admin-user
GRAFANA_PASSWORD_FILE=/opt/pawcycle/runtime/observability/grafana-admin-password
test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
test "$(sudo stat -c '%U %a' "$SOURCE_ROOT")" = 'opc 555'
test -z "$(find "$SOURCE_ROOT" -type d ! -perm 0555 -print -quit)"
test -z "$(find "$SOURCE_ROOT" -type f ! -perm 0444 -print -quit)"

IDENTITY_SCRIPT="$SOURCE_ROOT/infra/production/verify-observability-application-identity.sh"
RUNTIME_IDENTITY="$(mktemp /tmp/pawcycle-application-runtime-identity.XXXXXX)"
trap 'rm -f -- "$RUNTIME_IDENTITY"' EXIT
if ! sudo bash "$IDENTITY_SCRIPT" snapshot > "$RUNTIME_IDENTITY"; then
  rm -f -- "$RUNTIME_IDENTITY"
  printf 'Application runtime identity snapshot failed; stopping before Observability mutation\n' >&2
  exit 1
fi
chmod 600 "$RUNTIME_IDENTITY"
sudo bash "$IDENTITY_SCRIPT" verify --snapshot "$RUNTIME_IDENTITY"

sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  PAWCYCLE_METRICS_TARGET=metrics-proxy:9464 \
  PAWCYCLE_GRAFANA_ADMIN_USER_FILE="$GRAFANA_USER_FILE" \
  PAWCYCLE_GRAFANA_ADMIN_PASSWORD_FILE="$GRAFANA_PASSWORD_FILE" \
  docker compose \
  --project-name pawcycle-production-observability \
  --project-directory "$SOURCE_ROOT/infra/production-observability" \
  --file "$SOURCE_ROOT/infra/production-observability/compose.yaml" \
  config --quiet
sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  PAWCYCLE_METRICS_TARGET=metrics-proxy:9464 \
  PAWCYCLE_GRAFANA_ADMIN_USER_FILE="$GRAFANA_USER_FILE" \
  PAWCYCLE_GRAFANA_ADMIN_PASSWORD_FILE="$GRAFANA_PASSWORD_FILE" \
  docker compose \
  --project-name pawcycle-production-observability \
  --project-directory "$SOURCE_ROOT/infra/production-observability" \
  --file "$SOURCE_ROOT/infra/production-observability/compose.yaml" \
  up --detach --no-deps --wait --wait-timeout 60 --pull never --remove-orphans

if ! sudo bash "$IDENTITY_SCRIPT" verify --snapshot "$RUNTIME_IDENTITY"; then
  printf 'Application runtime identity changed during Observability apply; failing closed\n' >&2
  exit 1
fi

```

`docker compose`에는 `--project-directory`와 `--file`을 명시해 현재 directory와 shell-local `cd`에 의존하지 않는다. `--pull never`는 검증한 pinned image 이외의 pull을 허용하지 않는다.

## 적용 후 확인

다음 확인은 non-secret 상태와 HTTP status만 출력한다.

```bash
PROMETHEUS_URL=http://127.0.0.1:9090
GRAFANA_URL=http://127.0.0.1:3000
PROMETHEUS_ID="$(sudo docker ps --quiet \
  --filter label=com.docker.compose.project=pawcycle-production-observability \
  --filter label=com.docker.compose.service=prometheus)"
GRAFANA_ID="$(sudo docker ps --quiet \
  --filter label=com.docker.compose.project=pawcycle-production-observability \
  --filter label=com.docker.compose.service=grafana)"
METRICS_PROXY_ID="$(sudo docker ps --quiet \
  --filter label=com.docker.compose.project=pawcycle-production-metrics-proxy \
  --filter label=com.docker.compose.service=metrics-proxy)"
test -n "$PROMETHEUS_ID" && test -n "$GRAFANA_ID" && test -n "$METRICS_PROXY_ID"

test -z "$(sudo docker port pawcycle-production-backend-1 8080/tcp)"
test "$(sudo docker port "$METRICS_PROXY_ID" 9464/tcp)" = ""
test "$(sudo docker port "$PROMETHEUS_ID" 9090/tcp)" = '127.0.0.1:9090'
test "$(sudo docker port "$GRAFANA_ID" 3000/tcp)" = '127.0.0.1:3000'

sudo docker inspect "$METRICS_PROXY_ID" --format '{{json .NetworkSettings.Networks}}' | \
  grep -Fq 'pawcycle-production-app'
sudo docker inspect "$PROMETHEUS_ID" --format '{{json .NetworkSettings.Networks}}' | \
  grep -Fq 'pawcycle-production-app'
if sudo docker inspect "$GRAFANA_ID" --format '{{json .NetworkSettings.Networks}}' | \
  grep -Fq 'pawcycle-production-app'; then
  printf 'Grafana must not connect directly to the Application network\n' >&2
  exit 1
fi

PROM_NETWORKS="$(sudo docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' "$PROMETHEUS_ID" | sort)"
GRAFANA_NETWORKS="$(sudo docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' "$GRAFANA_ID" | sort)"
SHARED_OBS_NETWORKS="$(comm -12 <(printf '%s\n' "$PROM_NETWORKS") <(printf '%s\n' "$GRAFANA_NETWORKS") | sed '/^pawcycle-production-app$/d;/^$/d')"
test "$(printf '%s\n' "$SHARED_OBS_NETWORKS" | sed '/^$/d' | wc -l)" -eq 1

assert_observability_running() {
  local container_id="$1"
  test "$(sudo docker inspect --format '{{.State.Status}}' "$container_id")" = 'running'
  test "$(sudo docker inspect --format '{{.State.Restarting}}' "$container_id")" = 'false'
}

assert_observability_running "$PROMETHEUS_ID"
assert_observability_running "$GRAFANA_ID"
PROMETHEUS_RESTART_COUNT="$(sudo docker inspect --format '{{.RestartCount}}' "$PROMETHEUS_ID")"
GRAFANA_RESTART_COUNT="$(sudo docker inspect --format '{{.RestartCount}}' "$GRAFANA_ID")"
sleep 5
assert_observability_running "$PROMETHEUS_ID"
assert_observability_running "$GRAFANA_ID"
test "$(sudo docker inspect --format '{{.RestartCount}}' "$PROMETHEUS_ID")" = "$PROMETHEUS_RESTART_COUNT"
test "$(sudo docker inspect --format '{{.RestartCount}}' "$GRAFANA_ID")" = "$GRAFANA_RESTART_COUNT"

sudo docker exec "$METRICS_PROXY_ID" wget --quiet --output-document=/dev/null \
  http://127.0.0.1:9464/actuator/prometheus
test "$(sudo docker exec "$METRICS_PROXY_ID" sh -ec \
  'wget -S -O /dev/null http://127.0.0.1:9464/api/products 2>&1 | awk '\''$1 == "HTTP/1.1" { print $2 }'\'' | tail -n 1')" = 404

curl --fail --silent --show-error "$PROMETHEUS_URL/-/ready" >/dev/null
curl --fail --silent --show-error "$GRAFANA_URL/api/health" >/dev/null
curl --fail --silent --show-error "$PROMETHEUS_URL/api/v1/targets" | \
  python3 -c 'import json, sys; targets=json.load(sys.stdin)["data"]["activeTargets"]; assert sum(t.get("labels", {}).get("job") == "pawcycle-production-backend" and t.get("health") == "up" for t in targets) == 1'

```

Repository validation은 validation-only credential로 Grafana datasource가 `prometheus:9090`에 실제 도달하는지 확인한다. Production에서는 secret을 argv에 노출하지 않고 Prometheus target, Grafana health와 두 container의 shared internal network attachment를 확인한다.

Prometheus target `up`은 Docker-internal `metrics-proxy:9464/actuator/prometheus` 경로가 동작한다는 뜻이다. Metrics proxy의 `/actuator/prometheus`만 2xx이고 `/api/products`를 포함한 일반 path는 404여야 한다. Backend `:8080`, metrics-proxy `:9464`, Prometheus UI와 Grafana UI가 public interface에 bind되지 않았는지 Compose model과 container port로 확인한다. 세 provisioning dashboard의 의미는 다음과 같다.

1. `Production Overview`: up, HTTP request rate, 5xx rate, p95 latency
2. `Runtime`: process CPU, JVM heap, GC pause, threads, Hikari active/idle/pending/max
3. `PawCycle Operations`: reconciliation, subscription automation, idempotency, commerce pending

Application runtime identity는 각 적용 블록에서 preflight와 postflight가 동일한지 확인한다. Observability 적용 후에는 Prometheus/Grafana가 `running`이고 restart loop가 아니며, 짧은 안정화 window 동안 `RestartCount`가 증가하지 않는지 확인한 뒤 readiness와 Prometheus target을 검사한다. 이 확인은 dashboard 데이터가 정상이라는 뜻일 뿐 Production Verified 판정을 대체하지 않는다.

## Same-host backend state diagnostic

진단은 같은 `app01`에서 두 단계를 연속 실행한다. 첫 단계는 Application/metrics-proxy/release state의 read-only snapshot이고, 두 번째는 그 fresh snapshot과 localhost Prometheus target 상태를 결합한다. 진단 script는 기존 Application control checkout의 HEAD와 working tree를 변경하지 않고 승인 SHA에서 읽는다.

승인 merge SHA의 진단 script를 기존 control HEAD 변경 없이 `/tmp`에 materialize하고 SHA-256을 확인한다.

```bash
APP_CONTROL=/opt/pawcycle/source/repo
APPROVED_SHA='<approved-merge-sha>'
[[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ ]]

DIAG_SCRIPT=/tmp/pawcycle-diagnose-backend-state.sh
EXPECTED_DIAG_SHA256="$(sudo git -C "$APP_CONTROL" show \
  "${APPROVED_SHA}:infra/production/diagnose-backend-state.sh" |
  sha256sum | awk '{print $1}')"
sudo git -C "$APP_CONTROL" show \
  "${APPROVED_SHA}:infra/production/diagnose-backend-state.sh" > "$DIAG_SCRIPT"
chmod 500 "$DIAG_SCRIPT"
test "$(sha256sum "$DIAG_SCRIPT" | awk '{print $1}')" = "$EXPECTED_DIAG_SHA256"
```

Production snapshot은 fresh file로 만들고 즉시 최종 판정에 사용한다.

```bash
PRODUCTION_RESULT="$(mktemp /tmp/pawcycle-production-diagnostic.XXXXXX)"
chmod 600 "$PRODUCTION_RESULT"

sudo bash "$DIAG_SCRIPT" \
  --scope production \
  --https-origin 'https://<approved-production-domain>' \
  --state-dir /opt/pawcycle/state > "$PRODUCTION_RESULT"

bash "$DIAG_SCRIPT" \
  --scope observability \
  --prometheus-url http://127.0.0.1:9090 \
  --production-result "$PRODUCTION_RESULT"
```

최종 `NORMAL`만 exit `0`이다. `BACKEND_DOWN`, `OBSERVABILITY_DEGRADED`, `DEGRADED`, `UNKNOWN`은 non-zero다. Docker 조회 실패, release transition, malformed state, stale snapshot, Prometheus 응답·target cardinality 불일치는 fail-closed 한다. 검증 후 임시 script와 snapshot은 제거할 수 있다.

```bash
rm -f "$DIAG_SCRIPT" "$PRODUCTION_RESULT"
```


## Same-host overhead calibration

현재 repository에는 지속적인 host metric collector가 없다. 따라서 초기 적용 후에는 Application release SHA, workload, scrape interval과 측정 조건을 고정하고, Observability **OFF**와 **ON** 각각에서 짧은 동일 window의 read-only 정상 트래픽을 사용한다. stress/load/capacity test를 수행하지 않는다.

각 window에서 UTC 시작·종료 시각과 다음 aggregate evidence만 보존한다.

```bash
nproc
vmstat 1 60
free -b
df -P /
sudo docker stats --no-stream
sudo docker system df --verbose
for i in $(seq 1 30); do
  curl --silent --show-error --output /dev/null \
    --write-out '%{http_code} %{time_total}\n' \
    https://<approved-production-domain>/api/products
done
```

- CPU: host `vmstat`의 user+system 사용률과 application/Observability container CPU를 OFF/ON 비교한다.
- Memory: host `MemAvailable`과 container memory를 비교하고 OOM/restart 여부를 확인한다.
- Disk: root filesystem available bytes와 Prometheus/Grafana named volume 사용량 증가를 비교한다.
- HTTP: fixed sample의 status error ratio와 `time_total` p95를 비교한다. response body, cookie, credential과 운영 원시 데이터는 보존하지 않는다.

이 결과는 성능 tuning이나 새 threshold 승인이 아니다. 측정된 CPU/memory/disk/latency overhead 또는 failure-isolation 필요가 확인될 때만 별도 Compute decision을 연다.

Host metric gap을 continuous series로 해결하려면 `/proc`·`/sys` mount, 권한, collector image와 lifecycle, 추가 resource를 정해야 한다. 이 변경은 새로운 보안·운영 결정을 요구하므로 이번 범위에서는 추가하지 않으며, 필요 시 **Decision Required — Evidence Triggered**로 승격한다.

## Stop / rollback

다음 중 하나라도 성립하면 적용을 중단하고 Application release, HTTPS, certificate, migration 또는 Managed MySQL을 조작하지 않는다.

- Application runtime identity snapshot이 preflight와 다르다.
- Application Compose가 `backend`/`frontend`/`proxy` 외 service를 소유하거나 Backend `:8080` host publish가 보인다.
- `pawcycle-production-app` network가 없거나 external network 계약이 다르다.
- metrics-proxy가 host port를 publish하거나 endpoint-only/동적 DNS 확인에 실패한다.
- Prometheus/Grafana UI가 loopback 외 address에 bind되거나 pinned image/platform 확인에 실패한다.
- Grafana runtime credential file이 regular file·`0400`·`472:472` 계약을 만족하지 않는다.
- source marker가 없거나 source tree의 directory/regular file 권한이 각각 `0555`/`0444`가 아니어서 `opc`가 수정할 수 없고 non-owner container user가 읽을 수 있는 계약을 만족하지 않는다.

실패, 중단 또는 SSH session loss 뒤에는 이전 shell의 위치·변수로 재개하지 않는다. 새 session에서 다음 read-only 확인으로 관측성 project가 부분적으로 남았는지만 확인한다.

```bash
sudo docker ps --all \
  --filter label=com.docker.compose.project=pawcycle-production-observability \
  --format '{{.Names}} {{.Status}}'
sudo docker ps --all \
  --filter label=com.docker.compose.project=pawcycle-production-metrics-proxy \
  --format '{{.Names}} {{.Status}}'
```

상태가 불명확하거나 partial source가 남으면 Application을 재시작·recreate하지 않고 아래 rollback을 실행한다. Observability를 먼저 내리고 metrics-proxy를 내린다. named volume과 Application external network는 삭제하지 않는다.

```bash
set -Eeuo pipefail

APPROVED_SHA='<approved-merge-sha>'
SOURCE_ROOT="/opt/pawcycle/observability-source/$APPROVED_SHA"
GRAFANA_USER_FILE=/opt/pawcycle/runtime/observability/grafana-admin-user
GRAFANA_PASSWORD_FILE=/opt/pawcycle/runtime/observability/grafana-admin-password

sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  PAWCYCLE_METRICS_TARGET=metrics-proxy:9464 \
  PAWCYCLE_GRAFANA_ADMIN_USER_FILE="$GRAFANA_USER_FILE" \
  PAWCYCLE_GRAFANA_ADMIN_PASSWORD_FILE="$GRAFANA_PASSWORD_FILE" \
  docker compose \
  --project-name pawcycle-production-observability \
  --project-directory "$SOURCE_ROOT/infra/production-observability" \
  --file "$SOURCE_ROOT/infra/production-observability/compose.yaml" \
  down --remove-orphans

sudo env PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  docker compose \
  --project-name pawcycle-production-metrics-proxy \
  --project-directory "$SOURCE_ROOT/infra/production-metrics-proxy" \
  --file "$SOURCE_ROOT/infra/production-metrics-proxy/compose.yaml" \
  down --remove-orphans
```

Rollback은 Observability project와 metrics-proxy project에만 적용한다. `--volumes`를 사용하지 않으며 Application container, release SHA, migration state, Managed MySQL, HTTPS runtime과 certificate state를 변경하지 않는다. 저장소 준비 변경의 복구는 일반 revert PR로 수행하고 reset·rebase·force push는 사용하지 않는다.

두 project가 내려갔고 해당 SHA source가 더 이상 필요 없을 때만, 아래처럼 정확히 materialized source directory 하나를 정리할 수 있다. partial source 정리도 다른 `/opt/pawcycle` 경로를 대상으로 하지 않는다.

```bash
set -Eeuo pipefail

APPROVED_SHA='<approved-merge-sha>'
[[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ ]] || exit 64
SOURCE_ROOT="/opt/pawcycle/observability-source/$APPROVED_SHA"
test "$(sudo stat -c '%U %a' "$SOURCE_ROOT")" = 'opc 550'
sudo rm -rf -- "$SOURCE_ROOT"
```
