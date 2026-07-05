# API-001 Backend → Frontend/QA 인수인계

## 전달 목적

Frontend가 첫 번째 수직 MVP 화면·상태·API 연동을 구현할 때 필요한 API 계약 후보를 전달한다.

QA가 정상 흐름, 실패 흐름, 인증·인가, 입력 검증, 날짜 기준 시나리오를 만들 때 필요한 API 기준을 전달한다.

## 승인된 입력

- 상품 목록과 상품 상세는 비회원과 로그인 회원 모두 조회할 수 있는 공개 기능이다.
- SKU와 구독 가능 여부 확인은 공개 탐색 범위다.
- 구독 생성, 내 구독 목록, 내 구독 상세는 로그인한 회원만 사용할 수 있다.
- 구독 하나는 SKU 하나만 대상으로 한다.
- SKU는 구독 가능 여부를 가진다.
- 구독 생성 시 SKU가 존재하고 `subscribable=true`여야 한다.
- 수량은 1~10이다.
- 배송 주기는 2주, 4주, 8주다.
- 구독 생성 성공 후 생성된 구독 상세로 이동할 수 있도록 식별자를 제공해야 한다.
- 구독 생성 성공 후 서버가 계산한 다음 주문 예정일을 확인할 수 있어야 한다.
- 구독 생성 전에는 정확한 다음 주문 예정일 날짜를 표시하지 않는다.
- 날짜 계산과 표시는 `Asia/Seoul` 기준 날짜 단위다.
- API 날짜 표현 후보는 ISO-8601 local date 문자열이다.
- 화면 날짜 표시는 `YYYY. M. D.`다.
- 휴일, 주말, 영업일 보정은 적용하지 않는다.
- 다음 배송 예정일은 표시하지 않는다.
- 결제, 재고, 배송, 구독 상태, 삭제 정책은 첫 MVP API 계약에서 제외한다.

## API 계약 요약

| API | 메서드 | URI 후보 | 인증 | 성공 상태 후보 |
| --- | --- | --- | --- | --- |
| 상품 목록 조회 | `GET` | `/api/products` | 불필요 | `200 OK` |
| 상품 상세 조회 | `GET` | `/api/products/{productId}` | 불필요 | `200 OK` |
| 구독 생성 | `POST` | `/api/subscriptions` | 필요 | `201 Created` |
| 내 구독 목록 조회 | `GET` | `/api/subscriptions` | 필요 | `200 OK` |
| 내 구독 상세 조회 | `GET` | `/api/subscriptions/{subscriptionId}` | 필요 | `200 OK` |

주요 오류 코드 후보:

| 오류 코드 후보 | 의미 |
| --- | --- |
| `AUTH_REQUIRED` | 보호 API 인증 필요 |
| `PRODUCT_LIST_UNAVAILABLE` | 상품 목록 조회 실패 |
| `PRODUCT_NOT_FOUND` | 존재하지 않는 상품 |
| `SKU_NOT_FOUND` | 존재하지 않는 SKU |
| `SKU_NOT_SUBSCRIBABLE` | 구독 불가능 SKU |
| `REQUIRED_FIELD_MISSING` | 필수 입력 누락 |
| `INVALID_QUANTITY` | 수량 범위 위반 |
| `INVALID_DELIVERY_CYCLE` | 배송 주기 허용값 위반 |
| `SUBSCRIPTION_NOT_FOUND` | 존재하지 않거나 조회할 수 없는 구독 |

## Frontend 구현 입력

- API-001은 Proposed API Contract다. 사용자 승인 전 실제 구현에 고정하지 않는다.
- 상품 목록과 상품 상세는 인증 없이 호출할 수 있는 공개 API로 다룬다.
- 구독 생성, 내 구독 목록, 내 구독 상세는 인증 필요 API로 다룬다.
- 상품 목록 응답 후보는 상품 ID, 상품명, 대상 반려동물, 짧은 설명, 대표 이미지, SKU 구독 가능 요약, SKU별 표시 가격을 포함한다.
- 상품 상세 응답 후보는 상품 설명, SKU 목록, SKU별 가격, 구독 가능 여부, 선택 가능한 배송 주기 표현을 포함한다.
- 구독 생성 요청 후보는 `skuId`, `quantity`, `deliveryCycleWeeks`다.
- 구독 생성 성공 응답 후보는 `subscriptionId`, `nextOrderDate`다.
- 생성 성공 후 `subscriptionId`를 사용해 생성된 구독 상세로 이동한다.
- 구독 생성 전에는 정확한 `nextOrderDate`를 표시하지 않는다.
- 내 구독 목록은 `subscriptionId`, 상품 요약, SKU 요약, 수량, 배송 주기, 다음 주문 예정일을 표시할 수 있어야 한다.
- 내 구독 상세는 `subscriptionId`, 상품, SKU, 수량, 배송 주기, 구독 생성일, 다음 주문 예정일을 표시할 수 있어야 한다.
- API 날짜 후보인 ISO-8601 local date 문자열을 화면에서 `YYYY. M. D.`로 표시한다.
- `SUBSCRIPTION_NOT_FOUND`는 존재하지 않는 구독과 다른 회원 소유 구독 모두 같은 “구독을 확인할 수 없습니다.” 상태로 처리한다.
- 로그인 복귀 경로 저장 방식과 프론트엔드 라우팅 구현 방식은 후속 인증/FE 설계를 따른다.

## QA 검증 입력

공개 상품 조회:

- 비회원과 로그인 회원 모두 `GET /api/products`를 호출할 수 있다.
- 비회원과 로그인 회원 모두 `GET /api/products/{productId}`를 호출할 수 있다.
- 상품 목록에는 상품 ID, 상품명, 대상 반려동물, 짧은 설명, SKU 표시 가격, 구독 가능 SKU 요약 후보가 포함된다.
- 상품 목록 조회 실패는 `PRODUCT_LIST_UNAVAILABLE` 후보로 표현된다.
- 상품 상세에는 SKU 목록, SKU별 가격, `subscribable`, 선택 가능한 배송 주기 2/4/8 표현 후보가 포함된다.
- 존재하지 않는 상품은 `PRODUCT_NOT_FOUND` 후보로 표현된다.

구독 생성:

- 비회원은 `POST /api/subscriptions`를 사용할 수 없고 `AUTH_REQUIRED` 후보로 표현된다.
- 로그인 회원은 존재하며 `subscribable=true`인 SKU, 수량 1~10, 배송 주기 2/4/8로 구독을 생성할 수 있다.
- 성공 응답에는 생성된 `subscriptionId`와 서버 확정 `nextOrderDate`가 포함된다.
- `skuId`, `quantity`, `deliveryCycleWeeks` 누락은 `REQUIRED_FIELD_MISSING` 후보와 필드 오류로 표현된다.
- 존재하지 않는 SKU는 `SKU_NOT_FOUND` 후보로 표현된다.
- `subscribable=false` SKU는 `SKU_NOT_SUBSCRIBABLE` 후보로 표현된다.
- 수량 0, 11 등 범위 위반은 `INVALID_QUANTITY` 후보로 표현된다.
- 배송 주기 2/4/8 외 값은 `INVALID_DELIVERY_CYCLE` 후보로 표현된다.

내 구독 조회:

- 비회원은 내 구독 목록과 상세 API를 사용할 수 없고 `AUTH_REQUIRED` 후보로 표현된다.
- 로그인 회원은 자신의 구독 목록만 조회할 수 있다.
- 내 구독 상세는 본인 구독만 조회할 수 있다.
- 존재하지 않는 구독과 다른 회원 소유 구독은 `SUBSCRIPTION_NOT_FOUND` 후보로 동일하게 표현된다.
- 소유자, 존재 여부의 세부 정보가 노출되지 않는다.

날짜와 제외 범위:

- API 날짜 후보는 ISO-8601 local date 문자열이다.
- `nextOrderDate`는 서버 계산 확정값이어야 한다.
- 휴일, 주말, 영업일 보정은 적용하지 않는다.
- 다음 배송 예정일은 응답에 포함되지 않는다.
- 구독 상태, 결제, 재고, 배송, 삭제 정책 필드는 응답에 포함되지 않는다.

## Backend 구현 주의사항

- API-001 승인 전 Controller, DTO, Service, Repository를 구현하지 않는다.
- JPA Entity를 API 응답으로 직접 노출하지 않는다.
- 인증된 회원 식별 방식은 후속 인증 설계 또는 Backend 구현에서 확정한다.
- 구독 생성은 인증 회원 식별, SKU 조회, SKU 구독 가능 여부 확인, 수량·배송 주기 검증, 다음 주문 예정일 계산을 포함해야 한다.
- 다음 주문 예정일은 `Asia/Seoul` 날짜 단위로 계산한다.
- 다른 회원 소유 구독 상세는 존재 여부를 드러내지 않도록 존재하지 않는 구독과 같은 API 표현을 사용한다.
- 예외 클래스, 전역 예외 처리, Spring Security 설정은 API-001에서 구현하지 않는다.

## Deferred Technical Decision

- OpenAPI 파일 생성 여부와 도구 선택
- 실제 Controller, Request/Response DTO, Service, Repository 구조
- Spring Security, 세션, 토큰, 쿠키 구현 방식
- 로그인 복귀 경로 저장 방식
- Open Redirect 방지 구현
- Frontend 라우팅 구현 방식
- 실제 예외 클래스와 전역 예외 처리 방식
- 최종 오류 응답 JSON 세부 구조
- API 버전 전략
- 페이지네이션, 검색, 정렬, 필터 정책
- soft delete, hard delete, 삭제·탈퇴·보관·익명화 정책

## 중단 조건

- API 계약 승인 전 구현 코드 변경이 필요한 경우
- 결제, 재고, 배송, 구독 상태, 삭제 정책을 추가해야만 화면을 구현할 수 있는 경우
- Spring Security 구현 방식을 확정해야만 API 계약을 이해할 수 있는 경우
- Frontend 라우팅 구현을 확정해야만 API 계약을 사용할 수 있는 경우
- 신규 의존성, OpenAPI 생성 도구, GitHub Actions 변경이 필요한 경우
- Secret 또는 민감정보 노출이 의심되는 경우
