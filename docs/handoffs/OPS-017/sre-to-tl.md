# OPS-017 Platform/SRE → Tech Lead 인수인계

## 작업 정보

- 작업 ID: OPS-017
- 작업 등급: 고위험

## 전달 목적

Production 인증 session smoke의 저장소 기반이 실제 운영 실행 전에 안전한지 Tech Lead가 판단할 수 있도록 계약, 검증 증거와 남은 위험을 전달한다.

## 대상 역할 또는 운영자

사용자/Tech Lead와 별도 OPS-018 실제 운영자다.

## 입력 문서

OPS-017 사용자 승인 명세, AUTH-002~004 인증 계약, OPS-017 Runbook과 고위험 보고서다.

## 완료된 작업

승인 DuckDNS HTTPS origin, 공개 경로, 익명 거부, CSRF·session 회전, login·`/me` 회원 일치, logout과 기존 session 거부를 검증하는 script와 fake HTTP test를 준비했다. Production validator와 Repository Validation에 계약 test를 연결했다.

## 사용 가능한 결과

Credential 비노출, TLS 검증, redirect 거부, mode `700`/`600` 임시 저장, 모든 종료 경로 정리와 단계별 PASS 출력 계약이 포함된 실행 기반이다. Fake HTTP test는 정상 흐름과 승인된 실패 시나리오를 외부 network 없이 검증한다.

## 관련 파일

- `infra/production/verify-production-auth-session-smoke.sh`
- `infra/production/test-production-auth-session-smoke.sh`
- `docs/runbook/OPS-017-production-auth-session-smoke.md`
- `docs/reports/OPS-017/sre-report.md`

## 확정된 결정

OPS-017은 저장소 준비만 완료한다. 실제 production 요청과 결과 기록은 별도 고위험 OPS-018의 명시적 사용자 승인 뒤에 수행한다. Application·DB 데이터 쓰기와 credential의 argv·환경 변수·파일 전달은 허용하지 않는다.

## 미결정 사항

실제 production 실행 시각, 사용할 기존 운영 회원, 실행 결과와 실패 시 재실행 여부는 OPS-018에서 결정한다. Credential과 실제 식별값은 저장소나 인수인계에 기록하지 않는다.

## 승인 필요 항목

Production HTTPS login·logout 요청을 시작하려면 OPS-018 고위험 사용자 승인이 필요하다. 계정 생성·비밀번호 변경, API 계약 수정이나 데이터 쓰기가 필요하면 별도 작업 승인이 필요하다.

## 검증 포인트

Tech Lead는 허용 URL 정규식, TLS·redirect 옵션, 대화형 credential 입력, CSRF header 파일과 login 표준입력, session·token 회전 비교, stale cookie 확인, trap 정리와 PASS 전용 출력을 확인한다.

## 검증 결과

Bash 문법, fake HTTP 계약 test, production contract validator, OPS-017 고위험 task artifact validator, 관련 Markdown·UTF-8, commit message 규칙과 `git diff --check`가 통과했다. GitHub의 동적 SHA·CI·review 상태는 저장소에 고정하지 않는다.

## 지켜야 할 규칙

Email·password·`memberId`·CSRF token·session ID·cookie·domain과 원시 응답을 문서·로그·PR에 남기지 않는다. TLS 검증을 끄거나 redirect를 따라가거나 다른 쓰기 API를 호출하지 않는다.

## 적용·실행 방법

OPS-017에서는 실행하지 않는다. OPS-018 승인 뒤 운영자는 Runbook의 적용 전 게이트를 확인하고 script의 대화형 prompt만 사용한다.

## 알려진 위험

실제 production 경로와 credential은 아직 검증하지 않았다. Login 뒤 logout 전 실패하면 로컬 민감 파일은 정리되지만 서버 session은 만료 전까지 남을 수 있다.

## 남은 위험과 주의 사항

OPS-018 실제 실행·독립 검증이 남았다. 이 smoke는 production DB restore, session 부하·만료, 다중 instance 공유, 상품·구독·회원 데이터 쓰기와 다른 인증 시나리오를 다루지 않는다.

## 다음 권장 작업

PR의 보안 경계와 Repository Validation을 사람이 검토하고 병합 여부를 결정한다. 병합 뒤 실제 production 검증이 필요하면 OPS-018을 별도로 승인한다.

## 완료 조건

OPS-017을 production 검증 완료로 확대하지 않고, 저장소 준비와 OPS-018 실행 경계가 분명한 상태로 승인된다.

## 중단 조건

실제 network 요청, credential 기록, 운영 계정 변경, application·Nginx·DB 변경, 다른 쓰기 API 또는 승인 범위 밖의 production 상태 변경이 필요하면 중단한다.
