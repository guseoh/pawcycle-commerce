# 알려진 고위험 결함과 검증 공백

이 문서는 0단계 정리(2026-11-14)에서 코드와 문서로 확인한 **고위험 결함 후보와 아직 검증하지 않은 영역**을 모은 목록이다. 여기 있다고 해서 결함이 재현됐다는 뜻은 아니다. 각 항목은 고치기 전에 재현하거나 검증해야 하며, 결제·데이터·운영 결정이 걸린 항목은 별도 승인이 필요하다.

- 확인 기준: `main` `70af0c3d`
- 처리 계획: 통합 순서의 1단계(정합성)와 3단계(배포·보안 기반)

| # | 영역 | 내용 | 근거 | 상태 | 처리 단계 |
| --- | --- | --- | --- | --- | --- |
| R1 | 결제 | 일반 결제가 `PROCESSING`인 채로 앱이 중단되면(결제사 승인 직후 포함) 대사 대상이 아니다. 재고 예약과 주문이 그대로 남는다 | [#417](https://github.com/guseoh/pawcycle-commerce/issues/417), PR #415 재현 기록, [상태 전이](../domain/state-machines.md), [PAYMENT-RECOVERY-001](../runbook/PAYMENT-RECOVERY-001-processing-success.md) | **부분 해소**: 결제사 DONE이 확인된 경우의 성공 확정 유지보수 명령을 구현했다(PAYMENT-RECOVERY-002). 결제사 실패로 끝난 고립 결제의 실패 확정·예약 해제는 미구현 | 1단계(실패 확정은 별도 승인) |
| R2 | 스케줄러 | `@Scheduled` 작업에 분산 잠금이 없어, 인스턴스 두 개가 겹치면 같은 작업이 동시에 실행된다 | 작업별 동시 실행 테스트: 결제 만료 `twoWorkersSelectSameCandidateButOnlyOneExpiresAndReleases`, 정기결제 `billingChargeObservesCommittedClaimAndConcurrentProcessorCannotChargeTwice`, 재시도 `stockHoldCreatesNoAttemptThenConcurrentRetriesReuseOneGeneratedIdentity`, 구독 주문 `twoSchedulerInvocationsConvergeToOneDatabaseOrder`, 배송 알림 `twoConcurrentProcessorRunsCreateExactlyOneReminderPerSchedule`(CONSISTENCY-001 추가). 회차 보정은 구독별 `REQUIRES_NEW`+행 잠금+version 확인이고 기본 꺼짐 | **검증됨(중복 효과 없음)**. 지표 갱신 작업은 읽기 전용이다. 분산 잠금 도입은 필요 근거가 없다 | — |
| R3 | 인증 | 세션이 Tomcat 메모리에 있다(`spring-session` 없음). 재시작·배포 때 로그인이 풀릴 가능성이 높고, 인스턴스 두 개 이상으로 운영할 수 없다 | `backend/build.gradle`, `application.properties`의 `server.servlet.session.*` | 동작 미확인 | 3단계 |
| R4 | 보안·비용 | 로그인, Checkout, AI 엔드포인트에 요청 횟수 제한이 없다. AI 기능을 켜면 호출 비용이 통제되지 않는다 | rate limit 구현 0건 | 확인됨 | 3단계(AI 활성화 전 필수) |
| R5 | 구독 동시성 | 구독 명령이 원 설계(ARCH-007, 비관적 잠금 미도입)와 달리 `lockOwnedSubscription`으로 행 잠금을 잡는다 | PR #87 본문, ARCH-011(Accepted)의 `FOR UPDATE` 유지 명시, [ADR 색인](../adr/README.md) | **해소**: 구현 단계에서 바뀌고 ARCH-011로 승인된 변경이다 | — |
| R6 | 재고 | 여러 SKU를 담은 장바구니를 동시에 결제할 때의 잠금 순서와 초과 판매 | `CheckoutInventoryConcurrencyIntegrationTests`(CONSISTENCY-001): 재고 10개에 30명 동시 결제 → 10건 성공·20건 재고 부족, 음수·초과 판매 없음. SKU 2개를 반대 순서로 담은 20건 동시 결제 → 모두 성공, 데드락 없음. Checkout은 SKU 행을 `IN` 조회 한 번으로 잠근 뒤 예약한다 | **검증됨(결함 재현 안 됨)** | — |
| R7 | AI 입력 | 리뷰 요약은 사용자가 쓴 리뷰를 LLM 입력으로 쓴다. 프롬프트 인젝션 방어와 출력 검증 테스트가 없다. 기능 플래그는 기본 꺼짐이다 | `ReviewSummaryService`, `application.properties` | 미검증 | 8단계(활성화 전) |
| R9 | 데이터 보존 | 구독 멱등성 결과의 30일 보존·삭제 로직은 있지만 런타임 trigger가 없어 실제로는 삭제되지 않고 쌓인다. 정리 대상 건수 지표만 있다 | [OPS-IDEMP-001 보고서](../reports/OPS-IDEMP-001/be-report.md) "Scheduler trigger … 구현하지 않았다", `SubscriptionMetrics` | 확인됨(의도된 보류) | 2단계(관측과 함께 실행 방식 결정) |
| R10 | 결제 연동 | 운영 profile의 결제·환불·정기결제 어댑터는 모두 Unavailable(fail-closed)이라 운영에서 결제가 꺼져 있다. Toss 테스트 어댑터는 결제 승인·조회에만 있고, 환불·정기결제에는 없다. 테스트 환경 E2E는 아직 실행된 적이 없다(#228 Product Complete NO) | `TossUnavailablePaymentAdapter`·`TossUnavailableRefundAdapter`·`TossUnavailableBillingAdapter`(`!local-integration`), [PAYMENT-TOSS-TEST-001](../runbook/PAYMENT-TOSS-TEST-001-local-test-environment.md) | 확인됨. 테스트 환경 검증 절차 준비됨, 실행 대기(키 필요) | 1단계 이후 결제 연동 |
| R8 | 운영 문서 | README는 운영 대상이 "없음", Runbook 색인과 운영 개요는 "OCI app01"이다. 실제 운영 상태를 확인해야 정리할 수 있다 | `README.md`, `docs/runbook/README.md`, [운영 개요](production-operations-overview.md) | 보류(사용자 결정) | 0단계 후속 |

## 해결된 항목

| 항목 | 해결 |
| --- | --- |
| 상품 검색어의 `%`·`_`가 LIKE 와일드카드로 해석됨 | #524 (CATALOG-SEARCH-001) |
