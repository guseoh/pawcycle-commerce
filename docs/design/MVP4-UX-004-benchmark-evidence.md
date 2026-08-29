# MVP4-UX-004 실제 Commerce 벤치마크 증거

## 문서 지위

- 상태: `Supporting Evidence / Draft / Pending Product Owner Approval`
- 관찰일: 2026-08-29
- 목적: 브랜드 이름이 아니라 실제 화면에서 확인한 컴포넌트·상호작용과 PawCycle 내부 계약을 추적 가능한 근거로 남긴다.
- 증거 등급: 직접 화면과 조작을 확인한 `CONFIRMED`, 공식 설명만 확인한 `INDIRECT`, 인증·차단·미완료 흐름으로 확인하지 못한 `UNVERIFIED`.
- 경계: 레퍼런스의 가격·정책·혜택·지원 기능을 PawCycle 사실로 옮기지 않는다. 외부 UX가 좋아 보여도 현재 PawCycle API가 지원하지 않으면 `REJECT/UNSUPPORTED`로 분리한다.

## 직접 관찰 기록

| 출처 | 실제 경로 | 확인한 컴포넌트·상호작용 | 증거 | PawCycle 판정 |
| --- | --- | --- | --- | --- |
| Kurly | `https://www.kurly.com/main` | 로고·검색·유틸리티와 별도 카테고리/주 내비게이션, 카테고리 click open/close, hero와 상품 rail, scroll 뒤 축소 sticky 탐색행 | `CONFIRMED` | 검색 우선 header와 compact sticky `ADAPT`; Escape 미지원 menu `REJECT` |
| Kurly Pet PLP | `https://www.kurly.com/categories/991` | 좌측 filter rail, 결과 수, URL 정렬, 1280px에서 약 249px 3열 card, 배송·배지·설명·원가·할인·현재가·리뷰 | `CONFIRMED` | 정보 밀도를 보존하되 5열 과밀 `REJECT`; PawCycle 4열 상한 근거 |
| Kurly PDP | `https://www.kurly.com/goods/1001181670` | gallery+구매 요약, 옵션 선택, 수량 제약, 총액 갱신, 담기 CTA, sticky section anchor | `CONFIRMED` | gallery/구매 요약, option→quantity→CTA 순서 `ADAPT` |
| Kurly Cart | `https://www.kurly.com/cart` | 전체/개별 선택, 수량, 합계와 선택 상태 동기화 | `CONFIRMED` | **시각적 계층만 참고**. PawCycle Checkout API가 selected item ID를 받지 않으므로 Cart selection/선택 checkout은 `UNSUPPORTED/REJECT` |
| Musinsa Search/PLP | `https://www.musinsa.com/search/goods?keyword=운동화&gf=A` | 검색 결과, 복수 filter group open, brand 선택 즉시 URL/result count 갱신, 1280px 6열 | `CONFIRMED` | URL 동기화·group 독립 open `ADAPT`; 6열 과밀/작은 action `REJECT` |
| Musinsa Wishlist/Login | `https://www.musinsa.com/like/goods` | anonymous 설명+로그인 CTA, return context, visible labels/password reveal 등 | `CONFIRMED` | 사전 설명+안전한 GET returnTo `ADAPT`; 로그인 후 Wishlist 관리 UI는 `UNVERIFIED` |
| IKEA Search/PLP | `https://www.ikea.com/kr/ko/search/?q=KALLAX` | 검색어 유지/clear, 결과 status, filter chip, 비교 checkbox/tray, wishlist, add, option swatch | `CONFIRMED` | filter/compare interaction `ADAPT`; PawCycle PLP에서 option 필요 여부를 알 수 없으므로 대표 SKU quick add는 `UNSUPPORTED` |
| IKEA PDP | `https://www.ikea.com/kr/ko/p/kallax-shelving-unit-white-20351884/` | 모바일 gallery→상품명·가격·리뷰→옵션→배송/재고→수량→담기→detail/review, sticky action | `CONFIRMED` | 모바일 정보 순서와 sticky transaction action `ADAPT` |
| IKEA Cart | `https://www.ikea.com/kr/ko/shoppingcart/` | 수량 직접 입력/증감, 삭제, wishlist, semantic summary, coupon disclosure, mobile 결제 CTA | `CONFIRMED` | 수량·삭제·summary `ADAPT`; PawCycle Cart에는 coupon mutation이 없으므로 Cart coupon은 `UNSUPPORTED/REJECT` |
| IKEA Checkout | cart의 `결제하기`→guest flow | 단계 잠금, 상품/금액 disclosure, 현재 단계 중심 progressive flow | `CONFIRMED` | progressive section `ADAPT`; PawCycle은 `POST /api/checkout` 후 Toss widget이라는 자체 계약 우선 |
| IKEA Order/Support | purchase lookup / customer service | 주문 lookup form, 지원/주문관리/FAQ 분리 | `CONFIRMED` | context 기반 지원 구조 `ADAPT`; 비회원 주문조회 추가 금지 |
| PetFriends Home | `https://m.pet-friends.co.kr/main/tab/2` | Pet type, 사료/간식/용품, 연령·용도 category, 상품 rail 가격/리뷰/단가 | `CONFIRMED` | Pet→소비 목적→상품 탐색 계층 `ADAPT`; 혜택 과밀 `REJECT` |
| PetFriends PDP | `https://m.pet-friends.co.kr/product/detail/110966` | Pet/브랜드/랭킹, 리뷰, 가격, 배송, 추천, 하단 찜·Cart | `CONFIRMED` | Pet 적합성/신뢰와 mobile sticky action `ADAPT` |

## 공식 설명 기반 정기배송 근거

| 출처 | 확인한 공식 설명 | 증거 | 적용 경계 |
| --- | --- | --- | --- |
| [Petco Autoship FAQ](https://www.petco.com/content/petco/PetcoStore/en_US/pet-services/help/autoship.html) | 다음 주문일, skip, 주기, 수량, 주소·결제, 취소 관리 설명 | `INDIRECT` | 실제 account UI 형태는 추정하지 않는다. PawCycle에 없는 기본 Plan item quantity 변경을 만들지 않는다. |
| [PetSmart AutoShip Help](https://www.petsmart.com/help/your-order-H0003d.html) / [Terms](https://www.petsmart.com/help/auto-ship-HOO14.html) | Manage Order, skip, 주기, 재시작·취소·주소·결제와 사전 변경 제한 | `INDIRECT` | 날짜/주기 command를 분리하는 근거. 정책 시간 수치는 복제하지 않는다. |
| [Pet Valu Autoship Help](https://prb-support.freshdesk.com/support/solutions/articles/25000027718-how-do-i-make-changes-to-my-autoship-subscription-) | 날짜·skip·주기·결제·취소 변경 설명 | `INDIRECT` | 실제 지원 action은 Detail `availableActions`만 사용한다. |

Subscription List/Detail/Create의 **실제 로그인 account 화면은 여전히 `UNVERIFIED`**다. 해당 화면을 보지 못했다는 이유로 Petco/PetSmart의 문장 설명을 실제 UI 구조로 승격하지 않는다.

## PawCycle 내부 추적 가능 근거

| 영역 | 정확한 내부 근거 | 이 설계에서 확정하는 것 |
| --- | --- | --- |
| Product list/search | `backend/.../ProductDiscoveryReader.java`; `GET /api/products`; `docs/api/API-012-mvp4-final-product-backend-api.md` | q는 product name/short description/description, brand/category는 별도 filter; 4/3/3/2/2/2/1 grid |
| PDP options | `ProductDiscoveryReader.readDetailSkus/readDetailSupplement`; Product Detail의 `optionGroups[]`, SKU `selectedOptions[]` | option 조합으로 정확한 SKU 결정, 단일 SKU는 option 단계 생략 |
| Wishlist | `frontend/src/lib/commerce-final-api.ts` `WishlistItem`, `GET/POST/DELETE /api/wishlist/{productId}` | Product Card 수준 데이터 추정 금지; DELETE 성공 후 POST 기반 Undo 가능 |
| Cart | `commerce-final-api.ts` `CartItem/CartResult`; `/api/cart` | server Cart 전체, quantity/delete/version/pricing; selection/coupon/thumbnail 가정 금지 |
| Checkout | `commerce-final-api.ts` `checkout()`; `POST /api/checkout`; `CheckoutResult`; Toss confirm API | Phase A context 생성→Phase B Toss 결제→server confirmation |
| Order List | `commerce-final-api.ts` `OrderSummary`; `GET /api/orders` | filter/page/상품 summary 없이 날짜·번호·status·amount·detail만 |
| Order Detail/Reorder | `OrderDetail`, `QuickReorderResult`; `POST /api/orders/{orderId}/reorder` | availableActions, 부분 성공 added/skipped, Idempotency-Key |
| Subscription List | `frontend/src/lib/v2-api.ts` `V2SubscriptionSummary`; `GET /api/v2/subscriptions` | ACTIVE/PAUSED/CANCELED Summary-only list; issue/action/HELD group 금지 |
| Subscription Detail | `V2SubscriptionDetail`; `backend/.../V2SubscriptionCommandApplicationService.java`; `docs/api/API-008-mvp4-subscription-self-service-api-contract.md` | Detail-only issue/availableActions/nextDelivery, ETag, CHANGE_PLAN/주기/날짜/add-on 등 command |
| New Subscription | `v2Api.subscriptions.create({petId,planVersionId,deliveryCycleWeeks})` | Pet→compatible Plan→allowed cycle→summary→create; 날짜/주소/billing 입력 금지 |
| Subscription shipping | `commerceFinalApi.updateSubscriptionShipping(subscriptionId, AddressRequest, csrf)` | addressId assignment가 아니라 full AddressRequest mutation |
| Notifications | `commerce-final-api.ts` `readNotification/readAll` | 개별 read+모두 읽음 정식 interaction |
| Billing | `BillingMethodStatus`, `billingMethod()`, `prepareBilling()` | configured/registered/prepare만; method CRUD 금지 |
| My | `frontend/src/app/my/page.tsx` | OrderSummary/V2Summary/Cart/Notification/reorder timing으로만 dashboard; subscription Detail N+1 금지 |

## 핵심 화면별 Primary / Secondary 근거

| PawCycle 화면 | 외부 Primary/Secondary | 내부 권위 | 채택 / 제외 |
| --- | --- | --- | --- |
| Header/Search | Kurly/Musinsa `CONFIRMED` | `GET /api/products`, ProductDiscoveryReader | 검색 중심 header `ADAPT`; brand/category 자유검색 표현 제외 |
| Home | PetFriends/Kurly `CONFIRMED` | 현재 public discovery/recommendation API | compact hero, category, rail; autoplay 제외 |
| PLP | Kurly/Musinsa/IKEA `CONFIRMED` | Product list query/page contract | filter hierarchy+URL `ADAPT`; 5/6열 과밀 제외 |
| Product Card | PetFriends/Kurly/IKEA `CONFIRMED` | ProductSummary | 가격/리뷰 hierarchy; PLP quick add 제외 |
| PDP | Kurly/PetFriends/IKEA `CONFIRMED` | Product Detail option/review/Q&A API | gallery, option→SKU, sticky purchase `ADAPT`; PDP autoship selector 제외 |
| Compare | IKEA `CONFIRMED` | 기존 PawCycle compare 한도/URL contract | 최대 3개, semantic comparison; 외부 mobile compare UI `UNVERIFIED` |
| Wishlist | Musinsa/IKEA `CONFIRMED` | WishlistItem + add/delete endpoints | anonymous 설명, compact row, 실제 Undo; full Product Card 데이터 제외 |
| Cart | Kurly/IKEA `CONFIRMED` | CartItem/CartResult | 수량·삭제·summary; selection/coupon 제외 |
| Checkout | IKEA `CONFIRMED` | POST `/api/checkout` + Toss | progressive sections; Phase A/B 분리 |
| Checkout Result | 외부 `UNVERIFIED` | Toss redirect + server confirm | verifying/confirmed/unknown/failed |
| Order List | 외부 account list `UNVERIFIED` | `OrderSummary[]` | 날짜/번호/status/amount/detail만 |
| Order Detail | IKEA support context `CONFIRMED` | `OrderDetail.availableActions` | status/action/support 분리 |
| Reorder | IKEA Cart `CONFIRMED` | `QuickReorderResult` | added/skipped 영구 결과 panel |
| Subscription List | Petco/PetSmart `INDIRECT` | `V2SubscriptionSummary` | ACTIVE/PAUSED/CANCELED summary list; issue group 제외 |
| Subscription Detail | Petco/PetSmart/Pet Valu `INDIRECT` | `V2SubscriptionDetail` + command service | next delivery/주기/Plan/add-on/shipping command 분리 |
| New Subscription | Petco/PetSmart `INDIRECT` | v2 create request | Pet→Plan→cycle; first-date/address/billing inputs 제외 |
| My | 외부 `UNVERIFIED` | `/my` current code/API | counts/next delivery/recent order/management; issue N+1 제외 |
| Pets | PetFriends context `CONFIRMED` | v2 Pet list/create/patch | supported fields only; photo/birthday/delete 제외 |
| Notifications | 외부 center `UNVERIFIED` | notifications/read/read-all API | in-app read state only; email/push 제외 |
| Addresses | IKEA Checkout `CONFIRMED` | Address CRUD + separate subscription shipping | 560px edit, safe return; 저장 주소 자동 subscription mutation 제외 |
| Billing | 외부 billing UI `UNVERIFIED` | BillingMethodStatus/prepare | provider status/prepare only |
| Login | Musinsa `CONFIRMED` | sanitizeReturnTo/auth contract | visible labels, safe GET return |
| Notice/FAQ/Support | IKEA `CONFIRMED`, PetSmart `INDIRECT` | PawCycle trust routes | local nav/FAQ disclosure; 새 chat/channel 제외 |

## 증거 사용 규칙

1. `CONFIRMED`는 관찰한 컴포넌트/상호작용에만 붙인다. 그 사이트의 전체 UX 품질을 승인했다는 뜻이 아니다.
2. PawCycle 내부 근거는 가능한 한 파일 경로+endpoint/type 이름으로 식별한다.
3. 외부 패턴과 PawCycle API가 충돌하면 PawCycle 승인 계약이 우선하며 외부 패턴은 `REJECT/UNSUPPORTED`로 기록한다.
4. 실제 account UI를 못 본 Subscription/Billing/My는 계속 `INDIRECT/UNVERIFIED`로 남긴다.
5. 이 문서는 근거 문서이며 A–D의 화면 계약을 새로 정의하거나 덮어쓰지 않는다.