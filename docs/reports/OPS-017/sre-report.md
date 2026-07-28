# OPS-017 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-017
- 작업 등급: 고위험
- 역할: Platform/SRE

## 작업 목적

Production HTTPS에서 공개 상품, CSRF, session login, 현재 회원 조회, logout과 logout 뒤 기존 session 거부를 비파괴적으로 검증할 저장소 기반을 준비한다. OPS-017은 실제 production 실행이나 검증 완료 기록이 아니며 별도 OPS-018 승인이 필요하다.

## 입력 문서

사용자 승인 OPS-017 명세, AUTH-002 session 인증 제안, AUTH-003 승인 입력, AUTH-004 Backend 구현·QA 인수인계, 기존 production script·validator·CI 관례를 사용했다.

## 명시적 승인 근거

사용자는 저장소 script, fake HTTP 계약 test, production validator 연결, Runbook, 고위험 보고서와 Tech Lead 인수인계 준비를 승인했다. 실제 production login·logout과 외부 network 요청은 명시적으로 제외하고 별도 OPS-018 사용자 승인 작업으로 분리했다.

## 변경 범위

- 승인된 DuckDNS HTTPS origin과 인증 순서를 강제하는 production smoke script
- 외부 network 없이 정상·오류·정리 경계를 검증하는 fake curl test
- Production contract validator와 Repository Validation 연결
- OPS-017 Runbook, 고위험 보고서와 Tech Lead 인수인계

## 변경하지 않은 범위

Application·인증 API·Nginx·DB·Compose 동작, dependency, 운영 계정, AWS·CloudWatch·SNS, production DB restore와 아키텍처의 운영 검증 상태를 변경하지 않았다. 상품·구독·회원·DB 쓰기 API를 추가하거나 호출하지 않았다.

## 주요 결과

Script는 lowercase 단일-label DuckDNS HTTPS 형식과 `/opt/pawcycle/state/https-domain`의 승인 origin이 정확히 일치할 때만 credential을 요청한다. Curlrc를 첫 `--disable` 인자로 무시하고 TLS 검증을 유지하며 redirect를 따르지 않는다. 공개 세 경로, 익명 인증 거부, CSRF·session 회전, login과 `/me` 회원 일치, logout과 기존 session 거부를 순서대로 확인한다.

Email과 password는 실제 `/dev/tty`에서만 받고 비대화형 stdin을 거부하며 password echo를 끈다. CSRF header는 mode `600` 임시 파일, login JSON은 표준입력으로 `curl`에 전달한다. 성공 출력에는 다섯 단계 PASS만 남기고 식별값과 응답 본문을 출력하지 않는다.

## 결정 상태

저장소 기반은 준비됐지만 production 인증 session smoke는 아직 실행하지 않았다. 실제 production 결과와 Verified 판단은 OPS-018의 명시적 승인·실행·독립 검증 뒤에만 기록한다.

## API 영향

API 계약 변경은 없다. 기존 `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout` 계약의 운영 검증 소비자만 준비했다.

## DB 영향

DB·schema·Flyway·volume 변경은 없다. Smoke는 상품·구독·회원·DB 데이터를 생성·수정·삭제하지 않는다.

## 보안 영향

Credential을 CLI 인자, 환경 변수, 저장소 파일, command history와 비대화형 stdin으로 받지 않는다. 승인 production domain state와 정확히 일치하기 전에는 credential을 요청하지 않는다. 임시 directory는 mode `700`, cookie·응답·header 파일은 mode `600`이며 정상·실패·`INT`·`TERM`에서 정리한다. Ambient curlrc, TLS 우회, cross-host redirect, shell tracing, 원시 응답 출력은 금지했다.

## 운영 영향

OPS-017 자체는 production에 요청하거나 운영 상태를 바꾸지 않는다. OPS-018 실행 시 login과 logout으로 session 상태만 만들고 폐기하며 application·DB 데이터 쓰기는 하지 않는다.

## 성능 영향

성능 변경이나 측정은 없다. 실제 요청 수와 응답 시간은 OPS-017에서 측정하지 않았다.

## 실행한 검증

- 새 runtime·fake HTTP script Bash 문법 검사: 통과
- OPS-017 fake HTTP 계약 test: 통과
- Production contract validator: 통과
- OPS-017 고위험 task artifact validator: 통과
- 관련 Markdown·UTF-8 검사: 통과
- Commit message 규칙 검사: 통과
- `git diff --check`: 통과
- Repository Validation은 GitHub Checks를 권위 원본으로 확인

## 적용 전 검증

최신 `main`과 깨끗한 `ops/sre` 관계, 열린 역할 PR 부재, OPS-017 미사용을 확인했다. AUTH-002~004에서 CSRF header, session rotation, login·`/me` 응답, logout 뒤 `401 AUTH_REQUIRED` 계약을 대조했다. 실제 credential과 production origin은 입력하거나 조회하지 않았다.

## 적용 후 검증

Fake curl에서 공개 경로, 익명 거부, CSRF와 session 회전, `Secure`·`HttpOnly` cookie, 회원 일치, logout과 기존 session 거부의 정상 흐름을 확인했다. 비 HTTPS·미승인 host·승인 state와 다른 DuckDNS host·비 TTY 입력, `401`의 다른 오류 code, CSRF 누락·미회전, session 미회전, cookie 보안 속성 누락, 회원 불일치, logout 실패, logout 뒤 인증 유지와 중간 요청 실패가 안전하게 실패하고 임시 파일이 제거됨을 확인했다. 사용자 curlrc에 금지 설정이 있어도 모든 curl 호출의 첫 인자가 `--disable`인지 확인했다.

## 독립 검증

Production contract validator는 script의 URL·승인 domain state·TTY·curlrc 차단·TLS·credential 전달·임시 파일·trap·인증 순서 계약과 CI test 연결을 별도로 검사한다. GitHub Repository Validation 결과는 저장소 문서에 동적 run 번호나 check 개수를 고정하지 않고 Checks를 권위 원본으로 확인한다. 실제 production 독립 검증은 OPS-018에서 사용자/Tech Lead가 수행해야 한다.

## 실행하지 못한 검증과 이유

실제 production HTTPS 요청, login·logout, 운영 credential 입력과 외부 network 접근은 명시적 제외 범위라 실행하지 않았다. Application 전체 test는 application/API 동작을 변경하지 않고 fake HTTP 경계와 정적 production 계약만 변경해 반복하지 않았다.

## QA 필요 여부

별도 QA 문서는 작성하지 않는다. 기존 AUTH-004 QA 인수인계를 입력 계약으로 사용하고 OPS-017의 운영 script 경계는 fake HTTP test와 production validator로 검증한다. 실제 사용자 경로 확인은 OPS-018의 고위험 독립 검증 대상이다.

## QA 문서 경로 또는 생략 사유

새 사용자 기능이나 API 동작을 구현하지 않고 승인된 인증 계약을 소비하는 운영 smoke 기반만 준비하므로 별도 QA 산출물을 생략한다.

## AI 리뷰 반영 여부

CodeRabbit·Codex Review에서 확인된 실제 TTY 제한, 승인 production domain state와의 정확한 일치, curlrc 비활성화 지적을 반영한다. 최종 thread 해결 상태는 PR을 권위 원본으로 확인한다.

## AI 리뷰 미반영 항목과 이유

응답 추출을 임의의 추가 필드와 pretty-print까지 허용하라는 제안은 현재 AUTH DTO의 단일 필드 JSON shape를 정확히 검증하는 경계를 완화하므로 반영하지 않았다. Curl stderr를 출력하라는 제안은 실제 production domain 등 운영 식별자가 terminal·수집 로그에 남을 수 있어 비민감 단계 오류만 출력한다는 승인 경계와 충돌하므로 반영하지 않았다.

## 적용 방법

OPS-017 PR은 저장소 준비만 제공한다. 실제 운영자는 병합 뒤에도 `docs/runbook/OPS-017-production-auth-session-smoke.md`의 적용 전 게이트와 별도 OPS-018 승인을 충족한 경우에만 대화형으로 실행한다.

## 복구·롤백 증거

OPS-017에서는 실제 production 요청과 상태 변경이 없어 운영 rollback이 필요하지 않다. Fake test는 모든 성공·실패 경로에서 전용 임시 directory가 비워지는지 확인한다. 저장소 변경은 일반 revert PR로 복구할 수 있다.

## 위험과 제한

실제 production TLS, cookie 속성, login 계정, session 만료와 Nginx 경로는 아직 이 script로 검증하지 않았다. Login 성공 뒤 logout 전에 실패하면 로컬 credential·cookie는 정리되지만 서버 session은 만료 전까지 남을 수 있다.

## 남은 위험

OPS-018 승인·실행·독립 검증이 남았다. 이 smoke는 실제 production DB restore, 인증 부하·성능, 장시간 session 만료, 다중 instance session 공유와 application 데이터 쓰기를 검증하지 않는다.

## 다음 작업

Tech Lead가 저장소의 보안 경계와 fake HTTP 증거를 검토한 뒤 병합 여부를 판단한다. 실제 production 검증은 별도 OPS-018로 승인·수행·기록한다.

## Git 결과

최신 `main`에서 준비한 `ops/sre`에 OPS-017 구현과 review 반영 commit을 기록해 `origin/ops/sre`에 push했다. 정확한 head와 commit 목록은 Git을 권위 원본으로 확인한다.

## PR 결과

`main` 대상 PR #69를 생성해 Ready 상태로 유지한다. 자동 병합하지 않으며 head, review thread와 Repository Validation의 동적 상태는 GitHub를 권위 원본으로 확인한다.
