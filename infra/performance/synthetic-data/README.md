# 합성 데이터 생성기 (4단계 준비)

회원·상품·SKU·재고 원장·리뷰·주문·결제·배송·구독을 서로 연결한 합성 데이터를 **같은 seed에서 같은 행으로** 만든다. 4단계 전체 규모·혼합 부하 측정의 데이터 크기 축(1만 → 10만 → 100만)을 고정하기 위한 도구다. 측정 workload나 요청률은 정하지 않는다.

- 실행: Python 3 표준 라이브러리 + Docker. 새 dependency는 없다.
- 대상: `up`이 만든 일회용 MySQL 8.4.10 container만(`pawcycle-perfgen-*`, label `pawcycle.purpose=perf-synthetic-data`, 익명 volume, port 미공개). 공유 `pawcycle-local-integration-*` volume·container와 운영 DB는 거부한다.
- 스키마: 저장소 `db/migration` V1..Vn을 그대로 적용한다. Flyway migration을 추가하지 않는다. `flyway_schema_history`는 만들지 않으므로 backend를 이 DB에 붙이려면 아래 "4단계에서 쓰기"를 본다.

## 사용법

```bash
python infra/performance/synthetic-data/perfgen.py up --name pawcycle-perfgen-s4
python infra/performance/synthetic-data/perfgen.py generate --name pawcycle-perfgen-s4 --scale 10k --seed 42
python infra/performance/synthetic-data/perfgen.py check --name pawcycle-perfgen-s4
python infra/performance/synthetic-data/perfgen.py fingerprint --name pawcycle-perfgen-s4
python infra/performance/synthetic-data/perfgen.py down --name pawcycle-perfgen-s4
```

- `generate`는 `--scale 10k|100k|1m`, `--seed`, `--as-of`(주문·리뷰 이력의 끝, 기본 `2026-11-01 00:00:00`), `--schedule-start`(구독 다음 배송일 시작, 기본 `2030-01-01`)를 받는다. 출력은 단계별 소요 시간과 테이블별 행 수 JSON이다.
- `check`는 `invariants.sql`(도메인 불변식)과 생성 manifest 검사를 실행하고, 위반이 하나라도 있으면 exit 1이다. `--no-manifest`는 도메인 불변식만 본다(쓰기 부하 뒤 재검사용).
- `fingerprint`는 생성 테이블의 `CHECKSUM TABLE ... EXTENDED`를 출력한다. 같은 입력으로 다시 생성한 값과 비교해 재현성을 확인한다.
- `reset`은 application schema를 지우고 migration을 다시 적용한다. `down`은 container와 익명 volume 삭제를 확인한다.
- `up`의 기본 자원은 `--cpus 2 --memory 2g`다.

## 규모와 비율 (N = 1만·10만·100만)

| 대상 | 행 수 | 내용 |
| --- | --- | --- |
| members, member_addresses | N | 회원마다 기본 배송지 1개. 로그인 불가 hash(`synthetic-no-login`) |
| products / skus / inventories | N/10 / N/5 / N/5 | 상품당 SKU 2개(첫 SKU 구독 가능). 상품 약 5% INACTIVE, 둘째 SKU 약 10% INACTIVE |
| orders, payments, checkout_idempotency_results | N | 모두 `ONE_TIME`. PAID 85% / PAYMENT_FAILED 10% / EXPIRED 5%. 주문당 상품 1~2줄, 할인·배송비 0 |
| order_items | 약 1.33N | FULL snapshot |
| deliveries | PAID 주문 수 | PREPARING / SHIPPED / DELIVERED (시각이 `--as-of`를 넘지 않음) |
| inventory_movements | SKU 수 + 주문줄×2 | SKU별 INBOUND 10,000에서 시작, 주문줄마다 RESERVE 후 DEDUCT(성공) 또는 RELEASE(실패·만료) |
| reviews | 약 0.47N | DELIVERED 구매가 있는 (회원, 상품)에서 결정적 60% |
| subscription_plans 등 | N/1000 | 플랜마다 버전 1·상품 1·주기 2/4/8 |
| subscriptions와 snapshot·schedule·shipping·pet·billing | N/2 | runtime-managed. 구독 생성 API와 같이 수량 1, snapshot 총액 = PlanVersion 가격, snapshot 상품 = plan_items 복사. ACTIVE 80% / PAUSED 10% / CANCELED 10%. 회차는 각각 SCHEDULED / HELD / CANCELED |
| brands, categories | 50, 20 | 고정 |

모든 값은 `CRC32(seed:tag:key)`와 입력값의 함수다. `RAND()`·`UUID()`·`NOW()`를 데이터에 쓰지 않는다. brands·categories·members·billing·plan 주기는 seed와 무관하다.

의도적으로 만들지 않는 상태와 그 이유:

- `PAYMENT_PENDING` 주문·`READY` 결제: backend의 Checkout 만료 scheduler(기본 켜짐)가 측정 중에 처리해 쓰기 부하를 섞는다.
- 기한이 된 구독 회차: 모든 다음 배송일이 `--schedule-start` 이후다. 정기배송 부하용 due cohort는 4단계 측정 설계에서 정한다.
- 쿠폰·멤버십·취소·반품·환불·구독 주문·장바구니: 이번 범위의 연결 관계에 필요하지 않다.

## 불변식

`invariants.sql`은 생성기가 아니라 현재 도메인 규칙을 기준으로 쓴 read-only 검사다. 쓰기 부하 뒤에도 그대로 다시 실행할 수 있다.

- 주문 금액 = 주문줄 합, 주문줄 금액 = 단가×수량, ONE_TIME 주문당 NORMAL 결제 1건, 결제 금액 = 주문 결제 금액
- (주문 상태, 결제 상태) 허용 조합, PAID 주문의 `paid_at`·배송, SUCCEEDED 결제의 승인 정보, 배송은 PAID 주문에만
- 재고 = 원장 합계, 원장 연속성(직전 after = 다음 before), 이동 유형별 증감, 결제 결과와 예약 종료(DEDUCT/RELEASE) 건수·수량 일치, 주문줄마다 예약 존재
- 리뷰는 DELIVERED 구매가 있어야 함
- 구독 snapshot 소유·플랜 주기, snapshot 총액·상품 = PlanVersion, 구독 SKU의 구독 가능 여부, ACTIVE/PAUSED/CANCELED별 열린 회차 규칙

재고 원장 검사는 INBOUND 이동으로 시작하는 SKU만 대상으로 한다. 원장 없이 재고 행만 넣은 다른 fixture는 건너뛴다.

## 4단계에서 쓰기

- backend를 붙여 측정하려면 먼저 backend Flyway가 빈 DB에 schema와 `flyway_schema_history`를 만들게 한 뒤 `generate`를 실행해야 한다. 지금의 `up`은 migration을 raw SQL로 적용하므로 backend의 `validate-on-migrate`와 맞지 않는다. 이 연결(전용 compose project, backend 연결 방식)은 아직 만들지 않았다.
- 합성 회원은 로그인할 수 없다. 로그인 부하에 쓸 회원과 비밀번호 처리 방식은 별도로 정해야 한다.
- 1만 규모 생성은 local에서 약 50~85초였다. 대부분 chunk마다의 `docker exec` 비용이다. 10만·100만은 실행하지 않았고 소요 시간·disk 사용량은 측정 전이다. 100만 규모는 binlog를 포함해 수 GB를 쓸 수 있다.

## 검증

```bash
python infra/performance/synthetic-data/test_perfgen.py
```

Docker 없이 guard(이름·label·named volume·port 거부), 규모 비율, chunk 경계, 생성 SQL의 결정성과 DDL 부재, 불변식 SQL의 read-only 여부를 확인한다.
