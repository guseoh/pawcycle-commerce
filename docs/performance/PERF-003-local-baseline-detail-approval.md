# PERF-003 로컬 성능 기준선 세부 승인

## 문서 정보

- 작업 ID: `PERF-003`
- 역할: Tech Lead
- 상태: `Approved by PERF-003`
- 실행 게이트: `Open for One-time Local Baseline Measurement`
- 승인 주체: 사용자/Product Owner 겸 Tech Lead
- 승인 근거: PERF-003 작업 요청의 container sampling 방식과 warm-up 적용 단위 명시 승인
- 적용 대상: PERF-002에서 승인된 일회성 로컬 성능 기준선 측정
- 선행 승인: `docs/performance/PERF-002-local-baseline-approved-inputs.md`

이 문서가 container sampling 방식과 warm-up 적용 단위의 단일 승인 원본이다. PERF-002의 D1-A~D6-A는 변경하지 않으며, 실제 측정은 이 문서와 `docs/handoffs/PERF-003/tl-to-sre.md`를 입력으로 사용하는 별도 Platform/SRE 작업에서만 수행한다.

## Container sampling 승인

Container sampling은 고정 시간 간격이 아닌 event-based 방식으로 승인한다.

### 수집 위치와 횟수

각 warm 측정 단위마다 다음 세 위치에서 총 3회 수집한다.

1. `before`: 첫 측정 iteration 직전
2. `mid`: 중간 iteration 직후
3. `after`: 마지막 iteration 직후

- 30회 측정의 `mid`는 15번째 iteration 직후다.
- 10회 측정의 `mid`는 5번째 iteration 직후다.
- 10회 구독 상태 변경 cohort는 별도 warm-up을 적용하지 않지만 같은 `before`·`mid`·`after` container sampling을 적용한다.
- 각 표본에 `timestamp_utc`와 `before`, `mid`, `after` 관측 위치를 기록한다.
- 고정 시간 간격은 사용하지 않는다. 실제 표본 간 시간 차이는 `timestamp_utc`로 확인한다.
- `before`·`mid`·`after` 표본은 동일 warm 측정 단위의 iteration 경계에 결합한다.

### 지표별 집계

| 지표 | 승인 집계 |
| --- | --- |
| CPU percentage | 가용 표본 수, 평균, 최댓값 |
| memory usage bytes | 가용 표본 수, 평균, 최댓값 |
| Network RX/TX | 누적 counter 최초값, 최종값, 차이 |
| block read/write | 누적 counter 최초값, 최종값, 차이 |
| PIDs | 제공될 때만 가용 표본 수, 평균, 최댓값 |
| Container restart count | 측정 전 값, 측정 후 값, 차이 |

- Network RX/TX와 block read/write는 처리량으로 표현하지 않는다.
- Windows 또는 Docker 환경이 PIDs를 제공하지 않으면 `null`로 유지하며 0으로 대체하지 않는다.
- 가용하지 않은 PIDs 표본은 평균·최댓값 계산의 분모에 포함하지 않는다.
- Cold start 경과와 service health convergence는 HTTP elapsed와 container warm sampling에서 분리한다.

## Warm-up 적용 단위 승인

Warm-up은 다음 각 읽기 route의 측정 cohort 직전에 순차 5회 수행한다.

1. `GET /products`
2. `GET /api/products`
3. `GET /api/products/{productId}`
4. `GET /api/subscriptions`
5. `GET /api/subscriptions/{subscriptionId}`

- 각 route의 warm-up 5회가 끝난 직후 해당 route 측정 cohort를 시작한다.
- 인증된 읽기는 PERF-002에서 승인된 session·CSRF·login setup과 logout cleanup 경계를 그대로 사용한다.
- Warm-up 결과는 latency, status, 오류율과 표본 수 집계에서 모두 제외한다.
- 인증 lifecycle cohort에는 별도 warm-up을 적용하지 않는다.
- 구독 상태 변경 cohort에는 별도 warm-up을 적용하지 않는다.

## 실행 게이트 승인 결과

PERF-002에서 남은 container sampling 방식과 warm-up 적용 단위는 모두 `Approved by PERF-003`다. 이 두 세부 결정 때문에 설정됐던 `Blocked Pending Detail Approval`은 해소됐고 실행 게이트는 `Open for One-time Local Baseline Measurement`다.

Platform/SRE는 추가 제품·기술 결정을 만들지 않고 승인된 현재 PowerShell·Docker 도구 범위에서 일회성 로컬 성능 기준선 측정을 시작할 수 있다. 이 승인은 실제 측정 결과나 성능 판정을 미리 승인하는 것이 아니다.

## 변경하지 않은 승인과 미승인 항목

- PERF-002의 D1-A~D6-A cohort, 반복 횟수, concurrency 1, reset·seed, 계산법, 실패 처리와 증거 보존 정책은 유지한다.
- SLO, latency 목표, 오류 예산과 regression threshold는 미승인이다.
- concurrency 2 이상, arrival rate, 장시간·고부하 실행은 미승인이다.
- repository benchmark script, 신규 dependency·도구·외부 service는 미승인이다.
- 제품 코드, 테스트, Compose, Nginx, Dockerfile과 CI 변경은 미승인이다.
- 병목 확정과 최적화는 미승인이다.

## 적용 조건

- 실제 측정은 별도 Platform/SRE 작업 ID와 깨끗한 작업 트리에서 수행한다.
- 동일 commit, QA fixture, Docker Desktop 자원, 전원 모드와 background workload 조건을 고정한다.
- 원시 request record·log와 credential, cookie, session ID, CSRF token, body와 실제 데이터 ID를 저장소에 커밋하거나 장기 보존하지 않는다.
- 결과가 기대와 달라도 표본을 제거·대체하거나 승인 조건을 변경하지 않는다.

## 중단 조건

- PERF-002 또는 PERF-003의 승인 조건을 변경해야 함
- 실제 제품 코드, 실행 설정, CI, dependency 또는 repository benchmark script 변경이 필요함
- SLO·threshold·고부하·신규 도구·병목·최적화를 함께 결정해야 함
- 원시 record·log나 민감정보를 커밋하거나 장기 보존해야 함
- QA 회원 외 데이터 삭제, volume 삭제 또는 iteration별 reset이 필요함

## 승인 결과

Container sampling의 시점·횟수·지표별 집계·optional/null 규칙과 warm-up 5회의 route·실행 시점·제외 cohort는 `Approved by PERF-003`다. 실제 일회성 로컬 기준선 측정의 세부 결정 차단은 해소됐다. 자동화된 반복 측정, 운영 목표와 최적화는 승인되지 않았다.
