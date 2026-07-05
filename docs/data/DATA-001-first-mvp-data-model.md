# DATA-001 첫 수직 MVP 데이터 모델 설계

## 문서 상태

- 작업 ID: `DATA-001`
- 역할: Backend Engineer
- 결정 상태: Proposed Data Design
- 기준 입력: `PS-002`, `DOMAIN-001`, `UX-001`, `PS-003`, `UX-002`, `ARCH-001`
- 기준 브랜치: 최신 `main`

이 문서는 후속 API-001, Backend 구현, QA 검증을 위한 데이터 설계 제안이다. 사용자 승인 전까지 `Approved`로 표시하지 않는다.

## 작업 목적

첫 번째 수직 MVP의 상품 탐색, 구독 생성, 내 구독 목록 조회, 내 구독 상세 조회 흐름을 지원하는 최소 데이터 구조를 정리한다.

Product, SKU, Member, Subscription 중심으로 저장 책임, 관계, 제약 조건 후보, 인덱스 후보, 날짜 저장 기준, SKU 구독 가능 여부 저장 기준을 문서화한다.

이번 작업은 구현 작업이 아니다. Flyway 마이그레이션, JPA Entity, Repository, Service, Controller, API 요청·응답 JSON, HTTP 상태와 오류 코드는 확정하지 않는다.

## 승인된 입력

- 비회원과 로그인 회원은 상품 목록, 상품 상세, SKU와 구독 가능 여부를 확인할 수 있다.
- 구독 생성, 내 구독 목록, 내 구독 상세는 로그인한 회원만 사용할 수 있다.
- 구독 하나는 SKU 하나만 대상으로 한다.
- SKU는 구독 가능 여부를 가진다.
- 수량은 1~10이다.
- 배송 주기는 2주, 4주, 8주다.
- 구독 생성 성공 후 생성된 구독 상세 화면으로 이동한다.
- 구독 상세 화면에서 구독 ID와 다음 주문 예정일을 확인할 수 있어야 한다.
- 구독 생성 전에는 정확한 다음 주문 예정일 날짜를 표시하지 않는다.
- 구독 생성 성공 후 서버가 계산한 다음 주문 예정일을 표시한다.
- 내 구독 목록과 상세에서도 서버가 확정한 다음 주문 예정일을 표시한다.
- 날짜 계산과 표시는 `Asia/Seoul` 기준 날짜 단위로 다룬다.
- 휴일, 주말, 영업일 보정은 적용하지 않는다.
- 화면 날짜 표시는 `YYYY. M. D.`를 사용한다.
- 다음 배송 예정일은 표시하지 않는다.
- 첫 번째 MVP에는 구독 상태, 결제, 재고, 배송, 구독 변경·해지·일시정지 기능이 없다.

## 데이터 설계 범위

- `members` 테이블 후보
- `products` 테이블 후보
- `skus` 테이블 후보
- `subscriptions` 테이블 후보
- Product와 SKU 관계
- Member와 Subscription 관계
- SKU와 Subscription 관계
- 수량, 배송 주기, 구독 가능 여부, 다음 주문 예정일 제약 후보
- 상품 목록, 상품 상세, 구독 생성, 내 구독 목록, 내 구독 상세 조회에 필요한 데이터 요구사항
- API-001, Backend 구현, QA로 넘길 결정 분리

## 제외 범위

- Flyway 마이그레이션 작성
- SQL DDL 확정
- JPA Entity 구현
- Repository, Service, Controller 구현
- API 요청·응답 JSON 확정
- HTTP 상태와 오류 코드 확정
- URL과 라우트 문자열 확정
- Spring Security 구현 방식 확정
- Next.js 화면 또는 라우팅 구현
- 테스트 코드 작성
- 신규 의존성 추가
- 결제, 재고, 배송, 구독 상태 모델 추가
- 제품·도메인·UX·아키텍처 정책 변경
- CodeRabbit 설정 변경
- GitHub Actions 변경

## 데이터 설계 원칙

- 첫 번째 수직 MVP는 상품 탐색, 구독 생성, 내 구독 조회에 필요한 최소 데이터 구조만 설계한다.
- 결제, 재고, 배송, 구독 상태 모델은 현재 데이터 모델에 포함하지 않는다.
- 구독 하나는 SKU 하나만 참조한다.
- Subscription은 회원과 SKU를 참조한다.
- 수량 1~10, 배송 주기 2주·4주·8주는 데이터 제약과 도메인 검증 후보로 함께 기록한다.
- 다음 주문 예정일은 서버가 계산한 확정값으로 저장하는 방향을 우선 검토한다.
- 날짜는 `Asia/Seoul` 기준 날짜 단위로 다룬다.
- 휴일, 주말, 영업일 보정 컬럼이나 로직은 추가하지 않는다.
- 구현 편의를 위해 과도한 테이블 분리나 범용화 모델을 만들지 않는다.
- 마이그레이션과 JPA 매핑은 후속 Backend 작업에서 확정한다.

## 개념 데이터 모델

| 개념 | 설명 | 첫 MVP 포함 이유 |
| --- | --- | --- |
| Member | 구독을 생성하고 본인 구독을 조회하는 회원 | 구독 생성과 소유자 검증에 필요 |
| Product | 사용자에게 노출되는 상품 단위 | 상품 목록·상세 조회에 필요 |
| Sku | 실제 구독 선택 단위 | 구독 하나가 SKU 하나를 대상으로 하기 때문에 필요 |
| Subscription | 회원이 특정 SKU로 만든 정기배송 구독 | 구독 생성, 목록, 상세, 다음 주문 예정일 확인에 필요 |

## 테이블 후보 요약

| 테이블 후보 | 주요 책임 | 주요 참조 | 첫 MVP 포함 여부 |
| --- | --- | --- | --- |
| `members` | 회원 식별과 구독 소유자 기준 | `subscriptions` | 포함 |
| `products` | 상품 목록·상세 표시 | `skus` | 포함 |
| `skus` | 구독 가능한 실제 선택 단위 | `products`, `subscriptions` | 포함 |
| `subscriptions` | 회원의 SKU 정기배송 구독 | `members`, `skus` | 포함 |

테이블명과 컬럼명은 후속 마이그레이션 작업에서 최종 확정한다. 이 문서는 API-001과 Backend 구현이 추측 없이 논의할 수 있도록 후보 이름을 제공한다.

## Member 데이터 모델

| 필드 후보 | 의미 | 제약 후보 | 비고 |
| --- | --- | --- | --- |
| `id` | 회원 식별자 | PK | 구현 타입은 후속 결정 |
| `email` | 로그인 식별 또는 연락 기준 | unique 후보, nullable false 후보 | 인증 구현 세부는 후속 결정 |
| `display_name` | 사용자 표시명 | nullable false 후보 | 이름 정책은 후속 결정 |
| `created_at` | 생성 시각 | nullable false 후보 | 공통 감사 필드 후보 |
| `updated_at` | 수정 시각 | nullable false 후보 | 공통 감사 필드 후보 |

비밀번호, OAuth, 세션, 토큰, 권한 테이블은 이번 작업에서 확정하지 않는다. 인증 구현 방식은 후속 인증 설계 또는 Backend 작업으로 넘긴다.

개인정보 최소화 원칙에 따라 첫 MVP 구독 생성과 조회에 필요하지 않은 주소, 전화번호, 결제 정보는 Member 후보에 포함하지 않는다.

## Product 데이터 모델

| 필드 후보 | 의미 | 제약 후보 | 비고 |
| --- | --- | --- | --- |
| `id` | 상품 식별자 | PK | 목록·상세 조회 기준 |
| `name` | 상품명 | nullable false 후보 | 화면 표시 |
| `short_description` | 목록용 짧은 설명 | nullable false 후보 | 상품 목록 표시 |
| `description` | 상품 설명 | text 후보 | 상세 화면 |
| `pet_type` | 대상 반려동물 | enum/string 후보 | dog, cat 등 표현 방식 후속 결정 |
| `thumbnail_url` | 목록 이미지 | nullable 허용 여부 후속 결정 | 실제 파일 저장 방식 제외 |
| `display_status` | 노출 상태 | enum/string 후보 | 판매·재고 상태와 분리 |
| `created_at` | 생성 시각 | nullable false 후보 | 공통 감사 필드 후보 |
| `updated_at` | 수정 시각 | nullable false 후보 | 공통 감사 필드 후보 |

재고 수량, 결제 가격 정책 확장, 카테고리, 브랜드, 리뷰, 평점, 옵션 그룹은 이번 MVP에서 확정하지 않는다.

`display_status`는 상품 목록과 상세에 노출할 수 있는지 판단하기 위한 후보이며, 재고·품절·일반 판매 상태나 구독 가능 여부와 연결하지 않는다.

## SKU 데이터 모델

SKU는 실제 구독 선택 단위다.

| 필드 후보 | 의미 | 제약 후보 | 비고 |
| --- | --- | --- | --- |
| `id` | SKU 식별자 | PK | 구독 생성 시 참조 |
| `product_id` | 소속 상품 | FK 후보, nullable false 후보 | Product 1:N SKU |
| `name` | SKU명 또는 옵션명 | nullable false 후보 | 예: 2kg, 5kg |
| `price` | 표시 가격 | nullable false 후보, 0 이상 후보 | 결제는 MVP 제외 |
| `subscribable` | 구독 가능 여부 | boolean 후보, nullable false 후보 | PS-003/ARCH-001 반영 |
| `display_order` | 표시 순서 | nullable false 후보 또는 기본값 후보 | 목록 정렬 후보 |
| `created_at` | 생성 시각 | nullable false 후보 | 공통 감사 필드 후보 |
| `updated_at` | 수정 시각 | nullable false 후보 | 공통 감사 필드 후보 |

재고 상태와 구독 가능 여부는 첫 MVP에서 연결하지 않는다. `sold out`, sale status, inventory는 이번 DATA-001에서 확정하지 않는다.

구독 생성 시 SKU가 존재하고 `subscribable=true`인 경우에만 구독 생성 가능 후보로 본다. `subscribable=false`인 SKU는 상품 상세에서 구독 불가능으로 표시될 수 있지만 구독 생성 대상이 되면 안 된다.

## Subscription 데이터 모델

Subscription은 구독 생성, 내 구독 목록, 내 구독 상세, 다음 주문 예정일 확인에 필요한 최소 정보를 보존한다.

| 필드 후보 | 의미 | 제약 후보 | 비고 |
| --- | --- | --- | --- |
| `id` | 구독 식별자 | PK | 생성 성공 후 상세 이동에 필요 |
| `member_id` | 구독 소유 회원 | FK 후보, nullable false 후보 | 본인 구독 조회와 소유자 검증 |
| `sku_id` | 구독 대상 SKU | FK 후보, nullable false 후보 | 구독 하나는 SKU 하나 |
| `quantity` | 구독 수량 | nullable false, 1~10 후보 | 도메인 검증과 DB 제약 후보 |
| `delivery_cycle_weeks` | 배송 주기 | nullable false, 2/4/8 후보 | enum 또는 smallint 후속 결정 |
| `created_date` | 구독 생성일 | nullable false 후보 | `Asia/Seoul` 날짜 기준 |
| `next_order_date` | 다음 주문 예정일 | nullable false 후보 | 서버 계산 확정값 |
| `created_at` | 생성 시각 | nullable false 후보 | 감사 필드 |
| `updated_at` | 수정 시각 | nullable false 후보 | 감사 필드 |

첫 MVP에서는 구독 상태 컬럼, 결제 상태, 배송 상태, 주문 생성 상태, pause, cancel, skip, resume 관련 컬럼을 추가하지 않는다.

다음 배송 예정일 컬럼은 추가하지 않는다. `next_order_date`는 다음 주문 예정일이며 배송 예정일이 아니다.

## 관계와 카디널리티

| 관계 | 카디널리티 | 설명 | 후속 구현 고려 |
| --- | --- | --- | --- |
| Product - SKU | 1:N | 상품 하나는 여러 SKU를 가질 수 있다 | SKU 조회 인덱스 후보 |
| Member - Subscription | 1:N | 회원 하나는 여러 구독을 가질 수 있다 | 내 구독 목록 조회 인덱스 후보 |
| SKU - Subscription | 1:N | SKU 하나는 여러 회원의 구독 대상이 될 수 있다 | 구독 생성 시 FK |

JPA 단방향/양방향 매핑, fetch 전략, cascade, orphanRemoval 여부는 Backend 구현 작업에서 코드와 테스트를 기준으로 확정한다.

Product와 SKU를 같은 Aggregate로 둘지, Subscription이 SKU 전체를 참조할지 SKU 식별자만 참조할지는 후속 Backend 구현 설계에서 결정한다. DATA-001은 저장 구조 후보와 조회 요구사항만 제공한다.

## 제약 조건 후보

| 대상 | 제약 후보 | 이유 | 적용 위치 후보 |
| --- | --- | --- | --- |
| `subscriptions.quantity` | 1 이상 10 이하 | PS-002 수량 규칙 | 도메인 검증 + DB CHECK 후보 |
| `subscriptions.delivery_cycle_weeks` | 2, 4, 8 중 하나 | PS-002 배송 주기 규칙 | 도메인 검증 + DB CHECK 후보 |
| `subscriptions.member_id` | nullable false | 구독 소유자 필요 | DB FK 후보 |
| `subscriptions.sku_id` | nullable false | 구독 대상 SKU 필요 | DB FK 후보 |
| `subscriptions.created_date` | nullable false | 구독 생성일 필요 | 도메인 검증 + DB NOT NULL 후보 |
| `subscriptions.next_order_date` | nullable false | 서버 확정 다음 주문 예정일 필요 | 도메인 검증 + DB NOT NULL 후보 |
| `subscriptions.next_order_date` | `created_date`보다 이후이며 `created_date + delivery_cycle_weeks`로 계산 | 다음 주문 예정일 규칙 보존 | 도메인/애플리케이션 검증 후보, 실제 DB CHECK 또는 DDL 표현은 후속 마이그레이션 작업 |
| `skus.subscribable` | nullable false | 구독 가능 여부 판단 필요 | DB 기본값 후보 |
| `skus.product_id` | nullable false | SKU는 상품에 속함 | DB FK 후보 |
| `skus.price` | 0 이상 | 표시 가격 음수 방지 | 도메인 검증 + DB CHECK 후보 |
| `products.name` | nullable false | 상품 목록·상세 표시 필요 | DB NOT NULL 후보 |

MySQL 버전별 CHECK 제약 지원과 실제 적용 여부는 마이그레이션 작업에서 검증한다. DB 제약만 믿지 않고 도메인/애플리케이션 검증도 함께 필요하다.

## 인덱스 후보

| 인덱스 후보 | 대상 조회 | 이유 | 확정 여부 |
| --- | --- | --- | --- |
| `skus(product_id)` | 상품 상세의 SKU 목록 | Product 상세에서 SKU 조회 | 후보 |
| `subscriptions(member_id, id)` | 내 구독 목록·상세 | 회원별 구독 조회와 소유자 검증 | 후보 |
| `subscriptions(member_id, next_order_date)` | 내 구독 목록 정렬 후보 | 다음 주문 예정일 표시와 정렬 가능성 | 후보 |
| `products(display_status, id)` | 상품 목록 공개 조회 후보 | 노출 상품 목록 조회 가능성 | 후보 |

성능 측정 전 과도한 인덱스를 확정하지 않는다. 실제 인덱스명과 생성 DDL은 마이그레이션 작업에서 확정한다.

## 날짜와 시간 저장 기준

- 다음 주문 예정일은 서버가 계산한 확정값으로 저장하는 방향을 우선한다.
- 날짜 계산 기준은 `Asia/Seoul` 날짜 단위다.
- 생성일 + 배송 주기로 다음 주문 예정일을 계산한다.
- 휴일, 주말, 영업일 보정은 하지 않는다.
- 다음 배송 예정일은 저장하지 않는다.
- 화면 표시 형식은 `YYYY. M. D.`다.
- API 날짜 표현 형식은 API-001에서 확정한다.
- DB 타입은 DATE 후보로 기록하되 실제 타입은 마이그레이션 작업에서 검증한다.

`created_date`와 `next_order_date`는 날짜 단위 의미를 가진다. `created_at`과 `updated_at`은 감사 목적의 시각 후보이며, 시간대 저장 방식은 후속 공통 기술 결정에서 확정한다.

## 구독 가능 여부 저장 기준

- SKU는 구독 가능 여부를 가진다.
- 첫 MVP에서 SKU의 구독 가능 여부는 재고, 품절, 판매 상태와 연결하지 않는다.
- 구독 생성 시 SKU가 존재하고 `subscribable=true`인 경우에만 생성 가능하다.
- 비회원과 로그인 회원 모두 상품 상세에서 SKU의 구독 가능 여부를 확인할 수 있다.

첫 MVP에서는 Boolean 후보를 우선 기록한다. enum, 값 객체, 정책 테이블 분리는 구독 가능 사유나 상태 전이가 필요해지는 후속 작업에서 재검토한다.

## 조회 흐름별 데이터 요구사항

| 흐름 | 필요한 데이터 | 주요 테이블 후보 | 비고 |
| --- | --- | --- | --- |
| 상품 목록 조회 | 상품 ID, 이름, 대표 이미지, SKU 구독 가능 여부 요약 | `products`, `skus` | API 응답 구조는 API-001 |
| 상품 상세 조회 | 상품 상세, SKU 목록, 가격, 구독 가능 여부 | `products`, `skus` | 비회원 접근 가능 |
| 구독 생성 | `member_id`, `sku_id`, `quantity`, `delivery_cycle_weeks` | `members`, `skus`, `subscriptions` | 인증 회원 필요 |
| 내 구독 목록 | 구독 ID, 상품/SKU 표시 정보, 수량, 주기, 다음 주문 예정일 | `subscriptions`, `skus`, `products` | 본인 구독만 |
| 내 구독 상세 | 구독 ID, 소유자, 상품/SKU, 수량, 주기, 생성일, 다음 주문 예정일 | `subscriptions`, `skus`, `products` | 소유자 검증 필요 |

## 오류 조건과 데이터 근거

| 오류 조건 | 데이터 근거 | 후속 API 결정 |
| --- | --- | --- |
| 존재하지 않는 상품 | `products`에 id 없음 | HTTP 상태/오류 코드 API-001 |
| 존재하지 않는 SKU | `skus`에 id 없음 | HTTP 상태/오류 코드 API-001 |
| 구독 불가능 SKU | `skus.subscribable=false` | HTTP 상태/오류 코드 API-001 |
| 수량 범위 위반 | `quantity` 1~10 위반 | HTTP 상태/오류 코드 API-001 |
| 허용되지 않은 배송 주기 | `delivery_cycle_weeks`가 2/4/8 아님 | HTTP 상태/오류 코드 API-001 |
| 존재하지 않는 구독 | `subscriptions`에 id 없음 | HTTP 상태/오류 코드 API-001 |
| 다른 회원 소유 구독 접근 | `subscriptions.member_id` 불일치 | HTTP 상태/오류 코드 API-001 |

다른 회원 소유 구독과 존재하지 않는 구독은 사용자에게 같은 “조회할 수 없는 구독”으로 표현해야 한다. 데이터 설계는 `subscriptions.member_id`로 소유자 검증 근거를 제공하고, API 표현은 API-001에서 확정한다.

## API-001로 넘길 결정

- 상품 목록 응답에 SKU 요약을 어느 수준까지 포함할지
- 상품 상세 응답의 Product/SKU 필드 구조
- 구독 생성 요청 필드명
- 구독 생성 성공 응답에 포함할 구독 ID와 다음 주문 예정일 표현
- 내 구독 목록 응답 구조
- 내 구독 상세 응답 구조
- 날짜 표현 형식
- 인증 실패와 인가 실패 HTTP 상태
- 도메인 오류 코드와 메시지 구조

## Backend 구현으로 넘길 결정

- JPA Entity 클래스 구조
- 연관관계 방향
- fetch 전략
- cascade 사용 여부
- 승인된 수량·배송 주기 불변 조건을 보호하는 값 객체의 구현 형태와 JPA 매핑 방식
- enum 저장 방식
- 트랜잭션 경계
- Repository 쿼리 방식
- Flyway 마이그레이션 DDL
- MySQL CHECK 제약 실제 적용 여부
- `Clock` 또는 동등한 날짜 공급 추상화 사용 여부

## QA 검증으로 넘길 기준

- 상품 목록에서 공개 상품과 구독 가능 SKU 요약을 확인할 수 있다.
- 상품 상세에서 SKU별 구독 가능 여부를 확인할 수 있다.
- 구독 가능한 SKU, 수량 1~10, 배송 주기 2/4/8이면 구독 데이터가 생성될 수 있다.
- 구독 불가능 SKU로는 구독 데이터가 생성되면 안 된다.
- 수량 범위를 벗어나면 구독 데이터가 생성되면 안 된다.
- 허용되지 않은 배송 주기면 구독 데이터가 생성되면 안 된다.
- 생성된 구독은 회원과 SKU를 참조해야 한다.
- 내 구독 목록은 본인 구독만 조회할 수 있어야 한다.
- 다른 회원의 구독 상세는 조회할 수 없어야 한다.
- 다음 주문 예정일은 서버 계산 확정값이어야 한다.

## 추적성

| 요구사항 | 인수 조건 | 데이터 설계 | 후속 작업 | 상태 |
| --- | --- | --- | --- | --- |
| REQ-PRODUCT-001 | AC-PRODUCT-001-01 외 | `products`, `skus`, SKU 구독 가능 요약 후보 | API-001, BE, FE, QA | Proposed |
| REQ-PRODUCT-002 | AC-PRODUCT-002-01 외 | `products`, `skus`, `skus.subscribable`, `skus.price` | API-001, BE, FE, QA | Proposed |
| REQ-SUB-001 | AC-SUB-001-01 외 | `skus.subscribable`, `subscriptions.member_id`, `sku_id`, `quantity`, `delivery_cycle_weeks`, `created_date`, `next_order_date` | API-001, BE, QA | Proposed |
| REQ-SUB-002 | AC-SUB-002-01 외 | `subscriptions(member_id, id)` 조회 후보, Product/SKU 조인 후보 | API-001, BE, FE, QA | Proposed |
| REQ-SUB-003 | AC-SUB-003-01 외 | 구독 단건 조회와 소유자 검증을 위한 `subscriptions.member_id` | API-001, BE, FE, QA | Proposed |
| REQ-SUB-004 | AC-SUB-004-01 외 | `created_date`, `next_order_date`, DATE 후보, `Asia/Seoul` 날짜 단위 | API-001, BE, FE, QA | Proposed |
| REQ-AUTH-001 | AC-AUTH-001-01 외 | 보호 기능 데이터는 인증 회원의 `members.id`를 기준으로 생성·조회 | API-001, 인증 설계, BE, FE, QA | Proposed |
| REQ-AUTH-002 | AC-AUTH-002-01 외 | `subscriptions.member_id`로 본인 구독 목록·상세 소유자 검증 | API-001, BE, QA | Proposed |

## Deferred Technical Decision

- 실제 SQL DDL
- Flyway 마이그레이션 파일명과 내용
- JPA Entity 구조
- 테이블명과 컬럼명의 최종 확정 여부
- enum 저장 방식
- 날짜 DB 타입 최종 선택
- `created_at`/`updated_at` 공통 감사 필드 구현 방식
- 인증 테이블과 권한 모델
- API 요청·응답 JSON
- HTTP 상태와 오류 코드
- URL과 라우트 문자열
- 성능 측정 전 최종 인덱스 확정
- Product와 SKU Aggregate 경계
- Subscription과 SKU 참조 방식

## 위험과 제한

- 이 문서는 Proposed Data Design이며 사용자 승인 전 최종 DB 설계가 아니다.
- 실제 MySQL CHECK 제약 지원 여부와 DDL 표현은 마이그레이션 작업에서 검증해야 한다.
- 개인정보 최소화 원칙에 따라 Member 후보는 MVP에 필요한 최소 필드만 포함했다.
- 인증 방식이 확정되지 않아 Member와 인증 테이블의 관계는 설계하지 않았다.
- API 계약이 확정되지 않아 날짜 문자열 형식, 오류 구조, HTTP 상태는 정하지 않았다.
- 구독 가능 여부는 재고·품절·판매 상태와 분리했으므로 후속 결제·재고 MVP에서 재검토가 필요할 수 있다.

## 후속 작업 순서

1. API-001: 공개 상품 조회, 구독 생성, 내 구독 조회, 인증·인가 실패와 오류 계약 정의
2. 인증 설계: 로그인 복귀 경로 저장, 내부 GET 검증, Open Redirect 방지 결정
3. Backend 구현: 도메인 값 객체, JPA Entity, Flyway 마이그레이션, Repository, Service, Controller와 테스트 작성
4. Frontend 구현: UX-002와 API-001에 맞춘 화면·상태·라우팅 구현
5. QA: 요구사항, ARCH-001, API-001, DATA-001 기준 검증 계획과 테스트 작성
