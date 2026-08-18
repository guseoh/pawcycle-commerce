# ARCH-013: 향후 MySQL RDS Single-AZ 전환 준비

- 상태: Approved — repository readiness only
- 날짜: 2026-08-18
- 작업 ID: OPS-DB-002

## Context

현재 Production은 `ap-northeast-2d`의 t3.medium EC2 한 대와 Docker MySQL 8.4, 보존되는 `pawcycle-production-mysql-data` volume에 결합돼 있다. 따라서 EC2와 DB는 같은 failure domain이며, host/engine lifecycle 책임도 현재 EC2 Control에 직접 있다. OPS-013 logical backup과 isolated restore verification은 완료된 입력이나, automated backup/PITR과 실제 RDS restore는 검증되지 않았다. 향후 private RDS MySQL 8.4 전환에는 현재 Docker 기본 경로를 바꾸지 않는 명시적 runtime·read-only preflight·증거 경계가 필요하다.

## Decision

별도 고위험 운영 승인을 전제로 private `ap-northeast-2` RDS MySQL 8.4, `db.t4g.micro`, gp3 minimum 20 GiB, encryption, Single-AZ를 목표 계약으로 둔다. EC2와 같은 AZ를 우선하되 subnet group은 최소 2 AZ를 사용한다. Public access는 OFF, EC2 SG → RDS SG TCP 3306만 허용, automated backup/PITR은 생성 시 필수다. Backend만 database-egress network를 사용하며 RDS datasource는 endpoint:3306과 TLS `sslMode=REQUIRED`를 사용한다. 현재 Docker 기본은 `mysql:3306`, `sslMode=DISABLED`로 유지한다. 7-day retention은 후보일 뿐 강제하지 않는다.

## Why Single-AZ

Single-AZ는 현재 단일 EC2 규모·비용의 준비 범위이며 high availability 보장이 아니다. Multi-AZ 비용은 application이 여전히 단일 EC2인 동안 end-to-end HA를 제한적으로만 높인다. 측정된 availability/RTO 요구사항이 이를 정당화할 때 Multi-AZ를 재평가한다. read replica, RDS Proxy, VERIFY_CA/VERIFY_IDENTITY용 CA/truststore hardening, application multi-instance는 별도 결정이다.

## Consequences

- Managed RDS는 host/engine patch·storage 책임을 줄이고 automated backup/PITR capability를 제공한다.
- 비용과 migration/import/data divergence risk가 새로 생기며, RDS는 application HA나 EC2/Scheduler failure를 자동으로 해결하지 않는다.
- Repository gate는 source Docker volume과 verified logical backup/isolated restore fingerprints를 보존하고 RDS target evidence를 비교한다.

## Alternatives and deferred work

현 Docker MySQL 유지, Multi-AZ, read replica, RDS Proxy, VERIFY_CA/VERIFY_IDENTITY CA/truststore hardening, application multi-instance는 이번 결정에서 제외한다. retention 7 days는 비용·운영 승인 전 후보일 뿐이다.

## Validation and user approval

Validation은 fixture/static 계약만 확인한다. 이 ADR이나 fixture 성공은 RDS 생성, automated backup/PITR 적용, PITR restore, Production migration/cutover 또는 Production Verified를 뜻하지 않는다. 실제 운영은 명시적 고위험 사용자 승인, pre/post evidence, rollback boundary가 필요하다.
