# FOUNDATION-004 Platform/SRE → QA 인수인계

## 전달 목적

QA가 실제 Secret을 문서에 남기지 않고 첫 구독 MVP를 한 브라우저 origin에서 반복 검증할 수 있도록 로컬 통합 환경, fixture, reset과 중단 기준을 전달한다.

## 다음 역할

QA Engineer가 Runbook으로 환경을 준비하고 실제 브라우저에서 공개 상품, session·CSRF와 구독 수직 흐름을 독립 검증한다.

## 입력 문서

- `docs/runbook/FOUNDATION-004-local-integration.md`
- `docs/handoffs/FOUNDATION-004/be-to-sre.md`
- `docs/handoffs/FRONTEND-001/fe-to-qa.md`
- `docs/reports/FOUNDATION-004/sre-report.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`

## 사용 가능한 결과

- Docker Compose project `pawcycle-local-integration`
- MySQL 8.4.10, Java 25 Backend, Node.js 24 Frontend와 Nginx reverse proxy
- Backend 한 인스턴스와 MySQL named volume
- 같은 origin의 Frontend와 `/api/**`
- QA 회원·공개 상품·구독 가능 SKU bootstrap
- Full, Preserved와 Empty smoke 시나리오
- 시작·종료·로그·reset·전체 삭제·장애 진단·롤백 Runbook

## 단일 origin 접속 구조

- 기본 Frontend: `http://localhost:8080/products`
- API: 같은 origin의 `/api/**`
- `PAWCYCLE_LOCAL_HTTP_PORT`를 변경했다면 Frontend와 API에 같은 포트를 사용한다.
- Frontend는 상대 경로 `/api/**` 계약을 유지하며 CORS 예외를 추가하지 않는다.

## 환경 시작과 종료

저장소 루트에서 시작한다.

```powershell
Set-Location infra\local-integration
Copy-Item .env.example .env.local
docker compose --env-file .env.local config --quiet
docker compose --env-file .env.local up --detach
docker compose --env-file .env.local ps
```

`.env.local`의 placeholder를 안전한 로컬 값으로 바꾸되 실제 값은 이 문서, 이슈, PR, 로그와 화면 캡처에 남기지 않는다. 모든 서비스가 `healthy`일 때 브라우저 검증을 시작한다.

일반 종료와 재시작은 데이터를 보존한다.

```powershell
docker compose --env-file .env.local stop
docker compose --env-file .env.local start
```

`down` 뒤 `up --detach`도 named volume을 보존한다. `down --volumes`는 명시적 전체 삭제이므로 일반 QA 종료에 사용하지 않는다.

## QA fixture 기대값

- QA email local-part: `qa-foundation-004`
- 공개 상품: `[QA FOUNDATION-004]` 예약 접두사를 사용하는 상품 하나
- 구독 가능 SKU: `[QA FOUNDATION-004] 2kg` 하나
- SKU 가격: `19900.00`
- 제공된 배송 주기 중 하나로 수량 1~10의 구독 생성 가능
- 같은 credential의 반복 기동에서 회원·상품·SKU가 중복 생성되지 않음

실제 email 전체 값과 password는 `.env.local` 또는 현재 프로세스 환경 변수로만 전달한다.

## 빈 구독 상태 reset과 false 복원

1. `.env.local`의 `PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS`를 `true`로 바꾼다.
2. Backend와 proxy를 재생성한다.
3. Empty smoke 또는 브라우저에서 내 구독 목록이 비어 있는지 확인한다.
4. 즉시 reset 값을 `false`로 복원한다.
5. Backend와 proxy를 다시 재생성한다.

```powershell
docker compose --env-file .env.local up --detach --force-recreate backend proxy
powershell -NoProfile -File .\smoke.ps1 -Scenario Empty -BaseUri http://localhost:8080
```

reset은 예약된 QA 회원의 구독만 삭제해야 한다. 다른 회원이나 비fixture 데이터를 직접 정리하지 않는다.

## QA 브라우저 검증 범위

1. 비회원 공개 상품 목록과 상세, SKU 가격·구독 가능 여부
2. 보호 화면 접근 시 로그인 이동과 승인된 내부 GET 화면 복귀
3. 로그인 성공, 현재 회원 표시와 로그아웃
4. 공개 상품 진입만으로 CSRF를 선취득하지 않는지 확인
5. 로그인·로그아웃·구독 생성 직전의 CSRF 흐름
6. 구독 가능 SKU, 수량과 배송 주기를 사용한 구독 생성
7. 생성 뒤 내 구독 목록·상세와 서버 날짜·가격 표시
8. reset 뒤 빈 구독 상태
9. desktop·좁은 viewport·keyboard-only, label·fieldset·focus-visible·skip link
10. 실제 password, session cookie와 CSRF token이 화면 캡처·로그·버그 리포트에 없는지 확인

## 검증 포인트

- `mysql`, `backend`, `frontend`, `proxy`가 모두 healthy인가
- 한 URL에서 Frontend와 `/api/**`가 함께 동작하는가
- session cookie가 same-origin으로 유지되고 CORS 변경이 필요하지 않은가
- FOUNDATION-004 상품과 SKU가 반복 기동에도 하나씩인가
- reset `false`에서 QA 구독이 보존되는가
- reset `true`에서 QA 구독만 사라지는가
- reset 뒤 설정을 다시 `false`로 복원했는가
- 일반 종료·재시작에서 MySQL volume이 보존되는가
- Backend가 한 인스턴스인가

## 미결정 사항 또는 승인 필요 항목

- QA의 최종 통과·실패 판정
- 실제 제품 결함이나 승인 계약 위반 발견 시 담당 역할의 수정 승인
- 전체 MySQL volume 삭제가 필요한 경우 사용자 승인
- 이 PR의 병합 여부

## 중단 조건

- 실제 Secret, session ID 또는 CSRF token을 문서·로그·PR에 기록해야 하는 상황
- Backend·Frontend 제품 코드나 API·DB·Flyway·인증·CSRF·CORS 계약 변경이 필요한 상황
- fixture 충돌을 덮어쓰기 또는 기존 회원·비fixture 데이터 삭제로 해결해야 하는 상황
- `local-integration`과 production·test profile이 함께 활성화된 상황
- 여러 Backend 인스턴스의 동시 최초 bootstrap이 필요한 상황
- 필수 브라우저 시나리오에서 제품 결함이나 계약 위반이 재현되는 상황

## 남은 위험 또는 주의 사항

- local bootstrap은 Backend 한 인스턴스의 순차 최초 기동만 지원한다.
- 빈 DB에서 동시 최초 bootstrap은 직렬화되지 않으며 충돌 시 한 인스턴스가 시작 실패할 수 있다.
- reset을 `true`로 둔 채 재기동하면 QA 회원 구독이 매번 삭제되므로 즉시 `false`로 복원한다.
- 이 Compose와 Nginx는 local-only이며 production 배포 구조가 아니다.
- smoke 성공은 실제 브라우저의 반응형·접근성·사용자 흐름 검증을 대체하지 않는다.
