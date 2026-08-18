# OPS-DB-002 RDS migration and cutover readiness

- 작업 ID: OPS-DB-002 / 등급: 고위험 / 실행 구분: 저장소 변경
- 실제 AWS, RDS 생성, import, cutover, rollback은 별도 명시적 고위험 사용자 승인이 필요하다. 이 Runbook은 Repository readiness이며 Production Verified가 아니다.

## Canonical target evidence

`/opt/pawcycle/state/rds-target-verified`는 regular non-symlink mode 600 파일이어야 한다. `FORMAT_VERSION=1`, `RECORD_KIND=rds-target-verified`, `EVIDENCE_PHASE`, `FINAL_CONSISTENCY_VERIFIED`, `BACKUP_ID_SHA256`, `MANIFEST_SHA256`, `TARGET_HOST`, `TARGET_PORT=3306`, `TARGET_DATABASE_SHA256`, `SCHEMA_SHA256`, `FLYWAY_SHA256`, `FLYWAY_COUNT`, `TABLE_members`, `TABLE_products`, `TABLE_skus`, `TABLE_subscriptions`, `APPLICATION_SHA`, `CONTROL_SHA`, `CONTRACT_SHA`, `CONNECTIVITY_VERIFIED=true`, `IMPORT_VERIFIED=true`, `BACKEND_HEALTH_VERIFIED=true`, `API_SMOKE_VERIFIED=true`, `SOURCE_TARGET_DISTINCT=true`, `PRODUCTION_CUTOVER=false`를 정확히 한 번씩 기록한다. rehearsal은 `REHEARSAL/false`, cutover readiness는 final consistency point 뒤 재생성한 `CUTOVER/true`여야 한다. 비밀번호·raw DB data는 기록하지 않는다. OPS-013 `db-restore-verified`와 OPS-025 `db-restore-candidate`의 backup/manifest/schema/Flyway/core-table fingerprints와 일치해야 한다.

## Rehearsal (executable order)

1. 실제 승인 후에만 read-only preflight를 실행한다: `rds-read-only-preflight.sh`는 EC2/VPC/subnet/SG/orderability describe만 허용한다. EC2 SG에서 RDS SG TCP 3306만, public/CIDR/IPv6/prefix-list 노출 없음, private/Single-AZ/encryption/automated backup-PITR creation contract를 확인한다. retention은 비용·운영 승인 대기다.
2. `db-restore-verified`, `db-restore-candidate`, source active Docker MySQL service/image/health/mount/volume와 source backup identity를 보존·확인한다. Docker source volume을 stop/remove/delete하지 않는다.
3. isolated import와 target connectivity, Backend health/API smoke를 별도 승인된 환경에서 검증하고 canonical evidence를 만든다. `PRODUCTION_CUTOVER=false`를 유지한다.
4. 아래 canonical rehearsal gate invocation을 실행한다.

### Prerequisites and stop conditions

입력은 placeholder만 사용한다. `/opt/pawcycle/state`와 runtime root는 existing absolute non-symlink directory여야 하며, state·evidence·runtime bundle file은 해당되는 경우 root-owned regular non-symlink mode 600이어야 한다. shared `deploy.lock` contention, dirty Control, SHA mismatch, OPS-013/025 hash/count mismatch, unhealthy source MySQL, missing source volume, target fingerprint mismatch는 즉시 중단한다.

```bash
sudo bash infra/production/rds-read-only-preflight.sh \
  --region ap-northeast-2 --ec2-instance-id <i-hex> --vpc-id <vpc-hex> \
  --subnet-id <subnet-az-2d> --subnet-id <subnet-second-az> \
  --ec2-security-group-id <sg-ec2> --rds-security-group-id <sg-rds>
```

이는 describe-only preflight이며 RDS/SG/subnet/IAM을 만들거나 바꾸지 않는다. Docker rollback bundle은 기본 flags로, RDS bundle은 `<rds-endpoint>` 및 `--datasource-port 3306 --datasource-ssl-mode REQUIRED`로 별도 root-only runtime root에 stage한다. source/target schema·Flyway·core-table count는 OPS-013/OPS-025 logical backup/isolated restore manifest와 target verification에서 hash/count로만 기록한다; password·raw row는 evidence에 넣지 않는다.

```bash
sudo bash infra/production/materialize-ssm-env.sh \
  --ssm-prefix <ssm-prefix> --output-dir /opt/pawcycle/runtime-docker --region ap-northeast-2
sudo bash infra/production/materialize-ssm-env.sh \
  --ssm-prefix <ssm-prefix> --output-dir /opt/pawcycle/runtime-rds --region ap-northeast-2 \
  --datasource-host <rds-endpoint>.ap-northeast-2.rds.amazonaws.com \
  --datasource-port 3306 --datasource-ssl-mode REQUIRED
```

Evidence template (mode 600; placeholder values only):

```text
FORMAT_VERSION=1
RECORD_KIND=rds-target-verified
EVIDENCE_PHASE=REHEARSAL
FINAL_CONSISTENCY_VERIFIED=false
BACKUP_ID_SHA256=<64-hex>
MANIFEST_SHA256=<64-hex>
TARGET_HOST=<rds-endpoint>.ap-northeast-2.rds.amazonaws.com
TARGET_PORT=3306
TARGET_DATABASE_SHA256=<64-hex>
SCHEMA_SHA256=<64-hex>
FLYWAY_SHA256=<64-hex>
FLYWAY_COUNT=<nonnegative-integer>
TABLE_members=<nonnegative-integer>
TABLE_products=<nonnegative-integer>
TABLE_skus=<nonnegative-integer>
TABLE_subscriptions=<nonnegative-integer>
APPLICATION_SHA=<40-hex>
CONTROL_SHA=<40-hex>
CONTRACT_SHA=<40-hex>
CONNECTIVITY_VERIFIED=true
IMPORT_VERIFIED=true
BACKEND_HEALTH_VERIFIED=true
API_SMOKE_VERIFIED=true
SOURCE_TARGET_DISTINCT=true
PRODUCTION_CUTOVER=false
```

The ordered rehearsal result is: source backup identity → integrity check → OPS-025 candidate → separately approved RDS import → schema/Flyway/table manifest → Backend datasource rehearsal → Backend health and `/api/products` → evidence. RDS ingress is automated by preflight; EC2 egress to TCP 3306 is a manual connectivity prerequisite proved only by `CONNECTIVITY_VERIFIED=true` during rehearsal. Then run:

```bash
sudo bash infra/production/rds-transition-gate.sh rehearsal \
  --state-dir /opt/pawcycle/state --application-sha <40-hex> --control-sha <40-hex> \
  --evidence /opt/pawcycle/state/rds-target-verified \
  --rds-runtime-dir /opt/pawcycle/runtime-rds \
  --rollback-runtime-dir /opt/pawcycle/runtime-docker
```

## Production cutover (executable order)

1. 명시적 승인, clean Control/Application SHA, shared `deploy.lock`, OPS-013/025 state와 source Docker volume 보존을 확인한다.
2. RDS REQUIRED runtime과 별도 staged Docker default rollback runtime을 materialize하고 동일 secret identity를 gate가 값 출력 없이 비교한다. RDS URL에는 `allowPublicKeyRetrieval`을 넣지 않는다.
3. `rds-transition-gate.sh cutover ... --rds-runtime-dir /opt/pawcycle/runtime-rds --rollback-runtime-dir /opt/pawcycle/runtime-docker`로 readiness만 확인한다. 이 명령은 activation하지 않는다.
4. 승인된 별도 실행에서만 write quiesce/activation을 수행하고, 그 직후 Backend health, API smoke, external HTTPS를 독립 확인한다. 실패 시 다음 단계로 진행하지 않는다.

실제 Production cutover는 명시적 사용자 승인이 있는 별도 실행이다. Scheduler/write quiesce, 마지막 consistency backup/import, target import verification은 그 승인 경계 안에서만 한다. same-SHA activation은 기존 protected `deploy.sh` contract를 사용하되, RDS activation 명령 자체는 이 저장소 준비 범위에서 실행하지 않는다. Flyway/schema/data fingerprint, `/api/products`, external HTTPS를 확인한 뒤 안정화 기간에도 source Docker service와 named volume을 유지한다.

Cutover order is: preflight → verified final backup → explicit user approval → Scheduler OFF/write quiesce → final consistency point → separately approved import → evidence regeneration → cutover readiness gate → same Application SHA activation through the existing protected deploy contract → Flyway/schema/data → health/API/external HTTPS → stabilization. The activation boundary is intentionally separate; the approved operator uses only the existing protected command shape, for example `sudo bash infra/production/deploy.sh --sha <same-40-hex> --backend-image <approved-ghcr-backend> --frontend-image <approved-ghcr-frontend> --runtime-dir /opt/pawcycle/runtime-rds --state-dir /opt/pawcycle/state`, after its own current approvals. Never delete or stop the source Docker service/volume during stabilization.

The `deploy.lock concurrency` boundary is shared by readiness and release controls. The readiness gate obtains and releases a shared `deploy.lock` lock; it is not an activation reservation: immediately before actual activation, use the existing `deploy.sh` exclusive-lock contract and stop if any intervening release/restore operation is observed.

## Rollback (executable order)

1. 증상: RDS connectivity, import fingerprint, Backend/API/HTTPS failure. 영향: write/read availability 또는 data correctness risk. 확인: gate/evidence, health/API/HTTPS 결과, source volume/image/health/mount.
2. 완화: 트래픽과 writes를 중단하고 source Docker volume을 보존한다. `rds-transition-gate.sh rollback --state-dir /opt/pawcycle/state --application-sha <40-hex> --control-sha <40-hex> --rollback-runtime-dir /opt/pawcycle/runtime-docker`로 readiness를 확인한다.
3. 별도 승인된 rollback activation만 Docker default runtime을 사용한다. RDS runtime, source volume, evidence를 자동 삭제하지 않는다. 실패·data divergence·lock contention은 Tech Lead/User에게 escalation하고 non-sensitive evidence를 보존한다.

Rollback triggers include failed connectivity/import identity, Flyway/schema/table mismatch, unhealthy Backend, failed `/api/products`, or external HTTPS failure. After the rollback gate passes, the separately approved operator uses the same Application SHA and the existing protected deploy contract with `/opt/pawcycle/runtime-docker`, then rechecks MySQL/source mount, Backend health, API, and HTTPS. Preserve both the RDS target evidence and Docker source volume/service; no deletion is authorized.

```bash
sudo bash infra/production/rds-transition-gate.sh rollback \
  --state-dir /opt/pawcycle/state --application-sha <same-40-hex> \
  --control-sha <40-hex> --rollback-runtime-dir /opt/pawcycle/runtime-docker
sudo bash infra/production/deploy.sh --sha <same-40-hex> \
  --backend-image <approved-ghcr-backend> --frontend-image <approved-ghcr-frontend> \
  --runtime-dir /opt/pawcycle/runtime-docker --state-dir /opt/pawcycle/state
```

After the separately approved protected deploy, check in order: preserved source MySQL volume and mount, Backend health, `https://<approved-domain>/api/products`, then external HTTPS `/products`. Stop and escalate on the first failure; these commands are not authorized by repository readiness alone.

PITR follow-up은 automated backup/PITR 설정을 실제로 검증하고 별도 restore exercise를 수행하는 후속 승인 범위다. 이 Runbook은 그것을 검증하지 않는다.

## Post-create backup/PITR follow-up

별도 승인 후 applied retention 값, backup window와 `LatestRestorableTime`, 별도 restored RDS의 schema/Flyway/core-table fingerprints, restore-time measurement를 기록하고 RPO/RTO를 재평가한다. 이 절차 전에는 PITR 또는 restore가 Verified라고 주장하지 않는다.

Use read-only post-create checks for `BackupRetentionPeriod` and `LatestRestorableTime`; perform a separate approved restore exercise to a separate RDS target, compare schema/Flyway/core-table fingerprints, measure restore time, and re-evaluate RPO/RTO. Do not claim these checks ran in this repository task.

## Operational response

| 항목 | 내용 |
| --- | --- |
| 증상/영향 | connectivity, fingerprint, Backend/API/HTTPS failure는 availability 또는 data correctness risk다. |
| 확인 | gate output, non-sensitive evidence, source MySQL health/image/mount, `/api/products`, HTTPS를 확인한다. |
| 완화 | traffic/write를 중단하고 source Docker service·volume을 보존한다. |
| 에스컬레이션 | data divergence, source health failure, lock contention, import failure는 User/Tech Lead에게 즉시 escalation한다. |
| 증거 | hash/count/SHA, approval, gate/health/API/HTTPS 결과만 보존하며 secrets/raw data는 제외한다. |

`SKIPPED` is the required result when approval, clean state, source preservation, or a required verification input is absent; do not improvise an operation. Stop on every gate failure and escalate with the non-sensitive evidence listed above.
