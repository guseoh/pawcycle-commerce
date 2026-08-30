# Customer Commerce Screen Redesign · R0 구조 탐색 기록

> **ARCHIVED R0 STRUCTURE ONLY — DO NOT IMPLEMENT DIMENSIONS**
>
> 현재 구현 권위는 [R1 Screen Review](review-r1.md), [R1 Final Check](review-r1-final-check.md), [Customer page families](customer-page-families.md), [Visual Design System](visual-system.md), [Interaction / Responsive Contract](interaction-responsive.md)다. 이 파일은 A안으로 수렴하기 전 구조 탐색의 이유와 기능 경계만 보존한다. R0의 Header 80/56, old blue/citron palette, old Login 56, 옛 radius·control 치수는 **구현 후보가 아니다.**

**A안은 여전히 제안 상태이며 최종 Design Approval이 아니다.** R0의 도형 보드와 문구는 역사적 비교 자료다. R1과 수치·색·컴포넌트 규칙이 충돌하면 R0를 선택하지 않는다.

## 1. 보존하는 기능·데이터 경계

| 사용자 기능 | 확인한 권위 입력 | 유지할 디자인 경계 |
| --- | --- | --- |
| 상품 탐색/필터/정렬 | [API-009](../../api/API-009-mvp4-recommendation-and-product-discovery-api.md), [ProductFilters](../../../frontend/src/lib/api.ts), [ProductController](../../../backend/src/main/java/com/pawcycle/backend/catalog/product/api/ProductController.java) | petType/category/subcategory/brand/facet/min/max/subscribable/purchasable/page/size/sort. brand/category 단일 값, facet counts 없음 |
| PDP/사진/옵션/재고 | [ProductDetail](../../../frontend/src/lib/api.ts), [선택 panel](../../../frontend/src/components/product-purchase-panel.tsx) | 실제 optionGroups/selectedOptions·SKU 구매 가능 값. 임의 할인/옵션·배송 약속 금지 |
| Review/Q&A | [API-010](../../api/API-010-mvp4-product-detail-trust-api.md) | delivered 구매자 review, Q&A 답변 후 수정/삭제 금지. 익명 공개 필드 유지 |
| 인기·맞춤·연관·함께 보는 상품 | [API-012](../../api/API-012-mvp4-final-product-backend-api.md) | endpoint 순서·실제 결과 사용. 0개면 section 숨김, AI 실패로 구매 막지 않음 |
| Cart/Checkout/찜 | [Commerce client 계약](../../../frontend/src/lib/commerce-final-api.ts) | 전체 cart 주문, 서버 pricing/version, CSRF·idempotency. Cart image URL 필드 없음 |
| Login returnTo | [PS-003](../../product/PS-003-ux-product-decisions.md), [sanitizer](../../../frontend/src/lib/frontend-utils.ts) | 허용 내부 GET path만. query/form 복구·자동 mutation 없음 |
| 정기배송 시작 | [API-012 주문별 subscription-options](../../api/API-012-mvp4-final-product-backend-api.md) | 기존 Plan/Pet/주문 경로 유지. PDP에 가짜 월정액·구독할인 buy toggle 생성 금지 |

새 visual은 기존 기능 삭제 승인이 아니다. Compare, pet profile, 주문, 구독, AI 비교/요약, 리뷰/Q&A, 알림 등의 기존 경로는 navigation·관련 section에서 접근 가능해야 한다.

## 2. R0에서 얻은 구조 결론

### Home

현재 Production의 큰 소개 Hero와 반복 안내가 상품보다 먼저 보이는 문제를 확인했다. 구조 결론은 **상품 탐색과 실제 상품 선반을 브랜드 설명보다 우선**하는 것이다. R1은 Hero를 없애고 search/taxonomy/product/reorder 순으로 재구성한다. 상품/추천이 없을 때는 큰 빈 card를 여러 개 쌓지 않는다.

R0 자료: [desktop](visuals/home-desktop.png) · [mobile](visuals/home-mobile.png). **시각/치수는 R1 자료가 우선**한다.

### PLP

현재 Production의 중복 검색과 상시 sidebar form이 결과보다 강한 문제를 확인했다. 구조 결론은 **검색은 Header 한 곳, 필터는 toolbar/popover 또는 mobile drawer, 결과 grid가 주인공**이다. committed filter chip, no-result와 catalog-empty 분리, URL 기반 상태는 유지한다.

R0 자료: [desktop](visuals/plp-desktop.png) · [mobile](visuals/plp-mobile.png). 실제 4열/2열, Header, control 치수는 R1/Responsive가 권위다.

### PDP

Production populated PDP는 직접 검증하지 못했다. API/코드에서 gallery, option, price, stock, cart, wishlist, review/Q&A가 존재함만 확인했다. 구조 결론은 **이미지·상품 식별·확정 옵션 가격·구매 action을 명확히 분리**하고, 정기배송은 일반 구매와 가짜 price toggle로 합치지 않는 것이다.

R0 자료: [desktop](visuals/pdp-desktop.png) · [mobile](visuals/pdp-mobile.png). 실제 배치는 [R1 PDP](review-r1.md#핵심-6개-화면--desktop--mobile)가 우선한다.

### Cart

비로그인 Production은 직접 확인했고 populated Cart는 코드 기반으로 분석했다. 구조 결론은 **상품 행과 서버 권위 금액 summary를 분리**, 수량 draft/apply를 구분, 전체 주문만 제공하는 것이다. Cart response에 image URL이 없으므로 R1 기본은 text-only 행이다.

R0 자료: [desktop](visuals/cart-desktop.png) · [mobile](visuals/cart-mobile.png). 현재 시각 계약은 R1 Cart가 우선한다.

### Login

현재 Production의 중앙 card와 구독 전용 설명이 구매 복귀 맥락을 설명하지 못한다. 구조 결론은 **Commerce shell을 줄인 독립 인증 공간**과 안전한 returnTo 설명이다. R0의 `56h` 같은 Header 숫자는 폐기되었고 R1 compact Header 계약을 따른다.

R0 자료: [desktop](visuals/login-desktop.png) · [mobile](visuals/login-mobile.png). 현재 시각/치수는 R1 Login/Responsive가 우선한다.

### Checkout

R0에서는 독립 high-fidelity 시안이 없었다. 구조 결론만 `배송지 → 주문 상품 → 쿠폰` main과 서버 금액/action rail로 분리했다. R1에서 [Desktop](visuals/r1-checkout-desktop.png)·[Mobile](visuals/r1-checkout-mobile.png), 준비/결제 상태를 별도 계약으로 보강했다.

## 3. R0에서 유지하는 상태 원칙

- catalog empty, no-result, loading, error를 같은 빈 화면으로 합치지 않는다.
- filter는 URL committed state와 panel/drawer draft state를 구분한다.
- 옵션/수량/가격/재고는 서버 권위값을 사용하며 임의 first SKU·0원·무료배송을 만들지 않는다.
- Cart/Checkout의 수량·가격·version·멱등성 계약을 visual polish보다 우선한다.
- Login 이후 보호 mutation을 자동 replay하지 않는다.
- 정기배송 생성·변경은 주문/Plan/Pet 계약을 보존하며 일반 구매 토글처럼 축약하지 않는다.

상세 interaction은 [Interaction / Responsive Contract](interaction-responsive.md), 주문·구독·관리 화면은 [Customer page families](customer-page-families.md)가 권위다.

## 4. R1로 대체된 항목

다음 R0 항목은 역사 기록일 뿐 구현하면 안 된다.

- cobalt/citron 또는 cream/green 기반 visual token
- Header 80/56 등 R0 shell 높이
- Login 56h Header
- R0 button/control radius·height
- generic package glyph/category glyph
- custom select를 기본 구현처럼 읽을 수 있는 표현
- 5개 대표 화면만을 Design Approval 범위로 보는 문구

현재 Design Approval 범위는 **Home / populated PLP / PDP / Cart / Checkout / Login 6개 Desktop/Mobile + Order Detail / Subscription New / Subscription Detail + 상태/브랜드/최종 스트레스 보드**다.

## 5. Proposal / 후속 검증

| 항목 | 현재 경계 |
| --- | --- |
| 실상품 사진 | 기존 catalog image가 권위. R1 가상 packaging은 Production/PB 승인 아님 |
| Cart thumbnail | 기본 text-only. detail hydration은 별도 FE/API 비용 검토 전 미승인 |
| 새 웹폰트 | 기본 system stack. 별도 asset/font loading 승인 전 추가 안 함 |
| B editorial 캠페인 | merchandising API/운영 콘텐츠가 없어 A와 자동 혼합 금지 |
| 정책/회원가입/비밀번호 복구 | 실제 route·제품정책 없으면 가짜 action 생성 금지 |
| populated Production 최종 확인 | PDP·인증 Cart/Checkout은 구현 후 허용 fixture/Production 증거로 별도 검증 |

이 파일은 **R0 역사 보존용**이다. 구현자에게 전달되는 실제 명세는 R1 문서군이며, R0 값과 R1 값 사이에서 임의 절충하지 않는다.
