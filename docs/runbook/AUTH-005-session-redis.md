# AUTH-005 세션 Redis Runbook

## 목적과 경계

[AUTH-005 ADR](../adr/AUTH-005-spring-session-redis.md)의 세션 저장소(Spring Session Redis)를 local-integration에서 확인하는 절차와, 운영에서 켜기 전에 필요한 결정·작업을 정리한다.

- local-integration은 `PAWCYCLE_SESSION_STORE=redis`로 세션 전용 Redis(`session-redis`)를 쓴다.
- 운영 기본값은 `memory`다. 이 문서의 운영 절은 **준비 목록**이며, 운영 실행 승인이 아니다.

## 설정

| 환경 변수 | 기본값 | 뜻 |
| --- | --- | --- |
| `PAWCYCLE_SESSION_STORE` | `memory` | `memory`(Tomcat 메모리) 또는 `redis`. 그 밖의 값이면 기동 실패 |
| `PAWCYCLE_SESSION_REDIS_HOST` | `127.0.0.1` | 세션 전용 Redis host. 캐시 Redis(`PAWCYCLE_REDIS_*`)와 다른 인스턴스를 가리킨다 |
| `PAWCYCLE_SESSION_REDIS_PORT` | `6379` | 세션 전용 Redis port |
| `PAWCYCLE_SESSION_REDIS_CONNECT_TIMEOUT` | `1s` | 연결 timeout |
| `PAWCYCLE_SESSION_REDIS_COMMAND_TIMEOUT` | `1s` | 명령 timeout |

세션 key는 `pawcycle:session:sessions:<session id>`다. 쿠키 값은 session id의 Base64다. 쿠키 값과 session id는 출력·로그·문서에 남기지 않는다.

## local-integration 구성

`infra/local-integration/compose.yaml`의 `session-redis` 서비스:

- `redis:8.10.0-alpine3.23`, `--maxmemory 64mb --maxmemory-policy noeviction`
- AOF `--appendonly yes --appendfsync everysec`, RDB 스냅숏 끔(`--save ""`)
- `mem_limit: 128m`, `cpus: 0.25`, `pids_limit: 64`, `redis-cli ping` healthcheck
- named volume `session-redis-data`(`/data`). 이름을 고정하지 않아 compose project마다 따로 생긴다(기본 project면 `pawcycle-local-integration_session-redis-data`)

backend는 `session-redis`가 healthy가 된 뒤 시작한다. 캐시용 `redis`(영속성 없음, `allkeys-lru`)는 그대로이며 세션을 넣지 않는다.

## 재시작 리허설(일회용 compose project)

공유 `pawcycle-local-integration-*` volume을 쓰지 않도록 project 이름과 MySQL volume 이름을 바꾼 일회용 project로 실행한다.

1. 저장소 밖의 임시 디렉터리에 다음 두 파일을 만든다. credential은 실행할 때 새로 만든 무작위 값이며 기록하지 않는다.
   - `.env`: `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL`(local-part `qa-foundation-004`), `PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD`
   - `override.yaml`:

     ```yaml
     volumes:
       mysql-data:
         name: pawcycle-auth005-rehearsal-mysql-data
     ```

2. backend와 의존 서비스만 띄운다(frontend·proxy 불필요).

   ```bash
   docker compose --project-name pawcycle-auth005-rehearsal --env-file <tmp>/.env -f compose.yaml -f <tmp>/override.yaml up --detach --build --wait backend
   ```

3. backend 컨테이너 안에서 `curl`로 요청한다. 순서: `GET /api/auth/csrf` → `POST /api/auth/login`(CSRF 헤더) → `GET /api/auth/csrf`(로그인 후 토큰 재발급) → `GET /api/auth/me` 200 → `session-redis`에 세션 key 존재 확인.
4. `docker compose ... restart backend` 후 healthy를 기다린다. 컨테이너 `StartedAt`이 바뀐 것을 기록한다.
5. 같은 쿠키로 `GET /api/auth/me` 200, 재시작 전에 받은 CSRF 토큰으로 쓰기 요청(`POST /api/addresses` 201, 정리용 `DELETE`) 성공, CSRF 없는 쓰기 403을 확인한다.
6. (장애 정책 확인) `session-redis`를 멈추고 쿠키 없는 `GET /api/products`, 쿠키 있는 `GET /api/auth/me`, `GET /api/auth/csrf` 결과를 기록한다. 다시 시작한 뒤 같은 쿠키로 `GET /api/auth/me`를 확인한다(AOF 복원).
7. `POST /api/auth/logout` 204 → 세션 key 삭제 → 이전 쿠키로 `GET /api/auth/me` 401.
8. 정리: `docker compose ... down --volumes`로 컨테이너·network·volume을 지우고, `docker volume ls`로 `pawcycle-auth005-rehearsal*`가 남지 않았는지 확인한다.

### 2026-11-17 실측 결과

일회용 project `pawcycle-auth005-rehearsal`(volume `pawcycle-auth005-rehearsal-mysql-data`, `pawcycle-auth005-rehearsal_session-redis-data`), Docker Desktop(Windows), 기준 `main` `bce2100b` + 이 변경. 쿠키·토큰 값은 기록하지 않았다.

| 단계 | 결과 |
| --- | --- |
| backend `PAWCYCLE_SESSION_STORE` / session-redis `maxmemory-policy`·`appendonly` / 캐시 redis `maxmemory-policy` | `redis` / `noeviction`·`yes` / `allkeys-lru` |
| backend 기동 후 150초 유휴 → `GET /api/auth/csrf` | 200 |
| `POST /api/auth/login` → `GET /api/auth/csrf` → `GET /api/auth/me` | 200 → 200 → 200 |
| 세션 key: session-redis / 캐시 redis | 1개 / 0개 |
| `docker compose restart backend` (`StartedAt` 12:36:11Z → 12:39:54Z), 150초 유휴 | — |
| 같은 쿠키로 `GET /api/auth/me` | **200** |
| `POST /api/addresses` CSRF 없음 / 재시작 전 토큰 | 403 / **201** (정리 `DELETE` 204) |
| session-redis 중지: 쿠키 없는 `GET /api/products` / 쿠키 있는 `GET /api/auth/me` / 쿠키 없는 `GET /api/auth/csrf` | 200 / 500 / 401 |
| session-redis 재시작(AOF 복원) 후 같은 쿠키로 `GET /api/auth/me` | 200 |
| `POST /api/auth/logout` → 세션 key → 이전 쿠키로 `GET /api/auth/me` | 204 → 0개 → 401 |
| 정리 `down --volumes` 후 `pawcycle-auth005-rehearsal*` volume | 0개 |

처음 실측에서는 backend가 오래 유휴 상태였다가 받은 첫 세션 요청이 500이었다. 원인은 세션 Redis 공유 연결을 처음 맺는 초기화가 command timeout 1초를 넘긴 것이다(`Connection initialization timed out after 1 second(s)`). 재시작 직후의 첫 연결은 0.09초, 150초 유휴 뒤에는 0.76초였고 두 번째 요청부터는 0.02초였다. 그래서 기동 완료 시점에 세션 Redis 연결을 미리 맺도록 했다(`sessionRedisConnectionWarmUp`, 실패해도 기동은 계속하고 예외 종류만 경고로 남긴다). 위 표는 이 변경을 넣은 뒤의 결과다.

Redis가 멈췄을 때 세션 요청은 실패하지만(fail-closed) 상태 코드는 전용 코드가 아니다. 쿠키가 있는 요청은 500, 쿠키 없는 `GET /api/auth/csrf`는 401이다(세션 저장 실패 예외가 오류 처리 경로에서 인증 실패로 바뀌는 것으로 추정하며, 경로는 확인하지 않았다). 전용 응답(예: 503)으로 바꾸는 것은 인증 API 계약 변경이라 이 작업에 넣지 않았다.

## 운영 활성화 준비(미실행·별도 승인)

운영 compose(`infra/production/compose.yaml`)에는 세션 Redis를 **추가하지 않았다**. `infra/production/oci-release.py`가 compose의 서비스 집합(`backend`, `frontend`, `proxy`)과 volume 집합(`letsencrypt`, `certbot-webroot`)을 고정값으로 검사하므로(`Compose service set mismatch`, `Compose volume set mismatch`), 서비스를 넣으면 배포가 거부된다. 배포 도구 계약 변경은 이 작업의 범위 밖이다.

### 먼저 필요한 사용자 결정

1. **운영 Redis 위치와 사양**: 같은 OCI 호스트의 compose 서비스로 둘지, 관리형 서비스를 쓸지. 호스트 안에 둔다면 메모리 상한(아래 안 참고)과 호스트 여유 메모리.
2. **비용**: 같은 호스트면 추가 비용 없이 메모리만 쓴다. 관리형이면 월 비용이 생긴다.
3. **장애 정책 확인**: 세션 Redis 장애 = 로그인·로그인 사용자 요청·CSRF 발급 실패(fail-closed). 알림 기준과 복구 목표.
4. **Redis 인증**: 내부 network(`internal: true`)만으로 막을지, 비밀번호(ACL)를 둘지. 비밀번호를 쓰면 backend 설정에 password 항목을 추가하는 별도 변경이 필요하다(현재 코드에는 없음).
5. **전환 시점**: `memory` → `redis` 전환 배포에서 로그인 사용자가 1회 로그아웃된다. 공지 여부.

### 사양 안(제안, 결정 아님)

- 같은 호스트 compose 서비스, `redis:8.10.0-alpine3.23`을 digest로 고정
- `maxmemory 64mb`, `noeviction`, AOF `everysec`, `mem_limit 128m`, `cpus 0.25`, `read_only` + `/data` named volume
- `app` network(`internal: true`)에만 연결, 포트 공개 없음
- 근거: 세션 하나는 SecurityContext와 CSRF 토큰이라 대략 1KB 안팎이다(운영 측정 필요). 64MB면 수만 개 규모다. 익명 사용자도 `/api/auth/csrf`와 인증 필요 API의 401 응답에서 세션이 생기므로, 활성화 전 운영 트래픽에서 세션 생성 수를 측정해 상한을 다시 정한다.

### 활성화 순서(승인 후)

1. 저장소 변경: 운영 compose에 `session-redis` 서비스·volume 추가, `oci-release.py`의 서비스·volume·network 계약과 테스트(`test-oci-release*.py`, `test-production-compose.sh`) 갱신, 운영 env에 `PAWCYCLE_SESSION_STORE`·`PAWCYCLE_SESSION_REDIS_*` 추가. 하나의 PR로 리뷰한다.
2. `PAWCYCLE_SESSION_STORE=memory`인 채로 세션 Redis만 먼저 배포하고 healthy를 확인한다.
3. 공지 후 `PAWCYCLE_SESSION_STORE=redis`로 backend를 다시 배포한다(1회 전원 로그아웃).
4. 확인: 운영 인증 smoke([OPS-017](OPS-017-production-auth-session-smoke.md)), backend 재시작 후 로그인 유지, 세션 key 수·Redis 메모리 사용량.
5. 되돌리기: `PAWCYCLE_SESSION_STORE=memory`로 backend를 다시 배포한다(다시 1회 로그아웃). 세션 Redis 데이터는 버려도 된다.

`redis` 모드의 로그아웃 응답에는 쿠키 삭제 헤더가 두 개(기존 + Spring Session `Max-Age=0`) 붙는다. 운영 인증 smoke(`verify-production-auth-session-smoke.sh`)는 curl cookie jar와 상태 코드로 판정하므로 영향이 없을 것으로 보지만, `redis` 모드에서 실행해 본 적은 없다. 활성화 PR에서 함께 확인한다.
