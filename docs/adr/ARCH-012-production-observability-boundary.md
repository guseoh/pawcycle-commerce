# ARCH-012 Production Observability Boundary

## 상태

Accepted — `OPS-OCI-005` 저장소 변경. 이 ADR은 repository 계약만 변경하며 실제 OCI·Production 적용과 검증은 포함하지 않는다.

## 결정

현재 Production topology는 OCI `app01`의 Backend/Frontend/Nginx와 별도 OCI Managed MySQL이다. Production Application Compose는 `backend`, `frontend`, `proxy`만 소유하고 database lifecycle을 소유하지 않는다.

초기 Observability는 `app01` same-host Lean baseline으로 운영한다.

- Prometheus와 Grafana는 `infra/production-observability`의 별도 Compose project로 유지한다.
- `metrics-proxy`는 `infra/production-metrics-proxy`의 sibling Compose project로 유지하고 Application release lifecycle과 독립적으로 관리한다.
- Prometheus는 Application Docker `app` network에 연결하고 `metrics-proxy:9464` Docker-internal target을 scrape한다. metrics-proxy에는 host port를 publish하지 않는다.
- metrics-proxy는 Backend service name `backend:8080`을 동적으로 resolve하고 `/actuator/prometheus`만 전달한다. 다른 path는 404로 거부한다.
- Prometheus와 Grafana UI는 `127.0.0.1`에만 publish한다. Grafana admin user/password는 runtime 외부 file로만 주입한다.
- Prometheus 30초 scrape, 7일·5GB retention, Prometheus/Grafana persistent volume과 세 개의 dashboard provisioning 계약은 유지한다.

별도 Observability Compute는 **Deferred — Evidence Triggered**다. 실제 적용 후 same-host overhead를 측정해 CPU·memory·disk·application HTTP latency/error-rate overhead가 확인되거나 monitoring failure-domain 분리가 요구될 때만 별도 Compute를 재검토한다. 이 결정은 지금 새 Compute, NSG/IAM, Managed Observability 또는 추가 비용을 승인하지 않는다.

app01은 1 OCPU / 6 GB이므로 초기 Compose는 기존 값을 확대하지 않고 Prometheus `384m`/`0.25 CPU`, Grafana `256m`/`0.15 CPU`, metrics-proxy `32m`/`0.05 CPU`의 보수적인 상한을 사용한다. 이 값은 성능 tuning 결과가 아니며, 실제 적용 후 OFF/ON calibration으로 재평가한다.

## 측정 근거와 host metric 경계

현재 repository에는 `node_exporter`, cAdvisor 또는 별도 host metric collector가 없다. 초기 baseline은 app01 host-native read-only command와 Prometheus/Application HTTP signal로 CPU·memory·disk·latency/error-rate를 같은 조건의 OFF/ON window에서 비교한다. 지속적인 host metric series가 필요해지면 host `/proc`·`/sys` mount, 권한, image lifecycle과 추가 resource를 결정해야 하므로 **Decision Required — Evidence Triggered**로 남긴다.

## 과거 AWS 선택의 보존

과거에는 Production application과 분리된 arm64 `t4g.small` Observability EC2, Security Group ingress, SSM port forwarding, separate metrics-proxy host를 선택했다. 당시 기준은 2 vCPU, 약 1.9 GiB RAM, `MemAvailable` 약 500 MiB, swap 0이었고, MySQL 약 504 MiB·Backend 309 MiB·Frontend 121 MiB·Proxy 8 MiB 사용량 evidence에 따라 same-host full stack을 제외했다.

이 선택과 측정은 당시 AWS resource evidence에 따른 역사적 결정으로 보존한다. 현재 OCI 실행 지침으로 재사용하거나 잘못된 결정으로 소급 재작성하지 않는다.

## 범위와 불변식

Managed Observability, Alertmanager, centralized logging/Loki, OpenTelemetry 전환과 application/JVM/DB tuning은 이번 결정에서 제외한다. 실제 OCI network, secret, database, Compute와 Compose 실행은 별도 고위험 운영 승인에서만 수행한다.

Observability 변경 또는 rollback은 Backend/Frontend/Proxy release identity, `current-sha`/`previous-sha`, Flyway/migration state, OCI Managed MySQL lifecycle/data, HTTPS runtime과 certificate renewal state를 변경하지 않는다. Observability failure가 발생하면 Observability Compose와 standalone metrics-proxy만 복구 대상으로 삼는다.
