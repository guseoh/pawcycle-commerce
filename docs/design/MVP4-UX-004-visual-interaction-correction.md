# MVP4-UX-004 Visual + Interaction Supplement

## 지위

- 상태: `Supporting Design Specification / Draft / Pending Product Owner Approval`
- 권위: 화면별 실제 기능·API·상태는 A–C, 공통 responsive/accessibility 수치는 D가 권위다.
- 목적: A–D에 정의된 디자인을 구현할 때 필요한 overlay·focus·상태 전이와 대표 화면 composition을 보조한다.
- 이 문서는 A–D를 덮어쓰는 correction이 아니며, 과거 stale 계약의 override 용도로 사용하지 않는다.

## 상태 전이 계약

### Category menu

| 항목 | 계약 |
| --- | --- |
| Trigger | desktop `카테고리`, mobile `메뉴` |
| Initial | 닫힘, `aria-expanded=false` |
| Open | desktop popover 360×min(560, viewport-96)px; mobile left drawer `min(360px,100vw)` |
| User Action | leaf 선택, 하위 group disclosure expand/collapse, 외부 클릭(desktop), 닫기, Escape |
| Transition | leaf 선택→route push; disclosure 개폐는 route/history 변경 없음 |
| Pending | 선택 link만 pressed, 전체 overlay spinner 금지 |
| Success | 새 route `aria-current`, overlay close |
| Error | 현 route 유지+navigation error; 성공처럼 닫지 않음 |
| Close/Focus | background scroll 해제→원 trigger focus; drawer focus trap |
| Keyboard | Tab/Shift+Tab, Enter/Space, Escape; 일반 link DOM 순서 유지 |
| Persistence | open state 저장 금지; 현재 category는 route에서 파생 |

### Filter and sort

| 항목 | 계약 |
| --- | --- |
| Trigger | desktop filter, 1023px 이하 `필터 N`, sort select/button |
| Initial | URL applied 값; mobile draft는 open 시 복사 |
| Open | desktop accordion rail; mobile left drawer; sort는 native select/popover |
| User Action | checkbox/radio, 가격 입력, chip 제거, reset, apply, sort |
| Transition | desktop discrete control 즉시 apply, 가격은 `적용`; mobile은 local draft→`N개 결과 보기` |
| Pending | 기존 결과 유지+toolbar progress+`aria-busy` |
| Success | 결과 수/chips/grid/URL 동시 commit, page=1 |
| Error | 마지막 성공 결과/applied 유지+retry |
| Cancel | mobile Escape/닫기→draft 폐기, URL 불변 |
| Close/Focus | trigger 복귀; apply 성공 후 결과 heading announce |
| URL/History | apply/sort/reset/chip remove push; 단순 정규화 replace; back/forward 결과·scroll 복원 |

### Search

| 항목 | 계약 |
| --- | --- |
| Trigger | header search field, 검색 버튼, Enter |
| Initial | URL `q`; 입력 draft는 local state |
| Open | 자동완성 없음, clear만 조건부 표시 |
| User Action | 입력, clear, Enter, 검색 버튼 |
| Transition | trim 값→`/products?q=...`; 공백→`/products`; clear 자체는 URL 변경 없음 |
| Pending | 기존 page 유지+`검색 중`, 중복 submit 차단 |
| Success | result heading/live count, field는 URL 값과 동기화 |
| Error | 입력 유지+검색 실패 retry |
| Keyboard | Tab/Shift+Tab/Enter, Escape no-op |
| Mobile | initial 56px header+48px search; scroll 후 search row는 사라지고 56px compact header만 sticky |
| Persistence | URL 외 원시 query 저장 금지 |

### PDP purchase

| 항목 | 계약 |
| --- | --- |
| Trigger | PLP image/title 또는 direct URL |
| Initial | product core loading; 구매 가능/가격 확인 전 구매 CTA 확정 금지 |
| Open | option group은 각 의미가 보이는 field로 렌더; gallery lightbox는 click/Enter로만 open |
| User Action | gallery, option group, 수량, 위시, 담기, anchor |
| Transition | `optionGroups` 없음+단일 purchasable SKU→SKU 즉시 선택→quantity enable. option group 존재→필수 값 선택→`selectedOptions` 대조→정확히 한 SKU 결정→quantity enable→Cart intention |
| Disabled | 현재 부분 조합과 일치하는 SKU가 0개인 option value는 disabled+이유. SKU 미결정/재고0/`purchasable=false`는 Cart CTA disabled+이유 |
| Pending | 담기 `담는 중`; 선택 SKU의 가격/재고 확인 중 상태를 분리 |
| Success | 서버 확인 뒤 cart count/status와 `/cart` link |
| Error | option/quantity 유지 가능한 범위 유지, 최신 상품 상태+retry |
| Focus | gallery change는 강제 focus 없음; lightbox close→trigger; 담기 결과 announce |
| Mobile | gallery→summary→option/qty→trust→detail; 64px 가격+담기 bar, keyboard/overlay 시 static 또는 숨김 |
| Persistence | option/quantity local; Cart server state |

### Wishlist remove / undo

| 항목 | 계약 |
| --- | --- |
| Trigger | Wishlist row의 `위시에서 제거` |
| Initial | 서버 목록 row 유지 |
| Transition | DELETE 요청→성공 후 row 제거→6초 Undo toast |
| Undo | 실제 POST addWishlist 호출→성공 시 row 복구 |
| Error | DELETE 실패면 row 유지+inline retry; Undo 실패면 제거 상태 유지+`다시 저장` retry |
| Focus | 삭제 성공 후 다음 row 또는 heading; Undo toast가 focus를 강제로 가져가지 않음 |
| Accessibility | remove/undo 모두 상품명을 accessible name에 포함; status/alert 구분 |

### Cart

| 항목 | 계약 |
| --- | --- |
| Trigger | header Cart, PDP 성공의 `장바구니 보기` |
| Initial | server Cart loading; last-known total을 확정값처럼 표시 금지 |
| User Action | 수량 변경, item 삭제, 장바구니 전체 Checkout |
| 금지 | 전체/개별 선택, 선택 상품 checkout, Cart coupon 적용/해제, thumbnail 존재 가정 |
| Transition | item mutation→서버 Cart/version/pricing 재확인; 연속 수량 click queue 금지 |
| Pending | 해당 item+summary `계산 중`; Checkout disabled+이유 |
| Success | item/pricing/version을 서버 값으로 갱신 |
| Error | 실패 item 기존 값 유지/복구+retry; 409는 최신 Cart 전체 재확인 |
| Focus | 총액 변화 live 1회; 삭제 성공 후 다음 row/heading |
| Keyboard | +/-와 삭제 44px, CTA keyboard reachable |
| Mobile | text row→수량/가격→삭제; 64px `총 N개 · 총액 주문하기` |

### Checkout and payment

| 항목 | Phase A — 주문/결제 준비 | Phase B — Toss 결제 |
| --- | --- | --- |
| Trigger | 유효한 server Cart | CheckoutResult |
| User Action | 주소, optional coupon, `주문 및 결제 준비` | widget 결제수단/약관, `결제하기`, provider 승인/취소 |
| Transition | cartVersion+address+coupon→POST `/api/checkout`→CheckoutResult | CheckoutResult→widget ready→provider redirect→success/fail→server confirm |
| Pending | CTA `준비 중`, 동일 intention Idempotency-Key 유지 | widget/payment pending, provider 중복 승인 차단 |
| Success | prepared context 생성; 결제 완료로 표현 금지 | redirect 후 server confirm이 confirmed일 때만 완료 |
| Error | validation/CART_CHANGED/network unknown 분리 | widget fail/provider fail/unknown 분리 |
| Navigation | address는 sanitized GET returnTo. Billing route를 필수로 요구하지 않음 | back/refresh/provider cancel에서 approval 자동 replay 금지 |
| Mobile | 64px amount+Phase A CTA 하나 | 64px amount+Phase B CTA 하나; 동시에 두 primary 없음 |

저장 Billing Method selector/default/delete는 Checkout interaction이 아니다.

### Subscription management

| 항목 | 계약 |
| --- | --- |
| Trigger | Detail의 `availableActions`; List는 command를 직접 실행하지 않음 |
| Initial | top-level status, nextDelivery, issue, pendingChange, ETag, actions를 Detail server state에서 렌더 |
| Open | 날짜/주기/Plan/shipping을 각각 별도 dialog/surface로 분리 |
| User Action | 날짜 변경, 주기 변경, Plan 변경, skip/pause/resume/cancel, 다음 배송 add-on, shipping address |
| CHANGE_PLAN | 현재 Pet 호환+판매 중+effective cycle 지원 Plan만 후보. planVersionId 중심 command; legacy pet 보완이 서버상 필요할 때만 petId. add-on conflict는 자동 제거 금지 |
| Quantity | 기본 Plan item quantity 변경 금지. quantity는 다음 배송 add-on의 실제 지원 범위에서만 사용 |
| Shipping | 저장 주소 선택→full AddressRequest draft copy→사용자 확인→shipping PUT. 저장 주소 선택만으로 mutation 금지. PUT 성공 뒤에는 Detail의 issue/availableActions 변화만 server read-back 사실로 사용하며 현재 배송지 주소 문자열은 read-back 값처럼 표시하지 않음 |
| Pending | action별 `변경 중`, ETag/Idempotency-Key 유지, 충돌 가능 action 잠금 |
| Success | command는 최신 Detail/ETag 재조회. shipping은 mutation 성공 안내+Detail issue/action 재확인; current address summary 생성 금지 |
| Error | validation/409/412/HELD issue 구분; 자동 재적용 금지 |
| HELD | top-level status가 아니라 `ACTIVE + nextDelivery.status=HELD` 등 Schedule 상태로 설명 |
| Mobile | issue→next delivery→Plan/cycle→items→shipping/history→actions→danger; 복잡 edit full-screen |

## Annotated composition specifications

모든 수치는 D8 SSOT를 사용한다.

| 화면 | Desktop | Mobile | sticky·상태 annotation |
| --- | --- | --- | --- |
| Home | header→hero max320→pet category→routine strip→대표 rail→개인화→trust | 56+48 header/search→hero→2열 category→rail | hero/rail 자동재생 없음; 추천 실패 section 격리 |
| PLP | breadcrumb→h1/chips/toolbar→232px rail+24px+4×약281px→pagination | h1→chips→filter/sort→2열; 320–359 1열 row | 4/3/3/2/2/2/1; toolbar top offset D8 |
| PDP | breadcrumb→gallery7/summary5→anchor→detail/review/Q&A/recommend/trust | gallery→title/price→option/qty→trust→details→review/Q&A | 단일 SKU는 option 단계 생략; mobile 64px purchase bar |
| Cart | h1→8열 text item list / 4열 summary | h1→items→summary | desktop summary `top:64px`; selection/coupon 없음; mobile total Cart CTA |
| Checkout | h1→8열 Phase A / 4열 summary→Phase B widget | order disclosure→배송→coupon→준비→Toss | Phase A/B CTA 동시 활성화 금지 |
| Order List | h1→OrderSummary rows | compact rows | filter/pagination/대표상품 없음; server order 유지 |
| Order Detail | status→items/delivery/payment→timeline→cancel/return→reorder/subscription→support | 동일 의미 순서 | availableActions만; partial reorder 영구 panel |
| Subscription List | ACTIVE→PAUSED→CANCELED summary rows | 상태→날짜→Pet/주기/가격→상세 | issue/actions/HELD group 없음 |
| Subscription Detail | status/issue→next delivery→Plan/cycle→items/shipping action/history→actions | 동일 의미 1열 | 날짜/주기/Plan/shipping 분리; current shipping read-back 없음; pending/HELD detail-only |
| My | account→commerce counts→next subscription→recent order→timing→management | 동일 의미 1열 | Detail N+1 issue section 없음 |
| Mobile header | 해당 없음 | 56px menu/logo/cart+48px search | initial 104→sticky56 |
| Login | reading760 안 form480 | gutter16, fields/CTA52 | password reveal44, sanitized GET returnTo |

## 구현 전에 폐기된 과거 표현

다음 표현은 더 이상 구현 기준이 아니다.

- PLP 5열
- Cart 전체/개별 selection, selected subtotal, selected item checkout
- Cart coupon disclosure/apply/remove
- Cart thumbnail 필수 layout
- Checkout 저장 Billing Method selector/필수 등록
- Order List filter/pagination/대표 상품 summary
- 모든 PDP에서 필수 option 선택
- PLP/Home representative SKU quick add
- Subscription List issue/availableActions/HELD group
- 기본 Subscription Plan item quantity 변경
- Subscription current shipping address read-back
- Cart summary top 96px

`NO FRONTEND IMPLEMENTATION PERFORMED`
