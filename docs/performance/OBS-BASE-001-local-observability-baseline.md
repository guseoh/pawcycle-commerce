# OBS-BASE-001 Local Observability 기준선

## 결과

- 상태: local-integration 검증 완료
- 기준 commit: `736b0559f6361bc67e39d51bce5c7e8b99fa730f`
- 측정 UTC: `2026-08-09T02:56:34Z`
- 실행 구분: 저장소 변경 + local-integration 검증
- Production·Cloud·AWS 실행: 없음

Prometheus는 Docker internal network의 `http://backend:8080/actuator/prometheus`를 15초 간격으로 scrape했고 target health는 `up`이었다. Grafana datasource health는 `OK`였고 datasource UID `pawcycle-prometheus`, Dashboard UID `pawcycle-local-observability`, 12개 panel이 provisioning됐다. Grafana datasource 경유 HTTP count query도 성공했으며 Browser에서 기본 metric과 custom metric panel 렌더링을 확인했다.

internet-facing Proxy의 `http://127.0.0.1:8080/actuator/prometheus`는 `404`를 반환했고 Backend Prometheus payload를 포함하지 않았다.

## 측정 환경

| 항목 | 값 |
| --- | --- |
| Docker Engine | `28.5.1`, Linux `amd64` |
| Docker Compose | `2.40.0-desktop.1` |
| Backend build/runtime | Eclipse Temurin Java `25.0.3_9` |
| MySQL | `8.4.10` |
| Prometheus | `3.13.2` |
| Grafana | `13.1.3` |
| scrape interval / timeout | 15초 / 10초 |
| traffic | 기존 `smoke.ps1 -Scenario Full` 1회 |
| gauge 추가 관측 | Backend Prometheus endpoint direct scrape 20회, MySQL connection 보완 관측 direct scrape 5회 |

Secret 값은 출력하거나 문서화하지 않았다. 기존 local-integration `.env.local`을 process와 Compose 입력에만 사용했다.

## Metric 변화

| metric | traffic 전 | traffic 후 | 변화 |
| --- | ---: | ---: | ---: |
| `sum(http_server_requests_seconds_count)` | 30 | 44 | +14 |
| `sum(jvm_memory_used_bytes{area="heap"})` | 51,658,824 bytes | 76,824,648 bytes | +25,165,824 bytes |
| `sum(hikaricp_connections_usage_seconds_count)` | 60 | 73 | +13 |
| Hikari active / idle / pending | 측정 후 확인 | 0 / 10 / 0 | pending 없음 |
| reconciliation executions | 1 | 1 | 기존 조건부 trigger 실행 1회 확인 |

`FOUNDATION-004 smoke scenario passed: Full`을 확인했다. reconciliation metric은 기존 조건부 trigger의 실행 결과만 사용했으며 cadence를 변경하지 않았다. idempotency cleanup은 승인된 runtime trigger와 운영 batch size가 없으므로 실행시키지 않았고 Phase A Backend integration test 증거를 사용했다.

## Resource snapshot

`docker stats --no-stream`의 같은 local stack 단일 snapshot이다. CPU는 순간값이며 capacity 또는 운영 sizing 근거로 사용하지 않는다.

| container | CPU 전 / 후 | memory 전 / 후 |
| --- | ---: | ---: |
| MySQL | 0.63% / 0.40% | 473.7 / 474.4 MiB |
| Backend | 4.52% / 0.17% | 394.1 / 394.7 MiB |
| Frontend | 0.17% / 0.00% | 100.0 / 97.68 MiB |
| Proxy | 2.44% / 0.00% | 10.55 / 10.52 MiB |
| Prometheus | 0.31% / 0.29% | 30.55 / 31.01 MiB |
| Grafana | 0.73% / 0.96% | 195.9 / 195.8 MiB |

Container writable layer와 root filesystem snapshot은 다음과 같다.

| container | writable layer | root filesystem |
| --- | ---: | ---: |
| MySQL | 32,768 bytes | 867,487,744 bytes |
| Backend | 69,632 bytes | 401,129,472 bytes |
| Frontend | 40,960 bytes | 796,708,864 bytes |
| Proxy | 53,248 bytes | 66,486,272 bytes |
| Prometheus | 4,096 bytes | 251,953,152 bytes |
| Grafana | 8,192 bytes | 1,199,378,432 bytes |

Named volume 사용량 snapshot은 MySQL `216,040 KiB`, Prometheus `64 KiB`, Grafana `50,240 KiB`였다. Grafana image와 초기 volume이 local storage 증가의 주요 관측 항목이지만 이 단일 local snapshot으로 Production 배치 또는 비용을 결정하지 않는다.

## Gauge scrape와 DB connection

retained row와 cleanup candidate gauge는 한 scrape에서 creation·command별 `COUNT(*)` 네 개를 실행한다. 20회 direct scrape와 자연 15초 scrape가 겹친 구간에서 Hikari usage count는 101에서 188로 87 증가했다. 이후 Hikari active / idle / pending은 0 / 10 / 0으로 복귀했다.

MySQL connection 보완 관측에서는 direct scrape 5회 전후 `Threads_connected`가 12에서 12로 유지됐다. 이 결과는 해당 local 조건에서 지속 connection 증가나 pending이 관측되지 않았다는 근거일 뿐이다. 자연 scrape가 겹쳤으므로 usage 증가 87을 direct scrape만의 정확한 query 수나 latency로 해석하지 않는다.

## 검증 결과

- `docker compose --env-file .env.local -f compose.yaml -f compose.observability.yaml config --quiet`: 통과
- Prometheus `promtool check config`: 통과
- Java 25 `ObservabilityIntegrationTests`: 2개 통과, MySQL 8.4.10 사용
- Prometheus target: `up`, scrape URL `http://backend:8080/actuator/prometheus`
- Grafana datasource: health `OK`; Dashboard 12개 panel provisioning 확인
- Proxy `/actuator/prometheus`: `404`, Prometheus payload 없음
- `OBS-BASE-001 / 일반 / 저장소 변경` artifact validator: 통과
- `git diff --check`: 통과

## 제한과 후속 결정

- 단일 local snapshot이므로 Production CPU, memory, storage 또는 DB capacity를 대표하지 않는다.
- Production cache, refresh cadence, query timeout, alert threshold와 배치 크기는 결정하지 않았다.
- Scheduler, cleanup API endpoint와 runtime cleanup trigger를 추가하지 않았다.
- Production Compose와 healthcheck를 변경하지 않았다.
- local observability 구성의 rollback은 두 Compose 파일을 함께 지정한 `docker compose down`으로 Prometheus와 Grafana container를 먼저 제거한 뒤 이 작업 PR을 revert하는 순서다. 일반 종료에서 named volume을 삭제하지 않는다.
