# OPS-025 Actual Production DB 논리 복원 Runbook

## 상태와 범위

- 상태: 저장소 계약 준비 완료 후 별도 사용자 실행 승인 대기
- 작업 등급: 고위험
- 대상: OPS-013 completion marker와 무결성 검증을 통과한 논리 backup
- 목적: 현재 Production MySQL volume을 보존한 채 별도 candidate named volume에 복원·검증하고, 쓰기 중단 뒤 전환하거나 원래 volume으로 복귀

이 Runbook과 Script 준비는 Actual Production restore 실행 증거가 아니다. Actual Production restore 훈련, AWS·Docker·DB·Secret 명령과 데이터 변경은 별도 고위험 사용자 실행으로만 수행한다.

논리 손상 복구만 다룬다. source MySQL이나 volume을 읽을 수 없는 물리 장애, EBS 손상·분실, Instance 장애와 filesystem 복구는 범위 밖이다. 이 경우 실행을 중단하고 EBS snapshot·인프라 복구 방식, 비용, RPO/RTO를 사용자/Tech Lead가 별도로 결정한다.

## 불변 조건

- OPS-013 completion marker, object size·SSE-S3·SHA-256·gzip 검증과 사전 `restore-verify` 성공 기록이 없는 backup은 사용하지 않는다.
- MySQL은 `compose.yaml`과 동일한 pinned image만 사용한다.
- candidate는 별도 named volume, `none` network, host port 없음, Production source volume mount 없음으로 준비한다.
- schema fingerprint, Flyway history fingerprint·count와 `members`, `products`, `skus`, `subscriptions` count manifest가 backup과 일치하기 전에는 전환하지 않는다.
- cutover는 명시적 승인과 공유 `deploy.lock` 획득 후 Application 쓰기 경로를 중단하고 수행한다.
- source volume은 삭제·초기화·덮어쓰기하지 않고 protected recovery record와 함께 보존한다.
- `active-mysql-volume`은 이후 `deploy.sh`와 `rollback.sh`가 반드시 읽고 실제 MySQL mount와 대조한다. 상태가 없거나 손상되면 기본 volume으로 fallback하지 않고 실패한다.
- 실패 시 source·candidate volume을 자동 삭제하지 않는다. cutover 실패는 원래 volume과 같은 Application SHA의 자동 복귀를 한 번만 시도하고 중단한다.
- Flyway history 수동 수정, schema downgrade, raw datadir 복사, `docker compose down --volumes`, volume 삭제와 자동 재시도는 금지한다.
- backup ID, bucket·object key, account·host·IP·domain, row count와 Secret은 저장소·PR·완료 보고·공개 로그에 기록하지 않는다.

## 런타임 상태 계약

`/opt/pawcycle/state`는 root 소유 mode `700`, 아래 파일은 regular non-symlink mode `600`이어야 한다.

| 상태 | 의미 |
| --- | --- |
| `active-mysql-volume` | deploy·rollback·restore가 사용하는 유일한 active DB volume |
| `db-restore-verified` | 사전 OPS-013 `restore-verify` 성공의 backup ID hash·manifest hash·pinned image |
| `db-restore-candidate` | candidate volume, source volume, backup·manifest hash, schema·Flyway·핵심 table manifest |
| `db-restore-source` | 쓰기 중단 후 source volume의 schema·Flyway·핵심 table manifest와 Application SHA |
| `previous-mysql-volume` | cutover 전 source volume |
| `db-restore-application-sha` | cutover와 복귀에 고정한 Application SHA |

실제 backup ID는 hash만 상태에 남는다. Script는 state record와 Docker volume ownership label을 함께 대조한다. 상태 파일을 수동 편집·삭제해 gate를 우회하지 않는다.

## 적용 전 Gate

다음이 하나라도 불명확하면 중단한다.

1. 별도 고위험 실행 승인에 Control SHA, 현재 Application SHA, 사용하려는 제한된 운영 backup 기록, 허용 중단 구간과 복귀 판단자가 명시됐다.
2. Control checkout은 승인 SHA의 clean detached 상태이고 `infra/production/**` 직접 수정이 없다.
3. 네 Production Container가 healthy이며 내부 `/products`, `/api/products`와 외부 사용자 경로의 HTTPS가 정상이다.
4. 현재 `current-sha`, `contract-sha`, active MySQL mount와 named volume이 서로 일치한다.
5. OPS-020 임시 Container가 없고 다른 deploy·rollback·backup·restore 작업이 없다.
6. runtime·state·lock 파일의 소유권과 mode가 계약과 일치한다.
7. Docker disk·memory에 candidate 복원 여유가 있고 source와 candidate를 함께 보존할 수 있다.
8. 대상 backup은 OPS-013 제한된 운영 기록에 존재하며 completion marker까지 업로드 완료됐다.
9. schema downgrade, Flyway history 수정, 데이터 의미 판정이나 물리 volume/EBS 복구가 필요하지 않다.

## 1. Active volume 상태 최초 도입

OPS-025 Control을 처음 적용하기 전, 기존 pinned·healthy Production MySQL이 기본 source volume을 정확히 mount한 상태에서 한 번만 실행한다.

```bash
cd /opt/pawcycle/control
sudo infra/production/production-db-restore.sh \
  initialize-volume-state \
  --state-dir /opt/pawcycle/state
```

Script는 공유 `deploy.lock`을 획득하고 Production Compose MySQL이 정확히 하나인지, pinned image·health·`/var/lib/mysql` mount와 기본 volume 존재를 확인한 뒤에만 `active-mysql-volume`을 기록한다. 이미 상태가 있으면 덮어쓰지 않는다.

이후 OPS-021 방식으로 현재 clean Control SHA를 명시적으로 채택한다. 상태 초기화와 Control 채택은 Actual DB restore가 아니며 volume·schema·row를 변경하지 않는다.

## 2. 제한된 운영 입력 준비

OPS-013과 같은 root shell에서 실제 값은 출력하지 않고 환경으로만 전달한다.

```bash
export PAWCYCLE_BACKUP_BUCKET='<제한된 운영 기록에서 입력>'
export PAWCYCLE_BACKUP_REGION='ap-northeast-2'
export PAWCYCLE_BACKUP_PREFIX='<제한된 운영 기록에서 입력>'
export PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER='<12자리 예상 소유자>'
BACKUP_ID='<제한된 운영 기록에서 입력>'
CANDIDATE_VOLUME='pawcycle-production-mysql-candidate-<16 lowercase hex>'
```

candidate 이름은 정확히 허용 형식이어야 하고 현재 source 및 기존 volume과 겹치면 안 된다. 실제 값을 shell history, 문서, 채팅이나 PR에 복사하지 않는다.

## 3. 사전 restore-verify

candidate 준비 전에 동일 backup으로 OPS-013 격리 복원 검증을 실행한다.

```bash
sudo --preserve-env=PAWCYCLE_BACKUP_BUCKET,PAWCYCLE_BACKUP_REGION,PAWCYCLE_BACKUP_PREFIX,PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER \
  infra/production/db-backup-restore.sh \
  restore-verify \
  --backup-id "$BACKUP_ID" \
  --state-dir /opt/pawcycle/state
```

성공 조건:

- completion marker와 dump·manifest object 무결성 일치
- 동일 pinned MySQL image, `none` network, 임시 전용 volume, host port 없음
- schema fingerprint, Flyway history와 핵심 table manifest 일치
- 실행 전후 Production MySQL container identity·health·active volume 불변
- 임시 OPS-013 container·volume·work path 제거 성공
- `db-restore-verified` 제한 상태 기록 성공

실패하면 같은 명령을 자동 재시도하지 않는다. completion marker·checksum·capacity·SQL import·manifest 중 첫 실패 원인을 조사하고 새 backup 필요 여부를 승인받는다.

## 4. Candidate 복원·검증

사전 `restore-verify` 성공과 candidate 준비 승인을 다시 확인한 뒤 실행한다.

```bash
sudo --preserve-env=PAWCYCLE_BACKUP_BUCKET,PAWCYCLE_BACKUP_REGION,PAWCYCLE_BACKUP_PREFIX,PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER \
  infra/production/db-backup-restore.sh \
  restore-candidate \
  --backup-id "$BACKUP_ID" \
  --candidate-volume "$CANDIDATE_VOLUME" \
  --state-dir /opt/pawcycle/state \
  --runtime-dir /opt/pawcycle/runtime
```

Script는 `db-restore-verified`의 backup·manifest hash와 다시 받은 completion marker를 대조한다. candidate MySQL은 Production runtime DB 이름·credential 계약으로 초기화하되 password는 root-only 임시 파일과 MySQL `_FILE` 변수로만 주입하며 Container 환경·CLI 인자·출력에 값을 남기지 않는다. 성공 후 준비 Container와 work path는 제거하고 candidate volume은 정지 상태로 보존한다.

전환 금지 조건:

- `db-restore-candidate`가 없거나 mode·형식·hash가 다름
- candidate ownership·source·backup·manifest label 불일치
- source volume과 candidate volume이 같음
- candidate가 다른 Container에 attach됨
- schema·Flyway·핵심 table manifest 불일치
- Production MySQL identity·health·active volume 변경

실패한 candidate도 자동 삭제하지 않는다. 재사용하거나 삭제하지 말고 별도 정리 승인을 기다린다.

## 5. Cutover 직전 명시적 승인

여기서 다시 중단하고 사용자/Tech Lead에게 다음 비민감 사실만 보고한다.

- 사전 `restore-verify`: PASS/FAIL
- candidate 복원·schema·Flyway·핵심 table manifest: MATCH/MISMATCH
- source와 candidate가 별도 volume이며 둘 다 존재: PASS/FAIL
- 현재 Application SHA·Control SHA가 승인 입력과 일치: PASS/FAIL
- 네 health·내부 Smoke·외부 HTTPS: PASS/FAIL
- 쓰기 중단 시작 승인과 복귀 판단자: 승인/미승인

명시적 cutover 승인이 없으면 다음 명령을 실행하지 않는다.

## 6. 쓰기 중단과 Cutover

```bash
sudo infra/production/production-db-restore.sh \
  cutover \
  --candidate-volume "$CANDIDATE_VOLUME" \
  --backend-image '<승인된 GHCR Backend repository>' \
  --frontend-image '<승인된 GHCR Frontend repository>' \
  --runtime-dir /opt/pawcycle/runtime \
  --state-dir /opt/pawcycle/state
```

Script의 순서는 고정된다.

1. 공유 `deploy.lock` 획득과 current Control·Application·active volume 확인
2. candidate protected record·Docker label·미attach 상태 재검증
3. 현재 Release image preflight, 네 health, 내부 Smoke와 localhost HTTPS 검증
4. Proxy·Frontend·Backend 정지로 쓰기 경로 중단
5. source MySQL이 실행 중이고 쓰기가 중단된 상태에서 `db-restore-source` manifest 기록
6. source MySQL 정지
7. candidate 상태·label 재검증
8. `previous-mysql-volume`, `db-restore-application-sha`, `active-mysql-volume` 보호 상태 기록. 어느 기록이든 실패하면 source 복귀
9. 같은 Application SHA로 candidate MySQL만 활성화하고 image·health·mount와 schema·Flyway·핵심 table manifest 검증
10. Backend·Frontend 활성화 후 Flyway history를 포함한 DB manifest 재검증
11. 두 DB gate가 모두 성공한 뒤에만 Proxy를 활성화하고 네 health, 내부 Smoke와 HTTPS 검증

중간 실패 시 source와 candidate volume은 삭제하지 않는다. candidate 활성화 또는 적용 후 DB manifest 검증이 실패하면 source volume과 같은 Application SHA 복귀를 한 번 시도하고 성공 여부와 관계없이 중단한다.

## 7. 적용 후 검증

Script 성공만으로 완료하지 않는다. 운영자는 다음을 같은 승인 세션에서 대조한다.

- `current-sha`와 `db-restore-application-sha` 일치
- `active-mysql-volume`과 실행 MySQL `/var/lib/mysql` mount 일치
- source와 candidate volume 모두 존재하고 source가 변경·삭제되지 않음
- MySQL·Backend·Frontend·Proxy 모두 healthy
- 내부 `/products`, `/api/products` Smoke 정상
- 서버 localhost HTTPS와 승인 domain·certificate 검증 정상
- 사용자 PC 등 독립 경로에서 외부 HTTPS `/products`, `/api/products` 정상
- `db-restore-candidate`의 schema fingerprint, Flyway history와 핵심 table manifest 일치
- 사용자 승인 핵심 데이터 read-only 검증 정상
- OPS-020 임시 Container 부재

핵심 데이터 의미가 manifest count만으로 판정되지 않으면 임의 SQL 수정 없이 중단하고 Product Owner/Tech Lead에게 판정을 요청한다. 실제 row 값이나 query 결과는 공개 로그와 저장소에 남기지 않는다.

## 8. 원래 volume 복귀

cutover 후 검증 실패, 데이터 의미 불일치 또는 사용자 복귀 결정이 있으면 자동 재시도 대신 다음 명령을 한 번 실행한다.

```bash
sudo infra/production/production-db-restore.sh \
  revert \
  --backend-image '<승인된 GHCR Backend repository>' \
  --frontend-image '<승인된 GHCR Frontend repository>' \
  --runtime-dir /opt/pawcycle/runtime \
  --state-dir /opt/pawcycle/state
```

Script는 `previous-mysql-volume`, `db-restore-source`, `db-restore-application-sha`를 대조하고 쓰기 경로와 MySQL을 정지한 뒤 source volume과 기존 Application SHA를 활성화한다. source MySQL manifest, Backend·Frontend 시작 후 manifest 재검증을 모두 통과한 뒤에만 Proxy를 열며, 네 health, 내부 Smoke와 HTTPS가 일치해야 성공한다.

source 복귀 실패 시 candidate 재활성화를 한 번 시도하고 중단한다. 어느 경로에서도 source·candidate volume과 restore state record를 자동 삭제하지 않는다.

## 실패·중단 경계

| 단계 | 실패 상태 | 조치 |
| --- | --- | --- |
| 상태 최초 도입 | 기존 상태 존재, image·health·mount 불일치 | 덮어쓰기 금지, 현재 계약 조사 |
| restore-verify | completion marker·checksum·gzip·SQL·manifest 실패 | candidate 준비 금지, 자동 재시도 금지 |
| candidate 준비 | 부분 candidate 또는 record 실패 | source 불변, candidate 자동 삭제 금지, cutover 금지 |
| cutover 전 gate | 승인·lock·Control·Application·volume 불일치 | Container 정지 전 실패 |
| 쓰기 중단 자체 실패 | 일부 Application service 정지 가능 | source Release 재활성화를 한 번 시도하고 cutover 없이 중단 |
| 쓰기 중단 후 source record 실패 | source volume 불변, Application 정지 가능 | 같은 Application SHA 재활성화 시도 결과 확인 후 중단 |
| candidate 활성화·manifest 실패 | candidate 또는 일부 service 실패 | source volume·기존 Application SHA 자동 복귀 한 번, 두 volume 보존 |
| source 복귀 실패 | source 또는 service 활성화 실패 | candidate 재활성화 한 번 후 즉시 에스컬레이션 |
| 물리 volume·EBS 장애 | source를 읽을 수 없음 | 이 Runbook 중단, 별도 인프라 복구 결정 |

## 이후 deploy·rollback 회귀 방지

`release-common.sh`은 모든 `deploy.sh`·`rollback.sh`에서 `active-mysql-volume`을 필수로 읽는다. 허용 이름, regular non-symlink와 mode `600`을 검증하고 Compose에 정확한 volume을 전달하며, 실행 MySQL mount가 상태와 다르면 health 성공으로 처리하지 않는다.

따라서 candidate 활성화 후 Application deploy·rollback은 candidate를 유지한다. 상태 누락·손상 때 기본 volume으로 조용히 되돌아가지 않는다. DB source 복귀는 `rollback.sh`가 아니라 이 Runbook의 `revert`만 사용한다.

## 정리 경계

source·candidate volume, `db-restore-verified`, `db-restore-candidate`, `db-restore-source`, previous volume과 Application 상태는 Actual Production 훈련 종료 직후에도 자동 삭제하지 않는다.

보존 기간, candidate 폐기, source를 다시 active로 둘지, backup object lifecycle 밖 별도 보존과 RPO/RTO는 이번 범위에서 결정하지 않는다. volume 삭제나 state record 정리는 적용 후 증거·복귀 가능성·EBS 용량을 검토한 별도 고위험 승인으로만 수행한다.

## 비민감 증거 형식

```text
실행일(KST):
Control SHA 일치: PASS|FAIL
Application SHA 일치: PASS|FAIL
OPS-013 completion marker·restore-verify: PASS|FAIL
Candidate isolation·pinned image: PASS|FAIL
Schema·Flyway·core table manifest: MATCH|MISMATCH
Cutover 승인: 승인|미승인
쓰기 중단·공유 lock: PASS|FAIL
Cutover 또는 source 복귀: PASS|FAIL|미실행
MySQL·Backend·Frontend·Proxy health: PASS|FAIL
내부 Smoke·외부 HTTPS: PASS|FAIL
Source·candidate volume 보존: PASS|FAIL
실제 Production restore: 실행|미실행
잔여 위험과 에스컬레이션:
```

backup ID, volume 실제 이름, bucket·object, account·host·IP·domain, Secret, row count와 원시 로그는 기록하지 않는다.
