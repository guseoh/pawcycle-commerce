# MVP4-UX-004 Customer Commerce UI/UX 전면 재설계

## 문서 지위와 범위

- 작업 ID: `MVP4-UX-004`
- 등급: 일반
- 실행 구분: 저장소 문서 변경. Production·Cloud·운영 DB 실행이 아니다.
- 역할: UX/UI Designer
- 상태: `Proposed Design Contract / Draft / Pending Product Owner Approval`
- 기준: `origin/main`의 `626e1d2`와 2026-08-29 현재 Draft `origin/feat/fe/MVP4-FE-004`
- 포함: 고객용 탐색, 구매, 주문, 정기배송, 계정, 공통 반응형·접근성 설계 계약
- 제외: 프론트엔드·백엔드 코드, API·DB·인프라 변경, 새 제품 기능 승인, 운영 실행, 병합

이 문서는 MVP4 고객 경험의 승인 제안안이다. 기존 `MVP4-UX-002`의 제품 방향과 `MVP4-UX-003`의 기능·접근성 발견사항을 보존하고, 이번 correction은 [실제 벤치마크 증거](./MVP4-UX-004-benchmark-evidence.md)와 [Visual + Interaction Correction](./MVP4-UX-004-visual-interaction-correction.md)을 포함한다. Product Owner 승인 전에는 승인 완료 상태나 구현 권위로 취급하지 않는다. Draft FE-004와 그 안의 commerce layout correction도 현재 구현 의도를 이해하기 위한 감사 입력일 뿐 승인된 디자인 권위가 아니다.

## 승인 입력과 비승인 경계

### 보존되는 승인 입력

- 서버가 가격, 구매 가능 여부, 다음 배송일, 허용 액션, 충돌 버전의 권위다.
- 정기배송의 배송일 변경과 배송 주기 변경은 서로 다른 액션이다.
- 인증 후 복귀는 안전한 내부 GET 경로만 허용하며 작성 중 폼이나 POST를 자동 재실행하지 않는다.
- 비교는 2–3개 상품, 펫 프로필은 이름·펫 유형·품종·체중만 사용한다.
- 이메일·푸시·SMS가 아닌 인앱 알림만 MVP4 범위다.
- 원시 검색어는 상호작용 기록에 저장하지 않는다.

### 설계가 만들지 않는 제품 기능

최근 검색어 저장, 펫 사진·생일, 품절 대체품 자동 지정, 매장 픽업·당일 배송, 구독 자동 주기 변경, 결제 재시도, 상담 채팅, 이미지 업로드는 이 설계가 추가하지 않는다. 레퍼런스에 존재하더라도 `UNSUPPORTED`로만 기록한다.

## 감사된 고객 라우트 인벤토리

| 영역 | 실제 라우트 | 현재 주요 상태·의존성 | 재설계 번들 |
| --- | --- | --- | --- |
| 전역 셸 | 모든 고객 라우트 | `AppHeader`, `AppFooter`, `AuthProvider`; 인증 `loading/authenticated/anonymous/error` | D |
| 홈 | `/` | 공개 발견·추천, 로그인 상태, 펫 프로필, 상품 카드 | A |
| 상품 목록·검색 | `/products` | URL 쿼리, 필터·정렬·페이지, 비교, 상호작용 이벤트 | A |
| 상품 상세 | `/products/[productId]` | 상품·리뷰·Q&A·연관 추천, 구매 가능 상태 | A |
| 비교 | `/compare` | 2–3개 상품 비교 | A |
| 위시리스트 | `/wishlist` | 인증, 담기·삭제 | A |
| 장바구니 | `/cart` | 인증, 수량·선택·쿠폰·버전 충돌 | B |
| 결제 | `/checkout` | 인증, 주소·결제수단·쿠폰, idempotency, Toss 위젯 | B |
| 결제 결과 | `/checkout/success`, `/checkout/fail` | 승인 확인, 복구 안내 | B |
| 주문 | `/orders`, `/orders/[orderId]` | 주문 상태, 취소·반품, 재주문, 구독 전환 제안 | B |
| 정기배송 | `/subscriptions`, `/subscriptions/[subscriptionId]`, `/subscriptions/new` | v2 플랜·구독·명령·주기·추가상품, ETag/If-Match | C |
| 이전 정기배송 별칭 | `/mvp2/subscriptions`, `/mvp2/subscriptions/[subscriptionId]`, `/mvp2/subscriptions/new` | 현행 화면 컴포넌트로 연결되는 호환 경로 | C |
| 마이 | `/my` | 주문 요약, 재주문 시점, 정기배송 요약 | C |
| 펫 | `/pets` | 프로필 생성·수정, 펫 유형 불변 | C |
| 알림 | `/notifications` | 인앱 알림 목록·읽음 상태 | C |
| 배송지 | `/addresses` | 인증, 목록·추가·수정·기본값 | C |
| 결제수단 | `/billing-methods` | 인증, 목록·등록·기본값 | C |
| 인증 | `/login` | 안전한 `returnTo`, 세션·CSRF 수명주기 | C/D |
| 신뢰·지원 | `/notice`, `/faq`, `/support`, `/shipping`, `/returns` | 정적 신뢰 콘텐츠 | C/D |

`/admin/catalog`, `/admin/catalog/products/[productId]`, `/admin/operations`는 확인했지만 고객 경험 범위에서 제외한다.

### 실제 컴포넌트·API·상태 감사

| 소비 영역 | 확인한 현재 컴포넌트·client | 인증·상태 계약 | 설계 영향 |
| --- | --- | --- | --- |
| 공통 인증 | `AuthProvider`, `useAuth`, `auth-context.tsx` | `loading/authenticated/anonymous/error`, auth generation stale guard, CSRF lifecycle | 인증 확인 전 empty flash 금지, 세션 만료 시 안전한 GET 복귀 |
| 공개 catalog | catalog card/discovery/filter, `ProductDetailScreen`, `ComparisonScreen`, `api.ts` | 상품 목록·상세·리뷰·Q&A·category·discovery는 public | 공개 API 실패를 로그인 문제로 오표현하지 않음 |
| final product | recommendation, interaction, reorder timing, order subscription options, review summary, compare, `final-product-api.ts` | 추천 fallback, raw query 비저장, 2–3개 비교 | 개인화 실패 격리, 비교 최대 상태, 추천 자동 명령 금지 |
| 거래 | cart/wishlist/order/notification/address/checkout 컴포넌트, `commerce-final-api.ts` | cart version, checkout idempotency, quick reorder added/skipped, Toss confirm/billing | conflict·unknown payment·partial success를 별도 상태로 표현 |
| 정기배송 | `Mvp2Subscription*` 화면, pet/subscription components, `v2-api.ts` | ETag/If-Match, idempotency, nextDelivery/pendingChange/issue/availableActions | 날짜/주기 command 분리, stale 상태 자동 재실행 금지 |
| 검색·목록 history | catalog query parser와 experience tests | URL parsing, stale request guard, back/forward state 기반 존재 | URL을 단일 권위로 유지하고 scroll/focus 복원을 추가 |
| 로그인 | `LoginForm`, `sanitizeReturnTo` | same-origin 내부 GET만 허용 | form draft·POST·결제를 로그인 후 자동 replay하지 않음 |

### 현재 Draft FE-004 감사

`origin/feat/fe/MVP4-FE-004`는 최신 main 대비 34개 파일, 약 1,236 insertions/181 deletions의 Draft 변경으로 확인했다. 주소, 결제수단, 장바구니, 결제, 결제 성공, 마이, 홈, 상품 목록 화면과 공통 commerce 컴포넌트·CSS 및 `MVP4-UX-003-commerce-layout-correction.md`를 포함한다.

| Draft 의도 | 감사 판정 | MVP4-UX-004 기준 |
| --- | --- | --- |
| 1,480px 확장 셸 | 일부 보존 | PLP의 넓은 밀도는 유지하되 route별 1,440/1,320/1,180/760px로 대체 |
| 평면적 여백·divider와 card 축소 | 보존 | 독립 객체에만 card를 사용하고 거래/계정 목록은 행 중심 |
| compact control과 dense PLP | 조정 | hit target 44px, 5/4/3/2/1열 및 mobile drawer 계약 적용 |
| 연속형 PDP | 보존 | gallery/purchase 7/5, 하단 anchor, 독립 async section, mobile sticky CTA 추가 |
| 주요 거래·계정 화면 CSS 변경 | 재평가 필요 | 문서 승인 뒤 별도 Frontend 작업에서 A–D 상태·focus·conflict 기준으로 diff 재산정 |

Draft는 자동 폐기하거나 소급 수정하지 않는다. 승인 전에는 이 디자인을 Draft 코드에 반영하지 않으며, 승인 후 Frontend 역할이 충돌과 재사용 가능 범위를 판단한다.

## 권위 관계와 변경 판정

| 기존 결정 | 판정 | 새 기준 |
| --- | --- | --- |
| UX-002 `Warm Utility Commerce` | `ADAPT` | 따뜻함과 실용성은 유지하고 반려생활의 반복 주기를 드러내는 `Warm Routine Commerce`로 구체화 |
| cream/green 기반 색상과 과도하지 않은 장식 | `ADOPT` | 색 역할·대비·상태 토큰을 명시적으로 고정 |
| 서버 권위 상태와 안전한 인증 복귀 | `ADOPT` | 모든 낙관적 UI·재시도·중복 제출 계약에 적용 |
| UX-003의 큰 카드·과도한 여백 축소 | `ADOPT` | 상품/거래 화면의 평면적 정보 밀도로 반영 |
| UX-003 visual spec의 1,200–1,280px 중심 셸 | `SUPERSEDE` | 탐색 1,440px, 상세 1,320px, 거래 1,180px로 목적별 분리 |
| FE-004 correction의 1,480px 단일 셸 | `SUPERSEDE` | 넓은 PLP 의도만 유지하고 라우트별 폭을 사용 |
| 데스크톱 4열 고정 상품 그리드 | `ADAPT` | 1440px 이상 4열, 1200–1439 3열, 1024–1199 3열, 768–1023 2열, 600–767 2열, 360–599 2열, 320–359 1열 |
| 모바일 하단 내비게이션 | `PENDING PO` | 5개 항목 후보를 제시하되 승인 전 구현 금지 |
| 모든 내용을 카드로 감싸는 구성 | `REJECT` | 경계선·여백·표면색을 우선하고 카드는 독립 객체에만 사용 |

## 외부 벤치마크 증거 매트릭스

증거 등급은 직접 화면과 상호작용을 확인한 `CONFIRMED`, 공식 설명으로 동작을 확인했지만 실제 계정 UI는 보지 못한 `INDIRECT`, 접근 제한 등으로 확인하지 못한 `UNVERIFIED`다.

| 출처·화면 | 관찰한 구성·동작 | 채택 판정 | 증거 | PawCycle 적용 |
| --- | --- | --- | --- | --- |
| Kurly 홈 | 2단 헤더, 중앙 검색, 카테고리 메뉴, 프로모션과 상품 레일 | `ADAPT` | `CONFIRMED` | 헤더 검색 우선순위와 카테고리 탐색만 채택; 과도한 프로모션은 제외 |
| Kurly Pet PLP `/categories/991` | 좌측 필터, 결과 수·정렬, 1280px에서 249px 폭 3열의 정보 밀도 높은 카드 | `ADAPT` | `CONFIRMED` | 데스크톱 필터 레일과 URL 상태, PawCycle 1440px 4열 상한 |
| Kurly PDP `/goods/1001181670` | 좌측 갤러리, 우측 구매 정보, 옵션·총액·CTA, 하단 앵커 | `ADAPT` | `CONFIRMED` | 연속 PDP와 sticky 구매 요약; 구독 선택은 지원 API가 없어 추가하지 않음 |
| Kurly 상품 장바구니 `/cart` | 전체·개별 선택, 수량, 배송 group, 선택 해제에 따른 0원 합계 동기화 | `ADOPT` | `CONFIRMED` | 선택 집합과 서버 합계를 동시에 표시 |
| Musinsa Search/PLP | filter group 독립 개방, 선택 즉시 URL·결과 수 갱신, 1280px 6열 카드 | `ADAPT` | `CONFIRMED` | URL commit을 채택하고 20px target·6열 과밀은 제외 |
| IKEA Search/Cart/Checkout | 비교 4/5 tray, 수량·삭제·위시, 로그인/guest dialog, 배송→상세→결제 단계 잠금 | `ADAPT` | `CONFIRMED` | 비교 한도는 승인된 3개로 축소; checkout progressive disclosure 채택 |
| PetFriends Home/PDP | 펫 유형·소비 목적 category, 반려 적합성·가격·배송·후기, mobile 찜/담기 | `ADAPT` | `CONFIRMED` | pet commerce 정보 순서와 mobile sticky action 채택 |
| PetSmart Autoship 공식 학습 문서 | PDP 선택, 주기 설정, 다음 주문 변경·건너뛰기·수량·취소, 사전 알림 | `ADAPT` | `INDIRECT` | MVP4가 지원하는 날짜·주기·건너뛰기·취소만 적용; 이메일/푸시·대체품은 제외 |
| Chewy Autoship | 페이지 내용 확인 실패 | `UNSUPPORTED` | `UNVERIFIED` | 관행 추정에 사용하지 않음 |
| Petco Repeat Delivery | 기기 확인 iframe으로 차단 | `UNSUPPORTED` | `UNVERIFIED` | 관행 추정에 사용하지 않음 |

전체 URL, Primary/Secondary 화면 매핑, 접근 제한과 상호작용 기록은 [실제 Commerce 벤치마크 증거](./MVP4-UX-004-benchmark-evidence.md)를 단일 근거로 사용한다. 브랜드를 복제하지 않고 검증된 정보 계층과 상호작용 원리만 사용한다.

## 최종 시각 방향: Warm Routine Commerce

반려동물의 반복 소비를 “상품 판매”가 아니라 “다음 급여·교체·배송을 준비하는 일상”으로 보이게 한다. 시각적 인상은 따뜻하지만 장식적이지 않고, 구매와 정기배송의 상태는 운영 도구처럼 분명해야 한다.

### 색상 역할

| 토큰 | 값 | 역할 |
| --- | --- | --- |
| `canvas` | `#F7F4EC` | 전체 배경 |
| `surface` | `#FFFFFF` | 입력·거래·독립 콘텐츠 표면 |
| `surface-soft` | `#EFE9DA` | 구역 구분과 선택 전 보조 영역 |
| `text-strong` | `#17231D` | 제목·가격·핵심 상태 |
| `text` | `#33443B` | 본문 |
| `text-muted` | `#5F6F66` | 보조 설명·메타데이터 |
| `brand` | `#1F6B4F` | 주 CTA·활성 링크·포커스 보조 |
| `brand-hover` | `#18563F` | hover/pressed |
| `accent-text` | `#A14600` | 할인·주의 텍스트 |
| `accent-soft` | `#FFF1E6` | 할인·주의 배경 |
| `success` | `#257A4D` | 완료·정상 |
| `warning` | `#9A5A13` | 보류·확인 필요 |
| `danger` | `#B42318` | 실패·파괴 행동 |
| `border-soft` | `#D7D8D1` | 장식·section 경계 |
| `border-control` | `#727E77` | 입력·버튼 외곽선 |
| `selected-soft` | `#DCEFE6` | 선택·활성 배경 |
| `disabled-surface` | `#ECEFEB` | 비활성 control 배경 |
| `disabled-text` | `#6A746F` | 비활성 control 텍스트 |
| `focus` | `#0B63CE` | 모든 키보드 포커스 2px 외곽선 |

색만으로 상태를 구분하지 않는다. 상태는 아이콘, 짧은 제목, 설명을 함께 가진다. 계산된 대비는 `text-strong/canvas 14.76:1`, `text/canvas 9.41:1`, `text-muted/canvas 4.83:1`, `brand/white 6.41:1`, `brand-hover/white 8.60:1`, `accent-text/white 6.19:1`, `success/white 5.29:1`, `warning/white 5.46:1`, `danger/white 6.57:1`, `focus/white 5.69:1`, `border-control/white 4.23:1`, `disabled-text/disabled-surface 4.17:1`이다. 정상 본문 4.5:1·큰 텍스트와 비텍스트 control 3:1을 하한으로 삼고, 실제 구현 조합도 동일 방식으로 재검증한다.

### 타이포그래피와 밀도

- 서체: 현재 시스템 sans-serif 스택을 유지해 새 의존성을 만들지 않는다.
- 크기: `12 / 14 / 16 / 18 / 20 / 24 / 30 / 38 / 48px`.
- 본문: 16px/1.6, 보조 14px/1.5, 라벨 14px/1.3, 가격 20–30px/1.2.
- 굵기: 400 본문, 500 라벨, 600 섹션·버튼, 700 가격·핵심 제목. 긴 본문 전체에 700을 쓰지 않는다.
- 본문 한 줄은 데스크톱 65–75자, 도움말은 55–65자로 제한한다.

### 형태, 표면, 아이콘, 이미지

- radius: 입력·버튼 8px, 상품/독립 객체 10px, 대형 미디어 16px, 상태 pill은 완전 원형.
- shadow: 팝오버·drawer·sticky 겹침 구분에만 사용한다. 일반 섹션은 경계선 또는 여백으로 구분한다.
- 아이콘: 20px 기본, 16px 메타, 24px 핵심 조작. 선 두께와 라벨 위치를 통일하며 이모지를 기능 아이콘으로 사용하지 않는다.
- 상품 이미지: 카드 1:1 `object-fit: contain`, 상세 갤러리 4:5 안의 1:1 안전 영역. 실제 상품과 다른 장식 이미지를 대체 사진으로 쓰지 않는다.
- 반복 목록은 카드보다 행을 우선한다. 카드 전체 클릭과 내부 버튼이 충돌하지 않도록 제목 링크와 명시적 CTA를 분리한다.

### 간격과 모션

- spacing: `4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64 / 80px`.
- control 높이: 기본 44px, 핵심 CTA 48px, 모바일 입력·CTA 52px. 터치 목표는 최소 44×44px.
- motion: micro 80ms, control 140ms, overlay 220ms. 속성은 opacity/transform만 사용한다.
- `prefers-reduced-motion`에서는 자동 재생과 공간 이동을 끄고 즉시 전환 또는 80ms 이하 opacity만 허용한다.

## 레이아웃 시스템

| 컨텍스트 | 최대 콘텐츠 폭 | 열·간격 | 주요 사용 |
| --- | --- | --- | --- |
| 탐색·PLP | 1,440px | 12열, 24px gap | 홈, 상품 목록 |
| 상품 상세 | 1,320px | 12열, 24px gap | PDP, 비교 |
| 거래·계정 | 1,180px | 12열, 24px gap | 장바구니, 결제, 주문, 정기배송 |
| 읽기·폼 | 760px | 단일 열 | 로그인, 지원 콘텐츠, 단일 편집 폼 |

breakpoint, gutter, container, PLP 열 수, header와 sticky 충돌 규칙의 단일 SSOT는 [D8 반응형 기준](./MVP4-UX-004-d-shared-responsive-accessibility.md#d8-반응형-기준)이다. A–C는 숫자를 재정의하지 않는다.

## 공통 상호작용 계약

### URL, 검색, 필터, 뒤로가기

1. URL은 제출된 검색어, 적용된 필터·정렬·페이지의 단일 권위다.
2. 검색 입력 중 draft는 로컬 상태이며 Enter 또는 검색 버튼에서만 `/products?q=…`로 `pushState`한다.
3. 데스크톱 필터는 변경 즉시 한 번의 적용 이벤트로, 모바일 drawer는 `적용` 버튼에서 묶어서 `pushState`한다. `초기화`도 별도 history entry다.
4. 뒤로가기는 이전 결과·정렬·페이지와 스크롤·출발 상품 포커스를 복원한다. URL 밖에 원시 검색어를 별도 저장하지 않는다.
5. 결과 갱신 중 현재 목록을 지운 채 전체 spinner를 띄우지 않고 `aria-busy`, 상단 progress, skeleton을 사용한다. 실패하면 마지막 성공 결과를 유지하고 재시도한다.

### 오버레이

- modal: 열 때 trigger를 저장하고 제목으로 포커스를 이동한다. Tab을 내부에 가두며 Escape와 명시적 닫기 버튼을 제공한다. 닫으면 trigger로 복귀한다.
- drawer: 모바일 필터·메뉴에만 사용한다. 배경 스크롤을 잠그고, 닫기·초기화·적용을 고정 footer에 둔다.
- popover: 계정·카테고리 같은 짧은 선택에 사용하며 외부 클릭과 Escape로 닫힌다. hover만으로 열지 않는다.
- toast: 비파괴 성공 확인에만 사용한다. 오류 해결 행동과 영구 상태는 inline alert로 남긴다.

### 제출·실패·권위

- 모든 mutation 버튼은 첫 제출 직후 중복 클릭을 막고 진행 라벨을 노출한다.
- 결제·구독 명령·재주문은 동일 의도에서 idempotency key를 유지하고 terminal 결과 후에만 새 키를 만든다.
- optimistic UI는 서버가 확인하기 전 `저장됨`으로 표현하지 않는다. 충돌 시 서버 최신 상태, 사용자의 시도, 다시 적용 행동을 함께 보여준다.
- 인증 만료는 저장되지 않은 입력을 자동 전송하지 않는다. 안전한 GET 복귀 경로만 전달하고 재인증 후 사용자가 다시 확인한다.

## 화면별 문서 번들

- [A. 탐색·구매 진입](./MVP4-UX-004-a-discovery-purchase-entry.md)
- [B. 장바구니·결제·주문](./MVP4-UX-004-b-cart-checkout-orders.md)
- [C. 정기배송·계정·지원](./MVP4-UX-004-c-subscription-account.md)
- [D. 공통 컴포넌트·반응형·접근성](./MVP4-UX-004-d-shared-responsive-accessibility.md)
- [실제 Commerce 벤치마크 증거](./MVP4-UX-004-benchmark-evidence.md)
- [Visual + Interaction Correction](./MVP4-UX-004-visual-interaction-correction.md)

각 화면 계약의 `필수 상태`는 loading, empty, error, success, retry와 필요 시 authenticated/anonymous, stale/conflict를 포함한다. 구현자는 일부 상태를 생략하거나 임의의 modal/card 패턴으로 바꾸지 않는다.

## 현재 구현 및 Draft와의 갭

| 영역 | 현재 확인 | 구현 영향 |
| --- | --- | --- |
| 공통 셸 | 헤더·footer·인증 provider 존재 | 검색 draft/submit, 모바일 메뉴, 포커스 복귀 계약 보강 |
| 홈·PLP·PDP | 공개 API와 상품 카드·발견 컴포넌트 존재 | 라우트별 폭, 밀도, sticky, 결과 상태 및 뒤로가기 포커스 정렬 |
| 장바구니·결제 | 버전·idempotency·Toss 경로 존재 | 2열 거래 셸, 충돌 복구, 단계/CTA 문구와 mobile sticky 적용 |
| 주문·재주문 | 목록·상세·재주문/구독 옵션 존재 | 상태 타임라인, 부분 재주문 결과, 지원 진입점의 정보 구조 재배치 |
| 정기배송 | v2 서버 권위 필드와 액션 존재 | 날짜/주기 분리, pending change·issue·허용 액션 표현 통일 |
| 계정 | 마이·펫·알림·주소·결제수단 존재 | 계정 IA, 행 기반 관리, 모바일 편집 패턴 통일 |
| FE-004 Draft | 34개 파일에서 레이아웃·CSS·주요 화면 변경 중 | 승인 후 이 계약을 기준으로 차이를 다시 산정; Draft를 자동 정답으로 간주하지 않음 |

## `UNSUPPORTED` 목록

- 최근 검색·검색 자동완성 개인화·원시 검색어 저장
- 옵션 선택이 필요한 상품의 무조건 quick add
- PDP에서 신규 정기배송 주기와 결제를 한 번에 확정하는 기능
- 품절 대체품 자동 지정, 배송 예정 보장, 매장 픽업·당일 배송
- 이메일·푸시·SMS 알림, 결제 실패 자동 재시도
- 펫 사진·생일, 상담 채팅, 리뷰 이미지 업로드
- 추천 주기의 자동 적용, 장바구니 상품의 자동 구독 변환

이 항목은 UI를 숨기거나 “준비 중”으로 노출하지 않는다. 제품·API 승인이 생기기 전까지 설계 밖이다.

## 미결 Product Owner 결정과 Recommended Default

| # | PENDING PO | Recommended Default | 실제 근거 | Alternative | Trade-off | 영향 route |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 모바일 하단 내비게이션 | MVP4에서는 도입하지 않음 | IKEA·PetFriends mobile은 header/menu와 context CTA만으로 핵심 구매 흐름 제공 `CONFIRMED` | `홈/쇼핑/정기배송/주문/마이` 5개 | 미도입은 화면 공간·sticky 충돌을 줄이지만 재방문 route 접근이 한 단계 늘어남 | 모든 mobile route |
| 2 | 홈 사회적 증거 | `인기 상품` 한 section | PetFriends의 재구매/인기 근거와 Kurly 상품 rail `CONFIRMED` | `지금 많이 찾는 상품` | 인기는 설명이 안정적이나 실시간성 인상이 약함; 트렌딩은 신선하지만 근거·갱신 설명 필요 | `/` |
| 3 | checkout 최종 CTA 용어 | `결제하기` | IKEA의 실제 결제 진입 CTA와 guest dialog `CONFIRMED` | `주문하기` | 결제하기는 provider 진입을 정확히 알리지만 주문 생성과 결제의 결합을 설명해야 함 | `/cart`, `/checkout`, result |
| 4 | anonymous wishlist | 짧은 inline 안내 후 `로그인하기` | Musinsa wishlist empty가 로그인 이유를 먼저 설명하고 CTA 제공 `CONFIRMED` | 즉시 로그인 redirect | 안내는 맥락을 보존하나 한 번 더 activation; 즉시 이동은 빠르지만 surprise redirect 위험 | Product Card, PDP, `/wishlist`, `/login` |
| 5 | 신규 구독 주 진입 | 주문 상세의 `정기배송으로 다시 받기` | Petco/PetSmart 공식 설명은 상품/주문 context에서 반복배송 진입 `INDIRECT`; PawCycle order options 보유 | `/subscriptions/new` 독립 진입 | 주문 기반은 eligible context가 명확하나 신규 발견성이 낮음; 독립 진입은 발견성이 높지만 source 선택 단계 증가 | `/orders/[id]`, `/subscriptions/new` |
| 6 | 지원 진입 | footer `/support` 유지+주문·구독 상세 context link 항상 노출 | IKEA 주문조회가 관리·FAQ·문의로 연결 `CONFIRMED` | footer only | context link는 해결 속도가 높지만 상세 화면 밀도 증가; footer only는 단순하나 긴 탐색 필요 | footer, order/subscription detail, `/support` |

PO 결정 전에는 Recommended Default를 프로토타입·검토 기준으로만 사용하고 구현 승인으로 해석하지 않는다.

## 전역 인수 조건

- 모든 감사 라우트가 A–D 중 하나에 연결되고, loading/empty/error/success/retry가 명시된다.
- 320px, 375px, 768px, 1024px, 1440px 및 200% 확대에서 가로 스크롤과 가려진 CTA가 없다.
- 키보드만으로 검색, 필터, 상품 선택, 장바구니, 결제, 구독 변경, 계정 편집을 완료할 수 있다.
- focus 순서가 시각 순서와 같고 modal/drawer 닫기 후 원래 trigger로 복귀한다.
- 검색·필터·정렬·페이지·뒤로가기 상태가 URL과 동기화된다.
- 인증·중복 제출·stale/conflict·부분 성공이 서버 권위 규칙을 위반하지 않는다.
- `UNSUPPORTED` 기능이 구현 요구사항이나 모호한 placeholder로 변환되지 않는다.
- Draft FE-004를 포함한 실제 구현 diff는 이 문서 승인 후 별도 Frontend 작업에서만 다룬다.

## 검증 방법

- 최신 main 라우트·컴포넌트·API client·인증·상태 모델과 문서 매핑을 재검토한다.
- A–D 모든 화면에서 필수 상태, responsive, accessibility, gap, acceptance 기준의 존재를 문서 검색으로 검사한다.
- 상대 링크와 Markdown 구조를 검사하고 `docs/design/**` 밖 변경이 없는지 path-scoped diff로 확인한다.
- 이 작업에서는 UI 자동화·프론트엔드 테스트·운영 검증을 실행하지 않는다.

`NO FRONTEND IMPLEMENTATION PERFORMED`
