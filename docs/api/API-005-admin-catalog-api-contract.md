# API-005 MVP3 Admin Catalog API 승인 계약

## 상태

- 작업 ID: `MVP3-CATALOG-001`
- 상태: Approved — 현재 작업의 사용자 명시 승인
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Backend Engineer

## 목적과 호환성

ADMIN이 Category, Product, SKU를 생성·조회·수정하고 노출·판매 상태를 관리한다. 기존 공개 Product API, 인증과 Subscription 계약은 유지한다. hard delete endpoint, 회원 role 변경 endpoint와 실제 ADMIN 계정 생성은 제공하지 않는다.

`GET /api/auth/me`에는 기존 `memberId`를 유지하고 `role: "USER" | "ADMIN"`을 additive하게 제공한다. 로그인 성공 응답은 기존 `{ "memberId": number }`를 유지한다. 외부 요청으로 role을 입력하거나 변경하는 API는 없다.

## 인증·인가·CSRF

- `/api/admin/**`는 `ADMIN`만 접근할 수 있다.
- 미인증 요청은 `401 AUTH_REQUIRED`, 인증된 `USER`는 `403 ACCESS_DENIED`다.
- 상태 변경 요청은 AUTH-003의 session CSRF 계약과 `X-CSRF-TOKEN`을 그대로 사용한다. 누락·불일치는 `403 CSRF_INVALID`다.
- 로그인 시 DB role을 `ROLE_USER` 또는 `ROLE_ADMIN` authority로 변환한다.

## API 목록

| Method | URI | 성공 |
| --- | --- | --- |
| GET, POST | `/api/admin/categories` | GET 200, POST 201 |
| GET, PATCH | `/api/admin/categories/{categoryId}` | 200 |
| GET, POST | `/api/admin/products` | GET 200, POST 201 |
| GET, PATCH | `/api/admin/products/{productId}` | 200 |
| GET, POST | `/api/admin/products/{productId}/skus` | GET 200, POST 201 |
| PATCH | `/api/admin/products/{productId}/skus/{skuId}` | 200 |

## MVP4 Catalog 확장 delta

V24 이후 `/api/admin/**`의 기존 ADMIN, CSRF, Audit 계약을 그대로 적용한다. 상태 변경 요청은 모두 CSRF 토큰이 필요하며, 생성 요청은 `201 Location`을 반환한다.

| Method | URI | 용도 |
| --- | --- | --- |
| GET, PATCH | `/api/admin/brands/{brandId}` | Brand 상세·부분 수정 |
| GET, POST | `/api/admin/products/{productId}/images` | Image Gallery 조회·생성 |
| PATCH, DELETE | `/api/admin/products/{productId}/images/{imageId}` | Image 수정·삭제 |
| GET, POST | `/api/admin/products/{productId}/option-groups` | Option group 조회·생성 |
| PATCH, DELETE | `/api/admin/products/{productId}/option-groups/{groupId}` | Option group 수정·삭제 |
| POST | `/api/admin/products/{productId}/option-groups/{groupId}/values` | Option value 생성 |
| PATCH, DELETE | `/api/admin/products/{productId}/option-groups/{groupId}/values/{valueId}` | Option value 수정·삭제 |
| PUT | `/api/admin/products/{productId}/skus/{skuId}/option-values` | SKU의 option value 집합 교체 |
| GET, POST | `/api/admin/facets` | Facet definition 조회·생성 |
| GET, PATCH, DELETE | `/api/admin/facets/{definitionId}` | Facet definition 상세·수정·삭제 |
| POST | `/api/admin/facets/{definitionId}/options` | Facet option 생성 |
| PATCH, DELETE | `/api/admin/facets/{definitionId}/options/{optionId}` | Facet option 수정·삭제 |
| PUT, DELETE | `/api/admin/categories/{categoryId}/facets/{definitionId}` | Category의 허용 facet 배정·해제 |
| PUT | `/api/admin/products/{productId}/facet-values` | Product facet option 집합 교체 |

- `compareAtPrice`는 SKU 생성·수정 시 선택 필드이며 설정하면 `price`보다 반드시 커야 한다.
- Product당 `MAIN` 이미지는 하나만 허용한다. 공개 thumbnail은 MAIN image가 있으면 그 URL, 없으면 기존 `thumbnailUrl`을 사용한다.
- SKU option value는 해당 Product의 group에 속해야 하고 group당 하나만 지정할 수 있다. 같은 option value 집합을 가진 다른 SKU는 `409 SKU_OPTION_COMBINATION_CONFLICT`다.
- Product facet option은 Product Category에 배정된 facet definition에 속해야 한다. 그 외 assignment는 `409 PRODUCT_FACET_NOT_ALLOWED`다.
- Category는 root를 포함해 최대 3 depth이며 자기 자신·하위 Category를 parent로 지정할 수 없다.

POST 성공은 생성 리소스 URI를 `Location` header로 제공한다. 목록 응답은 각각 `{ "categories": [] }`, `{ "products": [] }`, `{ "skus": [] }`이며 `null` collection을 사용하지 않는다. Category는 `displayOrder ASC, categoryId ASC`, Product는 `productId ASC`, SKU는 `displayOrder ASC, skuId ASC` 순서다.

## 요청·응답 필드

### Category

```json
{
  "categoryId": 10,
  "name": "사료",
  "slug": "food",
  "displayOrder": 1,
  "active": true
}
```

POST는 `name`, `slug`, `displayOrder`, `active`를 모두 받는다. `name`은 1~100자, `slug`는 1~100자의 소문자 영숫자와 단일 `-` 구분 형식, `displayOrder`는 0 이상이다. slug는 ASCII binary 기준 unique다. Category 비활성은 관리 상태이며 연결 Product의 공개 상태를 자동 변경하지 않는다.

### Product

```json
{
  "productId": 101,
  "categoryId": 10,
  "name": "강아지 사료",
  "shortDescription": "매일 먹는 기본 사료",
  "description": null,
  "petType": "DOG",
  "thumbnailUrl": null,
  "status": "DRAFT"
}
```

POST는 nullable `categoryId`, `description`, `thumbnailUrl`과 필수 `name`, `shortDescription`, `petType`을 받는다. status 입력은 받지 않고 항상 `DRAFT`로 생성한다. `categoryId`가 있으면 존재하는 Category여야 한다. 허용 전이는 `DRAFT → PUBLIC`, `PUBLIC → INACTIVE`, `INACTIVE → PUBLIC`뿐이며 동일 상태 또는 그 밖의 전이는 409다.

### SKU

```json
{
  "skuId": 1001,
  "productId": 101,
  "skuCode": "DOG-FOOD-2KG",
  "name": "2kg",
  "price": 19900.00,
  "subscribable": true,
  "displayOrder": 1,
  "status": "ACTIVE"
}
```

POST는 `skuCode`, `name`, 0 이상 `price`, `subscribable`, 0 이상 `displayOrder`, `ACTIVE | INACTIVE` status를 모두 받는다. skuCode는 ASCII 영숫자로 시작하고 ASCII 영숫자·`.`·`_`·`-`만 허용하며 ASCII binary 기준 unique다. 생성 뒤 PATCH에서 skuCode를 받지 않아 변경할 수 없다. 판매 status와 기존 Subscription eligibility인 `subscribable`은 서로 독립이다.

## PATCH 규칙

- omitted 필드는 유지한다. 수정 필드가 하나도 없으면 400이다.
- non-null 필드를 명시적 `null`로 보내면 400이다.
- Product의 `categoryId`, `description`, `thumbnailUrl`은 명시적 `null`로 연결 또는 값을 해제할 수 있다.
- SKU PATCH에는 `skuCode`가 없으며 name, price, subscribable, displayOrder, status만 수정한다.
- 한 요청의 validation, 참조 조회, 상태 전이와 저장은 하나의 transaction이다.
- Product PATCH는 동일 Product 행의 쓰기 잠금을 획득한 뒤 상태 전이와 필드 수정을 수행한다. 동시에 들어온 PATCH를 직렬화하여 동일 전이의 중복 성공과 필드 유실을 방지하며, 잠금 뒤 관찰한 최신 상태에서 허용되지 않은 전이는 409다.

## 오류 계약

모든 오류는 기존 `{ "code", "message", "fieldErrors" }` shape를 사용하며 내부 예외·SQL·schema 정보를 노출하지 않는다.

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Bean validation, PATCH validation, malformed JSON·enum·path |
| 401 | `AUTH_REQUIRED` | 미인증 Admin 접근 |
| 403 | `ACCESS_DENIED` | USER의 Admin 접근 |
| 403 | `CSRF_INVALID` | 상태 변경 요청의 CSRF 실패 |
| 404 | `CATEGORY_NOT_FOUND` | Category 미존재 |
| 404 | `PRODUCT_NOT_FOUND` | Admin Product 미존재 |
| 404 | `SKU_NOT_FOUND` | Product에 속한 SKU 미존재 |
| 409 | `CATEGORY_SLUG_CONFLICT` | slug 중복 |
| 409 | `SKU_CODE_CONFLICT` | skuCode 중복 |
| 409 | `PRODUCT_STATUS_TRANSITION_CONFLICT` | 허용되지 않은 Product 상태 전이 |
| 500 | `ADMIN_CATALOG_UNAVAILABLE` | 예상하지 못한 Admin Catalog 오류 |

## 공개 Product와 Subscription 호환성

- 공개 Product 기준은 기존대로 정확히 `display_status=PUBLIC`이다.
- 공개 목록·상세의 Product/SKU 응답 shape와 정렬은 API-002를 유지한다.
- 공개 SKU projection에는 `status=ACTIVE`만 포함한다. ACTIVE SKU가 없어도 PUBLIC Product는 유지하고 `skuPrices`·`skus`는 빈 배열, `hasSubscribableSku`는 false다.
- SKU `INACTIVE`는 공개 projection만 제한한다. 기존 Subscription 생성 조건은 계속 SKU 존재와 `subscribable=true`만 사용하며 status를 새 조건으로 연결하지 않는다.

## DB·migration 계약

V12는 additive migration이다.

1. 첫 DDL에서 legacy `products.display_status`가 정확히 `DRAFT | PUBLIC | INACTIVE`인지 CHECK로 검증한다. 알 수 없는 값이 있으면 다른 V12 변경 전에 실패한다.
2. `members.role`을 추가하고 기존 row를 `USER`로 backfill한 뒤 NOT NULL, 기본값 `USER`, USER/ADMIN CHECK를 적용한다.
3. `categories`를 생성하고 nullable `products.category_id` FK를 추가한다.
4. `skus.sku_code`, `skus.status`를 nullable로 추가한 뒤 기존 row를 각각 `SKU-{id}`, `ACTIVE`로 deterministic backfill한다.
5. backfill 뒤 skuCode NOT NULL·unique와 SKU status CHECK를 적용한다.

MySQL DDL은 auto-commit될 수 있으므로 Production 적용·rollback·repair는 이 저장소 변경의 승인이 아니다. 실제 운영 migration 전에 legacy status preflight와 backup/restore 경계를 별도 승인해야 한다.

## 제외와 복구

Inventory, Cart, Wishlist, Coupon, Membership, Order, Payment, Delivery, Admin Dashboard, 회원 관리·role 변경 API/UI, 실제 ADMIN 계정 생성, JWT/OAuth, Production DB/AWS 실행은 제외한다. 저장소 변경은 일반 revert PR로 복구하되 이미 운영 DB에 적용된 V12의 down migration이나 데이터 삭제는 승인하지 않는다.
