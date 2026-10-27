# G6 Frontend fixture verification

2026-10-26 · UIUX-DESKTOP-468 · Repository Change · 일반

기준 main `3ea52d399c26ece4c3063666a756a2ec5658e3cd` · Issue #468 / #465

**Fixture Verified / Real Auth Unverified.** G6-00 실제 인증 환경은 Blocked다. 아래 결과는 loopback 합성 API와 실제 Chrome 렌더링에만 해당한다. 실제 주문·결제·주소·프로필·구독의 서버 통합 성공을 뜻하지 않는다.

## 목적

Issue #468의 G6-01~G6-07에서 승인한 고객 데스크톱 UI·접근성 Delta를 구현하고, 기존 Commerce 계약을 보존하는지 합성 Fixture와 회귀 테스트로 검증한다. Backend/API/DB·제품 정책·실제 인증 및 결제·업무 상태 변경은 범위에서 제외한다.

## Task 결과

| Task | 구현 | 관련 검증 |
|---|---|---|
| G6-01 | 주소 dialog를 제목/스크롤 body/action footer로 분리. 첫 입력·오류 focus, Tab/Shift+Tab 순환, Escape 복귀. Pet 편집 진입 및 취소·저장 완료 trigger 복귀 | 최초 회귀 2개. 실제 Chrome 3폭 footer, 편집/취소/합성 저장·실패 보존 확인. 200% 실제 확대는 미실행 |
| G6-02 | 구독 및 billing을 semantic label/value로 분리. 긴 값 wrap, 서버 pagination 보존 | 관련 회귀+구독 API 9개. 미설정/등록/긴 값 표본 |
| G6-03 | 중립 AuthRequiredState와 조회 오류 alert 구분. CTA 인접 빈 cart/주소 없음/구매불가 사유 | 관련 Commerce UX 포함 32개. 3개 차단 사유, anonymous/auth-error/query-error, safe return |
| G6-04 | 플랜명 포함 선택 버튼. 현재/다음/예정 변경 비교. 주문 예정일 명칭 정돈 | 구독 API 포함 13개. Enter 선택, 현재4주/다음8주, PAUSED/null |
| G6-05 | 기존 주문 관리 영역을 상태 직후로 이동, 보조 구독 제안 강조 축소 | 주문 계약 포함 20개. availableActions 없음에서 취소/반품 버튼 없음. 실제 P1 결함은 확정하지 않음 |
| G6-06 | 상품/옵션/서버 단가/행 금액, 수량 조작 정렬 | Commerce API 포함 16개. draft는 자동 적용되지 않으며 합성 명시 적용 후만 변경 |
| G6-07 | 찜한 상품 명칭, 짧은 보기 버튼+전체 accessible name, 중복 주문 link 축소, 0건 알림 행동 축소 | 관련 Commerce UX 포함 37개. 합성 찜 해제·undo/알림 빈·채움. 로그인 보조 레이아웃은 가치가 분명한 Delta가 없어 유지 |

각 Task 검사 숫자는 누적 관련 suite 실행 건수이며 합산한 고유 테스트 수가 아니다. 새 G6 회귀는 source/markup 계약 검사가 중심이고, focus의 실제 DOM 동작은 아래 브라우저 기록으로 보완했다.

## 최종 검사

- `npm run lint`: PASS, 0 errors / 기존 warnings 6. 최종 주소·회귀·Fixture 파일 lint도 PASS.
- `npm run typecheck`: PASS. 후속 주소 보정을 포함한 최종 build의 TypeScript 검사 PASS.
- `npm run test`: **194 / 194 PASS** (최종 모달 보정 및 Fixture 경계 검사 포함).
- `npm run build`: PASS, 28 경로 생성.
- 기존 Commerce/구독 API 계약 테스트 포함. `src/lib/**`, Backend/API/DB 파일 변경 없음.
- `git diff --check`: PASS.
- 자체 변경 코드 점검 완료. 독립 review와 동일시하지 않음.

## 브라우저

- Chrome, CSS viewport 1440×900 / 1280×800 / 1920×1080.
- 로그인은 anonymous, 나머지 화면은 member **합성 상태**로 14경로 × 3폭 = **42개 정상 표본**. DOM 준비·networkidle 후 캡처. 가로 overflow 0, 정상 캡처의 로딩 문구 0.
- 주소 modal footer는 1280×800에서도 화면 안에 위치(관찰 bottom 약751px). 내부 body만 scroll. Tab/Shift+Tab/Escape와 최초 focus/복귀 확인.
- 초기 last-Tab→BODY 문제를 발견해 보정했고 최종 Tab/Shift+Tab은 PASS. 최초 실패를 성공으로 덮어 기록하지 않고 로컬 browser-results.json에 보정 관계를 남겼다.
- 긴 텍스트 7화면, 6개 계정 empty 표본, auth/API query 오류, 로딩, 서버 허용 행동 없음, PAUSED, billing configured/registered, populated 알림 확인.
- 메모리 내 합성 pet PATCH·주소 저장 실패·수량 적용·찜 해제/되돌리기·로그인 복귀만 확인. Fixture는 API를 Backend로 전달하지 않고 알 수 없는 API/업무 변경을 차단한다.
- **실제 browser 200% zoom NOT_RUN**: 단축키가 viewport/DPR을 바꾸지 않았다. 640×400의 모달 reflow 등가 표본은 PASS지만 실제200% 검증으로 간주하지 않는다.
- 일부 초기 캡처가 로딩 프레임이어서 정상42개는 준비 완료 후 다시 수집했다. explicit clip의 timeout은 기본 viewport screenshot 방식으로 해결했다.

## 대표 before/after

모두 합성 데이터. Before는 이전 T18 audit의 같은 main SHA/1440×900 CSS viewport다. 기본 상품/가격/주문·구독 표본을 메모리 Fixture 재시작으로 동일하게 맞춘 After를 사용했다. 제품 원본이나 정책 변경 증거가 아니다. API/API data의 사실을 교정한 것으로 해석하지 않는다.

Before 일부 파일은 브라우저 출력이 1425×891이며 After 캡처는 1440×900 또는 브라우저 viewport 출력 크기다. 픽셀 차분이나 실제 전환율 근거로 사용하지 않는다.

### 대표 이미지 감사 및 미완료 증거 (2026-10-27 Correction)

GitHub에 저장된 JPG 바이너리의 가로·세로 픽셀을 독립 확인했다. 아래 두 After 이미지의 높이는 검수 viewport 높이 900px에 미달한다. 브라우저 렌더링·키보드 검사 보고와 별도로 **해당 대표 이미지만으로 완료 조건을 입증할 수 없다**.

| 항목 | Before JPG | After JPG | 이미지로 확인되지 않은 조건 |
| --- | --- | --- | --- |
| 주소 Dialog | 1440×900 | **1440×599** | 저장/취소 footer 전체 가시성 및 viewport 하단 비가림 |
| 빈 Checkout | 1425×891 | **1425×593** | 차단 사유와 비활성 CTA가 함께 보이는 전체 화면 |

UI 자체에서 재현된 결함으로 확정하지 않는다. 이전 로컬 Chrome DOM 보고(주소 footer bottom 약 751px)와 저장된 캡처 파일의 증거 범위를 구분한다. **이번 Correction에서는 새로운 스크린샷을 촬영하지 못했으므로 두 After 이미지를 완전한 시각 QA 증거로 승격하지 않는다.** 동일 합성 Fixture·비민감 데이터로 전체 viewport를 재촬영해 새 HEAD에서 확인해야 한다. 200% 실제 브라우저 확대 역시 NOT_RUN이며 640×400 reflow 등가 검사로 대체하지 않는다.

| Task | Before | After |
|---|---|---|
| G6-01 주소 dialog | [보기](address-before-fixture-1440.jpg) | [보기](address-after-fixture-1440.jpg) |
| G6-02 billing | [보기](billing-before-fixture-1440.jpg) | [보기](billing-after-fixture-1440.jpg) |
| G6-03 빈 Checkout | [보기](checkout-empty-before-fixture-1440.jpg) | [보기](checkout-empty-after-fixture-1440.jpg) |
| G6-04 구독 비교 | [보기](subscription-before-fixture-1440.jpg) | [보기](subscription-after-fixture-1440.jpg) |
| G6-05 주문 관리 | [보기](order-before-fixture-1440.jpg) | [보기](order-after-fixture-1440.jpg) |
| G6-06 Cart | [보기](cart-before-fixture-1440.jpg) | [보기](cart-after-fixture-1440.jpg) |
| G6-07 Wishlist | [보기](wishlist-before-fixture-1440.jpg) | [보기](wishlist-after-fixture-1440.jpg) |

전체 74개 After/상태 캡처, viewport/브라우저 검사 기록과 로그는 저장소 외부 `g6-ui/`에 보존했다. 이 PR에는 reviewer가 확인할 대표 7쌍만 넣었다.

## 남은 위험과 복구

- 실제 인증/인가·CSRF 통합, 실결제/provider, 실제 DB 조회/저장·주문/구독 state transition 미검증. G6-00 Blocked 유지.
- 실제 데이터/availableActions에 따라 G6-05 P1 여부는 재판정 필요.
- 실제200% 확대, 스크린리더 낭독, 모든 다건 pagination·상태 조합, 모바일 QA 미실행.
- 기존 로그인/구독 redirect, mutation protocol·Idempotency/ETag 및 서버 금액 계산은 유지.
- API 및 새 dependency/글로벌 CSS import/Production·Cloud·MySQL 변경 없음.
- 복구는 이 PR의 Frontend 및 합성 검증 파일을 일반 revert PR로 되돌리는 범위다. DB/운영 rollback 없음.
