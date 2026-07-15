# FOUNDATION-006 1차 MVP 접근성·브라우저 경계 후속 QA 계획

## 작업 정보

- 작업 ID: `FOUNDATION-006`
- 역할: QA Engineer
- 기준선: `origin/main` `22378f98f9dc455754bf775588907257592e65b8`
- 대상 환경: Windows 노트북, Docker Desktop Linux Engine, `http://localhost:8080`
- 판정: 통과, 실패, 일부 미실행, 전체 미실행을 구분하며 관찰하지 않은 동작은 통과로 기록하지 않는다.

## 목적

FOUNDATION-004에서 미실행으로 남은 실제 keyboard-only 흐름과 비정상 `returnTo` 경계를 신규 의존성이나 장애 주입 없이 후속 검증한다. 기존 local-integration에서 안전하게 재현할 수 없는 GET 장애와 session 만료는 미실행 사유와 잔여 위험을 유지한다.

## 승인 입력

- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/product/PS-003-ux-product-decisions.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/api/API-003-subscription-api-contract-decision-request.md`
- `docs/qa/FOUNDATION-004/first-mvp-browser-test-plan.md`
- `docs/reports/FOUNDATION-004/qa-report.md`
- `docs/reports/FOUNDATION-005/tl-report.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

## 범위와 증거 원칙

- 제품 코드, API·DB·인증 계약, Docker 구성은 변경하지 않는다.
- Playwright·Cypress 등 신규 도구, test dependency, fault injection 장치를 추가하지 않는다.
- 자동화 계층이 실제 Tab 키를 전달하지 못하면 사용자가 same-origin GUI 브라우저에서 수행한 관찰을 수동 증거로 분리해 기록한다.
- credential, cookie, session ID와 CSRF token 원문을 문서·로그·화면 캡처에 남기지 않는다.
- GET 장애·session 만료를 기존 승인 수단으로 재현할 수 없으면 값을 조작하지 않고 전체 미실행으로 기록한다.

## 테스트 케이스

| ID | 추적성 | 절차 | 기대 결과 | 증거 | 계획 판정 |
| --- | --- | --- | --- | --- | --- |
| QA-F006-001 | FOUNDATION-004 Runbook | Compose config, build, `up --wait`, Full smoke | 네 서비스 healthy, 정상 MVP smoke 통과 | 명령 결과 | 실행 |
| QA-F006-002 | REQ-PRODUCT-001·002, 접근성 | 상품 목록에서 keyboard-only로 상세 이동 | 논리적 Tab 순서, 시각적 focus, Enter로 상세 이동 | 사용자 GUI 관찰 | 실행 |
| QA-F006-003 | REQ-AUTH-001, AUTH-003 | 로그인 폼을 keyboard-only로 입력·제출 | label 순서, focus 표시, Enter 제출, 상품 상세 복귀 | 사용자 GUI 관찰 | 실행 |
| QA-F006-004 | REQ-SUB-001·004 | SKU·수량·주기를 keyboard-only로 입력하고 생성 | Space·Enter로 조작, 생성 성공 후 상세 이동 | 사용자 GUI 관찰 | 실행 |
| QA-F006-005 | REQ-SUB-002·003, AUTH-003 | 구독 목록·상세 이동 후 로그아웃 | 링크·버튼 keyboard 동작, 로그아웃 후 회원 상태 정리 | 사용자 GUI 관찰 | 실행 |
| QA-F006-006 | UX 입력 검증·접근성 | 수량 0 또는 11 등 입력 오류 제출 | 오류 안내와 오류 요약 또는 첫 오류 focus | 사용자 GUI 관찰 | 실행 |
| QA-F006-007 | UX skip link | 새 상품 목록 진입 직후 첫 Tab과 Enter | skip link가 먼저 표시되고 본문으로 이동 | 사용자 GUI 관찰 | 실행 |
| QA-F006-008 | PS-003 로그인 복귀 | 외부 절대 URL을 `returnTo`로 로그인 | same-origin `/products`, 외부 이동 없음 | 실제 브라우저 URL | 실행 |
| QA-F006-009 | PS-003 로그인 복귀 | protocol-relative URL을 `returnTo`로 로그인 | same-origin `/products`, 외부 이동 없음 | 실제 브라우저 URL | 실행 |
| QA-F006-010 | PS-003 로그인 복귀 | `/login`을 `returnTo`로 로그인 | `/products` fallback | 실제 브라우저 URL | 실행 |
| QA-F006-011 | PS-003 로그인 복귀 | 음수 상품 ID를 `returnTo`로 로그인 | `/products` fallback | 실제 브라우저 URL | 실행 |
| QA-F006-012 | PS-003 로그인 복귀 | 음수 구독 ID를 `returnTo`로 로그인 | `/products` fallback | 실제 브라우저 URL | 실행 |
| QA-F006-013 | PS-003 로그인 복귀 | 숫자가 아닌 구독 ID를 `returnTo`로 로그인 | `/products` fallback | 실제 브라우저 URL | 실행 |
| QA-F006-014 | API-002 D7, UX 오류 상태 | 상품 목록·상세 GET 오류와 keyboard 재시도 | 안전 오류, focus 가능한 재시도, 복구 | 기존 승인 재현 수단 | 조건부 실행 |
| QA-F006-015 | API-003 D5, UX 오류 상태 | 구독 목록·상세 GET 오류와 keyboard 재시도 | 안전 오류, focus 가능한 재시도, 복구 | 기존 승인 재현 수단 | 조건부 실행 |
| QA-F006-016 | AUTH-003 DR2 | session 만료 후 회원 상태와 보호 화면 확인 | 회원 상태 정리, 로그인 또는 안전 기본 경로 이동 | 기존 승인 재현 수단 | 조건부 실행 |

## 판정 기준

- 통과: 기대 결과를 실제 명령 또는 GUI 관찰로 확인했다.
- 실패: 기대 결과와 다른 제품 동작을 재현 가능한 증거로 확인했다.
- 일부 미실행: 한 테스트 케이스의 일부 조합만 확인했다.
- 전체 미실행: 안전한 재현 수단이 없어 해당 테스트 케이스를 실행하지 않았다.
- 차단: 환경 또는 계약 충돌로 계획한 검증을 진행할 수 없다.

## 중단 조건

- 검증에 신규 dependency, Secret 추측, API·DB·인증 계약 변경 또는 장애 주입이 필요하다.
- 제품 결함이 발견되어 Frontend 또는 Backend 코드 수정이 필요하다.
- 비QA 데이터나 다른 프로젝트 volume의 안전을 확인할 수 없다.
- GUI 관찰이 필요한 사례를 사용자 관찰 없이 통과로 기록해야 한다.

## 남은 위험

GET 장애별 재시도와 session 만료는 승인된 안전 재현 수단이 없으면 계속 실제 브라우저 미재현 위험으로 남는다. 자동화 계층의 Tab 전달 제약은 사용자 수동 관찰로 보완하되 자동 회귀 테스트를 대체하지 않는다.
