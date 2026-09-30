# PERF-V7-003 OCI 격리 Catalog Scale Runbook

## 상태와 목적

- 작업 ID: `PERF-V7-003`
- 작업 등급: 고위험
- 현재 실행 구분: 저장소 변경
- Parent: #302
- Repository preparation: #305

이 Runbook은 OCI Production과 같은 `app01` / OCI Managed MySQL DB System을 사용하면서도 **live customer-visible Catalog를 synthetic scale data로 오염시키지 않는** I0 / I10K 측정 경계를 정의한다.

이 문서와 관련 script가 merge되어도 실제 OCI schema/user/runtime/load 실행이 승인되는 것은 아니다.

별도 사용자 승인 전 금지:

- performance schema 생성·삭제
- performance DB account 생성·GRANT·DROP
- app01 isolated Backend 실행
- I0/I10K import
- SSH local-forward
- k6 load
- DB/runtime cleanup

## 실험 의미

세 결과를 같은 의미로 섞지 않는다.

```text
P0
현재 public Production
실제 HTTPS/Nginx/Application/Managed MySQL end-to-end 참고값

I0
isolated Catalog Core control
V1 base manifest
32 Product

I10K
isolated Catalog Core 10K
V1 + deterministic V2
10,000 Product
seed 20260826
```

순수 Catalog Core cardinality 비교는 다음만 사용한다.

```text
I0 ↔ I10K
```

P0와 I10K는 데이터 family와 request transport가 모두 다르므로 pure Before/After로 표현하지 않는다.

## 아키텍처 경계

승인된 구조:

```text
desktop k6
   │
   │ SSH local forward
   ▼
app01 127.0.0.1:<performance-port>
   │
   ▼
isolated Backend container
   │
   │ dedicated performance Docker bridge
   ▼
same OCI Managed MySQL DB System
   ├─ pawcycle_perf_core_control
   └─ pawcycle_perf_core_10k
```

다음은 사용하지 않는다.

- Production Nginx
- Production Frontend
- Production `app` network
- Production `database-egress` network
- live Production schema
- Production application DB account
- public performance listener
- 새 NSG
- 새 Load Balancer
- 새 Managed MySQL
- 새 permanent Compute

따라서 이 구조는 **data isolation**이며 **resource isolation**은 아니다. app01 CPU/memory와 OCI MySQL DB System resource는 Production과 공유한다.

## 고정 identity

Compose project:

```text
pawcycle-performance-catalog
```

performance DB username:

```text
pawcycle_perf_catalog
```

schema:

```text
I0    → pawcycle_perf_core_control
I10K  → pawcycle_perf_core_10k
```

dataset ID:

```text
I0    → catalog-core-control-v1
I10K  → catalog-core-10k-v1
```

다른 identity가 보이면 중단한다.

## Repository 검증

실제 OCI에 연결하지 않고 저장소에서 다음을 검증한다.

```bash
python -m py_compile \
  infra/performance/catalog-isolated/prepare-isolated-catalog-provenance.py \
  infra/performance/catalog-isolated/validate-isolated-catalog.py \
  infra/performance/catalog-isolated/test_validate_isolated_catalog.py \
  infra/performance/catalog-isolated/collect-stage-evidence.py \
  infra/performance/catalog-isolated/test_collect_stage_evidence.py

sudo python infra/performance/catalog-isolated/test_validate_isolated_catalog.py
python infra/performance/catalog-isolated/test_collect_stage_evidence.py
sudo bash infra/performance/catalog-isolated/test-isolated-catalog-contract.sh
```

전용 CI `Isolated Catalog Validation`은 다음 변경에서 실행되어야 한다.

- `infra/performance/catalog-isolated/**`
- `infra/performance/k6/isolated-capacity-api-products.js`
- `infra/performance/k6/lib/isolated-capacity.js`
- `infra/performance/k6/lib/baseline.js`
- `infra/performance/k6/run-isolated-capacity.sh`
- 이 Runbook
- 전용 workflow 자체

검증 대상:

- schema/dataset exact mapping
- live schema 거부
- DB URL TLS 요구
- digest-pinned Backend image
- config/password/dataset ownership/mode
- manifest/report/provenance regular non-symlink
- approved source SHA와 source marker 일치
- approved source의 generator/base digest
- manifest/report/provenance checksum 연결
- Product/SKU/Inventory cardinality
- loopback-only port
- Production Docker network 비가입
- import apply acknowledgement
- runtime start acknowledgement
- startup 실패 시 runtime cleanup
- cleanup acknowledgement
- cleanup `--volumes` 금지
- local Backend image가 없어도 cleanup 가능
- isolated k6 loopback target only
- non-empty 결과 directory 재사용 거부

## 승인 exact-SHA source 준비

### 공통 원칙

실제 실행 승인 후에도 mutable checkout에서 바로 실행하지 않는다.

모든 운영 command는 **병합이 끝난 canonical main의 40자리 merge SHA를 기준으로 materialize한 source**에서 실행한다.

source marker:

```text
.approved-sha
```

marker 내용과 source directory 이름은 모두 exact approved SHA와 같아야 한다.

### app01 canonical performance layout

canonical root와 경로:

```text
/opt/pawcycle-performance/
├── source/<APPROVED_SHA>
├── data/catalog/<DATASET_ID>
└── runtime/catalog/
    ├── env/<DATASET_ID>.env
    └── db-password
```

app01 performance root의 기준은 `infra/performance/catalog-isolated/app01-paths.sh`다. source, dataset, config, secret을 위 layout으로 둔다. Production `/opt/pawcycle`의 owner/mode 변경을 전제로 하지 않는다. source까지의 parent chain은 root-owned이고 group/other writable이 아니어야 한다.

Production control checkout의 HEAD/working tree를 바꾸지 않고 `OPS-OBS-001`에서 검증한 `git archive` 패턴을 재사용한다.

materialization 후 계약:

- source directory 이름 = approved SHA
- `.approved-sha` 내용 = approved SHA
- app01 source root와 source 내부 directory는 root-owned
- app01 source root와 source 내부 directory는 `0555`로 정규화
- app01 source regular file은 root-owned + `0444`
- source file까지 이어지는 전체 부모 경로에서 group/other write를 허용하지 않음
- `.git` 없음
- Application control checkout HEAD/working tree 불변

필요 경로:

```text
infra/performance/catalog-isolated/**
infra/performance/k6/**
scripts/prepare-product-scale-data.py
scripts/generate-product-data-v2.py
backend/src/main/resources/catalog/demo-catalog.json
```

app01에서는 archive 추출과 `.approved-sha` 작성이 끝난 뒤, 운영 실행 전에 source tree를 다음처럼 정규화한다.

```bash
sudo chown -R root:root "$SOURCE_ROOT"
sudo find "$SOURCE_ROOT" -type d -exec chmod 0555 {} +
sudo find "$SOURCE_ROOT" -type f -exec chmod 0444 {} +
```

이 계약은 provenance 생성기가 root 권한으로 approved wrapper를 실행하기 전에 비특권 사용자가 중간 directory를 통해 wrapper나 generator 경로를 교체하지 못하게 한다. 검증기는 symlink뿐 아니라 source file까지 이어지는 전체 부모 경로의 소유권과 group/other write 가능 여부도 fail-closed로 확인한다.

app01의 모든 실행 블록은 먼저 다음 경계를 다시 확인한다.

```bash
set -euo pipefail
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="/opt/pawcycle-performance/source/$APPROVED_SHA"

test -d "$SOURCE_ROOT"
test ! -L "$SOURCE_ROOT"
test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
test "$(basename "$SOURCE_ROOT")" = "$APPROVED_SHA"
test ! -e "$SOURCE_ROOT/.git"
(
  set -o pipefail
  sudo find "$SOURCE_ROOT" -type d -print0 |
    sudo xargs -0 -r stat -c '%u %a' |
    awk 'BEGIN { ok = 1; seen = 0 } { seen = 1; if ($0 != "0 555") ok = 0 } END { exit ok && seen ? 0 : 1 }'
)
(
  set -o pipefail
  sudo find "$SOURCE_ROOT" -type f -print0 |
    sudo xargs -0 -r stat -c '%u %a' |
    awk 'BEGIN { ok = 1; seen = 0 } { seen = 1; if ($0 != "0 444") ok = 0 } END { exit ok && seen ? 0 : 1 }'
)

cd "$SOURCE_ROOT"
```

앞선 SSH shell의 local variable을 전제로 하지 않는다. 새 shell에서는 `APPROVED_SHA`와 `SOURCE_ROOT`를 다시 선언하고 marker를 다시 확인한다.

### desktop k6 source

desktop도 평소 작업 checkout을 그대로 사용하지 않는다.

k6를 실행하는 Bash 환경에 승인 merge SHA의 archive를 별도 materialize한다.

권장 예:

```text
$HOME/pawcycle-performance-source/<APPROVED_SHA>
```

해당 source에도 `.approved-sha`를 만들고 source directory 이름과 marker가 approved SHA와 같아야 한다.

k6 실행 직전:

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="$HOME/pawcycle-performance-source/$APPROVED_SHA"

test -d "$SOURCE_ROOT"
test ! -L "$SOURCE_ROOT"
test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
test "$(basename "$SOURCE_ROOT")" = "$APPROVED_SHA"

cd "$SOURCE_ROOT"
```

`run-isolated-capacity.sh`도 전달된 `--source-root`와 실제 script source root가 다른 경우 fail-closed한다.

## Dataset 준비

Dataset도 승인 exact-SHA source에서 생성한다.

### I0

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="/opt/pawcycle-performance/source/$APPROVED_SHA"

test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
cd "$SOURCE_ROOT"

python scripts/prepare-product-scale-data.py \
  --target-products 32 \
  --seed 20260826 \
  --dataset-id catalog-core-control-v1 \
  --output /approved/staging/catalog-core-control-v1/manifest.json \
  --report /approved/staging/catalog-core-control-v1/report.json
```

### I10K

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="/opt/pawcycle-performance/source/$APPROVED_SHA"

test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
cd "$SOURCE_ROOT"

python scripts/prepare-product-scale-data.py \
  --target-products 10000 \
  --seed 20260826 \
  --dataset-id catalog-core-10k-v1 \
  --output /approved/staging/catalog-core-10k-v1/manifest.json \
  --report /approved/staging/catalog-core-10k-v1/report.json
```

위 `/approved/staging/**`은 예시다. 실제 실행에서는 승인된 absolute staging path를 사용한다.

generated manifest와 report는 Git에 commit하지 않는다.

app01의 최종 dataset layout:

```text
/opt/pawcycle-performance/data/catalog/
  catalog-core-control-v1/
    manifest.json
    report.json
    provenance.json

  catalog-core-10k-v1/
    manifest.json
    report.json
    provenance.json
```

각 dataset directory:

- root-owned
- mode `0700`
- non-symlink

manifest/report/provenance:

- root-owned
- regular non-symlink
- mode `0444`

## Dataset provenance 생성

`manifest.json`과 `report.json`만 서로 맞는다고 승인하지 않는다.

설치된 dataset을 exact approved source의 generator로 다시 재현하고 bytes가 같은 경우에만 immutable `provenance.json`을 만든다.

app01에서:

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="/opt/pawcycle-performance/source/$APPROVED_SHA"
DATASET_ID='<catalog-core-control-v1-or-catalog-core-10k-v1>'
DATASET_DIR="/opt/pawcycle-performance/data/catalog/$DATASET_ID"

test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
cd "$SOURCE_ROOT"

sudo python infra/performance/catalog-isolated/prepare-isolated-catalog-provenance.py \
  --source-root "$SOURCE_ROOT" \
  --dataset-dir "$DATASET_DIR"
```

이 도구는 다음을 확인한다.

```text
approved source marker
→ approved source prepare wrapper + Data V2 generator
→ approved source base manifest
→ same seed / same target count로 dataset 재생성
→ installed manifest bytes 비교
→ installed report bytes 비교
→ provenance.json 생성
```

`provenance.json`은 다음을 연결한다.

- approved source SHA
- prepare wrapper SHA-256
- Data V2 generator SHA-256
- base manifest SHA-256
- generated manifest SHA-256
- report SHA-256
- dataset ID
- seed
- total Product count

이미 `provenance.json`이 존재하면 덮어쓰지 않고 fail-closed한다.

## Runtime config와 Secret 경계

runtime config는 repository 밖에 둔다.

예시 경로:

```text
/opt/pawcycle-performance/runtime/catalog/env/catalog-core-control-v1.env
/opt/pawcycle-performance/runtime/catalog/env/catalog-core-10k-v1.env
```

config:

- root-owned
- regular non-symlink
- mode `0600`
- DB password를 포함하지 않음

DB password:

```text
/opt/pawcycle-performance/runtime/catalog/db-password
```

- root-owned
- regular non-symlink
- mode `0400` 또는 `0600`
- Git / Issue / PR / command output에 값 기록 금지

config 형식은 `infra/performance/catalog-isolated/runtime.env.example`을 따른다.

Backend image는 tag-only reference를 허용하지 않는다.

```text
<registry>/<image>@sha256:<64-hex>
```

가능하면 현재 승인 Production Backend와 같은 RepoDigest를 사용한다.

runtime/import 전에는 wrapper가 다음을 확인한다.

- local image 존재
- `linux/amd64`
- RepoDigest와 config digest 일치
- Docker 실행 시 `--pull never`

따라서 실행 시점에 암묵적으로 다른 image를 pull하지 않는다.

## OCI DB provisioning — 별도 승인 필요

다음 단계는 **실제 운영 DB mutation**이다. 별도 사용자 승인을 받은 뒤에만 승인된 MySQL admin client에서 실행한다.

논리 대상:

```text
pawcycle_perf_core_control
pawcycle_perf_core_10k
'pawcycle_perf_catalog'@'%'
```

요구 계약:

- 두 performance schema는 `utf8mb4 / utf8mb4_0900_ai_ci`
- performance account 권한은 두 performance schema로 제한
- global `*.*` application privilege 금지
- live Production schema privilege 금지
- schema/account identity가 이미 존재하면 기존 grants와 owner 목적을 확인하기 전 변경 금지

개념 SQL은 다음과 같다. password literal은 문서·shell history에 넣지 않고 승인된 secure input 방식으로 제공한다.

```sql
CREATE DATABASE pawcycle_perf_core_control
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE pawcycle_perf_core_10k
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'pawcycle_perf_catalog'@'%'
  IDENTIFIED BY <secure-secret-input>;

GRANT ALL PRIVILEGES
  ON pawcycle_perf_core_control.*
  TO 'pawcycle_perf_catalog'@'%';

GRANT ALL PRIVILEGES
  ON pawcycle_perf_core_10k.*
  TO 'pawcycle_perf_catalog'@'%';
```

Flyway가 empty performance schema를 초기화해야 하므로 schema-local DDL 권한이 필요하다.

`SHOW GRANTS`에서 허용된 두 schema 이외 권한이 보이면 중단한다.

## Production / Observability Gate

DB/runtime/import/load mutation 전에 기존 `OPS-OBS-001` same-host diagnostic을 실행한다.

필수 결과:

```text
production_assessment=READY
release_coordination=stable
status=NORMAL
prometheus_target=up
```

이 중 하나라도 아니면 performance 실행을 시작하지 않는다.

진단도 승인 merge SHA의 exact source에서 실행하고 기존 Production control checkout을 변경하지 않는다.

load 종료 후에도 같은 Gate를 다시 수행한다.

## Clock synchronization preflight

Desktop, app01, OCI Monitoring의 timestamp를 같은 measurement window에 비교하기 전에
clock synchronization을 read-only로 확인한다. Desktop과 app01은 synchronized 상태여야
하고, 두 host 사이의 관측 offset upper bound가 1초 미만이어야 한다. 확인이 불가능하거나
1초 이상이면 실제 I0/I10K load를 시작하지 않는다. OCI Monitoring service clock은 operator가
조정하지 않으며, 그 timestamp는 1분 aggregation window로만 해석한다.

Windows Desktop에서 다음을 확인한다.

```powershell
Get-Service W32Time | Select-Object Name, Status
w32tm /query /status
```

`W32Time`이 `Running`이어야 한다. status의 `Source`가 `Local CMOS Clock`이 아니고,
`Last Successful Sync Time`이 현재 시각 기준 24시간 이내여야 한다. 이 조건을 만족하지 않으면
실제 load를 시작하지 않는다.

app01에서 다음 중 사용할 수 있는 read-only 상태 명령을 실행한다.

```bash
timedatectl show -p NTP -p NTPSynchronized -p TimeUSec
# 또는 chrony가 구성된 경우:
chronyc tracking
```

`NTPSynchronized=yes`여야 한다. chrony를 쓰는 경우 `Leap status: Normal`이어야 한다.
상태를 판정할 수 없으면 실제 load를 시작하지 않는다.

두 host 시계의 차이를 확인하려면 Windows PowerShell에서 SSH alias를 지정하고 아래
read-only probe를 실행한다. SSH는 기존 접근 경로를 사용한다. probe는 app01에서 UTC 시각을
읽고, Desktop의 송수신 시각 중간값과 왕복시간으로 offset의 보수적 상한을 계산한다.
5회 중 가장 작은 상한도 1초 미만이어야 한다.

```powershell
$App01SshAlias = 'app01'
$Samples = foreach ($Index in 1..5) {
  $Timer = [Diagnostics.Stopwatch]::StartNew()
  $Before = [DateTimeOffset]::UtcNow
  $RemoteText = ssh -o BatchMode=yes $App01SshAlias 'date -u +%s.%N'
  $After = [DateTimeOffset]::UtcNow
  $Timer.Stop()
  if ($LASTEXITCODE -ne 0 -or $RemoteText -notmatch '^\d+\.\d+$') {
    throw 'app01 clock probe failed; block the load'
  }
  $RemoteSeconds = [double]::Parse($RemoteText, [Globalization.CultureInfo]::InvariantCulture)
  $MidpointSeconds = (($Before - [DateTimeOffset]::UnixEpoch).TotalSeconds +
    ($After - [DateTimeOffset]::UnixEpoch).TotalSeconds) / 2
  $OffsetSeconds = $RemoteSeconds - $MidpointSeconds
  [pscustomobject]@{
    OffsetSeconds = $OffsetSeconds
    OffsetUpperBoundSeconds = [Math]::Abs($OffsetSeconds) + $Timer.Elapsed.TotalSeconds / 2
  }
}
$BestBound = ($Samples | Measure-Object OffsetUpperBoundSeconds -Minimum).Minimum
if ($BestBound -ge 1) { throw 'Desktop/app01 clock offset is not verified below one second; block the load' }
$Samples
```

Probe 실패, synchronized 상태 불명, 또는 best upper bound가 1초 이상이면 실제 load는 차단한다.
OCI Monitoring service clock에는 변경을 가하지 않는다.

## Dataset preflight

각 실행 shell에서 approved source marker를 다시 검증한다.

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="/opt/pawcycle-performance/source/$APPROVED_SHA"
DATASET_ID='<dataset-id>'
DATASET_DIR="/opt/pawcycle-performance/data/catalog/$DATASET_ID"

test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
cd "$SOURCE_ROOT"

sudo python infra/performance/catalog-isolated/validate-isolated-catalog.py \
  --source-root "$SOURCE_ROOT" \
  --config-file "/opt/pawcycle-performance/runtime/catalog/env/$DATASET_ID.env" \
  --password-file /opt/pawcycle-performance/runtime/catalog/db-password \
  --dataset-dir "$DATASET_DIR"
```

PASS 예:

```text
catalog_isolation_preflight=PASS
```

preflight는 다음을 함께 검증한다.

```text
approved source SHA
+ generator/base digest
+ provenance
+ manifest/report digest
+ dataset cardinality
+ runtime config/schema identity
```

validator는 password와 DB URL을 출력하지 않는다.

## Schema bootstrap → Import → Runtime → Load

empty performance schema는 import command로 초기화하지 않는다. `ProductionDemoCatalogImportCommand`는 `spring.flyway.enabled=false`를 강제하므로, import 전에 명시적 `schema-bootstrap`이 필요하다. Bootstrap은 승인된 Backend image를 `SPRING_MAIN_WEB_APPLICATION_TYPE=servlet`로 잠시 시작해 Flyway와 JPA startup을 확인한다. Container-internal readiness health가 healthy가 된 직후 manager가 schema-bootstrap container만 stop/remove하고, rehearsal의 다음 import 단계가 performance DB egress network를 재사용한다. `web-application-type=none`은 approved Production image의 `ProductionAuthSmokeMemberBootstrap`이 maintenance candidate로 해석해 `SpringApplication.run` 전에 실패할 수 있으므로 사용하지 않으며, auth-smoke maintenance enabled도 설정하지 않는다. Bootstrap은 host port를 publish하지 않고 `performance-db-egress`만 사용한다. Bootstrap 실패는 outer cleanup으로 isolated container/network를 정리하며 DB schema/account와 데이터는 보존한다. 이미 migration이 적용된 schema에서 Flyway는 version history를 확인해 미적용 migration만 수행한다. 반복 rehearsal의 import는 동일 manifest와 기존 데이터가 일치할 때만 진행하며 충돌은 실패로 중단한다.

실제 OCI schema/account provisioning과 별도 실행 승인을 완료한 후:

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="/opt/pawcycle-performance/source/$APPROVED_SHA"
DATASET_ID='<dataset-id>'
DATASET_DIR="/opt/pawcycle-performance/data/catalog/$DATASET_ID"

test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
cd "$SOURCE_ROOT"

sudo bash infra/performance/catalog-isolated/manage-isolated-catalog.sh \
  schema-bootstrap \
  --source-root "$SOURCE_ROOT" \
  --config-file "/opt/pawcycle-performance/runtime/catalog/env/$DATASET_ID.env" \
  --password-file /opt/pawcycle-performance/runtime/catalog/db-password \
  --dataset-dir "$DATASET_DIR" \
  --acknowledge "BOOTSTRAP:$DATASET_ID"
```

actual load의 canonical 절차는 아래 `rehearse` 한 번으로 bootstrap → validate → apply → runtime smoke → down을 확인한 뒤, `up`으로 runtime을 다시 올려 load를 실행하는 것이다. 위 단독 `schema-bootstrap`과 아래 개별 import 명령은 단계별 재개·진단에 사용한다. rehearsal은 실제 OCI 실행이며 repository validation 결과로 대체할 수 없다.

```bash
sudo bash infra/performance/catalog-isolated/manage-isolated-catalog.sh \
  rehearse \
  --source-root "$SOURCE_ROOT" \
  --config-file "/opt/pawcycle-performance/runtime/catalog/env/$DATASET_ID.env" \
  --password-file /opt/pawcycle-performance/runtime/catalog/db-password \
  --dataset-dir "$DATASET_DIR" \
  --acknowledge "REHEARSE:$DATASET_ID"
```

rehearsal은 preflight → schema bootstrap detached start → healthy wait → bootstrap service stop/remove → import validate → import apply → isolated Backend 시작 → readiness 및 `/api/products` smoke → project down을 수행한다. Bootstrap service가 health를 통과하지 못하거나 stop/remove가 실패하면 fail-closed하고 outer project cleanup을 실행한다. 시작 전 performance Compose project에 기존 container가 있으면 중단한다. 성공·실패 모두 container/network를 정리하며 DB schema/account와 imported data는 자동 삭제하지 않는다. 실패 시 DB의 부분 변경 가능성을 조사한 뒤 다시 실행한다.

`schema-bootstrap`, `import-validate`, `import-apply`, `rehearse`, `up`, `down`은 app01의 `/run/lock/pawcycle-performance-catalog.lock`을 nonblocking 방식으로 공유한다. 다른 lifecycle action이 실행 중이면 fail-closed하며, process 종료 시 kernel lock이 자동 해제된다. read-only `preflight`와 `status`는 lock을 사용하지 않는다.

## Import

모든 command는 승인 exact-SHA source에서 실행한다.

### validate

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="/opt/pawcycle-performance/source/$APPROVED_SHA"
DATASET_ID='<dataset-id>'
DATASET_DIR="/opt/pawcycle-performance/data/catalog/$DATASET_ID"

test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
cd "$SOURCE_ROOT"

sudo bash infra/performance/catalog-isolated/manage-isolated-catalog.sh \
  import-validate \
  --source-root "$SOURCE_ROOT" \
  --config-file "/opt/pawcycle-performance/runtime/catalog/env/$DATASET_ID.env" \
  --password-file /opt/pawcycle-performance/runtime/catalog/db-password \
  --dataset-dir "$DATASET_DIR"
```

empty performance schema에서 `import-validate`는 Flyway migration을 적용하지 않는다. `schema-bootstrap`이 성공해 schema migration과 JPA startup을 확인한 뒤 validate를 실행한다. validate는 schema를 읽어 검증하며, 실제 OCI DB 접근이므로 **DB 실행 승인 이후**에만 수행한다.

### apply

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="/opt/pawcycle-performance/source/$APPROVED_SHA"
DATASET_ID='<dataset-id>'
DATASET_DIR="/opt/pawcycle-performance/data/catalog/$DATASET_ID"

test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
cd "$SOURCE_ROOT"

sudo bash infra/performance/catalog-isolated/manage-isolated-catalog.sh \
  import-apply \
  --source-root "$SOURCE_ROOT" \
  --config-file "/opt/pawcycle-performance/runtime/catalog/env/$DATASET_ID.env" \
  --password-file /opt/pawcycle-performance/runtime/catalog/db-password \
  --dataset-dir "$DATASET_DIR" \
  --acknowledge "APPLY:$DATASET_ID"
```

wrapper는 APPLY 전에 VALIDATE를 다시 실행하고 apply command에 `--pawcycle.catalog.manifest-import.confirm-apply=true`를 전달한다. validate에는 이 확인 인자를 전달하지 않는다.

Backend가 실행 중이면 import를 거부한다.

## Isolated Backend 시작

적용 전:

- performance loopback port가 비어 있는지 확인
- Production project container identity 불변
- performance project에 다른 dataset container가 남아 있지 않은지 확인

실행:

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="/opt/pawcycle-performance/source/$APPROVED_SHA"
DATASET_ID='<dataset-id>'
DATASET_DIR="/opt/pawcycle-performance/data/catalog/$DATASET_ID"

test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
cd "$SOURCE_ROOT"

sudo bash infra/performance/catalog-isolated/manage-isolated-catalog.sh \
  up \
  --source-root "$SOURCE_ROOT" \
  --config-file "/opt/pawcycle-performance/runtime/catalog/env/$DATASET_ID.env" \
  --password-file /opt/pawcycle-performance/runtime/catalog/db-password \
  --dataset-dir "$DATASET_DIR" \
  --acknowledge "START:$DATASET_ID"
```

성공 조건:

- isolated Backend healthy
- `127.0.0.1:<performance-port>/actuator/health/readiness` 2xx
- `127.0.0.1:<performance-port>/api/products` 2xx
- endpoint가 loopback 이외 interface에 publish되지 않음
- container label dataset/schema가 config와 일치
- Production project/network identity 불변

`compose up` 이후 health/API 검증 전에 실패하면 wrapper가 생성한 performance project를 `compose down --remove-orphans`로 정리한다.

response body는 evidence에 저장하지 않는다.

## SSH local-forward

실제 load 승인 후 desktop에서 tunnel을 연다.

개념 형태:

```text
desktop 127.0.0.1:<local-port>
→ SSH
→ app01 127.0.0.1:<performance-port>
```

public listener, NSG, Nginx, DNS, TLS를 추가하지 않는다.

I0과 I10K는 동일한 tunnel 조건을 사용한다.

SSH tunnel overhead가 포함되므로 I0/I10K는 상대 cardinality 비교이며 public Production absolute capacity로 해석하지 않는다.

## Isolated k6

desktop에서도 승인 exact-SHA source를 사용한다.

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="$HOME/pawcycle-performance-source/$APPROVED_SHA"
DATASET_ID='<dataset-id>'
RUN_ID="$APPROVED_SHA-$DATASET_ID-$(date -u +%Y%m%dT%H%M%SZ)"
RESULTS_DIR="$HOME/pawcycle-performance-results/$RUN_ID"

test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
cd "$SOURCE_ROOT"

bash infra/performance/k6/run-isolated-capacity.sh \
  --source-root "$SOURCE_ROOT" \
  --target-url http://127.0.0.1:<local-port> \
  --dataset-id "$DATASET_ID" \
  --results-dir "$RESULTS_DIR" \
  --evidence-ssh-target <approved-app01-ssh-alias> \
  --isolated-host-port <performance-port> \
  --acknowledge-isolated-load YES
```

### Windows PowerShell 5 + Git for Windows

앞에서 materialize한 **exact approved desktop source archive**에서 실행한다. 평소 checkout이나
다른 SHA의 runner를 호출하지 않는다. 아래는 별도 실제 load 승인, clock/Production Gate,
loopback SSH tunnel 준비가 끝난 뒤 사용할 실행 예시이며, 저장소 검증은 OCI load를 승인하거나
수행하지 않는다. `<...>`는 승인된 로컬 값으로 대체하고 실제 식별값/키를 저장소에 기록하지 않는다.

```powershell
$ErrorActionPreference = 'Stop'
$ApprovedSha = '<approved-40-character-merge-sha>'
$SourceRoot = Join-Path $env:USERPROFILE "pawcycle-performance-source/$ApprovedSha"
$DatasetId = '<catalog-core-control-v1-or-catalog-core-10k-v1>'
$TargetRps = '<approved-supported-rate>'
$RunId = "$ApprovedSha-$DatasetId-$([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))"
$ResultsDir = Join-Path $env:USERPROFILE "pawcycle-performance-results/$RunId"
$GitBash = 'C:/Program Files/Git/bin/bash.exe'
$Cygpath = 'C:/Program Files/Git/usr/bin/cygpath.exe'
$EvidenceSshExecutable = 'C:/Windows/System32/OpenSSH/ssh.exe'
$EvidenceSshIdentityFile = 'C:/<approved-local-key-directory>/<identity-file>'
$EvidenceSshTarget = '<user>@<host>'

if ($ApprovedSha -cnotmatch '^[0-9a-f]{40}$') { throw 'Invalid approved SHA' }
$Source = Get-Item -LiteralPath $SourceRoot
$Marker = Get-Item -LiteralPath (Join-Path $SourceRoot '.approved-sha')
if (-not $Source.PSIsContainer -or $Marker.PSIsContainer -or
    ($Source.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
    ($Marker.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
    $Source.Name -cne $ApprovedSha -or
    (Get-Content -LiteralPath $Marker.FullName -Raw).Trim() -cne $ApprovedSha -or
    (Test-Path -LiteralPath (Join-Path $SourceRoot '.git'))) {
    throw 'Approved source archive identity mismatch'
}
foreach ($Executable in @($GitBash, $Cygpath, $EvidenceSshExecutable)) {
    if (-not (Test-Path -LiteralPath $Executable -PathType Leaf)) { throw 'Required executable missing' }
}
# Existing approved secure environment; do not print or hard-code these identities.
foreach ($Name in @('PAWCYCLE_PERF_OCI_COMPARTMENT_ID', 'PAWCYCLE_PERF_OCI_DB_SYSTEM_ID')) {
    if (-not [Environment]::GetEnvironmentVariable($Name, 'Process')) { throw 'OCI identity environment missing' }
}

# Convert only local paths consumed by Bash. Keep the SSH executable/identity native.
$SourcePosix = & $Cygpath -u -- $Source.FullName
if ($LASTEXITCODE -ne 0) { throw 'Source path conversion failed' }
$ResultsPosix = & $Cygpath -u -- $ResultsDir
if ($LASTEXITCODE -ne 0) { throw 'Results path conversion failed' }
$Runner = "$SourcePosix/infra/performance/k6/run-isolated-capacity.sh"
$RunnerArgs = @(
    $Runner,
    '--source-root', $SourcePosix,
    '--target-url', 'http://127.0.0.1:<local-port>',
    '--dataset-id', $DatasetId,
    '--results-dir', $ResultsPosix,
    '--evidence-ssh-target', $EvidenceSshTarget,
    '--evidence-ssh-executable', $EvidenceSshExecutable,
    '--evidence-ssh-identity-file', $EvidenceSshIdentityFile,
    '--isolated-host-port', '<performance-port>',
    '--target-rps', $TargetRps,
    '--acknowledge-isolated-load', 'YES'
)
& $GitBash @RunnerArgs
if ($LASTEXITCODE -ne 0) { throw 'Isolated capacity stage failed; do not advance RPS' }
```

`python3`, `oci`, `k6`는 이 Git Bash의 `PATH`에서 실행 가능해야 한다. OCI CLI에는 기존
Monitoring read 권한이 필요하며 두 OCI identity 환경변수 요구사항은 Linux 경로와 같다.
SSH target은 승인된 alias 또는 직접 `<user>@<host>`를 사용할 수 있다. Windows에서 인증이
검증된 OpenSSH executable을 명시하고 identity는 `C:/...` native forward-slash 경로로 전달한다.
identity 경로를 runner의 일반 출력에 기록하지 않으며 키 내용은 읽거나 출력하지 않는다.

두 SSH option을 생략하면 기존 `PATH`의 `ssh`를 사용하고 `-i`를 추가하지 않는다.
runner는 선택한 executable을 하나의 경로/이름으로 실행하고 identity를 별도 `-i` argv로 전달한다.
collector의 `sudo -n python3 /opt/pawcycle-performance/...` 호출은 하나의 SSH remote-command
argument이며 `MSYS2_ARG_CONV_EXCL='*'`는 그 SSH process에만 적용된다. 전역 MSYS/Git 설정은
바꾸지 않는다. PowerShell은 Bash 파일의 종료 상태만 확인하며 collector/k6 lifetime, cleanup,
summary/host JSONL/evidence 조립과 fail-closed 판정은 기존 Bash runner가 소유한다.

runner는 기존 non-empty results directory를 거부한다. 따라서 이전 실행과 새 실행의 일부 RPS 결과가 하나의 series처럼 섞이지 않는다.

기본 실행은 전체 series를 순서대로 진행한다.

```text
25 → 50 → 100 → 150 → 200 → 250 RPS
```

승인된 한 단계만 실행할 때는 위 명령에 `--target-rps <supported-rate>`를 추가한다.
지원 값은 `25`, `50`, `100`, `150`, `200`, `250`이다. 예를 들어
`--target-rps 25`는 25 RPS stage의 collector, k6, evidence assemble을 완료한 뒤 종료하며
다음 RPS를 자동 실행하지 않는다. 단일 stage도 동일하게 warm-up 30초,
measurement 120초, 아래 threshold와 evidence 수집 계약을 적용한다.
실제 load는 실행 모드와 관계없이 별도 사용자 승인 후에만 수행한다.

threshold:

- expected-status error rate = 0
- dropped iterations = 0

각 stage summary:

- datasetId
- targetRps
- actualRps
- droppedIterations
- p50/p95/p99/max
- expectedStatusErrorRate
- allocated/active VUs

한 stage가 실패하면 다음 RPS로 진행하지 않는다.

## Evidence

실제 I0/I10K 실행에서는 위 두 evidence 인자를 필수로 사용한다. Desktop의 같은
approved source에 `python3`, `oci` CLI와 기존 OCI Monitoring read 권한이 필요하다.
`PAWCYCLE_PERF_OCI_COMPARTMENT_ID`와 `PAWCYCLE_PERF_OCI_DB_SYSTEM_ID`는
승인된 기존 secure environment에서 제공한다. 값은 command output, 결과 파일,
Issue/PR/report에 쓰지 않는다. 실행 전에 read-only OCI Monitoring query로
`oci_mysql_database`의 아래 여섯 지표가 대상 DB System에 존재하는지 확인한다.

```text
CPUUtilization, MemoryUtilization, ActiveConnections,
CurrentConnections, Statements, StatementLatency
```

Runner는 각 stage 직전에 approved source의 collector를 SSH로 app01에서 실행한다.
collector는 isolated Backend의 loopback `/actuator/prometheus`, `/proc`,
`docker inspect`/`docker stats`에서 allowlisted timestamp/value만 stdout JSONL로
전송한다. Production Prometheus target이나 Observability topology는 변경하지 않는다.
k6 요약에는 첫 measurement 요청 직전부터 마지막 measurement 응답 완료까지의 UTC가 포함된다. Runner는 그 구간에
속한 Host/Container/JVM/Tomcat/Hikari sample과 겹치는 OCI Monitoring 1분 aggregation bucket만
`*-evidence.json`에 합친다. OCI bucket은 순간값이 아닌 `windowStartUtc`/`windowEndUtc`로
표현하며, OCI `endTime`이 exclusive이므로 query 시작/종료를 각각 1분 확장한 뒤
measurement와 겹치지 않는 bucket은 제거한다. OCI 1분 해상도는 120초 stage보다 거칠며
같은 DB System의 Production traffic도 포함한다. 이 한계를 병목 판정에 반영한다.

collector가 시작되지 않거나 중단되거나 필수 metric/구간 sample이 없으면
다음 RPS로 진행하지 않는다. 실패한 stage의 k6 요약과 이미 수집된 Host JSONL은
보존한다. 결과 디렉터리는 Git 밖에 두고 접근을 제한한다. 장기 보고서에는
필요한 aggregate만 옮기고 raw `/actuator/prometheus` payload는 보존하지 않는다.
collector는 query 전에 마지막으로 예상되는 겹침 bucket의 종료 시각까지 기다린다.
그 뒤 OCI datapoint 게시가 늦으면 20초 간격으로 최대 6회(총 추가 대기 최대 120초)
재확인한다. measurement와 겹치는 예상 bucket이 모두 도착하지 않으면 수집 실패로 중단한다.

I0 / I10K 모두 동일한 evidence schema를 사용한다.

최소:

- approved main SHA
- exact source marker SHA
- Backend image digest
- dataset ID
- provenance SHA-256
- base manifest SHA-256
- generated manifest SHA-256
- report SHA-256
- Product/SKU/Inventory cardinality
- measurement start/end UTC
- target/actual RPS
- error / dropped iteration
- p50/p95/p99/max
- Host CPU user/system/iowait, MemAvailable, SwapTotal/SwapFree, root filesystem free
- sample interval의 `/proc/vmstat` `pswpin`/`pswpout` page 및 byte delta (swap capacity와 분리)
- isolated Backend CPU/memory/restart/OOM
- JVM/Tomcat/Hikari
- OCI MySQL CPU/memory/connections/statements/statement latency
- Production READY / Observability NORMAL pre/post
- limitations

Secret, DB password, session, cookie, raw Product response, raw Production DB row는 기록하지 않는다.

## Stop 조건

즉시 중단:

- Production diagnostic != READY
- Observability diagnostic != NORMAL
- release transition 존재
- source marker != approved SHA
- script source root != approved source root
- provenance mismatch
- dataset checksum mismatch
- config/schema/dataset identity mismatch
- performance account grant가 두 schema보다 넓음
- Backend image digest mismatch
- performance endpoint가 loopback 이외 interface에 publish
- performance container가 Production Docker network에 연결
- Production Application container identity 변화
- Production health degradation
- isolated Backend unhealthy/restart/OOM
- OCI MySQL 이상 징후
- k6 expected-status error 또는 dropped iteration

중단 후 RPS를 높이지 않는다.

## Runtime cleanup

runtime만 내릴 때도 승인 exact source를 사용한다.

```bash
APPROVED_SHA='<approved-40-character-merge-sha>'
SOURCE_ROOT="/opt/pawcycle-performance/source/$APPROVED_SHA"
DATASET_ID='<dataset-id>'
DATASET_DIR="/opt/pawcycle-performance/data/catalog/$DATASET_ID"

test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"
cd "$SOURCE_ROOT"

sudo bash infra/performance/catalog-isolated/manage-isolated-catalog.sh \
  down \
  --source-root "$SOURCE_ROOT" \
  --config-file "/opt/pawcycle-performance/runtime/catalog/env/$DATASET_ID.env" \
  --password-file /opt/pawcycle-performance/runtime/catalog/db-password \
  --dataset-dir "$DATASET_DIR" \
  --acknowledge DOWN:pawcycle-performance-catalog
```

이 command는 performance Compose project만 대상으로 하며 `--volumes`를 사용하지 않는다.

local Backend image가 이미 없어도 exact project identity가 맞으면 runtime cleanup은 가능해야 한다.

DB schema/account는 이 command가 삭제하지 않는다.

## DB cleanup — 별도 승인 필요

모든 evidence가 보존되고 performance runtime이 내려간 뒤 별도 사용자 승인을 받아 exact identity만 정리한다.

대상:

```text
pawcycle_perf_core_control
pawcycle_perf_core_10k
'pawcycle_perf_catalog'@'%'
```

다른 schema/user가 보이면 중단한다.

개념 cleanup:

```sql
DROP DATABASE pawcycle_perf_core_control;
DROP DATABASE pawcycle_perf_core_10k;
DROP USER 'pawcycle_perf_catalog'@'%';
```

cleanup 후 Production READY / Observability NORMAL을 다시 확인한다.

## 복구 경계

Repository 준비 변경의 복구는 일반 revert PR이다.

실제 실행에서는 다음을 복구 대상으로 삼지 않는다.

- live Production schema
- Production Backend/Frontend/Nginx
- Production Docker network
- release state
- Observability volumes
- certificate

performance runtime과 exact performance schema/account만 별도 승인 범위에서 정리한다.

## 판정

### Opt-in discovery lifecycle diagnostics

기존 `pawcycle.catalog.product.discovery.diagnostics.enabled`가 true일 때만
`pawcycle.catalog.product.discovery.lifecycle`을 추가한다. 기존 4-phase metric은 유지한다.
service가 reader transaction proxy 호출 전에 scope를 열고 반환/예외 뒤 finally에서 제거한다.
Spring `TransactionExecutionListener`는 기존 manager에 추가하며 Hibernate session별
`SessionEventListener`는 acquisition/release callback을 관측한다. pool/DataSource/manager를
wrap하거나 connection handling 설정을 바꾸지 않는다.

고정 phase:

- `transaction-begin`: Spring beforeBegin → afterBegin.
- `transaction-commit` / `transaction-rollback`: 해당 before → after callback.
- `transaction-completion`: beforeCommit 또는 beforeRollback → reader proxy 반환 경계.
- `transaction-cleanup`: afterCommit 또는 afterRollback → reader proxy 반환 경계.
- `connection-acquire`: Hibernate acquisition start → end.
- `pre-repository`: 획득 완료부터 첫 repository 진입까지 lease와 겹치는 시간.
- `repository-with-connection`: repository 본문과 획득 완료 → release start의 교집합 합.
- `repository-body-paired`: 완결된 호출의 repository 본문 시간; 기존 total을 대체하지 않음.
- `post-repository-connection-hold`: 마지막 repository 종료 이후 release start까지의 교집합.
- `connection-release`: Hibernate release start → end.
- `connection-lease-total`: Hibernate acquisition end → release end.

하나의 호출에 여러 acquire/release pair가 있으면 완전한 pair별로 기록한다. acquisition이
본문 중간에 발생해도 교집합으로 계산한다. 실제 교집합이 비어 있는 0은 유효하지만,
불완전 sequence를 0ms로 합성하지 않는다. 고정 result matched/unmatched를 사용하는
`pawcycle.catalog.product.discovery.lifecycle.calls`와 `.pairs` Counter로 coverage를 확인한다.
pair Timer의 count와 호출/본문/transaction Timer의 count는 multi-pair에서 다르다.
재진입·중복/역순 event는 unmatched로 남기며 request/thread/connection ID label은 없다.

completion/cleanup은 proxy 반환까지의 관측 경계이며 순수 JDBC commit/cleanup method
시간이 아니다. acquisition/release end callback은 실패 시 finally에도 호출될 수 있다.
lease-total은 close/recycle 후 callback까지 포함한 외부 근사값이며 Hikari usage와 정확히
동일하지 않다. Hikari의 ms 단위, callback overhead, 완료 cohort 차이를 함께 해석한다.
여러 repository body 사이의 공백은 repository-with-connection에 포함하지 않으므로
세 구간을 lease-total과 무조건 보존식으로 비교하지 않는다.

collector의 `productDiscoveryLifecycle`은 optional extension이다. field가 없는 과거 sample은
그대로 assemble하며, extension이 존재하면 고정 phase/coverage 전체와 값·label을 검증한다.
기존 evidence/SQL/API/dataset/workload를 변경하지 않는다. 계측 overhead는 미측정이며,
동일 150 RPS 재측정은 별도 승인 후 수행한다. 200/250 RPS는 계속 BLOCKED다.

I0와 I10K가 끝난 뒤:

```text
I0
→ I10K
→ 병목 계층
→ 최소 개선 후보
→ 동일 조건 재측정
→ KEEP / DEFER / REJECT
```

100K는 10K 결과가 추가 scale evidence의 필요성을 보여 줄 때만 진행한다.
