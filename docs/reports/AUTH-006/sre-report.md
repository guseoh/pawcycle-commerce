# AUTH-006 익명 CSRF 세션 기준선 보고서

## 작업 정보

- 작업 ID: AUTH-006
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 로컬 실행 범위: 격리 측정
- 역할: Backend, Platform/SRE
- 작업 브랜치: `ops/sre/AUTH-006-csrf-session-baseline`
- 대상 브랜치: `main`

## 작업 목적

AUTH-003이 승인한 익명 `GET /api/auth/csrf`의 세션 생성 동작을 변경하지 않고, fresh client 요청과 동일 anonymous session 재사용 요청의 세션 생성 차이를 재현 가능한 로컬 기준선으로 측정한다. 측정 결과는 향후 완화책의 필요성과 별도 검증 범위를 판단하기 위한 입력이며, 이번 작업 자체는 rate limit이나 인증 구조를 변경하지 않는다.

## 승인 입력과 추적성

- 현재 사용자 지시를 고위험 저장소 변경과 로컬 격리 측정 승인 근거로 사용했다.
- AUTH-003의 HttpSession 인증, `HttpSessionCsrfTokenRepository`, 익명 CSRF endpoint, session cookie와 CSRF lifecycle 계약을 기준으로 삼았다.
- Production 실행, Secret 접근, 운영 DB 접근, merge는 승인 범위에 포함되지 않았다.

## 포함 범위

- fresh client와 same-session cohort의 동일 요청 수 비교
- session-created, active-session, JVM heap, process CPU, cohort duration의 aggregate 수집
- measurement-only Compose overlay의 Tomcat session metric 활성화
- loopback-only 임의 Backend port와 전용 disposable MySQL volume
- 민감한 HTTP 응답·cookie·session identifier가 evidence에 포함되지 않는 fail-closed 검증
- 작은 기본 workload와 제한된 요청 수 parameter
- Harness regression과 이 보고서

## 제외 범위

- CSRF endpoint rate limit, cache, stateless token, JWT/OAuth2 또는 인증 구조 변경
- Production 기본 metric 설정과 Production observability 계약 변경
- API, DB schema, migration, Frontend와 제품 코드 변경
- capacity, 비용, SLO 또는 완화 threshold 확정
- Production, AWS, 운영 DB, Secret, credential 실행
- merge와 CodeRabbit 수동 호출

## 현재 lifecycle 경계

익명 `GET /api/auth/csrf`는 `HttpSessionCsrfTokenRepository`를 통해 CSRF token을 세션에 저장하므로 fresh client마다 anonymous session이 생성된다. 동일 client가 `JSESSIONID` cookie를 재사용하면 같은 session을 사용한다. Harness는 이 계약을 바꾸지 않고 두 cohort를 분리해 관찰한다.

측정 과정에서 응답 JSON이 token 필드를 가진 성공 응답인지만 확인한다. token 값과 cookie 값은 출력하거나 파일에 기록하지 않는다. evidence는 요청 수, 성공·실패 수, duration과 runtime aggregate metric만 포함한다.

## 측정 환경과 방법

- workload identity: `auth006-anonymous-csrf-session-baseline-local-v1`
- endpoint: local isolated Backend의 `/api/auth/csrf`
- 기본 workload: cohort당 50회, warm-up 3회
- 실행 순서: reused-session cohort 후 fresh-client cohort
- reused-session: 하나의 in-memory cookie jar를 요청 전체에서 재사용
- fresh-client: 요청마다 cookie 저장소가 없는 새 HTTP opener 사용
- metric source: local runtime의 `/actuator/prometheus`
- Tomcat metric 이름: 실제 runtime에서 `tomcat_sessions_*` 표본을 발견하고 누락·중복이면 실패
- 격리: 고정 Compose project, loopback-only 임의 port, task 전용 MySQL volume
- evidence: OS 임시 디렉터리의 aggregate JSON만 허용

## Measurement-only 설정

`server.tomcat.mbeanregistry.enabled=true`에 대응하는 환경 변수는 AUTH-006 local Compose overlay의 Backend service에만 둔다. Backend `application.properties`와 `infra/production/**`에 같은 설정이 들어가면 synthetic regression이 실패한다. 이 설정은 Production 기본값이나 장기 metric contract가 아니다.

## 주요 결과

저장소·합성 검증은 통과했지만 Docker Desktop host 오류 때문에 실제 로컬 기준선 수치는 생성하지 않았다.

### 저장소와 합성 검증

- Python syntax: PASS
- AUTH-006 unit/synthetic/fail-closed regression: PASS
- repository contract validation: PASS
- Docker Compose rendered model validation: PASS
- `git diff --check`: PASS
- `SessionCookieConfigurationTests`: PASS
- `AuthIntegrationTests`, `SecurityFoundationIntegrationTests`: 로컬 DB URL 부재로 ApplicationContext 생성 전 실패

### 로컬 격리 기준선

상태: **measurement-not-run**

Docker Desktop 4.48.0 시작을 시도했으나 기존 host 환경에서 inference socket 초기화 오류가 발생해 Linux engine이 시작되지 않았다. `docker-desktop` WSL 배포판은 `Stopped` 상태였고 Docker API가 열리지 않아 Compose `up`과 실제 HTTP workload는 실행되지 않았다. 따라서 session, heap, CPU, duration의 실제 수치를 생성하거나 추정하지 않았다.

Compose config 렌더링은 daemon 없이 성공했으며 project identity, loopback publication, measurement setting과 전용 volume 이름을 검증했다. Docker engine이 시작되기 전에 실패했으므로 AUTH-006 container와 volume은 생성되지 않았다.

## 자동 회귀 검증

- Production 기본 설정과 `infra/production/**`에 measurement setting이 없음을 확인한다.
- Compose project identity, loopback-only Backend publication, QA bootstrap 비활성화와 전용 volume을 확인한다.
- Tomcat session-created/active-current, JVM heap과 process CPU metric이 없거나 모호하면 실패한다.
- 모든 요청의 성공, 두 cohort의 동일 요청 수, fresh session-created delta와 성공 요청 수의 일치, reused delta 1을 확인한다.
- session/CSRF/cookie/credential 관련 evidence key와 대표 header 값 패턴을 거부한다.
- 결과 디렉터리를 OS 임시 디렉터리 아래로 제한한다.
- 실행 성공 뒤에도 isolated Compose cleanup이 실패하면 evidence를 기록하지 않고 전체 실행을 실패 처리한다.

## CHANGE / KEEP / DEFER

- CHANGE: local measurement overlay, aggregate-only measurement harness, synthetic/fail-closed Harness regression을 추가했다.
- KEEP: AUTH-003 인증·세션·CSRF·cookie 계약, API 응답, DB schema, Production 기본 설정과 제품 코드를 유지했다.
- DEFER: 실제 baseline 수치와 완화책 결정은 Docker engine 복구 후 동일 harness의 성공 실행 및 추가 evidence 검토까지 보류한다.

## 해석 경계와 다음 후보

실제 측정값이 없으므로 fresh client의 운영 영향 크기나 완화 필요성을 결론 내릴 수 없다. 측정이 성공하더라도 단일 노트북의 순차 workload는 Production capacity·비용·SLO 또는 rate-limit threshold 근거가 아니다.

후속 판단에서는 per-IP edge protection을 후보로 검토할 수 있지만 shared NAT 사용자와 정상 CSRF lifecycle에 미치는 영향을 별도로 검증해야 한다. 동시 요청, 시간 경과에 따른 session 회수, 실제 traffic 분포와 비용 관측 없이 threshold를 확정하지 않는다.

## 위험과 제한

- 실제 local runtime metric과 HTTP lifecycle은 아직 검증되지 않았다.
- dynamic Tomcat metric discovery와 cleanup 경로는 합성·Compose model 수준에서만 검증됐다.
- 순차적인 작은 workload는 concurrency, GC pressure, session timeout 후 회수와 장시간 CPU 비용을 대표하지 않는다.
- Actuator aggregate는 application 내부 상관관계를 보여주지만 외부 proxy와 Production topology를 대표하지 않는다.
- Docker Desktop host 오류를 해결하기 위해 user setting, socket 파일 또는 Docker data를 변경·삭제하지 않았다.

## 실행하지 못한 검증과 이유

- 실제 AUTH-006 local isolated measurement: Docker Desktop host 초기화 오류로 engine unavailable
- Backend/MySQL integration lane과 Backend 전체 test: local MySQL runtime unavailable
- Repository Validation 전체와 PR Metadata Validation: PR 생성 후 원격 CI에서 확인
- Production 검증: 승인 범위 밖이며 실행하지 않음

## 복구 경계

저장소 변경은 AUTH-006 overlay, harness, regression, Harness workflow 등록과 이 보고서를 revert하면 제거된다. 제품 코드, DB와 Production 설정에는 복구할 변경이 없다. 실제 측정 실행 시에는 고정 project의 `docker compose down --volumes --remove-orphans`만 사용하며 다른 project나 일반 volume을 삭제하지 않는다.
