# UIUX-DESKTOP-012 — Desktop customer UI

2026-11-01 · 일반 · 저장소 변경 · 기준 main c55e0a57f1efa09a604c78f517db527196781137

## Task 결과

| Task | 결과 |
| --- | --- |
| D0 | 최신 main에서 독립 worktree. A/R1 및 기존 검색·비교·확대·구매 동작 유지. main과 고객 코드가 같은 Admin worktree 빌드를 before 기준으로 사용 |
| D1 | Desktop Home 소개/CTA 중복 축소, 카테고리 밀도 조정. 1440×900 동일 fixture에서 첫 상품 이미지 y608→447, 가격 y1000→839 |
| D2 | 필터·정렬·비교를 한 toolbar로 배치. 결과 수와 선택 chip 분리, 정렬 폭 확보 |
| D3 | Desktop 구매 패널의 외곽·중첩 구획 축소, 찜 로그인 안내의 우선순위 완화 |
| D4 | Cart 행별 수량 미적용 안내. Checkout 제목/CTA 간결화. 결제 준비≠결제 완료 안내 유지 |
| D5 | 주소 dialog 내부 오류·렌더 후 오류 focus·입력 보존. My 부분 성공 유지/실패 항목 재시도. 비로그인 중립 컴포넌트 |
| D6 | 주문 예정일 문구 통일, 구독 선택 완료/미완료 안내, 해지 행동 분리 |
| D7 | 기존 소유 CSS에서 수정. 덮어써지는 동일 블록 선언 정리. 기존 모바일 fallback 보존을 위해 closure 전면 이동은 수행하지 않음 |
| D9 | 아래 로컬 검사와 브라우저 근거 확보 |

## 검증

- lockfile 기준 npm ci --ignore-scripts. Next 16.4.3. package/lockfile 변경 없음.
- npm run lint: 0 errors, 기존 6 warnings (기존 이미지/aria/ref/미사용 import).
- npm run typecheck: PASS.
- npm test: 183/183 PASS. 주소 focus 보정 후 관련 27/27 PASS.
- npm run build: 최종 PASS. 초기 junction 오류와 sandbox mkdir EPERM은 독립 의존성 설치 및 권한을 갖춘 동일 build로 해결.
- Chrome 1024×900 / 1280×900 / 1440×900: Home, PLP, PDP, Cart, Checkout, Login(로그인된 상태), My, 배송지, 구독 생성/상세 각 10개 경로의 실제 innerWidth 확인 및 준비된 제목 기준 DOM 가로 overflow 0 (변경 전/후 각각 30건).
- 대표 캡처만 아래 보존. 이 행렬은 모든 화면/모든 상태의 시각 PASS 또는 접근성 인증을 의미하지 않음.
- 가격 필터 apply/chip, 정렬, 비교 모드/2개 선택/종료, SKU·수량 선택·합성 Cart 담기, 이미지 확대, Cart 수량 적용·빈 상태, PLP 빈 결과, Home API 오류 확인.
- 주소 저장 fixture 409: dialog 내부 오류 focus, 입력 보존, Escape 후 배송지 추가로 focus 복귀 확인. 4xx/5xx 전체 종류는 미검증.
- My 주문 요약만 503: 다른 세 요약 유지, 주문 요약 재시도 후 정상 복구 확인.
- 비로그인 Cart: 중립 안내이며 안내 내부 role=alert 없음.

## 시각 근거

모든 상품/회원/주소/금액은 frontend/scripts/visual-fixture.mjs의 합성 데이터다. API 요청은 Backend로 전달하지 않는다. 이미지 URL이 같은 가상 패키지이며 실제 상품 이미지 개선 근거가 아니다. 캡처 과정의 합성 Cart 조작으로 badge 수량은 달라질 수 있다.

| 화면 | Before | After |
| --- | --- | --- |
| Home 1440 | [before](before-home-1440.jpg) | [after](after-home-1440.jpg) |
| PLP 1440 | [before](before-products-1440.jpg) | [after](after-products-1440.jpg) |
| PDP 1024 | [before](before-products-1-1024.jpg) | [after](after-products-1-1024.jpg) |
| Checkout 1440 | [before](before-checkout-1440.jpg) | [after](after-checkout-1440.jpg) |

[주소 저장 오류](address-save-error-1440.jpg) · [My 부분 실패](my-partial-failure-1440.jpg) · [구독 생성](after-subscriptions-new-1440.jpg) · [구독 상세](after-subscriptions-1-1440.jpg)

## 한계 및 복구

- 모바일/Tablet 시각 검증, 실제 인증·CSRF·Toss 결제·구독 command 통합, 412/UNKNOWN 실서버 상태, screen reader/forced-colors/200% zoom은 미실행.
- 실제 상품 이미지/운영 카탈로그, SKU 이름 보강, 판매자/정책 콘텐츠, Backend 계약은 제외.
- 별도 Admin 작업은 ADMIN-RETURN-013. 이 변경은 병합·배포 승인이 아님.
- 복구: 이 고객 UI commit을 되돌리는 일반 revert PR. Production 실행 없음.
