# PERF-SCALE-002 — 규모별 혼합 부하 기준선

- 작업 ID: PERF-SCALE-002 (고도화 4단계)
- 작업 등급: 일반
- 실행 구분: 저장소 변경
- 측정 대상: 로컬 일회용 환경만. 운영·운영 DB·OCI에는 연결하지 않았다.
- 도구: `infra/performance/scale/run-scale.py`(환경), `infra/performance/scale/run-mixed.py`(측정), `infra/performance/k6/scale-mixed.js`(부하)
- 해석 경계: 이 보고서는 **같은 PC·같은 자원 상한에서 규모만 바꾼 상대 비교용 원자료**다. 운영 용량의 절대값이 아니다. 병목 판단과 개선 방향은 규모별 측정(H)이 끝난 뒤 별도로 기록한다.

## 목적

데이터가 1만 → 10만 → 100만으로 커질 때 같은 요청률에서 어느 요청이 먼저 느려지는지 비교할 기준선을 남긴다. 이 기준선은 2단계 알림 임계값 확정과 5단계 병목 개선의 근거가 된다. G-2에서는 측정 도구를 만들고 1만 규모를 한 번 측정했다.

## 측정 조건

| 항목 | 값 |
| --- | --- |
| backend | 운영과 같은 상한 640m / 0.75 CPU, `MaxRAMPercentage=65`, profile `local-integration`(sandbox 결제), 요청은 backend 직접(nginx 미경유) |
| MySQL | 8.4.10, 2 CPU / 2 GiB, binlog 끔 |
| 스케줄러 | 결제 만료·구독 자동화·정기결제·회차 보정·배송 알림·멱등성 정리 끔. 지표 갱신 3개만 켜짐 |
| cache·세션 | 상품 목록 cache 끔, 세션 memory |
| 부하 | 묶음별 `constant-arrival-rate`, 전체 20 iteration/s(탐색 70% · 회원 조회 15% · 쓰기 10% · 로그인 5%), 예열 2분 후 측정 10분 |
| 호스트 | Windows Docker Desktop, 측정 중 다른 무거운 Docker 작업 없음 |

## 규모별 결과 (측정 구간)

측정 구간 10분의 클라이언트·서버·DB 결과다. 1만 규모에서 응답 계약 위반·dropped iteration·5xx·커넥션 획득 시간 초과·row lock 대기가 모두 0이었고, 쓰기 뒤 불변식이 통과했다.

### 클라이언트(k6)

| 규모 | 묶음 | 요청 수 | p50 | p95 | p99 | max | 오류율 | iteration 완료/예정 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1만 | 탐색 | 8,401 | 9.8ms | 26.5ms | 34.1ms | 62.3ms | 0 | 8,401 / 8,400 |
| 1만 | 회원 조회 | 1,801 | 5.0ms | 12.7ms | 20.1ms | 28.5ms | 0 | 1,801 / 1,800 |
| 1만 | 쓰기(요청 단위) | 4,804 | 10.5ms | 18.4ms | 35.9ms | 89.0ms | 0 | 1,201 / 1,200 |
| 1만 | 로그인 | 601 | 63.9ms | 83.7ms | 151.1ms | 181.3ms | 0 | 601 / 600 |
| 10만 | — | 미측정(H) | | | | | | |
| 100만 | — | 미측정(H) | | | | | | |

- dropped iteration 0, 상태 코드 오류 0. 완료가 예정보다 1건 많은 것은 arrival-rate 구간 경계의 정상 오차다.
- 쓰기 지연은 iteration 안 4요청(담기·장바구니·Checkout·결제 확인) 각각의 응답 시간이다.

### 서버(backend `/actuator/prometheus`, 10초 간격 59회 수집)

| 규모 | HTTP 요청 | 5xx | Hikari 활성 최대 | Hikari 대기 최대 | 획득 시간 초과 | GC pause | heap 최대 | process CPU 최대 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1만 | 16,477 | 0 | 2 | 0 | 0 | 94회 / 0.294초 | 133 MiB | 39% |

스케줄 작업: 지표 갱신 3개가 각 10회 성공. 그 밖의 작업은 꺼져 있어 실행되지 않았다.

### DB(`performance_schema`, 측정 구간)

- row lock 대기 증가 0건, 대기 시간 0ms.
- 총 실행 시간 상위 SQL digest(값은 `?`로 정규화):

| 순위 | 문장(앞부분) | 실행 수 | 합계 | 평균 | 읽은 행 |
| ---: | --- | ---: | ---: | ---: | ---: |
| 1 | 상품 목록 조회 `SELECT p.id product_id, p.name, ...` | 5,891 | 29,975ms | 5.09ms | 5,140,992 |
| 2 | `COMMIT` | 16,297 | 11,731ms | 0.72ms | 0 |
| 3 | 상품 목록 건수 `SELECT /*+ NO_BNL(c, b) */ COUNT(*) FROM products p FORCE INDEX(PRIMARY) ...` | 3,283 | 8,302ms | 2.53ms | 6,483,925 |
| 4 | 상품 목록 건수 `SELECT COUNT(*) FROM products p JOIN categories c ...` | 1,324 | 5,054ms | 3.82ms | 1,377,364 |
| 5 | `SET autocommit = ?` | 32,593 | 2,986ms | 0.09ms | 0 |

전체 10개는 [evidence/mix-10k-g2.json](evidence/mix-10k-g2.json)의 `db.top_digests`에 있다.

### 쓰기 뒤 상태

- 측정으로 생긴 주문: PAID 1,439, PAYMENT_PENDING 3. PENDING은 실행이 끝날 때 Checkout까지만 하고 결제 확인 전에 끊긴 iteration이다(결제 만료 스케줄러가 꺼져 있어 남는다).
- `perfgen check --no-manifest`(도메인 불변식 25개) PASS.
- 정리 후 남은 container·network·volume 0개.

## 예열 구간 관찰

예열 2분에서는 쓰기 오류율 0.31%(상태 코드 오류 3건), 쓰기 p95 194ms, 회원 조회 max 1.3초였다. JVM이 막 기동된 상태라 측정 구간과 분리했다. 예열은 기록만 하고 판정하지 않는다.

## 환경 준비 시간 (1만)

| build | MySQL | Flyway 첫 기동 | 생성 | 검사 | cohort | 재기동 | 세션 150개 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2.7s(cache) | 11.6s | 52.3s | 15.1s | 8.0s | 8.9s | 47.2s | 21.0s |

## 위험과 제한

- 1만 규모 1회다. 같은 조건 반복 측정의 편차는 아직 재지 않았다.
- 요청률 20 iteration/s(HTTP 약 27 요청/s)는 도구 검증용 기본값이다. 규모 간 비교에 쓸 요청률과 단계적 증가(용량 상한)는 H에서 정한다.
- 정기배송 처리 시나리오(`--due-count` + 자동화 켜기)는 아직 없다.
- cache·Redis 세션을 끈 측정이라, 운영에서 cache를 켰을 때의 탐색 지연과는 다르다.

## 원자료

- [evidence/mix-10k-g2.json](evidence/mix-10k-g2.json): 실행 1회의 전체 보고서(환경, 예열·측정 k6 요약, 서버 지표, DB digest, 주문 상태, 불변식, 정리 결과). 비밀번호·세션·회원 이메일은 들어 있지 않다.
