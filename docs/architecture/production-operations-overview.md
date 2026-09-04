# Production 운영 아키텍처 개요

## 문서 목적과 상태

이 문서는 OPS-OCI-002의 현재 repository target architecture를 설명한다. 구현은 **Accepted — Repository Readiness**이며 **Production Verified가 아니다**. OCI tenancy, 네트워크·보안 규칙·A1 인스턴스·MySQL 연결·Object Storage·HTTPS의 실제 생성과 실행은 이 문서의 범위가 아니다.

## Target topology

```text
Internet
   |
   v
OCI VCN
   +-- Public Application Subnet
   |     +-- A1 Application host
   |           - Nginx / HTTPS
   |           - Frontend
   |           - Spring Boot Backend
   |           - metrics-proxy
   |
   +-- Private DB Subnet
   |     +-- MySQL HeatWave / MySQL.Free
   |
   +-- Trial Observability host (A1)
         - Prometheus
         - Grafana

OCI Object Storage
   - logical backup / manifest / completion marker

GitHub Actions
   - multiarch image publish / readiness / CI

Operator
   -> OCI Run Command
   -> Application host
```

Application release lifecycle and database lifecycle are deliberately separate. The Application Compose project owns exactly `backend`, `frontend`, and `proxy`; MySQL HeatWave is an external managed endpoint reached through `database-egress` with TLS `REQUIRED`. The Backend and Frontend images publish `linux/amd64` and `linux/arm64`; the Stage 1 MySQL tool image remains an immutable multiarch index.

| service | network | host exposure | state |
| --- | --- | --- | --- |
| `proxy` | `edge`, `app` | `80`, `443` | existing Nginx/Certbot volumes |
| `frontend` | `app` | none | container filesystem only |
| `backend` | `app`, `database-egress` | none | container filesystem only |

`app` is internal. `database-egress` is a dedicated non-internal bridge used only for the outbound managed database connection. No local database service, local database volume, or database cutover is part of Application activation.

## Network and security boundaries

- no public Backend 8080
- no public MySQL 3306
- no direct public Prometheus 9090
- no direct public Grafana 3000
- no initial load balancer
- no NAT requirement unless a measured operational need is approved

Nginx is the only published Application entry point. HTTPS state is validated by the existing certificate/domain contract before an enabled release is accepted. Datasource `REQUIRED` is the Repository Readiness encryption minimum; server certificate and hostname authentication are not Production Verified. Before any actual OCI managed DB credential connection, the endpoint and certificate chain must be checked, then `VERIFY_CA` or `VERIFY_IDENTITY` and trust material must receive a separate approval.

## Delivery and operation

`.github/workflows/publish-production-images.yml` publishes immutable SHA-tagged Backend and Frontend multiarch images. `production-release-readiness.yml` verifies both platforms and never activates a host. GitHub main push does not automatically deploy Production.

The operator-approved path is:

```text
materialize-runtime-env.sh
  -> invoke-oci-production-command.sh
  -> OCI Run Command
  -> production-command-dispatch.sh
  -> deploy.sh / rollback.sh
```

The dispatcher is bounded to `/opt/pawcycle/control` and `/opt/pawcycle/state`, validates the fetched main ancestry, and derives GHCR repositories from the HTTPS origin. The wrapper passes no credential parameters and only accepts bounded lifecycle results.

## Database backup boundary

`oci-db-backup-restore.sh` writes a logical dump, manifest, and completion marker to OCI Object Storage using instance principal authentication and no-overwrite/checksum verification. `restore-verify` downloads a complete object set into an isolated temporary MySQL container on `network none`, verifies schema/Flyway fingerprints and core tables, and removes explicitly created resources. It never restores or cuts over the managed Production DB.

## Capacity and region decisions

The Free Trial baseline is an Application A1 host plus a separate Observability A1 host. Long-term one-host versus two-host topology is **measurement pending** and must be decided from actual quota, memory headroom, failure-domain and recovery evidence. MySQL.Free HA/read replica/storage scaling, Redis, Queue, Kafka, and load balancing remain **DEFER** until measured need and quota justify a new decision.

The Home Region is **pending Oracle signup/support**. Tokyo is only the current candidate; no region, OCID, IP, bucket name, or credential is committed here.

## Rollback and verification boundary

Release state transitions retain current/previous SHA, contract SHA, incomplete transition markers, contract approvals, migration-boundary protection, immutable image/revision checks, health/smoke/HTTPS verification, and fail-closed state publication. An Application rollback changes Application images only; managed database state is not modified. Actual OCI resource provisioning, A1 deployment, managed DB connection, Object Storage backup, Run Command execution, HTTPS, and restore rehearsal remain unverified gates.
