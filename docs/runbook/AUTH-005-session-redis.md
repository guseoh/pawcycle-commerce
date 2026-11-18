# AUTH-005 세션 Redis Runbook

## 목적과 경계

[AUTH-005 ADR](../adr/AUTH-005-spring-session-redis.md)의 세션 저장소(Spring Session Redis)를 local-integration에서 확인하는 절차와, 운영에서 켜기 전에 필요한 결정·작업을 정리한다.

- local-integration은 `PAWCYCLE_SESSION_STORE=redis`로 세션 전용 Redis(`session-redis`)를 쓴다.
- 운영은 별도 sibling project `infra/production-session-redis`를 쓰도록 저장소가 준비돼 있고, 운영 compose의 스위치 기본값은 `memory`다. 이 문서의 운영 절은 **적용 절차**이며, 운영 실행 승인이 아니다.

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

## 운영 구성(저장소 준비 완료, 운영 미실행)

2026-11-17 사용자 결정에 따라 운영 세션 Redis는 Application compose의 서비스가 아니라 **별도 sibling Compose project**다([ARCH-012](../adr/ARCH-012-production-observability-boundary.md)의 metrics-proxy 패턴). 이 절의 명령은 운영 실행 승인이 아니다. 실제 적용은 #470 운영 트랙에서 별도로 승인한다.

### 구성 요소

| 항목 | 값 |
| --- | --- |
| 정의 | `infra/production-session-redis/compose.yaml` |
| Compose project | `pawcycle-production-session-redis` (service `session-redis` 하나) |
| image | `redis:8.10.0-alpine3.23@sha256:978f0e01…6241`(digest 고정, `linux/amd64` manifest 확인). `oci-release.py`의 `SESSION_REDIS_IMAGE`와 같아야 한다(`validate-production-contracts.py`가 검사) |
| 서버 옵션 | `--maxmemory 64mb --maxmemory-policy noeviction --appendonly yes --appendfsync everysec --save ""` |
| 자원 | `mem_limit: 128m`, `cpus: 0.25`, `pids_limit: 64`, `read_only: true`, `no-new-privileges` |
| 상태 확인·재시작 | `redis-cli ping` healthcheck, `restart: unless-stopped`, `stop_grace_period: 30s` |
| 데이터 | named volume `pawcycle-production-session-redis-data`(`/data`, 이름 고정) |
| network | external `pawcycle-production-app`(`internal: true`, Application project 소유)에만 연결. host port 없음, `expose` 없음 |
| 인증 | 없음(사용자 결정). Docker-internal network만 접근 경계다. 인증이 필요해지면 backend 설정 변경이 따르는 별도 결정이다 |

backend는 같은 `app` network에서 service 이름 `session-redis:6379`로 접근한다. 세션 Redis는 Application release와 독립된 lifecycle을 가진다. release 도구는 이 project를 시작·중지·재생성하지 않고, `Host.siblings()`가 release 전후로 이 컨테이너의 `StartedAt`을 비교해 바뀌면 fail-closed 한다.

### 저장소 선택 스위치: 운영 compose의 `environment`

운영 backend의 세션 저장소는 `infra/production/compose.yaml`의 backend `environment`가 정한다.

```yaml
PAWCYCLE_SESSION_STORE: memory          # 기본. 활성화 PR에서만 redis로 바꾼다
PAWCYCLE_SESSION_REDIS_HOST: session-redis
PAWCYCLE_SESSION_REDIS_PORT: "6379"
```

운영 runtime 환경 파일(`backend.env`)에는 새 키를 넣지 않는다. 이유는 다음과 같다.

- `oci-release.py`는 baseline 기록의 `runtime_env_sha256`과 현재 `backend.env` 해시가 같아야 preflight·activate·rollback을 허용한다. 환경 파일을 고치면 기존 baseline에서 넘어가는 경로가 막힌다.
- canonical bundle(`g0-t2a-canonical-v1`)은 `production_env.py`가 키 집합을 정확히 일치하게 검사한다. 세션 키를 넣으면 `ENV_KEYS`로 거부된다.
- Compose에서 `environment`가 `env_file`보다 우선하므로, 누가 `backend.env`에 세션 키를 넣어도 저장소가 바뀌지 않는다. 스위치는 리뷰된 compose 한 곳뿐이다.

따라서 `production_env.py`와 `oci-release.py`의 앱 compose 서비스·volume 집합 계약은 바뀌지 않았다. 기존 운영 환경 파일 그대로 release·preflight·복구가 통과한다(`test-oci-release.py`의 `SessionRedisReleaseTests`).

### release 도구의 세션 Redis 검사

`oci-release.py`는 backend 환경의 `PAWCYCLE_SESSION_STORE`를 읽는다(없으면 `memory`).

| 대상 | `memory`(또는 키 없음) | `redis` |
| --- | --- | --- |
| preflight·activate·rollback의 대상 compose(`validate_config`) | sibling을 보지 않는다 | host/port가 `session-redis`/`6379`이고 sibling이 아래 조건을 만족해야 통과 |
| 실행 중 backend(`smoke`, 활성화 전후) | sibling을 보지 않는다 | 같은 조건. 단 `rollback`의 활성화 전 현재 release smoke는 sibling을 보지 않는다(아래) |
| 그 밖의 값 | — | `session store selection mismatch`로 거부 |

sibling 조건: `pawcycle-production-session-redis`/`session-redis` 컨테이너가 정확히 하나, image가 `SESSION_REDIS_IMAGE`, running·healthy, network가 `pawcycle-production-app` 하나, host port 공개 없음. 이 검사는 `docker ps`·`docker inspect`만 쓰며 sibling을 바꾸지 않는다.

`memory`로 되돌린 release로의 rollback과, AUTH-005 이전 release(세션 키 없음)로의 rollback도 그대로 통과한다. 현재 release가 `redis`이고 sibling이 멈췄거나 unhealthy여도 `memory` 대상 rollback은 막히지 않는다. 활성화 전 현재 release smoke는 애플리케이션 identity·image·HTTP 검사를 그대로 하되 sibling 검사만 건너뛰고, sibling 필요 여부는 rollback 대상 compose(`validate_config`)와 활성화 후 smoke가 정한다. 그래서 `redis` 대상 rollback은 여전히 healthy sibling을 요구한다. preflight·activate는 현재 release가 `redis`면 sibling 정상을 계속 요구한다.

### 운영 적용 전 확인할 것

1. **compose 변경은 manual-pair release로 반영된다.** 이 저장소 변경은 운영 compose의 `compose_sha256`을 바꾼다. `--application-only`(CD 포함)는 compose 변경을 거부하므로, 이 변경을 처음 운영에 올리는 것은 DB compatibility evidence가 있는 manual release다. 값이 `memory`이므로 동작은 지금과 같다.
2. **메모리 여유**: 세션 Redis 상한 128MiB가 app01(6GB)에 더해진다. 적용 전 `free -m`과 기존 컨테이너 사용량을 기록한다.
3. **세션 크기와 상한**: 세션 하나는 SecurityContext와 CSRF 토큰으로 대략 1KB 안팎이다(운영 측정 필요). 익명 사용자도 `/api/auth/csrf`와 인증 필요 API의 401 응답에서 세션이 생기므로, 활성화 뒤 `DBSIZE`와 `used_memory`를 보고 64MB 상한을 다시 판단한다.
4. **알림**: 세션 Redis 전용 알림은 아직 없다. 활성화 전에 Observability 트랙에서 다룰지 결정한다.

## 운영 적용 절차(미실행·별도 승인)

각 단계는 별도 승인 단위다. Application `backend`/`frontend`/`proxy`와 Managed MySQL에는 이 절의 Compose 명령을 실행하지 않는다. **release가 진행 중일 때 sibling을 조작하지 않는다**(release의 sibling fence가 실패하고 복구 gate로 넘어간다).

### 1. Sibling 기동(backend는 계속 `memory`)

[OPS-OBS-001](OPS-OBS-001-production-observability.md)의 source 준비와 같은 방식으로 승인된 SHA의 `infra/production-session-redis`만 read-only source로 꺼낸다(`git archive` → `0555`/`0444`, `.approved-sha` marker). 그 다음 Application identity snapshot을 찍고 적용한다.

```bash
set -Eeuo pipefail
APPROVED_SHA='<approved-merge-sha>'
SOURCE_ROOT="/opt/pawcycle/session-redis-source/$APPROVED_SHA"
test "$(cat "$SOURCE_ROOT/.approved-sha")" = "$APPROVED_SHA"

sudo docker network inspect pawcycle-production-app --format '{{.Internal}}' | grep -qx true
sudo env PAWCYCLE_APP_NETWORK=pawcycle-production-app docker compose \
  --project-name pawcycle-production-session-redis \
  --project-directory "$SOURCE_ROOT/infra/production-session-redis" \
  --file "$SOURCE_ROOT/infra/production-session-redis/compose.yaml" \
  config --quiet
sudo env PAWCYCLE_APP_NETWORK=pawcycle-production-app docker compose \
  --project-name pawcycle-production-session-redis \
  --project-directory "$SOURCE_ROOT/infra/production-session-redis" \
  --file "$SOURCE_ROOT/infra/production-session-redis/compose.yaml" \
  up --detach --no-deps --wait --wait-timeout 60 --remove-orphans
```

image가 host에 없으면 compose가 digest reference를 pull한다. 적용 후 확인한다.

```bash
REDIS_ID="$(sudo docker ps --quiet \
  --filter label=com.docker.compose.project=pawcycle-production-session-redis \
  --filter label=com.docker.compose.service=session-redis)"
test -n "$REDIS_ID"
test -z "$(sudo docker port "$REDIS_ID")"
sudo docker inspect "$REDIS_ID" --format '{{.State.Health.Status}} {{range $n, $_ := .NetworkSettings.Networks}}{{$n}} {{end}}'
sudo docker exec "$REDIS_ID" redis-cli config get maxmemory-policy
sudo docker exec "$REDIS_ID" redis-cli config get appendonly
```

성공 기준: `healthy pawcycle-production-app`, port 출력 없음, `noeviction`, `yes`. Application container ID·`StartedAt`이 적용 전후 같다. 실패하면 아래 "sibling 제거"로 되돌린다(backend가 `memory`이므로 사용자 영향 없음).

### 2. 활성화(공지 후, 1회 전원 로그아웃)

1. 저장소 PR: `infra/production/compose.yaml`의 `PAWCYCLE_SESSION_STORE: memory`를 `redis`로 바꾼다(`validate-production-contracts.py`의 기본값 검사도 함께 바꾼다). 리뷰·병합.
2. 그 SHA로 manual release preflight. preflight가 sibling identity·health·network·port를 검사한다. sibling이 없거나 unhealthy면 여기서 멈춘다.
3. activate. backend가 재생성되며 메모리 세션이 사라진다(1회 로그아웃).
4. 확인: 운영 인증 smoke([OPS-017](OPS-017-production-auth-session-smoke.md)), `redis-cli DBSIZE` 증가, `INFO memory`의 `used_memory_human`. 다음 release(backend 재생성) 뒤에도 로그인 유지가 확인되면 R3 해소를 Production Verified로 기록한다.

### 3. Rollback

- **backend만 되돌림(권장)**: release 도구의 `rollback`으로 직전 verified release(`memory`)로 돌린다. 다시 1회 로그아웃이 생긴다. sibling은 그대로 둬도 되고 데이터는 버려도 된다.
- **sibling 제거**: backend가 `memory`일 때만 한다. backend가 `redis`인 채로 sibling을 멈추면 로그인·로그인 사용자 요청·CSRF 발급이 실패한다(fail-closed).

```bash
sudo env PAWCYCLE_APP_NETWORK=pawcycle-production-app docker compose \
  --project-name pawcycle-production-session-redis \
  --file "$SOURCE_ROOT/infra/production-session-redis/compose.yaml" \
  down
```

`down`은 volume을 지우지 않는다. 데이터를 버리기로 결정한 경우에만 `sudo docker volume rm pawcycle-production-session-redis-data`를 실행한다.

### 데이터 보존

- 세션은 일시 데이터라 별도 백업을 두지 않는다. 데이터를 잃으면 사용자가 다시 로그인하는 것으로 끝난다.
- AOF `everysec`: 프로세스·호스트 비정상 종료 때 최대 약 1초분의 세션 쓰기를 잃을 수 있다. 정상 종료(`stop_grace_period: 30s`)는 AOF를 flush한다.
- 컨테이너 재생성·image 교체·`down`/`up`에도 named volume이 남아 세션이 유지된다(`test-session-redis.sh`가 재생성·`down` 후 복원을 검증). sibling을 재시작하는 동안에는 세션 요청이 실패하므로 release와 겹치지 않는 시간에 한다.
- image 교체는 `compose.yaml`의 digest와 `oci-release.py`의 `SESSION_REDIS_IMAGE`를 한 PR에서 함께 바꾼다. 둘이 다르면 `redis` 모드 preflight가 거부된다.

### 운영 구성 일회용 재현(2026-11-17)

운영 이미지 정의(`infra/production/backend.Dockerfile`)로 빌드한 backend, 운영 compose + `compose.test.yaml`(일회용 MySQL), 이 sibling compose를 일회용 이름(`pawcycle-auth005-repro-*`)으로 띄웠다. app network는 운영처럼 Application project가 `internal: true`로 만들고, sibling은 그 network에 external로 붙었다. 활성화 PR은 compose override(`PAWCYCLE_SESSION_STORE: redis` 한 줄)로 흉내 냈다. 첫 기동의 Flyway migration이 WSL에서 운영 healthcheck 재시도 한도를 넘어, 재현에서만 backend healthcheck `retries`를 늘렸다. backend 재생성은 release와 같은 `up --no-deps --force-recreate --wait backend`다. 쿠키·토큰·credential 값은 기록하지 않았다. Docker Engine 29.9.2(WSL2).

| 단계 | 결과 |
| --- | --- |
| sibling network / published port / `PortBindings` | app network 하나 / 없음 / `{}` |
| A. 기본 compose: backend `PAWCYCLE_SESSION_STORE` | `memory` |
| A. 로그인 → `/api/auth/me` → backend 재생성(container ID 변경) → 같은 쿠키 `/api/auth/me` | 200 → 200 → **401**(R3 재현), sibling 세션 key 0개 |
| B. 활성화 compose: backend `PAWCYCLE_SESSION_STORE` / network | `redis` / app·database(test)·egress |
| B. 로그인 → `/api/auth/me` → backend 재생성(container ID 변경) | 200 → 200, sibling 세션 key 1개 |
| B. 같은 쿠키 `/api/auth/me` | **200** |
| B. 재생성 전 CSRF 토큰으로 `POST /api/auth/logout` → 세션 key → 이전 쿠키 `/api/auth/me` | 204 → 0개 → 401 |
| backend 재생성 동안 sibling `StartedAt`·container ID | 변하지 않음 |
| 정리 후 `auth005` 컨테이너·volume·network | 0개 |

### 검증 자산

- `bash infra/production-session-redis/test-session-redis.sh`: 일회용 internal network·volume에서 compose 계약(digest·옵션·자원·host port 없음·app network만), `noeviction`, AOF 재생성 복원, sibling `down` 뒤 Application 컨테이너·network 불변.
- `python infra/production/test-oci-release.py`(root/Linux): `SessionRedisReleaseTests` — 기존 runtime 환경 파일로 AUTH-005 이전 compose·`memory` compose 모두 preflight 통과, `redis` 선택 시 sibling 검사와 거부 사례, 환경 파일 세션 키 거부, sibling 재시작 시 release 실패 유지.
- `bash infra/production/test-production-compose.sh`: 운영 compose의 기본값 `memory`와 `env_file`이 세션 저장소를 바꾸지 못함.

`redis` 모드의 로그아웃 응답에는 쿠키 삭제 헤더가 두 개(기존 + Spring Session `Max-Age=0`) 붙는다. 운영 인증 smoke(`verify-production-auth-session-smoke.sh`)는 curl cookie jar와 상태 코드로 판정하므로 영향이 없을 것으로 보지만, 운영 `redis` 모드에서 실행해 본 적은 없다. 활성화 단계에서 확인한다.
