# MVP4-UX-005 · R1 최종 승인 전 보정

2026-08-30 / UX/UI Designer / 일반 / 저장소 변경. **A/R1의 정보 구조와 브랜드 방향은 유지한다.** 이번 보정은 새 방향 탐색이 아니라 최종 Design Approval 전에 남은 시각 리스크와 오래된 계약 충돌을 닫기 위한 것이다. Frontend 구현, Ready for review, merge, Production 실행은 승인하지 않는다.

## 1. Multi-brand catalog stress

R1의 가상 PawCycle 패키지 4종은 브랜드 방향을 이해하는 데 유효하지만, 통일된 PB imagery가 실제 multi-brand Commerce보다 화면을 쉽게 정돈해 보이게 할 수 있다. 따라서 별도 [heterogeneous catalog stress board](visuals/r1-catalog-stress.svg)를 추가한다.

![R1 heterogeneous catalog stress board](visuals/r1-catalog-stress.svg)

보드는 R1 palette에 맞춰 재색칠한 상품이 아니라 서로 다른 빨강·파랑·초록·노랑·검정 계열의 패키지, 세로·원통·병·상자 비율, 긴 브랜드명, 두 줄 상품명, 이미지 없음, 구매 불가 상태를 의도적으로 섞은 가상 데이터다. 실제 외부 브랜드나 Production 상품을 재현하지 않는다.

### 판정

- **PASS — R1의 핵심 위계는 PB imagery에 의존하지 않는다.** 상품 사진의 고유 색을 억지로 aubergine/apricot으로 맞추지 않고 white/surface image stage가 배경을 맡는다.
- Product Card의 권위는 `image → brand → product name → selling price → trust/status` 순서다. 상품 고유 색이 강해도 primary action·focus·selected state만 PawCycle brand color를 사용한다.
- 긴 브랜드는 1줄 고정 높이로 잘라 핵심 상품명을 밀어내지 않는다. 실제 구현에서는 brand는 자연 줄바꿈 대신 1줄 ellipsis를 기본으로 하고 전체 값은 accessible name/title 대체가 아니라 실제 DOM text로 접근 가능해야 한다.
- 상품명은 2줄까지 시각 clamp하되 접근 가능한 이름은 전체 문자열을 유지한다. 가격·구매 가능 여부·오류 메시지는 clamp하지 않는다.
- `image=null`은 generic package/동물 사진을 생성하지 않고 같은 image stage 안에서 `이미지 준비 중` text로 복구한다.
- purchasable=false 상품은 상세 탐색을 막지 않으며, 이미지 색상만 흐려 구분하지 않고 `현재 구매 불가` text/status를 함께 표시한다.
- 이 보드는 정적 시각 스트레스 검토다. 실제 카탈로그의 다양한 aspect ratio, 투명 PNG, 긴 한글/영문 혼합, 200% zoom에서의 최종 PASS는 FE 구현 후 실제 fixture로 검증한다.

## 2. Orbit mark small-size check

R1의 두 궤도 mark는 큰 wordmark에서는 브랜드 서명으로 기능하지만 작은 크기에서 `00`, 안경, 단순 Venn diagram처럼 읽힐 위험이 있다. [small-mark board](visuals/r1-small-mark.svg)에서 32/24/20/16px를 분리해 검토한다.

![R1 orbit small mark board](visuals/r1-small-mark.svg)

### 판정

- 32px: desktop wordmark 사용 가능.
- 24px: compact wordmark 사용 가능.
- **20px: UI에서 허용하는 mark-only 최소 크기.** mobile Header의 mark+PawCycle text 조합은 20px 이상 mark를 사용한다.
- **16px: R1 두 궤도 mark 사용 금지.** 형태 식별 여유가 부족하므로 favicon을 새 orbit mark로 강제 교체하지 않는다. 별도의 simplified favicon이 승인되기 전에는 기존 favicon asset을 유지한다.
- 두 궤도는 category, success, subscription status 같은 기능 icon으로 재사용하지 않는다. 브랜드 서명과 기능 icon의 의미를 분리한다.
- 실제 브라우저 font/icon rasterization, 고DPI, pinned tab, favicon rendering은 정적 SVG로 PASS 처리하지 않고 구현 후 확인한다.

## 3. R0 stale contract 정리 원칙

R0 `screen-redesign.md`는 구조 탐색 기록만 남기고 **구현 치수의 권위를 제거한다.** Header 80/56, Login 56, old citron/blue 등의 R0 수치를 구현자가 선택할 수 있는 대안처럼 남기지 않는다.

현재 구현 권위는 다음 순서다.

1. 제품/API/도메인 승인 계약
2. `review-r1.md`의 핵심6개 화면과 R1 브랜드 방향
3. `customer-page-families.md`의 주문·구독·관리 화면 계약
4. `visual-system.md`의 R1 token/component
5. `interaction-responsive.md`의 R1 interaction/breakpoint
6. `screen-redesign.md`는 **ARCHIVED R0 STRUCTURE ONLY — DO NOT IMPLEMENT DIMENSIONS**

문서끼리 수치가 충돌하면 R0를 선택하지 않는다. R1에서도 충돌이 새로 발견되면 구현자가 임의 선택하지 않고 Design Approval 전에 정정한다.

## 4. 최종 Design Approval 전에 남는 항목

- A/B/C 중 실제 구현 방향 선택. 현재 권고는 A/R1이나 사용자 승인 기록은 아직 없다.
- R1 핵심6개 Desktop/Mobile과 Order Detail/Subscription New/Subscription Detail의 실제 시각 승인.
- heterogeneous catalog stress 결과와 orbit 20px 최소 크기/16px 금지 규칙 승인.
- 실제 상품 이미지 정책은 카탈로그 권위 데이터를 따르며 가상 PB imagery는 Production asset이 아니다.
- Production populated PDP·인증 Cart/Checkout은 여전히 UNVERIFIED다. 이는 디자인 문서의 직접 구현 승인을 자동 차단하지는 않지만, 구현 후 Production/fixture QA에서 별도 검증한다.

**이 보정 완료 자체는 Design Approval, FE 착수 승인, Ready 전환, merge, Production 배포 승인이 아니다.**
