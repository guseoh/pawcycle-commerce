# FOUNDATION-006 1차 MVP 접근성·브라우저 경계 후속 QA 보고서

## 작업 정보

- 작업 ID: `FOUNDATION-006`
- 역할: QA Engineer
- 역할 브랜치: `test/qa`
- 기준 commit: `22378f98f9dc455754bf775588907257592e65b8`
- 결과: **조건부 통과**

## 목적

FOUNDATION-004에서 미실행으로 남은 실제 keyboard-only 흐름과 비정상 `returnTo` 경계를 노트북 local-integration에서 후속 검증하고, 안전한 재현 수단이 없는 항목은 통과로 바꾸지 않는다.

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

## 시작 기준 상태

- Git root와 origin은 각각 로컬 PawCycle 저장소와 `https://github.com/guseoh/pawcycle-commerce.git`로 일치했다.
- 시작 branch는 `main`, 작업 트리는 깨끗했고 열린 PR은 없었다.
- fetch 후 로컬 `main`은 `origin/main`보다 11 commits 뒤였으며 `git pull --ff-only origin main`으로 `22378f9`까지 갱신했다.
- README 기준 commit `22378f98f9dc455754bf775588907257592e65b8`이 포함됐고 로컬 `main`과 `origin/main`이 일치했다.
- 기존 로컬·원격 `test/qa`는 PR #46까지 병합 완료, 열린 PR 없음, 원격 역할 브랜치 대비 `main`에서 삭제된 QA 산출물 없음임을 확인한 뒤 정리했다. 최신 `main`에서 새 `test/qa`를 생성했다.

## 테스트 환경

- Windows 노트북, Docker Desktop Linux Engine
- MySQL 8.4.10, Java 25 Backend image, Node.js 24 Frontend image, Nginx proxy
- same-origin: `http://localhost:8080`
- local `.env.local`은 Git 제외 상태이며 credential 원문을 출력하거나 문서화하지 않았다.
- 사용자가 인앱 GUI 브라우저에서 keyboard-only 흐름을 수행했고 QA는 자동 브라우저로 `returnTo` URL 결과를 확인했다.

## 변경 범위

- `docs/qa/FOUNDATION-006/first-mvp-follow-up-test-plan.md`
- `docs/reports/FOUNDATION-006/qa-report.md`

## 변경하지 않은 범위

- Backend·Frontend 제품 코드와 테스트 코드
- API·DB·인증·CSRF·CORS 계약
- Docker Compose와 Nginx 설정
- 신규 dependency, 브라우저 자동화 도구와 fault injection
- FOUNDATION-004 역사적 QA 계획과 보고서

## 실행한 검증과 결과

| ID | 기대 결과 | 실제 결과 | 판정 |
| --- | --- | --- | --- |
| QA-F006-001 | Compose config·build·health·Full smoke 통과 | config 통과, Backend·Frontend build 성공, mysql·backend·frontend·proxy healthy, Full smoke 통과 | 통과 |
| QA-F006-002 | 상품 목록부터 상세까지 논리적 Tab·focus·Enter | 사용자가 keyboard-only로 이동·focus 표시·상세 진입을 확인 | 통과 |
| QA-F006-003 | 로그인 폼 순서·focus·Enter와 상세 복귀 | 사용자가 이메일·비밀번호·로그인 순회와 성공 복귀를 확인 | 통과 |
| QA-F006-004 | SKU·수량·주기 keyboard 조작과 구독 생성 | 사용자가 Space·Enter 조작, 생성과 상세 이동을 확인 | 통과 |
| QA-F006-005 | 구독 목록·상세와 로그아웃 keyboard 동작 | 사용자가 목록·상세 이동과 로그아웃을 확인 | 통과 |
| QA-F006-006 | 입력 오류 안내와 focus 이동 | 사용자가 오류 안내와 focus 처리를 확인 | 통과 |
| QA-F006-007 | 첫 Tab에 skip link 표시와 본문 이동 | `Ctrl+L`로 새 진입 후 첫 Tab에 `본문으로 건너뛰기` 표시를 사용자가 재확인 | 통과 |
| QA-F006-008 | 외부 절대 URL 차단 | 로그인 후 `http://localhost:8080/products` | 통과 |
| QA-F006-009 | protocol-relative URL 차단 | 로그인 후 `http://localhost:8080/products` | 통과 |
| QA-F006-010 | `/login` 재귀 복귀 차단 | 로그인 후 `/products` | 통과 |
| QA-F006-011 | 음수 상품 ID 차단 | 로그인 후 `/products` | 통과 |
| QA-F006-012 | 음수 구독 ID 차단 | 로그인 후 `/products` | 통과 |
| QA-F006-013 | 숫자가 아닌 ID 차단 | 로그인 후 `/products` | 통과 |
| QA-F006-014 | 상품 GET 오류·재시도 | 승인된 안전 장애 재현 수단 없음 | 전체 미실행 |
| QA-F006-015 | 구독 GET 오류·재시도 | 승인된 안전 장애 재현 수단 없음 | 전체 미실행 |
| QA-F006-016 | session 만료 후 정리·이동 | cookie·session 저장소를 읽거나 조작하지 않는 원칙과 승인된 만료 수단 부재 | 전체 미실행 |

결과 합계는 통과 13건, 일부 미실행 0건, 전체 미실행 3건, 실패 0건, 차단 0건이다.

## 핵심 검증 결과

- 상품 목록 → 상품 상세 → 로그인 → SKU·수량·배송 주기 입력 → 구독 생성 → 목록·상세 → 로그아웃의 keyboard-only 흐름을 사용자가 same-origin GUI 브라우저에서 완료했다.
- skip link는 새 상품 목록 진입 직후 첫 Tab에 실제 표시됐다.
- 외부 절대 URL, protocol-relative URL, `/login`, 음수 상품 ID, 음수 구독 ID와 숫자가 아닌 ID는 실제 브라우저 로그인 뒤 모두 same-origin `/products`로 수렴했다.
- 기존 MVP 정상 흐름은 Full smoke와 사용자 GUI 관찰에서 회귀가 발견되지 않았다.

## 실패·경계·권한·회귀 결과

- 실패와 차단은 없다.
- 승인되지 않은 외부 origin 이동은 0건이다.
- keyboard-only 흐름에서 focus 누락, 비논리적 순서, Enter·Space 비활성 또는 오류 focus 결함은 관찰되지 않았다.
- GET 오류별 재시도와 session 만료는 제품 통과가 아니라 전체 미실행이다.

## 자동 검증

| 명령 또는 검사 | 결과 |
| --- | --- |
| `git fetch origin --prune`과 branch·PR 관계 확인 | 통과 |
| `git pull --ff-only origin main`과 `main == origin/main` 확인 | 통과 |
| `docker compose ... config --quiet` | 통과 |
| `docker compose ... pull mysql proxy` | 통과 |
| `docker compose ... build backend frontend` | 통과 |
| `docker compose ... up --detach --wait --wait-timeout 180` | 네 서비스 healthy |
| `smoke.ps1 -Scenario Full -BaseUri http://localhost:8080` | 통과 |
| 실제 브라우저 비정상 `returnTo` 6건 | 6건 통과 |
| `npm test` | 12 tests 통과 |
| `py -3 scripts/validate-task-artifacts.py --task-id FOUNDATION-006` | 통과 |
| commit 제목 validator | 통과 |
| 새 산출물 whitespace 검사 | 통과 |

## 실행하지 못한 검증과 이유

- 상품 목록·상세 GET 오류와 재시도는 기존 Runbook·지원 코드에 안전한 endpoint별 재현 수단이 없어 전체 미실행했다.
- 구독 목록·상세 GET 오류와 재시도도 같은 이유로 전체 미실행했다. 따라서 재시도 UI keyboard 접근은 실제 장애 화면에서 확인하지 않았다.
- session 만료는 cookie·session ID 원문이나 저장소를 읽고 조작하지 않으며 승인된 만료 장치가 없어 전체 미실행했다.
- CSRF token 강제 변조와 생성 POST timeout은 FOUNDATION-006 제외 범위이므로 실행하지 않았다.

## 환경 중단과 재검증

- 최초 노트북 환경에는 Docker Engine 실행과 `.env.local`이 없어 사용자가 로컬 credential을 준비한 뒤 진행했다. 값은 출력하지 않았다.
- 첫 Full smoke 시 QA가 Runbook 설명을 잘못 해석해 script가 요구하는 환경 변수명이 아닌 별도 이름을 전달했고, script는 API 요청 전 credential 준비 검사에서 중단됐다. 제품·데이터 변경은 없었다. 실제 script 이름으로 바로잡아 Full smoke만 재실행해 통과했다.
- 자동 브라우저 계층은 링크에 focus를 만들 수 있었지만 Tab·Enter 키를 페이지에 전달하지 못했다. 이를 제품 결함으로 단정하지 않고 사용자가 GUI 브라우저에서 수동 순회했으며, skip link 시작 위치까지 별도 재검증했다.

## 결함과 심각도

확정 제품 결함은 없다. 실패 0건이므로 Critical·High·Medium·Low 버그 리포트와 담당 역할 인수인계는 작성하지 않는다.

## 적용 방법

`infra/local-integration/.env.local`의 reset을 `false`로 유지하고 `docker compose --env-file .env.local up --detach --wait`로 환경을 재사용한다. credential 원문은 로컬 파일 밖으로 복사하지 않는다.

## 남은 위험과 제한

- GET endpoint별 오류·재시도 UI와 session 만료는 실제 브라우저 미재현 위험으로 남는다.
- keyboard-only 결과는 사용자 수동 관찰이며 자동 회귀 테스트가 아니다.
- Nginx timeout이나 장애 주입 장치를 추가하지 않았으므로 오류 표시 지연 특성은 FOUNDATION-004 기준선을 유지한다.

## 인수인계 생략 사유

확정 결함이 없고 다음 구현 역할이 정해지지 않았다. 전달할 제품 변경 요청이 없으므로 `docs/handoffs/FOUNDATION-006/` 인수인계를 생략한다.

## 최종 판정

**조건부 통과**다. FOUNDATION-004의 keyboard-only와 비정상 `returnTo` 열린 위험은 실제 GUI 관찰과 브라우저 URL 증거로 통과했다. 정상 MVP 흐름의 회귀와 P0·P1·계약 위반은 발견되지 않았다. 다만 GET 오류별 재시도와 session 만료 3건은 안전 재현 수단 부재로 전체 미실행이므로 조건부 판정을 유지한다.

## 다음 작업

- Product Owner/Tech Lead는 전체 미실행 3건의 잔여 위험을 확인한다.
- 필요하면 별도 승인 작업에서 민감정보를 노출하지 않는 GET 오류·session 만료 전용 검증 수단을 설계한다.

## Git 결과

- branch: `test/qa`
- commit·push: 최종 검증 후 수행 예정

## PR 결과

- 제목: `test(qa): FOUNDATION-006 MVP 접근성 후속 검증`
- 상태: 최종 검증과 push 후 Draft가 아닌 PR 생성 예정
