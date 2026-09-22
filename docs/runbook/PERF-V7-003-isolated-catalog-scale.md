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

승인된 제안:

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
  infra/performance/catalog-isolated/validate-isolated-catalog.py \
  infra/performance/catalog-isolated/test_validate_isolated_catalog.py

sudo python infra/performance/catalog-isolated/test_validate_isolated_catalog.py
sudo bash infra/performance/catalog-isolated/test-isolated-catalog-contract.sh
```

검증 대상:

- schema/dataset exact mapping
- live schema 거부
- DB URL TLS 요구
- digest-pinned Backend image
- config/password/dataset ownership/mode
- manifest/report regular non-symlink
- manifest checksum
- Product/SKU/Inventory cardinality
- loopback-only port
- Production Docker network 비가입
- import apply acknowledgement
- runtime start acknowledgement
- cleanup acknowledgement
- cleanup `--volumes` 금지
- isolated k6 loopback target only

## Repository source 준비

실제 실행 승인 후에도 mutable checkout에서 바로 실행하지 않는다.

현재 Production control checkout의 HEAD/working tree를 바꾸지 않고 승인 merge SHA의 performance 경로를 별도 source로 materialize한다.

권장 runtime source:

```text
/opt/pawcycle/performance-source/<APPROVED_SHA>
```

적용 전 확인:

- approved SHA 40자리
- merge가 끝난 canonical main SHA
- source marker와 SHA 일치
- directory `0555`
- regular file `0444`
- `.git` 없음
- Application control checkout HEAD/working tree 불변

필요 경로:

```text
infra/performance/catalog-isolated/**
infra/performance/k6/**
scripts/prepare-product-scale-data.py
backend/src/main/resources/catalog/demo-catalog.json
```

source preparation은 `OPS-OBS-001`의 exact-SHA `git archive` 패턴을 재사용한다. checkout/reset/rebase/force-push로 Production control source를 변경하지 않는다.

## Dataset 준비

### I0

workstation 또는 승인된 준비 환경에서:

```bash
python scripts/prepare-product-scale-data.py \
  --target-products 32 \
  --seed 20260826 \
  --dataset-id catalog-core-control-v1 \
  --output tmp/performance/catalog-core-control-v1.json \
  --report tmp/performance/catalog-core-control-v1-report.json
```

### I10K

```bash
python scripts/prepare-product-scale-data.py \
  --target-products 10000 \
  --seed 20260826 \
  --dataset-id catalog-core-10k-v1 \
  --output tmp/performance/catalog-core-10k-v1.json \
  --report tmp/performance/catalog-core-10k-v1-report.json
```

generated manifest와 report는 Git에 commit하지 않는다.

app01의 승인 dataset layout:

```text
/opt/pawcycle/performance-data/catalog/
  catalog-core-control-v1/
    manifest.json
    report.json

  catalog-core-10k-v1/
    manifest.json
    report.json
```

각 dataset directory:

- root-owned
- mode `0700`
- non-symlink

manifest/report:

- root-owned
- regular non-symlink
- mode `0444`

source artifact를 위 경로에 설치할 때 checksum을 workstation report와 다시 비교한다.

## Runtime config와 Secret 경계

runtime config는 repository 밖에 둔다.

예시 경로:

```text
/opt/pawcycle/performance/catalog/env/catalog-core-control-v1.env
/opt/pawcycle/performance/catalog/env/catalog-core-10k-v1.env
```

config:

- root-owned
- regular non-symlink
- mode `0600`
- DB password를 포함하지 않음

DB password:

```text
/opt/pawcycle/performance/catalog/db-password
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

가능하면 현재 승인 Production Backend와 같은 RepoDigest를 사용하고 적용 전 image OS/architecture와 digest를 독립 확인한다.

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

진단은 승인 merge SHA의 `infra/production/diagnose-backend-state.sh`를 사용하고 기존 Production control checkout을 변경하지 않는다.

load 종료 후에도 같은 Gate를 다시 수행한다.

## Dataset preflight

실행 전:

```bash
sudo python infra/performance/catalog-isolated/validate-isolated-catalog.py \
  --config-file /opt/pawcycle/performance/catalog/env/<dataset>.env \
  --password-file /opt/pawcycle/performance/catalog/db-password \
  --dataset-dir /opt/pawcycle/performance-data/catalog/<dataset>
```

PASS 예:

```text
catalog_isolation_preflight=PASS
```

validator는 password와 DB URL을 출력하지 않는다.

## Import

모든 command는 승인 exact-SHA source의 script를 사용한다.

### validate

```bash
sudo bash infra/performance/catalog-isolated/manage-isolated-catalog.sh \
  import-validate \
  --config-file /opt/pawcycle/performance/catalog/env/<dataset>.env \
  --password-file /opt/pawcycle/performance/catalog/db-password \
  --dataset-dir /opt/pawcycle/performance-data/catalog/<dataset>
```

주의: empty performance schema에서는 Backend startup 과정의 Flyway가 migration을 적용할 수 있다. 따라서 `import-validate`도 최초 실행은 DB read-only가 아니다. **DB 실행 승인 이후**에만 수행한다.

### apply

```bash
sudo bash infra/performance/catalog-isolated/manage-isolated-catalog.sh \
  import-apply \
  --config-file /opt/pawcycle/performance/catalog/env/<dataset>.env \
  --password-file /opt/pawcycle/performance/catalog/db-password \
  --dataset-dir /opt/pawcycle/performance-data/catalog/<dataset> \
  --acknowledge APPLY:<dataset-id>
```

wrapper는 APPLY 전에 VALIDATE를 다시 실행한다.

Backend가 실행 중이면 import를 거부한다.

## Isolated Backend 시작

적용 전:

- performance loopback port가 비어 있는지 확인
- Production project container identity 불변
- performance project에 다른 dataset container가 남아 있지 않은지 확인

실행:

```bash
sudo bash infra/performance/catalog-isolated/manage-isolated-catalog.sh \
  up \
  --config-file /opt/pawcycle/performance/catalog/env/<dataset>.env \
  --password-file /opt/pawcycle/performance/catalog/db-password \
  --dataset-dir /opt/pawcycle/performance-data/catalog/<dataset> \
  --acknowledge START:<dataset-id>
```

성공 조건:

- isolated Backend healthy
- `127.0.0.1:<performance-port>/actuator/health/readiness` 2xx
- `127.0.0.1:<performance-port>/api/products` 2xx
- endpoint가 loopback 이외 interface에 publish되지 않음
- container label dataset/schema가 config와 일치
- Production project/network identity 불변

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

desktop의 repository checkout에서:

```bash
bash infra/performance/k6/run-isolated-capacity.sh \
  --target-url http://127.0.0.1:<local-port> \
  --dataset-id <dataset-id> \
  --results-dir /absolute/path/to/results \
  --acknowledge-isolated-load YES
```

고정 stage:

```text
25 → 50 → 100 → 150 → 200 → 250 RPS
각 stage:
  warm-up 30s
  measurement 120s
```

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

I0 / I10K 모두 동일한 evidence schema를 사용한다.

최소:

- approved main SHA
- Backend image digest
- dataset ID
- manifest checksum
- Product/SKU/Inventory cardinality
- measurement start/end UTC
- target/actual RPS
- error / dropped iteration
- p50/p95/p99/max
- Host CPU/memory/iowait/swap
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

runtime만 내릴 때:

```bash
sudo bash infra/performance/catalog-isolated/manage-isolated-catalog.sh \
  down \
  --config-file /opt/pawcycle/performance/catalog/env/<dataset>.env \
  --password-file /opt/pawcycle/performance/catalog/db-password \
  --dataset-dir /opt/pawcycle/performance-data/catalog/<dataset> \
  --acknowledge DOWN:pawcycle-performance-catalog
```

이 command는 performance Compose project만 대상으로 하며 `--volumes`를 사용하지 않는다.

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
