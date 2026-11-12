# 성능 문서(Performance Documents)

이 디렉터리는 성능 실험, 측정 메모, 개선 보고서를 보관한다.

측정 가능한 성능 질문이 있을 때만 사용한다. 추측 기반 최적화(Optimization)를 정당화하는 용도로 사용하지 않는다.

성능 작업은 다음 순서를 따른다.

1. 기준 성능(Baseline) 측정
2. 병목 증거(Bottleneck Evidence) 수집
3. 가설(Hypothesis) 작성
4. 승인된 변경 또는 인수인계
5. 동일 조건에서 재측정
6. 개선 전후 보고

새 실험은 `docs/performance/experiment-template.md`를 기준으로 작성한다.

## 종료된 실험

| 실험 | 보존 자료 | 실행 상태 |
| --- | --- | --- |
| OPS-PERF-002 구독 migration | [측정 기록](OPS-PERF-002-legacy-migration-local-measurement.md), [당시 스크립트](OPS-PERF-002-local-migration-measurement.ps1), [fixture](fixtures/OpsPerf002MigrationMeasurementTests.java) | 현재 저장소에서 제거된 `LegacyMvp2MigrationService`를 사용한다. 당시 실행의 증거로 보존한다. |
| Phase 9 상품 조회 진단 | [실험 도구와 조건](../../infra/performance/phase9/README.md), [종료 기록](PERF-PH9-014-phase9-conclusion.md) | 최초 결과가 소비된 workload는 당시 계약에 따라 재실행하지 않는다. |
| Phase 10 Redis 및 구독 burst | [실험 스크립트](../../infra/performance/phase10/), [종료 기록](../reports/PERF-PH10-008/PERF-PH10-008-phase10-conclusion.md) | Redis After, burst Before, 10k baseline, scheduler tuning After는 종료된 실험이다. 최초 결과를 보존한다. |

[PERF-COMMERCE-003 보고서](../reports/PERF-COMMERCE-003/report.md)에서 참조한 변경 전 파일 경로는 [당시 InventoryService.java](https://github.com/guseoh/pawcycle-commerce/blob/873f144e93979d3c40612fc4abdbb1895986fbd3/backend/src/main/java/com/pawcycle/backend/commerce/InventoryService.java)에서 확인할 수 있다.
