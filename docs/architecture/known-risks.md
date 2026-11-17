# 알려진 고위험 결함과 검증 공백

이 문서는 0단계 정리(2026-11-14)에서 코드와 문서로 확인한 **고위험 결함 후보와 아직 검증하지 않은 영역**을 모은 목록이다. 여기 있다고 해서 결함이 재현됐다는 뜻은 아니다. 각 항목은 고치기 전에 재현하거나 검증해야 하며, 결제·데이터·운영 결정이 걸린 항목은 별도 승인이 필요하다.

- 확인 기준: `main` `70af0c3d`
- 처리 계획: 통합 순서의 1단계(정합성)와 3단계(배포·보안 기반)

| # | 영역 | 내용 | 근거 | 상태 | 처리 단계 |
| --- | --- | --- | --- | --- | --- |
| R1 | 결제 | 일반 결제가 `PROCESSING`인 채로 앱이 중단되면(결제사 승인 직후 포함) 대사 대상이 아니다. 재고 예약과 주문이 그대로 남는다 | [#417](https://github.com/guseoh/pawcycle-commerce/issues/417), PR #415 재현 기록, [상태 전이](../domain/state-machines.md), [PAYMENT-RECOVERY-001](../runbook/PAYMENT-RECOVERY-001-processing-success.md) | **부분 해소**: 결제사 DONE이 확인된 경우의 성공 확정 유지보수 명령을 구현했다(PAYMENT-RECOVERY-002). 결제사 실패로 끝난 고립 결제의 실패 확정·예약 해제는 미구현 | 1단계(실패 확정은 별도 승인) |
| R2 | 스케줄러 | `@Scheduled` 작업에 분산 잠금이 없어, 인스턴스 두 개가 겹치면 같은 작업이 동시에 실행된다 | 작업별 동시 실행 테스트: 결제 만료 `twoWorkersSelectSameCandidateButOnlyOneExpiresAndReleases`, 정기결제 `billingChargeObservesCommittedClaimAndConcurrentProcessorCannotChargeTwice`, 재시도 `stockHoldCreatesNoAttemptThenConcurrentRetriesReuseOneGeneratedIdentity`, 구독 주문 `twoSchedulerInvocationsConvergeToOneDatabaseOrder`, 배송 알림 `twoConcurrentProcessorRunsCreateExactlyOneReminderPerSchedule`(CONSISTENCY-001 추가). 회차 보정은 구독별 `REQUIRES_NEW`+행 잠금+version 확인이고 기본 꺼짐 | **검증됨(중복 효과 없음)**. 지표 갱신 작업은 읽기 전용이다. 분산 잠금 도입은 필요 근거가 없다 | — |
| R3 | 인증 | 세션이 Tomcat 메모리에 있다(`spring-session` 없음). 재시작·배포 때 로그인이 풀릴 가능성이 높고, 인스턴스 두 개 이상으로 운영할 수 없다 | `backend/build.gradle`, `application.properties`의 `server.servlet.session.*` | 동작 미확인 | 3단계 |
| R4 | 보안·비용 | 처음 기록: Checkout·결제·검색·AI 엔드포인트에 요청 횟수 제한이 없고, 운영 nginx는 로그인만 IP당 분당 10회(burst 5)로 막았다. **저장소 변경(2026-11-17, 미배포)**: 운영 nginx(`nginx.https.conf`)에 IP 기준 `limit_req`를 `/api/auth/csrf`, 검색어가 있는 `GET /api/products`, checkout, Toss 결제 확인·빌링, interactions, AI 엔드포인트(상품 비교·리뷰 요약·개인화 추천)로 넓혔고, 로그인은 앱 메모리 버킷으로 계정(정규화 이메일)당 15분 안 실패 10회 시 15분 잠근다. 임계값은 2026-11-17 사용자가 확정했다. 남은 한계: IP 키라 NAT·통신사 공유 IP 사용자는 예산을 나눠 쓴다. 계정 잠금은 재시작 시 초기화되고 인스턴스 간 공유되지 않으며, 남의 계정을 일부러 잠그는 것이 가능하다(IP 제한이 속도를 묶는다). 회원 단위(로그인 후) 제한은 없다 | `infra/production/nginx.https.conf`의 zone 7개, `LoginAttemptLimiter`, [Edge 요청 제한 계약](../runbook/OPS-OCI-CD-02-release-recovery.md#edge-요청-제한과-access-log-계약-r4r11), `test-production-nginx.sh`·`LoginThrottleIntegrationTests` | **부분 해소(저장소)**. 운영 반영 전이며, 운영 `remote_addr`가 실제 클라이언트 IP인지와 정상 사용자 오탐률은 미확인 | 3단계(운영 반영은 #470), AI 활성화 전 임계값 재검토 |
| R5 | 구독 동시성 | 구독 명령이 원 설계(ARCH-007, 비관적 잠금 미도입)와 달리 `lockOwnedSubscription`으로 행 잠금을 잡는다 | PR #87 본문, ARCH-011(Accepted)의 `FOR UPDATE` 유지 명시, [ADR 색인](../adr/README.md) | **해소**: 구현 단계에서 바뀌고 ARCH-011로 승인된 변경이다 | — |
| R6 | 재고 | 여러 SKU를 담은 장바구니를 동시에 결제할 때의 잠금 순서와 초과 판매 | `CheckoutInventoryConcurrencyIntegrationTests`(CONSISTENCY-001): 재고 10개에 30명 동시 결제 → 10건 성공·20건 재고 부족, 음수·초과 판매 없음. SKU 2개를 반대 순서로 담은 20건 동시 결제 → 모두 성공, 데드락 없음. Checkout은 SKU 행을 `IN` 조회 한 번으로 잠근 뒤 예약한다 | **검증됨(결함 재현 안 됨)** | — |
| R7 | AI 입력 | 리뷰 요약은 사용자가 쓴 리뷰를 LLM 입력으로 쓴다. 프롬프트 인젝션 방어와 출력 검증 테스트가 없다. 기능 플래그는 기본 꺼짐이다 | `ReviewSummaryService`, `application.properties` | 미검증 | 8단계(활성화 전) |
| R9 | 데이터 보존 | 구독 멱등성 결과의 30일 보존·삭제 로직은 있지만 런타임 trigger가 없어 실제로는 삭제되지 않고 쌓인다. 정리 대상 건수 지표만 있다 | [OPS-IDEMP-001 보고서](../reports/OPS-IDEMP-001/be-report.md) "Scheduler trigger … 구현하지 않았다", `SubscriptionMetrics` | **실행 수단 추가(2026-11-16, #538)**: `SubscriptionIdempotencyCleanupTrigger`가 1시간마다 정리한다. 기본 꺼짐(`pawcycle.subscription.idempotency.cleanup.enabled`)이라 켜기 전까지는 여전히 쌓인다 | 운영에서 켤지는 별도 결정 |
| R10 | 결제 연동 | 운영 profile의 결제·환불·정기결제 어댑터는 모두 Unavailable(fail-closed)이라 운영에서 결제가 꺼져 있다. Toss 테스트 어댑터는 결제 승인·조회에만 있고, 환불·정기결제에는 없다. 테스트 환경 E2E는 아직 실행된 적이 없다(#228 Product Complete NO) | `TossUnavailablePaymentAdapter`·`TossUnavailableRefundAdapter`·`TossUnavailableBillingAdapter`(`!local-integration`), [PAYMENT-TOSS-TEST-001](../runbook/PAYMENT-TOSS-TEST-001-local-test-environment.md) | 확인됨. **테스트 환경 부분 검증(2026-11-15)**: 결제위젯 연동 키로 위젯 표시와 결제창 취소 직후 상태(S2)까지 확인. **실제 승인과 backend confirm(S1)은 미실행** — 카드 결제가 실제 카드사 인증 화면으로 이어져 중단 | 1단계 이후 결제 연동 |
| R11 | 개인정보·결제 | 처음 기록: 운영 nginx access log가 기본 combined 형식이라 query string까지 남아, Toss 결제 성공 redirect(`/checkout/success?paymentKey=...`)의 결제 키가 Docker 로그에 남을 수 있었다. **저장소 변경(2026-11-17, 미배포)**: `nginx.conf`·`nginx.https.conf`의 모든 server가 query string 없는 JSON access log(`$uri`, 요청 ID 포함)를 쓰고 `X-Request-ID`를 backend로 넘긴다. 배포 도구 지문(`NGINX_CONTRACT_SHA256`)을 새 설정으로 갱신했고, query를 남기던 이전 설정은 배포 도구가 거부한다. 남은 한계: nginx error log는 upstream 오류·요청 제한 거부 때 query를 포함한 요청 줄을 남길 수 있다. 이미 운영 Docker 로그에 남은 과거 기록은 다루지 않았다 | `infra/production/nginx.conf`, `nginx.https.conf`, `oci-release.py`, `test-production-nginx.sh`(결제 키 probe가 stdout·stderr 어디에도 없음), `test-oci-release.py` | **부분 해소(저장소)**. 운영 반영과 운영 로그 확인은 미실행 | 3단계(운영 반영은 #470) |
| R8 | 운영 문서 | README는 운영 대상이 "없음", Runbook 색인과 운영 개요는 "OCI app01"이다. 실제 운영 상태를 확인해야 정리할 수 있다 | `README.md`, `docs/runbook/README.md`, [운영 개요](production-operations-overview.md) | 보류(사용자 결정) | 0단계 후속 |

## 해결된 항목

| 항목 | 해결 |
| --- | --- |
| 상품 검색어의 `%`·`_`가 LIKE 와일드카드로 해석됨 | #524 (CATALOG-SEARCH-001) |
