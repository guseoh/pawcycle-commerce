# MVP4-UX-004 D. 공통 컴포넌트·반응형·접근성

상태: `Proposed Design Contract / Draft / Pending Product Owner Approval`

## 목적

이 문서는 A–C 화면을 일관되게 검토하기 위한 제안 계약이다. 색상·타입·형태는 [재설계 제안안](./MVP4-UX-004-customer-commerce-redesign.md), 실제 근거는 [벤치마크 증거](./MVP4-UX-004-benchmark-evidence.md), 화면 전이는 [Visual + Interaction Correction](./MVP4-UX-004-visual-interaction-correction.md)에 연결한다. breakpoint·container·header·sticky 수치는 이 문서 D8만이 SSOT다.

## D1. App Shell과 Footer

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | layout이 `AppHeader`, `AppFooter`, `AuthProvider`를 모든 고객 route에 제공한다. |
| 문제 | route마다 폭·상단 여백·footer 진입이 달라지면 sticky CTA와 skip link, focus 복원이 불안정하다. |
| 레퍼런스 | Kurly의 full→compact sticky header와 Musinsa의 검색·account·cart 유틸리티는 `CONFIRMED/ADAPT`; 지나치게 많은 GNB와 홍보 footer는 `REJECT`. |
| 최종 IA | skip link→header→main→footer. 1024px 이상 initial header는 72px primary(logo 140px, search 440px/1200–1439 360px, account/cart 각 44px)+48px nav다. 768–1023은 56px primary+48px search, 320–767도 동일 높이에서 logo 96px와 menu/cart 44px을 쓴다. main은 route별 `discovery/product/transaction/reading` container를 명시한다. footer는 쇼핑, 계정, 정책·지원 3 group과 사업 필수 정보만 둔다. |
| visual hierarchy | header/main은 1px `border-soft`, footer는 `surface-soft`. initial header가 viewport 위로 벗어나면 1024px 이상 64px compact row, 1023px 이하 56px mobile row 한 개만 sticky다. 검색행과 nav행은 함께 고정하지 않는다. |
| 컴포넌트 | `SkipLink`, `AppHeader`, `RouteContainer`, `Breadcrumb`, `AppFooter`, 선택적 `MobileBottomNav`. |
| interaction/navigation | route 전환 후 기본은 main `h1`로 focus를 보내지 않고 문서 상단부터 자연 순서를 유지한다. 사용자가 명시적 CTA로 결과/오류에 도달한 경우만 해당 heading focus. skip link는 main으로 이동. |
| loading/empty/error/success | shell은 데이터 상태와 무관하게 유지한다. page 오류가 header/footer까지 없애지 않는다. auth loading은 계정 slot만 skeleton. |
| responsive | header·sticky offset은 D8. footer 768px 이상 3열, 767px 이하 순차 heading+link list. 모바일 bottom nav는 PO 승인 전 구현 금지. |
| accessibility | landmarks 하나씩, `main` id 고정, 현재 nav `aria-current`, breadcrumb ordered list, footer heading 구조. |
| gap·impact | route container variant와 sticky offset token을 통일해야 한다. |
| acceptance | 모든 route에서 skip link가 작동하고 header/footer가 page error에 남으며, sticky 요소가 anchor/focus를 가리지 않는다. |

### 모바일 하단 내비게이션 후보

Recommended Default는 MVP4 미도입이다. PO가 도입을 승인한 경우에만 `홈 / 쇼핑 / 정기배송 / 주문 / 마이` 5개를 사용한다. 각 항목은 icon+label, 높이 64px+safe-area, `aria-current`를 갖고 장바구니는 header에 유지한다. 키보드가 열리거나 PDP/cart/checkout transaction CTA가 있으면 bottom nav를 숨긴다. 승인 전에는 header menu와 page links로 동일 route에 접근한다.

## D2. Button과 Link

| variant | 사용 | 금지 |
| --- | --- | --- |
| Primary | 화면 또는 dialog의 핵심 진행 1개 | 한 viewport에 경쟁하는 primary 여러 개 |
| Secondary | 대안·편집·상세 | 주 행동보다 강한 fill |
| Tertiary/Text | 저위험 보조 이동 | 44px 미만 target |
| Destructive | 취소·삭제 최종 확인 | 경고 전 모든 취소/반품을 빨간 primary로 표시 |
| Icon | 검색 clear, 위시, 닫기 | accessible name 없는 단독 icon |

- 크기: small 40px은 비터치 보조 control에만, default 44px, primary transaction 48px, mobile form/transaction 52px. 좌우 padding은 16/20/24px, icon gap 8px, radius 8px다.
- Primary: default `brand` fill/white text, hover·pressed `brand-hover`, focus `focus` 2px+2px offset, disabled `disabled-surface/disabled-text`, loading은 기존 폭 유지.
- Secondary: white fill/`border-control`/`text-strong`, hover `surface-soft`, pressed `selected-soft`, focus는 동일 outline.
- Destructive: white fill/`danger` border+text, 최종 확인 dialog의 primary에서만 `danger` fill/white를 사용한다.
- 상태: default, hover, focus-visible, active, disabled, loading을 모두 제공한다.
- disabled는 서버 권한 대기나 필수 입력 미충족처럼 이유가 이해될 때 사용한다. 허용되지 않은 action을 영구 disabled CTA로 노출하지 않는다.
- loading은 너비를 유지하고 동사형 라벨 `저장 중`, `확인 중`으로 바꾼다. spinner만 두지 않는다.
- 링크와 버튼의 의미를 바꾸지 않는다. route 이동은 링크, mutation/modal은 버튼이다.
- 같은 사용자 의도의 중복 submit은 첫 activation에서 막는다. 결제·구독·재주문은 idempotency 계약도 함께 적용한다.

## D3. Form, Field, Validation

### 구조

`Label → optional/required 표시 → control → hint → field error` 순서다. placeholder는 label을 대신하지 않는다. form submit 오류가 2개 이상이면 상단 `ErrorSummary`와 field 오류를 함께 제공하며 summary link는 해당 field로 이동한다.

| 상태 | 표현 |
| --- | --- |
| default | 1px border, surface |
| hover | border 강화, 값 변화 없음 |
| focus | 2px `focus` outline + 2px offset |
| invalid | danger border+icon+문장, 색만 사용하지 않음 |
| disabled | 실제로 사용할 수 없는 control; 이유 인접 표기 |
| read-only | disabled보다 높은 대비의 text/control, focus 가능한 설명 |
| pending | form 또는 field 단위 busy, 값 유지 |
| success | 영구 summary 또는 짧은 status; 모든 field에 초록 check를 반복하지 않음 |

- text input/select 높이는 desktop 44px, mobile 52px, padding 0 12px, radius 8px, `border-control` 1px다. textarea는 최소 120px, checkbox/radio 시각 control은 20px이고 label을 포함한 target은 44px다.
- invalid border는 `danger` 2px, error text는 14px/1.5 `danger`; focus와 invalid가 겹치면 danger border를 유지하고 바깥 `focus` outline을 추가한다.
- 날짜는 locale 표시와 서버 전송 형식을 분리한다. 다음 배송일과 주기는 서로 다른 fieldset이다.
- 금액은 서버 응답 전 계산 완료로 표현하지 않는다.
- 비밀번호, 결제 토큰, 전체 카드번호, 원시 provider 오류를 log/DOM/URL에 넣지 않는다.
- mobile input font는 최소 16px, 적절한 `autocomplete`, `inputmode`를 지정한다.

## D4. Modal, Dialog, Drawer, Popover

| 패턴 | 허용 사용 | 크기·행동 |
| --- | --- | --- |
| Modal dialog | 파괴 확인, 짧은 결과 확인 | 일반 480px, 상세 결과 560px; mobile 위험 확인 `min(328px, calc(100vw - 32px))` 중앙 dialog |
| Full-screen dialog | mobile의 주소·결제·복잡한 구독 편집 | 100dvh, header close, footer CTA, safe area |
| Drawer | 모바일 메뉴·PLP filter | 왼쪽에서 너비 `min(360px,100vw)`, 100dvh; filter만 reset/apply footer 고정 |
| Popover | 카테고리·계정의 짧은 메뉴 | trigger 인접, viewport collision 대응 |
| Toast | 비파괴 성공·되돌리기 | 6초, undo 진행 중이면 완료까지 유지; 오류 해결을 toast에만 두지 않음 |

### 공통 focus 계약

1. trigger element와 이전 focus를 저장한다.
2. overlay가 DOM에 준비된 후 제목 또는 첫 유효 control로 focus한다.
3. Tab/Shift+Tab을 modal/drawer 내부에서 순환한다. popover는 메뉴 패턴을 쓸 때만 roving focus를 사용한다.
4. Escape로 안전하게 닫는다. 진행 중 결제처럼 닫기가 위험하면 Escape 동작 대신 이유를 설명하고 명시적 선택을 요구한다.
5. 닫으면 살아 있는 trigger로 돌아가고, trigger가 제거됐으면 다음 논리적 heading/action으로 이동한다.
6. background를 inert 처리하고 scroll을 잠근다. scrollbar 보정으로 layout shift를 만들지 않는다.

### 공통 패턴 선택표

| 패턴 | 고정 사용 조건과 구조 | 키보드·focus | mobile·close |
| --- | --- | --- | --- |
| Accordion | 서로 관련된 긴 설정 group; 여러 panel 동시 open | heading button, Enter/Space, `aria-expanded`; focus 유지 | 동일 inline pattern, history 없음 |
| Disclosure | FAQ·coupon·order summary 한 항목의 show/hide | native button, Enter/Space; 내용은 바로 다음 DOM | 외부 클릭으로 닫지 않음, Escape 필수 아님 |
| Tabs | 같은 수준의 상호 배타 view 2–5개 | Arrow/Home/End, selected tab과 panel 연결 | 가로 scroll 가능하되 prev/next로만 숨겨진 tab 접근 금지 |
| Dropdown menu | 계정 같은 3–7개 짧은 action | trigger→첫 item, Arrow/Escape, trigger 복귀 | 600px 미만은 같은 popover 폭을 viewport-32px로 제한 |
| Select | form 값 하나 선택 | native select 우선; 브라우저 기본 키 | OS picker 사용, custom bottom sheet 금지 |
| Popover | category·sort·짧은 비파괴 선택 | 외부 클릭/Escape close, trigger 복귀 | viewport-32px, max-height 60dvh |
| Modal | 위험 확인·짧은 결과 | title focus, Tab trap, Escape cancel, trigger 복귀 | 중앙 `min(328px, calc(100vw - 32px))`; 복잡 form 금지 |
| Drawer | category/menu/filter | title/첫 control focus, Tab trap, Escape | left drawer만 사용, swipe만으로 닫기 금지 |
| Bottom sheet | order summary의 읽기+닫기만 | dialog semantics, close button, Escape | checkout summary에만 사용; form·filter·위험 확인 금지 |
| Tooltip | icon 의미의 짧은 보조 설명 | hover와 focus 모두 표시, Escape close | tap 의존 금지; 필수 정보는 항상 visible text |
| Toast | 서버 확인된 비파괴 결과, undo | focus를 빼앗지 않고 status announce, undo 44px | bottom CTA 위 16px, 5–8초; 오류 해결 금지 |
| Carousel | Home 상품 rail·hero 1개 | prev/next named button, slide status, drag 비필수 | 1.1–2.1 card peek; 자동재생 기본 금지 |
| Pagination | PLP/order list의 서버 page | 실제 link, current `aria-current=page` | prev/current/next+전체 page 접근 link, 무한 scroll 금지 |
| Sticky | compact header, PLP toolbar, PDP/transaction CTA | focus target을 가리지 않고 scroll-margin 사용 | 동시에 top 1개+bottom 1개, 합계 20dvh 이하 |
| Expandable text | 긴 상품 설명 preview | button `더 보기/접기`, focus 유지 | 4 lines preview, 사용자 선택 전 자동 collapse 금지 |
| Inline edit | 단일 값·낮은 복잡도 | `편집`→field→저장/취소, cancel은 원값 | 주소·결제·구독 변경에는 사용 금지 |
| Destructive action | 삭제·취소·반품 | 대상/영향/복구 가능성→modal confirm; pending 차단 | bottom sheet 금지, danger primary는 최종 확인만 |
| Skeleton transition | 최초 core·독립 section loading | skeleton `aria-hidden`, status 한 개 | 500ms 미만에는 유지 content/progress; 완료 시 fade 80ms 이하 |

## D5. Navigation, URL, Scroll, Focus

### URL 단일 권위

- 제출된 search, 적용된 filter/sort/page, compare 상품 ID처럼 공유 가능한 탐색 상태만 URL에 둔다.
- form draft, 결제 context, 민감 정보, 원시 search history는 URL/localStorage에 저장하지 않는다.
- 명시적 사용자 적용은 `pushState`, 같은 entry의 단순 정규화는 `replaceState`다.

### 뒤로가기 복원

- PLP→PDP: history state에 출발 product ID와 scroll anchor를 저장한다. back 후 URL 결과가 안정되면 scroll 복원, 해당 product title에 programmatic focus-visible을 준다.
- 목록→상세 전반: filter/page/scroll을 복원하되 서버 결과가 달라 출발 item이 없으면 result heading으로 이동하고 설명한다.
- modal open/close는 공유 가능한 별도 화면이 아니면 history entry를 만들지 않는다. browser back으로 닫기를 지원하려면 모든 overlay에 일관되게 적용해야 하므로 MVP4 기본은 제외한다.
- checkout/payment에서 back/refresh는 mutation을 자동 재실행하지 않는다.

### 페이지 이동

- pagination/새 search/filter apply는 결과 heading 위로 scroll한다.
- in-page anchor는 sticky offset을 고려하고 target heading에 `scroll-margin-top`을 준다.
- route 전환마다 무조건 body focus를 강제하지 않는다. screen reader announcement는 route title과 main heading으로 충분히 제공한다.

## D6. Loading, Empty, Error, Success, Retry

| 상태 | page | section | item/control |
| --- | --- | --- | --- |
| loading | 최초 핵심 skeleton, shell 유지 | 독립 skeleton/`aria-busy` | 버튼 진행 라벨 |
| empty | 정상적으로 데이터 없음+다음 행동 | 해당 section만 empty/숨김 기준 | 해당 없음 |
| error | 핵심 데이터 실패+retry | 마지막 성공 내용 유지+retry | 원래 값 유지+inline retry |
| success | 서버 확인된 summary | 새 상태 영구 반영 | toast/status 보조 |
| retry | 같은 안전 조회 또는 사용자 확인 후 새 mutation | 실패 범위만 | idempotency/최신 version 확인 |

### Skeleton 규칙

- 실제 layout 비율을 예약하고 1.6초 정적 pulse를 사용한다. 이동하는 shimmer는 사용하지 않는다.
- 모든 skeleton 조각을 screen reader가 낭독하지 않게 묶어 숨기고, 하나의 `불러오는 중` status만 제공한다.
- 500ms 미만의 빠른 조회에는 flashing skeleton 대신 현재 content 유지 또는 최소 progress를 쓴다.

### Error 문장 구조

`무엇을 완료하지 못했는지 → 현재 데이터가 안전한지 → 사용자가 할 수 있는 다음 행동` 순서다. HTTP 코드, enum, stack, provider 원문은 노출하지 않는다. 예: `수량을 바꾸지 못했어요. 기존 수량은 그대로예요. 다시 시도해 주세요.`

### 부분 성공

재주문처럼 부분 성공이 가능한 경우 전체 toast 하나로 끝내지 않는다. summary→성공 item→제외 item과 이유→다음 CTA 순으로 영구 panel을 제공한다.

## D7. Product, Status, Price, Date 표현

- 상품 상태 badge는 최대 2개. 우선순위: 구매 불가/품절 → issue → 할인 → 배송/추천 메타.
- 가격은 현재가, 원가, 할인율의 의미를 DOM 순서와 시각 순서에서 일치시킨다. 할인율만 단독 표시하지 않는다.
- 리뷰 0개는 평균 없음으로, 별 0개가 아니다.
- 날짜는 `2026년 9월 12일`처럼 locale 기준. 시간대가 중요하지 않은 배송일에 임의 시간을 붙이지 않는다.
- 상태 enum은 고객 언어 사전을 통과한다. unknown은 `상태 확인 필요`로 안전하게 실패한다.
- server-authoritative 값이 갱신 중이면 이전 값에 `확인 중`을 붙이고 새 값으로 추정하지 않는다.

### Product Card 세부 anatomy

| 영역 | 고정 규격 |
| --- | --- |
| container | 외곽 card fill 없음, 1:1 media+12px content gap; hover는 image border `brand`, 전체 card shadow 금지 |
| media | 1:1, `surface`, radius 10px, `object-fit:contain`; 상단 badge 최대 2개, 우상단 wish 44px |
| title | brand 12px/1.4 `text-muted`; 상품명 15px/1.45 600, 2 lines; 전체 accessible name 유지 |
| description | 13px/1.45 `text-muted`, 1 line; 없으면 공간 예약 금지 |
| price | 현재가 18px/1.25 700; 할인 14px 700 `accent-text`; 원가 13px `text-muted` 취소선 |
| meta | 리뷰 13px, 구매/배송 상태 13px; badge보다 server 구매 불가 문장이 우선 |
| actions | quick add 44px full width; compare/wish 각 44px. 4열은 add 아래 compare, 3열 이하는 compare를 card footer 보조행에 둔다 |
| states | loading은 media/title/price 비율 예약; unavailable은 image overlay+문장, add disabled+이유; error는 card 제거 대신 inline retry |

## D8. 반응형 기준

### breakpoint와 container

| 범위 | grid | gutter | 주요 전환 |
| --- | --- | --- | --- |
| 1440px 이상 | discovery max 1440/12열/24 gap, product 1320, transaction 1180, reading 760 | 32px | header 120→sticky 64; PLP 232 rail+24+4열(약 281px), PDP 7/5, transaction 8/4 |
| 1200–1439 | 동일 max, 12열/24 gap | 24px | header 120→64; PLP 216 rail+24+3열, PDP 7/5, transaction 8/4 |
| 1024–1199 | viewport-48, 12열/20 gap | 24px | header 112→64; PLP drawer+3열, PDP 7/5, transaction 8/4 |
| 768–1023 | viewport-48, 8열/20 gap | 24px | header 104→56; PLP 2열, PDP/transaction 단일 열 |
| 600–767 | viewport-32, 4열/16 gap | 16px | header 104→56; 2열 cards, 단일 form |
| 360–599 | viewport-32, 4열/12 gap | 16px | header 104→56; 2열 cards, 64px mobile transaction action |
| 320–359 | viewport-24, 4열/8 gap | 12px | header 104→56; 1열 112px image+정보 행 card, 긴 label wrap |

범위 경계는 구현/QA 공통 기준이며 A–C에서 별도 `900px`, `1200px rail 4열`, `1440px 5열`을 만들지 않는다. 200% browser zoom은 zoom 뒤 CSS viewport에 위 표를 그대로 적용한다. 예를 들어 1440px 화면의 200% zoom은 720 CSS px이므로 600–767 규칙과 2열 card를 사용한다.

### container와 sticky 충돌

- `discovery`: `min(1440px, viewport-gutter×2)`, `product`: 1320px, `transaction`: 1180px, `reading/form`: 760px다.
- top sticky offset은 1024px 이상 64px, 1023px 이하 56px다. PLP toolbar와 PDP anchor는 compact header 바로 아래 한 개만 sticky다.
- bottom transaction action은 64px+safe-area다. mobile bottom nav는 동시에 존재하지 않고, footer sentinel이 보이면 action을 document flow의 static 위치로 바꾼다.
- focus target에는 top offset+16px의 `scroll-margin-top`, fixed bottom action이 있는 form에는 96px+safe-area의 scroll padding을 둔다.

### 모바일 우선순위

- 검색·filter·sort·가격·CTA는 화면 안에 항상 도달 가능해야 한다.
- desktop hover action은 mobile에서 항상 보이는 icon/label로 전환한다.
- 표는 핵심 label/value 행 또는 tabs로 reflow한다. 전체 페이지 수평 scroll을 허용하지 않는다.
- `100vh` 대신 `100dvh`와 safe-area를 사용한다. keyboard가 열리면 fixed CTA가 input/error를 가리지 않는다.
- sticky 영역 합계는 viewport 높이의 20%를 넘지 않는다.

### 확대와 긴 콘텐츠

- 200% browser zoom과 320 CSS px에서 기능 손실이 없어야 한다.
- 버튼 라벨, 가격, 주문번호, 주소, 상품명은 wrap 가능해야 한다. 높이 고정으로 잘라내지 않는다.
- 한글/영문/숫자 혼합 문자열은 `overflow-wrap:anywhere`를 식별자 영역에만 적용한다.

## D9. 키보드·스크린리더 계약

### 기본 키

| 패턴 | 키보드 |
| --- | --- |
| 링크/버튼 | Tab 이동, Enter/Space native 동작 |
| native select/radio/checkbox | 브라우저 기본 키 유지 |
| tabs | Arrow로 tab 이동, Home/End, 활성 panel 연결 |
| modal/drawer | Tab trap, Escape 닫기, trigger 복귀 |
| disclosure | Enter/Space toggle, `aria-expanded` |
| carousel rail | 명시적 이전/다음 버튼; drag 필수 아님 |

- DOM 순서와 시각 순서를 CSS `order`로 뒤집지 않는다.
- `focus-visible` 2px outline을 제거하지 않는다. 그림자만으로 focus를 표현하지 않는다.
- live region은 결과 수, mutation 결과, 부분 성공 summary처럼 사용자가 기다린 변화에만 쓴다. skeleton/가격의 모든 작은 변화는 반복 낭독하지 않는다.
- icon+text가 같은 링크 안에서 같은 이름을 반복하지 않게 장식 icon을 숨긴다.
- 이미지 alt는 기능과 문맥에 맞춘다. 상품명과 이미지가 같은 링크 안에 있으면 이미지는 빈 alt, 이미지 단독 링크이면 상품명 alt를 사용한다.

## D10. Motion과 Carousel

- Home hero는 정적 1개로 고정하고 자동 재생하지 않는다. 상품 rail은 previous/next와 현재 위치를 제공하며 drag 없이 모든 항목에 도달한다.
- overlay는 opacity+translate 8–16px, 최대 220ms. list reorder나 price update에 과한 spring을 쓰지 않는다.
- reduced motion에서는 carousel 자동 전환, parallax, zoom, smooth scroll을 끈다. focus 이동은 즉시 이루어진다.
- loading shimmer가 vestibular/인지 부담을 만들지 않도록 넓은 고대비 band를 사용하지 않는다.

## D11. 콘텐츠와 신뢰 규칙

- UI 문장은 결과와 다음 행동 중심 존댓말을 사용한다. `실패했습니다`보다 `수량을 바꾸지 못했어요. 기존 수량은 그대로예요.`를 쓴다.
- AI 추천은 실제 서버가 제공한 근거만 설명한다. “우리 아이가 좋아할” 같은 보장 문구를 쓰지 않는다.
- 배송·할인·가격·재고·정기배송 혜택을 추정하지 않는다.
- destructive dialog는 대상, 되돌릴 수 있는지, 언제 적용되는지를 한 문단으로 설명한다.
- raw enum, ISO timestamp, API field name, HTTP code, ETag를 고객에게 그대로 보이지 않는다.

## D12. Unsupported 패턴 차단

다음 컴포넌트/상태는 승인 전 design system에 만들지 않는다.

- 최근 검색 panel과 개인화 autocomplete
- email/push/SMS notification preference
- pet photo uploader와 birthday picker
- payment retry button
- out-of-stock automatic substitute selector
- same-day/store pickup badge
- support chat launcher
- PDP autoship frequency selector
- subscription cycle auto-apply control

빈 자리나 disabled `준비 중` control로도 노출하지 않는다.

## D13. 접근성·반응형 인수 체크리스트

### 모든 화면

- [ ] `h1` 하나와 순차 heading, landmark, skip link가 있다.
- [ ] 모든 interactive target이 44×44px 이상이며 accessible name이 독립적으로 이해된다.
- [ ] focus-visible이 배경과 3:1 이상 구분되고 sticky/overlay에 가려지지 않는다.
- [ ] 색·위치·icon만으로 상태를 전달하지 않는다.
- [ ] loading/empty/error/success/retry가 서로 다른 문장과 행동을 가진다.
- [ ] 320/375/768/1024/1440, 200% 확대에서 가로 scroll과 기능 손실이 없다.
- [ ] reduced motion에서 자동 이동·zoom·smooth scroll이 제거된다.
- [ ] anonymous/session expiry가 unsafe returnTo 또는 mutation replay를 만들지 않는다.

### 복합 컴포넌트

- [ ] modal/drawer/popover가 focus 진입·trap/관리·Escape·복귀 계약을 만족한다.
- [ ] URL 상태가 back/forward, copy/paste, refresh에서 동일한 결과를 만든다.
- [ ] mutation은 item 단위 pending/error를 제공하고 double submit을 막는다.
- [ ] conflict는 최신 서버 상태와 다시 확인 행동을 보여준다.
- [ ] 표·비교·timeline이 semantic structure를 사용하며 mobile에서 reflow한다.

## D14. 구현 영향 요약

| 구현 영역 | 예상 변경 | 비변경 |
| --- | --- | --- |
| 공통 CSS/tokens | container variant, grid, spacing, focus, state colors, reduced motion | 새 font·디자인 dependency 없음 |
| App shell | route container, skip link, sticky offset, mobile menu | auth/API 정책 변경 없음 |
| 공통 components | button/field/status/overlay/skeleton/error/price/date | 새 제품 action 없음 |
| navigation state | URL commit, scroll/focus restore | raw query persistence 없음 |
| transaction state | pending/conflict/partial success | 서버 상태·idempotency 계약 변경 없음 |

이 문서는 Frontend 구현 범위를 설명하지만 구현을 수행하거나 승인하지 않는다. 디자인 승인 뒤 별도의 Frontend 작업이 gap을 재산정해야 한다.
