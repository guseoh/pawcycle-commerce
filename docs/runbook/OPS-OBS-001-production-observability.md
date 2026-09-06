# OPS-OBS-001 OCI Production Observability

## 범위와 상태

Application host와 분리된 Trial A1 Observability host에서 Prometheus·Grafana를 운영하고, Application host의 `metrics-proxy`만 scrape 경계로 사용한다. **Accepted — Repository Readiness**이며 **Production Verified가 아니다**. 실제 OCI subnet, NSG, host 접근과 Grafana 관리 경로는 별도 결정·승인 대상이다.

## Trial baseline

```text
Application host: Nginx, Frontend, Backend, metrics-proxy
Observability host: Prometheus, Grafana
```

- Prometheus/Grafana UI는 host loopback에만 bind한다.
- `metrics-proxy`는 `/actuator/prometheus`만 Backend에 전달하고 다른 path는 거부한다.
- Observability host와 Application host의 failure domain을 분리한다.
- Grafana 관리 접근 방식은 OCI network 생성 후 확정하며 public `3000`은 허용하지 않는다.

## 확인 절차

1. `bash infra/production-observability/validate-observability.sh`로 Compose/network/loopback contract를 확인한다.
2. `bash infra/production-metrics-proxy/test-metrics-proxy.sh`로 endpoint-only proxy와 rejection path를 확인한다.
3. Application host에서는 `diagnose-backend-state.sh --scope production`으로 non-sensitive snapshot을 만든다.
4. Observability host에서는 loopback Prometheus URL과 snapshot을 결합해 최종 `NORMAL`/`UNKNOWN`을 판정한다.

진단 snapshot에는 SHA·health·HTTP status와 같은 비민감 결과만 담고 runtime password, token, certificate, IP/OCID를 출력하지 않는다. 진단은 release lock을 획득하지 않으며 Application/DB state를 변경하지 않는다.

## 실패·롤백

- target down: Prometheus target과 metrics-proxy endpoint-only health만 확인하고 Application/DB에는 개입하지 않는다.
- metrics-proxy failure: standalone proxy만 재기동·복구하며 release SHA와 migration state를 변경하지 않는다.
- Prometheus/Grafana failure: 해당 host의 config/volume/권한만 확인하고 public 관리 port를 임시 개방하지 않는다.
- snapshot age/cardinality/parse 불일치: 최종 상태를 `UNKNOWN`으로 두고 에스컬레이션한다.

Observability rollback은 Observability Compose와 standalone metrics-proxy 범위로 제한한다. Application release, managed DB, Object Storage backup, Scheduler와 HTTPS lifecycle은 건드리지 않는다.

## Evidence status

Repository validator와 observability/metrics-proxy fixture tests는 통과했다. 실제 Trial host, OCI network, Prometheus scrape, Grafana 접근과 long-term one-host/two-host topology는 **Not Verified**이며 measurement pending이다.
