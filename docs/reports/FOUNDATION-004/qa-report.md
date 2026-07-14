# FOUNDATION-004 첫 구독 MVP QA 보고서

## 작업 정보

- 작업 ID: `FOUNDATION-004`
- 역할: QA Engineer
- 기준 브랜치: `main`
- 작업 브랜치: `test/qa`
- 기준 SHA: `02e86c4593bf53de000428ab0cd7279c46df652a`
- 검증일: 2026-07-15
- 최종 판정: **조건부 통과**

## 목적

최신 `main`에 병합된 첫 구독 MVP를 실제 `pawcycle-local-integration` 환경과 same-origin 브라우저에서 독립 검증한다. 공개 상품 탐색부터 session 로그인, CSRF, 구독 생성·목록·상세, 로그아웃, 오류·경계·반응형, 일반 재시작 보존과 reset 복원까지 승인된 요구사항과 계약에 연결한다.

## 승인 입력

- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/api/API-002-public-product-api-contract-proposal.md`의 Approved D1~D7
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`의 Approved DR1~DR3
- `docs/api/API-003-subscription-api-contract-decision-request.md`의 Approved D1~D8
- `docs/handoffs/FRONTEND-001/fe-to-qa.md`
- `docs/handoffs/FOUNDATION-004/sre-to-qa.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`
- PR #45의 병합 head와 Repository Validation 이력

## 테스트 환경

- Windows 11, Docker Desktop Linux engine
- Compose project: `pawcycle-local-integration`
- 단일 origin: `http://localhost:8080`
- 서비스: `mysql`, `backend`, `frontend`, `proxy` 각 1개, 최종 모두 healthy
- Backend profile: `local-integration`, 단일 인스턴스
- MySQL named volume: `pawcycle-local-integration-mysql-data`
- 브라우저: Codex 인앱 브라우저
- viewport: 기본 화면, 1280×800 desktop, 390×844 narrow
- 좁은 화면 실제 content viewport는 375px, desktop content viewport는 1265px였으며 두 경우 모두 수평 overflow가 없었다.
- 실제 QA email 전체 값, password, session cookie와 CSRF token은 수집하거나 기록하지 않았다.

## 변경 범위

- 브라우저 QA 계획과 실행 상태 기록
- QA 보고서 작성
- 실제 local-integration 시작·재시작·Backend 일시 중지·복구·reset 검증
- 비민감 method·path·status·횟수와 Docker metadata 확인

## 변경하지 않은 범위

- Backend·Frontend 제품 코드
- API, DB schema, Flyway, 인증·CSRF·CORS 계약
- Compose, Dockerfile, CI workflow와 production 인프라
- 신규 dependency와 E2E framework
- MySQL volume 삭제, 다른 회원·비fixture 데이터 생성·삭제
- 자동 병합

## 인수 조건 매핑과 결과

| ID | 실제 결과와 비민감 증거 | 상태 | 심각도·근거 |
| --- | --- | --- | --- |
| QA-F004-001 | `/`는 `/products`로 이동했고 비회원 상품 카드가 표시됐다. 공개 진입에 로그인 강제가 없었다. | 통과 | 해당 없음 |
| QA-F004-002 | 공개 API와 화면에서 fixture 상품·SKU가 각각 하나였고 가격 19,900원, 구독 가능, 주기 2·4·8주가 일치했다. | 통과 | 해당 없음 |
| QA-F004-003 | 비회원 `/subscriptions`와 양의 상세 경로가 로그인으로 이동하며 내부 `returnTo`를 보존했다. 재로그인 뒤 `/subscriptions`로 복귀했다. | 통과 | 해당 없음 |
| QA-F004-004 | 외부 URL은 로그인 성공 뒤 `/products`로 대체되어 외부 이동이 없었다. protocol-relative, `/login`, 0은 Frontend 단위 테스트가 통과했다. 음수·비정상 값을 브라우저에서 각각 반복하지 않았다. | 미실행(일부 입력) | 잔여 위험 Low. sanitizer 단위 증거는 있으나 전체 브라우저 입력 조합은 미실행이다. |
| QA-F004-005 | 사용자가 직접 credential을 입력했고 회원 UI `로그아웃 · 회원 1`과 로그인 `200` 1건을 확인했다. 실제 credential은 읽지 않았다. | 통과 | 해당 없음 |
| QA-F004-006 | 비민감 dummy credential 1회 시도에서 승인된 일반 실패 안내가 표시됐고 `401`이었다. 자동 반복은 없었다. | 통과 | 해당 없음 |
| QA-F004-007 | 로그아웃 버튼 1회 조작 뒤 회원 UI가 사라지고 보호 경로가 로그인 화면으로 이동했다. | 통과 | 해당 없음 |
| QA-F004-008 | session cookie만 안전하게 제거하는 브라우저 수단을 사용하지 않았다. `AUTH_REQUIRED` 피드백 단위 테스트는 통과했다. | 미실행(단위 테스트 대체) | 잔여 위험 Medium. 실제 만료 browser UX는 미재현이다. |
| QA-F004-009 | 최초 공개 진입의 CSRF 선취득은 0건이었다. 로그인 전·후 token 흐름을 path·status·횟수로 확인했고 생성 전 추가 CSRF 없이 POST 1건만 발생했다. | 통과 | 해당 없음 |
| QA-F004-010 | token 원문을 읽거나 변조하지 않아 브라우저 `CSRF_INVALID`는 재현하지 않았다. token 폐기·원 POST 미재실행 관련 Frontend 테스트는 통과했다. | 미실행(단위 테스트 대체) | 잔여 위험 Medium. Backend 대상 테스트는 로컬 JDK 제한으로 재실행하지 못했다. |
| QA-F004-011 | SKU, 수량, 서버 제공 주기를 선택할 수 있었고 생성 전에는 합계나 확정 다음 주문일을 표시하지 않았다. | 통과 | 해당 없음 |
| QA-F004-012 | 입력 누락, 수량 0·11에서 오류 요약과 필드 오류가 표시되고 요약이 active 상태였다. 생성 POST는 0건이었다. | 통과 | 해당 없음 |
| QA-F004-013 | 유효 입력에서 더블클릭 시 전체 form과 버튼이 처리 중 비활성화됐고 생성 POST `201`은 1건뿐이었다. 성공 상세로 이동했다. | 통과 | 해당 없음 |
| QA-F004-014 | 실제 생성 POST timeout은 안전하게 주입하지 않았다. 정상 빠른 반복의 중복 방지와 CSRF 원 POST 미재실행 단위 테스트만 확인했다. | 미실행 | 잔여 위험 Medium. 네트워크 timeout browser 경로는 미재현이다. |
| QA-F004-015 | 독립 요청으로 만든 두 구독이 최신순으로 표시됐고 상세의 SKU, 수량, 주기, 가격과 날짜가 입력·서버 결과에 맞았다. | 통과 | 해당 없음 |
| QA-F004-016 | 서버 날짜가 timezone 이동 없이 `YYYY. M. D.` 형식으로 표시됐고 날짜 formatter 단위 테스트도 통과했다. | 통과 | 해당 없음 |
| QA-F004-017 | 미존재와 비숫자 상세는 동일한 조회 불가 화면과 비노출 문구를 사용했다. 타인 소유 동등 응답은 기존 `SubscriptionApiIntegrationTests`와 PR #45 Application validation 성공 이력으로 대조했다. | 통과 | 해당 없음. 타인 계정을 새로 만들지 않았다. |
| QA-F004-018 | reset 뒤 재로그인한 `/subscriptions`에 `아직 구독이 없습니다.`가 표시됐다. `Empty` smoke도 통과했다. | 통과 | 해당 없음 |
| QA-F004-019 | Backend 일시 중지 뒤 상품 목록은 약 60초 후 오류·`다시 시도`를 표시했고 Backend 복구 후 재시도로 정상 목록을 회복했다. 목록·상세 장애를 각각 반복하지 않았다. | 미실행(목록·상세 장애) | 잔여 위험 Low. GET 오류 UI 공통 동작은 상품 목록에서 확인했다. |
| QA-F004-020 | skip link, 접근 가능한 이름과 focus-visible 스타일은 확인했다. 브라우저 도구의 keypress가 focus 이동·선택을 발생시키지 않아 end-to-end keyboard-only 순회를 완료하지 못했다. | 미실행(키보드 순회) | 잔여 위험 Medium. 제품 결함으로 판정할 증거는 없다. |
| QA-F004-021 | SKU와 배송 주기는 group, 수량은 이름 있는 spinbutton으로 노출됐다. 오류 요약과 필드 오류가 함께 표시됐다. | 통과 | 해당 없음 |
| QA-F004-022 | 390×844와 1280×800에서 핵심 정보·버튼이 유지됐고 수평 overflow가 없었다. | 통과 | 해당 없음 |
| QA-F004-023 | `reset=false` 일반 `down`→`up --wait` 뒤 두 구독과 동일 named volume이 보존됐고 네 서비스가 healthy였다. 엔진 재기동 뒤에도 재로그인 시 두 구독이 남아 있었다. | 통과 | 해당 없음 |
| QA-F004-024 | reset true 재생성 뒤 `Empty`가 통과했고 즉시 false로 복원해 Backend·proxy를 재생성했다. false 상태의 `Empty`와 브라우저 빈 상태도 통과했다. | 통과 | 해당 없음 |
| QA-F004-025 | Backend는 한 인스턴스였고 named volume 이름이 유지됐다. `board-mysql-dev`는 동일 container ID를 유지했으며 직접 변경·삭제하지 않았다. | 통과 | 환경 엔진 재기동으로 uptime은 초기화됐으며 아래 제한에 별도 기록한다. |

결과 합계는 통과 19건, 일부 또는 전체 미실행 6건, 실패 0건, 차단 0건이다.

## 핵심 검증 결과

- 공개 상품 → 안전한 로그인 복귀 → 구독 생성 → 목록 최신순 → 상세 → 로그아웃의 첫 사용자 흐름이 same-origin 브라우저에서 연결됐다.
- 입력 오류와 빠른 반복은 생성 POST 전에 차단되거나 처리 중 잠금으로 1건만 전송됐다.
- 미존재·비숫자 상세는 소유권 정보를 구분해 노출하지 않았다.
- 일반 재시작에서 구독과 volume이 보존됐고 reset true 후 false 복원에서 최종 빈 구독 상태가 확인됐다.
- 최종 reset은 `false`이며 `mysql`, `backend`, `frontend`, `proxy`는 모두 healthy다.
- P0·P1 또는 승인 계약 위반으로 판정할 재현 가능한 제품 결함은 발견하지 않았다.

## 실패·경계·권한·회귀 결과

- 잘못된 credential은 일반 로그인 실패로 수렴했다.
- 미입력, 수량 0·11은 focus 가능한 오류 요약과 필드 오류를 표시하고 POST를 보내지 않았다.
- 빠른 더블클릭은 생성 POST 1건으로 제한됐다.
- 미존재·비숫자 상세는 같은 조회 불가 화면을 사용했다.
- 공개 상품 GET 장애는 약 60초 후 오류 상태로 전환됐고 명시적 재시도로 회복했다. 오류 노출 지연은 Nginx timeout 경계로 남는다.
- 다른 회원·비fixture 데이터는 생성하거나 삭제하지 않았다.

## 자동 검증

| 검증 | 결과 |
| --- | --- |
| Compose config와 네 서비스 health | 통과 |
| 공개 상품 API와 UI field 대조 | 통과 |
| `smoke.ps1 -Scenario Empty` (reset=true) | 최초 실행은 shell credential 환경 변수 미전달로 중단, 값을 출력하지 않는 process-local 전달로 집중 수정 후 통과 |
| `smoke.ps1 -Scenario Empty` (reset=false 복원 후) | 통과 |
| `npm test` | 12 tests 통과 |
| Backend `AuthIntegrationTests`, `SubscriptionApiIntegrationTests` | 로컬 JDK 25 부재로 task 시작 전 미실행 |
| PR #45 Repository Validation `29342595381` | 검증 당시 Application validation과 conventions 성공 이력. 현재 QA PR 상태가 아님 |
| FOUNDATION-004 산출물 validator | 통과 |
| PR 본문 UTF-8 validator | 통과 |
| `git diff --check` | 통과 |

## 실행하지 못한 검증과 이유

- session cookie 원문 또는 저장소를 읽지 않는 원칙 때문에 만료 session 로그아웃을 브라우저에서 만들지 않았다.
- CSRF token을 읽거나 변조하지 않아 `CSRF_INVALID` browser flow를 만들지 않았다.
- 생성 POST timeout을 안전하게 주입할 승인된 test hook이 없어 실행하지 않았다.
- 타인 소유 상세는 다른 회원 데이터를 새로 만들지 않는 경계 때문에 browser에서 실행하지 않았다.
- 목록·상세 GET 장애는 상품 목록과 동일한 환경 중단을 반복하지 않았다.
- 브라우저 제어 도구의 keypress가 focus 이동·radio 선택을 발생시키지 않아 keyboard-only 전체 순회를 완료하지 못했다.
- Backend 대상 테스트는 로컬 Gradle이 요구하는 JDK 25 toolchain을 찾지 못했고 자동 다운로드 저장소도 설정되지 않아 실행 전 중단됐다. 의존성이나 toolchain 설정은 변경하지 않았다.

## 환경 중단과 재검증

- Backend 장애 복구 뒤 Docker Desktop Linux engine pipe가 일시적으로 사라져 `localhost:8080`과 Docker CLI가 응답하지 않았다.
- `docker desktop status`가 `starting`에서 `running`으로 복구된 뒤 PawCycle Compose만 `up --detach --wait --wait-timeout 120`으로 다시 시작했다.
- `board-mysql-dev`는 직접 조작하지 않았고 복구 전후 동일 container ID였다. 다만 Docker engine 재기동으로 uptime은 초기화됐다.
- 복구 뒤 네 PawCycle 서비스 healthy, 동일 MySQL named volume, 재시작 전 구독 보존, reset true와 false 복원, 최종 browser 빈 상태를 다시 확인했다.

## 결함과 심각도

- 재현 가능한 제품 결함: 없음
- P0·P1: 없음
- Bug report와 QA→구현 역할 인수인계: 작성하지 않음
- 환경 재기동과 keyboard-only 미실행은 제품 결함으로 단정하지 않고 남은 위험으로 기록한다.

## 적용 방법

- 제품 적용 변경은 없다.
- QA 재현은 `docs/qa/FOUNDATION-004/first-mvp-browser-test-plan.md`와 `docs/runbook/FOUNDATION-004-local-integration.md`를 따른다.
- 실제 credential은 `.env.local`과 현재 process 환경에만 두고 출력하지 않는다.

## 위험과 제한

- keyboard-only 전체 흐름, session 만료 logout, CSRF_INVALID와 생성 POST timeout은 실제 browser 증거가 없다.
- 타인 소유 상세는 기존 통합 테스트·CI 이력에 의존하며 별도 browser 계정으로 재현하지 않았다.
- Nginx 기본 timeout 때문에 Backend 단절 오류 화면이 약 60초 뒤 나타났다.
- Docker Desktop engine이 검증 중 한 차례 재기동되어 다른 프로젝트 container의 uptime이 초기화됐다. container ID와 PawCycle volume은 보존됐지만 무중단 상태는 아니었다.
- Backend 대상 테스트의 현재 로컬 재실행 증거는 없고 병합된 PR #45의 Application validation 성공 이력을 대조했다.

## 최종 판정

**조건부 통과**다. 첫 MVP의 공개 탐색, 인증, CSRF 정상 흐름, 구독 생성·조회, 로그아웃, 입력 경계, 반응형, 일반 재시작 보존과 reset 복원은 실제 브라우저·환경에서 통과했다. P0·P1과 승인 계약 위반은 발견되지 않았다. 다만 실제 browser keyboard-only, session 만료, CSRF_INVALID, POST timeout과 일부 GET 장애 조합은 미실행이므로 사용자 병합 검토에서 이 제한을 확인해야 한다.

## 다음 작업

- 사용자 Product Owner/Tech Lead가 조건부 통과의 미실행 항목을 수용할지 결정한다.
- 필요하면 별도 QA 세션에서 실제 keyboard-only 수동 검증과 승인된 fault-injection 수단을 사용한 session·CSRF·timeout 재검증을 수행한다.
- 제품 결함이 새로 재현되면 소유 역할에 bug report와 재검증 경로를 전달한다.

## Git 결과와 PR 상태

- 브랜치: `test/qa`
- QA 산출물 commit: `6fea992fbfd47b669a09cce6fd1dd09e16156621`
- push: `origin/test/qa` 완료
- PR: #46 `test(qa): FOUNDATION-004 첫 구독 MVP 검증`
- 대상 브랜치: `main`
- PR 상태: Open, Ready for review
- 자동 병합: 하지 않음
- 이 절의 상태 기록 commit을 포함한 최종 head, CI와 PR 상태의 권위 원본은 GitHub다.
