# API-003 Tech Lead → Backend Engineer 인수인계

## 전달 목적

승인된 API-003 D1~D8을 기준으로 구독 생성·내 목록·내 상세 API 3개와 migration, 도메인과 테스트를 하나의 Backend 수직 작업으로 구현한다.

## 다음 역할 또는 대상 역할

- 대상 역할: Backend Engineer
- 상태: `Ready for Implementation`
- 승인 일자: `2026-07-13`
- 승인 근거: 사용자 입력 `API-003 D1~D8 전체 추천안 승인`
- 승인 계약: `docs/api/API-003-subscription-api-contract-decision-request.md`

PR #39 병합이 아니라 사용자 명시 승인이 구현 근거다. API-001과 DATA-002 원본 전체는 계속 `Proposed`이며 API-003이 승인한 구독 범위만 구현 입력으로 사용한다.

## 입력 문서

- `docs/api/API-003-subscription-api-contract-decision-request.md`
- `docs/reports/API-003/tl-report.md`
- `docs/handoffs/API-003/tl-to-po.md`
- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/reports/AUTH-004/qa-report.md`

## 구현 범위

- `POST /api/subscriptions` — `201 Created`, session 인증과 유효한 CSRF token
- `GET /api/subscriptions` — `200 OK`, 빈 결과 `subscriptions: []`, `subscriptionId DESC`
- `GET /api/subscriptions/{subscriptionId}` — `200 OK`
- 현재 session principal의 `memberId`로 소유자를 결정하고 request body에서 회원 ID를 받지 않는다.
- 미존재·타인 소유·비숫자 상세 ID는 동일한 `404 SUBSCRIPTION_NOT_FOUND`로 처리한다.
- 생성은 원자적 transaction, 목록·상세는 read-only transaction으로 구현한다.
- `Asia/Seoul` 날짜 계산을 테스트 가능한 `Clock` 또는 동등한 최소 구조로 격리한다.
- 필요한 방향의 JPA 관계만 사용하고 N+1과 query 수를 확인한다.

## 생성·조회 계약

- 생성 필수 입력: `skuId`, `quantity`, `deliveryCycleWeeks`
- 생성 성공 응답: `subscriptionId`, `nextOrderDate`
- 날짜: ISO-8601 local date, 다음 주문 예정일은 서버가 `Asia/Seoul` 기준으로 계산
- 목록: Product·SKU 요약, 수량, 배송 주기, 다음 주문 예정일
- 상세: 목록 정보에 현재 가격과 생성일 추가
- 가격 snapshot, 구독 상태와 배송 예정일은 포함하지 않는다.

## 오류와 보안

- 공통 오류 필드: `code`, `message`, `fieldErrors`
- 입력 오류: `400 VALIDATION_FAILED`
- SKU 미존재: `404 SKU_NOT_FOUND`
- 구독 불가 SKU: `409 SKU_NOT_SUBSCRIBABLE`
- 조회 불가 구독: `404 SUBSCRIPTION_NOT_FOUND`
- endpoint별 승인된 안전한 500 오류 코드를 사용한다.
- 내부 예외·SQL·schema·table·column·stack trace와 소유자 정보를 응답에 노출하지 않는다.
- status·body·유의미한 timing 차이와 server log로 소유권·존재 여부를 불필요하게 구분하지 않는다.
- application log의 원문 `memberId`·`subscriptionId`를 마스킹하고 배포 access log의 path parameter 마스킹은 통합 QA에서 확인한다.

## 데이터와 migration

- 새 Flyway migration으로 `subscriptions`를 추가한다.
- 필드: `id`, `member_id`, `sku_id`, `quantity`, `delivery_cycle_weeks`, `created_date`, `next_order_date`
- 회원·SKU NOT NULL FK
- `quantity` 1~10 CHECK
- `delivery_cycle_weeks` 2·4·8 CHECK
- 날짜 NOT NULL과 `next_order_date > created_date` CHECK
- `(member_id, id)` 인덱스
- `created_at`, `updated_at`, 상태, soft delete, 가격 snapshot과 동일 조건 unique는 추가하지 않는다.

## 중복 요청 계약

- 동일 회원의 동일 SKU·수량·배송 주기 복수 구독을 허용한다.
- 유효 요청 1회마다 구독 1건을 생성한다.
- Idempotency-Key와 중복 요청 저장소를 도입하지 않는다.
- Frontend는 처리 중 제출을 비활성화한다.
- timeout·retry 중복 생성 위험은 승인된 MVP 위험이다. 후속 요구가 생기면 별도 승인 작업에서 멱등성을 결정한다.

## 사용 가능한 결과

- D1~D8 `Approved` API·DB·보안·구현 계약
- Product Owner 승인 완료 기록
- API 3개를 하나로 구현할 수 있는 범위·테스트·제외·중단 기준

## 미결정 사항 또는 승인 필요 항목

없음. 승인 범위 안에서는 별도 API 결정 없이 구현을 시작할 수 있다. 범위 밖 결정이 필요하면 중단 조건을 따른다.

## 검증 포인트

- 생성 domain/service 단위 테스트와 `Clock` 기반 날짜 경계
- MySQL 8.4 migration·FK·CHECK·transaction 통합 테스트
- 생성·목록·상세 성공 계약과 JSON shape
- validation·SKU 오류·동일 404·안전 500 오류 계약
- 익명 접근, POST CSRF 누락·오류, 본인·타인 소유 Security 회귀
- 동일 조건 복수 생성과 요청 1회당 1건 생성
- 목록 빈 배열·내림차순, 목록·상세 N+1 방지와 query 수
- 공개 상품 목록·상세 GET 익명 접근 회귀

## 제외 범위

- 구독 상태·변경·해지·일시정지
- 주문·결제·재고·배송과 가격 snapshot
- Idempotency-Key, 신규 dependency와 API별 작업 분할
- Frontend, Docker·Health Check, Discord 알림과 CI workflow
- API별 별도 QA PR

## 남은 위험 또는 주의 사항

- D6의 timeout·retry 중복 생성 위험은 승인됐으며 구현에서 임의로 멱등성을 추가하지 않는다.
- 실제 migration type·제약 이름은 승인 의미를 바꾸지 않는 범위에서 기존 MySQL convention을 따른다.
- 배포 access log 마스킹은 후속 통합 QA에서 실제 환경 기준으로 재확인한다.

## 작업 순서와 후속 역할

1. Backend Engineer가 API 3개와 migration을 하나의 PR에서 통합 구현·검증한다.
2. Platform/SRE가 Discord 상세 알림 개선을 별도 작업으로 병렬 수행한다.
3. 공개 상품·구독 Frontend를 구현한다.
4. 인증·상품·구독 전체 흐름을 독립 통합 QA로 한 번 검증한다.

## 중단 조건

- 승인 계약과 구현 가능한 코드·schema가 충돌함
- 새로운 제품·email·cookie·CORS·API·DB 결정이 필요함
- API-001·DATA-002 전체 승인 또는 승인된 D1~D8 변경이 필요함
- 신규 dependency, CI workflow나 범위 밖 인프라 변경이 필요함
- 재현 가능한 보안 문제 또는 민감정보 노출이 발견됨
- destructive Git 작업이 필요함

중단 시 제품 코드를 우회 수정하지 말고 충돌 항목, 재현 근거와 필요한 사용자 결정을 Tech Lead에게 인계한다.
