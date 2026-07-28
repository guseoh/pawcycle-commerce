# OPS-017 Production 인증 session smoke Runbook

## 목적과 현재 상태

이 Runbook은 production HTTPS에서 공개 경로, CSRF, session login, 현재 회원 조회, logout과 logout 뒤 기존 session 거부를 비파괴적으로 확인하는 절차다. OPS-017은 실행 script와 fake HTTP 계약 검증만 준비하며 실제 production 요청을 실행하지 않는다. 실제 사용자 실행과 결과 기록은 별도 고위험 작업 OPS-018의 명시적 승인 뒤에만 수행한다.

이 검증은 상품·구독·회원·DB 데이터를 생성·수정·삭제하지 않는다. 운영 계정 생성, 비밀번호 변경, 다른 쓰기 API, application·Nginx·DB 변경도 범위 밖이다.

## 승인된 계약

- 대상 origin은 `/opt/pawcycle/state/https-domain`에 승인된 `https://<lowercase single-label>.duckdns.org` 하나다. path, query, port, uppercase, 다중 label, 다른 DuckDNS label과 다른 host는 허용하지 않는다.
- TLS 인증서 검증을 유지하고 redirect를 따라가지 않는다. Curl의 사용자·system 기본 설정은 `--disable` 첫 인자로 무시한다.
- 공개 `/products`, `/login`, `/api/products`는 각각 `200`이어야 한다.
- 익명 `/api/auth/me`는 `401`과 `AUTH_REQUIRED`여야 한다.
- `GET /api/auth/csrf`로 초기 token과 session을 얻고 `POST /api/auth/login`에 사용한다.
- Login 전후 `JSESSIONID`와 CSRF token은 각각 달라야 한다.
- Login 응답과 인증된 `/api/auth/me`의 `memberId`는 같아야 한다.
- Login 뒤 새 CSRF token으로 `POST /api/auth/logout`을 호출하고 `204`를 확인한다.
- Logout 전 session cookie를 사용한 후속 `/api/auth/me`는 `401 AUTH_REQUIRED`여야 한다.

## OPS-018 적용 전 확인

실제 실행자는 다음을 모두 확인한다. 하나라도 불명확하면 credential을 입력하거나 요청을 시작하지 않는다.

1. OPS-018 고위험 production 검증이 사용자에게 명시적으로 승인됐다.
2. 실행할 checkout이 승인된 `main`이며 `infra/production/verify-production-auth-session-smoke.sh`의 검증이 통과했다.
3. 대상은 `/opt/pawcycle/state/https-domain`의 일반 non-symlink, mode `600` 승인 state와 정확히 같은 lowercase 단일-label DuckDNS HTTPS origin이고 현재 production release의 `/products`, `/login`, `/api/products`를 제공한다.
4. AUTH-002~004의 session·CSRF 계약과 현재 production application 계약이 일치한다.
5. 검증용 회원은 이미 존재하며 상품·구독·회원·DB 데이터를 변경할 필요가 없다.
6. 실행 terminal은 공유 화면 녹화, shell tracing과 credential 수집 도구를 사용하지 않는다.
7. 결과에는 단계별 PASS와 비민감 시각만 남기며 email, password, `memberId`, CSRF token, session ID, cookie와 원시 응답을 기록하지 않는다.

## 사용자 실행

Email과 password를 CLI 인자, 환경 변수, 파일 또는 shell history에 넣지 않는다. Script는 실제 `/dev/tty`에서만 email과 password를 읽고 비대화형 stdin·redirect·pipe를 거부하며 password echo를 끈다. 승인 domain state는 root-only 운영 파일이므로 기존 production script와 같이 `sudo`로 실행한다.

```bash
sudo bash infra/production/verify-production-auth-session-smoke.sh \
  https://<approved-lowercase-single-label>.duckdns.org
```

Script는 credential 입력 전에 요청 origin과 승인 domain state의 정확한 일치를 확인한다. 전용 임시 directory를 mode `700`으로 만들고 cookie·응답·CSRF header 파일을 mode `600`으로 관리한다. 정상 종료, 실패, `INT`, `TERM`에서 trap이 임시 파일과 process 내부 민감 변수를 정리한다. Ambient curlrc, `curl -k`, redirect 추적과 shell tracing은 사용하지 않는다.

성공 시 표준 출력에는 다음 단계별 PASS만 남는다.

```text
PASS public HTTPS paths
PASS anonymous session rejection
PASS login session and CSRF rotation
PASS authenticated member identity
PASS logout and stale session rejection
```

## 실제 확인 순서

1. 공개 세 경로의 `200`을 확인한다.
2. Cookie 없는 `/api/auth/me`의 `401 AUTH_REQUIRED`를 확인한다.
3. 초기 CSRF token과 session cookie를 유지해 login한다.
4. Login 응답의 `memberId`, `JSESSIONID` 회전과 login 뒤 CSRF token 회전을 메모리에서 비교한다.
5. 인증된 `/api/auth/me`의 `memberId`가 login 응답과 같은지 확인한다.
6. 회전된 CSRF token으로 logout하고 `204`를 확인한다.
7. Logout 직전 cookie 사본으로 `/api/auth/me`를 다시 호출해 `401 AUTH_REQUIRED`를 확인한다.

Script는 비교값이나 응답 본문을 출력하지 않는다. 예상하지 않은 HTTP status, 응답 shape, token·session 미회전 또는 회원 불일치가 있으면 첫 실패에서 중단한다.

## 증상과 첫 확인

실패 시 화면에 표시된 단계 이름과 비민감 오류 분류만 사용한다.

1. URL 거부: HTTPS, lowercase, 단일 label, `.duckdns.org`, path·query·port 부재와 `/opt/pawcycle/state/https-domain`의 정확한 일치를 확인한다. State를 수동 변경하거나 다른 경로로 우회하지 않는다.
2. 공개 경로 실패: 현재 release와 Nginx·application health를 기존 production 절차로 확인한다.
3. 익명 거부 실패: `/api/auth/me`의 redirect나 HTML이 아닌 `401 AUTH_REQUIRED` 계약을 확인한다.
4. CSRF·session 회전 실패: AUTH-003 설정과 현재 배포된 Backend release를 확인한다.
5. 회원 불일치: 실제 식별값을 기록하지 않고 login과 `/me` 계약 불일치로만 에스컬레이션한다.
6. Logout 또는 후속 거부 실패: 추가 쓰기 요청을 하지 않고 인증 경계 문제로 에스컬레이션한다.

원시 body, cookie jar, curl verbose 출력과 credential을 이슈·PR·보고서에 붙이지 않는다.

## 중단과 완화

Application·인증 API·Nginx·DB 계약 변경, 운영 계정 생성·비밀번호 변경, 다른 쓰기 API, TLS 우회, redirect 추적 또는 실제 식별값 기록이 필요하면 즉시 중단한다. 같은 실행 안에서 원인을 추측해 반복 login하거나 application·DB를 변경하지 않는다.

공개 경로나 인증 계약이 실패하면 기존 production health·release 절차로 현재 상태만 읽기 전용 확인하고 사용자/Tech Lead에 에스컬레이션한다. 이 smoke는 자동 remediation을 수행하지 않는다.

## 정리와 복구·rollback

성공 흐름은 logout과 기존 session 거부까지 확인하므로 별도 application·DB rollback이 없다. Script는 모든 종료 경로에서 로컬 cookie·응답·header 임시 파일과 민감 변수를 삭제한다.

Login 성공 뒤 logout 전에 요청 실패나 signal이 발생하면 로컬 cookie는 삭제되지만 이미 만들어진 서버 session을 추가 network 요청으로 정리하지 않는다. 해당 session은 서버 만료 정책까지 남을 수 있다. 실제 값을 복구하거나 로그로 추출하지 말고 비민감 실패 시각과 단계만 기록한 뒤 사용자/Tech Lead가 재실행 여부를 결정한다.

저장소 준비 결함은 일반 revert PR로 복구한다. Production DB restore, schema downgrade, volume 삭제, 운영 계정 변경은 이 Runbook의 복구 수단이 아니다.

## OPS-018 비민감 증거

별도 승인된 실제 실행 뒤에는 다음만 OPS-018 고위험 보고서에 기록한다.

- 사용자 승인과 실행 시각
- 다섯 단계 PASS 여부
- 실패가 있었다면 식별값 없는 단계와 분류
- application·DB 데이터 쓰기 미실행 여부
- logout 완료 또는 logout 전 실패로 남은 session 만료 위험

Workflow run 번호, 현재 check 개수, email, password, `memberId`, CSRF token, session ID, cookie, domain과 원시 응답은 저장소 문서에 고정하지 않는다.

## 에스컬레이션

현재 API 계약만으로 검증할 수 없거나 승인 domain state를 읽을 수 없거나 credential을 실제 TTY 외의 argv·환경 변수·일반 파일로 전달해야 하거나 실제 데이터 쓰기가 필요하면 OPS-017/018 범위를 확대하지 않는다. 사용자/Tech Lead가 별도 작업과 위험 수용 여부를 결정한다.
