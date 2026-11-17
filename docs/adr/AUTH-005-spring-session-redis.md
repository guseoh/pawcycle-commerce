# AUTH-005: 세션 저장소를 Spring Session Redis로 외부화

## 상태

Approved — 사용자 승인 2026-11-17 (세션 외부화 방식으로 Spring Session Redis 선택. 운영 활성화는 별도 승인)

## 날짜

2026-11-17

## 작업 ID

`AUTH-005`

## 맥락(Context)

[known-risks](../architecture/known-risks.md) R3: 로그인 세션(SecurityContext)과 CSRF 토큰이 Tomcat 메모리의 `HttpSession`에 있다. backend를 재시작하거나 배포로 컨테이너를 다시 만들면 전원이 로그아웃되고 CSRF 토큰도 무효가 된다. 같은 이유로 backend를 두 개 이상 띄울 수 없다.

지켜야 하는 조건은 다음과 같다.

- [AUTH-003](AUTH-003-session-authentication-approved-inputs.md)의 세션 기반 인증 계약을 그대로 둔다. 쿠키 이름 `JSESSIONID`, `HttpOnly`, `SameSite=Lax`, HTTPS에서 `Secure`, `Domain` 없음, `Path=/`, 지속 `Max-Age` 없음, 유휴 30분, 로그인 시 session id 변경, 로그아웃 시 세션 무효화와 CSRF 폐기, `HttpSessionCsrfTokenRepository`.
- 운영 compose에는 아직 Redis가 없다. 운영 배포 도구(`infra/production/oci-release.py`)는 compose의 서비스·볼륨 집합을 고정값으로 검사한다.
- local-integration의 기존 Redis는 상품 목록 캐시용이다. 영속성이 없고 `allkeys-lru`라서, 세션을 넣으면 메모리가 찰 때 세션이 지워진다.

## 결정(Decision)

1. **Spring Session Data Redis**로 세션을 저장한다. 버전은 Spring Boot BOM(4.1.0)이 관리한다(`spring-boot-starter-session-data-redis`, Spring Session 4.1.0).
2. 저장소는 `pawcycle.session.store`(환경 변수 `PAWCYCLE_SESSION_STORE`) 하나로 고른다. 값은 `memory` 또는 `redis`이고 그 밖의 값이면 기동에 실패한다. **기본값은 `memory`**다. 운영에 세션 Redis를 준비하기 전에 이 코드가 배포돼도 지금과 똑같이 동작하게 하기 위해서다.
3. 세션 Redis는 **캐시 Redis와 분리된 세션 전용 인스턴스**다. 연결은 `PAWCYCLE_SESSION_REDIS_HOST`·`PAWCYCLE_SESSION_REDIS_PORT`(기본 timeout 1초)로 따로 받는다. Boot의 Redis 세션 자동 구성은 캐시 연결이 있으면 그 연결로 세션을 켜 버리므로 제외하고, `common/security/SessionStoreConfiguration`이 `redis`일 때만 세션 전용 연결(`@SpringSessionRedisConnectionFactory`)로 켠다. 캐시 연결은 그대로 남는다.
4. 저장 형식은 Spring Session 기본값(`RedisSessionRepository`, JDK 직렬화)이고 key namespace는 `pawcycle:session`이다. 세션에 들어가는 `AuthenticatedMemberPrincipal`을 `Serializable`로 바꿨다.
5. 쿠키·timeout은 기존 `server.servlet.session.*` 설정을 Spring Session이 그대로 쓴다. `SecurityConfig`는 바꾸지 않았다.
6. 세션 Redis는 `noeviction`(메모리가 차도 세션을 지우지 않고 쓰기를 거부)과 AOF(`appendfsync everysec`)로 운영한다. local-integration에 이 구성을 추가했다.

### Redis 장애 시 정책: 인증 불가(fail-closed)

세션 Redis에 연결할 수 없으면 세션을 읽고 쓰는 요청이 실패한다. 로그인·로그인 상태 확인·CSRF 토큰 발급과 쓰기 요청이 모두 해당한다. 메모리로 대신 저장하거나(fail-open) 인증을 건너뛰지 않는다. 대체 저장을 하면 Redis가 돌아왔을 때 세션이 둘로 갈라지고, 로그아웃한 세션이 살아나는 경로가 생기기 때문이다. 세션 쿠키 없이 들어오는 공개 조회(상품 목록 등)는 세션 저장소를 읽지 않는다. local-integration 실측에서 세션 Redis를 멈추면 쿠키 없는 상품 목록은 200, 쿠키가 있는 `GET /api/auth/me`는 500, 쿠키 없는 `GET /api/auth/csrf`는 401이었다. 장애 전용 응답 코드(예: 503)는 인증 API 계약 변경이라 정하지 않았다.

기동할 때 세션 Redis에 닿지 않아도 backend는 기동한다. 기동 완료 시점에 세션 Redis 연결을 한 번 미리 맺되, 실패하면 경고만 남긴다. 세션 Redis 장애가 상품 조회 같은 익명 기능까지 막지 않게 하기 위해서다. 미리 맺는 이유는 유휴 상태의 첫 연결 초기화가 1초 timeout을 넘어 첫 로그인 요청이 500이 된 실측 때문이다.

### 전환 시 1회 전원 로그아웃

`memory`에서 `redis`로 바꾸는 배포에서는 메모리에 있던 세션이 옮겨지지 않는다. 그 순간 로그인한 사용자는 한 번 로그아웃되고 CSRF 토큰을 다시 받아야 한다. 되돌릴 때(`redis` → `memory`)도 같다. 사전 공지 대상이며, 이 1회 로그아웃을 사용자가 수용했다.

## 검토한 대안(Alternatives Considered)

### Spring Session JDBC (MySQL)

- 장점: 새 인프라가 없고 기존 DB 백업으로 세션이 보존된다.
- 단점: Flyway migration(`SPRING_SESSION` 테이블)이 필요하다. 요청마다 `lastAccessedTime`을 갱신하는 쓰기가 생겨 640MB·0.75 CPU 운영 DB 경로에 부하가 늘고, 만료 정리 작업이 필요하다.
- 결과: 선택하지 않음.

### 무상태 토큰(JWT·서명 쿠키)

- 장점: 서버에 세션 상태가 없다.
- 단점: 승인된 AUTH-003 세션 인증 결정을 뒤집는다. 로그아웃·강제 만료가 어렵고 CSRF 모델도 바뀐다.
- 결과: 선택하지 않음.

## 결과와 영향(Consequences)

- `redis`로 켜면 backend 재시작·재생성 후에도 로그인 상태와 CSRF 토큰이 유지된다. 여러 backend 인스턴스의 전제 조건 하나가 해소된다. 단, 컨테이너를 제자리에서 다시 만드는 동안의 짧은 중단은 그대로다(무중단 배포는 별도 작업).
- 세션 Redis가 인증의 필수 의존성이 된다. 메모리 상한을 넘으면(`noeviction`) 새 세션 쓰기가 거부되어 로그인이 실패한다. 익명 사용자도 `/api/auth/csrf` 호출이나 인증이 필요한 API의 401 응답에서 세션이 생기므로, 메모리 상한은 익명 세션 수까지 고려해 정해야 한다.
- `redis` 모드의 로그아웃 응답에는 기존 쿠키 삭제 헤더 외에 Spring Session의 삭제 헤더(`Max-Age=0`)가 하나 더 붙는다. 브라우저의 결과(쿠키 삭제)는 같다. 쿠키 값은 Spring Session 형식(session id의 Base64)이 되지만 값은 원래 불투명하므로 계약 변경이 아니다.
- JDK 직렬화는 클래스 버전에 묶인다. Spring Security 등 세션에 들어가는 클래스의 직렬화 호환이 깨지는 업그레이드에서는 기존 세션을 읽지 못할 수 있다. 그런 업그레이드는 세션 비우기(1회 로그아웃)를 포함해 배포한다.
- 운영 활성화는 이 결정에 포함되지 않는다. 운영 compose에 세션 Redis를 추가하려면 `oci-release.py`의 서비스·볼륨 집합 계약을 바꿔야 하며, 운영 Redis 사양·비용·인증 방식 결정이 먼저 필요하다. 절차와 결정 항목은 [AUTH-005 Runbook](../runbook/AUTH-005-session-redis.md)에 있다.

## 검증(Validation)

- `RedisSessionStoreIntegrationTests`: 일회용 MySQL·세션 Redis에서 로그인 → 애플리케이션 컨텍스트를 닫고 새로 띄움 → `/api/auth/me` 200, CSRF 없는 쓰기 403·있는 쓰기 201, 로그아웃 204 후 Redis 세션 key 삭제와 이전 쿠키 401. 세션 연결과 캐시 연결이 다른 bean인지, 쿠키 속성이 AUTH-003과 같은지도 확인한다. CI는 backend job에 일회용 세션 Redis를 붙이고, 이 Redis가 없으면 CI에서 테스트가 실패한다.
- `MemorySessionStoreIntegrationTests`: `memory`에서 세션 Redis 없이 기동·로그인·쓰기·로그아웃, 잘못된 저장소 값의 기동 실패.
- `SessionRedisOutageIntegrationTests`: `redis`인데 세션 Redis에 닿지 않을 때 기동하고, 익명 상품 조회는 200, 세션이 필요한 요청은 실패한다.
- local-integration 실측(2026-11-17): 일회용 compose project에서 로그인 → `docker compose restart backend` → `/api/auth/me` 200, 재시작 전 CSRF 토큰으로 쓰기 201, 세션 Redis 재시작(AOF) 후에도 유지. 결과표는 Runbook.

## 사용자 승인(User Approval)

- 2026-11-17: 사용자(PO 겸 Tech Lead)가 R3 해결책으로 Spring Session Redis를 선택했다(Spring Session JDBC 대안은 선택하지 않음). 운영 profile 기본값 `memory`, Redis 장애 시 인증 불가, 전환 시 1회 전원 로그아웃을 이 작업의 조건으로 지정했다.
- 운영 활성화(운영 Redis 도입·사양·비용·배포 계약 변경)는 승인되지 않았다.
