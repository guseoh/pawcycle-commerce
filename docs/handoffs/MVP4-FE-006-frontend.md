# MVP4-FE-006 Frontend 구현·검증 인수인계

- 작업 ID: MVP4-FE-006
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Frontend Engineer
- 다음 소비자: 사용자/Tech Lead 및 후속 Frontend·QA
- 상태: **Draft PR #257 — Ready/merge/Production 실행 없음**
- 기준: fetch 후 `origin/main` = `39b9091c53b958f16eab489f3b674813fb4282d5`
- branch: `feat/fe/MVP4-FE-006`
- Draft PR: #257 `feat(frontend): A R1 Daily Orbit 고객 Commerce 구현`
- 격리 worktree: `C:/Users/guseo/IdeaProjects/pawcycle-commerce/tmp/worktrees/MVP4-FE-006`
- 승인 입력: PR #256, `docs/design/MVP4-UX-005/README.md`와 A/R1 문서. R0/B/C는 구현 권위로 사용하지 않음.

## 변경과 계약 경계

`frontend/src/app/globals.css`, `shopping.css`를 Customer A/R1 token과 composition으로 교체했다. 기존 cream/green 스타일은 Admin 전용 scope와 legacy header/footer로 격리했다. 새 의존성이나 Backend/API 변경은 없다.

검색 중심 Header, Hero 없는 Home 실제 catalog 조회, 상단 PLP popover/mobile drawer, 열린 상품 grid, PDP 이미지·옵션·가격 분리, Cart 행/summary, Checkout 배송→상품→금액, dedicated Login shell을 구현했다. Order Detail과 Subscription New/Detail은 현재 상태·예정 회차·pending change·현재 plan·availableActions를 분리했다. 나머지 Customer family는 같은 shell, navigation, controls와 상태 계층을 사용한다.

Cart operation별 retry, Checkout 주소 returnTo, Wishlist 단일 undo, 주문→구독 진입, 저장 주소 draft와 CHANGE_PLAN 비교/확정 로직은 유지했다. 검색 폼을 Header로 통합하면서 기존 SEARCH interaction 전송도 옮겼다. 단일 SKU 자동 선택 테스트만 현재 사용자의 명시적 선택 계약으로 변경했다. 가격·재고·배송 예정일을 새로 계산하지 않는다.

Product 이미지 fallback과 품절/구매 불가 상태를 적용했다. Home은 기존 product API의 가격을 사용하며, 가격 없는 recommendation 응답에 가상 가격을 넣지 않는다. R1 가상 PB 이미지는 앱에 사용하지 않았다.

ChatGPT correction에서 Customer mutation의 `AUTH_REQUIRED`가 실패한 mutation을 자동 replay하지 않고 인증/CSRF 상태를 익명으로 전환하도록 공통 인증 수명주기를 보강했다. Checkout에서 주소 관리로 이동한 뒤 세션이 만료되어도 `/addresses?returnTo=/checkout` 문맥만 안전하게 로그인 복귀 대상으로 유지하며 임의 query나 외부 URL은 허용하지 않는다. Subscription formatter는 prototype 상속 key를 label로 취급하지 않도록 own-property 조회로 보강했다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| `npm test` | **PASS, 123/123** — 최신 HEAD `66da0b2b3c61545bba5d7ce094c00acceda91e61`에서 사용자 재실행. `AUTH_REQUIRED는 mutation을 재실행하지 않고 익명 전환 뒤 원래 오류를 유지한다` 회귀 테스트 포함 |
| `npm run typecheck` | **PASS** — 최신 HEAD에서 `tsc --noEmit`, 종료 코드 0 |
| `npm run lint` | PASS, 0 errors / 기존 warning 5개 |
| `npm run build` | PASS |
| `git diff --check` | PASS |
| Repository Validation #1468 | PASS — Commit/PR conventions, Harness, Frontend lint/build, Application validation success |
| 320/375/768/1024/1440 렌더링 | 아래 25개 화면·상태, 125회 측정에서 `scrollWidth <= clientWidth`, 화면 오른쪽 넘침 없음 |
| Desktop 필터 취소 | draft 폐기, URL 유지, Escape 후 가격 버튼 focus 복귀 PASS |
| Mobile 필터 | 적용 후 URL `petType=DOG`, Escape focus 복귀, 역방향 Tab 마지막 control 이동 PASS |
| Cart 확인창 | 취소가 기본 focus, 취소/Escape 시 mutation 없음 |
| PDP | 명시적 SKU 선택 전 구매 버튼 disabled, mobile sheet 하단 좌표 = viewport 높이, Escape 닫기 |
| Order/Subscription | 주문에서 호환 플랜 선택 진입, 구독 pet/plan/cycle 선택, 현재 4주와 예정 8주 분리, CHANGE_PLAN 비교/확인창, 저장 배송지 draft UI 확인 |
| reduced motion | 실제 `matchMedia('(prefers-reduced-motion: reduce)').matches === true`; button transition `0s`, animation `none` 확인 |
| Mobile 메뉴 역방향 Tab | **PASS** — 최초 FAIL 원인 보정 후 375px 전체 정·역방향 cycle, 320px 경계 cycle, Escape/focus return 재검증 |
| AUTH_REQUIRED 단위 회귀 | **PASS** — mutation 1회 실행, 익명 전환 1회, 원래 오류 유지 |
| 200% browser zoom | **UNVERIFIED** — in-app browser에서 Ctrl++ / Ctrl+= 후 viewport·devicePixelRatio가 변하지 않음. 720px viewport를 200% zoom으로 대체 주장하지 않음 |

`npm test` 실행 중 Node의 experimental type stripping 및 MODULE_TYPELESS_PACKAGE_JSON warning이 출력되지만 테스트 실패는 아니며 이번 작업에서 package module 체계를 변경하지 않았다. 기존 lint warning 5개도 unrelated cleanup 대상으로 확장하지 않았다.

## Local evidence

루트 기준 `tmp/visual-evidence/`에 로컬 PNG와 `viewport-measurements.json`을 저장했다. binary는 Git에 추가하지 않는다. 캡처 높이는 1000px viewport에서 full-page이며 파일 suffix는 요청 viewport 너비다. 세로 scrollbar가 있으면 실제 client 폭은 15px 작다.

- 핵심: `home`, `plp`, `pdp`, `cart`, `checkout`, `login`, `order-detail`, `subscription-new`, `subscription-detail`
- 빈 상태: `plp-no-result`, `cart-empty`
- Family: `my`, `pets`, `addresses`, `billing`, `notifications`, `wishlist`, `compare`, `orders`, `subscriptions`, `support`, `faq`, `notice`, `shipping`, `returns`
- 각 이름의 `-375.png`, `-1440.png` 캡처가 있다. 320/768/1024는 JSON 측정 근거를 남겼다.
- 추가: `filter-drawer-375.png`, `pdp-options-sheet-375.png`, `subscription-change-confirm-1440.png`
- Production 대조: 승인 UX 작업에 보존된 `docs/design/MVP4-UX-005/evidence/production-plp-1440.jpg`와 비교했다. 이번 FE 작업에서 Production을 새로 직접 캡처했다고 주장하지 않는다.

## Correction 결과: 모바일 메뉴 포커스 순환

원인은 접힌 `<details>`의 `전체 보기` 링크였다. Chrome에서 해당 링크는 `getClientRects().length === 1`과 `tabIndex === 0`을 반환해 기존 목록에 들어갔지만, native Tab 대상이 아니어서 첫 `닫기`에서 Shift+Tab 시 마지막 항목으로 실제 이동하지 않았다.

`drawerFocusable`은 닫힌 details 내부에서는 `summary`만 포함하도록 최소 보정했다. 메뉴 close/Escape에서는 cleanup이 background inert를 해제한 뒤 원래 trigger로 focus를 복귀시키고, drawer 바깥 pointer close는 trigger focus를 강제하지 않도록 정리했다. keydown listener는 effect cleanup에서 제거되므로 open/close 반복으로 누적되지 않는다.

Chrome local fixture에서 다음을 PASS로 재검증했다.

- 375px: 15개 실제 focusable 순서 전체 forward Tab 및 backward Shift+Tab cycle, Escape, 메뉴 trigger focus return
- 320px: `닫기` + Shift+Tab → `목욕·케어`, `목욕·케어` + Tab → `닫기`, Escape → 메뉴 trigger focus return
- 375px drawer 바깥 click: overlay close, trigger focus 강제 복귀 없음

`mvp4-ux-regression.test.mts`에는 닫힌 details descendant를 제외하고 cleanup/focus return 경로를 유지하는 source regression을 추가했다. 이 테스트는 browser keyboard QA를 대체하지 않으며, 위 실제 브라우저 결과를 별도 근거로 유지한다.

## Fixture 재현 및 미검증 경계

`frontend/scripts/visual-fixture.mjs`는 disposable UI 검증 전용이다. 127.0.0.1에만 bind하며 `/api/**`를 Backend로 전달하지 않는다. 상품·주소·회원 값과 이미지는 명시적인 QA 합성 데이터다. Production catalog 적재나 운영 mutation은 없다.

현재 fixture의 `auth=anonymous`는 `/api/auth/me` 조회에만 `AUTH_REQUIRED`를 반환하며 Cart/Checkout mutation 자체에는 세션 만료를 주입하지 않는다. 따라서 최신 단위 회귀는 PASS지만, 실제 브라우저에서 mutation 도중 세션이 만료되는 E2E는 이 fixture로 검증했다고 주장하지 않는다.

```powershell
# frontend에서, 각각 별도 터미널
npm.cmd run build
npm.cmd run start -- --hostname 127.0.0.1 --port 3007
node scripts/visual-fixture.mjs
# http://localhost:3006/__fixture
```

기존 local integration 환경의 `.env.local`은 이 worktree에 없었다. 다른 worktree Secret을 읽거나 복사하지 않고 독립 fixture로 검증했다. fixture의 일반 mutation은 409로 차단하며 로그인 및 장바구니 수량/삭제만 메모리에서 재현한다.

**UNVERIFIED:** 실제 Backend/DB 연동, 실제 catalog 사진 스트레스(합성 컬러·비율·긴 이름·null 이미지로만 확인), Toss SDK 결제, 실제 브라우저 AUTH_REQUIRED mutation/session-expiry E2E, Checkout→Address→Login 실 Backend resume E2E, 실제 서버 version 충돌 E2E, Wishlist undo의 실 Backend E2E, 모든 issue/status 조합, mobile menu 이외 overlay 조합, 타 브라우저/스크린리더, 200% zoom. 단위/contract 테스트 PASS와 실제 통합 PASS는 구분한다.

위험: 공통 CSS와 Frontend 인증 수명주기 변경 범위가 있으므로 후속 통합 QA에서 실제 API 데이터와 세션 만료 경계를 확인해야 한다. Admin은 redesign하지 않았고 full visual regression은 미실행이다. 복구는 인증 correction `412984d9` 또는 전체 task commit 단위 revert이며 API/DB/인프라 rollback은 필요 없다. 기존 branch/worktree/미추적 파일, main, Production은 변경하지 않았다.
