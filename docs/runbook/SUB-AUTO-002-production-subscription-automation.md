# SUB-AUTO-002 Production 정기배송 자동화 배포·활성화 Runbook

## 상태와 범위

- 작업 ID: `SUB-AUTO-002`
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Platform/SRE

이 Runbook은 SUB-AUTO-001의 V9~V11 Application을 Scheduler OFF로 배포한 뒤, 별도 승인 입력으로 Production Scheduler를 활성화·관찰·중단하는 절차다. 저장소 준비만 완료했으며 Production, AWS, 운영 DB, Secret, restore는 실행하지 않았다.

실제 실행은 별도 고위험 사용자 승인이 필요하다. Secret과 운영 식별값은 저장소·채팅·PR·명령 출력에 기록하지 않는다. batch size와 fixed delay는 측정·승인 없이 권장값을 만들지 않고 사용자가 승인한 명시값만 사용한다.

## 불변 조건과 영향

- `deploy.sh`와 `rollback.sh`는 materialized runtime의 `PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'`일 때만 실행된다.
- `PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE`와 `PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS`도 양의 정수 명시값이어야 한다. Spring 기본값에 의존하지 않는다.
- Scheduler 활성화는 현재 Application Release를 바꾸지 않고 Backend runtime만 다시 생성하는 별도 command다.
- 현재 Release와 target Release의 `backend/src/main/resources/db/migration/**`가 다르면 schema boundary다. target 활성화 실패 시 이전 Release 자동복귀를 하지 않고 Application을 정지하며, `rollback.sh`도 pre-migration Release를 거부한다.
- 모든 deploy·rollback·activation은 protected `active-mysql-volume`을 유지한다. `down --volumes`, volume 삭제, down migration, Flyway history 수정·repair, DROP, 직접 데이터 수정과 자동 재시도를 하지 않는다.

중단 중에는 주문 자동 생성이 지연될 수 있다. 잘못 활성화하면 duplicate Order, Schedule 무Order advance, snapshot/cardinality 불일치 또는 반복 failure로 이어질 수 있으므로 아래 aggregate gate를 모두 통과하기 전에는 활성화하지 않는다.

## 적용 전 Gate

1. 별도 실제 운영 실행 승인이 있고 승인 Release SHA, clean Control SHA, Backend·Frontend image repository, runtime/state 경로가 고정됐다.
2. 현재 `current-sha`, `contract-sha`, 실행 image와 active MySQL mount가 기존 Production 계약과 일치한다.
3. 다른 deploy·rollback·backup·restore가 없고 공유 `deploy.lock`을 사용할 수 있다.
4. V9~V11 migration과 Scheduler 활성화를 같은 단계로 취급하지 않는다. 최초 Application deploy의 runtime은 반드시 OFF다.
5. 기존 OPS-013 backup·격리 restore 증거와 OPS-025 실제 restore 승인 경계를 확인한다. 확인은 restore 실행 승인이 아니다.

## 1. Scheduler OFF runtime 준비

기존 승인 SSM 경로에 아래 세 leaf를 명시한다. 실제 prefix와 값은 제한된 운영 기록에서만 다룬다.

```text
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=false
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE=<사용자 승인 양의 정수>
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS=<사용자 승인 양의 정수>
```

기존 `materialize-ssm-env.sh`로 runtime bundle을 다시 만든다. Script는 값 누락·중복·잘못된 boolean·0 이하 수를 거부하고 실제 값을 출력하지 않는다. 생성된 `backend.env`를 출력하지 않는다.

## 2. V9~V11 Application 배포

OFF runtime에서만 기존 deploy command를 실행한다.

```bash
sudo infra/production/deploy.sh \
  --sha "$TARGET_SHA" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir /opt/pawcycle/runtime \
  --state-dir /opt/pawcycle/state
```

같은 migration bundle의 target 실패는 기존 healthy Release 자동복귀를 사용할 수 있다. migration bundle이 다르면 Backend 기동 중 Flyway가 일부 DDL을 적용했을 가능성을 배제할 수 없으므로 자동복귀를 차단하고 Backend·Frontend·Proxy를 정지한다. `current-sha`를 target으로 기록하지 않고 MySQL volume을 보존한다.

## 3. Read-only Production preflight

Application health와 기존 smoke가 통과한 뒤 OFF 상태를 확인한다.

```bash
sudo infra/production/subscription-automation-preflight.sh \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --expect-bundle-enabled false \
  --expect-running-enabled false \
  --runtime-dir /opt/pawcycle/runtime \
  --state-dir /opt/pawcycle/state
```

PASS에는 다음이 포함된다.

- bundle과 실행 Backend의 Scheduler OFF, batch size, fixed delay 명시값
- Flyway V9·V10·V11 각각 SUCCESS
- `subscription_orders`, `subscription_order_items`, `uk_subscription_orders_schedule`, `idx_schedules_due_automation` 존재
- due candidate count와 oldest due date
- duplicate Order, Schedule 무Order advance, Order/snapshot/item cardinality, 처리된 ACTIVE Subscription의 미래 Schedule cardinality aggregate anomaly가 모두 0
- execution, processed candidate, created Order, failure, duplicate/no-op aggregate metric

출력에는 고객·Subscription·Schedule·Order 식별자, row payload, Secret과 원시 DB 오류가 포함되지 않는다. schema 또는 Flyway가 불완전하면 preflight가 실패하며 활성화하지 않는다.

## 4. 첫 activation 승인과 실행

OFF preflight의 candidate count와 oldest due date를 사용자가 검토한다. 예상 밖 규모·날짜면 원인을 확인할 때까지 중단한다. 승인한 최대 후보 수를 `MAX_DUE_CANDIDATES`로 고정한다. 이 값은 성능 최적값이나 alert threshold가 아니다.

승인 SSM runtime을 `PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=true`로 다시 materialize한다. batch size와 fixed delay도 같은 승인 명시값을 유지한다. 아직 실행 Backend는 OFF이고 bundle만 ON이어야 한다.

```bash
sudo infra/production/subscription-automation-control.sh activate \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --max-due-candidates "$MAX_DUE_CANDIDATES" \
  --runtime-dir /opt/pawcycle/runtime \
  --state-dir /opt/pawcycle/state
```

control command는 공유 lock, current Release/image, schema, candidate maximum과 aggregate invariant를 다시 확인한 뒤 현재 SHA의 Backend만 runtime ON으로 다시 생성한다. Backend health, 기존 내부 smoke와 HTTPS gate를 통과하지 못하면 Backend를 정지해 Scheduler 실행을 막고 MySQL을 보존한다.

## 5. activation 후 성공 판정

승인 fixed delay와 batch를 기준으로 충분한 관찰 구간을 사용하되 이 Runbook은 임계값을 정하지 않는다. 같은 preflight를 `--expect-bundle-enabled true --expect-running-enabled true --max-due-candidates "$MAX_DUE_CANDIDATES"`로 반복하고 적용 전 aggregate 값과 비교한다.

성공은 사용자가 다음을 모두 확인한 경우에만 선언한다.

- execution total이 증가하고 processed candidate·created Order 변화가 설명 가능함
- failure가 비정상적으로 증가하지 않음
- Schedule당 Order 최대 1건이며 duplicate aggregate anomaly가 0
- Order 없는 Schedule advance가 0
- Order·effective snapshot·item cardinality anomaly가 0
- 처리된 ACTIVE Subscription의 future Schedule cardinality anomaly가 0
- Backend health, 기존 `/products`·`/api/products` smoke와 HTTPS가 정상

candidate count, metric과 anomaly aggregate만 보존한다. 원시 row, 식별자, payload와 전체 log를 증거에 복사하지 않는다.

## 증상·중단·완화

다음 중 하나면 즉시 Scheduler OFF 전환을 시작하고 성공 선언·추가 retry·추가 deploy를 중단한다.

- duplicate Order 또는 unique/schema 이상
- Schedule 무Order advance
- snapshot/item/future Schedule cardinality 이상
- 반복 failure 또는 설명할 수 없는 failure 증가
- Flyway V9~V11 실패·부분 적용·history 이상
- Backend health, API smoke 또는 HTTPS 회귀

승인 SSM runtime의 enabled 값을 `false`로 materialize한 뒤 실행한다.

```bash
sudo infra/production/subscription-automation-control.sh deactivate \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir /opt/pawcycle/runtime \
  --state-dir /opt/pawcycle/state
```

deactivate는 current Release·control·보호된 MySQL volume과 OFF runtime만 먼저 확인하고, Backend를 OFF runtime으로 재생성한 뒤 full postflight를 실행한다. postflight anomaly·schema·metric 실패는 deactivate를 막지 않으며 Scheduler는 OFF로 남는다. Backend 재생성의 health/smoke 실패는 Backend를 정지해 Scheduler가 계속 실행되지 않게 한다. state 파일 수동 편집이나 실행 중 env 덮어쓰기로 gate를 우회하지 않는다.

## migration 실패·부분 적용 경계

V9, V10 또는 V11 실패·부분 적용은 Scheduler OFF와 Application 정지 상태에서 중단한다. 같은 deploy 반복, 자동 retry, Flyway repair, history 수정, down migration, DROP, 직접 row 수정과 old Release 기동을 하지 않는다.

다음 선택은 별도 사용자 승인 없이는 실행하지 않는다.

1. 원인과 non-transactional DDL 상태를 확인한 forward-fix Release 준비
2. 기존 OPS-013 검증 backup을 사용하는 OPS-025 restore 절차

검증된 복구 선택지는 forward-fix 또는 기존 OPS-025 restore뿐이다. 두 선택 모두 Scheduler OFF와 MySQL volume 보존을 유지하고, 서비스 재기동은 MySQL health 확인 뒤 Backend·Frontend health 확인, 마지막 Proxy traffic 허용 순서로만 진행한다. 실제 downtime/RTO는 현재 증거가 없어 미확정이며, 별도 승인된 실제 실행에서 측정 후 기록한다. Flyway repair는 자동 복구 절차가 아니며 별도 판단 전까지 금지 경계다. 데이터 손실 가능성 때문에 forward-fix 또는 restore 실행은 별도 사용자 승인이 필요하다.

OPS-025는 별도 candidate named volume에 복원·검증하고 source volume을 보존하는 고위험 실제 운영 절차다. 이 Runbook이나 `rollback.sh`가 restore를 대신하지 않는다. backup/restore 식별값, volume 실제 이름과 row count는 저장소 증거에 남기지 않는다.

## rollback과 복구

- migration bundle이 같은 Application Release 간 rollback만 기존 `rollback.sh`가 허용한다.
- migration bundle이 다르면 수동 rollback도 Container·state 변경 전에 실패한다.
- schema downgrade가 필요하면 rollback 대상이 아니며 forward-fix 또는 별도 승인 restore를 선택한다.
- 어느 실패 경로에서도 MySQL named volume을 삭제하지 않는다.
- 저장소 변경 자체는 일반 revert PR로 복구한다. Production 적용·restore는 별도 승인과 증거가 필요하다.

## 에스컬레이션과 증거

Backend Engineer와 Product Owner/Tech Lead에게 activation 전후 aggregate 출력, Release/Control SHA, health·smoke 결과, 실패 단계, Scheduler OFF 또는 Backend 정지 결과와 두드러진 위험을 전달한다. 실제 식별자·Secret·raw row/log는 전달하지 않는다.

Production alert threshold·Discord escalation/repeat 정책, cadence·batch 최적값, forward-fix 내용, restore 실행과 고객 영향 조치는 사용자가 별도로 결정한다.
