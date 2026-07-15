# FOUNDATION-004 첫 구독 MVP 브라우저 QA 계획

## 작업 정보

- 작업 ID: `FOUNDATION-004`
- 역할: QA Engineer
- 기준: 최신 `main`의 local-integration 환경
- 단일 origin: `http://localhost:8080`
- 판정: 통과, 실패, 미실행, 차단을 구분하며 브라우저 미재현을 통과로 기록하지 않는다.

## 승인 입력

- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/api/API-002-public-product-api-contract-proposal.md`의 Approved D1~D7
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`의 Approved DR1~DR3
- `docs/api/API-003-subscription-api-contract-decision-request.md`의 Approved D1~D8
- `docs/handoffs/FRONTEND-001/fe-to-qa.md`
- `docs/handoffs/FOUNDATION-004/sre-to-qa.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

## 환경과 증거 원칙

- Docker Compose project는 `pawcycle-local-integration`이며 Backend는 한 인스턴스만 사용한다.
- 시작 전 `mysql`, `backend`, `frontend`, `proxy`가 모두 healthy이고 reset은 `false`여야 한다.
- 실제 email, password, session cookie와 CSRF token은 기록하지 않는다.
- 브라우저 증거는 URL, 화면 상태, 접근 가능한 이름, focus 상태와 비민감 method·path·status·횟수로 제한한다.
- 전체 HAR, 민감 header, cookie 저장소와 token 원문은 수집하지 않는다.
- 환경 재시작에는 Runbook의 `--wait --wait-timeout 120`을 사용하고 named volume을 삭제하지 않는다.

## 테스트 케이스

| ID | 승인 원본·인수 조건 | 사전 조건 | 수행 절차 | 기대 결과 | 증거 | 최종 상태 |
| --- | --- | --- | --- | --- | --- | --- |
| QA-F004-001 | UX-001 REQ-PRODUCT-001, API-002 D1·D4 | 비회원, healthy | `/`와 `/products` 진입 | `/`가 상품 화면으로 이동하고 로그인 강제 없이 상품 카드 표시 | URL·DOM | 통과 |
| QA-F004-002 | UX-001 REQ-PRODUCT-002, API-002 D1·D2 | 공개 fixture | 상품 상세 이동, 목록·상세 API와 화면 비교 | FOUNDATION-004 상품·구독 가능 SKU가 각각 하나이며 이름·가격·가능 여부·주기 일치 | 비민감 API field·DOM | 통과 |
| QA-F004-003 | UX-001 REQ-AUTH-001, FE 인수인계 2 | 비회원 | `/subscriptions`, 양의 상세 ID 직접 접근 | 로그인 이동, 안전한 내부 GET `returnTo` 보존 | URL·DOM | 통과 |
| QA-F004-004 | UX-001 REQ-AUTH-001, FE 인수인계 3 | 비회원 | 외부·protocol-relative·`/login`·0·음수·비정상 ID `returnTo`로 로그인 | 로그인 성공 시 `/products` fallback, 외부 이동 없음 | URL | 미실행(일부 입력) |
| QA-F004-005 | AUTH-003 DR1·DR2 | QA credential, 비회원 | 올바른 credential 로그인 | 현재 회원 UI 갱신, same-origin session 유지 | DOM·비민감 요청 횟수 | 통과 |
| QA-F004-006 | AUTH-003 DR1 | 비회원 | 잘못된 credential 로그인 | 회원 존재 여부를 구분하지 않는 승인 실패 안내 | DOM·status/code | 통과 |
| QA-F004-007 | AUTH-003 DR1·DR2, FE 인수인계 4 | 로그인 | 로그아웃 후 보호 화면 재접근 | 회원 상태 초기화, 보호 화면은 로그인 이동 | DOM·URL | 통과 |
| QA-F004-008 | AUTH-003 DR2, FE 인수인계 4 | 로그인 후 서버 session 만료를 안전하게 재현 가능 | 만료 상태에서 로그아웃 | 일반 실패가 아닌 세션 만료 안내 후 `/products` 이동 | DOM·URL·status/code | 미실행(단위 테스트 대체) |
| QA-F004-009 | AUTH-003 DR2, FE 인수인계 7 | 새 비회원 browser session | 상품 목록 진입 후 로그인·로그아웃·생성 흐름의 CSRF 요청 계수 | 공개 진입 선취득 없음, token 없을 때만 획득, POST 자동 재실행 없음 | proxy path·status·횟수 | 통과 |
| QA-F004-010 | AUTH-003 DR2, FE 인수인계 9 | 안전한 CSRF_INVALID 재현 수단 | token 무효화 후 상태 변경 시도 | token 폐기, 수동 재시도 안내, 원래 POST 자동 재실행 없음 | DOM·요청 횟수 또는 기존 테스트 | 미실행(단위 테스트 대체) |
| QA-F004-011 | UX-001 AC-SUB-001, API-003 D2 | 로그인, 구독 가능 SKU | SKU·수량 1·주기 선택 | 허용 입력 선택 가능, 생성 전 합계·확정 nextOrderDate 미표시 | DOM | 통과 |
| QA-F004-012 | UX-001 입력 검증·접근성 | 로그인, 상품 상세 | 입력 누락, 수량 0·11 제출 | POST 없이 필드 오류·오류 요약 표시, 요약 또는 첫 오류에 focus | DOM·focus·POST 횟수 | 통과 |
| QA-F004-013 | API-003 D2·D6, UX-001 처리 상태 | 유효 입력 | 생성 버튼 빠른 반복 입력 | 처리 중 잠금, POST 1회, 성공 ID 상세 이동 | DOM·URL·POST 횟수 | 통과 |
| QA-F004-014 | API-003 D6, FE 위험 | 안전한 timeout/실패 재현 | 생성 POST 실패 또는 timeout 관찰 | 자동 재실행 없음, 명시적 사용자 재시도만 가능 | DOM·POST 횟수 | 미실행 |
| QA-F004-015 | API-003 D3, UX-001 REQ-SUB-002·003 | 생성 완료 | 목록과 상세 확인 | 생성 구독 표시, 서버 순서·SKU·수량·주기·가격·날짜 일치 | 비민감 API field·DOM | 통과 |
| QA-F004-016 | UX-001 REQ-SUB-004, API-003 D2·D3 | 생성 완료 | 날짜 표시 비교 | ISO local date가 timezone 이동 없이 `YYYY. M. D.`로 표시 | API date·DOM | 통과 |
| QA-F004-017 | API-003 D4·D5 | 로그인 | 미존재·비숫자 상세 접근, 타인 소유는 기존 테스트 증거 확인 | 같은 조회 불가 화면, 소유권 정보 비노출 | DOM·status/code·기존 테스트 | 통과 |
| QA-F004-018 | API-003 D3 | reset 후 로그인 | `/subscriptions` 진입 | `subscriptions: []`를 빈 상태로 표시 | DOM·API shape | 통과 |
| QA-F004-019 | UX-001 오류 상태, FE 인수인계 12 | GET 실패를 안전하게 재현 가능 | 상품·목록·상세 실패 후 재시도 | 오류와 명시적 재시도, 복구 후 정상 화면, 오류 요약 focus | DOM·focus·status | 미실행(목록·상세 장애) |
| QA-F004-020 | UX-001 접근성 424~439 | desktop, keyboard-only | skip link부터 상품·form·제출·재시도 이동 | 논리적 tab 순서, focus-visible, 식별 가능한 이름 | DOM·focus·화면 | 미실행(키보드 순회) |
| QA-F004-021 | UX-001 접근성 | 상품 상세 | label·fieldset·legend·오류 연결 검사 | 수량 label, 배송 주기 group, 오류 programmatic 연결 | DOM semantics | 통과 |
| QA-F004-022 | UX-001 반응형 411~422 | desktop과 좁은 viewport | 주요 화면과 긴 정보 확인 | 핵심 작업 미가림, 읽기 순서·줄바꿈·버튼 접근 유지 | viewport별 화면·DOM | 통과 |
| QA-F004-023 | SRE Runbook, FOUNDATION-004 인수인계 | reset=false, 생성 구독 | 일반 `down`→`up --wait` 후 재로그인 | 구독·volume 보존, fixture 중복 없음, 네 서비스 healthy | container metadata·DOM·API | 통과 |
| QA-F004-024 | SRE reset 경계 | QA 구독 존재 | reset true 재생성→Empty 확인→즉시 false 복원 | QA 회원 구독만 빈 상태, 최종 reset=false | DOM·API·container metadata | 미실행(비QA 대조군) |
| QA-F004-025 | FOUNDATION-004 운영 경계 | 전체 실행 전후 | service·volume·다른 project 상태 비교 | Backend 한 인스턴스, volume·다른 project 보존 | Docker metadata | 미실행(타 프로젝트 volume·data) |

## 심각도와 중단 기준

- Critical: 데이터 손실, 인증·CSRF 우회, Secret 노출 또는 다른 프로젝트 데이터 변경
- High: 공개 탐색→로그인→구독 생성→목록·상세의 핵심 흐름 완료 불가
- Medium: 핵심 정보·경계·접근성 동작 불일치이나 우회 가능
- Low: 비차단 시각·문구·보조 동작 불일치
- Critical·High 또는 승인 계약 위반은 해당 흐름을 통과 처리하지 않고 담당 역할로 전달한다.

## 실행 제외와 대체 증거

- 다른 회원이나 비fixture 데이터를 새로 만들거나 삭제하지 않는다. 타인 소유 상세은 기존 Backend Security 테스트 증거와 브라우저 미재현을 분리한다.
- 기존 비QA 대조군의 pre/post 상태를 안전하게 수집할 수 없으면 reset의 비QA 보존과 타 프로젝트 volume·data 보존을 통과로 기록하지 않는다.
- CSRF_INVALID·POST timeout을 안전하게 만들 수 없으면 브라우저 통과로 기록하지 않고 관련 Frontend 단위 테스트와 Backend Security 테스트를 최소 범위로 확인한다.
- Backend·Frontend image build와 이미 통과한 전체 smoke는 관련 구현 변경이 없으므로 반복하지 않는다.
