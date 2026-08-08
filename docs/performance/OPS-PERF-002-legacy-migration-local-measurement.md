# OPS-PERF-002 legacy migration local measurement

## 대상과 경계

- 기준: `main` 5b382294bde25df460c5eda864ea85fdc32a6297의 `LegacyMvp2MigrationService`
- 환경: Windows Docker Desktop, MySQL 8.4.10, Eclipse Temurin 25.0.3, Spring Boot test profile
- 실행 구분: 볼륨을 연결하지 않은 임시 local MySQL container에서 실제 migration service를 호출한 representative dry-run
- 제외: Production DB·Cloud·AWS·실제 source-write freeze·운영 부하 실행

저장소에는 Production legacy row 규모 근거가 없다. 따라서 기존 OPS-PERF-001 local performance fixture와 같은 100건을 비교 가능한 representative 경계로 선택했다. 이는 Production-sized fixture나 Production latency가 아니다. 기존 local integration DB에는 작업 외 legacy row 1건이 있어 첫 시도는 fixture write 전에 중단했고, 기존 row를 변경하지 않았다. 최종 표본은 같은 MySQL image와 migration V1~V3를 적용한 격리 container에서 수집했다.

## Migration 실행 시간

각 run은 고유 member 1건, product 1건, SKU 1건과 legacy subscription 100건을 먼저 commit했다. `preflight().valid()`를 확인한 뒤 `migrateAfterSourceWriteFreeze(true)` 호출 시작부터 transaction commit 반환까지를 측정했다. 호출 후에는 다음을 모두 확인하고 전용 fixture만 삭제했다.

- subscription 100건이 `mvp2_managed=true`, `ACTIVE`, current snapshot 보유, legacy API 비노출 상태
- snapshot·snapshot item·future schedule 각 100건
- migration-only plan 100건
- cleanup 후 전용 member와 legacy subscription 잔여 0건

Warm-up 2회 후 독립 fixture 7회 표본은 다음과 같다.

| 구분 | run | legacy rows | 실행 시간 (ms) | 정합성 | cleanup |
| --- | ---: | ---: | ---: | --- | --- |
| warm-up | 1 | 100 | 1282 | pass | pass |
| warm-up | 2 | 100 | 1018 | pass | pass |
| sample | 1 | 100 | 805 | pass | pass |
| sample | 2 | 100 | 821 | pass | pass |
| sample | 3 | 100 | 1027 | pass | pass |
| sample | 4 | 100 | 666 | pass | pass |
| sample | 5 | 100 | 671 | pass | pass |
| sample | 6 | 100 | 611 | pass | pass |
| sample | 7 | 100 | 674 | pass | pass |

대표값은 **median 674 ms**, observed range는 **611~1027 ms**다. Spring context 시작과 Flyway 적용 시간은 service 호출 구간 밖이므로 포함하지 않았다.

Rollback은 별도 1건 fixture에 같은 날짜의 schedule을 미리 넣어 migration DML을 실패시켰다. 호출 실패 후 subscription은 `mvp2_managed=false`, current snapshot은 null, 생성된 migration snapshot은 0건이어서 transaction 전체 rollback을 확인했다. 이후 전용 fixture cleanup도 성공했다.

## `FOR UPDATE` contention

V1~V3 schema를 적용한 격리 MySQL에서 전용 legacy subscription 1건을 사용했다. 독립 session A가 transaction을 시작하고 해당 row를 `SELECT ... FOR UPDATE`로 잠근 직후 MySQL advisory lock으로 readiness를 알린 다음 2초 동안 transaction을 유지했다. Session B는 readiness를 확인한 뒤 같은 row에 충돌 `UPDATE`를 실행했고, client wall-clock을 측정했다. Baseline은 session A 없이 같은 `UPDATE`를 실행했다. 각 조건은 5회다.

| 조건 | 표본 (ms) | median (ms) | observed range (ms) |
| --- | --- | ---: | ---: |
| baseline | 294.5, 395.7, 312.9, 248.7, 355.5 | 312.9 | 248.7~395.7 |
| row lock 충돌 | 1722.9, 1908.1, 1746.6, 1934.2, 1755.2 | 1755.2 | 1722.9~1934.2 |

충돌 session의 wall-clock median은 baseline보다 1442.3 ms 길었다. 이 값은 Docker command 시작 비용과 readiness polling 이후 남은 lock hold 시간을 함께 포함한 실제 blocking 관측값이며, migration transaction의 정확한 lock hold 시간은 아니다. 첫 동기화 없는 시도는 session A 시작 전에 session B가 끝나 contention을 입증하지 못했으므로 폐기했고 위 표에 포함하지 않았다.

현재 구현에서 subscription row lock은 preflight 이후 `FOR UPDATE` query가 실행될 때 획득되고 transaction commit까지 유지된다. 따라서 100건 migration service 전체 호출 시간 611~1027 ms는 이 fixture에서 lock hold의 상한이지만, lock 획득 시점 자체를 계측하지 않았으므로 정확한 hold time으로 표현하지 않는다. 독립 session 실험은 충돌 writer가 lock 해제까지 실제로 대기한다는 점을 확인한다.

## 안전성 판정과 한계

Representative local 100건에서는 모든 측정 run이 성공했고 post-migration 정합성, 의도된 실패의 전체 rollback, 실패 경로 cleanup을 확인했다. MySQL command wrapper는 실행 직후 native exit code를 검사했으며, 의도적인 invalid SQL이 non-zero 실패로 처리되는 것도 확인했다. Cleanup SQL은 첫 fixture write 전에 준비했고 전용 email/name/ID 범위만 삭제했으며, cleanup 오류가 있으면 원래 오류를 보존하도록 했다.

그러나 **이 결과만으로 Production migration 활성화를 승인할 수 없다.** 실제 Production legacy cardinality가 없고 migration은 row별 DML을 수행한다. 또한 source-write freeze가 불완전하면 충돌 writer가 transaction의 row lock 해제까지 block된다는 위험이 실제 관측됐다. 이번 측정만으로 구현을 최적화하거나 transaction 의미를 바꾸지 않았다.

운영 활성화 전에는 별도 승인 하에 다음이 필요하다.

- 실제 Production legacy row 수와 허용 maintenance window 비교
- 애플리케이션·DB 양쪽 source-write freeze 절차와 실패 시 복구 확인
- lock wait/blocked writer 관측, timeout 기준, migration 중단·rollback 판단자 지정
- Production과 같은 자원·데이터 분포에서의 실행 시간 및 lock hold 재검증

Production 실행, 운영 DB 접근, Cloud/AWS 실행은 하지 않았다.
