# Interaction / Responsive Contract — 제안

상태 PROPOSED. 아래는 **새 설계의 기대 동작**이며 Production 검증 결과가 아니다. 실제 관찰 범위는 [감사](production-audit.md). A안 상세 계약, B/C 선택 시 화면 구성의 재승인 필요. 서버 계약을 바꾸지 않는다.

## 1. 탐색·검색·필터

### Category / Mobile navigation

- Desktop 전체 category trigger click/Enter/Space로 640w panel open; hover는 배경 피드백만. 로딩이면 3행 skeleton, empty면 `카테고리를 준비하고 있어요`·`전체 상품` link. error면 retry. 실제 discovery의 1·2-depth만 출력한다.
- open 시 첫 category heading(또는 close)에 focus. trigger `aria-expanded=true`, `aria-controls`. Desktop panel은 `dialog`와 내부 `nav`로, 앱 menu/menuitem 역할을 링크에 강제하지 않는다. modal인 mobile은 배경 inert·body scroll lock; desktop non-modal은 trap하지 않고 Tab이 panel 밖으로 나가면 닫는다.
- Escape·닫기·outside click은 close; Escape/닫기는 trigger focus return. outside pointer는 클릭한 정상 대상을 그대로 존중하며 focus를 다시 훔치지 않는다. category link는 해당 GET으로 이동, close 후 새 h1 focus. 하위 category는 parent 선택 후 펼침, hover 필수 금지.
- mobile header 메뉴는 full-height drawer(너비 min(360,viewport), desktop panel과 달리 modal). 순서: 닫기 → 전체 상품/DOG/CAT → 실제 category accordion → 내 정보/찜/주문/정기배송 → 고객지원. desktop 계정 경로 누락 금지. 뒤로가기와 상호 충돌하지 않도록 overlay open은 URL에 넣지 않는다.

### Search

- Header와 PLP 본문의 중복 search를 없앤다. 320/375에서는 header 아래 한 줄의 search만. 입력값 draft와 URL의 committed `q`를 구분한다.
- Enter/검색 버튼에서 trim된 q 적용. 새 검색은 기존 category/brand/facet/price/petType/조건을 모두 해제하고 `sort=RECOMMENDED,page=0,size=12`로 넓게 탐색한다. 빈 검색 제출은 전체 상품. 입력 중에는 query 요청 없음, 자동완성·최근검색 저장·인기검색 기능 없음.
- 브라우저 back/forward는 URL 값으로 input·chip·sort·page 동기화. clear-x는 draft만 비우고 focus 유지, 제출 전 결과 안 바뀜. 실패해도 검색어 보존. 검색 input과 submit 각각 keyboard accessible.
- 결과 업데이트는 `검색 결과 N개` polite 1회. search 제출 시 결과 제목으로 focus, filter click에서는 원래 control focus 유지. 이전 느린 응답이 최근 URL 결과를 덮지 않아야 한다.

### Filter 상태 모델

`Committed(URL)` → open → `Draft(copy)` → apply(검증) → `Committed(new URL, page=0)` → loading → success/empty/error. close/cancel/Escape/outside는 draft 폐기, URL 불변. popover는 동시에 하나만 열며 다른 trigger를 누르면 기존 미적용 draft 폐기 후 새 panel open. 변경값은 panel 안에 `적용 전`으로 표시한다.

| control | 값과 UI | reset / apply |
| --- | --- | --- |
| pet type | radio tiles 전체(없음)/강아지(DOG)/고양이(CAT), single | draft 변경 후 apply. profile 변경 아님 |
| category | 실제 discovery parent single, child single. child는 parent 선택 시 표시 | parent 변경하면 subcategory와 facet 해제, child 변경하면 facet 해제. brand/petType은 보존 |
| brand | radio rows single, 이름 검색은 현재 discovery 내 local filter | 여러 brand query로 확장하지 않음 |
| price | minPrice/maxPrice numeric pair | 음수·비정상 숫자·역전 범위이면 inline error, apply 차단. 값 생략은 제한 없음 |
| purchasable | `구매 가능한 상품만` checkbox | checked→true, unchecked→parameter 없음. 기존 URL false는 `구매 불가 상품` 읽기 chip으로 표시하고 지울 수 있음 |
| subscription | `정기배송 대상만` checkbox | checked→subscribable=true, unchecked→없음. false URL은 `정기배송 제외` chip으로 해석. 현재 UI에 false를 새 토글로 추가하지 않음 |
| facets | 해당 category의 실제 options checkbox | 반복 `facet=key:value`; 없는 자유 텍스트 facet 만들지 않음. 중복 제거. category 전환 시 stale 값 삭제 |

- desktop reset은 열린 panel의 draft 그룹만 초기화; `적용` 전 결과 불변. mobile drawer reset은 전체 filter draft 초기화, q/sort 유지, apply 뒤 확정. footer 고정 `초기화`/`적용하기`. 미리 결과 개수를 계산할 API가 없으므로 `123개 상품 보기` 같은 예상 숫자 금지.
- chip은 committed 값만. clear one은 즉시 URL patch+page0, category chip 삭제는 child/facet도 삭제. clear all은 filter 전체만 제거하고 q/sort/size 보존. **검색까지 초기화**는 별도 명시 action으로 q도 제거한다. pending 중 중복 변경은 최근 의도를 우선, focus 이탈·stale 응답 금지.
- 적용 후 panel 닫기, trigger focus return. 결과 영역 loading 표시, sort/filters는 읽기 가능. 네트워크 오류는 해당 URL의 오류이며 이전 상품을 새 결과인 것처럼 표시하지 않는다. last good list를 유지할 경우 `이전 결과` 표시와 stale 상태를 명시해야 하므로 기본안은 skeleton→error다.
- URL의 알 수 없는 filter값은 임의 다른 값으로 매핑하지 않는다. 해석할 수 없다는 안내와 reset action 제공. SSR/client가 같은 기본값 사용.

### Sort / Pagination / Compare

- sort UI styled native single-select (custom 구현 강제 없음), 추천순(RECOMMENDED), 최신순(NEWEST), 낮은 가격순(PRICE_ASC), 높은 가격순(PRICE_DESC), 평점순(RATING), 리뷰 많은 순(REVIEW_COUNT). 선택 즉시 적용·page0, 다른 조건 보존. 일반 커머스의 판매량순을 임의 추가하지 않음.
- native select: label·value·disabled·focus ring을 보존하며 OS 기본 keyboard/option popup을 따른다. custom select를 실제 채택하는 경우에만 trigger Enter/Space/ArrowDown open, Up/Down 이동, Home/End, Enter 확정, Escape 취소+return, Tab close, aria-selected와 disabled skip을 구현한다. native 동작을 custom keyboard로 덮어쓰지 않는다.
- size12 고정, numbered pagination44 targets. 320에서는 이전/현재/다음만, 현재 `2 / 5` 설명. page 전환 시 grid 제목 focus, Back은 URL·scroll 복원. infinite scroll 자동 추가 없음.
- 기존 2~3개 상품 비교 기능 보존. 비교 checkbox 선택으로 client selection만 변경, 2개부터 `상품 비교`, 3개 초과는 안내하고 기존 선택 유지. fixed tray는 bottom bar 공간을 차지하며 mobile에서는 toolbar 아래 `비교 2/3` 요약으로, PDP 구매 bar와 중복하지 않음. 비교 API canonical facts 우선, AI unavailable은 정상 fallback.

## 2. 구매·찜·인증

| Action | 기대 동작 / 보존 계약 |
| --- | --- |
| Wishlist | checked 여부 loading/error/ready 분리. 로그인 필요하면 안전한 product GET returnTo로 이동, 성공 로그인 후 **자동 찜 실행 없음**. 기존 상태 확인 뒤 명시 click→요청 중 잠금→성공 toggle+announcement. 실패는 기존 상태 유지+retry. 찜 실패가 cart action 차단하지 않음 |
| PDP option | optionGroups 실제 조합만. 선택할 수 없는 조합은 disabled+설명, radio/button selected 표시. 선택 완결 전 가격은 `옵션을 선택해 주세요`; 선택된 SKU price/purchasable/availableQuantity가 권위. 임의 첫 SKU 조합·최저가 선택 금지 |
| PDP quantity / cart | 정수 및 서버 재고 범위 검증→명시 담기→CSRF 처리·busy→성공 후 GET cart→count+toast. 실패하면 옵션/수량 유지, code별 안내. submit 중 같은 action 중복 금지 |
| Cart quantity | stepper/직접 입력은 draft. 행 `수량 적용`을 누를 때 PATCH, 서버 GET의 quantity/lineAmount/pricing/version으로 확정. 실패하면 draft+서버 확정 금액 구분. 적용 전 결제로 넘기지 않고 적용/되돌리기 안내 |
| Cart delete | 행 삭제→상품명 있는 confirm dialog→취소 default/삭제 destructive. 성공 서버 재조회. `실행 취소`로 자동 재담기 API를 호출하지 않음 |
| Cart count feedback | confirmed cart items의 quantity 합, unknown/loading/anonymous는 0으로 위장하지 않음. `장바구니에 담았어요. 현재 N개` live. animation bounce 없음 |
| Checkout | 전체 Cart 주문만. address/coupon/cartVersion/멱등키 기존 계약 유지. API 확정 전 예상과 확정 가격 분리. 주문 준비 성공≠결제 성공, `결제수단 선택` 단계에서 Toss 화면, UNKNOWN이면 재결제 유도 금지 |
| Login returnTo | PS-003·sanitizeReturnTo 허용 내부 GET path 그대로. query 포함 복귀/외부 URL/임의 path는 현재 sanitizer에서 허용 안 함; fallback `/products`. 로그인 후 form·선택 상태 자동복원 약속 금지 |
| Login submit | 비어 있는 입력은 field error+summary, 비밀번호 붙여넣기/자동완성 허용. show/hide는 UI 상태만. invalid credentials 공통 문구. CSRF 재확인 실패시 명시 재시도, 성공 시 replace(returnTo). mutation 자동 replay 금지 |
| Login pending | form busy 표시, 중복 submit 금지, password를 toast/log/URL에 출력하지 않음. 이미 로그인한 사람은 `계속하기`+`상품 보기`, 강제 logout 없음 |

Cart에 선택주문·판매자별 배송비·프로모션 코드·직접 구독할인 toggle·비회원 cart를 새로 만들지 않는다. 이런 기능이 필요해 보이면 별도 Product Proposal이다. 상세에서는 `정기배송 대상` 사실을 보여주되 SKU별 할인 Autoship을 실행 가능한 버튼처럼 설계하지 않는다.

## 3. Overlay / keyboard / sticky

- modal open: trigger 저장→dialog title 또는 첫 안전 focus→배경 inert→Tab/ShiftTab 순환. close→trigger 반환(사라졌다면 다음 논리적 heading). filter 적용·category link 이동 후에는 새 문맥의 focus 사용. `aria-modal=true`와 dialog label 필요.
- Escape 최상단 overlay 하나만 닫음. 오류가 있어도 탈출 가능. 확인 dialog outside click은 취소, 구매 요청 진행 중에는 중복 요청 차단하되 진행 상태를 영구 가두지 않음.
- 모든 keyboard tab 순서는 시각 순서와 일치. grid arrow key를 기본 링크 navigation에 강요하지 않음. skip link, heading h1→h2→h3, form label, 상태 live 1개, focus가 sticky/bar 아래 숨지 않도록 scroll-margin-top 사용.
- A Header: R1 desktop88h, mobile136h(64 masthead+검색48 및 여백24), 스크롤에 따라 크기/위치 변경 없음. 로그인·구매 전용 desktop88h/mobile64h, 구매 화면 mobile는64h compact header. 읽는 도중 header 자동 숨김 없음.
- PDP mobile action bar: 원래 담기 action이 viewport 밖일 때만, 선택 미완료면 `옵션 선택` scroll/focus action. 조건 충족이면 `장바구니에 담기`. 원래 action이 보이면 fixed bar 숨김, DOM에서 숨긴 duplicate focus 제거. 품절/실패/loading은 동일 label/disabled/안내 반영.
- Cart mobile bar: body summary 아래가 아닌 화면 하단에 고정 합계/전체 주문. 본문에 bar실측높이+safe-area+16 padding. 다른 bottom nav 없음. 화면 높이480 미만 또는 키보드로 form 편집 중에는 fixed bar를 해제하고 본문 flow로 배치해 입력과 focus를 가리지 않음.
- browser zoom 200%, 320 width reflow, reduced-motion, 고대비/forced-colors를 검증 대상으로 둔다. overlay 내 focus trap/확대는 이번 정적 시안만으로 통과했다고 주장하지 않는다.

## 4. Breakpoint 계약

구간: S=320–767 / M=768–1023 / L=1024–1439 / XL=1440 이상. `max-width1280`. viewport<320 지원 정책은 별도 결정. 320의 핵심 label/가격/CTA 잘림 금지. desktop 축소 대신 정보 순서를 재배치한다.

| 요소 | 320 | 375 | 768 | 1024 | 1440 |
| --- | --- | --- | --- | --- | --- |
| 본문 usable width | 288 | 343 | 720 | 960 | 1280 |
| Header | 64+72, 메뉴/wordmark/cart, search 다음 줄 | 동일 | 64+48 compact, 계정 추가 | 한 줄88, search min240 | 한 줄88, search max640 |
| Home 위계 | h1→종 links→category2열→product2열, 큰 Hero 없음 | 동일 | category6열→상품3열 | category strip→상품3열 | category strip→상품4열 |
| PLP cards/gap | 2열138 / gap12 | 2열165.5 / gap12 | 3열229.3 / gap16 | 3열302.7 / gap26 | 4열302 / gap24 |
| filter/sort | 2 controls 44h(필터 flex1+정렬140), chips wrap | 정렬152 | 필터+종+정렬 toolbar, 상세 drawer | full toolbar popovers | full toolbar popovers |
| category nav | full-height drawer, parent accordion | 동일 | 360w drawer | 640w anchored panel | 640w anchored panel |
| PDP | name/price→image288→options→details, sticky | image343 | image328 + 구매368, details 전폭; 담기는 flow | thumb56+gallery440+buy416/gaps24 | thumb64+gallery672+buy448/gaps48 |
| Cart | row text/quantity2줄, summary 본문, fixed action | 동일 | rows+본문 summary; fixed 없음 | main608+gap32+summary320 | main864+gap48+summary368 |
| Login | 전용64h header, form288, 목적문구1줄+form | 전용64h header, form343 | 전용64h header, centered form400, 큰 branding 없음 | 전용88h header, 안내384+gap80+form400 | 전용88h header, 안내496+gap96+form400, max992 |
| Footer | support + 3 accordion | 동일 | 3열, account link 유지 | 3열+brand | 3열+brand |
| section gap | 32 | 32 | 40 | 48 | 56 |

PDP 1440의 column 합계 `64+48+672+48+448=1280`; 1024 `56+24+440+24+416=960`. tablet 구매 column은 옵션 전체 노출에 공간 부족하면 gallery 아래로 내려 1열로 fallback(콘텐츠 최소너비328). 긴 옵션/200% 확대에서는 고정 높이로 자르지 않는다.

375에서 selected chip은 두 줄로, 숨긴 `+N`에만 필터 상태를 가두지 않는다. category tile2열은 실제 taxonomy가 많을 경우 첫4개+`전체 카테고리` action이며 상품 row를 무한히 밀지 않는다. mobile product image target과 찜44 target은 서로 분리한다.

## 5. 구현 전/후 확인 체크리스트

시각 승인 전에는 **R1 핵심6개 화면(Home/PLP/PDP/Cart/Checkout/Login)의 Desktop/Mobile, Order Detail/Subscription New/Subscription Detail, 상태 보드와 R1 최종 검토 보드**를 확인한다. 코드 구현 후에만 다음을 실제 환경에서 pass/fail 판정한다.

1. 320/375/768/1024/1440에서 제목·금액·긴 label·drawer footer 가림 없음, 200% zoom에서도 reflow.
2. Category와 filter의 click/outside/Escape/Tab/focus return, drawer background inert, scroll lock 복원.
3. search·sort·filter apply/reset·clear one/all·pagination·back/forward가 같은 URL 상태를 표시, 늦은 응답 차단.
4. 가격 역전·unknown query·빈 taxonomy·0결과·API오류·retry가 구별됨.
5. wishlist/auth/cart/checkout 오류가 입력을 잃거나 성공으로 오인시키지 않음; 서버 금액·재고·version·멱등키 보존.
6. keyboard·screen reader·reduced-motion·forced-colors, form 자동완성/붙여넣기, sticky focus 가림 없음.
7. 실제 상품 사진/긴 상품명/옵션 조합/할인 없음/재고0/리뷰0/null price로 populated 검증. 이번 Production empty catalog로 대체할 수 없음.

## R1 전체 Customer 범위

[Customer page families](customer-page-families.md)의 주문·구독 상세, 계정·지원 상태를 추가 적용한다. [R1 최종 승인 전 보정](review-r1-final-check.md)의 multi-brand stress와 orbit 최소 크기 규칙도 구현 전 승인 대상이다. C dock는 기존 MVP4 PO 결정과 충돌하므로 별도 결정 변경 전 적용 금지. A에는 C dock 없음. 사용자 Screenshot 미첨부는 직접 Production 재캡처로 핵심 대조가 충족되어 Design Approval blocker가 아니다.
