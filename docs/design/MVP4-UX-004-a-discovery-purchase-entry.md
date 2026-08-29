# MVP4-UX-004 A. 탐색·구매 진입

상태: `Proposed Design Contract / Draft / Pending Product Owner Approval`

## 적용 범위와 읽는 법

대상은 전역 Header/Search, `/`, `/products`, Product Card, `/products/[productId]`, `/compare`, `/wishlist`다. 각 화면의 계약 표는 현재 구현, 문제, 레퍼런스, 최종 정보 구조·visual hierarchy·responsive behavior, 상세 interaction, navigation, loading/empty/error/success/retry, accessibility, gap, implementation impact, acceptance criteria를 빠짐없이 포함한다.

공통 시각 토큰과 URL·overlay·중복 제출 규칙은 [재설계 제안안](./MVP4-UX-004-customer-commerce-redesign.md), breakpoint·container·sticky 수치는 [D8 SSOT](./MVP4-UX-004-d-shared-responsive-accessibility.md#d8-반응형-기준), 실제 구성과 전이는 [correction](./MVP4-UX-004-visual-interaction-correction.md)을 따른다.

## A1. Header와 Search

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | `AppHeader`와 인증 provider가 모든 고객 라우트를 감싼다. 홈·상품 목록의 검색 및 로그인 상태가 연결되어 있으나, 검색 입력 draft와 URL 적용 시점, 모바일 메뉴의 focus 계약은 정식 문서로 고정되지 않았다. |
| 문제 | 유틸리티 링크·카테고리·검색·계정 행동의 우선순위가 화면별로 달라질 수 있다. 모바일에서 검색과 메뉴가 동시에 확장되면 상단 공간과 focus가 충돌한다. |
| 레퍼런스 | Kurly의 검색 중심 full header→56px sticky nav와 category click은 `CONFIRMED/ADAPT`; Escape로 닫히지 않은 category menu는 `REJECT`. Musinsa의 검색·마이·좋아요·cart 복귀 URL은 `CONFIRMED/ADAPT`. |
| 최종 IA | desktop primary 72px: logo→search→account/cart, nav 48px: category→`상품/정기배송/주문`→support. mobile 56px: menu→logo→cart, 다음 48px search row. 정확한 폭·sticky 전환은 D8이다. |
| visual hierarchy | 로고와 검색이 1차, 카테고리와 핵심 내비게이션이 2차, 유틸리티가 3차다. 장바구니 수량은 2자리까지 숫자, 100 이상은 `99+`로 읽기 가능한 라벨을 제공한다. |
| 컴포넌트 | `LogoLink`, `CategoryPopover`, `SearchForm`, `PrimaryNav`, `AccountMenu`, `CartLink`, `MobileMenuDrawer`, skip link. icon-only 버튼은 항상 접근 가능한 이름을 가진다. |
| 상세 interaction | 입력은 로컬 draft다. Enter와 검색 버튼은 trim한 값으로 `/products?q=`를 push한다. 공백은 `/products`로 이동한다. clear는 입력만 비우고, clear 후 제출해야 URL이 바뀐다. 검색 중 300ms 자동 요청이나 개인 검색 기록 저장은 하지 않는다. |
| hover/focus/navigation | 링크 hover는 `brand` 색+underline, 버튼 hover는 `surface-soft`를 사용한다. `Tab`: skip link→logo→search→account→cart→category→primary nav 순서다. `/login?returnTo=`에는 현재 안전한 GET 경로만 전달한다. |
| loading | 인증 확인 중 계정 영역은 같은 폭의 skeleton과 `계정 확인 중` 접근성 텍스트를 사용한다. 전체 헤더를 숨기지 않는다. |
| empty | 검색어가 비어 있으면 placeholder `상품명, 브랜드, 카테고리 검색`; 최근 검색 목록은 표시하지 않는다. |
| error/retry | 인증 상태 실패는 계정 영역 inline 상태로 표시하고 `다시 확인`을 제공한다. 검색 이동은 클라이언트 라우팅 실패 시 기본 form GET으로 복구한다. |
| success | 검색 제출 후 `/products` 결과 heading(`tabindex=-1`)에 focus하고 결과 수를 한 번 announce한다. 로그인 성공 후 원래 안전한 GET 위치로 복귀한다. |
| responsive | D8 header SSOT를 사용한다. category/menu는 left drawer `min(360px,100vw)`, search row는 1023px 이하에서 48px 전체 폭이고 compact sticky에는 포함하지 않는다. |
| accessibility | search landmark 1개, `type=search`, 고정된 visually-hidden label `상품 검색`, drawer focus trap·Escape·trigger 복귀, 현재 경로 `aria-current=page`. |
| gap·impact | 헤더 구성과 CSS, search submit/clear, menu 상태, 로그인 복귀 표현을 조정해야 한다. API 변경은 없다. |
| acceptance | 키보드로 검색 제출·clear·메뉴 닫기가 가능하고, 뒤로가기로 직전 URL과 입력 표시가 복원되며, 비로그인 복귀가 POST를 재실행하지 않는다. |

## A2. Home `/`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 공개 발견 API, 추천, 로그인 상태, 펫 프로필과 상품 카드를 조합한다. 기존 감사에서 유사한 상품 rail 반복과 개인화 오류의 과노출이 지적됐다. |
| 문제 | 모든 섹션이 같은 카드 모양이면 시작점이 불명확하고, 비로그인 또는 추천 실패가 페이지 전체 실패처럼 보인다. 배너가 크면 실제 상품 탐색을 밀어낸다. |
| 레퍼런스 | PetFriends의 펫 유형→사료/간식/용품→상품 rail과 Kurly의 hero→상품 rail은 `CONFIRMED/ADAPT`. 자동 재생 중심 hero와 반복 혜택 rail은 `REJECT`. |
| 최종 IA | `핵심 가치+쇼핑 시작` compact hero → 반려동물 유형 quick category → `필요한 시점에 다시` 생활주기 설명 → 대표 상품 rail 1개 → 로그인/펫 상태에 따른 추천 → 신뢰 strip(배송·반품·지원). PO 결정 전 `인기`와 `트렌딩`을 동시에 노출하지 않는다. |
| visual hierarchy | hero는 최대 320px, copy 7열·보조 시각 5열. 상품 rail은 1440px에서 4.5개가 보이며 제목/근거/전체보기 순서다. 배경색 alternation은 3종이다. |
| 컴포넌트 | `CompactHero`, `PetTypeTiles`, `RoutineValueStrip`, `ProductRail`, `RecommendationGate`, `TrustStrip`. 모든 rail은 landmark 제목과 이전/다음 버튼을 갖는다. |
| interaction | hero CTA는 `/products`; 펫 유형은 `/products?petType=`; rail 버튼은 한 카드 폭 단위 이동한다. drag만 요구하지 않는다. anonymous 추천 CTA는 `/login?returnTo=/pets`, authenticated CTA는 `/pets`로 이동한다. |
| hover/focus/navigation | 카드 hover가 이미지를 확대하지 않고 border/제목 underline만 바꾼다. rail focus가 화면 밖이면 자연스럽게 scrollIntoView한다. |
| loading | hero/카테고리는 즉시, 각 data section은 독립 skeleton 1행. 전체 페이지 spinner를 쓰지 않는다. layout shift 방지를 위해 이미지 비율을 예약한다. |
| empty | 추천 없음은 `아직 맞춤 추천을 만들 정보가 부족해요`와 `/pets` CTA를 표시한다. 공개 발견 rail이 비면 해당 section만 숨기고 trust strip은 유지한다. |
| error/retry | 공개 추천 실패는 fallback 상품 rail과 작은 inline 안내를 사용한다. 전체 홈 오류로 승격하지 않는다. retry는 실패 섹션 단위다. |
| success | 로그인+펫이 있으면 근거 문구 `반려견 프로필을 바탕으로`처럼 실제 입력만 설명한다. AI가 만든 허위 근거를 표시하지 않는다. |
| responsive | D8 SSOT를 사용한다. 1024px 이상 hero 7/5, 1023px 이하 단일 열 copy→visual; 상품 rail은 1440px 4.5개, 1200–1439 3.5개, 768–1199 2.5개, 360–767 2.1개, 320–359 1.1개다. |
| accessibility | carousel은 자동 재생하지 않거나 명시적 pause를 제공한다. 섹션 제목 구조 `h1` 하나, `h2` 순차. 추천 근거는 이미지 alt가 아닌 본문으로 제공한다. |
| gap·impact | 홈 section 순서와 중복 제거, 독립 async boundary, 추천 fallback, rail 제어를 조정한다. API 변경은 없다. |
| acceptance | 개인화 실패에도 category와 공개 상품이 보이고, 어느 breakpoint에서도 hero가 첫 상품을 두 화면 이상 아래로 밀지 않으며, rail을 키보드로 조작할 수 있다. |

## A3. PLP/Search `/products`

### 페이지 구조

```text
Breadcrumb
H1 + 검색어/카테고리 설명
선택 필터 chip + 결과 수 + 정렬
┌ Filter rail ┐ ┌ Product results 5/4/3/2/1 columns ┐
└─────────────┘ └ Pagination + result announcement ─┘
```

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | `q`, petType, category/subcategory, brand, facet, min/max price, sort, page를 URL에서 읽고 상품 카드·필터·비교 이벤트를 사용한다. stale request guard와 history 복원 테스트 기반이 있다. |
| 문제 | 필터 적용 시점과 모바일 draft, 결과 갱신 중 상태, zero result recovery, 5열 밀도와 200% 확대 규칙이 하나의 계약으로 묶이지 않았다. |
| 레퍼런스 | Kurly Pet PLP의 249px 3열 card와 filter rail, Musinsa의 filter→URL/result 갱신, IKEA의 mobile filter chips·비교 tray는 `CONFIRMED/ADAPT`. Musinsa 6열과 20px action, PawCycle 1440px 5열은 `REJECT`. |
| 최종 IA | breadcrumb→제목/설명→적용 filter chip→결과 toolbar→filter rail+grid→pagination. 검색 결과 제목은 `“사료” 검색 결과`, category는 category label을 쓴다. |
| visual hierarchy | 상품 이미지/이름/가격이 1차, 할인·구매 가능·리뷰가 2차, 비교·위시·quick add가 3차다. 필터 rail은 결과보다 어둡거나 큰 card가 되지 않는다. |
| 컴포넌트 | `ResultHeader`, `AppliedFilterChips`, `FilterRail`, `FilterDrawer`, `SortSelect`, `ProductGrid`, `Pagination`, `ResultsStatus`, Product Card. |
| detailed interaction | desktop filter는 checkbox/select 확정 시 URL push; 연속 가격 입력은 `적용`에서 push. mobile은 drawer local draft 후 `N개 결과 보기`로 한 번 적용한다. sort/reset/page도 push. chip 제거는 해당 파라미터만 제거하고 page=1. |
| hover/focus/navigation | 필터 label 전체 클릭 가능. drawer trigger에 활성 필터 수 표시. pagination은 `<a>`로 직접 접근 가능. PDP에서 back 시 이전 상품이 focus-visible 상태로 복귀한다. |
| loading | 최초는 8–10개 card skeleton. 후속 갱신은 기존 결과를 유지하고 결과 영역에 `aria-busy=true`, toolbar progress를 표시한다. stale 응답은 폐기한다. |
| empty | `조건에 맞는 상품이 없어요`; 적용 필터 요약, `필터 초기화`, 검색어 유지한 category 완화 후보를 제공한다. 존재하지 않는 상품을 추천하지 않는다. |
| error/retry | 마지막 성공 결과가 있으면 유지+inline retry. 최초 실패는 결과 영역 error panel. 401이 필요 없는 공개 목록을 로그인으로 보내지 않는다. |
| success | `총 N개`를 polite live region으로 알리고 grid heading에 focus를 강제하지 않는다. 사용자가 apply를 누른 경우 결과 제목으로 scroll한다. |
| responsive | D8 SSOT: 1440px 이상 232px rail+4열, 1200–1439 216px rail+3열, 1024–1199 drawer+3열, 768–1023 2열, 600–767 2열, 360–599 2열, 320–359 1열 행 card다. 1023px 이하 filter/sort 2분할 toolbar는 compact header 아래 sticky다. |
| accessibility | checkbox fieldset/legend, 결과 수 live region, sort visible label, chip 제거 버튼 `브랜드 X 필터 제거`, skeleton은 반복 낭독하지 않는다. |
| gap·impact | 목록 CSS와 filter state commit 시점, back scroll/focus, stale 표시를 정렬한다. API 파라미터·응답 변경은 없다. |
| acceptance | URL 복사 시 같은 결과가 열리고, back/forward가 검색·필터·정렬·page·scroll을 복원하며, mobile drawer cancel은 URL을 바꾸지 않는다. |

## A4. Product Card

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 홈·PLP·추천에서 재사용되고 비교·위시·상호작용 기록과 연결된다. 기존 감사에서 가격/배지/이미지 의미와 quick action 일관성 문제가 확인됐다. |
| 문제 | 카드 전체 링크 안에 버튼을 중첩하면 키보드·스크린리더와 click target이 충돌한다. 많은 배지와 긴 이름은 가격·CTA를 밀어낸다. |
| 레퍼런스 | Kurly·PetFriends·IKEA의 제목·현재가·원가·할인·배송·리뷰와 card-level action은 `CONFIRMED/ADAPT`; 의미가 중복된 다수 배지와 44px 미만 action은 `REJECT`. |
| 최종 IA | 이미지→최대 2개 badge→브랜드(선택)→상품명 링크→보조 설명 1줄→가격 묶음→평점/리뷰→행동. 카드 자체는 링크가 아니며 이미지와 제목만 동일 PDP로 간다. |
| visual hierarchy | 현재가가 가장 강하고 할인율은 accent, 원가는 취소선+muted. `품절`은 이미지 overlay와 텍스트 모두 표시한다. 2줄 이후 이름은 clamp하되 전체 이름을 접근성 이름으로 보존한다. |
| 컴포넌트 | `ProductImageLink`, `BadgeStack`, `PriceBlock`, `RatingSummary`, `WishlistButton`, `CompareCheckbox`, `QuickAddButton`. |
| interaction | quick add는 옵션 없이 단일 purchasable 상품에만 노출한다. 그 외는 `옵션 보기`로 PDP 이동. wishlist/compare 결과는 inline 상태+toast, 반복 클릭 중 disable. 최대 3개 비교에서 4번째는 이유와 교체 행동을 안내한다. |
| hover/focus/navigation | 이미지 zoom 금지, 링크 underline/outline. tab 순서는 제목 링크→위시→비교→quick add. hover-only action은 금지한다. |
| loading | 카드 비율을 고정한 skeleton. 이미지 lazy load 실패는 브랜드/상품명 기반 중립 placeholder와 alt 정책을 사용한다. |
| empty | 카드 단독 empty는 없다. 목록/rail이 empty를 소유한다. 가격이 없거나 구매 불가는 `현재 구매할 수 없음`으로 표현하고 임의 가격을 만들지 않는다. |
| error/retry | 위시/담기 실패는 해당 버튼 옆 inline message와 retry. 전체 카드가 사라지지 않는다. |
| success | 담기 후 `장바구니에 담았어요`와 `/cart` action; 위시 버튼은 pressed 상태와 `위시리스트에서 제거` 이름으로 바뀐다. |
| responsive | D7 Product Card와 D8 SSOT를 사용한다. 4열은 quick add 아래 compare, 3/2열은 compare를 footer 보조행, 320–359는 이미지 112px+정보의 1열 행 card로 고정한다. 모든 action은 44px이다. |
| accessibility | 중첩 interactive 금지, wishlist `aria-pressed`, 비교 native checkbox, 가격 읽기 순서 현재가→원가→할인, 이미지 alt는 상품명 반복 시 빈 alt를 검토한다. |
| gap·impact | 공통 card DOM과 action 조건을 통일해야 한다. 옵션 존재 여부를 현재 응답에서 확정할 수 없다면 quick add를 숨긴다. |
| acceptance | 카드 내 모든 행동이 독립 focus target이고, 품절/구매불가/로그인 필요/비교 최대 상태가 색 외 텍스트로 구분된다. |

## A5. PDP `/products/[productId]`

### 데스크톱 와이어프레임

```text
Breadcrumb
┌ Gallery 7 cols ───────────┐ ┌ Purchase summary 5 cols (sticky) ┐
│ thumbnail + main media    │ │ title / price / stock / CTA       │
└───────────────────────────┘ └────────────────────────────────────┘
Anchor nav: 상품정보 | 리뷰 | Q&A | 배송·반품
Plain-text product sections
Review summary/list
Q&A list
Related / complementary products
```

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | plain-text 상품 섹션, 리뷰·Q&A·평균, 연관/보완 추천과 구매 가능 상태를 사용한다. 0개 리뷰는 average `null`, count `0`이 권위다. |
| 문제 | 긴 상세가 구매 CTA와 현재 위치를 분리하고, 리뷰·Q&A loading/error가 전체 PDP를 막을 수 있다. 구독 레퍼런스를 따라 PDP에 지원되지 않는 주기 선택을 추가할 위험이 있다. |
| 레퍼런스 | Kurly의 gallery+sticky 구매 정보+하단 anchor는 `CONFIRMED/ADAPT`. 옵션 선택 전 cart disabled 동작은 확인했으나 PawCycle 옵션 API가 보장되지 않아 조건부로만 적용한다. PetSmart의 PDP Autoship 선택은 `INDIRECT/UNSUPPORTED` for MVP4. |
| 최종 IA | breadcrumb→gallery/purchase summary→anchor nav→상품 정보→review→Q&A→관련/보완 추천→배송·반품 신뢰. Recommended Default에서 PDP에는 신규 정기배송 CTA를 두지 않고 주문 상세에서만 `/subscriptions/new`를 안내한다. |
| visual hierarchy | 제목/현재가/구매 가능/수량/주 CTA가 우측 첫 화면에 함께 보인다. 보조 메타와 정책 링크는 divider 아래. 상세는 continuous canvas이며 섹션마다 큰 card를 만들지 않는다. |
| 컴포넌트 | `ProductGallery`, `PurchaseSummary`, `QuantityStepper`, `PrimaryPurchaseAction`, `WishlistButton`, `AnchorNav`, `ProductFacts`, `ReviewSummary/List`, `QuestionList`, `RecommendationRail`. |
| detailed interaction | thumbnail 선택은 main image와 selected 상태 변경. 수량 stepper는 min/max/재고를 서버 응답 범위에서 제한. 구매불가이면 CTA disabled+이유. anchor는 heading으로 scroll하고 URL hash를 갱신한다. |
| hover/focus/navigation | 확대 기능이 있으면 click/Enter로 명시적 dialog를 열고 hover zoom만 쓰지 않는다. sticky summary는 header와 겹치지 않는다. related card→PDP 이동은 새 상품 상단으로 이동한다. |
| loading | 핵심 상품 skeleton 우선; 리뷰·Q&A·추천은 독립 lazy section. anchor는 해당 section 준비 후 활성화한다. |
| empty | 리뷰 0건은 `첫 리뷰를 기다리고 있어요`, 평균 별점 없음. Q&A 0건은 문의 방법. 관련 상품이 없으면 section 숨김. |
| error/retry | 상품 404는 not-found+목록 CTA. 핵심 실패는 page-level retry. 리뷰/Q&A/추천 실패는 해당 section retry. 장바구니 충돌은 서버 최신 구매 가능 상태를 다시 표시한다. |
| success | 장바구니 담기 후 수량·상품명 확인 toast와 장바구니 링크. 리뷰/Q&A 제출 기능이 실제 범위에 없다면 CTA를 노출하지 않는다. |
| responsive | D8 SSOT: 1024px 이상 7/5+summary sticky, 1023px 이하 단일 열, 767px 이하 64px `가격+담기` action이다. keyboard가 열리면 action을 static으로 바꾸고 safe area를 포함한다. |
| accessibility | gallery 버튼 이름 `N번째 이미지 보기`, selected 상태, 이미지 대체텍스트; quantity에 label과 변경 announcement; anchor `aria-current`; 별점은 `5점 만점에 X점, N개 리뷰`. |
| gap·impact | PDP layout, 독립 async boundaries, mobile sticky bar, hash/focus를 조정한다. 새 구독/옵션 API는 요구하지 않는다. |
| acceptance | 첫 화면에서 상품·가격·구매 가능·CTA를 이해하고, 0리뷰를 0점으로 오해하지 않으며, 긴 상세에서도 keyboard로 section 이동과 구매가 가능하다. |

## A6. Compare `/compare`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | `ComparisonScreen`이 2–3개 상품의 final-product API를 사용한다. PLP card에서 비교 후보를 추가한다. |
| 문제 | 2개 미만 상태, raw enum/빈 속성, 모바일 wide table, 최대 개수 오류가 명확히 정의되지 않았다. |
| 레퍼런스 | IKEA PLP의 checkbox·`4/5개 선택됨` tray·compare URL은 `CONFIRMED/ADAPT`; PawCycle 한도는 승인 계약대로 3개다. 외부 실제 비교표의 mobile reflow는 `UNVERIFIED`. |
| 최종 IA | heading+설명→선택 상품 strip→차이만 보기 control→속성 그룹별 비교표→각 상품 CTA. 2개 미만이면 선택 도우미를 먼저 보인다. |
| visual hierarchy | 상품명·가격·구매 가능을 sticky header, 차이가 있는 행을 subtle surface, 동일/unknown은 낮은 대비로 표시한다. raw enum은 사용자 용어로 매핑한다. |
| 컴포넌트 | `CompareSelection`, `DifferenceToggle`, semantic `table`, `ProductColumnHeader`, `UnknownValue`, `RemoveButton`. |
| interaction | 최대 3개. 제거 후 1개가 되면 표를 숨기고 추가 선택 CTA를 보인다. `차이만 보기`는 URL boolean parameter로 유지하고 상품 ID 외 개인 데이터는 저장하지 않는다. |
| navigation | `상품 더 찾기`는 현재 PLP URL로 복귀. 상품 제목은 PDP. back은 이전 compare selection을 복원한다. |
| loading | 표의 row/column skeleton; 선택 strip은 유지. |
| empty | 0개: PLP CTA. 1개: `비교할 상품을 하나 더 선택하세요`와 최근 검색이 아닌 category/목록 링크. |
| error/retry | 일부 상품 실패면 성공 column 유지, 실패 column retry/remove. 전체 실패는 inline retry. |
| success | 2–3개일 때 비교표, 구매 가능 상품만 담기. 제거 성공은 focus를 다음 상품 또는 추가 CTA로 이동. |
| responsive | D8 SSOT: 1024px 이상 semantic table, 1023px 이하 상품 tabs+속성별 2열 비교, 세 번째 상품은 tab 전환이다. 가로 drag만 요구하지 않고 200% 확대에서도 속성 label을 유지한다. |
| accessibility | table caption, row header `<th scope=row>`, column header, unknown은 `정보 없음`, toggle accessible name, 제거 후 live announcement. |
| gap·impact | raw value formatter, partial error, mobile presentation과 focus management 보강. API 변경 없음. |
| acceptance | 0/1/2/3/초과 선택 상태가 모두 설명되고, 모바일에서 가로 스크롤 없이 두 상품의 동일 속성을 비교할 수 있다. |

## A7. Wishlist `/wishlist`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 인증된 commerce API 목록과 삭제·장바구니 행동을 사용한다. 비로그인 진입은 로그인 복귀 규칙을 따른다. |
| 문제 | 품절/구매불가 상품, 일부 action 실패, 비로그인 클릭 후 복귀가 별도 설계되지 않았다. |
| 레퍼런스 | Musinsa의 anonymous wishlist 설명+로그인 CTA와 IKEA card의 save action은 `CONFIRMED/ADAPT`; 로그인 후 실제 목록 관리 UI는 `UNVERIFIED`. |
| 최종 IA | heading+개수→상품 grid/list→계속 쇼핑. 대량 선택/삭제는 현재 API가 명확하지 않으므로 제공하지 않는다. |
| visual hierarchy | 상품 카드와 동일하되 `위시에서 제거`가 명시적 보조 행동. 구매불가는 가격보다 상태를 우선한다. |
| 컴포넌트 | `WishlistGrid`, Product Card variant, `RemoveButton`, `ContinueShopping`. |
| interaction | 제거는 즉시 삭제 대신 되돌리기 가능한 toast를 사용할 수 있을 때만 optimistic; 복구 API가 없으면 확인 없이 서버 성공 후 제거한다. 담기는 중복 제출 방지. |
| navigation | anonymous는 `/login?returnTo=/wishlist`; 로그인 후 GET 복귀. 카드→PDP→back 시 목록 위치 복원. |
| loading | 인증 확인 후 grid skeleton. 인증 확인 전 empty로 깜빡이지 않는다. |
| empty | `저장한 상품이 없어요`+`상품 둘러보기`; 추천 실패와 섞지 않는다. |
| error/retry | 목록 실패 page section retry; 단일 제거/담기 실패는 item inline retry. 401은 재인증 CTA. |
| success | 제거·담기 결과를 상품명 포함 live region으로 알린다. 품절 상품은 남겨 두고 상태를 표시한다. |
| responsive | Product Card와 D8 SSOT를 그대로 사용한다. 1440px 이상 4열, 1200–1439 3열, 1024–1199 3열, 768–1023 2열, 600–767 2열, 360–599 2열, 320–359 1열 행 card다. mobile action은 항상 보인다. |
| accessibility | 위시 제거는 `상품명 위시리스트에서 제거`, grid heading/개수, toast는 `status`, 오류는 `alert`. |
| gap·impact | anonymous gate, item별 pending/error, back focus를 통일한다. API 변경 없음. |
| acceptance | 인증·empty·품절·일부 실패를 구분하고, 제거 실패가 다른 상품 상태를 없애지 않으며, 로그인 후 안전하게 목록으로 복귀한다. |

## A 번들 구현 순서와 검증

1. Header/Search URL 계약과 공통 Product Card를 먼저 고정한다.
2. PLP history·filter·responsive를 적용한 뒤 Home rail과 Wishlist가 같은 card 계약을 소비하게 한다.
3. PDP independent state와 sticky CTA를 적용하고 Compare mobile 표현을 마무리한다.
4. route 단위 keyboard journey, 320/375/768/1024/1440, 200% zoom, reduced motion을 검증한다.

외부 벤치마크 관찰일: 2026-08-29. 확인 URL: `https://www.kurly.com/`, `https://www.kurly.com/categories/991`, `https://www.kurly.com/goods/1001181670`, `https://www.kurly.com/cart`, `https://www.oliveyoung.co.kr/store/main/main.do`.
