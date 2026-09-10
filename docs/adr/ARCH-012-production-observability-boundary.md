# ARCH-012 Production Observability Boundary

## 상태

Accepted — OPS-OBS-001D 저장소 변경. OPS-OCI-005A는 OCI app01 runtime identity와 source/control checkout 정합성만 보정한다. 실제 Production 적용·검증은 포함하지 않는다.

## 결정

OCI 초기 Observability는 Application이 실행 중인 `app01` same-host에서 별도 Compose project로 운영한다. `metrics-proxy`도 Application release Compose와 분리된 `infra/production-metrics-proxy` sibling project로 운영한다. 이 project는 `pawcycle-production-edge`와 `pawcycle-production-app`을 external network로만 참조하고 backend service name `backend:8080`에 연결한다. Production Application Compose는 OCI Managed MySQL을 소유하지 않고 `backend`/`frontend`/`proxy`만 소유한다.

Application release lifecycle이 metrics-proxy를 재기동하지 않으므로 metrics-proxy는 Docker embedded DNS resolver와 동적 upstream resolution을 사용한다. Backend container가 교체되어 `backend`의 IP가 바뀌어도 metrics-proxy를 recreate하지 않고 새 주소를 재해석할 수 있어야 한다.

Production host는 Backend `:8080`을 계속 host에 공개하지 않는다. 승인된 metrics 경계만 standalone `metrics-proxy`에 연결하고, proxy는 `/actuator/prometheus`만 Backend에 전달하며 나머지 path는 거부한다.

Prometheus는 30초 scrape, 7일·5GB retention과 persistent volume을 사용한다. Grafana와 Prometheus UI는 host loopback에만 bind하며, 운영자 접근은 승인된 host-local forwarding으로 한정한다. Grafana admin user/password는 runtime 외부 file로만 주입한다.

## 측정 근거와 trade-off

- Production 기준은 2 vCPU, 약 1.9GiB RAM, `MemAvailable` 약 500MiB, swap 0이었다.
- 동일 host의 관측 당시 MySQL 약 504MiB, Backend 309MiB, Frontend 121MiB, Proxy 8MiB를 사용했다.
- 따라서 당시 AWS Production host에 Prometheus·Grafana full stack을 두는 선택은 memory headroom을 잠식하고 application과 같은 failure domain을 공유하므로 제외했다. 이 historical evidence는 OCI app01 same-host baseline을 금지하지 않는다.
- 향후 Production host를 증설하거나 Observability를 별도 Compute로 분리하는 일은 OCI evidence와 별도 승인으로 결정한다.
- OCI app01 대상의 고정 image manifest와 observability image boundary는 repository validator가 매번 preflight한다.

## 범위와 후속 결정

Managed Observability(AMP/Managed Grafana)와 Alerting은 이번 결정에서 제외한다. 실제 OCI network·IAM·Compute·Secret 생성과 Production Compose 실행은 별도 고위험 실제 운영 실행 승인에서만 한다.

OCI Application의 release/deploy/rollback, health/smoke, HTTPS/certificate, Managed MySQL과 migration boundary는 observability lifecycle 밖에 둔다. Observability는 OCI Application source/control checkout `/opt/pawcycle/source/repo`를 기준으로 승인된 SHA의 detached artifact만 사용하며, Application release marker file을 만들거나 요구하지 않는다. 적용 전후에는 Application container ID, image ref/ID, Compose project/service label과 required network attachment를 read-only로 비교하고 불일치하면 fail-closed 한다. Application release lifecycle은 metrics-proxy를 recreate·wait·stop하지 않는다. metrics-proxy 장애는 해당 scrape 경로만 실패시킬 수 있으며 DB·volume·migration state를 변경하는 경로를 갖지 않는다.
