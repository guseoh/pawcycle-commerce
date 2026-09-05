# OPS-OCI-002 OCI Production DB Backup/Restore

## 범위와 상태

이 Runbook은 `oci-db-backup-restore.sh`의 repository 기준 logical backup, manifest/completion, isolated restore verification과 cleanup을 정의한다. **Accepted — Repository Readiness**이며 **Production Verified가 아니다**. 실제 managed DB, Object Storage, credential과 OCI API는 실행하지 않는다.

## Required runtime

- root가 소유한 runtime `current` bundle과 정확한 `backend.env` 10개 key
- 별도 backup credential file: `MYSQL_BACKUP_USERNAME`, `MYSQL_BACKUP_PASSWORD` 두 key, mode `600`, current UID 소유
- OCI CLI와 instance principal
- MySQL 8.4.10 immutable multiarch tool image
- bucket/prefix/region은 승인된 운영 입력으로만 전달하며 문서·로그에 기록하지 않음

`backend.env`는 private IP/FQDN, port `3306`, database, JDBC URL, username/password, TLS `REQUIRED`를 제공한다. root/server credential을 Backend runtime에 섞지 않는다.

`REQUIRED`는 Repository Readiness 단계의 encryption minimum이며 server certificate/hostname authentication을 Production Verified로 만들지 않는다. 실제 OCI managed DB credential connection 전에 endpoint와 certificate chain을 확인하고, 그 결과를 바탕으로 별도 승인된 `VERIFY_CA` 또는 `VERIFY_IDENTITY`와 trust material contract를 적용해야 한다. 이 Gate 전에는 Production execution을 시작하지 않는다.

## Backup

```bash
sudo bash /opt/pawcycle/control/infra/production/oci-db-backup-restore.sh backup \
  --runtime-dir /opt/pawcycle/runtime \
  --backup-credential-file /run/pawcycle/backup-credentials \
  --bucket <approved-bucket> --prefix <approved-prefix> --region <approved-region>
```

script는 one-shot MySQL tool container에서 `mysqldump`를 수행하고, schema/Flyway fingerprint와 dump hash/size를 manifest에 기록한다. Object Storage에는 `dump.sql.gz` → `manifest.txt` → `complete` 순서로 instance principal, `--no-overwrite`, `--verify-checksum`을 사용한다. completion marker가 없으면 backup을 성공으로 보지 않는다.

## Restore verification

```bash
sudo bash /opt/pawcycle/control/infra/production/oci-db-backup-restore.sh restore-verify \
  --runtime-dir /opt/pawcycle/runtime \
  --backup-credential-file /run/pawcycle/backup-credentials \
  --bucket <approved-bucket> --prefix <approved-prefix> --region <approved-region> \
  --backup-id <timestamp-random-id>
```

completion → manifest → dump를 다운로드해 hash·size를 확인한다. 임시 MySQL은 named volume과 `network none`, host port 미공개, 제한된 read-only/tmpfs/security 경계로 시작한다. 복원 후 schema fingerprint, Flyway fingerprint와 `members/products/skus/subscriptions` core table query를 확인한다.

Source core-table count는 별도 query connection이 `mysqldump --single-transaction`과 동일한 snapshot임을 보장하지 않으므로 manifest에 기록하지 않는다. 대신 restore 쪽 count 결과 형식과 schema/Flyway·dump hash/size를 strict하게 검증한다.

## Cleanup and failure handling

```bash
sudo bash /opt/pawcycle/control/infra/production/oci-db-backup-restore.sh cleanup \
  --runtime-dir /opt/pawcycle/runtime \
  --backup-credential-file /run/pawcycle/backup-credentials \
  --bucket <approved-bucket> --prefix <approved-prefix> --region <approved-region> \
  --backup-id <timestamp-random-id>
```

cleanup은 정확한 backup ID의 dump/manifest/complete object와 생성된 temporary container/volume/workdir만 제거한다. 하나라도 cleanup에 실패하면 작업은 실패로 남긴다. object already exists, missing completion, hash/fingerprint mismatch, credential mode/owner 오류는 fail-closed 한다.

## 금지 경계

이 절차는 **Production DB mutation 금지**다. managed Production DB에 restore, cutover, schema migration, credential/grant 생성 또는 data deletion을 수행하지 않는다. Application deploy/rollback과 DB backup lifecycle은 분리한다. Secret이나 실제 식별값은 stdout·commit·PR·문서에 기록하지 않는다.

## Evidence status

fake OCI/Object Storage lifecycle, checksum/order, isolated restore, credential boundary와 cleanup failure tests는 repository에서 검증한다. 실제 bucket, instance principal, managed DB backup, restore rehearsal, OCI account/quota는 **Not Verified**다.
