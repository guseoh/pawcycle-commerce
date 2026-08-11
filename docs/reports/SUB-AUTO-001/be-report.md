# SUB-AUTO-001 Backend Engineer 보고서

## 작업

- 작업 ID: `SUB-AUTO-001`
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Backend Engineer

## 목적

승인된 PS-005/DOMAIN-003/ARCH-008/DATA-004 계약에 따라 due ACTIVE SubscriptionSchedule을 최소 정기 Order 한 건으로 원자 처리하고, 중복·실패·재실행·장기 중단·pending plan 변경에도 다음 미래 Schedule까지 안전하게 연결한다.

## 결과 또는 증거

- V9~V11 additive Flyway로 Order header, immutable item snapshot, Schedule별 unique constraint와 due query index를 추가했다.
- target별 `REQUIRES_NEW` transaction에서 Order/item snapshot, effective snapshot, pending promotion, future Schedule, version 증가를 함께 확정한다.
- reconciliation과 command 경로가 Order 없는 due Schedule을 소비하지 않도록 책임을 분리했다.
- JDK 25 Docker와 local MySQL 8.4에서 실패했던 Observability, automation, command, reconciliation 관련 네 테스트 클래스(30 tests)를 재실행해 통과했다.
- 익명-volume 격리 MySQL 8.4에서 `V9SubscriptionOrderMigrationIntegrationTests`와 `V2SubscriptionReconciliationIntegrationTests`를 통과시켜 V8 upgrade, fresh migration, unique constraint와 failure isolation을 검증했다.
- `python -m unittest scripts.test_validate_task_artifacts`(31 tests), `validate-task-artifacts.py --task-id SUB-AUTO-001 --task-grade 고위험 --execution-type "저장소 변경"`, Compose config, Grafana JSON, `promtool check rules`를 통과했다.
- Backend 전체 test는 165건 중 163건 통과했다. 남은 2건은 기존 `ProductionAuthSmokeMemberBootstrapProcessTests`가 Docker Desktop bind-mount에서 Hibernate classpath 초기화 중 60초 subprocess deadline을 초과한 환경 한정 실패다. thread dump에서 DB lock·automation trigger가 아니라 Hibernate 초기화 상태를 확인했고, 범위 밖 maintenance 코드는 변경하지 않았다.
- 격리 Compose 시도는 명시적 shared local volume 재사용 경고를 받아 즉시 중지했다. InnoDB lock으로 초기화·쓰기 전 차단된 것을 로그로 확인했고, 이후 익명-volume MySQL로 전환했다.
- Production/Cloud/운영 DB는 실행하지 않았다.

## 위험 또는 제한

- MySQL DDL은 non-transactional이므로 V9/V10/V11을 DDL 단위로 분리했다. 중간 실패 시 적용된 이전 version을 되돌리지 않으며 원인 수정 뒤 Flyway repair 경계를 확인한다.
- Production Scheduler 활성화, Production DB migration/deploy, 실제 결제·재고·배송은 수행하지 않았다.
- local Docker 기반 검증은 Production 검증이 아니다. Production cadence, threshold, escalation/repeat 정책은 확정하지 않았다.
- 전체 Backend test의 2건 process timeout은 local Docker Desktop bind-mount 성능 제한으로 미해결이며, CI에서의 required check 결과를 별도로 확인해야 한다.
- Draft PR #121의 본문은 repository validator 필수 구획에 맞춰 보정했습니다. 기존 실패 workflow rerun은 최초 PR event payload를 재사용하므로, 이 보고서 갱신 commit으로 새 PR CI를 시작해 최종 required check와 AI review를 재확인합니다.
