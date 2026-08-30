# MVP4-DATA-007 Production Customer Catalog one-shot import

## 목적과 경계

이 Runbook은 Canonical Customer Catalog를 기존 Production one-shot Catalog command로 선택할 수 있도록 준비된 SRE 실행 절차를 정의한다. Canonical Customer Catalog의 데이터 계약은 `docs/data/MVP4-DATA-005-canonical-customer-catalog.md`를 따른다. 즉 Data V1 baseline과 Customer Catalog V3 supplement를 하나의 논리 Catalog로 취급하며, 기대 합계는 Product 100 / SKU 166 / Brand 10 / Customer Category 27, DOG/CAT Product 각 50이다.

이번 `MVP4-DATA-007`은 **저장소 준비**다. script, 검증 계약과 Runbook을 준비하는 것까지가 범위이며, 이 변경의 검증 과정에서는 Production DB `validate` 또는 `apply`를 실행하지 않는다. 실제 Production DB 접근과 mutation은 별도 고위험 `실제 운영 실행` 승인 이후에만 수행한다.

기존 `infra/production/import-demo-catalog.sh` 파일명은 이전 운영 계약과 호출 경로의 호환성을 위해 유지한다. 기본 target도 기존과 동일한 `demo`다. Canonical Customer Catalog를 대상으로 할 때만 `--target customer`를 명시한다. 허용 target은 `demo|customer`뿐이며 그 외 값은 Docker one-shot 실행 전에 fail-closed 처리한다.

## 유지되는 안전 경계

Customer target은 새로운 DB 직접 접근 경로를 만들지 않고 PR #260에서 준비된 Production Catalog command를 사용한다. script는 기존과 동일하게 current release SHA와 image digest state를 검증하고, healthy Production Backend/MySQL과 internal data network를 확인하며, release lock과 보호된 runtime env 계약을 재사용한다. Secret 값은 출력하지 않는다.

`validate`는 dry-run이고 `apply`는 shell의 `--confirm-apply`와 Java command의 `confirm-apply=true`를 모두 통과해야 한다. 자동 apply와 자동 재시도는 없다. Customer Catalog importer는 기존 business key와 관계가 manifest와 충돌하면 덮어쓰지 않고 실패하며, apply는 baseline과 supplement를 하나의 transaction 경계에서 처리한다.

Catalog 검증은 기존 row를 `SELECT ... FOR UPDATE`로 읽을 수 있으므로 `validate`도 트랜잭션 종료 전까지 row lock을 보유할 수 있다. 실제 운영 실행은 승인된 저트래픽 유지보수 시간대에서 수행하고, lock timeout 또는 contention 실패 시 DB timeout을 임의로 늘리거나 바로 재시도하지 않는다.

## 실제 운영 실행 절차

아래 명령은 저장소 준비 검증에서 실행하지 않는다. 별도 고위험 실제 운영 실행 승인을 받은 뒤 현재 release와 운영 상태를 다시 확인하고 사용한다.

1. 현재 Production release SHA, Backend image repository, runtime/state directory, Backend/MySQL health와 data network 상태를 확인한다.
2. 먼저 Customer Catalog dry-run을 수행한다.

   ```bash
   sudo bash infra/production/import-demo-catalog.sh \
     --target customer \
     --operation validate \
     --sha <현재-40자-release-sha> \
     --backend-image ghcr.io/<owner>/<repository>-backend
   ```

3. 명령이 성공하고 `CUSTOMER_CATALOG_IMPORT_RESULT status=PASS` aggregate summary를 반환하는지 확인한다. baseline과 supplement summary 모두 성공이어야 한다. validation 결과가 불명확하거나 command가 non-zero로 종료되면 apply하지 않는다.
4. validation 결과와 현재 운영 상태를 검토한 뒤 **별도 적용 승인**을 받은 경우에만 apply를 수행한다.

   ```bash
   sudo bash infra/production/import-demo-catalog.sh \
     --target customer \
     --operation apply \
     --confirm-apply \
     --sha <현재-40자-release-sha> \
     --backend-image ghcr.io/<owner>/<repository>-backend
   ```

5. apply의 aggregate summary가 PASS인지 확인하고, Product 100 / SKU 166 / Brand 10 / Customer Category 27 및 DOG/CAT Product 각 50 계약을 별도 postflight에서 확인한다. 이후 실제 Home / PLP / PDP에서 Customer Catalog 노출을 확인한다.

## 실패와 중단 조건

다음 경우에는 apply를 시작하지 않거나 즉시 성공 판정을 중단한다.

- current release SHA 또는 approved Backend digest가 일치하지 않음
- Production Backend/MySQL이 running + healthy가 아님
- internal data network 또는 runtime/state 보호 계약이 유효하지 않음
- `customer` validate가 non-zero 또는 예상 aggregate PASS summary를 반환하지 않음
- business-key/relationship conflict 또는 lock contention 발생
- apply confirmation이 없거나 운영 상태가 validation 이후 변경됨
- 실행 세션 단절 등으로 transaction 결과를 확정할 수 없음

실행 중단이나 연결 단절은 성공으로 추정하지 않고 결과를 `UNKNOWN`으로 취급한다. one-shot process와 DB transaction 상태, aggregate postflight를 확인하기 전에는 apply를 재실행하지 않는다.

## 복구와 증거

Customer Catalog row를 직접 삭제하거나 역방향 SQL seed로 복구하지 않는다. 성공 apply 후 복구가 필요하면 기존 Production backup/restore와 rollback 절차를 사용하고 Product Owner/Tech Lead의 별도 결정을 따른다.

실제 Production 실행 시에는 `validate → 승인 → apply → aggregate postflight → 실제 화면 확인`의 결과와 실패·복구 여부를 별도 운영 증거로 남긴다. 저장소 CI Green은 Production Verified를 의미하지 않는다.
