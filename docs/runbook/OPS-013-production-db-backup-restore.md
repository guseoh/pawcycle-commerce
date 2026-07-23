# OPS-013 운영 DB 논리 백업·격리 복원 Runbook

## 목적

운영 MySQL application DB를 중지하거나 변경하지 않고 압축 논리 dump를 비공개 S3에 저장한다. dump를 production network와 분리된 임시 MySQL·named volume에 먼저 복원해 같은 snapshot에서 schema, Flyway history와 핵심 table count manifest를 만들고, 이후 독립 복원 검증에서도 일치하는지 확인한다.

실제 bucket·region·prefix, account·role 식별자, credential, dump 원문과 row는 저장소·PR·실행 로그에 기록하지 않는다.

## 증상과 영향

- 증상: 논리 백업 또는 복원 검증 증거가 없거나 최근 백업의 무결성을 확인할 수 없음
- 영향: MySQL volume 장애나 운영자 실수 뒤 application DB 복구 가능성을 입증할 수 없음
- 이 Runbook의 한계: 논리 백업·격리 복원 검증만 다루며 production DB restore, EBS 복구와 application rollback을 수행하지 않음

## 고정 계약

- source: Compose project `pawcycle-production`의 healthy MySQL 한 개
- source volume: `pawcycle-production-mysql-data`, 읽기 전용 논리 dump 대상
- MySQL image: production과 동일한 저장소 고정 digest
- AWS region: 승인된 서울 region `ap-northeast-2`
- S3 bucket: OPS-013 전용으로 새로 생성한 빈 private bucket과 예상 bucket owner 일치
- S3 storage class: S3 Standard
- encryption: SSE-S3 `AES256`
- retention: 지정 prefix 아래 object 생성 14일 뒤 만료
- restore: `--network none`, host port 없음, 고유 임시 named volume
- 핵심 table: `members`, `products`, `skus`, `subscriptions`
- completion marker가 없는 object set은 복원 대상으로 사용하지 않음

## 중단 조건

다음 중 하나면 실행하지 않는다.

- 최신 main과 현재 production release·HTTPS·MySQL health가 불명확함
- production MySQL image 또는 volume이 고정 계약과 다름
- MySQL migration·DDL 또는 대량 쓰기가 진행 중임
- AWS region이 승인된 서울 region `ap-northeast-2`가 아니거나 EC2와 bucket에서 일치하는지 확인할 수 없음
- S3 bucket owner가 현재 승인 AWS account와 일치하는지 확인할 수 없음
- bucket이 OPS-013 전용 신규 빈 bucket인지 확인할 수 없음
- bucket Public Access Block 네 항목, SSE-S3 또는 14일 prefix lifecycle을 확인할 수 없음
- instance role이 아닌 access key·Secret을 명령행이나 파일에 입력해야 함
- backup work root나 runtime 파일이 symlink이거나 root 전용 mode가 아님
- script가 요구하는 disk·available memory preflight를 통과하지 못함
- 단일 compressed dump object가 승인된 5,000,000,000 byte single-request upload 한도를 초과함
- production service 중지, production DB 쓰기 또는 production volume 변경이 필요함
- 임시 restore container가 `none` network·무 publish·별도 volume 계약을 만족하지 않음

## 1. 저장소와 production 기준 확인

```bash
cd /opt/pawcycle/repository
git fetch --prune origin
git switch main
git pull --ff-only origin main
git status --short

sudo docker ps \
  --filter label=com.docker.compose.project=pawcycle-production \
  --format '{{.Label "com.docker.compose.service"}} {{.Status}}'
sudo docker volume inspect pawcycle-production-mysql-data >/dev/null
sudo docker compose version
aws --version
df -h /opt/pawcycle /var/lib/docker
free -h
```

`git status --short`는 비어 있어야 하고 네 production container가 healthy여야 한다. application SHA, 실제 hostname, IP와 credential은 증거에 복사하지 않는다.

## 2. 비공개 S3 bucket 준비

이 단계는 bucket·IAM을 변경할 권한이 있는 사용자 작업이다. EC2 instance role 권한과 분리해 수행한다. 아래 식별자는 현재 shell에서만 보유하고 terminal transcript에 값을 출력하지 않는다.

```bash
read -r -s -p 'Backup bucket: ' BACKUP_BUCKET
printf '\n'
read -r -s -p 'Backup prefix: ' BACKUP_PREFIX
printf '\n'

[[ "$BACKUP_BUCKET" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]]
[[ "$BACKUP_PREFIX" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]*[A-Za-z0-9]$ ]]

export PAWCYCLE_BACKUP_BUCKET="$BACKUP_BUCKET"
export PAWCYCLE_BACKUP_REGION="ap-northeast-2"
export PAWCYCLE_BACKUP_PREFIX="$BACKUP_PREFIX"
export PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER="$(aws sts get-caller-identity --query Account --output text)"
[[ "$PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" =~ ^[0-9]{12}$ ]]
unset BACKUP_BUCKET BACKUP_PREFIX
```

`PAWCYCLE_BACKUP_REGION`은 승인된 서울 region `ap-northeast-2`로 고정한다. `PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER`는 현재 승인 AWS account에서 자동 조회하며 값을 문서·로그에 복사하지 않는다.

OPS-013은 lifecycle·bucket policy의 기존 설정을 덮어쓰는 위험을 없애기 위해 **전용 신규 빈 bucket만 허용**한다. 기존 bucket 재사용은 이 Runbook 범위에서 제외한다. 생성 명령이 이미 존재하는 bucket 때문에 실패하면 다른 전용 bucket 이름으로 다시 시작하고 기존 bucket 설정을 변경하지 않는다.

```bash
if ! aws s3api create-bucket \
    --bucket "$PAWCYCLE_BACKUP_BUCKET" \
    --region "$PAWCYCLE_BACKUP_REGION" \
    --create-bucket-configuration "LocationConstraint=$PAWCYCLE_BACKUP_REGION" \
    >/dev/null; then
  printf '%s\n' '전용 신규 bucket 생성 실패: 기존 bucket을 변경하지 마세요.' >&2
  exit 1
fi
```

Public Access Block 네 항목과 SSE-S3 기본 암호화를 적용한다.

```bash
aws s3api put-public-access-block \
  --bucket "$PAWCYCLE_BACKUP_BUCKET" \
  --region "$PAWCYCLE_BACKUP_REGION" \
  --public-access-block-configuration \
  'BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true'

aws s3api put-bucket-encryption \
  --bucket "$PAWCYCLE_BACKUP_BUCKET" \
  --region "$PAWCYCLE_BACKUP_REGION" \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
```

현재 AWS 관리자 사용자만 읽을 수 있는 mode `600` 임시 파일로 지정 prefix의 14일 만료 rule을 적용한다. `put-bucket-lifecycle-configuration`은 bucket의 lifecycle 전체를 교체하므로 위에서 생성한 OPS-013 전용 빈 bucket에만 실행한다.

```bash
umask 077
LIFECYCLE_FILE="$(mktemp)"
chmod 600 "$LIFECYCLE_FILE"
tee "$LIFECYCLE_FILE" >/dev/null <<EOF
{
  "Rules": [
    {
      "ID": "expire-ops013-backups-after-14-days",
      "Status": "Enabled",
      "Filter": {"Prefix": "${PAWCYCLE_BACKUP_PREFIX}/"},
      "Expiration": {"Days": 14}
    }
  ]
}
EOF

if ! aws s3api put-bucket-lifecycle-configuration \
  --bucket "$PAWCYCLE_BACKUP_BUCKET" \
  --region "$PAWCYCLE_BACKUP_REGION" \
  --lifecycle-configuration "file://$LIFECYCLE_FILE"; then
  rm -f -- "$LIFECYCLE_FILE"
  unset LIFECYCLE_FILE
  exit 1
fi
rm -f -- "$LIFECYCLE_FILE"
unset LIFECYCLE_FILE
```

bucket policy에는 TLS와 지정 prefix의 `AES256` upload를 강제한다. 실제 bucket·prefix로 치환한 policy는 저장소가 아닌 현재 AWS 관리자 사용자만 읽을 수 있는 mode `600` 임시 파일에서 적용하고 즉시 삭제한다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "DenyInsecureTransport",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:*",
      "Resource": [
        "arn:aws:s3:::<backup-bucket>",
        "arn:aws:s3:::<backup-bucket>/*"
      ],
      "Condition": {"Bool": {"aws:SecureTransport": "false"}}
    },
    {
      "Sid": "RequireSseS3ForBackupPrefix",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::<backup-bucket>/<backup-prefix>/*",
      "Condition": {
        "StringNotEquals": {
          "s3:x-amz-server-side-encryption": "AES256"
        }
      }
    }
  ]
}
```

실행할 때는 위 구조를 다음 root 전용 임시 파일로 생성해 적용한다.

```bash
umask 077
BUCKET_POLICY_FILE="$(mktemp)"
chmod 600 "$BUCKET_POLICY_FILE"
tee "$BUCKET_POLICY_FILE" >/dev/null <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "DenyInsecureTransport",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:*",
      "Resource": [
        "arn:aws:s3:::${PAWCYCLE_BACKUP_BUCKET}",
        "arn:aws:s3:::${PAWCYCLE_BACKUP_BUCKET}/*"
      ],
      "Condition": {"Bool": {"aws:SecureTransport": "false"}}
    },
    {
      "Sid": "RequireSseS3ForBackupPrefix",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::${PAWCYCLE_BACKUP_BUCKET}/${PAWCYCLE_BACKUP_PREFIX}/*",
      "Condition": {
        "StringNotEquals": {
          "s3:x-amz-server-side-encryption": "AES256"
        }
      }
    }
  ]
}
EOF

if ! aws s3api put-bucket-policy \
  --bucket "$PAWCYCLE_BACKUP_BUCKET" \
  --region "$PAWCYCLE_BACKUP_REGION" \
  --policy "file://$BUCKET_POLICY_FILE"; then
  rm -f -- "$BUCKET_POLICY_FILE"
  unset BUCKET_POLICY_FILE
  exit 1
fi
rm -f -- "$BUCKET_POLICY_FILE"
unset BUCKET_POLICY_FILE
```

## 3. EC2 instance role 최소 권한

실제 role·account ARN은 저장소에 기록하지 않는다. `<backup-bucket>`과 `<backup-prefix>`만 사용자 로컬 정책에서 치환한다. lifecycle·encryption·Public Access Block 쓰기 권한은 instance role에 주지 않는다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadBackupBucketContract",
      "Effect": "Allow",
      "Action": [
        "s3:GetBucketLocation",
        "s3:GetBucketPublicAccessBlock",
        "s3:GetEncryptionConfiguration",
        "s3:GetBucketVersioning",
        "s3:GetLifecycleConfiguration"
      ],
      "Resource": "arn:aws:s3:::<backup-bucket>"
    },
    {
      "Sid": "WriteAndVerifyBackupObjects",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::<backup-bucket>/<backup-prefix>/*"
    }
  ]
}
```

`s3:DeleteObject`, wildcard bucket, KMS와 public ACL 권한은 추가하지 않는다.

## 4. bucket 계약 확인

다음 값만 확인하고 실제 식별자는 증거에 복사하지 않는다.

```bash
aws s3api get-bucket-location \
  --bucket "$PAWCYCLE_BACKUP_BUCKET" --region "$PAWCYCLE_BACKUP_REGION" \
  --expected-bucket-owner "$PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" \
  --query LocationConstraint --output text

aws s3api get-public-access-block \
  --bucket "$PAWCYCLE_BACKUP_BUCKET" --region "$PAWCYCLE_BACKUP_REGION" \
  --expected-bucket-owner "$PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" \
  --query '[PublicAccessBlockConfiguration.BlockPublicAcls,PublicAccessBlockConfiguration.IgnorePublicAcls,PublicAccessBlockConfiguration.BlockPublicPolicy,PublicAccessBlockConfiguration.RestrictPublicBuckets]' \
  --output text

aws s3api get-bucket-encryption \
  --bucket "$PAWCYCLE_BACKUP_BUCKET" --region "$PAWCYCLE_BACKUP_REGION" \
  --expected-bucket-owner "$PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" \
  --query 'ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm' \
  --output text

aws s3api get-bucket-versioning \
  --bucket "$PAWCYCLE_BACKUP_BUCKET" --region "$PAWCYCLE_BACKUP_REGION" \
  --expected-bucket-owner "$PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" \
  --query Status --output text

aws s3api get-bucket-lifecycle-configuration \
  --bucket "$PAWCYCLE_BACKUP_BUCKET" --region "$PAWCYCLE_BACKUP_REGION" \
  --expected-bucket-owner "$PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" \
  --query "length(Rules[?Status=='Enabled' && Expiration.Days==\`14\` && Filter.Prefix=='${PAWCYCLE_BACKUP_PREFIX}/'])" \
  --output text
```

기대값은 같은 region, `True True True True`, `AES256`, versioning `None`, 전체 lifecycle rule count와 14일 지정 prefix 일치 count가 각각 정확히 `1`이다. bucket은 OPS-013 전용 신규 bucket이어야 하며 versioning이 `Enabled` 또는 `Suspended`이거나 다른 lifecycle rule이 있으면 사용하지 않는다.

## 5. 운영 논리 백업

DDL·migration이 없는 저부하 시점에 수행한다. `--single-transaction`은 일반 row 쓰기를 막지 않지만 dump 중 `ALTER`, `CREATE`, `DROP`, `RENAME`, `TRUNCATE`는 금지한다.

```bash
cd /opt/pawcycle/repository
sudo --preserve-env=PAWCYCLE_BACKUP_BUCKET,PAWCYCLE_BACKUP_REGION,PAWCYCLE_BACKUP_PREFIX,PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER \
  infra/production/db-backup-restore.sh backup
```

script는 다음 순서로 fail-close한다.

1. healthy production MySQL·고정 image·고정 volume 확인
2. mysql·mysqldump, 대상 DB·Flyway·핵심 table 확인
3. DB 크기 기준 disk와 available memory 확인
4. `--single-transaction --quick --skip-lock-tables` 압축 dump 생성
5. dump를 `none` network 임시 MySQL에 import하고 같은 snapshot의 schema·Flyway·핵심 table count manifest 생성
6. metadata object는 1 MiB, dump object는 5,000,000,000 byte 한도를 확인한 뒤 dump·manifest와 checksum 업로드
7. S3 size·SSE-S3·다운로드 checksum 재검증
8. 마지막에만 completion marker 업로드

성공 로그의 backup ID만 비민감 증거로 기록한다. 실제 object key, bucket과 row count는 기록하지 않는다.

## 6. 격리 복원 검증

성공한 backup ID를 입력한다. production DB container와 application에는 연결하지 않는다.

```bash
read -r -p 'Backup ID: ' BACKUP_ID
sudo --preserve-env=PAWCYCLE_BACKUP_BUCKET,PAWCYCLE_BACKUP_REGION,PAWCYCLE_BACKUP_PREFIX,PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER \
  infra/production/db-backup-restore.sh restore-verify \
  --backup-id "$BACKUP_ID"
```

script는 completion marker, object size·SSE-S3, SHA-256과 gzip을 확인한 뒤 실제 압축 해제 크기를 기준으로 Docker disk 여유를 계산하고 복원한다. 임시 MySQL은 다음 계약을 가진다.

- production과 동일한 pinned MySQL image
- `--network none`
- host port publish 없음
- `pawcycle-restore-verify-<backup-id>-<suffix>` 전용 named volume
- 무작위 root password를 mode `600` 임시 파일로만 전달
- production MySQL volume mount 없음
- 640 MiB memory, 0.70 CPU, 256 PID 상한

복원 뒤 schema fingerprint, Flyway fingerprint·history count와 네 핵심 table count를 dump snapshot에서 생성한 manifest와 비교한다. 실제 row와 count 값은 stdout에 출력하지 않는다.

## 7. cleanup과 재실행

정상·오류 종료에서는 trap이 임시 container·volume·파일을 제거한다. 강제 종료로 남은 resource는 정확한 backup ID로만 정리한다.

```bash
sudo infra/production/db-backup-restore.sh cleanup --backup-id "$BACKUP_ID"
```

cleanup은 OPS-013 restore label, `none` network, production volume 미사용을 다시 확인한 resource만 제거한다. production container·volume·state와 S3 object는 삭제하지 않는다. 같은 backup을 다시 검증하거나 새 backup을 생성해도 이름 충돌이 없어야 한다.

## 8. 실패 판정과 복구

| 실패 | 판정 | 안전한 조치 |
| --- | --- | --- |
| production health·image·volume 불일치 | backup 중단 | production 원인을 먼저 확인 |
| disk·memory 부족 | backup 또는 restore 중단 | application을 중지하지 말고 용량 계획을 별도 승인 |
| bucket 계약·IAM 실패 | upload 전 중단 | PAB·SSE-S3·lifecycle·role 정책 수정 후 재시도 |
| dump·gzip 실패 | backup 실패 | 임시 파일 cleanup 확인 후 새 backup ID로 재시도 |
| compressed object 5,000,000,000 byte 초과 | backup 실패 | multipart 권한을 임의 추가하지 말고 별도 설계 승인 |
| upload·head·download checksum 실패 | backup 실패 | completion marker 부재를 확인하고 새 backup 생성 |
| restore SQL 실패 | 복원 검증 실패 | 임시 resource cleanup 뒤 원본 backup 상태 조사 |
| schema·Flyway·table count 불일치 | 복원 검증 실패 | 성공으로 기록하지 말고 DDL·dump 또는 manifest 손상 조사 |

실패 시 production service, `pawcycle-production-mysql-data`, release SHA와 HTTPS state를 변경하지 않는다. 부분 upload는 14일 lifecycle 대상이며 instance role에 삭제 권한을 추가하지 않는다.

## 9. 비민감 증거 형식

```text
OPS-013 backup: PASS|FAIL, backup ID recorded separately
S3 contract: Seoul region, expected owner, PAB 4/4, SSE-S3, 14-day prefix lifecycle PASS|FAIL
Upload verification: size, encryption, checksum, completion marker PASS|FAIL
Isolated restore: none network, no host port, temporary volume PASS|FAIL
Data verification: schema, Flyway history, core table counts MATCH|MISMATCH
Cleanup: temporary container, volume, work files ABSENT|PRESENT
Production preservation: release, HTTPS state, MySQL container and volume UNCHANGED|UNKNOWN
```

bucket·account·ARN·hostname·IP·email·credential, application SHA 값, object key, dump 원문, row와 실제 count는 기록하지 않는다.

## 10. 재부팅·application rollback과의 관계

OPS-013은 application rollback이나 production restore를 수행하지 않는다. 현재 OPS-012 rollback은 `previous-sha` 부재로 Deferred다. 실제 이전 SHA rollback을 백업 성공으로 대체하거나 완료로 기록하지 않는다.

## 에스컬레이션과 후속 작업

- 자동 schedule과 실패 알림
- certificate·DB backup 보존 정책 통합
- 실제 production restore 승인 절차
- cross-region·versioning·Glacier·별도 KMS가 필요한 규제 또는 RPO/RTO 결정
- backup·restore 실행 중 count 불일치가 반복될 때의 쓰기 정합성 전략

이 항목은 OPS-013 구현·운영 검증 완료로 간주하지 않는다.
