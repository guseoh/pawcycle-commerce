# FOUNDATION-004 로컬 통합 환경 Runbook

## 목적과 경계

MySQL 8.4, Java 25 Backend, Node.js 24 Frontend와 Nginx를 Docker Compose로 실행해 `http://localhost:8080` 한 origin에서 Frontend와 `/api/**`를 검증한다. 이 구성은 로컬 개발·QA 전용이며 production 배포, TLS 또는 CORS 정책을 정의하지 않는다. Backend는 한 인스턴스만 실행한다.

## 사전 요구사항

- 저장소 루트에서 명령을 실행할 수 있는 로컬 checkout
- Docker Desktop Linux Engine과 Docker Compose v2
- 로컬 포트 `8080` 또는 사용자가 선택한 대체 포트
- Git에 기록하지 않을 MySQL과 QA bootstrap credential

연결 확인:

```powershell
docker version
docker compose version
```

Docker Server의 `Os`가 `linux`여야 한다.

## 환경 변수 준비

```powershell
Set-Location infra\local-integration
Copy-Item .env.example .env.local
```

`.env.local`의 모든 placeholder를 로컬 값으로 교체한다. QA email의 local-part는 `qa-foundation-004`여야 한다. 실제 email, password, JDBC credential 또는 전체 connection string은 명령 출력, 로그, 문서와 PR에 복사하지 않는다. 루트 `.gitignore`는 `.env.local`을 제외한다.

평상시에는 다음 설정을 유지한다.

```text
PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS=false
```

로컬 HTTP cookie 완화인 `SESSION_COOKIE_SECURE=false`는 Compose의 `local-integration` Backend에만 고정되어 있으며 production·test profile과 함께 사용하지 않는다.

## 최초 build와 시작

```powershell
docker compose --env-file .env.local config --quiet
docker compose --env-file .env.local pull mysql proxy
docker compose --env-file .env.local build backend frontend
docker compose --env-file .env.local up --detach
docker compose --env-file .env.local ps
```

`mysql`, `backend`, `frontend`, `proxy`가 모두 `healthy`가 될 때까지 기다린다. MySQL named volume `pawcycle-local-integration-mysql-data`는 자동으로 생성된다.

## 단일 origin과 smoke

- 기본 접속 주소: `http://localhost:8080/products`
- API 주소 구조: `http://localhost:8080/api/**`
- `PAWCYCLE_LOCAL_HTTP_PORT`를 바꿨으면 같은 포트를 smoke의 `BaseUri`에도 사용한다.

현재 shell에 `.env.local`과 동일한 QA email·password 환경 변수를 안전하게 설정한 뒤 실행한다. 값을 명령 이력에 직접 입력하거나 출력하지 않는다.

```powershell
powershell -NoProfile -File .\smoke.ps1 -Scenario Full -BaseUri http://localhost:8080
```

스크립트는 Frontend, 공개 상품 목록·상세, CSRF 획득, 로그인, 현재 회원, 구독 생성·목록·상세와 로그아웃을 같은 origin에서 확인한다. cookie와 CSRF token은 프로세스 메모리에만 유지한다. 이 smoke는 QA의 독립 브라우저 검증을 대체하지 않는다.

## 반복 시작과 fixture 멱등성

일반 재시작에서는 volume을 삭제하지 않는다.

```powershell
docker compose --env-file .env.local down
docker compose --env-file .env.local up --detach
powershell -NoProfile -File .\smoke.ps1 -Scenario Preserved -BaseUri http://localhost:8080
```

`Preserved` 시나리오는 QA 구독 보존과 FOUNDATION-004 상품·SKU fixture가 각각 하나인지를 확인한다. 빈 DB에서 여러 Backend 인스턴스를 동시에 처음 실행하는 것은 지원하지 않는다.

## 빈 구독 상태 reset과 false 복원

1. `.env.local`에서 `PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS=true`로 변경한다.
2. Backend와 proxy를 재생성하고 빈 상태를 확인한다.

```powershell
docker compose --env-file .env.local up --detach --force-recreate backend proxy
powershell -NoProfile -File .\smoke.ps1 -Scenario Empty -BaseUri http://localhost:8080
```

3. 즉시 `.env.local`의 reset 값을 `false`로 복원한다.
4. Backend와 proxy를 다시 재생성한다.

```powershell
docker compose --env-file .env.local up --detach --force-recreate backend proxy
powershell -NoProfile -File .\smoke.ps1 -Scenario Empty -BaseUri http://localhost:8080
```

reset은 예약된 QA 회원의 구독만 삭제한다. 다른 회원이나 비fixture 데이터를 삭제해야 한다면 중단하고 Backend 담당자에게 전달한다.

## 로그 확인

최근 범위만 확인하고 credential 또는 인증 값을 복사하지 않는다.

```powershell
docker compose --env-file .env.local logs --tail 200 mysql
docker compose --env-file .env.local logs --tail 200 backend
docker compose --env-file .env.local logs --tail 200 frontend
docker compose --env-file .env.local logs --tail 200 proxy
```

## 종료, 재시작과 데이터 보존

```powershell
docker compose --env-file .env.local stop
docker compose --env-file .env.local start
```

또는 `down` 뒤 `up --detach`를 사용해도 named volume은 유지된다. `.env.local`의 reset 값이 `false`인지 먼저 확인한다.

## 명시적 전체 삭제

다음 명령만 MySQL named volume까지 삭제한다. 필요한 데이터가 없다는 사실을 직접 확인한 경우에만 실행한다.

```powershell
docker compose --env-file .env.local down --volumes
```

일반 종료·재시작에는 `--volumes`를 사용하지 않는다.

## 장애 진단

| 증상 | 확인 | 완화 |
| --- | --- | --- |
| port bind 실패 | `docker compose --env-file .env.local ps`와 사용 중인 로컬 포트 | `PAWCYCLE_LOCAL_HTTP_PORT`를 비어 있는 포트로 변경하고 재생성 |
| image pull 실패 | Docker 로그인·네트워크와 공식 태그 접근 | 네트워크 복구 뒤 실패한 `pull` 또는 `build` 한 번 재실행 |
| MySQL unhealthy | `logs --tail 200 mysql`, volume 권한과 환경 변수 존재 여부 | 실제 값을 출력하지 말고 변수 존재 여부만 확인; 불필요한 volume 삭제 금지 |
| Flyway·Backend 실패 | `logs --tail 200 backend`, MySQL healthy, profile과 enable flag | schema·migration을 임의 변경하지 말고 Backend에 전달 |
| bootstrap 충돌 | Backend 로그의 충돌 영역과 QA email local-part | 기존 row를 덮어쓰거나 삭제하지 말고 Backend에 전달 |
| Frontend unhealthy | `logs --tail 200 frontend`, Backend와 무관한 `/products` 응답 | 제품 코드 변경 없이 Frontend에 전달 |
| `/api/**` 502 | `backend` health와 `proxy` 로그 | Backend가 healthy인 뒤 proxy를 재생성 |
| cookie·CSRF 실패 | 단일 origin 사용 여부, reset이 아닌 profile·cookie 설정 | CORS·인증 정책을 우회하지 말고 Backend 또는 QA에 전달 |

## 롤백과 원상 복구

1. `docker compose --env-file .env.local down`으로 로컬 stack을 내린다. 이 명령은 데이터를 보존한다.
2. `.env.local`을 삭제하거나 안전한 로컬 보관소로 이동한다.
3. 변경 자체를 되돌릴 때는 이 작업의 Git 변경만 revert하고 제품 코드는 수정하지 않는다.
4. volume 삭제가 승인된 경우에만 별도 `down --volumes` 절차를 사용한다.

제품 결함, API·DB·인증·CSRF·CORS 계약 변경 필요, 비fixture 데이터 삭제 필요 또는 실제 Secret 기록 필요가 확인되면 작업을 중단하고 담당 역할과 사용자에게 에스컬레이션한다.
