# OPS-PERF-001 MVP2 목록 조회 로컬 측정

## Boundary

- 대상: `GET /api/v2/subscription-plans` (`plans()`) 및 `GET /api/v2/subscriptions` (`subscriptions()`)
- 기준: `main`의 `6d287d7535e4b6ae710f4b417865df5570c45be0`
- 환경: 사용자 확인 완료 local-integration MySQL·Backend·Frontend·proxy, host `http://localhost:${PAWCYCLE_LOCAL_HTTP_PORT:-8080}`
- 실행 구분: 저장소 변경. Production·Cloud·운영 DB 실행 없음.

## 방법과 데이터

- 계약 page size는 기본 20, 최대 100이며 작은·일반·큰 값으로 10·20·100을 선택했다.
- 전용 local member, CAT Pet, 판매 가능 Plan 100개와 MVP2 Subscription 100개(각각 snapshot item 및 미래 schedule 1개)를 생성했다. local bootstrap의 DOG Plan과 분리하고, 요청 결과가 prefix Plan 100개로만 구성됐는지 확인한다. prefix fixture는 실행 후 정리했고 기존 local fixture·subscription은 대상으로 삼지 않았다.
- 각 route/page size에 warm-up 3회 뒤 순차 9회를 호출했다. latency는 host-side elapsed milliseconds의 median이다.
- SQL 수는 같은 local MySQL의 `Questions` global status 전후 차이 median이다. health check 등 같은 짧은 window의 부수 SQL 영향 때문에 개별 표본에는 변동이 있으며, 이 값은 Production APM 수치가 아니다.

## Before (Platform/SRE 측정)

| Route | page size | SQL query count median (observed range) | latency median |
| --- | ---: | ---: | ---: |
| `subscription-plans` | 10 | 32 (32–43) | 26 ms |
| `subscription-plans` | 20 | 52 (52–63) | 69 ms |
| `subscription-plans` | 100 | 212 (212–212) | 144 ms |
| `subscriptions` | 10 | 51 (51–51) | 44 ms |
| `subscriptions` | 20 | 91 (91–102) | 84 ms |
| `subscriptions` | 100 | 411 (411–422) | 202 ms |

`plans()`는 item·delivery cycle을 PlanVersion마다, `subscriptions()`는 pet·snapshot·snapshot item·다음 schedule을 Subscription마다 조회한다. page size가 10→20→100으로 커질 때 두 route의 query count가 선형 증가했으므로 N+1로 판정한다.

## After (Backend batch 조회 측정)

동일 local/representative 환경에서 전용 fixture 100건, page size 10·20·100, warm-up 3회와 각 9회 조건으로 재측정했다. 모든 HTTP 응답은 200이었다.

| Route | page size | SQL query count median (observed range) | latency median |
| --- | ---: | ---: | ---: |
| `subscription-plans` | 10 | 14 (14–21) | 21 ms |
| `subscription-plans` | 20 | 14 (14–21) | 21 ms |
| `subscription-plans` | 100 | 14 (14–25) | 29 ms |
| `subscriptions` | 10 | 15 (15–22) | 21 ms |
| `subscriptions` | 20 | 15 (15–22) | 19 ms |
| `subscriptions` | 100 | 19 (15–22) | 50 ms |

`plans()`는 현재 page의 PlanVersion ID로 item·delivery cycle을 각각 batch 조회하고, `subscriptions()`는 현재 page의 Pet·Snapshot·SnapshotItem·다음 Schedule을 batch 조회해 기존 DTO 구조로 조립한다. 두 route 모두 page size 증가에 따른 query count의 선형 증가는 제거됐다. `subscriptions()` size 100의 19 median은 local MySQL `Questions` global delta에 포함되는 health check 영향 범위 안에서 관측됐으며, size별 item 수에 비례한 증가는 아니다.

## 결정과 한계

Platform/SRE 측정 뒤 Backend Engineer가 API·도메인·DB schema·의존성을 바꾸지 않고 현재 page related row만 batch 조회하도록 최소 변경했다. 관련 V2 통합 테스트와 동일 fixture의 After 재측정을 완료했다.

이 값은 공유 local Docker Desktop·대표 fixture의 관측이며 네트워크, 동시 사용자, Production 데이터 분포, DB resource contention을 대표하지 않는다. latency를 Production 성능 또는 SLO로 해석하지 않는다.

## 재현

`docs/performance/OPS-PERF-001-local-query-measurement.ps1`는 `.env.local`을 출력하지 않고 전용 fixture를 만들며, 로그인·측정·로그아웃 중 실패해도 `finally`에서 전용 fixture를 정리한다.
