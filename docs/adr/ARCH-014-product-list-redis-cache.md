# ARCH-014 상품 목록 Redis cache

## 상태

Accepted

## 날짜

2026-08-23

## 작업 ID

`PERF-PH10-001`

## 맥락(Context)

Phase 9에서 `/api/products`는 CPU 증가에 따라 처리량이 개선됐지만 CPU2.0에서도 dropped iteration, tail latency와 Hikari pending이 남았다. SQL은 저렴했고 Hikari 확대는 악화됐으며, 유효 JFR hotspot 증거는 확보되지 않았다. 따라서 Redis를 DB 병목의 해법으로 단정하지 않고 반복 목록 read의 JPA materialization, DTO 조립과 connection 경로를 우회하는 Read Scale 후보로 동일 조건에서 검증한다.

## 결정(Decision)

- `GET /api/products` 응답만 Redis에 cache한다. detail, session과 다른 도메인은 대상이 아니다.
- MySQL이 authoritative source다. miss는 기존 `ProductListReader`의 상품 조회와 ACTIVE SKU batch 조회, 즉 non-empty 기준 2-query 계약을 그대로 실행한다. hit는 이 reader를 실행하지 않는다.
- data key는 namespace와 format version을 포함한 `pawcycle:catalog:product-list:v1`이고 value는 Java native serialization이 아닌 기존 `ProductListView`의 명시적 JSON이다.
- concurrent invalidation을 식별하는 generation key `pawcycle:catalog:product-list:v1:generation`을 별도로 둔다. miss가 DB를 읽기 전에 generation을 캡처하고, Redis Lua script는 그 generation이 그대로일 때만 data key를 저장한다.
- Product와 SKU create/update가 성공하면 현재 transaction의 `afterCommit`에서 Redis Lua script가 generation 증가와 data key 삭제를 하나의 원자 연산으로 수행한다. rollback에서는 실행하지 않는다.
- 따라서 이전 DB snapshot을 읽은 concurrent miss가 invalidation 뒤늦게 완료되더라도 generation이 달라져 오래된 값을 다시 cache하지 않는다. invalidation이 conditional set보다 늦게 실행되면 같은 원자 연산에서 data key를 삭제한다.
- TTL은 `PAWCYCLE_PRODUCT_LIST_CACHE_TTL`로 외부화하며 기본값은 5분이다. TTL은 correctness 경계가 아니라 Redis 오류 등으로 남은 stale entry의 제한된 안전망이다.
- Redis read/write/invalidation 오류는 `error` metric으로 기록하고 목록 read는 authoritative DB로 fail-open한다. DB 오류는 기존 endpoint 오류로 전파한다.
- `hit`, `miss`, `error` counter와 Redis Actuator health를 노출한다. cache와 Redis health는 기본 비활성이며 local integration에서만 활성화한다.

## 검토한 대안(Alternatives Considered)

### 단순 delete 기반 cache-aside invalidation

구현은 단순하지만, cache miss가 이전 DB snapshot을 읽은 뒤 Admin transaction이 commit·delete하고 그 miss가 오래된 값을 다시 `SET`하는 race가 가능하다. TTL 동안 stale 상품 가격·상태·SKU가 재노출될 수 있어 generation + conditional set으로 보강한다.

### Spring Cache abstraction과 native Java serialization

구현량은 줄지만 key/value와 장애 경계를 명시적으로 통제하기 어렵고 native serialization은 장기 호환성과 안전성이 낮아 채택하지 않는다.

### TTL만 사용하는 invalidation

쓰기 직후 stale response를 허용하므로 채택하지 않는다. TTL은 after-commit invalidation 또는 Redis 장애 시 남은 stale entry의 제한된 안전망으로만 사용한다.

### Product detail, session 또는 distributed lock 확장

현재 Read Scale 증거와 작업 범위를 넘으므로 채택하지 않는다. generation token은 cache refill race만 해결하며 별도 distributed lock을 도입하지 않는다.

## 결과와 영향(Consequences)

- cache hit는 목록 read의 DB/JPA/DTO 경로를 우회하지만 Redis network와 JSON 역직렬화 비용이 생긴다.
- miss는 data key 확인 외에 generation read와 conditional Redis script 비용이 추가된다. 이 비용이 효과를 상쇄하는지는 After first-result에서 판단한다.
- Redis 장애 중에도 catalog read는 DB로 계속 동작한다. 다만 Redis timeout 동안의 지연과 fallback으로 인한 DB pressure, invalidation 자체가 실패했을 때 TTL까지 남을 수 있는 stale entry는 After 측정 및 장애 검증에서 확인해야 한다.
- Production Redis, secret, 비용 리소스와 배포 구성은 이 결정에 포함되지 않는다.
- 효과는 PERF-PH9-010을 Before로 재사용한 Redis After first-result로만 판단하며 Before를 재실행하지 않는다.

## 검증(Validation)

단위 테스트로 miss/hit, reader 우회, generation 기반 stale refill 방지, Redis 장애 fallback과 metric을 검증한다. Spring transaction manager와 `@Transactional` proxy를 사용하는 통합 테스트로 Product/SKU mutation의 commit 이후 invalidation과 rollback 시 미실행을 검증한다. 기존 API 통합 계약과 non-empty 2-query miss 계약은 그대로 유지한다. local-only harness는 CPU2.0 고정 envelope와 Redis health/resource, warm-up hit 증가를 fail-close한 뒤 한 번의 After 결과를 수집하도록 준비한다. 이 ADR 작업에서는 workload를 실행하지 않는다.

## 사용자 승인(User Approval)

2026-08-23 이 작업 지시에서 Redis 목록 cache 도입과 경계를 승인했다.
