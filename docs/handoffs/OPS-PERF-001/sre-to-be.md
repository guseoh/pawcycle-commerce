# OPS-PERF-001 Backend 성능 개선 요청

`plans()`과 `subscriptions()`에서 page item 수에 선형 비례한 SQL 증가를 실제 local measurement로 확인했다. 상세 수치는 `docs/performance/OPS-PERF-001-local-query-measurement.md`를 따른다.

- `plans()`: page 100에서 observed query count median 212. `planDto()`가 version별 `plan_items`, `plan_version_delivery_cycles`를 개별 조회한다.
- `subscriptions()`: page 100에서 observed query count median 411. `subscriptionSummaryDto()`가 subscription별 pet, snapshot, snapshot item, next schedule을 개별 조회한다.

Backend 범위에서 API·도메인·DB schema·의존성 변경 없이 필요한 related rows를 batch 조회하는 최소 변경을 검토한다. 동일 100-item fixture, page size 10/20/100, warm-up 3회와 9회 반복으로 before/after를 재측정하고, 효과 또는 회귀가 없으면 변경을 revert한다.
