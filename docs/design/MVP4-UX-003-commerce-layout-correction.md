# MVP4-UX-003 Commerce Layout Correction

- 상태: Active visual correction delta
- 대상 구현: `MVP4-FE-004`
- 대상 PR: `#252`
- 기준: 사용자 실제 Browser 시각 검토 + 2026-08-29 공개 Commerce reference 재검토
- 목적: 기존 `MVP4-UX-003-visual-design-spec.md`의 제품/API/접근성 계약은 유지하되, 과도한 card-first 구성과 좁은 desktop canvas를 수정한다.

## 1. Authority

이 문서는 `MVP4-UX-003-visual-design-spec.md`의 **시각 레이아웃·밀도·surface·screen composition 규칙에 한해서 우선하는 correction delta**다.

다음은 그대로 유지한다.

- 제품/도메인/API/DB 계약
- Pet 필드와 immutable petType 계약
- Compare 2~3개 계약
- PDP의 일반 구매 + `/subscriptions/new` 진입 계약
- Cart에서 subscription 상태를 추론하지 않는 계약
- Subscription의 server-authoritative 상태/action 계약
- raw search query 비저장 계약
- 접근성, reduced-motion, 44px minimum target의 목적
- asset provenance 규칙

이번 correction은 새 기능을 추가하지 않는다.

## 2. 왜 correction이 필요한가

실제 Browser 검토에서 다음 문제가 확인됐다.

1. 넓은 desktop에서도 content가 1240px 안에 과도하게 모여 좌우 여백은 크고 내부는 답답하다.
2. section card → item card → control card 구조가 반복되어 쇼핑몰보다 dashboard처럼 보인다.
3. Product List에서 filter와 product card가 모두 크게 보여 상품 밀도가 낮다.
4. PDP에서 gallery가 지나치게 크고 상품 정보와 구매 영역을 여러 card로 분할해 하나의 구매 흐름으로 읽히지 않는다.
5. Home category/brand가 outer card + inner card로 반복되어 merchandising보다 box가 더 눈에 띈다.
6. category dropdown이 좁고 길어 desktop navigation으로 불편하다. 반대로 화면 전체를 크게 덮는 mega menu도 PawCycle 규모에는 과하다.
7. 44px touch target을 모든 control의 시각적 크기로 해석해 desktop control까지 무겁게 보이는 경향이 있다.

따라서 이번 correction의 핵심은 **크기 축소가 아니라 layout grammar 교체**다.

## 3. Reference application — 이름이 아니라 패턴을 가져온다

### Chewy

가져올 것:
- pet-first discovery
- category와 product merchandising이 페이지 본문으로 자연스럽게 이어지는 구조
- Autoship을 별도 dashboard 기능이 아니라 구매/재구매 흐름으로 연결하는 방식
- PDP에서 상품 정보와 구매 action을 연속적으로 읽는 방식

가져오지 않을 것:
- PawCycle 계약에 없는 서비스/프로모션
- 시각적 복제

### Petco

가져올 것:
- 결과 수·정렬·filter와 product grid가 가까이 붙는 PLP 밀도
- filter가 상품보다 시각적으로 강해지지 않는 구조
- category page에서 popular category가 있더라도 빠르게 product result로 이어지는 방식

가져오지 않을 것:
- PawCycle에 없는 수십 개 facet
- store pickup/same-day 같은 미승인 기능

### Musinsa / Kurly / 국내 Commerce 공통 패턴

가져올 것:
- product image / brand / name / price가 card chrome보다 우선하는 grid
- 검색을 중심에 둔 header
- 넓은 desktop canvas와 높은 상품 스캔 밀도
- section을 border card보다 whitespace/divider로 나누는 방식

가져오지 않을 것:
- 캠페인 carousel 과잉
- 큰 hero 반복
- PawCycle에 없는 프로모션/쿠폰 노출

## 4. Global layout reset

### 4.1 Desktop canvas

기존 `1240px max-width`를 기본값으로 사용하지 않는다.

- Global commerce shell: `width: min(calc(100% - 48px), 1480px)`
- 1600px 이상에서는 상품 탐색 화면이 충분한 폭을 사용해야 한다.
- 1280~1599px에서는 viewport gutter 24~32px를 유지하면서 fluid하게 축소한다.
- article/help/form처럼 읽기 폭이 중요한 화면만 별도 max-width를 사용한다.
- 모든 route를 동일한 좁은 centered card 안에 넣지 않는다.

### 4.2 Vertical rhythm

- major section gap: desktop `48~56px`, mobile `36~44px`
- title → content: desktop `16~20px`, mobile `12~16px`
- hero 다음 핵심 commerce section은 불필요한 80px 공백을 만들지 않는다.
- page title은 desktop `30~34px`를 기본으로 한다.
- Home display만 `40~44px`까지 허용한다.

### 4.3 Surface hierarchy

기본은 **flat page + whitespace + divider**다.

Card를 사용하는 경우:
- 실제 독립 object: product card, order item summary, next-delivery highlight, issue/action-required block
- modal/popover
- 명확한 선택 단위

Card를 사용하지 않는 경우:
- page 전체
- category section 전체
- brand section 전체
- PDP 기본 상품 정보 전체
- desktop filter rail 전체
- My/Subscription의 모든 작은 기능을 동일한 tile로 만드는 방식

금지:
- section card 안에 item card를 반복하고 그 안에 다시 control card를 만드는 3중 surface
- 모든 section에 border + radius + background를 동시에 적용
- dashboard tile처럼 같은 크기의 관리 card를 균등 반복

### 4.4 Radius/elevation

- 일반 product/control surface: `6~10px`
- 큰 section을 rounded rectangle로 감싸지 않는다.
- shadow는 dropdown/modal/sticky overlay 중심으로만 사용한다.
- product card hover translate/shadow는 필수가 아니다. product image/텍스트가 주인공이어야 한다.

### 4.5 Controls

44px는 **hit area 기준**이다.

- desktop secondary/tertiary control을 모두 44px 높이의 큰 button처럼 보이게 만들지 않는다.
- visual height는 compact하게 유지하되 padding/hit area로 접근성 target을 확보할 수 있다.
- primary purchase CTA만 충분히 강하게 표시한다.

## 5. Header / navigation reset

Desktop header는 commerce 탐색을 빠르게 시작하는 구조로 유지하되 과도한 메뉴를 만들지 않는다.

Primary row:
- logo
- 중앙/넓은 search
- wishlist/cart/notification/My 또는 로그인 utility cluster

Secondary row:
- 상품
- DOG
- CAT
- 정기배송
- 주문

`카테고리`는 긴 dropdown이나 full-width mega menu를 기본 탐색으로 사용하지 않는다.

- 전체 category 선택은 `/products`의 filter/discovery에서 해결한다.
- DOG/CAT은 petType 진입점으로 사용한다.
- 별도 category popover가 꼭 필요하면 1차 category만 compact하게 보여주고 2depth 전체를 세로로 길게 늘어놓지 않는다.
- page를 아래로 밀어내는 expanded menu 금지
- viewport 대부분을 덮는 mega menu 금지

## 6. Home

목표: dashboard가 아니라 pet commerce storefront.

Desktop order:
1. compact hero — copy + 하나의 primary CTA + optional image
2. authenticated: pet context / reorder / next delivery를 하나의 compact routine strip 또는 2-column utility row로 제공
3. category discovery — outer card 없이 compact text/image links
4. personalized product rail — 4~5 visible products
5. popular/trending product rail
6. brands — 낮은 높이의 text/logo links, outer card 없음
7. repeat-commerce benefit / trust

규칙:
- category 하나당 큰 card 금지
- brand 하나당 큰 tile 금지
- Home 전체를 card sections로 쪼개지 않는다.
- 상품 section은 실제 product image와 price가 가장 많이 보여야 한다.
- 로그인 Home에서 pet routine이 marketing hero보다 커지지 않는다.

## 7. Product List / Search

목표: product가 화면 대부분을 차지하는 dense commerce grid.

Desktop:
- top: breadcrumb/context + title + result count + sort
- active filters는 얇은 chip row
- body: `220~240px` filter rail + fluid product grid
- 1480px shell에서 filter 포함 4~5 product columns가 자연스럽게 보이도록 한다.
- 1280px 부근에서는 4 columns 우선
- tablet에서 3/2 columns

Filter:
- desktop filter 전체를 큰 card로 감싸지 않는다.
- section heading + checkbox/select + divider 구조
- filter rail 자체 background/border는 최소화

Product card:
- borderless 또는 매우 약한 boundary
- image가 가장 큰 요소
- brand → name → rating/meta → price → subscription/stock 1줄
- 항상 큰 bottom action button을 넣지 않는다. 상품 클릭/텍스트 링크 또는 필요한 action만 사용한다.
- compare/wishlist가 card layout을 밀어내지 않게 compact control로 배치한다.

## 8. Product Detail

목표: `Gallery + 하나의 연속된 purchase column`.

Desktop shell:
- 2-column, gallery `minmax(0, 1.1fr)` / purchase `minmax(420px, .9fr)` 수준
- primary gallery는 무조건 560px 이상을 강제하지 않는다.
- 이미지가 viewport보다 구매 정보의 첫 화면 노출을 방해하지 않게 최대 높이를 제어한다.

Purchase column order:
1. brand
2. title
3. rating/review
4. price
5. delivery / stock / subscription eligibility
6. divider
7. option
8. quantity
9. primary cart CTA
10. wishlist / compare compact actions
11. eligible only: `/subscriptions/new?...` secondary entry
12. trust/detail helper

금지:
- 상품 기본 정보 card + 구매 card를 별도 큰 surface로 중첩
- 가격/상품명과 option 영역 사이에 큰 빈 공간
- 일반 구매와 subscription cycle control을 PDP에서 동시에 조작

Desktop purchase column은 sticky 가능하되 header와 자연스럽게 분리하고 전체가 하나의 card처럼 떠 보이지 않게 한다.

## 9. Cart / Checkout

### Cart

- item은 table/list row 성격으로 표현
- image / name / option / unit / quantity / subtotal / remove가 한 행 hierarchy로 읽힌다.
- item마다 거대한 card를 만들지 않는다.
- order summary만 별도 summary surface를 사용할 수 있다.

### Checkout

- main flow + summary rail
- 배송지 / 쿠폰 / 주문확인은 큰 card 3개를 쌓기보다 step heading + divider + compact form section으로 구성
- summary는 sticky 가능
- primary payment/order action만 강하게 강조

## 10. Orders / Subscription / My

### Orders

- order list는 card gallery가 아니라 commerce history list/table에 가깝게 구성
- order date/status/amount/actions가 빠르게 scan되어야 한다.

### Subscription list/detail

- dashboard tile grid 금지
- 첫 viewport: status + next delivery + estimated amount + required action
- next-delivery만 필요한 경우 accent surface 사용
- items/add-on/pending change는 list/divider로 이어진다.
- management actions는 accordion/section으로 정리하되 모두 같은 큰 card로 만들지 않는다.
- danger zone만 별도 경계 가능

### My

- account center 구조
- 첫 영역: next needed action / reorder / next delivery
- 그 아래 Orders / Subscription / Pets / Addresses / Billing / Notifications는 compact navigation/list
- 동일 크기 management card 6~8개 grid 금지

## 11. Pets / Notifications / Support / Admin

- Pets: profile row/list + edit form. DOG/CAT static avatar는 작고 보조적으로 사용한다.
- Notifications: timeline/list. notification 하나마다 큰 card 금지.
- Support: readable article layout. commerce shell 폭을 억지로 사용하지 않는다.
- Admin: 운영 도구 특성상 density 우선. 고객 storefront card language를 그대로 복사하지 않는다.

## 12. Mobile

Mobile은 desktop card를 한 열로 쌓는 방식이 아니다.

- 16px gutter
- product grid 2 columns, zoom-safe 1 column
- mobile filter는 bottom sheet/dialog 유지
- page-level outer card 제거
- PDP는 gallery → core info → option/qty → sticky primary action
- Cart/Checkout은 row/section 기반
- Subscription은 action-required / next delivery를 먼저 보여주고 나머지는 accordion
- bottom nav와 sticky purchase action이 겹치지 않는다.

## 13. Visual acceptance criteria

Desktop 1440/1920에서:
- content가 중앙 1200px 안에 답답하게 몰리지 않는다.
- Home/PLP/PDP의 주요 section이 화면 폭을 목적에 맞게 사용한다.
- PLP에서 상품이 filter보다 시각적으로 우선한다.
- 1920px viewport에서 PLP가 filter + 상품 3개만 보이는 구성은 실패다.
- PDP 첫 viewport에서 image만 과도하게 지배하지 않고 상품명·가격·구매 action을 함께 이해할 수 있다.
- Home category/brand에 outer card + inner card 중첩이 없다.
- 전체 화면에서 동일한 rounded card 문법이 반복되지 않는다.

Commerce scan:
- Product card는 3초 안에 image/name/price/availability를 읽을 수 있다.
- Cart/Order/Subscription은 status와 다음 action을 빠르게 찾을 수 있다.
- primary CTA는 decision group당 하나가 명확하다.

Responsive:
- 1440, 1280, 1024, 768, 375, 320에서 overflow 없음
- desktop navigation은 세로로 긴 category dropdown에 의존하지 않는다.
- mobile은 desktop section의 단순 적층이 아니다.

## 14. Implementation boundary

이번 correction은 `frontend/**` 중심의 layout/surface/style/component composition 변경이다.

허용:
- CSS/layout refactor
- existing component DOM hierarchy 조정
- 화면별 section composition 조정
- 이미 승인된 API 응답을 다른 시각 hierarchy로 표시
- 필요한 최소 regression test 수정

금지:
- Backend/API/DB 변경
- 새 product policy
- 새 category/domain 계약
- 새 dependency를 이용한 UI framework 도입
- fake price/review/shipping/subscription data
- Figma 재작업
- Production/AWS/RDS/Toss/AI Provider 실행

## 15. Review and deployment sequence

1. 이 correction delta 기준으로 PR #252 visual implementation을 수정한다.
2. lint / typecheck / test / build / repository validation을 수행한다.
3. CodeRabbit 수동 incremental review를 최신 HEAD 기준으로 수행하고 유효 지적만 반영한다.
4. 스크린샷 제출을 merge gate로 사용하지 않는다.
5. 기능 회귀가 없고 CI/review가 닫히면 Ready/merge 여부를 사용자와 판단한다.
6. merge 후에도 자동 배포하지 않는다.
7. 사용자가 정확한 main SHA의 Production 배포를 별도 승인하면 manual production deploy를 실행한다.
8. 배포된 public site를 사용자와 ChatGPT가 직접 함께 검토한다.
9. 배포 후 visual 문제는 measured post-deploy correction으로 한 번에 정리한다.
10. Browser 직접 검토 전 `MVP4 Product Complete`를 선언하지 않는다.
