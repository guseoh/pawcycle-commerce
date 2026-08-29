# MVP4-UX-004 Customer Commerce UI/UX 전면 재설계

## 문서 지위와 범위

- 작업 ID: `MVP4-UX-004`
- 등급: 일반
- 실행 구분: 저장소 문서 변경. Production·Cloud·운영 DB 실행이 아니다.
- 역할: UX/UI Designer
- 상태: `Proposed Design Contract / Draft / Pending Product Owner Approval`
- `main` 감사 기준: `626e1d2bd4b30f70e0e9a35decad4ef43ed1ddb9`
- FE-004 감사 snapshot: `09875636e9142d2b5ea8b618801ddca480c38ad3` (`feat/fe/MVP4-FE-004`는 보조 branch 이름)
- 포함: 고객용 탐색, 구매, 주문, 정기배송, 계정, 공통 반응형·접근성 설계 계약
- 제외: Frontend/Backend 코드, API·DB·인프라 변경, 새 제품 기능 승인, 운영 실행, 병합

이 문서는 MVP4 고객 경험의 **승인 제안안**이다. A–D가 화면·공통 구현 계약의 본문이며, [벤치마크 증거](./MVP4-UX-004-benchmark-evidence.md)와 [Visual + Interaction Supplement](./MVP4-UX-004-visual-interaction-correction.md)는 보조 근거다. Product Owner 승인 전에는 승인 완료 상태나 Frontend 구현 권위로 취급하지 않는다. FE-004는 위 immutable SHA의 감사 입력이며 이후 branch 이동으로 과거 감사 판정을 바꾸지 않는다.

## 승인 입력과 비승인 경계

### 보존되는 승인 입력

- 서버가 가격, 구매 가능 여부, 다음 배송일, 허용 액션, 충돌 버전의 권위다.
- 정기배송의 배송일·배송 주기·Plan 변경은 서로 다른 command다.
- 인증 후 복귀는 안전한 내부 GET 경로만 허용하며 작성 중 form이나 POST를 자동 재실행하지 않는다.
- 비교는 2–3개 상품, Pet profile은 현재 API가 지원하는 필드만 사용한다.
- email/push/SMS가 아닌 in-app notification만 MVP4 범위다.
- 원시 검색어는 상호작용 기록에 저장하지 않는다.
- 외부 Commerce 패턴보다 최신 PawCycle API·코드 계약이 우선한다.

### 설계가 만들지 않는 제품 기능

최근 검색 저장/개인화 autocomplete, Pet 사진·생일·삭제, 품절 대체품 자동 지정, 매장 픽업·당일 배송, 구독 자동 주기 적용, 결제 실패 자동 retry, 상담 chat, 리뷰 이미지 업로드는 추가하지 않는다.

현재 API 정합성상 다음도 MVP4-UX-004에서 만들지 않는다.

- Cart item selection / 선택 상품 Checkout
- Cart coupon apply/remove
- 저장 Billing Method list/default/delete와 Checkout selector
- Order List filter/pagination/대표 상품 summary
- PLP/Home representative SKU quick add
- Subscription List issue/availableActions/HELD top-level group
- Subscription Plan 기본 item quantity 변경
- Wishlist 응답에 없는 image/price/stock 가정

## 감사된 고객 라우트 인벤토리

| 영역 | 실제 라우트 | 현재 주요 상태·의존성 | 재설계 번들 |
| --- | --- | --- | --- |
| 전역 셸 | 모든 고객 라우트 | `AppHeader`, `AppFooter`, `AuthProvider`; auth 4상태 | D |
| 홈 | `/` | public discovery/recommendation, auth, Pet profile, Product Card | A |
| 상품 목록·검색 | `/products` | URL query/filter/sort/page, compare, stale guard | A |
| 상품 상세 | `/products/[productId]` | optionGroups/SKU selectedOptions, review/Q&A, recommendation, purchasable | A |
| 비교 | `/compare` | 2–3개 비교 | A |
| 위시리스트 | `/wishlist` | `WishlistItem(productId, productName, createdAt)`, add/delete | A |
| 장바구니 | `/cart` | `CartItem` 수량·가격·구매가능·version; selection/coupon 없음 | B |
| 결제 | `/checkout` | address, optional coupon, cartVersion/idempotency→CheckoutResult→Toss | B |
| 결제 결과 | `/checkout/success`, `/checkout/fail` | provider redirect 뒤 server confirmation | B |
| 주문 | `/orders`, `/orders/[orderId]` | list `OrderSummary[]`; Detail actions/cancel/return/reorder | B |
| 정기배송 | `/subscriptions`, `/subscriptions/[subscriptionId]`, `/subscriptions/new` | Summary vs Detail 분리, ETag, v2 commands, create 3 fields | C |
| 이전 정기배송 별칭 | `/mvp2/subscriptions*` | 현행 화면으로 연결되는 호환 경로 | C |
| 마이 | `/my` | Order/V2 Summary, Cart, notification, reorder timing | C |
| Pet | `/pets` | list/create, name/breed/weightKg patch, petType immutable | C |
| 알림 | `/notifications` | in-app list, read, read-all | C |
| 배송지 | `/addresses` | address CRUD/default; subscription shipping은 별도 full AddressRequest | C |
| Billing | `/billing-methods` | Toss `{configured, registered}` + prepare | C |
| 인증 | `/login` | safe `returnTo`, session/CSRF lifecycle | C/D |
| 신뢰·지원 | `/notice`, `/faq`, `/support`, `/shipping`, `/returns` | static trust content | C/D |

`/admin/catalog`, `/admin/catalog/products/[productId]`, `/admin/operations`는 고객 경험 범위에서 제외한다.

## 실제 컴포넌트·API·상태 감사

| 소비 영역 | 확인한 권위 | 설계 영향 |
| --- | --- | --- |
| 공통 인증 | `AuthProvider`, `useAuth`, `sanitizeReturnTo`, CSRF lifecycle | auth 확인 전 empty flash 금지, safe GET return |
| Product discovery | `ProductDiscoveryReader`, `GET /api/products`, API-012 | q/brand/category 의미 분리, URL SSOT, PLP density |
| PDP | Product Detail `optionGroups`, SKU `selectedOptions`, Review/Q&A API | option→SKU, Review create/update/delete, Question create 보존 |
| Wishlist/Cart/Order | `frontend/src/lib/commerce-final-api.ts` | 실제 response field 밖 정보를 추정하지 않음 |
| Checkout/Toss | `commerceFinalApi.checkout`, CheckoutResult, Toss confirm | Phase A context 생성과 Phase B payment 분리 |
| Subscription | `frontend/src/lib/v2-api.ts`, V2 command service, API-008 | Summary/Detail 분리, ETag/idempotency, Plan/주기/날짜 command 분리 |
| My | `frontend/src/app/my/page.tsx` | summary data만 사용, subscription Detail N+1 금지 |
| 검색/history | catalog query parser와 stale guard | URL single authority, back/scroll/focus 복원 |

## FE-004 Draft 감사 snapshot

감사는 **`09875636e9142d2b5ea8b618801ddca480c38ad3`**에 고정한다. 이 snapshot은 당시 최신 main 대비 34개 파일의 Draft 변경을 포함했고, 주소/Billing/Cart/Checkout/My/Home/PLP와 공통 commerce CSS·문서를 수정하고 있었다. branch가 이후 이동하더라도 이 섹션의 “34개 파일”과 gap 판정은 이 SHA를 기준으로 재현한다.

| Draft 의도 | 감사 판정 | MVP4-UX-004 기준 |
| --- | --- | --- |
| 1,480px 단일 확장 셸 | `SUPERSEDE` | 탐색 1,440 / PDP 1,320 / 거래 1,180 / 읽기 760으로 목적별 분리 |
| 평면적 여백·divider와 card 축소 | `ADOPT` | 독립 객체만 card, 반복 거래/계정은 row/divider |
| compact control과 dense PLP | `ADAPT` | target 44px, D8의 4/3/3/2/2/2/1 |
| 연속형 PDP | `ADOPT` | gallery/purchase 7/5, anchors, async sections, option→SKU, mobile CTA |
| 기존 주요 거래·계정 CSS | 재평가 필요 | A–D 승인 뒤 별도 Frontend 작업에서 현재 HEAD와 gap 재산정 |

Draft 코드를 이 디자인 문서에서 소급 수정하지 않는다.

## 권위 관계와 변경 판정

| 기존 결정 | 판정 | 새 기준 |
| --- | --- | --- |
| UX-002 `Warm Utility Commerce` | `ADAPT` | `Warm Routine Commerce`로 구체화 |
| cream/green + 절제된 장식 | `ADOPT` | 역할/대비/state token 명시 |
| 서버 권위와 안전한 auth return | `ADOPT` | mutation/retry/conflict 전체에 적용 |
| UX-003 큰 card·과도한 여백 축소 | `ADOPT` | 상품/거래 화면의 평면적 정보 밀도 |
| 1,200–1,280px 중심 셸 | `SUPERSEDE` | 1,440/1,320/1,180/760 목적별 폭 |
| 1,480px 단일 셸 | `SUPERSEDE` | route별 width |
| 4열 fixed grid | `ADAPT` | D8 4/3/3/2/2/2/1 |
| mobile bottom nav | `PENDING PO` | 미도입 Recommended Default |
| 모든 내용을 card로 감싸기 | `REJECT` | divider/spacing/surface 우선 |

## 외부 벤치마크 경계

상세 관찰과 내부 추적 근거는 [benchmark evidence](./MVP4-UX-004-benchmark-evidence.md)가 SSOT다.

- Kurly/Musinsa/IKEA/PetFriends에서 직접 확인한 header, PLP, PDP, Cart, Checkout, mobile interaction은 `CONFIRMED` 범위 안에서만 사용한다.
- Petco/PetSmart/Pet Valu 정기배송은 공식 설명 기반 `INDIRECT`다. 실제 account UI는 `UNVERIFIED`다.
- 외부 Cart의 selection/coupon, 외부 PLP quick add, Autoship quantity 등은 PawCycle 현재 API가 없으면 도입하지 않는다.
- 접근 안 된 Chewy/Petco account 화면은 관행으로 추정하지 않는다. 다른 접근 가능한 service 조사 후보로 남기되 현재 계약을 막는 근거로 사용하지 않는다.

## 최종 시각 방향: Warm Routine Commerce

반려동물의 반복 소비를 “상품 판매”만이 아니라 “다음 급여·교체·배송을 준비하는 일상”으로 보이게 한다. 따뜻하지만 장식적이지 않고, 구매·배송·정기배송 상태는 운영 도구처럼 명확해야 한다.

### 색상 역할

| 토큰 | 값 | 역할 |
| --- | --- | --- |
| `canvas` | `#F7F4EC` | 전체 배경 |
| `surface` | `#FFFFFF` | 입력·거래·독립 콘텐츠 표면 |
| `surface-soft` | `#EFE9DA` | 구역 구분 |
| `text-strong` | `#17231D` | 제목·가격·핵심 상태 |
| `text` | `#33443B` | 본문 |
| `text-muted` | `#5C6A62` | 보조 설명·metadata; canvas/soft/selected surface에서 일반 text AA 확보 |
| `brand` | `#1F6B4F` | 주 CTA·활성 link |
| `brand-hover` | `#18563F` | hover/pressed |
| `accent-text` | `#A14600` | 할인·주의 text |
| `accent-soft` | `#FFF1E6` | 할인·주의 background |
| `success` | `#257A4D` | 완료·정상 |
| `warning` | `#9A5A13` | 보류·확인 필요 |
| `danger` | `#B42318` | 실패·파괴 행동 |
| `border-soft` | `#D7D8D1` | section 경계 |
| `border-control` | `#727E77` | control 외곽선 |
| `selected-soft` | `#DCEFE6` | 선택·활성 background |
| `disabled-surface` | `#ECEFEB` | disabled background |
| `disabled-text` | `#6A746F` | disabled text |
| `focus` | `#0B63CE` | keyboard focus 2px |

검증된 `text-muted` 대비는 약 `canvas 5.17:1`, `surface-soft 4.69:1`, `selected-soft 4.75:1`, `white 5.69:1`이다. 일반 text 4.5:1, 큰 text·non-text control 3:1을 하한으로 삼는다. 색만으로 상태를 구분하지 않는다.

### 타이포그래피와 밀도

- 현재 system sans-serif stack 유지, 새 font dependency 없음.
- size: `12 / 14 / 16 / 18 / 20 / 24 / 30 / 38 / 48px`.
- body 16/1.6, secondary 14/1.5, label 14/1.3, price 20–30/1.2.
- weight: 400 body, 500 label, 600 section/button, 700 price/key title.
- body line length 65–75자, help 55–65자 권장.

### 형태·표면·아이콘·이미지

- radius: input/button 8px, product/독립 object 10px, large media 16px, status pill full.
- shadow는 popover/drawer/sticky overlap 구분에만 사용. 일반 section은 border/spacing.
- icon: 20px default, 16px meta, 24px key action; emoji를 기능 icon으로 쓰지 않는다.
- Product image: Card 1:1 contain, PDP 4:5 영역 안 1:1 safe area.
- 반복 목록은 card보다 row 우선. 전체 clickable card 안에 nested button을 만들지 않는다.

### 간격·모션

- spacing: `4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64 / 80px`.
- control: default 44px, primary transaction 48px, mobile field/CTA 52px. touch target 최소 44×44.
- motion: micro 80ms, control 140ms, overlay 220ms; opacity/transform 중심.
- reduced motion에서는 parallax/zoom/smooth scroll을 제거한다. Home hero/상품 rail은 애초에 autoplay하지 않는다.

## 레이아웃 시스템

| 컨텍스트 | 최대 콘텐츠 폭 | 열·간격 | 주요 사용 |
| --- | --- | --- | --- |
| 탐색·PLP | 1,440px | 12열, 24px gap | Home, PLP |
| 상품 상세 | 1,320px | 12열, 24px gap | PDP, Compare |
| 거래·계정 | 1,180px | 12열, 24px gap | Cart, Checkout, Order, Subscription, account |
| 읽기·폼 | 760px | 단일 열 | Login, support, compact form |

breakpoint, gutter, PLP columns, header, sticky offset의 SSOT는 [D8](./MVP4-UX-004-d-shared-responsive-accessibility.md#d8-반응형-기준)이다.

## 공통 상호작용 계약

### URL, 검색, 필터, 뒤로가기

1. URL은 제출된 `q`, 적용된 filter/sort/page, compare IDs 같은 공유 가능한 탐색 상태의 single authority다.
2. Search draft는 local이고 submit할 때만 `/products?q=`로 push한다. 현재 q는 product name/description 의미를 넘지 않는다.
3. Desktop filter discrete value는 apply 시 URL, mobile drawer는 local draft→`적용`에서 URL commit.
4. PLP→PDP back은 URL/result/scroll/source product focus를 복원한다.
5. Order List처럼 filter/page 계약이 없는 목록에는 존재하지 않는 URL state를 만들지 않고 scroll/focus만 복원한다.

### Overlay

- modal: 위험 확인/짧은 결과. focus trap/Escape/trigger return.
- drawer: mobile category/filter.
- popover: account/category/sort 등 짧은 선택.
- full-screen dialog: mobile address/복잡 subscription edit.
- toast: non-destructive success 또는 실제 inverse API가 있는 Wishlist Undo에만 사용.

### 제출·실패·권위

- mutation은 first activation에서 duplicate를 막고 진행 label을 제공한다.
- Idempotency-Key를 지원하는 Checkout/Subscription/Reorder는 동일 intention의 retry 의미를 server replay/conflict 계약과 맞춘다.
- 서버 confirm 전 `저장됨/결제완료/구독변경완료`로 표현하지 않는다.
- auth expiry 뒤 POST/form/payment를 자동 replay하지 않는다.
- conflict는 latest server state와 사용자의 이전 시도를 구분하고 자동 재적용하지 않는다.

## 화면별 문서 번들

- [A. 탐색·구매 진입](./MVP4-UX-004-a-discovery-purchase-entry.md)
- [B. 장바구니·결제·주문](./MVP4-UX-004-b-cart-checkout-orders.md)
- [C. 정기배송·계정·지원](./MVP4-UX-004-c-subscription-account.md)
- [D. 공통 컴포넌트·반응형·접근성](./MVP4-UX-004-d-shared-responsive-accessibility.md)
- [실제 Commerce 벤치마크 증거](./MVP4-UX-004-benchmark-evidence.md)
- [Visual + Interaction Supplement](./MVP4-UX-004-visual-interaction-correction.md)

A–D 자체가 implementation-ready Draft contract다. 보조 문서의 과거 문장으로 A–D를 뒤집지 않는다.

## 현재 구현/Draft와의 갭

| 영역 | 현재 확인 | Frontend 승인 후 영향 |
| --- | --- | --- |
| 공통 셸 | Header/Footer/Auth 존재 | route container, header responsive, focus/overlay 정렬 |
| Home/PLP/PDP | public discovery, option/review/Q&A 존재 | visual hierarchy, URL/history, option→SKU, async section |
| Wishlist | 최소 `WishlistItem` + add/delete | compact row와 실제 Undo; full Product Card 가정 제거 |
| Cart | quantity/pricing/version, selection/coupon/image 없음 | text row, server Cart 전체 Checkout, conflict recovery |
| Checkout | address/coupon/cartVersion→CheckoutResult→Toss | Phase A/B, unknown/confirm, mobile CTA |
| Order | Summary list + rich Detail | list는 현재 fields만, Detail actions/partial reorder |
| Subscription | Summary/Detail/create/command contract | Summary list, Detail command 분리, shipping AddressRequest |
| Account | My/Pet/Notification/Address/Billing 존재 | 실제 API 범위로 IA/row/edit 통일 |
| FE-004 snapshot | `09875636...` | 디자인 승인 뒤 현재 Frontend HEAD와 별도 gap 재산정 |

## 미결 Product Owner 결정과 Recommended Default

| # | PENDING PO | Recommended Default | 근거·Trade-off | 영향 |
| --- | --- | --- | --- | --- |
| 1 | mobile bottom navigation | MVP4 미도입 | sticky transaction과 충돌/화면 점유 최소화; route 접근 한 단계 증가 | mobile 전체 |
| 2 | Home 사회적 증거 | `인기 상품` 1 section | stable evidence, trend freshness는 약함 | `/` |
| 3 | Checkout CTA copy | Phase A `주문 및 결제 준비`, Phase B `결제하기` | context 생성과 실제 provider approval을 구분 | Cart/Checkout/result |
| 4 | anonymous Wishlist | inline 이유 설명→`로그인하기` | surprise redirect 감소, activation 1회 증가 | Card/PDP/Wishlist/Login |
| 5 | 신규 Subscription 주 진입 | Order Detail `정기배송으로 다시 받기` | eligible context 명확, 독립 발견성은 낮음 | Order Detail/New Subscription |
| 6 | contextual support | Footer `/support` + Order/Subscription Detail context link | 해결 속도↑, detail density↑ | footer/details/support |

PO 결정 전 Recommended Default는 Draft 검토 기준이며 승인으로 해석하지 않는다.

## 전역 인수 조건

- 모든 고객 route가 A–D에 연결되고 loading/empty/error/success/retry가 명시된다.
- 320/375/768/1024/1440, 200% zoom에서 가로 scroll/가려진 CTA 없음.
- keyboard로 search/filter/PDP option/Cart/Checkout/Subscription/account 핵심 흐름 수행 가능.
- focus 순서=visual order, modal/drawer close→논리적 trigger return.
- 실제 URL contract가 있는 search/filter/sort/page만 history와 동기화.
- auth/idempotency/stale/conflict/partial/unknown이 server authority를 위반하지 않음.
- Unsupported 기능이 placeholder/disabled control로 노출되지 않음.
- FE-004에 실제 적용하는 작업은 Design Approval 뒤 별도 Frontend task에서 현재 HEAD 기준으로 수행.

## 검증 방법

- 최신 main API/client/state model과 A–D를 재대조한다.
- stale 문구(`5열`, Cart selection/coupon, saved billing CRUD, Order filter/page, HELD top-level, Address 760px, Cart sticky 96px 등)를 전체 MVP4-UX-004 문서에서 검색한다.
- relative links/Markdown/path scope를 확인한다.
- 문서 변경만이므로 Frontend test/UI automation/Production validation은 실행하지 않는다.

`NO FRONTEND IMPLEMENTATION PERFORMED`