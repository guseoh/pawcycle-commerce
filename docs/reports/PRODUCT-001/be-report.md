# PRODUCT-001 Backend 작업 보고서

## 작업 정보

- 작업 ID: `PRODUCT-001`
- 역할: Backend Engineer
- 기준 브랜치: `main`
- 작업 브랜치: `feat/be`
- 상태: 구현 및 로컬 컴파일 완료, Java 25·MySQL 원격 검증 진행 전

## 목적

API-002에서 승인한 공개 상품 목록 `GET /api/products`와 상세 `GET /api/products/{productId}`를 기존 Product·Sku JPA와 session Security 기반 위에 구현한다.

## 입력 문서

- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/handoffs/API-002/tl-to-be.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/product/PS-002-first-mvp-requirements.md`

## 변경 범위

- 공개 상품 목록·상세 Controller와 application read model
- 정확한 `PUBLIC` 상품 조회와 상품·SKU 결정적 정렬
- 목록용 SKU batch 조회
- read-only application transaction
- 상품 404와 목록·상세별 안전한 500 mapping
- Product·Sku Entity의 응답 매핑용 최소 접근자
- 서비스 단위, API 오류, MySQL 통합·Security 회귀 테스트
- QA 인수인계

## 변경하지 않은 범위

- API-002 승인 계약과 D1~D7
- 인증·CSRF·session 동작
- DB schema·Flyway migration과 신규 dependency
- 구독, 재고, 판매, 할인, 결제, 통화와 배송 정책
- Frontend, CI와 인프라

## 주요 결과

- 비회원과 로그인 회원 모두 두 GET API를 CSRF token 없이 호출할 수 있다.
- 목록·상세 응답은 JPA Entity가 아닌 승인 필드만 가진 application read model을 반환한다.
- 목록은 상품 `id ASC`, SKU 가격은 `display_order ASC, id ASC` 순서다.
- 상세 SKU도 `display_order ASC, id ASC` 순서다.
- SKU 없는 공개 상품을 유지하고 `skuPrices`, `skus`는 빈 배열, `hasSubscribableSku`는 `false`다.
- nullable `description`, `thumbnailUrl`은 응답 필드의 JSON `null`로 유지한다.
- 구독 가능 SKU는 `[2, 4, 8]`, 구독 불가능 SKU는 `[]`를 반환한다.
- 비공개와 미존재 상세는 동일한 `404 PRODUCT_NOT_FOUND`를 사용한다.
- 예상하지 못한 목록·상세 실패는 내부 원인을 응답에 노출하지 않고 각각 승인된 500 코드로 변환한다.

## 계층과 트랜잭션

- API 계층은 URI와 응답 반환만 담당한다.
- application 계층은 공개 상품·SKU 조회, 응답 mapping과 오류 경계를 조율한다.
- repository 계층은 공개 filter와 계약상 순서를 SQL/query method로 보장한다.
- 목록과 상세 application method는 `@Transactional(readOnly = true)`다.
- 조회 작업만 수행하며 DB schema나 상태를 변경하지 않는다.

## 공개 상태 정확성

- MySQL 기본 collation의 대소문자 비교 완화를 피하기 위해 native query의 `BINARY p.display_status = 'PUBLIC'` 조건을 사용한다.
- `public`, `Public`, 앞뒤 공백과 기타 값은 목록에서 제외되고 상세에서는 미존재와 같은 404다.
- 새 enum, 허용 상태와 migration을 추가하지 않았다.

## 조회 방식과 query 근거

- 목록은 공개 Product 1회 조회 후 대상 product ID 전체의 SKU를 batch 1회 조회한다.
- 목록 상품 수와 무관하게 최대 2개 query이며 결과가 비면 Product query 1개만 실행한다.
- 상세는 공개 Product 1회와 해당 Product의 정렬된 SKU 1회로 최대 2개 query다.
- 서비스 단위 테스트가 repository 호출 횟수를 고정하고, MySQL 통합 API 테스트가 Hibernate Statistics의 prepared statement 수 `2`를 확인한다.
- N+1 검증을 위한 신규 dependency는 추가하지 않았다.

## 오류와 로그

- `PRODUCT_NOT_FOUND`: `404`, `상품을 확인할 수 없습니다.`, 빈 `fieldErrors`
- `PRODUCT_LIST_UNAVAILABLE`: `500`, `상품 목록을 불러오지 못했습니다.`, 빈 `fieldErrors`
- `PRODUCT_DETAIL_UNAVAILABLE`: `500`, `상품 정보를 불러오지 못했습니다.`, 빈 `fieldErrors`
- 500 응답에는 내부 예외, SQL, schema·table·column과 stack trace를 포함하지 않는다.
- 서버 로그는 상품 ID나 credential·session·CSRF 값을 새로 넣지 않고 안정적인 endpoint 메시지와 예외를 기록한다.

## 검증 결과

- Java 21 임시 로컬 toolchain의 `clean compileJava compileTestJava`: 통과
- API-002 계약 JSON과 구현 필드 수동 대조: 통과
- `git diff --check`: 통과
- `python scripts\validate-task-artifacts.py --task-id PRODUCT-001`: 통과

## 실패 후 수정

- 로컬 기본 Java 21에서는 프로젝트 Java 25 toolchain을 찾지 못해 기준 `test`가 실행 전 실패했다.
- 저장소를 변경하지 않는 임시 Java 21 init script로 컴파일과 focused test 실행을 시도했다.
- 소스·테스트 컴파일은 성공했으나 Gradle test worker가 기존 테스트를 포함한 모든 테스트 클래스를 `ClassNotFoundException`으로 로드하지 못했다.
- 같은 실행기 원인이 focused 재실행에서도 반복돼 추가 반복을 중단하고 Java 25·MySQL Repository Validation로 보완한다.
- Repository Validation run `29188054853`에서 Backend test 2건이 실패했다. 숫자가 아닌 `/api/products/test` path가 기존 Security 회귀 기대와 달리 `400`이 되었고, `availableDeliveryCycles`의 실제 JSON 배열을 Java `List.of(2, 4, 8)`과 직접 비교하는 JsonPath assertion이 실패했다.
- 첫 집중 수정에서 숫자가 아닌 상세 path를 `PRODUCT_NOT_FOUND`로 처리해 Security 회귀는 해소됐지만, run `29188140349`에서 동일 배열 직접 비교 assertion이 다시 실패했다. query-count 실패로 오인해 추가했던 SKU `JOIN FETCH`는 원인과 무관해 제거했다.
- 배열 계약은 변경하지 않고 길이 `3`과 인덱스별 값 `2`, `4`, `8`을 검증하도록 수정해 값과 순서를 안정적으로 확인한다. 수정 후 Java 25·MySQL focused·전체 재검증을 수행한다.

## 실행하지 못한 검증과 이유

- 로컬 Java 25 test·build: Java 25 toolchain이 설치되지 않았고 download repository가 구성되지 않았다.
- 로컬 MySQL 8.4 통합 실행: 승인된 격리 credential과 실행 환경이 없어 실행하지 않았다.
- 위 검증은 PR의 Repository Validation에서 Java 25와 MySQL 8.4로 수행한다.

## 적용 방법

1. 기존 V1 schema와 Product·Sku 데이터에서 정확히 `PUBLIC`인 상품을 준비한다.
2. 비회원과 로그인 회원으로 목록·상세 GET을 호출한다.
3. 승인 JSON shape, 정렬, 빈 배열·null, 가격 number와 배송 주기를 확인한다.
4. 비공개 변형과 미존재 상품의 동일 404, 예상 외 실패의 endpoint별 안전한 500을 확인한다.

## 위험과 제한

- `BINARY` 공개 비교는 현재 승인된 MySQL 기반 계약에 맞춘 최소 구현이다.
- 로컬 Java 25·MySQL 실행 근거는 없으며 원격 Repository Validation 성공 전 병합 준비 완료로 판단하지 않는다.
- 실제 운영 데이터의 `petType` 문자열은 새 검증 없이 그대로 전달된다.

## 다음 작업

- Repository Validation에서 Java 25 Backend test/build와 MySQL 8.4 통합 테스트를 확인한다.
- QA가 정상·빈 값·공개 경계·정렬·404·500·Security 회귀 시나리오를 독립 검증한다.
- Frontend 연동은 별도 승인 작업에서 API-002 계약을 사용한다.

## Git 결과

- 구현·검증 문서 commit과 PR 정보는 검증 후 기록한다.
- 일반 push만 사용하고 자동 병합하지 않는다.

## PR 상태

- `feat/be`에서 `main` 대상 PR을 생성하고 Ready for review 상태는 필수 검증 후 확정한다.
