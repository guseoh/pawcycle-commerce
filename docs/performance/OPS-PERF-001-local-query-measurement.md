# OPS-PERF-001 MVP2 목록 조회 로컬 측정

## Boundary

- 대상: `GET /api/v2/subscription-plans` (`plans()`) 및 `GET /api/v2/subscriptions` (`subscriptions()`)
- 기준: `main`의 `6d287d7535e4b6ae710f4b417865df5570c45be0`
- 환경: 사용자 확인 완료 local-integration MySQL·Backend·Frontend·proxy, host `http://localhost:8080`
- 실행 구분: 저장소 변경. Production·Cloud·운영 DB 실행 없음.

## 방법과 데이터

- 계약 page size는 기본 20, 최대 100이며 작은·일반·큰 값으로 10·20·100을 선택했다.
- 전용 local member, DOG Pet, 판매 가능 Plan 100개와 MVP2 Subscription 100개(각각 snapshot item 및 미래 schedule 1개)를 생성했다. prefix fixture는 실행 후 정리했고 기존 local fixture·subscription은 대상으로 삼지 않았다.
- 각 route/page size에 warm-up 3회 뒤 순차 9회를 호출했다. latency는 host-side elapsed milliseconds의 median이다.
- SQL 수는 같은 local MySQL의 `Questions` global status 전후 차이 median이다. health check 등 같은 짧은 window의 부수 SQL 영향 때문에 개별 표본에는 변동이 있으며, 이 값은 Production APM 수치가 아니다.

## 결과

| Route | page size | SQL query count median (observed range) | latency median |
| --- | ---: | ---: | ---: |
| `subscription-plans` | 10 | 36 (32–39) | 50 ms |
| `subscription-plans` | 20 | 52 (52–56) | 71 ms |
| `subscription-plans` | 100 | 212 (212–219) | 168 ms |
| `subscriptions` | 10 | 55 (51–58) | 121 ms |
| `subscriptions` | 20 | 91 (91–98) | 116 ms |
| `subscriptions` | 100 | 411 (411–418) | 204 ms |

`plans()`는 item·delivery cycle을 PlanVersion마다, `subscriptions()`는 pet·snapshot·snapshot item·다음 schedule을 Subscription마다 조회한다. page size가 10→20→100으로 커질 때 두 route의 query count가 선형 증가했으므로 N+1로 판정한다.

## 결정과 한계

측정 근거 없는 최적화는 하지 않았다. 이번 역할은 Platform/SRE이므로 backend fetch 전략·JDBC query를 변경하지 않았고, 최소 batch 조회 개선은 Backend Engineer 후속 작업으로 전달한다. 동일 fixture, page size, warm-up·반복 방법으로 before/after를 재측정해야 한다.

이 값은 공유 local Docker Desktop·대표 fixture의 관측이며 네트워크, 동시 사용자, Production 데이터 분포, DB resource contention을 대표하지 않는다. latency를 Production 성능 또는 SLO로 해석하지 않는다.

## 재현

`docs/performance/OPS-PERF-001-local-query-measurement.ps1`는 `.env.local`을 출력하지 않고 전용 fixture를 만들며, 로그인·측정·로그아웃 중 실패해도 `finally`에서 전용 fixture를 정리한다.
