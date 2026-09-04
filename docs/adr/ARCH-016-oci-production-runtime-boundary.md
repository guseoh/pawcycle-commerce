# ARCH-016: OCI Production Runtime Boundary

- 상태: Accepted — Repository Readiness
- Production Verified 아님
- 작업 ID: OPS-OCI-002

## Context

기존 Production provider와 application host/database lifecycle 결합을 종료하고, 무료 장기 운영 후보를 위한 provider-neutral repository contract를 확정한다. 이 ADR은 저장소 준비와 실제 OCI tenancy·resource provisioning을 분리한다.

## Decision

1. 기존 Production runtime을 종료하고 OCI를 새 장기 Production provider 후보로 채택한다.
2. Free Trial baseline은 A1 ARM64 Application host와 별도 A1 ARM64 Observability host의 2-host 구조로 둔다.
3. Backend와 Frontend image는 동일 commit SHA로 `linux/amd64`와 `linux/arm64`를 모두 게시한다.
4. Application Compose는 `backend`, `frontend`, `proxy`만 소유한다. MySQL은 Compose 밖의 MySQL HeatWave / MySQL.Free managed endpoint다.
5. Application release lifecycle과 DB lifecycle은 분리한다. DB private endpoint는 `database-egress`에서 TLS minimum `REQUIRED`로 접근한다.
6. `VERIFY_CA`와 `VERIFY_IDENTITY`는 실제 endpoint·certificate chain을 확인한 뒤 hardening decision으로 남긴다.
7. Logical backup은 OCI Object Storage에 기록하고 instance principal과 no-overwrite/checksum 검증을 사용한다.
8. `restore-verify`는 isolated temporary MySQL에서만 수행하며 managed Production DB 자동 restore/cutover는 금지한다.
9. OCI Run Command는 operator-approved execution boundary다. GitHub Actions의 automatic Production deploy는 도입하지 않는다.
10. 장기 one-host versus two-host topology는 actual quota와 measurement로 결정한다.
11. MySQL.Free HA/read replica/storage scaling은 DEFER한다.
12. Redis, Queue, Kafka, load balancer, multi-instance는 measured need 전 도입하지 않는다.

## Consequences

- GitHub Actions는 multiarch image publish/readiness와 CI만 수행한다.
- 운영자는 `invoke-oci-production-command.sh`를 통해 bounded OCI Run Command를 승인·실행한다.
- Application rollback은 Application image만 변경하며 managed DB state를 수정하지 않는다.
- Object Storage backup과 restore verification은 Application activation과 독립된 lifecycle이다.
- 실제 NSG, subnet, A1, managed DB, Object Storage, certificate, quota와 cost는 이 ADR이 생성하지 않는다.

## Region and secrets

Home Region은 Oracle signup/support pending으로 확정하지 않는다. Tokyo는 현재 candidate일 뿐 tenancy 생성 전 미확정이다. OCI OCID, IP, bucket name, username, password, token과 기타 secret은 기록하지 않는다.

## Validation status

Repository validator, shell contract tests, fake OCI lifecycle tests와 multiarch manifest inspection은 repository readiness evidence다. 실제 OCI API, account/quota, A1 deployment, MySQL.Free connection, Object Storage backup, Run Command, Production HTTPS와 restore rehearsal은 검증하지 않았다.
