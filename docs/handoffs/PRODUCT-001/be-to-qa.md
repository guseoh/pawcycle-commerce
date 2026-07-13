# PRODUCT-001 Backend → QA 인수인계

## 전달 목적

API-002 공개 상품 목록·상세 구현의 독립 QA 검증 입력을 전달한다.

## 다음 역할 또는 대상 역할

- 수신: QA Engineer
- 후속 협업: Backend Engineer, Tech Lead

## 입력 문서

- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/handoffs/API-002/tl-to-be.md`
- `docs/reports/PRODUCT-001/be-report.md`

## 사용 가능한 결과

- `GET /api/products`
- `GET /api/products/{productId}`
- 승인 전용 목록·상세 read model
- 정확한 `PUBLIC` filter, 결정적 정렬과 endpoint별 오류 mapping

## 현재 상태

- 로컬 Java 21 compileJava·compileTestJava: 통과
- 로컬 Java 25와 격리 MySQL 환경은 없어 직접 실행하지 않음
- 2026-07-12 GitHub Actions Java 25·MySQL 8.4 Backend test·build: 통과
- 2026-07-12 GitHub Actions Frontend install·lint·build, conventions와 task artifact validator: 통과

## 검증 포인트

공개 상품 목록·상세 API의 계약, 공개 접근, 응답 구조, 빈 값, 정렬, 공개 상태 경계와 오류 응답을 QA가 독립 검증한다. 로컬에서는 Java 21 임시 환경으로 `compileJava`와 `compileTestJava`가 통과했고 Java 25·MySQL은 환경 부재로 직접 실행하지 않았다. Java 25 Backend test·build와 MySQL 8.4 통합 검증, Frontend install·lint·build 및 conventions는 GitHub Actions에서 완료됐으므로, QA는 동일한 CI 수행이 아니라 아래 계약·오류·경계·회귀 항목을 독립적으로 확인한다.

### 정상 시나리오

1. 비회원으로 목록과 상세를 호출해 `200`과 승인 JSON shape를 확인한다.
2. 로그인 session으로 같은 두 API를 호출해 동일한 응답 계약을 확인한다.
3. 네 GET 요청 모두 CSRF token 없이 성공하는지 확인한다.
4. 가격이 JSON string이 아닌 number인지 확인한다.
5. 응답에 `displayStatus`와 승인되지 않은 필드가 없는지 확인한다.

## 빈 값 시나리오

- 공개 상품이 없으면 `{ "products": [] }`다.
- SKU 없는 공개 상품은 목록·상세에서 제외되지 않는다.
- SKU 없는 목록 항목은 `skuPriceSummary.skuPrices=[]`, `hasSubscribableSku=false`다.
- SKU 없는 상세는 `skus=[]`다.
- nullable `description`, `thumbnailUrl`은 생략되지 않고 `null`이다.

## 공개 경계 시나리오

- 정확히 대문자 `PUBLIC`만 목록·상세에 공개된다.
- `public`, `Public`, 앞뒤 공백과 기타 상태는 목록에서 제외된다.
- 위 비공개 변형의 상세는 존재하지 않는 상품과 동일한 `404 PRODUCT_NOT_FOUND`다.
- 404의 message는 `상품을 확인할 수 없습니다.`, `fieldErrors`는 `[]`다.

## 정렬과 SKU 시나리오

- 상품은 `productId ASC`다.
- 목록 가격 배열과 상세 SKU는 `display_order ASC`, 동률이면 `id ASC`다.
- 구독 가능한 SKU가 하나 이상이면 `hasSubscribableSku=true`다.
- 구독 가능 SKU의 `availableDeliveryCycles`는 `[2, 4, 8]`이다.
- 구독 불가능 SKU의 `availableDeliveryCycles`는 `[]`다.

## 오류 시나리오

- 목록 예상 외 실패는 `500 PRODUCT_LIST_UNAVAILABLE`, message `상품 목록을 불러오지 못했습니다.`다.
- 상세 예상 외 실패는 `500 PRODUCT_DETAIL_UNAVAILABLE`, message `상품 정보를 불러오지 못했습니다.`다.
- 두 응답의 `fieldErrors`는 `[]`다.
- 응답에 내부 예외 메시지, SQL, schema·table·column과 stack trace가 없는지 확인한다.

## query 검증

- 여러 공개 상품과 SKU를 준비해도 목록 prepared statement 수가 최대 2인지 확인한다.
- 목록 결과가 비면 Product query 1개만 실행하는지 확인한다.
- 상세는 Product 1회 + SKU 1회로 최대 2개 query인지 확인한다.
- 상품 수만큼 SKU query가 반복되지 않는지 확인한다.

## Security 회귀

- 기존 login, csrf, me, logout 성공·실패 테스트가 유지되는지 확인한다.
- 공개 GET 때문에 `/api/auth/me` 등 보호 API가 익명 접근 가능해지지 않았는지 확인한다.
- 기본 `/logout` 비활성화와 승인된 `/api/auth/logout` 동작이 유지되는지 확인한다.

## 트랜잭션과 DB 영향

- 목록·상세 application method는 read-only transaction이다.
- DB schema와 Flyway migration 변경은 없다.
- Product·Sku 데이터 읽기만 수행한다.

## 미결정 사항 또는 승인 필요 항목

- PRODUCT-001 범위 안의 추가 결정은 없다.
- `petType` 허용값, `PUBLIC` 외 상태, 재고·판매·가격·통화·구독 정책은 별도 승인 없이는 확장하지 않는다.

## 남은 위험 또는 주의 사항

- 로컬 Java 25·MySQL 직접 실행 근거는 없으며, 해당 환경 검증은 GitHub Actions의 성공 이력으로 보완했다.
- QA는 실제 MySQL 8.4에서 확인된 collation 경계를 포함해 공개 상태 계약을 독립적으로 확인한다.

## 제외 범위와 중단 조건

- 구독·관리자·재고·결제·배송 API를 함께 검증 범위로 확장하지 않는다.
- migration, 신규 dependency, Frontend·CI 변경이 필요하면 QA를 중단하고 별도 작업으로 분리한다.
