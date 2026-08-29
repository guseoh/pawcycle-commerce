# MVP4-UX-004 Visual + Interaction Correction

## 지위와 델타 적용

- 상태: `Proposed Design Contract / Draft / Pending Product Owner Approval`
- 적용 대상: 기존 상위 문서와 A–D 번들에 대한 delta correction.
- 보존: 서버 권위, 날짜/주기 command 분리, idempotency, 안전한 GET `returnTo`, raw query 비저장, 2–3개 비교, MVP4 지원 필드·주기·채널 제한.
- 변경: 1440px PLP 5열을 4열로 축소하고, 모호한 responsive/overlay 선택지를 D의 단일 SSOT로 대체하며, 아래 상태 전이·화면 조성을 구현 판단 기준으로 추가한다.
- 승인 전 효과: 프론트엔드 구현 지시가 아니며 기존 FE Draft를 소급 수정하지 않는다.

## 상태 전이 계약

아래 표의 `Persistence`는 새 저장 기능을 뜻하지 않는다. 허용된 URL·history·현재 server state의 수명만 정의한다.

### Category menu

| 항목 | 계약 |
| --- | --- |
| Trigger | desktop `카테고리` 버튼, mobile `메뉴` 버튼 |
| Initial | 닫힘, `aria-expanded=false`, trigger가 tab sequence에 존재 |
| Open | desktop popover 360×min(560, viewport-96)px; mobile left drawer `min(360px,100vw)` |
| User Action | 항목 선택, 하위 group disclosure, 외부 클릭, 닫기, Escape |
| Transition | 선택한 leaf route로 이동; disclosure는 같은 overlay 안에서 expand/collapse |
| Pending | route 이동 전 선택 항목만 pressed; 전체 메뉴 spinner 금지 |
| Success | 새 route의 현재 항목에 `aria-current`; overlay 닫힘 |
| Error | navigation 실패 시 현 route 유지+page-level navigation error; 메뉴를 성공처럼 닫지 않음 |
| Cancel | Escape·닫기·desktop 외부 클릭은 선택 없이 닫음 |
| Close | background scroll 해제 후 원 trigger focus |
| Focus | 열 때 첫 category, 닫을 때 trigger; drawer는 focus trap |
| Keyboard | Tab/Shift+Tab, Enter/Space, Escape. roving menu를 쓰지 않고 일반 link 순서 사용 |
| Mobile | 56px header 아래 full-height drawer, header close와 고정 footer 없음; category link가 직접 행동 |
| Persistence | open state 저장 금지; 현재 category만 route에서 파생 |
| URL/History | leaf 선택은 정상 route push; 개폐는 history entry 없음 |

### Filter and sort

| 항목 | 계약 |
| --- | --- |
| Trigger | desktop filter control, 1023px 이하 `필터 N` 버튼, `정렬` button/select |
| Initial | URL을 parse한 applied 값; 모바일 draft는 열 때 applied 값 복사 |
| Open | desktop accordion rail; mobile left drawer `min(360px,100vw)`; sort는 320px 이상 bottom sheet가 아닌 popover/select |
| User Action | checkbox/radio, 가격 입력, chip 제거, reset, apply, sort 선택 |
| Transition | desktop discrete control 즉시 apply, 가격은 `적용`; mobile은 local draft 후 `N개 결과 보기` |
| Pending | 기존 결과 유지+toolbar progress+`aria-busy`; 조작 control만 pending |
| Success | 결과 수·chips·grid·URL을 같은 commit에서 갱신, page=1 |
| Error | 마지막 성공 결과·applied chips 유지, 실패한 draft와 `다시 시도` 제공 |
| Cancel | mobile 닫기/Escape는 draft 폐기, applied 유지 |
| Close | 적용 성공 또는 cancel 후 trigger focus; 성공 후 결과 heading announcement |
| Focus | drawer 제목→controls→reset/apply; chip 제거 후 다음 chip, 없으면 filter trigger |
| Keyboard | accordion Enter/Space, native checkbox/radio/select, Escape close, Tab trap in drawer |
| Mobile | filter/sort 2개 44px toolbar; drawer footer `초기화` secondary+`N개 결과 보기` primary |
| Persistence | applied만 URL; draft·원시 query·overlay state 저장 금지 |
| URL/History | apply/sort/reset/chip remove `pushState`; parameter 정렬만 `replaceState`; back/forward가 결과·scroll 복원 |

### Search

| 항목 | 계약 |
| --- | --- |
| Trigger | header search field, 검색 버튼, Enter |
| Initial | URL `q`를 표시; 입력 중 draft는 field local state |
| Open | 자동완성 panel 없음. focus 시 clear button만 조건부 표시 |
| User Action | 입력, clear, Enter, 검색 버튼, Escape |
| Transition | trim 후 값이 있으면 `/products?q=…`, 공백이면 `/products`; clear만으로 URL 변경 없음 |
| Pending | 기존 page 유지+submit button `검색 중`; 중복 submit 차단 |
| Success | result heading과 live result count; query field는 URL 값과 일치 |
| Error | 입력 유지, 검색 실패 alert와 retry; 로그인 오류로 오표현 금지 |
| Cancel | Escape는 입력을 지우지 않고 focus만 유지; 별도 panel이 없으므로 close 없음 |
| Close | 해당 없음 |
| Focus | submit 후 result heading을 programmatic announce; back 후 출발 field 또는 상품 복원 |
| Keyboard | Tab, Shift+Tab, Enter submit, Escape no-op; clear는 named button |
| Mobile | header 56px 아래 48px search row; 스크롤 시 search row는 사라지고 56px compact header만 sticky |
| Persistence | 원시 query 별도 저장 금지; URL만 공유 가능 상태 |
| URL/History | 새 submit push, 동일 정규화 replace, back/forward는 field·결과 동기화 |

### PDP purchase

| 항목 | 계약 |
| --- | --- |
| Trigger | PLP image/title link 또는 direct URL |
| Initial | product core loading; 구매 가능·가격 확인 전 CTA 숨김, skeleton은 실제 비율 예약 |
| Open | option selector는 native select 한 패턴으로 고정하고 상세 anchor는 page 내부 link를 사용 |
| User Action | gallery, option, 수량, 위시, 담기, anchor |
| Transition | 필수 option 선택→quantity enable→서버 범위 내 quantity→담기 intention |
| Pending | 담기 버튼 폭 유지+`담는 중`; 가격·재고의 이전 값에는 `확인 중` |
| Success | cart count·inline status 갱신, `장바구니 보기` 제공; 서버 확인 전 성공 표현 금지 |
| Error | 현재 선택 유지, 구매 가능 최신값·inline retry; 409는 최신 상품 상태 표시 |
| Cancel | option disclosure 닫기 또는 수량 원복; 담기 mutation은 UI 닫기로 취소됐다고 표시 금지 |
| Close | option overlay가 있으면 trigger focus; page는 유지 |
| Focus | gallery change는 강제 focus 없음; 담기 결과 status announce; anchor target은 scroll-margin 적용 |
| Keyboard | thumbnail button, native quantity, named +/- buttons, anchor links, Escape closes option disclosure |
| Mobile | gallery→summary→option/quantity→trust→detail; 64px `가격+담기` bar, safe area 포함, keyboard 시 static 위치로 전환 |
| Persistence | option/quantity는 current page state; refresh 복원 보장 없음; cart는 server state |
| URL/History | PDP route push; anchor hash replace; back은 PLP product/scroll/focus 복원 |

### Cart and coupon

| 항목 | 계약 |
| --- | --- |
| Trigger | header cart link, PDP 성공의 `장바구니 보기` |
| Initial | server cart loading; last-known total을 확정값처럼 캐시 표시 금지 |
| Open | coupon은 inline disclosure, 삭제 확인은 복구 불가일 때 modal |
| User Action | 전체/개별 선택, 수량, 삭제, coupon 입력·적용·해제, checkout |
| Transition | item 단위 mutation→server cart version/total commit; 연속 수량 click queue 금지 |
| Pending | 해당 item과 summary `계산 중`; checkout disabled+이유; 다른 item 탐색은 유지 |
| Success | 선택 수·item·discount·shipping·total과 cart version을 한 번에 갱신 |
| Error | 실패 item만 원래 값 유지+inline retry; 409는 최신 cart 전체를 보여주고 재확인 요구 |
| Cancel | coupon disclosure 닫기는 입력 유지하지 않음; 삭제 modal cancel은 무변경 |
| Close | disclosure trigger 또는 삭제 trigger focus; 삭제 성공이면 다음 item, 없으면 heading |
| Focus | 총액 변화 live region 1회; 오류는 item alert 또는 error summary |
| Keyboard | checkbox label 전체, +/- 44px, Enter coupon apply, Escape modal/disclosure close |
| Mobile | item image 96px+정보, action 다음 행; summary는 본문 뒤, 64px `N개 · 총액 주문하기` bar |
| Persistence | cart/coupon은 server state; 입력 중 coupon code local only |
| URL/History | `/cart`; item mutation history 없음; checkout은 `/checkout` push |

### Checkout and payment

| 항목 | 계약 |
| --- | --- |
| Trigger | 유효한 cart의 주문 CTA |
| Initial | 배송→할인→결제→최종 확인 순; 첫 미완료 section만 expanded, 이후 section locked |
| Open | 주소/결제 추가는 기존 route로 이동; order summary는 desktop sticky, mobile disclosure |
| User Action | 주소, coupon, 결제수단, 약관, 최종 CTA, provider 승인/취소 |
| Transition | 각 section 완료→summary read-only→다음 section open; 변경 시 downstream 재확인 |
| Pending | CTA `결제 준비 중`, 같은 intention idempotency key 유지, 전체 form `aria-busy` 금지 |
| Success | provider redirect 뒤 server confirmation 전 `결제 확인 중`; 확정 후에만 주문 완료 |
| Error | field validation, cart 409, provider fail, unknown을 분리; 입력·선택 유지 가능한 범위 명시 |
| Cancel | provider 취소는 checkout으로 안전 복귀하고 자동 재승인 금지 |
| Close | summary sheet close는 trigger focus; 결제 진행 중 임의 modal close로 성공/취소 추정 금지 |
| Focus | submit 오류 첫 field; 단계 완료 다음 heading; redirect 결과 `h1` |
| Keyboard | section heading button, native form controls, widget 자체 keyboard, Escape는 summary만 닫음 |
| Mobile | 1열 progressive sections, 상단 상품/금액 disclosure, 64px 금액+CTA; keyboard 시 fixed 해제 |
| Persistence | form draft 자동 저장 금지; server checkout context만 사용; password/token URL 금지 |
| URL/History | address/billing은 sanitized GET returnTo; back/refresh mutation replay 금지; result는 success/fail route |

### Subscription management

| 항목 | 계약 |
| --- | --- |
| Trigger | list/detail의 server `availableActions`, 주문 상세의 승인된 진입 CTA |
| Initial | next delivery, cycle, issue, pending change, ETag와 actions를 server state에서 렌더 |
| Open | 날짜는 480px modal/mobile full-screen, 주기는 별도 480px dialog, 두 dialog 동시 금지 |
| User Action | 날짜 변경, 2/4/8주 주기 변경, 수량, 건너뛰기, 취소, add-on |
| Transition | 현재값→허용 범위/영향 확인→해당 command 한 개 submit; 날짜와 주기 command 합치지 않음 |
| Pending | action card/button에 `변경 중`; ETag와 idempotency 유지; 다른 destructive action 잠금 |
| Success | 최신 detail 재조회, pendingChange가 있으면 상단 영구 banner, 효과 시점 문장 표시 |
| Error | validation/409/412/issue를 구분; 최신 server state와 사용자 시도를 함께 보여주고 자동 재적용 금지 |
| Cancel | dialog close는 draft 폐기, server state 불변 |
| Close | 성공 후 summary heading, cancel 후 trigger focus |
| Focus | open 시 dialog title/첫 control, conflict는 alert heading, close 시 live trigger |
| Keyboard | radio/date controls, Tab trap, Escape cancel, destructive confirm은 명시적 버튼 |
| Mobile | detail 1열; issue→next delivery→cycle→items→history→danger; 편집은 full-screen dialog |
| Persistence | server subscription state만 영구; dialog draft·추천 주기 저장 금지 |
| URL/History | detail route 유지, dialog history 없음; 인증 만료 후 sanitized detail GET 복귀 |

## Annotated composition specifications

모든 수치는 D의 container·breakpoint SSOT를 사용한다. 아래 `1→2→3`은 DOM과 시각 순서가 같다.

| 화면 | 1440px 이상 조성 | 모바일 조성 | sticky·행동·상태 annotation |
| --- | --- | --- | --- |
| Home | 1 header 120px → 2 hero max 320px, copy 7/visual 5 → 3 pet category 6개/한 행 → 4 lifecycle strip 3칸 → 5 대표 rail 4.5카드 → 6 개인화 → 7 trust | 56px header+48px search → hero copy/visual → 2열 category → 2.1카드 rail | compact header 64px만 sticky; carousel 자동재생 없음; 추천 실패는 section 격리 |
| PLP | breadcrumb → h1/설명 → chips → 52px toolbar → 232px rail+24px gap+4×281px grid → pagination | h1→chips horizontal wrap→44px filter/sort row→2열 card→pagination | filter group 독립 accordion; toolbar sticky offset 64px; URL commit 후 result announce |
| PDP | breadcrumb → gallery 7열/summary 5열 → 56px anchor → detail/review/Q&A/recommend/trust | gallery→title/price/review→option/qty→trust→details→review/Q&A | desktop summary는 container 안 sticky; mobile 64px 가격+담기, anchor는 horizontal links |
| Cart | h1 → 8열 item list / 4열 320px summary → trust | h1→items→coupon→summary→recommend | summary top 96px sticky; mobile bottom CTA는 선택 수+server total, conflict 시 disabled |
| Checkout | h1/step 설명 → 8열 progressive form / 4열 summary | 상품/금액 disclosure→배송→할인→결제→확인 | 한 번에 편집 section 1개; 64px final CTA, keyboard 시 fixed 해제 |
| Order Detail | breadcrumb→status header→8열 상품/배송/결제 / 4열 next action→timeline→cancel/return→reorder/subscription→support | status→next action→items→delivery/payment→timeline→reorder→danger→support | `availableActions`만 노출; destructive는 마지막, 부분 재주문 영구 결과 panel |
| Subscription Detail | breadcrumb→status/issue→8열 next delivery/cycle/items/history / 4열 actions→danger | issue→next delivery→cycle→items→history→actions→danger | 날짜/주기 편집 분리; pending banner가 summary 위; server 최신화 전 성공 금지 |
| My | h1/account→issue full row→8열 next delivery / 4열 recent order→management rows→support | account→issue→next delivery→recent order→management→support | 추천 rail 중복 금지; section별 retry; logout은 마지막 tertiary |
| Mobile header/nav | 해당 없음 | 56px: menu 44, logo 96, spacer, cart 44; 다음 48px search; bottom nav는 PO 승인 전 없음 | 스크롤 104px 후 56px header만 sticky; search/category drawer focus 복귀 |
| Mobile PLP | 해당 없음 | 16px gutter, 2×`calc((100%-12px)/2)` card; 320–359는 image 112px+정보 행 1열 | filter/sort 44px, drawer left, quick add full-width 44px, compare/wish 44px overlay control |
| Mobile PDP | 해당 없음 | 16px gutter; media viewport width; 정보 section 24px gaps; accordion 48px headers | 64px bottom purchase bar; 페이지 footer 진입 시 static; keyboard/overlay 중 숨김 |
| Mobile Checkout | 해당 없음 | 16px gutter; order disclosure 48px; section headers 52px; fields 52px; summary before CTA | 현재 section만 open; error summary top; 64px CTA safe-area 포함 |
| Login | 760px reading container 안 480px form, logo→h1→context→fields→submit→recovery | 16px gutter, 52px fields/CTA | password reveal 44px, error summary+inline, 성공 후 sanitized GET only |

## 명시적으로 변경된 기존 계약

1. PLP `1440+ 5열 / 1200–1439 4열`을 `1440+ 4열 / 1200–1439 3열`로 변경한다. 반려상품 카드의 상태·설명·가격·리뷰·44px 행동을 220px 내에 압축하지 않는다.
2. `900px` 분기를 제거한다. 모든 A–C responsive 문장은 D의 `1440/1200/1024/768/600/360/320` SSOT만 참조한다.
3. drawer의 `bottom/full sheet` 선택지를 제거한다. mobile category/filter는 left drawer, 복잡한 편집은 full-screen dialog, short sort는 native select/popover로 고정한다.
4. PDP `6/6 또는 단일 열`, My `8/4 또는 2열`, 주소 `modal/full-screen 또는 inline` 같은 선택지를 제거하고 D 및 위 composition으로 고정한다.
5. 문서 지위를 승인 완료 상태가 아니라 `Proposed Design Contract / Draft / Pending Product Owner Approval`로 변경한다.

`NO FRONTEND IMPLEMENTATION PERFORMED`
