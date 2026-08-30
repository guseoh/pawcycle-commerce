# MVP4-FE-004 FE → QA

## 작업 정보

- 작업 ID: `MVP4-FE-004`
- 작업 등급: 일반
- 실행 구분: 저장소 변경
- 기준: `origin/main` `b6b710661eaf66f6c28bbfd7c55845f5d5a9e86c`
- 감사 일시: 2026-08-30 KST
- 감사 환경: 연결된 Chrome in-app browser, 실제 Production HTTPS, viewport `1440×1000`와 `390×844`

## 감사한 화면

- Home `/`
- 전체 상품 PLP `/products`
- CAT PLP `/products?petType=CAT` (50개 결과)
- no-result PLP `/products?q=zzzz-no-such-product`
- option PDP `/products/33`
- low-stock PDP `/products/47` (선택 SKU 재고 3개)
- subscription-capable PDP `/products/1`
- mobile filter drawer, mobile navigation drawer

실제 Production 화면과 DOM, click, Escape, focus, URL, viewport 계측을 확인했다. Catalog image, brand, price, availability, option 선택 후 서버 상태가 표시되는지 확인했으며 Production mutation·로그인·주문·결제는 실행하지 않았다.

## 우선순위 판정

- **P0 없음**: Home, 100개 PLP, CAT 50개 PLP, no-result, option/sold-out/low-stock PDP에서 구매·탐색을 막는 오류와 horizontal overflow를 발견하지 못했다.
- **P1 (Production 배포 blocker)**: 390px 모바일 메뉴를 열고 Escape로 닫으면 Production의 `activeElement`가 `body`에 남아 메뉴 trigger로 focus가 복귀하지 않았다. 최신 main의 `AppHeader`에는 `restoreMenuFocus`와 cleanup focus return이 이미 구현되어 있으므로 이번 branch에서는 중복 UI 수정 없이 해당 Escape 경계를 더 구체적으로 regression assertion으로 고정했다. Production 배포 전에는 이 동작을 다시 확인해야 한다.
- **P2 / 보류**: low-stock가 `구매 가능 · 재고 3개`로 표시되는 표현 개선, `PawCycle Demo Catalog` 브랜드 노출, 익명 추천의 인증 필요 안내는 각각 데이터/제품 정책 또는 polish 영역이다. Frontend에서 임의로 숨기거나 의미를 바꾸지 않았다.

## 구현 및 회귀 범위

- `frontend/src/components/mvp4-ux-regression.test.mts`에 모바일 메뉴 Escape가 `restoreMenuFocus` 경로를 설정하는 source contract assertion을 추가했다.
- `frontend/src/components/app-header.tsx` 및 API/도메인 코드는 변경하지 않았다.
- 기존 focus trap은 닫힌 `<details>` descendant를 제외하고, visible/usable control을 순환하며, close 시 trigger focus를 반환한다. Browser Tab cycle은 별도 QA로 유지한다.

## 실제 계측 결과

- Production Home/PLP/PDP: `scrollWidth === clientWidth` at 390px and 1440px (세로 scrollbar 15px 제외)
- PLP 1440: heading bottom `219`, toolbar top `259`, filter trigger top `327`, grid top `387`; 승인 spacing 범위 내 dead space 없음
- PLP compare checkbox: `appearance:none`, 20px control, 44px label target, semantic native checkbox 유지
- Mobile filter: initial focus `닫기`, `닫기` + `Shift+Tab` → `초기화`, `초기화` + `Tab` → `닫기`, Escape 후 filter trigger focus return
- CAT filter: URL `petType=CAT`, selected chip `반려동물: 고양이`, total 50개
- PDP 33: 1kg/1팩 선택 시 `현재 품절 · 구매 불가`, 2kg/1팩 선택 시 `구매 가능 · 재고 44개`
- PDP 47: 1개 선택 시 `구매 가능 · 재고 3개`

## QA 필요 여부

필요하다. 배포 전 실제 Production 또는 동등한 same-origin browser 환경에서 다음을 재확인한다.

1. 390px/320px에서 모바일 메뉴 open → forward/backward Tab cycle → Escape → 메뉴 trigger focus return
2. 1440px/390px Home·PLP·PDP overflow와 이미지 로드
3. no-result, sold-out, low-stock, option required 상태
4. 다른 브라우저/스크린리더와 200% zoom (in-app browser 200% zoom은 이번 작업에서 미실행)

## 남은 위험과 다음 판단

현재 Production은 최신 main의 Escape focus 보정이 배포되지 않은 상태로 보인다. 이 task에서는 Production deploy를 수행하지 않는다. Product/Release owner가 해당 배포를 승인한 뒤 P1 browser QA를 재실행해야 Product Completion blocker가 해소된다. P2 항목은 데이터/제품 결정 없이는 수정하지 않는다.
