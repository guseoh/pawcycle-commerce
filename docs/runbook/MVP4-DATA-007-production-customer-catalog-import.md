# MVP4-DATA-007 Production Customer Catalog one-shot import

## 목적과 경계

이 Runbook은 Canonical Customer Catalog를 Production one-shot Catalog command로 검증하고 적용하는 절차를 정의한다. Canonical Customer Catalog의 데이터 계약은 `docs/data/MVP4-DATA-005-canonical-customer-catalog.md`를 따른다. Data V1 baseline과 Customer Catalog V3 supplement를 하나의 논리 Catalog로 취급하며, 기대 합계는 Product 100 / SKU 166 / Brand 10 / Customer Category 27, DOG/CAT Product 각 50이다.

`infra/production/import-demo-catalog.sh` 파일명은 기존 호출 경로 호환성을 위해 유지한다. 기본 target은 `demo`이며 Canonical Customer Catalog를 대상으로 할 때만 `--target customer`를 명시한다. 허용 target은 `demo|customer`뿐이다.

저장소 준비와 실제 Production DB 실행은 분리한다. 저장소 변경·CI·PR 검증만으로 Production `validate` 또는 `apply`를 실행한 것으로 취급하지 않는다.

## 두 가지 Production identity 경로

Importer는 운영 환경이 실제로 사용하는 release identity 구조를 명시적으로 선택한다. 자동 fallback은 없다.

### `release-state` — 기존 관리형 release 계약

기본값이다. 다음 계약을 모두 요구한다.

- `/opt/pawcycle/state/current-sha`
- `/opt/pawcycle/state/<release-sha>.images`
- `/opt/pawcycle/runtime/current -> .bundle.*`
- 보호된 bundle의 `backend.env`
- bundle `backend.env`는 기존 관리형 release 계약의 single-quoted `KEY='value'` 형식을 유지
- GHCR Backend digest
- 실행 중 Backend의 SHA tag, `org.opencontainers.image.revision`, image ID가 승인 state와 일치

기존 AWS Production release-control 경로와 같이 위 state가 존재하는 환경에서 사용한다. 이 경로의 runtime env 형식을 OCI direct runtime 형식으로 자동 완화하지 않는다.

### `running-container` — 현재 OCI runtime identity 계약

OCI 초기 전환 환경처럼 기존 release-state marker를 만들지 않고 현재 Compose runtime을 직접 운영하는 경우에만 운영자가 명시적으로 `--identity-mode running-container`를 선택한다.

이 경로는 legacy state를 생성하거나 보완하지 않는다. 대신 다음 조건을 모두 fail-closed로 확인한다.

- `/opt/pawcycle/runtime/backend.env`가 일반 파일이고 mode `600`
- 현재 OCI의 direct `backend.env`인 unquoted `KEY=value` 형식을 허용한다. fully single-quoted `KEY='value'`도 방어적 호환으로 디코딩하지만 한쪽 quote만 있는 값은 잘못된 runtime 계약으로 거부한다.
- `--sha`로 전달한 40자 SHA가 현재 Production control Git repository에 실제 commit으로 존재하고 현재 control history에 포함됨
- 실행 중 Backend가 정확히 하나이고 `running + healthy`
- 실행 중 Backend image reference가 `<backend-image>:<sha>`와 정확히 일치
- Backend Compose `working_dir`와 `config_files` label이 실행 중인 importer와 같은 `infra/production` source를 가리킴
- 해당 local image tag의 image ID와 실행 중 Backend container의 image ID가 정확히 일치
- Backend에 연결된 non-internal network가 정확히 하나이고 그 `database-egress` network의 container member가 Backend 하나뿐임

검증이 끝나면 one-shot은 tag를 다시 해석하지 않고 이미 확인한 **실행 중 Backend image ID**를 직접 사용한다. 따라서 preflight 이후 local tag가 바뀌어도 다른 image를 실행하는 경로로 확장하지 않는다.

현재 OCI Production처럼 `current-sha`, `<sha>.images`, `runtime/current`, revision label이 없는 환경을 통과시키기 위해 이 파일들을 임의 생성하거나 symlink를 만들면 안 된다.

## 공통 안전 경계

두 identity 경로 모두 새로운 DB 직접 접근 경로를 만들지 않는다.

- healthy Production Backend에 연결된 유일한 non-internal database-egress network를 사용한다.
- Production MySQL 접속 정보는 보호된 Backend runtime env에서만 one-shot에 전달한다.
- Secret 값은 출력하지 않는다.
- subscription automation은 one-shot에서 강제로 비활성화한다.
- one-shot은 read-only root filesystem, tmpfs, non-root user, capability drop, memory/CPU/PID 제한을 유지한다.
- 기존 release lock을 사용해 다른 Production release command와 겹치지 않게 한다.

`validate`는 dry-run이고 `apply`는 shell의 `--confirm-apply`와 Java command의 `confirm-apply=true`를 모두 통과해야 한다. 자동 apply와 자동 재시도는 없다. Customer Catalog importer는 기존 business key와 관계가 manifest와 충돌하면 덮어쓰지 않고 실패하며, apply는 baseline과 supplement를 하나의 transaction 경계에서 처리한다.

Catalog 검증은 기존 row를 `SELECT ... FOR UPDATE`로 읽을 수 있으므로 `validate`도 transaction 종료 전까지 row lock을 보유할 수 있다. 실제 운영 실행은 승인된 저트래픽 유지보수 시간대에서 수행하고, lock timeout 또는 contention 실패 시 DB timeout을 임의로 늘리거나 바로 재시도하지 않는다.

## 실제 운영 실행 절차 — 현재 OCI `running-container`

현재 OCI runtime이 `running-container` 계약을 사용한다면 먼저 읽기 전용으로 실제 Backend identity를 확인한다. SHA와 image repository는 현재 실행 중 Backend에서 확인한 값만 사용한다.

1. 현재 Production Backend가 healthy인지 확인한다.
2. Backend image reference의 40자 SHA가 Git commit으로 존재하는지 확인한다.
3. Backend가 사용하는 Compose source, direct runtime `backend.env`, database-egress network와 member count를 확인한다.
4. 먼저 Customer Catalog dry-run을 수행한다.

```bash
sudo bash infra/production/import-demo-catalog.sh \
  --target customer \
  --operation validate \
  --identity-mode running-container \
  --sha <현재-Backend-image-tag의-40자-Git-SHA> \
  --backend-image <현재-Backend-image-repository>
```

5. 명령이 성공하고 `CUSTOMER_CATALOG_IMPORT_RESULT status=PASS` aggregate summary를 반환하는지 확인한다. baseline과 supplement summary가 모두 성공이어야 한다. validation 결과가 불명확하거나 command가 non-zero로 종료되면 apply하지 않는다.
6. validation 결과와 현재 운영 상태를 검토한 뒤 **별도 적용 승인**을 받은 경우에만 apply를 수행한다.

```bash
sudo bash infra/production/import-demo-catalog.sh \
  --target customer \
  --operation apply \
  --identity-mode running-container \
  --confirm-apply \
  --sha <validate에서 확인한-동일-SHA> \
  --backend-image <validate에서 확인한-동일-image-repository>
```

7. apply의 aggregate summary가 PASS인지 확인하고 Product 100 / SKU 166 / Brand 10 / Customer Category 27 및 DOG/CAT Product 각 50 계약을 별도 postflight에서 확인한다.
8. 실제 Home / PLP / PDP에서 Customer Catalog 노출을 확인한다.

validate 이후 Backend container, image ID, Compose source 또는 운영 상태가 달라졌다면 기존 validate를 apply 승인 근거로 재사용하지 않는다.

## 기존 `release-state` 실행

관리형 release state가 존재하는 환경에서는 기본 identity mode와 기존 quoted bundle env 계약을 유지한다.

```bash
sudo bash infra/production/import-demo-catalog.sh \
  --target customer \
  --operation validate \
  --sha <현재-40자-release-sha> \
  --backend-image ghcr.io/<owner>/<repository>-backend
```

apply는 별도 승인 뒤 `--operation apply --confirm-apply`를 사용한다.

## 실패와 중단 조건

다음 경우에는 apply를 시작하지 않거나 즉시 성공 판정을 중단한다.

- 선택한 identity mode의 필수 runtime/state 계약이 존재하지 않거나 권한이 다름
- `backend.env`의 필수 key가 누락·중복되거나 허용되지 않은 key, 선택한 identity mode와 맞지 않는 값 인코딩 또는 잘못된 quote 형식이 존재함
- 전달한 SHA가 실행 중 Backend image tag와 일치하지 않음
- `running-container`에서 SHA가 Production control Git history에 없거나 Compose source label이 현재 source와 다름
- local image ID와 실행 중 Backend image ID가 다름
- Production Backend가 running + healthy가 아니거나 Backend의 non-internal database-egress network가 정확히 하나가 아님
- database-egress network membership이 Backend 하나가 아님
- `customer` validate가 non-zero 또는 예상 aggregate PASS summary를 반환하지 않음
- business-key/relationship conflict 또는 lock contention 발생
- apply confirmation이 없거나 validation 이후 runtime identity가 변경됨
- 실행 세션 단절 등으로 transaction 결과를 확정할 수 없음

실행 중단이나 연결 단절은 성공으로 추정하지 않고 결과를 `UNKNOWN`으로 취급한다. one-shot process와 DB transaction 상태, aggregate postflight를 확인하기 전에는 apply를 재실행하지 않는다.

## 복구와 증거

Customer Catalog row를 직접 삭제하거나 역방향 SQL seed로 복구하지 않는다. 성공 apply 후 복구가 필요하면 승인된 Production backup/restore 절차를 사용하고 Product Owner/Tech Lead의 별도 결정을 따른다.

실제 Production 실행 시에는 `preflight → validate → 별도 승인 → apply → aggregate postflight → 실제 화면 확인`의 결과와 실패·복구 여부를 운영 증거로 남긴다. 저장소 CI Green은 Production Verified를 의미하지 않는다.
