# OPS-IDEMP-001 Backend 작업 보고서

## 작업 정보

- 작업 ID: `OPS-IDEMP-001`
- 등급: 고위험
- 역할: Backend Engineer
- 실행 구분: 저장소 변경
- 기준: `origin/main` `687bab3a67c4b7865c20543924189455da1c7f5b`
- 작업 브랜치: `feat/be-OPS-IDEMP-001`
- 관련 Issue: `#107`, `#88`

## 목적과 결과

- V4에서 생성·관리 idempotency table에 nullable `completed_at DATETIME(6)`과 cleanup index를 추가했다.
- V3의 기존 row는 `response_status`가 2xx이고 `response_body`가 존재하는 성공 완료 결과만 V4 실행 시점의 단일 UTC 시각으로 backfill한다. reservation 등 불완전 row는 null로 보존한다.
- 신규 reservation은 `completed_at`을 생략해 null로 두고, 비즈니스 성공 응답 최종 update에서 같은 transaction 안에 `UTC_TIMESTAMP(6)`을 최초 기록한다. replay와 response body 보정 update는 완료 시각을 변경하지 않는다.
- 별도 `V2IdempotencyCleanupService`가 주입한 `Clock` 기준 30일 cutoff를 계산한다. `completed_at < cutoff`만 creation·command table별 양의 `batchSize`까지 삭제하고 삭제 수를 구분해 반환한다. 정확히 cutoff와 null row는 보존한다.
- API-004와 DATA-003에 30일 retention, replay 비연장, cleanup 후 새 요청 가능성, V4와 bounded cleanup 경계를 반영했다.

## 검증 증거

- 격리 환경: Eclipse Temurin Java `25.0.3`, MySQL `8.4.10` Docker container. Production·Cloud·AWS·운영 DB는 사용하지 않았다.
- 집중 테스트: V4 V3→V4 backfill/schema/index, 생성·command 완료 시각, replay 불변, 30일 경계, null 보존, table별 bounded delete·삭제 수, `V2SubscriptionIdempotencyConcurrencyIntegrationTests`를 함께 실행해 통과했다.
- V4 migration 테스트는 격리 test DB를 V3까지 구성하고 성공·불완전 fixture를 넣은 뒤 V4를 적용해 성공 row의 동일 UTC backfill, 불완전 row null, 두 cleanup index와 MySQL 8.4를 검증하고 최신 schema로 복구했다.
- Backend 전체 `cleanTest test`: 148개 중 146개 통과, 기존 `ProductionAuthSmokeMemberBootstrapProcessTests` 2개가 60초 process timeout으로 실패했다.
- 실패 클래스만 재실행: 7개 중 5개 통과, 같은 2개가 같은 process timeout으로 재실패했다. 반복 실행이나 범위 밖 수정은 중단했다.
- A/B baseline 비교: detached `origin/main` `687bab3a67c4b7865c20543924189455da1c7f5b` sibling worktree에서 같은 Java 25.0.3 image, MySQL 8.4.10 image, datasource와 Docker network 조건으로 `ProductionAuthSmokeMemberBootstrapProcessTests`를 1회 실행했다. 7개 중 5개가 통과하고 `successfulProcessForcesFlywayOffAndPrintsOnlyPassLine`, `duplicateProcessFailsWithoutChangingMemberOrRevealingDetails`가 branch와 같은 60초 `maintenance process completed` timeout으로 실패했다.
- 판정 A: 동일 실패가 OPS-IDEMP-001 변경이 없는 `origin/main`에서도 재현되므로 이번 변경과 독립적인 local/Docker environment baseline failure로 분류한다. timeout과 production-auth 테스트는 수정하지 않았고 임시 baseline worktree·container·network는 제거했다.
- OPS-IDEMP-001 집중 테스트 성공, Backend 전체 148개 중 146개 성공과 동일 baseline failure를 local 증거로 사용하며 GitHub `Backend and MySQL validation` 전체 테스트를 최종 gate로 둔다.
- `docker run ... python scripts/validate-task-artifacts.py --task-id OPS-IDEMP-001 --task-grade 고위험 --execution-type "저장소 변경"`: 통과했다.
- tracked 변경과 신규 파일별 `git diff --check`: 통과했다.

## 실패 후 수정

- 최초 MySQL container health command는 PowerShell quoting으로 `SELECT 1`이 image 인자로 분리돼 container 생성 전에 실패했다. health 확인을 `mysqladmin ping`으로 한 번 교정한 뒤 MySQL 8.4.10 기동과 집중 테스트가 통과했다.
- 전체 회귀 실패는 subscription·migration assertion이 아니라 유지보수 하위 Java process의 제한 시간 초과다. 동일 실패의 집중 재실행도 실패하여 승인된 검증 예산에 따라 추가 수정·반복을 하지 않았다.

## 미실행과 위험

- local 전체 회귀의 동일 실패는 A/B 기준선에서 기존 환경 failure로 확인했다. commit·push·Draft PR 이후 GitHub `Backend and MySQL validation` 성공 전에는 병합 준비 완료로 판정하지 않는다.
- Scheduler trigger, 운영 batch size, Micrometer/Actuator, retry/backoff, alert는 구현하지 않았다.
- Production migration, Production cleanup, Cloud·AWS 접근과 자동 merge는 수행하지 않았다.
- MySQL DDL은 비transactional이므로 실제 적용 전 백업·실행 창·실패 복구 절차의 별도 승인과 검증이 필요하다. 이번 결과는 격리 test DB 증거이며 Production 적용 증거가 아니다.
- cleanup이 삭제한 key는 승인 계약대로 새 요청으로 처리될 수 있으며, 실제 호출 주기와 batch size는 후속 Scheduler 정책으로 남는다.
