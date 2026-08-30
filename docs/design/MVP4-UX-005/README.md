# MVP4-UX-005 Customer Commerce Visual Redesign

- 작업 ID: MVP4-UX-005 / 등급: 일반 / 실행 구분: 저장소 변경 / 역할: UX/UI Designer
- 기준: `main`, `bec817d` (2026-08-30 origin fetch 확인, PR #255 포함)
- 작업 branch: `design/ux/MVP4-UX-005`, 별도 worktree. 기존 FE branch·고유 commit·미추적 파일 보존.
- 승인 입력: Production/외부 Commerce 감사와 R1 보정 완료 후 2026-08-30 사용자 + ChatGPT Design Approval.
- 상태: **DESIGN APPROVED — A/R1 Daily Orbit 선택. Ready/merge/Frontend 구현/Production 승인은 별도다.**
- 승인 대상 설계 snapshot: `11854358e1e950e37e919e053441779287958e92`
- 작성일: 2026-08-30, Asia/Seoul. Production은 공개 페이지 조회와 탐색만 수행했다. 운영 데이터 변경·로그인 제출·주문·결제·배포 없음.

## R1 승인본

**[1차 리뷰 보정과 6개 high-fidelity 화면](review-r1.md)** → **[R1 최종 승인 및 보정 기록](review-r1-final-check.md)** → **[Customer 전체 family와 주문·구독 핵심 계약](customer-page-families.md)** → [R1 상태 보드](visuals/r1-states.png). **A/R1 Daily Orbit을 구현 방향으로 선택했다.** 아래 A/B/C 표·비교 보드는 탐색 근거를 보존하기 위한 R0 기록이며 B/C를 현재 구현에 혼합하지 않는다.

## 읽는 순서와 근거

1. [Production Visual Audit](production-audit.md): 직접 확인한 문제와 확인하지 못한 범위.
2. [External Commerce Benchmark](commerce-benchmark.md): 실제 rendered page, 채택·변형·거부 판정.
3. 이 문서의 세 가지 Visual Direction 비교 기록.
4. [Visual Design System](visual-system.md): 승인된 A/R1 token·component 계약.
5. [R1 Screen Review](review-r1.md): Home / populated PLP / PDP / Cart / Checkout / Login 6개 Desktop/Mobile.
6. [R1 Final Check](review-r1-final-check.md): multi-brand catalog stress, orbit small-size, R0 stale contract 정리와 최종 승인 기록.
7. [Customer families](customer-page-families.md): 전체 관리·지원 범위와 Order Detail/Subscription New/Detail 상세.
8. [Interaction / Responsive Contract](interaction-responsive.md): 입력·URL·상태·포커스·5개 너비의 구성.
9. [R0 구조 기록](screen-redesign.md): 역사적 구조 탐색만 보존. **구현 치수 권위 없음.**

시안은 **정적 디자인 문서**이며 상품·가격·개수·개인정보는 가상 예시다. R0 도형 보드는 구조 탐색 기록, R1은 가상 패키지 사진·브랜드 표현을 포함하는 high-fidelity 검토본이다. 생성 imagery는 실제 판매 상품이나 PB 출시 결정이 아니다. Frontend/HTML 앱을 만들지 않았다. 실제 캡처는 `evidence/`, 시안은 `visuals/`, 생성 원본은 `assets/`로 구분한다.

## 왜 기존 설계의 연장이 아닌가

색 변경보다 먼저 아래 구조를 폐기했다. 기능·접근성 계약은 남긴다.

| 기존 기본값 | 승인 판정 | A/R1 구현 방향 |
| --- | --- | --- |
| cream canvas + forest green 전체 체계 | 폐기 | white/surface + aubergine + apricot, ink 중심 |
| 소개 문구 + 우측 정기배송 카드 Hero | 삭제 | Hero 없이 탐색과 상품 선반 우선 |
| 반복되는 대형 둥근 section card | 삭제 | 열린 product grid와 구분선, 필요한 panel만 제한 사용 |
| 검색·브랜드·계정 + 별도 일반 메뉴 2단 Header | 재구성 | 단일 검색 중심 masthead와 결과 맥락 |
| PLP 고정 왼쪽 form sidebar | 삭제 | 상단 filter toolbar/popover, mobile drawer |
| 브라우저 기본 select/fieldset 외형 | 삭제 | styled native select / radio·checkbox / filter chip, native semantics 보존 |
| Login 가운데 테두리 카드 + 전체 Commerce shell | 삭제 | 전용 인증 shell, 복귀 맥락과 form 중심 |
| Home Help 구획과 Footer 링크 중복 | 통합 | Footer 지원 입구 1개 + 정책/쇼핑/계정 위계 |

## 세 가지 Visual Direction — A/R1 선택 완료

R0에서 A/B/C를 독립 탐색했고, R1에서 A를 브랜드·화면·상태 수준까지 보정했다. **현재 구현 방향은 A/R1 Daily Orbit이다.** B와 C는 비교 근거로만 보존한다. C mobile bottom dock는 기존 MVP4 PO 결정과도 충돌하므로 현재 구현에 포함하지 않는다.

![세 방향의 Home 구조와 시각 언어 비교](visuals/directions.png)

동일 기능 범위·동일 시안 상품으로 비교했다. 서비스 규모가 크거나 개인화 데이터가 있다는 가정은 하지 않는다.

| 비교 항목 | A · Clear Supply / R1 Daily Orbit | B · Pet Edit / 편집형 쇼룸 | C · Daily Club / 일상형 상점 |
| --- | --- | --- | --- |
| Palette | **R1 승인:** white `#FFFFFF`, ink `#241C2E`, aubergine `#4B286D`, apricot `#F3B88F`, surface `#F3F0F7` | white `#FFFFFF`, graphite `#19191C`, plum `#6B3157`, mist `#F0EDF2` | white `#FFFFFF`, navy `#202850`, coral `#C74352`, lilac `#EEE9FF` |
| Typography | system sans, 명확한 한국어 hierarchy와 tabular price | sans 본문 + 시스템 명조 display | sans 중심, 친근한 label hierarchy |
| Home layout | Hero 없음. 검색 → 종별·카테고리 → 상품 4열 → 재구매·개인화 → 짧은 배송 안내 | 편집 타이틀 + 큰 상품 사진 중심 | 종 선택 → 빠른 카테고리 → 해당 종 상품 |
| PLP layout | 상단 filter bar + 4열, 결과가 주인공 | filter sheet + 3열 큰 사진 | 종별 segment + category + 3열 |
| Product presentation | 1:1 neutral stage, 상품명2줄, 판매가 우선, 최소 badge | 4:5 큰 이미지, 편집 여백 | 1:1 이미지, 상태·가격 강조 |
| Navigation | 한 줄 검색 중심 Header, 전체 카테고리 panel | 축약 masthead, search overlay | dog/cat 지속 + mobile dock |
| Component style | radius8 중심, product card 외곽 없음, aubergine focus/action | 얇은 hairline, 낮은 radius | soft fill, 큰 radius, 선택 check |
| Density | 중간~높음, 상품 탐색 우선 | 낮음~중간, editorial 우선 | 중간, 목적별 진입 우선 |
| Brand mood | **선택됨:** 정확한 구매·재구매 도구 + Daily Orbit 브랜드 서명 | 취향과 상품 이해의 편집 상점 | 친근한 반려생활 앱형 상점 |
| 위험 / trade-off | 다양한 실제 상품 이미지에서도 hierarchy 유지 필요 → stress board PASS, 실제 구현 QA 남음 | merchandising/이미지 운영 기준 필요 | bottom dock와 구매 CTA 경쟁, 기존 PO 결정 충돌 |
| 현재 상태 | **DESIGN APPROVED** | 미선택 | 미선택 |

### 선택 판정

**A/R1 Daily Orbit을 최종 Visual Direction으로 선택한다.** 좁은 카탈로그에서도 검색과 구매 판단을 우선할 수 있고 신규 merchandising 운영 기능 없이 성립하며, R1 보정을 통해 generic utility shop 위험도 줄였다. B/C의 장점을 임의 혼합해 절충하지 않는다.

A/R1 승인본은 핵심6개와 주문·구독3개 화면, heterogeneous catalog/orbit small-size 최종 검토 보드를 포함한다. 실제 상품 데이터·브라우저 interaction·Production 인증 구매 흐름은 구현 후 별도 검증한다.

## 기능 보존과 문서 권위

사용자 요청 > 승인 제품·API 조건 > 본 승인 설계. [PS-003 로그인 복귀·공개 탐색 결정](../../product/PS-003-ux-product-decisions.md), [API-009 탐색](../../api/API-009-mvp4-recommendation-and-product-discovery-api.md), [API-010 PDP/Review/Q&A](../../api/API-010-mvp4-product-detail-trust-api.md), [API-012 추천·반복 구매](../../api/API-012-mvp4-final-product-backend-api.md)를 제약으로 사용한다.

- [이전 Visual spec](../MVP4-UX-003-visual-design-spec.md)의 cream/green 보존·Home hero·sidebar 관련 시각 조항은 **A/R1 승인 범위에서 superseded**다. 인증·도메인·API·상태 안전 계약은 계속 유지한다.
- 사용자 명칭 `UX-004`와 별개로 latest main의 실제 디자인 파일명은 `MVP4-UX-003-*`다. 존재하지 않는 파일을 승인 원본으로 만들지 않는다.
- `ui-ux-pro-max`는 시각 방향의 권위가 아니다. focus, contrast, responsive, 상태 누락 점검 보조에만 사용한다.
- R0 `screen-redesign.md`는 구조 탐색 기록이며 구현 치수 권위가 없다. R1과 충돌하면 R0 값을 사용하지 않는다.
- 가상 PB imagery는 실제 카탈로그 asset 결정이 아니며 실제 상품 사진을 PawCycle palette로 재색칠하지 않는다.

## Visual Approval Gate

| 승인 항목 | 검토 자료 | 상태 |
| --- | --- | --- |
| A/B/C 방향 선택 | 비교 보드·표 | **APPROVED — A/R1** |
| Header·Hero 삭제·탐색 순서·Footer 통합 | Home, PLP, Login Desktop/Mobile | **APPROVED** |
| 색·폰트·숫자·버튼·필터·선택 상태 | Visual System + state board | **APPROVED** |
| 상품 있음/없음의 균형 | R1 핵심6개 + 주문·구독3개 + 상태 보드 | **APPROVED** |
| multi-brand catalog 내구성 | [heterogeneous catalog stress](visuals/r1-catalog-stress.svg) | **APPROVED — 정적 PASS, 실제 fixture QA 후속** |
| orbit mark 소형 식별성 | [32/24/20/16px board](visuals/r1-small-mark.svg) | **APPROVED — 최소20px, 16px 금지** |
| 320/375/768/1024/1440 구성 | Responsive Contract·치수 표 | **APPROVED AS CONTRACT — 구현 후 실제 reflow 검증** |
| 상품 사진 / 폰트 asset 정책 | system font, 실제 이미지는 catalog 권위 source | **APPROVED WITH LIMIT** |
| 조사 제한 | populated Production PDP·인증 Cart/Checkout 미검증 | **ACCEPTED LIMITATION — 구현 후 QA** |
| 최종 Design Approval | [R1 최종 승인 기록](review-r1-final-check.md) | **DESIGN APPROVED** |

사용자 Screenshot 원본은 Codex 작업 환경에 미첨부였으나 직접 Production 재캡처로 핵심 대조가 충족되어 승인 blocker가 아니다. Production populated PDP·인증 Cart/Checkout 미검증은 구현 후 검증 항목으로 유지한다.

## 후속 Gate

Design Approval은 완료됐지만 다음 승인을 자동 포함하지 않는다.

- PR #256 Ready for review 전환
- PR #256 merge / main 변경
- Frontend 구현 작업 시작·PR 생성·merge
- Production 배포·운영 DB·catalog import

다음 저장소 작업은 최신 main과 승인된 A/R1 설계를 기준으로 별도 coherent work unit으로 진행한다. 자동 merge하지 않는다.
