# SEC-LOG-001 Backend 작업 보고서

## 작업

- 작업 ID: `SEC-LOG-001`
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Backend Engineer / QA Engineer

## 목적

Backend 제품 로그에서 credential, token, session/CSRF, 결제·Billing key, raw request body와 불필요한 개인정보를 logger 인자로 직접 전달하지 못하도록 회귀 경계를 만든다. 확인된 Billing 성공 로그의 raw `memberId` 노출은 제거하되 API, DB와 제품 동작은 변경하지 않는다.

## 현재 로그 경계

- 제품 코드는 SLF4J parameterized logging을 사용한다.
- logger 호출의 값 인자에는 password/password hash, credential, email, 배송 개인정보, Billing·Payment key, session/CSRF, Authorization/Cookie, raw request body와 `memberId`를 직접 전달하지 않는다.
- 고정된 event 문구는 민감값을 포함하지 않는 범위에서 허용한다.
- 예상 외 exception은 기존과 같이 고정 event 문구와 stack trace를 기록한다. exception 객체의 message는 이 작업의 자동 redaction 대상이 아니다.

## 제거한 값

- `BillingApplicationService.prepare`의 성공 INFO에서 raw `memberId` 인자를 제거했다.
- `BillingApplicationService.complete`의 성공 INFO에서 raw `memberId` 인자를 제거했다.
- 두 성공 event 로그 자체는 운영 흐름 확인에 필요한 최소 event로 유지하며 식별자와 credential/token/key는 기록하지 않는다.

## 유지한 internal identifier와 이유

- `SubscriptionReconciliationApplicationService` 실패 로그의 `subscriptionId`를 유지한다.
- `SubscriptionOrderProcessor` 실패 로그의 `subscriptionId`와 `scheduleId`를 유지한다.
- 이 값들은 reconciliation/automation 실패 대상을 식별하고 재시도·복구 상태를 진단하는 내부 resource identifier다. 이 예외는 개인정보나 다른 식별자의 무제한 logging 승인이 아니다.

## 자동 회귀 검증

- `BillingApplicationServiceLoggingTests`는 sentinel 값으로 두 성공 경로를 실행해 event 로그는 남고 raw `memberId`, prepare token, auth key, customer key와 billing key는 출력되지 않음을 확인한다.
- `SensitiveLoggingContractTests`는 JDK AST로 Backend production Java source의 SLF4J logger 호출과 실제 값 인자만 검사한다. 일반 문자열 검색이 아니므로 logger 밖의 도메인 필드와 민감 용어가 포함된 고정 event 문구는 오탐으로 차단하지 않는다.
- 금지 범주는 password/password hash, credential, email, recipient name/phone, phone, postal code, address line, prepare/auth/billing/payment/customer key, session ID/JSESSIONID, CSRF token, Authorization/Cookie, raw request/body 객체와 `memberId`다.
- 테스트 자체가 각 금지 범주의 logger 인자를 검출하는지 확인하고, logger 밖의 민감 이름과 승인된 `subscriptionId`/`scheduleId` 실패 로그는 허용되는지 확인한다.
- 신규 dependency는 추가하지 않았다.

## exception redaction의 남은 한계

- 기존 exception stack trace logging은 제거하거나 변경하지 않았다.
- AUTH-004에서 확인한 것처럼 arbitrary exception message의 일반적인 민감정보 redaction은 자동 보장되지 않는다.
- 이번 정적·경로 검토에서 실제 credential/Secret이 exception message로 흘러가는 재현 가능한 제품 경로는 발견하지 못했다. 이는 전역 redaction 보장을 의미하지 않는다.
- 일반 redaction을 제공하려면 sanitizer/filter 또는 전역 logging architecture 결정이 필요하므로 이 작업에서는 DEFER한다.

## 검증 결과

- PASS — `BillingApplicationServiceLoggingTests`, `SensitiveLoggingContractTests`, `AuthExceptionHandlerTests` focused 실행
- PASS — 민감 logging 계약 테스트가 현재 production logger 호출에서 위반 없음 확인
- PASS — `git diff --check`
- NOT RUN — Backend 전체 test와 MySQL 기반 subscription 통합 테스트의 최종 판정은 Repository Validation에서 확인한다. 로컬 targeted 실행은 JDBC URL이 구성되지 않아 Spring context 시작 전에 실패했다.
- PENDING — Harness validation, Repository Validation 전체와 PR Metadata Validation은 Draft PR의 최종 HEAD에서 확인한다.

## 위험/제한

- AST 회귀는 SLF4J `Logger`의 `trace/debug/info/warn/error` 직접 호출 인자를 검사한다. logging framework 밖의 출력 경로나 runtime object의 내부 `toString()` 구현을 일반적으로 redaction하지 않는다.
- exception 객체를 stack trace와 함께 기록할 때 exception message에 임의 민감값이 포함되는 경우는 자동 redaction하지 않는다.
- 실제 Production 로그 수집·보존·권한 정책과 OCI 설정은 범위 밖이며 변경하지 않았다.
- API, DB schema/migration, Frontend와 제품 동작은 변경하지 않았다.

## CHANGE / KEEP / DEFER

- CHANGE — Billing 성공 INFO의 raw `memberId` 제거, logger 직접 민감 인자 회귀 테스트 추가
- KEEP — subscription reconciliation/automation 실패 로그의 `subscriptionId`·`scheduleId`, 기존 exception stack trace logging
- DEFER — arbitrary exception message의 일반 redaction, 전역 sanitizer/filter, 중앙 로그 수집·보존·권한 정책
