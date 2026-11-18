# 규모 측정 환경 (PERF-SCALE-002, 4단계 G-1)

합성 데이터 생성기(`../synthetic-data/perfgen.py`)가 만든 1만·10만·100만 규모 DB에 backend 하나를 붙여, 같은 조건에서 규모만 바꿔 측정할 수 있게 하는 **로컬 일회용** 환경이다. 운영, 운영 DB, OCI, 공유 `pawcycle-local-integration` volume에는 연결하지 않는다.

이 단계(G-1)는 환경을 처음부터 끝까지 만들고 지우는 것까지다. 혼합 부하 k6 script와 보고서 형식은 G-2에서 만든다.

## 실행

```bash
python infra/performance/scale/run-scale.py --scale 10k --seed 42 --due-count 200 --out <로컬 폴더>
```

| 옵션 | 기본값 | 내용 |
| --- | --- | --- |
| `--scale` | (필수) | `10k`, `100k`, `1m` |
| `--seed` | 42 | 생성기 seed. 같은 값이면 같은 데이터 |
| `--due-count` | 0 | `--as-of` 날짜에 처리할 회차가 되는 ACTIVE 구독 수 |
| `--cohort` | 1000 | 로그인 가능한 합성 회원 수(id 1..K) |
| `--run` | `<scale>-<MMDDHHmm>` | 실행 id. project `pawcycle-perf-<run>`, DB container `pawcycle-perfgen-<run>` |
| `--port` | 18090 | backend가 `127.0.0.1`에 여는 port |
| `--keep` | 끔 | 끝난 뒤 환경을 남긴다(부하 측정용). `--down <run>`으로 지운다 |
| `--out` | 없음 | 요약 JSON을 저장할 폴더. 비밀값은 들어가지 않는다 |

종료 코드: `0` 모두 통과, `3` 환경은 만들었지만 불변식·로그인 검증 실패, `2` 중단(STOP). 중단·실패 때도 `--keep`과 관계없이 환경을 지우고 남은 것이 없는지 확인한다.

## 순서

```text
backend 이미지 build (infra/local-integration/backend.Dockerfile)
  → MySQL 시작 (perfgen guard 확인)
  → backend 첫 기동: Flyway가 빈 DB에 schema + flyway_schema_history 생성 → backend 정지
  → perfgen generate (schema가 저장소 최신 migration 버전과 같은지 확인) → perfgen check
  → 로그인 cohort 준비 (해시 복사)
  → backend 재기동, health 확인, cohort 회원 3명 로그인 + /api/auth/me 200, cohort 밖 회원 로그인 거부 확인
  → compose down -v → container·network·익명 volume이 남지 않았는지 확인
```

## 구성

| 서비스 | 내용 |
| --- | --- |
| mysql | `mysql:8.4.10`, 2 CPU / 2 GiB, binlog 끔. 이름 `pawcycle-perfgen-<run>`, label `pawcycle.purpose=perf-synthetic-data`, 익명 volume, port 미공개(생성기 guard 규칙 그대로) |
| backend | 운영 backend와 같은 상한(`mem_limit 640m`, `cpus 0.75`, `MaxRAMPercentage=65`), read-only root, `127.0.0.1:<port>`만 공개 |

- **profile:** `local-integration`. 결제·정기결제·환불은 sandbox adapter만 쓴다. 실제 Toss에는 연결하지 않는다.
- **fixture 없음:** demo catalog, local QA bootstrap, customer catalog v3를 끈다. DB에는 생성기가 만든 데이터만 있다.
- **cache·session:** 상품 목록 cache 끔(Redis 없음), session은 memory. 측정 대상은 backend와 DB다.
- **요청 경로:** backend에 직접 요청한다. 운영 nginx의 IP별 요청 제한에 걸리면 backend가 아니라 nginx 거부를 측정하게 되기 때문이다.

### 스케줄러

실제 존재하는 설정으로만 켜고 끈다. G-1은 모두 끈 상태로 실행한다.

| 작업 | 설정 | G-1 |
| --- | --- | --- |
| Checkout 결제 만료 | `PAWCYCLE_COMMERCE_CHECKOUT_EXPIRATION_ENABLED` | 끔 |
| 구독 주문 자동화 | `PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED` | 끔 |
| 정기결제 | `PAWCYCLE_COMMERCE_BILLING_ENABLED` | 끔 |
| 회차 보정 | `PAWCYCLE_SUBSCRIPTION_RECONCILIATION_ENABLED` | 끔 |
| 배송 알림 | `PAWCYCLE_SUBSCRIPTION_DELIVERY_REMINDER_ENABLED` | 끔 |
| 멱등성 정리 | `PAWCYCLE_SUBSCRIPTION_IDEMPOTENCY_CLEANUP_ENABLED` | 끔 |
| 지표 갱신 3개(주문·구독·구독 backlog) | 끄는 설정 없음 | 켜짐 |

## 로그인 cohort (해시 복사)

backend에는 회원가입 API가 없다. 그래서 다음처럼 만든다.

1. 실행마다 무작위 비밀번호를 만든다. 이 값은 실행 프로세스의 메모리와 환경 변수에만 있고 출력·파일·로그에 남지 않는다.
2. 방금 build한 backend jar에서 Spring Security의 `BCrypt` 클래스를 꺼내, 저장소 Dockerfile과 같은 JDK 이미지(`eclipse-temurin:25.0.3_9-jdk-noble`, network 없음)에서 cost 10 해시를 만든다. backend의 `BCryptPasswordEncoder` 기본값과 같은 비용이다.
3. 합성 회원 id 1..K의 `password_hash`를 그 해시로 바꾼다. 이 회원들은 생성된 주문·구독 이력을 그대로 가진다.

로그인 실패 잠금은 실패만 세므로 성공 로그인 부하에는 영향이 없다.

## 정기배송 due cohort

`--due-count K`는 id 순서로 처음 K개 ACTIVE 구독의 다음 배송일과 회차 날짜를 `--as-of` 날짜(기본 2026-11-01)로 옮긴다. 나머지 회차는 지금처럼 2030년 이후다. `perfgen check`는 기한이 된 회차가 정확히 K개이고 모두 ACTIVE 구독의 SCHEDULED 회차인지 확인한다.

## 결과 해석

- 로컬 Docker Desktop(Windows)에서 같은 PC, 같은 자원 상한으로 규모만 바꿔 비교하는 기준선이다. 운영 용량의 절대값이 아니다.
- MySQL은 운영(OCI Managed)과 사양을 맞출 수 없다. 2 CPU / 2 GiB로 고정하고 결과와 함께 기록한다.
- 측정 중 다른 무거운 Docker 작업이 CPU를 나눠 쓰면 결과가 흔들린다. 본 측정은 다른 작업과 시간을 겹치지 않는다.
