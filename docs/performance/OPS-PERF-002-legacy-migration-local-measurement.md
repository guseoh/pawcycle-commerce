# OPS-PERF-002 legacy migration local measurement

- 작업 ID: OPS-PERF-002
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Platform/SRE

## 대상과 경계

- 대상: `LegacyMvp2MigrationService.migrateAfterSourceWriteFreeze(true)`의 실제 DML transaction과 `FOR UPDATE` query
- 실행: 볼륨 없는 전용 MySQL container에서 수행한 **isolated local migration execution**
- 환경: Windows Docker Desktop, MySQL 8.4.10, Eclipse Temurin 25.0.3, Spring Boot test profile
- 제외: Production DB·Cloud·AWS·실제 운영 source-write freeze·Production backup/restore

저장소에는 Production legacy row 규모 근거가 없다. 따라서 실행 시간은 기존 OPS-PERF-001과 같은 legacy subscription 100건을 representative local boundary로 유지했다. Lock footprint는 인위적인 sleep이나 제품 test hook 없이 실제 migration transaction을 관측하기 위해 legacy target 300건과 그 사이의 managed row 1건을 사용했다. 이는 Production-sized fixture나 Production latency/SLO가 아니다.

## 재현 방법과 provenance

재현 wrapper와 local-only harness는 다음과 같다.

- [`OPS-PERF-002-local-migration-measurement.ps1`](./OPS-PERF-002-local-migration-measurement.ps1)
- [`OpsPerf002MigrationMeasurementTests.java`](./fixtures/OpsPerf002MigrationMeasurementTests.java)

최종 run은 clean source commit `c4ca22fc77be9d470fc74b8a3ade575fcd11638d`, run ID `OPS-PERF-002-c4ca22f-run2`에서 실행했다.

```powershell
./docs/performance/OPS-PERF-002-local-migration-measurement.ps1 `
  -Rows 100 -Warmup 2 -Iterations 7 `
  -LockRows 300 -LockIterations 5 `
  -RunId OPS-PERF-002-c4ca22f-run2 `
  -OutputPath docs/performance/evidence/OPS-PERF-002/OPS-PERF-002-c4ca22f-run2.json
```

- MySQL image: `mysql:8.4.10`
- 실행 digest: `mysql@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6`
- 확인 version: `mysql  Ver 8.4.10 for Linux on x86_64 (MySQL Community Server - GPL)`
- 최종 raw evidence: [`OPS-PERF-002-c4ca22f-run2.json`](./evidence/OPS-PERF-002/OPS-PERF-002-c4ca22f-run2.json)
- 100-row lock 관측 창의 한계를 확인한 선행 raw evidence: [`OPS-PERF-002-97c7b3f-run1.json`](./evidence/OPS-PERF-002/OPS-PERF-002-97c7b3f-run1.json)

Raw evidence에는 source commit, fixture/run ID, script/harness SHA-256, Java/MySQL image digest, 모든 표본, post-validation, rollback assertion, cleanup, native exit 결과가 들어 있다. Harness JSONL schema에는 임의 필드를 추가하지 않고 후속 evidence 경로만 기존 `notes`에서 연결한다.

## Representative migration 실행 시간

각 run은 고유 member/product/SKU와 legacy subscription 100건을 commit한 뒤 `preflight().valid()`를 확인하고 실제 migration service 호출 시작부터 transaction commit 반환까지 측정했다. Warm-up 2회 후 독립 fixture 7회 결과는 다음과 같다.

| 구분 | 표본 (ms) | median (ms) | observed range (ms) |
| --- | --- | ---: | ---: |
| 최종 run2 | 1114.3, 1057.0, 870.5, 714.2, 756.7, 638.7, 809.2 | **809.2** | **638.7~1114.3** |

Warm-up은 1387.7 ms와 1105.3 ms였다. 모든 run에서 subscription 100건의 managed/ACTIVE/current snapshot/legacy API 비노출 상태, snapshot·snapshot item·future schedule 100건, cleanup 후 전용 member와 legacy row 0건을 확인했다.

이전 PR 표본은 보존되지 않은 임시 wrapper에서 median 674 ms(611~1027 ms)였고, 보존 wrapper run1은 median 893.8 ms(730.8~1082.7 ms)였다. 최종 판단은 source/digest/raw evidence가 연결된 run2의 809.2 ms를 우선한다. 차이는 local Docker/JVM/MySQL 실행 변동 범위이며 Production 변화로 해석하지 않는다.

Rollback probe는 legacy row 1건에 같은 날짜의 schedule을 미리 넣어 migration schedule insert를 실패시켰다. 실패 후 `mvp2_managed=false`, `current_snapshot_id=null`, migration snapshot 0건을 확인해 DML transaction 전체 rollback을 검증했고 전용 fixture cleanup도 성공했다.

## 실제 migration query의 lock footprint

각 300-row lock run은 legacy target row 사이에 `mvp2_managed=true` row 1건을 배치했다. Actual service migration을 별도 thread에서 시작하고 `performance_schema.data_locks`에 `subscriptions` RECORD lock이 보인 뒤 다음 세 writer를 독립 connection에서 동시에 실행했다.

1. legacy target row `UPDATE`
2. predicate 비대상 managed row `UPDATE`
3. 인접 auto-increment managed row `INSERT`

`performance_schema.data_lock_waits`의 requesting connection ID를 각 writer connection ID와 대조했다. 각 조건은 baseline 5회와 actual migration contention 5회다.

| operation | baseline median/range (ms) | contention median/range (ms) | 직접 wait attribution |
| --- | ---: | ---: | ---: |
| legacy target update | 0.9 / 0.8~3.5 | **1963.8 / 1882.2~2482.5** | 0/5 |
| managed row update | 0.8 / 0.6~2.1 | **1966.8 / 1882.3~2481.1** | 5/5 |
| adjacent managed insert | 24.0 / 10.1~27.3 | **2002.0 / 1888.6~2527.4** | 5/5 |

같은 run의 actual migration transaction 시간은 2532.2, 2145.2, 1924.0, 2000.6, 1898.5 ms(median 2000.6 ms)였다. Legacy target update는 5회 모두 transaction 완료 시점과 함께 풀리는 wall-clock blocking을 보였지만 짧은 polling 구간에서 requesting connection ID가 직접 포착되지는 않았다. 따라서 이 operation의 정확한 wait entry/hold time을 직접 측정했다고 주장하지 않는다.

반면 managed row update와 adjacent managed insert는 5/5 모두 실제 `data_lock_waits`에 직접 나타났다. 현재 schema에는 `mvp2_managed` index가 없고 REPEATABLE READ의 join/full-scan locking read가 predicate 비대상 record와 insert gap에도 영향을 주는 footprint가 실제 서비스 query에서 확인됐다. SKU/Product parent write 영향은 이번 probe에 포함하지 않았으므로 판정하지 않는다.

Writer duration은 lock 획득 시점부터의 정확한 hold time이 아니라 writer 시작·실행과 남은 migration transaction 시간을 포함한 blocking 관측값이다. 제품 코드에 sleep/test hook을 넣지 않았고 query·transaction·schema·index를 변경하지 않았다.

## 실패·cleanup·native command 경계

- Wrapper는 clean worktree와 exact source commit을 확인하고 tracked harness를 임시 Backend test source로 복사한다.
- MySQL/Java native command 직후 exit code를 검사하며 의도적인 invalid SQL이 non-zero로 처리되는지 먼저 검증한다.
- Fixture cleanup은 전용 member/product/SKU/subscription과 migration에서 생성한 하위 row만 대상으로 한다.
- 측정/HTTP가 아닌 Spring/SQL/Gradle 실패 시에도 `finally`에서 임시 test source, MySQL container, 전용 network를 정리한다.
- Cleanup 실패는 원래 실패를 덮지 않는다. 최종 run의 cleanup, raw evidence provenance validation, `final_legacy_rows=0`은 모두 pass였다.
- Docker volume을 생성·reset·삭제하지 않았고 기존 local integration DB의 작업 외 legacy row도 변경하지 않았다.

## 운영 위험과 DATA-003 복구 경계

Representative local execution은 성공했지만 **Production migration 활성화를 승인할 근거로 충분하지 않다.** 실제 Production cardinality가 없고 migration은 row별 DML을 수행한다. 더 중요하게, source-write freeze가 불완전하면 legacy target뿐 아니라 managed row writer와 인접 insert도 transaction 종료까지 block될 수 있다.

DATA-003은 DDL auto-commit과 DML transaction을 분리하고, 데이터 write 뒤 복구를 승인된 backup/restore 및 별도 변경 관리 경계에 둔다. 이번 local 측정은 Production backup/restore 가능성, recovery point, 복구 시간, DML 실패 후 backup 복원 여부를 검증하지 않았다.

Production 활성화 전에는 별도 고위험 승인 하에 다음을 검증해야 한다.

- 실제 Production legacy row 수와 허용 maintenance window
- application ingress와 DB 권한/lock을 함께 사용하는 source-write freeze
- 승인된 backup/restore 절차, 복구 지점과 restore 정합성
- DML transaction 실패 후 rollback만으로 충분한지 또는 backup 복구가 필요한지의 판단 절차와 책임자
- lock wait/blocked writer 관측, timeout, 중단·rollback 기준
- Production과 같은 자원·데이터 분포에서의 실행 시간과 lock hold

위 backup/restore·복구 판단 검증 전에는 Production 활성화를 승인하지 않는다. 데이터 손실 위험과 활성화 여부는 사용자와 운영 담당자가 다시 판단해야 한다. 이번 작업에서 Production DB/Cloud/AWS 접근, 실제 운영 source-write freeze, Production backup/restore는 수행하지 않았다.
