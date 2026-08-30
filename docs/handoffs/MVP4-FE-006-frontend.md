# MVP4-FE-006 Frontend 구현·검증 인수인계

- 작업 ID: MVP4-FE-006
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Frontend Engineer
- 다음 소비자: 사용자/Tech Lead 및 후속 Frontend·QA
- 상태: **Draft PR #257 유지 — Ready/merge/Production 실행 없음**
- 기준: 최초 구현 시작 시 `origin/main` = `39b9091c53b958f16eab489f3b674813fb4282d5`
- branch: `feat/fe/MVP4-FE-006`
- 현재 원격 correction HEAD: `412984d977b842d8fa61a4c8cef53488d37a2979`
- Draft PR: #257 `feat(frontend): A R1 Daily Orbit 고객 Commerce 구현`
- 격리 worktree: `C:/Users/guseo/IdeaProjects/pawcycle-commerce/tmp/worktrees/MVP4-FE-006`
- 승인 입력: PR #256, `docs/design/MVP4-UX-005/README.md`와 A/R1 문서. R0/B/C는 구현 권위로 사용하지 않음.

## 변경과 계약 경계

`frontend/src/app/globals.css`, `shopping.css`를 Customer A/R1 token과 composition으로 교체했다. 기존 cream/green 스타일은 Admin 전용 scope와 legacy header/footer로 격리했다. 새 의존성이나 Backend/API 변경은 없다.

검색 중심 Header, Hero 없는 Home 실제 catalog 조회, 상단 PLP popover/mobile drawer, 열린 상품 grid, PDP 이미지·옵션·가격 분리, Cart 행/summary, Checkout 배송→상품→금액, dedicated Login shell을 구현했다. Order Detail과 Subscription New/Detail은 현재 상태·예정 회차·pending change·현재 plan·availableActions를 분리했다. 나머지 Customer family는 같은 shell, navigation, controls와 상태 계층을 사용한다.

Cart operation별 retry, Checkout 주소 returnTo, Wishlist 단일 undo, 주문→구독 진입, 저장 주소 draft와 CHANGE_PLAN 비교/확정 로직은 유지했다. 검색 폼을 Header로 통합하면서 기존 SEARCH interaction 전송도 옮겼다. 단일 SKU 자동 선택 테스트만 현재 사용자의 명시적 선택 계약으로 변경했다. 가격·재고·배송 예정일을 새로 계산하지 않는다.

Product 이미지 fallback과 품절/구매 불가 상태를 적용했다. Home은 기존 product API의 가격을 사용하며, 가격 없는 recommendation 응답에 가상 가격을 넣지 않는다. R1 가상 PB 이미지는 앱에 사용하지 않았다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| 최초 구현 `npm test` | PASS, 122/122 — direct correction 전 결과 |
| 최초 구현 `npm run typecheck` | PASS — direct correction 전 결과 |
| 최초 구현 `npm run lint` | PASS, 0 errors / 기존 warning 5개 |
| 최초 구현 `npm run build` | PASS |
| 최초 구현 `git diff --check` | PASS |
| Repository Validation #1467 / correction `412984d9` | PASS — conventions, Harness, Frontend lint/build, Application validation |
| direct correction 이후 `npm test` | **UNVERIFIED** — 현재 PR CI가 `npm test`를 실행하지 않음 |
| direct correction 이후 별도 `npm run typecheck` | **UNVERIFIED** — 현재 PR CI에 별도 typecheck step 없음 |
| 320/375/768/1024/1440 렌더링 | 최초 구현 기준 25개 화면·상태, 125회 측정에서 `scrollWidth <= clientWidth`, 화면 오른쪽 넘침 없음 |
| Desktop 필터 취소 | draft 폐기, URL 유지, Escape 후 가격 버튼 focus 복귀 PASS |
| Mobile 필터 | 적용 후 URL `petType=DOG`, Escape focus 복귀, 역방향 Tab 마지막 control 이동 PASS |
| Cart 확인창 | 취소가 기본 focus, 취소/Escape 시 mutation 없음 |
| PDP | 명시적 SKU 선택 전 구매 버튼 disabled, mobile sheet 하단 좌표 = viewport 높이, Escape 닫기 |
| Order/Subscription | 주문에서 호환 플랜 선택 진입, 구독 pet/plan/cycle 선택, 현재 4주와 예정 8주 분리, CHANGE_PLAN 비교/확인창, 저장 배송지 draft UI 확인 |
| reduced motion | 실제 `matchMedia('(prefers-reduced-motion: reduce)').matches === true`; button transition `0s`, animation `none` 확인 |
| Mobile 메뉴 정·역방향 Tab | **PASS** — 아래 correction 이력 참조 |
| 200% browser zoom | **UNVERIFIED** — in-app browser에서 Ctrl++ / Ctrl+= 후 viewport·devicePixelRatio가 변하지 않음. 720px viewport를 200% zoom으로 대체 주장하지 않음 |

Lint warning: 기존 admin-catalog `categoryHierarchy` 미사용 1개, 기존 `<img>` 4개(catalog card/comparison/subscription detail/product detail). unrelated warning 정리는 하지 않았다.

초기 테스트에서 과거 Hero/sidebar/style selector와 단일 SKU 자동 선택을 고정한 source assertion이 실패했다. 승인된 계약에 맞게 관련 assertion을 수정했으며 기존 동작 검사는 삭제하지 않았다. Header auth binding 변경 후 source assertion 1개가 실패한 것은 실제 binding 형태를 반영해 한 번 수정 후 통과했다.

## Local evidence

루트 기준 `tmp/visual-evidence/`에 로컬 PNG와 `viewport-measurements.json`을 저장했다. binary는 Git에 추가하지 않는다. 캡처 높이는 1000px viewport에서 full-page이며 파일 suffix는 요청 viewport 너비다. 세로 scrollbar가 있으면 실제 client 폭은 15px 작다.

- 핵심: `home`, `plp`, `pdp`, `cart`, `checkout`, `login`, `order-detail`, `subscription-new`, `subscription-detail`
- 빈 상태: `plp-no-result`, `cart-empty`
- Family: `my`, `pets`, `addresses`, `billing`, `notifications`, `wishlist`, `compare`, `orders`, `subscriptions`, `support`, `faq`, `notice`, `shipping`, `returns`
- 각 이름의 `-375.png`, `-1440.png` 캡처가 있다. 320/768/1024는 JSON 측정 근거를 남겼다.
- 추가: `filter-drawer-375.png`, `pdp-options-sheet-375.png`, `subscription-change-confirm-1440.png`
- Production 대조: 승인 UX 작업에 보존된 `docs/design/MVP4-UX-005/evidence/production-plp-1440.jpg`와 비교했다. 이번 FE 작업에서 Production을 새로 직접 캡처했다고 주장하지 않는다.
- 최초 구현 실행 로그: `tmp/test-final.log`, `tmp/typecheck-final.log`, `tmp/lint-final.log`, `tmp/build-final.log`

## Correction 결과: 모바일 메뉴 포커스 순환

원인은 접힌 `<details>`의 `전체 보기` 링크였다. Chrome에서 해당 링크는 `getClientRects().length === 1`과 `tabIndex === 0`을 반환해 기존 목록에 들어갔지만, native Tab 대상이 아니어서 첫 `닫기`에서 Shift+Tab 시 마지막 항목으로 실제 이동하지 않았다.

`drawerFocusable`은 닫힌 details 내부에서는 `summary`만 포함하도록 최소 보정했다. 메뉴 close/Escape에서는 cleanup이 background inert를 해제한 뒤 원래 trigger로 focus를 복귀시키고, drawer 바깥 pointer close는 trigger focus를 강제하지 않도록 정리했다. keydown listener는 effect cleanup에서 제거되므로 open/close 반복으로 누적되지 않는다.

Chrome local fixture에서 다음을 PASS로 재검증했다.

- 375px: 15개 실제 focusable 순서 전체 forward Tab 및 backward Shift+Tab cycle, Escape, 메뉴 trigger focus return
- 320px: `닫기` + Shift+Tab → `목욕·케어`, `목욕·케어` + Tab → `닫기`, Escape → 메뉴 trigger focus return
- 375px drawer 바깥 click: overlay close, trigger focus 강제 복귀 없음

`mvp4-ux-regression.test.mts`에는 닫힌 details descendant를 제외하고 cleanup/focus return 경로를 유지하는 source regression을 추가했다. 이 테스트는 browser keyboard QA를 대체하지 않으며, 위 실제 브라우저 결과를 별도 근거로 유지한다.

## Correction 결과: 인증 만료와 Checkout 복귀

ChatGPT가 사용자 요청에 따라 PR #257 branch에 `412984d9` correction을 직접 반영했다.

- `executeWithCsrf`는 이제 mutation 중 `AUTH_REQUIRED`가 발생하면 회원 ID와 CSRF token을 폐기하고 `anonymous`로 전환한 뒤 **원래 오류를 그대로 rethrow**한다. mutation을 자동 재실행하지 않는다.
- 이 동작은 Cart, Checkout, Address, Billing, Notification, Order/Subscription 등 `executeWithCsrf`를 사용하는 Customer mutation에 공통 적용된다. Backend/API 계약은 바꾸지 않는다.
- Checkout→Address 흐름에서 세션이 만료된 경우 `/addresses?returnTo=%2Fcheckout` 문맥을 로그인 returnTo로 보존한다. 허용 예외는 이 정확한 내부 GET 경로뿐이며 arbitrary query, `/admin`, 외부 URL은 계속 차단한다.
- `formatScheduleStatus`, `subscriptionIssueCopy`는 own-property lookup만 허용해 `__proto__` 같은 inherited key가 fallback을 우회하지 못하도록 보정했다.
- `csrf-lifecycle.test.mts`, `frontend-utils.test.mts`에 AUTH_REQUIRED 단일 실행/익명 전환, 안전한 Checkout resume, `__proto__` fallback 회귀 테스트를 추가했다.

Repository Validation #1467은 correction HEAD에서 Green이었다. 다만 현재 workflow의 Frontend lane은 `npm ci → npm run lint → npm run build`만 실행하므로 새 `node:test` 회귀 테스트 자체의 PASS는 아직 증명하지 않는다.

## Fixture 재현 및 미검증 경계

`frontend/scripts/visual-fixture.mjs`는 disposable UI 검증 전용이다. 127.0.0.1에만 bind하며 `/api/**`를 Backend로 전달하지 않는다. 상품·주소·회원 값과 이미지는 명시적인 QA 합성 데이터다. Production catalog 적재나 운영 mutation은 없다.

```powershell
# frontend에서, 각각 별도 터미널
npm.cmd run build
npm.cmd run start -- --hostname 127.0.0.1 --port 3007
node scripts/visual-fixture.mjs
# http://localhost:3006/__fixture
```

기존 local integration 환경의 `.env.local`은 이 worktree에 없었다. 다른 worktree Secret을 읽거나 복사하지 않고 독립 fixture로 검증했다. fixture의 일반 mutation은 409로 차단하며 로그인 및 장바구니 수량/삭제만 메모리에서 재현한다.

**UNVERIFIED:** direct correction 이후 전체 `npm test`와 별도 typecheck, 실제 Backend/DB 연동, 실제 AUTH_REQUIRED 브라우저 세션 만료 E2E, Checkout→Address→Login 실제 Backend resume E2E, 실제 catalog 사진 스트레스(합성 컬러·비율·긴 이름·null 이미지로만 확인), Toss SDK 결제, 실제 서버 version 충돌 E2E, Wishlist undo의 실 Backend E2E, 모든 issue/status 조합, mobile menu 이외 overlay 조합, 타 브라우저/스크린리더, 200% zoom. 기존 단위/contract 테스트 PASS와 실제 통합 PASS는 구분한다.

위험: 인증 수명주기 공통 경계와 공통 CSS 변경 범위가 크므로 Ready 전 direct correction 이후 전체 frontend test/typecheck와 실제 인증 만료 focused QA가 필요하다. Admin은 redesign하지 않았지만 full visual regression은 미실행이다. 인증 correction 복구는 `412984d9` commit 단위 revert, 전체 작업 복구는 task commit 단위 revert이며 API/DB/인프라 rollback은 필요 없다. main과 Production은 변경하지 않았다.
