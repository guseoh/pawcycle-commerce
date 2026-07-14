# SUBSCRIPTION-001 Backend 작업 보고서

## 작업 정보

- 작업 ID: `SUBSCRIPTION-001`
- 역할: Backend Engineer
- 기준 브랜치: `main`
- 작업 브랜치: `feat/be`
- 상태: 구현 및 로컬 보조 검증 완료, Java 25·MySQL 8.4 원격 검증 대기

## 작업 목적

API-003에서 승인된 구독 생성, 내 목록, 내 상세 API와 `subscriptions` migration을 하나의 Backend 수직 기능으로 구현한다.

## 입력 문서

- `docs/api/API-003-subscription-api-contract-decision-request.md`의 Approved D1~D8
- `docs/handoffs/API-003/tl-to-be.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/domain/glossary.md`
- `docs/domain/rules.md`

## 변경 범위

- `V2__create_subscriptions.sql` migration
- Subscription Entity, Repository와 application service
- 생성·목록·상세 request/read model과 Controller
- validation, SKU·소유권·endpoint별 안전 오류 mapping
- 서울 날짜와 구독 불변 조건 단위 테스트
- API·Security·MySQL 물리 계약·query 수 통합 테스트
- Frontend 인수인계

## 변경하지 않은 범위

- 구독 상태, 변경, 해지, 일시정지와 재개
- 주문, 결제, 재고, 배송과 자동 주문 생성
- 가격 snapshot과 결제 금액
- 동일 조건 unique, `Idempotency-Key`와 retry 보장
- Frontend, CI workflow, Discord와 인프라
- 신규 dependency와 API-003 승인 계약

## 주요 결과

- `POST /api/subscriptions`는 session principal의 `memberId`를 소유자로 사용하고 `201`로 `subscriptionId`, `nextOrderDate`만 반환한다.
- 생성일과 다음 주문 예정일은 주입한 `Clock`을 `Asia/Seoul`로 해석해 계산하며 휴일·주말 보정을 적용하지 않는다.
- 동일 회원의 동일 SKU·수량·배송 주기 요청도 요청마다 서로 다른 구독을 생성한다.
- `GET /api/subscriptions`는 본인 구독만 `subscriptionId DESC`로 반환하고 빈 결과를 `subscriptions: []`로 유지한다.
- `GET /api/subscriptions/{subscriptionId}`는 본인 소유 조건을 함께 사용하며 미존재·타인 소유·비숫자 ID를 같은 `SUBSCRIPTION_NOT_FOUND`로 반환한다.
- Entity는 API에 노출하지 않고 생성 결과와 목록·상세 read model로 변환한다.
- 목록·상세 Repository는 SKU와 Product를 fetch join해 각각 prepared statement 1개로 조회한다.

## API와 오류 계약

- 입력 누락·타입·수량·배송 주기 오류: `400 VALIDATION_FAILED`
- SKU 미존재: `404 SKU_NOT_FOUND`
- 구독 불가 SKU: `409 SKU_NOT_SUBSCRIBABLE`
- 조회할 수 없는 구독: `404 SUBSCRIPTION_NOT_FOUND`
- 예상하지 못한 생성·목록·상세 오류: `SUBSCRIPTION_CREATE_FAILED`, `SUBSCRIPTION_LIST_UNAVAILABLE`, `SUBSCRIPTION_DETAIL_UNAVAILABLE`
- 비필드 오류의 `fieldErrors`는 빈 배열이며 응답에 내부 예외, SQL, schema·table·column과 소유권 정보를 포함하지 않는다.
- application log는 endpoint 단위의 고정 문구만 추가하고 원문 `memberId`·`subscriptionId`를 기록하지 않는다.

## 트랜잭션과 실패 상태

- 생성 service method는 기본 read-write `@Transactional`이다.
- SKU 조회와 구독 가능 여부 검증 뒤 회원 reference와 구독을 저장하며, 예상하지 못한 실패 시 transaction 전체가 rollback된다.
- SKU 미존재·구독 불가 오류는 저장 전에 종료된다.
- 목록·상세 service method는 `@Transactional(readOnly = true)`다.
- 멱등성 저장소가 없으므로 timeout 뒤 재요청은 별도 구독을 만들 수 있으며 이는 API-003 D6에서 승인된 MVP 위험이다.

## 영속성과 migration

- `subscriptions`: `id`, `member_id`, `sku_id`, `quantity`, `delivery_cycle_weeks`, `created_date`, `next_order_date`
- 회원·SKU NOT NULL FK
- 수량 1~10, 배송 주기 2·4·8, `next_order_date > created_date` CHECK
- `(member_id, id)` 복합 인덱스
- 상태, 감사 시각, soft delete, 가격 snapshot, 동일 조건 unique와 추가 인덱스는 없다.
- 도메인은 DB의 날짜 순서보다 강한 `nextOrderDate = createdDate + deliveryCycleWeeks` 불변 조건을 검증한다.

## query 근거

- 목록 JPQL은 소유자 조건과 `id DESC` 정렬에 SKU·Product fetch join을 사용한다.
- 상세 JPQL은 `subscriptionId`와 `memberId`를 동시에 조건으로 사용하고 SKU·Product를 fetch join한다.
- MySQL 통합 API 테스트는 Hibernate Statistics prepared statement 수를 목록 1개, 빈 목록 1개, 상세 1개로 검증한다.
- 측정 근거 없는 추가 index나 cache는 도입하지 않았다.

## 검증 결과

- Java 21 임시 toolchain `compileTestJava`: 통과
- Java 21 임시 toolchain 구독·인증·상품 focused 단위 테스트 38개: 통과
- API·MySQL·Security·query 수 통합 테스트 소스 컴파일: 통과
- `git diff --check`: 통과
- `python scripts\validate-task-artifacts.py --task-id SUBSCRIPTION-001`: 통과

## 실행하지 못한 검증과 이유

- 로컬 Java 25 test·build: Java 25 toolchain이 설치되지 않았고 download repository가 구성되지 않았다.
- 로컬 MySQL 8.4 통합 테스트: Docker daemon이 실행되지 않고 datasource 환경 변수가 없어 격리 MySQL에 연결할 수 없었다.
- 위 검증은 Draft PR의 Repository Validation Java 25·MySQL 8.4 결과와 구분해 확인한다.

## 실패 후 수정

- 최초 Java 25 compile은 toolchain 부재로 소스 컴파일 전에 실패했다.
- 저장소에 포함되지 않는 임시 Java 21 init script로 전체 소스·테스트 컴파일을 확인했다.
- focused 단위 테스트의 첫 실행에서 공용 mock helper의 미사용 stub 2개가 Mockito strictness에 걸렸다. 해당 stub만 lenient로 제한하고 같은 focused 테스트를 재실행해 통과했다.

## 적용 방법

1. 기존 V1 schema에 Flyway V2를 적용한다.
2. AUTH-003 session으로 로그인하고 유효한 `X-CSRF-TOKEN`을 포함해 구독을 생성한다.
3. 생성 응답의 `subscriptionId`로 본인 상세를 조회한다.
4. 목록에서 본인 데이터, 내림차순과 빈 배열 계약을 확인한다.
5. Repository Validation의 migration·FK·CHECK·query 수·Security 회귀 결과를 확인한다.

## 위험과 제한

- timeout·retry는 중복 구독을 만들 수 있고 이번 MVP는 이를 허용한다.
- 상세의 `sku.price`는 현재 가격이며 가격 snapshot이나 결제 금액이 아니다.
- 배포 access log의 path parameter 마스킹은 후속 통합 QA에서 실제 환경 기준으로 확인해야 한다.
- 로컬 Java 25·MySQL 8.4 실행 근거는 없으며 원격 검증과 구분한다.

## 다음 작업

- Frontend가 승인된 session·CSRF 흐름과 세 API JSON을 연동한다.
- Frontend 완료 후 인증·상품·구독 전체 흐름을 독립 통합 QA로 검증한다.
- 사용자가 필수 검증과 리뷰를 확인한 뒤 병합 여부를 결정한다.

## Git 결과

- 작업 브랜치: `feat/be`
- 구현 commit은 본 보고서를 포함하므로 실제 SHA는 PR의 Git 이력을 원본으로 확인한다.
- reset, rebase, force push와 자동 병합을 사용하지 않는다.

## PR 상태

- `main` ← `feat/be` Draft PR을 필수 로컬 검증 후 생성한다.
- Repository Validation과 리뷰를 반영한 뒤 Ready 전환 여부를 판단한다.
- 원격 head, CI와 review 상태는 GitHub를 권위 있는 원본으로 확인한다.
