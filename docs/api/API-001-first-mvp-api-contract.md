# API-001 첫 수직 MVP API 계약 설계

## 문서 상태

- 작업 ID: `API-001`
- 역할: Backend Engineer
- 결정 상태: Proposed API Contract
- 기준 입력: `PS-002`, `DOMAIN-001`, `UX-001`, `PS-003`, `UX-002`, `ARCH-001`, `DATA-001`

이 문서는 첫 번째 수직 MVP API 계약 후보를 정리한다. 사용자 승인 전까지 `Approved`로 표시하지 않는다.

## 작업 목적

상품 목록 조회, 상품 상세 조회, 구독 생성, 내 구독 목록 조회, 내 구독 상세 조회에 필요한 API 계약 후보를 문서화한다.

HTTP 메서드, URI 후보, 인증·인가 요구사항, 요청 필드, 응답 필드, 성공 상태, 오류 상태, 오류 코드 후보, 날짜 표현과 요구사항 추적성을 정리해 Frontend, Backend 구현, QA가 추측 없이 후속 작업을 진행할 수 있게 한다.

이번 작업은 구현 작업이 아니다. Controller, Service, Repository, JPA Entity, Flyway 마이그레이션, 테스트 코드, OpenAPI 생성 도구와 신규 의존성은 추가하지 않는다.

## 승인된 입력

- 비회원과 로그인 회원은 상품 목록과 상품 상세를 조회할 수 있다.
- 비회원과 로그인 회원은 SKU와 구독 가능 여부를 확인할 수 있다.
- 구독 생성, 내 구독 목록, 내 구독 상세는 로그인 회원만 사용할 수 있다.
- 구독 하나는 SKU 하나만 대상으로 한다.
- SKU는 구독 가능 여부를 가진다.
- 구독 생성 시 SKU가 존재하고 `subscribable=true`여야 한다.
- 수량은 1~10이다.
- 배송 주기는 2주, 4주, 8주다.
- 구독 생성 성공 후 생성된 구독 상세로 이동할 수 있도록 식별자를 제공해야 한다.
- 구독 생성 성공 후 서버가 계산한 다음 주문 예정일을 확인할 수 있어야 한다.
- 구독 생성 전에는 정확한 다음 주문 예정일 날짜를 표시하지 않는다.
- 날짜 계산과 표시는 `Asia/Seoul` 기준 날짜 단위다.
- 화면 날짜 표시는 `YYYY. M. D.`다.
- 휴일, 주말, 영업일 보정은 적용하지 않는다.
- 다음 배송 예정일은 표시하지 않는다.
- 첫 MVP에는 구독 상태, 결제, 재고, 배송, 구독 변경·해지·일시정지 기능이 없다.
- soft delete, hard delete, 삭제·탈퇴·보관·익명화 정책은 이번 API-001에서 확정하지 않고 후속 정책 설계로 미룬다.

## API 설계 범위

- `GET /api/products` 상품 목록 조회 후보
- `GET /api/products/{productId}` 상품 상세 조회 후보
- `POST /api/subscriptions` 구독 생성 후보
- `GET /api/subscriptions` 내 구독 목록 조회 후보
- `GET /api/subscriptions/{subscriptionId}` 내 구독 상세 조회 후보
- 공통 날짜 표현 후보
- 공통 오류 응답 후보
- 오류 코드 후보
- 인증·인가 요구사항
- 요구사항과 DATA-001 매핑

## 제외 범위

- Backend 구현
- Frontend 구현
- Flyway 마이그레이션
- JPA Entity
- Repository, Service, Controller
- 테스트 코드
- OpenAPI 생성 도구 또는 신규 의존성 추가
- Spring Security 구현 방식 확정
- 로그인 화면 구현
- Next.js 라우팅 구현
- 결제, 재고, 배송, 구독 상태 모델 추가
- soft delete, `deleted_at`, `is_deleted` 추가
- 관리자 기능
- 자동 주문 생성
- 성능 최적화
- CodeRabbit 설정 변경
- GitHub Actions 변경
- 자동 병합

## API 설계 원칙

- 첫 번째 수직 MVP의 상품 탐색, 구독 생성, 내 구독 조회에 필요한 최소 API만 제안한다.
- 공개 상품 탐색 API와 로그인 필요 구독 API를 분리한다.
- API 응답은 화면이 필요한 정보를 제공하되 결제, 재고, 배송, 구독 상태 정보를 포함하지 않는다.
- 구독 생성은 SKU 하나, 수량 1~10, 배송 주기 2/4/8, SKU 구독 가능 여부를 검증할 수 있어야 한다.
- 서버가 다음 주문 예정일을 계산하고 API는 서버 확정값을 반환한다.
- 생성 전 다음 주문 예정일을 클라이언트가 확정값처럼 계산하거나 표시하지 않도록 계약과 인수인계에 명시한다.
- 존재하지 않는 구독과 다른 회원 소유 구독은 사용자에게 같은 “조회할 수 없는 구독”으로 표현할 수 있게 설계한다.
- URI와 필드명은 후보이며 사용자 승인 전 최종 계약이 아니다.

## 공통 규칙

| 항목 | 후보 | 비고 |
| --- | --- | --- |
| Base path | `/api` | 구현 전 제안 |
| Content-Type | `application/json` | 요청 본문이 있는 API 기준 |
| 응답 날짜 | ISO-8601 local date 문자열 | 예: `2026-07-06`, 구현 포맷터가 필요하면 달력 연도 기준 `yyyy-MM-dd` |
| 화면 날짜 | `YYYY. M. D.` | FE 표시 책임 |
| 식별자 필드 | `productId`, `skuId`, `subscriptionId` | DB 컬럼명 강제 아님 |
| 금액 필드 | `price` | SKU 표시 가격, 결제 금액 아님 |
| 배송 주기 필드 | `deliveryCycleWeeks` | 값 후보: `2`, `4`, `8` |

할인, 총 결제 금액, 쿠폰, 재고, 품절, 배송 예정일, 구독 상태는 첫 MVP API 응답에 포함하지 않는다. SKU 표시 가격인 `price`는 상품 목록·상세와 구독 조회 응답 후보에 포함할 수 있다.

## 인증과 인가 규칙

| API | 인증 | 인가 | 설명 |
| --- | --- | --- | --- |
| 상품 목록 조회 | 불필요 | 불필요 | 비회원과 로그인 회원 모두 조회 가능 |
| 상품 상세 조회 | 불필요 | 불필요 | SKU와 구독 가능 여부도 공개 탐색 범위 |
| 구독 생성 | 필요 | 인증 회원 본인으로 생성 | 비회원은 생성 불가 |
| 내 구독 목록 조회 | 필요 | 본인 구독만 | 다른 회원 구독은 목록에 포함하지 않음 |
| 내 구독 상세 조회 | 필요 | 본인 구독만 | 존재하지 않는 구독과 다른 회원 소유 구독은 같은 표현 후보 |

Spring Security, 세션, 토큰, 쿠키, 로그인 복귀 경로 저장 방식은 API-001에서 구현하지 않는다.

## 날짜 표현 규칙

- 서버 계산 기준: `Asia/Seoul`
- 도메인 날짜 단위: 날짜
- API 날짜 표현 후보: ISO-8601 local date 문자열(예: `2026-07-06`)
- 구현 포맷터가 필요하면 달력 연도 기준 `yyyy-MM-dd`를 사용한다.
- 화면 표시 형식: `YYYY. M. D.`
- 다음 주문 예정일은 구독 생성일 + 배송 주기로 계산한다.
- 다음 배송 예정일은 API에 포함하지 않는다.
- 휴일, 주말, 영업일 보정은 하지 않는다.
- 정기 주문 자동 생성 트리거로 사용하지 않는다.

예시:

| 항목 | 값 후보 |
| --- | --- |
| `createdDate` | `2026-07-06` |
| `deliveryCycleWeeks` | `4` |
| `nextOrderDate` | `2026-08-03` |

## 공통 오류 응답 후보

오류 응답 구조 후보는 다음과 같다. 필드명과 세부 구조는 사용자 승인 전 최종 계약이 아니다.

| 필드 후보 | 의미 | 비고 |
| --- | --- | --- |
| `code` | 기계가 구분하는 오류 코드 후보 | 예: `AUTH_REQUIRED` |
| `message` | 사용자 또는 FE가 표시할 수 있는 기본 메시지 후보 | 최종 문구는 UX/FE에서 조정 가능 |
| `fieldErrors` | 필드별 오류 후보 | 입력 검증 오류에만 사용 |

필드 오류 후보:

| 필드 후보 | 오류 예 |
| --- | --- |
| `skuId` | 필수 입력 누락, 존재하지 않는 SKU, 구독 불가능 SKU |
| `quantity` | 필수 입력 누락, 1~10 범위 위반 |
| `deliveryCycleWeeks` | 필수 입력 누락, 2/4/8 외 값 |

## API 목록 요약

| API | 메서드 | URI 후보 | 인증 | 성공 상태 후보 | 주요 오류 후보 |
| --- | --- | --- | --- | --- | --- |
| 상품 목록 조회 | GET | `/api/products` | 불필요 | `200 OK` | `PRODUCT_LIST_UNAVAILABLE` |
| 상품 상세 조회 | GET | `/api/products/{productId}` | 불필요 | `200 OK` | `PRODUCT_NOT_FOUND` |
| 구독 생성 | POST | `/api/subscriptions` | 필요 | `201 Created` | `AUTH_REQUIRED`, `SKU_NOT_FOUND`, `SKU_NOT_SUBSCRIBABLE`, `REQUIRED_FIELD_MISSING`, `INVALID_QUANTITY`, `INVALID_DELIVERY_CYCLE` |
| 내 구독 목록 조회 | GET | `/api/subscriptions` | 필요 | `200 OK` | `AUTH_REQUIRED` |
| 내 구독 상세 조회 | GET | `/api/subscriptions/{subscriptionId}` | 필요 | `200 OK` | `AUTH_REQUIRED`, `SUBSCRIPTION_NOT_FOUND` |

## 상품 목록 조회 API

| 항목 | 내용 |
| --- | --- |
| 메서드 | `GET` |
| URI 후보 | `/api/products` |
| 인증 | 불필요 |
| 성공 상태 후보 | `200 OK` |
| 목적 | 비회원과 로그인 회원이 상품 목록과 SKU 구독 가능 요약을 확인한다. |

요청 쿼리:

- 첫 MVP에서는 검색, 정렬, 필터, 페이지네이션 정책을 확정하지 않는다.

응답 필드 후보:

| 필드 후보 | 의미 | DATA-001 매핑 |
| --- | --- | --- |
| `products` | 상품 목록 배열 후보 | `products` |
| `productId` | 상품 ID | `products.id` |
| `name` | 상품명 | `products.name` |
| `petType` | 대상 반려동물 | `products.pet_type` |
| `shortDescription` | 짧은 설명 | `products.short_description` |
| `thumbnailUrl` | 대표 이미지 | `products.thumbnail_url` |
| `hasSubscribableSku` | 구독 가능한 SKU 존재 여부 요약 | `skus.subscribable` |
| `skuPriceSummary` | SKU별 표시 가격 또는 가격 범위 후보 | `skus.price` |

오류 후보:

| 조건 | 상태 후보 | 오류 코드 후보 | 메시지 후보 |
| --- | --- | --- | --- |
| 일시적 상품 목록 조회 실패 | `500 Internal Server Error` | `PRODUCT_LIST_UNAVAILABLE` | 상품 목록을 불러오지 못했습니다. |

## 상품 상세 조회 API

| 항목 | 내용 |
| --- | --- |
| 메서드 | `GET` |
| URI 후보 | `/api/products/{productId}` |
| 인증 | 불필요 |
| 성공 상태 후보 | `200 OK` |
| 목적 | 비회원과 로그인 회원이 상품 상세, SKU 목록, SKU별 구독 가능 여부를 확인한다. |

경로 변수 후보:

| 필드 후보 | 필수 | 설명 |
| --- | --- | --- |
| `productId` | 예 | 조회할 상품 식별자 |

응답 필드 후보:

| 필드 후보 | 의미 | DATA-001 매핑 |
| --- | --- | --- |
| `productId` | 상품 ID | `products.id` |
| `name` | 상품명 | `products.name` |
| `petType` | 대상 반려동물 | `products.pet_type` |
| `description` | 상품 설명 | `products.description` |
| `thumbnailUrl` | 대표 이미지 | `products.thumbnail_url` |
| `displayStatus` | 노출 상태 후보 | `products.display_status` |
| `skus` | SKU 목록 | `skus` |
| `skuId` | SKU ID | `skus.id` |
| `skuName` | SKU명 또는 옵션명 | `skus.name` |
| `price` | SKU 표시 가격 | `skus.price` |
| `subscribable` | 구독 가능 여부 | `skus.subscribable` |
| `availableDeliveryCycles` | 선택 가능한 배송 주기 표현 후보 | 승인된 2/4/8 규칙 |

`availableDeliveryCycles` 후보는 상품별·SKU별 배송 주기 정책을 새로 만들지 않고, 첫 MVP의 공통 선택지 `2`, `4`, `8`을 표현한다.

오류 후보:

| 조건 | 상태 후보 | 오류 코드 후보 | 메시지 후보 |
| --- | --- | --- | --- |
| 존재하지 않는 상품 | `404 Not Found` | `PRODUCT_NOT_FOUND` | 상품을 확인할 수 없습니다. |

## 구독 생성 API

| 항목 | 내용 |
| --- | --- |
| 메서드 | `POST` |
| URI 후보 | `/api/subscriptions` |
| 인증 | 필요 |
| 성공 상태 후보 | `201 Created` |
| 목적 | 로그인 회원이 구독 가능한 SKU 하나, 수량, 배송 주기를 입력해 구독을 생성한다. |

요청 필드 후보:

| 필드 후보 | 필수 | 허용값 | 설명 |
| --- | --- | --- | --- |
| `skuId` | 예 | 존재하는 SKU | 구독 대상 SKU |
| `quantity` | 예 | 1~10 | 구독 수량 |
| `deliveryCycleWeeks` | 예 | 2, 4, 8 | 배송 주기 |

성공 응답 필드 후보:

| 필드 후보 | 의미 | DATA-001 매핑 |
| --- | --- | --- |
| `subscriptionId` | 생성된 구독 식별자, 상세 이동에 필요 | `subscriptions.id` |
| `nextOrderDate` | 서버가 계산한 다음 주문 예정일 | `subscriptions.next_order_date` |

성공 후 FE는 `subscriptionId`를 사용해 생성된 구독 상세로 이동할 수 있다. 생성 전에는 정확한 `nextOrderDate`를 표시하지 않는다.

오류 후보:

| 조건 | 상태 후보 | 오류 코드 후보 | 메시지 후보 |
| --- | --- | --- | --- |
| 인증 필요 | `401 Unauthorized` | `AUTH_REQUIRED` | 로그인이 필요합니다. |
| `skuId` 누락 | `400 Bad Request` | `REQUIRED_FIELD_MISSING` | 구독할 옵션을 선택해 주세요. |
| `quantity` 누락 | `400 Bad Request` | `REQUIRED_FIELD_MISSING` | 수량을 입력해 주세요. |
| `deliveryCycleWeeks` 누락 | `400 Bad Request` | `REQUIRED_FIELD_MISSING` | 배송 주기를 선택해 주세요. |
| 존재하지 않는 SKU | `404 Not Found` | `SKU_NOT_FOUND` | 선택한 옵션을 구독할 수 없습니다. |
| 구독 불가능 SKU | `409 Conflict` | `SKU_NOT_SUBSCRIBABLE` | 이 옵션은 현재 구독 대상으로 사용할 수 없습니다. |
| 수량 범위 위반 | `400 Bad Request` | `INVALID_QUANTITY` | 수량은 1개 이상 10개 이하로 입력해 주세요. |
| 배송 주기 허용값 위반 | `400 Bad Request` | `INVALID_DELIVERY_CYCLE` | 배송 주기는 2주, 4주, 8주 중에서 선택해 주세요. |

`409 Conflict`는 현재 리소스 상태인 `subscribable=false` 때문에 생성할 수 없다는 후보다. 최종 상태와 오류 코드 선택은 사용자 승인 전까지 Proposed 상태로 둔다.

## 내 구독 목록 조회 API

| 항목 | 내용 |
| --- | --- |
| 메서드 | `GET` |
| URI 후보 | `/api/subscriptions` |
| 인증 | 필요 |
| 인가 | 본인 구독만 조회 |
| 성공 상태 후보 | `200 OK` |
| 목적 | 로그인 회원이 자신의 구독 목록과 다음 주문 예정일을 확인한다. |

요청 쿼리:

- 첫 MVP에서는 검색, 정렬, 필터, 페이지네이션 정책을 확정하지 않는다.

응답 필드 후보:

| 필드 후보 | 의미 | DATA-001 매핑 |
| --- | --- | --- |
| `subscriptions` | 구독 목록 배열 후보 | `subscriptions` |
| `subscriptionId` | 구독 ID | `subscriptions.id` |
| `product` | 상품 요약 | `products` |
| `product.productId` | 상품 ID | `products.id` |
| `product.name` | 상품명 | `products.name` |
| `sku` | SKU 요약 | `skus` |
| `sku.skuId` | SKU ID | `skus.id` |
| `sku.skuName` | SKU명 또는 옵션명 | `skus.name` |
| `quantity` | 수량 | `subscriptions.quantity` |
| `deliveryCycleWeeks` | 배송 주기 | `subscriptions.delivery_cycle_weeks` |
| `nextOrderDate` | 다음 주문 예정일 | `subscriptions.next_order_date` |

오류 후보:

| 조건 | 상태 후보 | 오류 코드 후보 | 메시지 후보 |
| --- | --- | --- | --- |
| 인증 필요 | `401 Unauthorized` | `AUTH_REQUIRED` | 로그인이 필요합니다. |

빈 목록은 오류가 아니다. `200 OK`와 빈 배열 후보로 표현한다.

## 내 구독 상세 조회 API

| 항목 | 내용 |
| --- | --- |
| 메서드 | `GET` |
| URI 후보 | `/api/subscriptions/{subscriptionId}` |
| 인증 | 필요 |
| 인가 | 본인 구독만 조회 |
| 성공 상태 후보 | `200 OK` |
| 목적 | 로그인 회원이 자신의 구독 상세와 다음 주문 예정일을 확인한다. |

경로 변수 후보:

| 필드 후보 | 필수 | 설명 |
| --- | --- | --- |
| `subscriptionId` | 예 | 조회할 구독 식별자 |

응답 필드 후보:

| 필드 후보 | 의미 | DATA-001 매핑 |
| --- | --- | --- |
| `subscriptionId` | 구독 ID | `subscriptions.id` |
| `product` | 상품 정보 | `products` |
| `product.productId` | 상품 ID | `products.id` |
| `product.name` | 상품명 | `products.name` |
| `sku` | SKU 정보 | `skus` |
| `sku.skuId` | SKU ID | `skus.id` |
| `sku.skuName` | SKU명 또는 옵션명 | `skus.name` |
| `sku.price` | SKU 표시 가격 | `skus.price` |
| `quantity` | 수량 | `subscriptions.quantity` |
| `deliveryCycleWeeks` | 배송 주기 | `subscriptions.delivery_cycle_weeks` |
| `createdDate` | 구독 생성일 | `subscriptions.created_date` |
| `nextOrderDate` | 다음 주문 예정일 | `subscriptions.next_order_date` |

오류 후보:

| 조건 | 상태 후보 | 오류 코드 후보 | 메시지 후보 |
| --- | --- | --- | --- |
| 인증 필요 | `401 Unauthorized` | `AUTH_REQUIRED` | 로그인이 필요합니다. |
| 존재하지 않는 구독 | `404 Not Found` | `SUBSCRIPTION_NOT_FOUND` | 구독을 확인할 수 없습니다. |
| 다른 회원 소유 구독 접근 | `404 Not Found` | `SUBSCRIPTION_NOT_FOUND` | 구독을 확인할 수 없습니다. |

다른 회원 소유 구독은 `403 Forbidden` 대신 `404 Not Found` 후보로 표현해 존재 여부와 소유자 정보를 노출하지 않는다. 이 선택은 “조회할 수 없는 구독” 사용자 표현을 우선한 Proposed API Contract다.

## 오류 코드 후보

| 오류 코드 후보 | 상태 후보 | 적용 API | 설명 |
| --- | --- | --- | --- |
| `AUTH_REQUIRED` | `401 Unauthorized` | 보호 API | 로그인 필요 |
| `PRODUCT_NOT_FOUND` | `404 Not Found` | 상품 상세 | 존재하지 않는 상품 |
| `PRODUCT_LIST_UNAVAILABLE` | `500 Internal Server Error` | 상품 목록 | 일시적 상품 목록 조회 실패 후보 |
| `SKU_NOT_FOUND` | `404 Not Found` | 구독 생성 | 존재하지 않는 SKU |
| `SKU_NOT_SUBSCRIBABLE` | `409 Conflict` | 구독 생성 | 구독 불가능 SKU |
| `REQUIRED_FIELD_MISSING` | `400 Bad Request` | 구독 생성 | 필수 입력 누락 |
| `INVALID_QUANTITY` | `400 Bad Request` | 구독 생성 | 수량 1~10 위반 |
| `INVALID_DELIVERY_CYCLE` | `400 Bad Request` | 구독 생성 | 배송 주기 2/4/8 외 값 |
| `SUBSCRIPTION_NOT_FOUND` | `404 Not Found` | 내 구독 상세 | 존재하지 않거나 조회할 수 없는 구독 |

## 요구사항 추적성

| 요구사항 | 인수 조건 | API 계약 | 관련 데이터 설계 | 후속 구현 | 테스트 | 상태 |
| --- | --- | --- | --- | --- | --- | --- |
| REQ-PRODUCT-001 | AC-PRODUCT-001-01 외 | `GET /api/products`, 상품 ID·상품명·대상 동물·짧은 설명·SKU 가격·구독 가능 요약 | `products`, `skus` | BE, FE | 공개 목록 조회 | Proposed |
| REQ-PRODUCT-002 | AC-PRODUCT-002-01 외 | `GET /api/products/{productId}`, SKU 목록·가격·`subscribable`·배송 주기 후보 | `products`, `skus.subscribable`, `skus.price` | BE, FE | 공개 상세 조회, 존재하지 않는 상품 | Proposed |
| REQ-SUB-001 | AC-SUB-001-01 외 | `POST /api/subscriptions`, `skuId`, `quantity`, `deliveryCycleWeeks`, `subscriptionId`, `nextOrderDate` | `skus.subscribable`, `subscriptions` | BE, FE | 생성 성공·실패 조건 | Proposed |
| REQ-SUB-002 | AC-SUB-002-01 외 | `GET /api/subscriptions`, 본인 구독 목록 | `subscriptions(member_id, id)`, `skus`, `products` | BE, FE | 본인 목록·빈 목록 | Proposed |
| REQ-SUB-003 | AC-SUB-003-01 외 | `GET /api/subscriptions/{subscriptionId}`, 본인 구독 상세 | `subscriptions.member_id`, `skus`, `products` | BE, FE | 본인 상세, 조회 불가 구독 | Proposed |
| REQ-SUB-004 | AC-SUB-004-01 외 | `nextOrderDate`, `createdDate`, ISO-8601 local date 문자열 후보 | `created_date`, `next_order_date` | BE, FE | 날짜 계산·표시 기준 | Proposed |
| REQ-AUTH-001 | AC-AUTH-001-01 외 | 공개 API와 보호 API 분리, 보호 API `AUTH_REQUIRED` 후보 | `members.id` | 인증 설계, BE, FE | 비회원 보호 API 접근 | Proposed |
| REQ-AUTH-002 | AC-AUTH-002-01 외 | 다른 회원 구독 상세을 `SUBSCRIPTION_NOT_FOUND` 후보로 통합 | `subscriptions.member_id` | BE, QA | 소유자 정보 미노출 | Proposed |

## DATA-001 매핑

| API | DATA-001 개념 | 데이터 요구사항 |
| --- | --- | --- |
| 상품 목록 조회 | Product, SKU | 상품 표시 정보, SKU 구독 가능 요약, SKU 표시 가격 |
| 상품 상세 조회 | Product, SKU | 상품 상세, SKU 목록, `subscribable`, 선택 가능한 배송 주기 |
| 구독 생성 | Member, SKU, Subscription | 인증 회원, SKU 존재·구독 가능 여부, 수량, 배송 주기, 다음 주문 예정일 |
| 내 구독 목록 조회 | Member, Subscription, SKU, Product | `member_id` 기준 본인 목록, 상품/SKU 요약, 다음 주문 예정일 |
| 내 구독 상세 조회 | Member, Subscription, SKU, Product | `member_id` 소유자 검증, 단건 상세, 생성일, 다음 주문 예정일 |

## Frontend 구현으로 넘길 결정

- API URI 후보와 필드명은 Proposed 상태이므로 사용자 승인 후 구현한다.
- 상품 목록과 상품 상세는 인증 없이 호출할 수 있는 공개 API로 다룬다.
- 구독 생성, 내 구독 목록, 내 구독 상세는 인증 필요 API로 다룬다.
- 구독 생성 성공 후 `subscriptionId`를 사용해 생성된 구독 상세로 이동한다.
- 생성 전에는 `nextOrderDate`를 확정값처럼 표시하지 않는다.
- API 날짜 후보인 ISO-8601 local date 문자열을 화면에서 `YYYY. M. D.`로 표시한다.
- `SUBSCRIPTION_NOT_FOUND`는 존재하지 않는 구독과 다른 회원 소유 구독 모두에서 같은 사용자 표현으로 처리한다.
- 입력 오류는 `fieldErrors` 후보와 오류 코드 후보를 기준으로 필드 아래와 오류 요약에 연결한다.
- 로그인 복귀 경로 저장 방식과 FE 라우팅 구현 방식은 후속 인증/FE 설계에서 확정한다.

## Backend 구현으로 넘길 결정

- Controller, Request/Response DTO, Service, Repository 구현
- JPA Entity와 Flyway 마이그레이션
- 인증 컨텍스트에서 회원 식별자를 얻는 방식
- 구독 생성 트랜잭션 경계
- SKU 존재와 `subscribable=true` 검증
- `quantity` 1~10, `deliveryCycleWeeks` 2/4/8 검증
- `Asia/Seoul` 기준 날짜 공급과 다음 주문 예정일 계산
- `SUBSCRIPTION_NOT_FOUND`를 존재하지 않는 구독과 다른 회원 소유 구독에 동일하게 적용하는 조회 방식
- 실제 예외 클래스, Spring Security 설정, 필터, 인터셉터, 로그인 복귀 구현 방식

## QA 검증으로 넘길 기준

- 비회원과 로그인 회원 모두 `GET /api/products`를 조회할 수 있다.
- 비회원과 로그인 회원 모두 `GET /api/products/{productId}`에서 SKU와 구독 가능 여부를 확인할 수 있다.
- 존재하지 않는 상품은 `PRODUCT_NOT_FOUND` 후보로 표현된다.
- 비회원은 `POST /api/subscriptions`, `GET /api/subscriptions`, `GET /api/subscriptions/{subscriptionId}`를 사용할 수 없다.
- 로그인 회원은 구독 가능한 SKU, 수량 1~10, 배송 주기 2/4/8로 구독을 생성할 수 있다.
- 구독 생성 성공 응답에는 생성된 `subscriptionId`와 서버 확정 `nextOrderDate`가 포함된다.
- `skuId`, `quantity`, `deliveryCycleWeeks` 누락은 필드 오류로 표현된다.
- 존재하지 않는 SKU, 구독 불가능 SKU, 수량 범위 위반, 배송 주기 허용값 위반은 각각 구분된다.
- 내 구독 목록에는 본인 구독만 포함된다.
- 존재하지 않는 구독과 다른 회원 소유 구독은 같은 `SUBSCRIPTION_NOT_FOUND` 후보로 표현된다.
- 다음 배송 예정일, 구독 상태, 결제, 재고, 배송 데이터는 응답에 포함되지 않는다.

## Deferred Technical Decision

- OpenAPI 파일 생성 여부와 도구 선택
- 실제 Controller, DTO, Service, Repository 구조
- Spring Security, 세션, 토큰, 쿠키 구현 방식
- 로그인 복귀 경로 저장 방식
- Open Redirect 방지 구현
- Frontend 라우팅 구현 방식
- 실제 예외 클래스와 전역 예외 처리 방식
- 최종 오류 응답 JSON 세부 구조
- API 버전 전략
- 페이지네이션, 검색, 정렬, 필터 정책
- 삭제, 탈퇴, 보관, 익명화 정책

## 위험과 제한

- 이 문서는 Proposed API Contract이며 사용자 승인 전 최종 계약이 아니다.
- HTTP 상태와 오류 코드 후보는 구현 전 검토 대상이다.
- URI와 필드명은 후보이며 Frontend와 Backend 구현 전에 승인 필요하다.
- 인증·인가 구현 방식은 확정하지 않았다.
- soft delete, hard delete, 삭제·탈퇴·보관·익명화 정책은 API-001 범위에서 제외했다.
- 결제, 재고, 배송, 구독 상태가 후속 MVP에 추가되면 API 응답과 오류 코드 재검토가 필요하다.

## 후속 작업 순서

1. 사용자 검토로 API-001 Proposed API Contract 승인 또는 수정
2. 인증 설계: 로그인 복귀, 내부 GET 검증, Open Redirect 방지 결정
3. Backend 구현: Controller, DTO, 도메인/애플리케이션 서비스, JPA, Flyway, 테스트 작성
4. Frontend 구현: UX-002와 API-001에 맞춘 화면·상태·API 연동 구현
5. QA: API-001, DATA-001, UX-002 기준 테스트 계획과 검증 수행
