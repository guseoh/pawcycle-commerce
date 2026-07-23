from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_exact(path: str, old: str, new: str, count: int = 1) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    actual = text.count(old)
    if actual != count:
        raise RuntimeError(f"{path}: expected {count} matches, found {actual}")
    target.write_text(text.replace(old, new, count), encoding="utf-8")


def patch_backup_script() -> None:
    path = "infra/production/db-backup-restore.sh"
    replace_exact(
        path,
        'MAX_METADATA_OBJECT_BYTES=1048576\nCORE_TABLES=(members products skus subscriptions)',
        'MAX_METADATA_OBJECT_BYTES=1048576\nAPPROVED_AWS_REGION="ap-northeast-2"\nCORE_TABLES=(members products skus subscriptions)',
    )
    replace_exact(
        path,
        'S3_PREFIX="${PAWCYCLE_BACKUP_PREFIX:-}"\nBACKUP_ID=""',
        'S3_PREFIX="${PAWCYCLE_BACKUP_PREFIX:-}"\nEXPECTED_BUCKET_OWNER="${PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER:-}"\nBACKUP_ID=""',
    )
    replace_exact(
        path,
        "The bucket, region, and prefix may instead be supplied through\n"
        "PAWCYCLE_BACKUP_BUCKET, PAWCYCLE_BACKUP_REGION, and PAWCYCLE_BACKUP_PREFIX.\n"
        "AWS credentials must come from the EC2 instance role; this script never accepts them.",
        "The bucket, region, prefix, and expected bucket owner must be supplied through\n"
        "PAWCYCLE_BACKUP_BUCKET, PAWCYCLE_BACKUP_REGION, PAWCYCLE_BACKUP_PREFIX, and\n"
        "PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER. AWS credentials must come from the EC2\n"
        "instance role; this script never accepts them.",
    )
    replace_exact(
        path,
        'validate_region() {\n  [[ "$1" =~ ^[a-z]{2}(-gov)?-[a-z]+-[0-9]+$ ]] || die "AWS region format is invalid"\n}\n',
        'validate_region() {\n'
        '  [[ "$1" == "$APPROVED_AWS_REGION" ]] || die "AWS region must be the approved Seoul region"\n'
        '}\n\n'
        'validate_account_id() {\n'
        '  [[ "$1" =~ ^[0-9]{12}$ ]] || die "expected S3 bucket owner must be a 12-digit AWS account ID"\n'
        '}\n',
    )
    replace_exact(
        path,
        'validate_s3_inputs() {\n'
        '  [[ -n "$S3_BUCKET" && -n "$AWS_REGION" && -n "$S3_PREFIX" ]] \\\n'
        '    || die "bucket, region, and prefix are required"\n'
        '  validate_bucket "$S3_BUCKET"\n'
        '  validate_region "$AWS_REGION"\n'
        '  validate_prefix "$S3_PREFIX"\n'
        '}\n',
        'validate_s3_inputs() {\n'
        '  [[ -n "$S3_BUCKET" && -n "$AWS_REGION" && -n "$S3_PREFIX" && -n "$EXPECTED_BUCKET_OWNER" ]] \\\n'
        '    || die "bucket, region, prefix, and expected bucket owner are required"\n'
        '  validate_bucket "$S3_BUCKET"\n'
        '  validate_region "$AWS_REGION"\n'
        '  validate_prefix "$S3_PREFIX"\n'
        '  validate_account_id "$EXPECTED_BUCKET_OWNER"\n'
        '}\n',
    )
    replace_exact(
        path,
        'aws_capture() {\n'
        '  local output_file="$1"\n'
        '  shift\n\n'
        '  : > "$AWS_ERROR_FILE"\n'
        '  if ! aws "$@" >"$output_file" 2>"$AWS_ERROR_FILE"; then\n'
        '    : > "$AWS_ERROR_FILE"\n'
        '    die "AWS request failed; bucket and object identifiers were suppressed"\n'
        '  fi\n'
        '  : > "$AWS_ERROR_FILE"\n'
        '}\n',
        'aws_capture() {\n'
        '  local output_file="$1"\n'
        '  local -a arguments\n'
        '  shift\n'
        '  arguments=("$@")\n\n'
        '  if [[ "${arguments[0]:-}" == "s3api" ]]; then\n'
        '    (( ${#arguments[@]} >= 2 )) || die "invalid S3 API invocation"\n'
        '    arguments=("${arguments[0]}" "${arguments[1]}" --expected-bucket-owner "$EXPECTED_BUCKET_OWNER" "${arguments[@]:2}")\n'
        '  fi\n\n'
        '  : > "$AWS_ERROR_FILE"\n'
        '  if ! aws "${arguments[@]}" >"$output_file" 2>"$AWS_ERROR_FILE"; then\n'
        '    : > "$AWS_ERROR_FILE"\n'
        '    die "AWS request failed; bucket and object identifiers were suppressed"\n'
        '  fi\n'
        '  : > "$AWS_ERROR_FILE"\n'
        '}\n',
    )
    replace_exact(
        path,
        'write_source_manifest() {\n'
        '  local container="$1"\n'
        '  local manifest="$2"\n'
        '  local schema_file="$WORK_DIR/source-schema"\n'
        '  local flyway_file="$WORK_DIR/source-flyway"',
        'write_restore_manifest() {\n'
        '  local manifest="$1"\n'
        '  local schema_file="$WORK_DIR/snapshot-schema"\n'
        '  local flyway_file="$WORK_DIR/snapshot-flyway"',
    )
    replace_exact(path, '  local count_file="$WORK_DIR/source-count"', '  local count_file="$WORK_DIR/snapshot-count"')
    replace_exact(
        path,
        '  source_mysql_query "$container" "$(schema_query)" "$schema_file"\n'
        '  source_mysql_query "$container" "$(flyway_query)" "$flyway_file"',
        '  restore_mysql_query "$(schema_query)" "$schema_file"\n'
        '  restore_mysql_query "$(flyway_query)" "$flyway_file"',
    )
    replace_exact(
        path,
        '      source_mysql_query "$container" "SELECT COUNT(*) FROM \\`${table}\\`;" "$count_file"',
        '      restore_mysql_query "SELECT COUNT(*) FROM \\`${table}\\`;" "$count_file"',
    )
    replace_exact(
        path,
        'put_object() {\n'
        '  local key="$1"\n'
        '  local file="$2"\n'
        '  local response="$WORK_DIR/aws-response"\n'
        '  local size\n\n'
        '  size="$(stat -c \'%s\' "$file")"\n'
        '  (( size <= MAX_SINGLE_UPLOAD_BYTES )) \\\n'
        '    || die "backup object exceeds the approved single-request S3 upload limit"\n\n'
        '  aws_capture "$response" s3api put-object \\\n'
        '    --bucket "$S3_BUCKET" --region "$AWS_REGION" \\\n'
        '    --key "$key" --body "$file" \\\n'
        '    --storage-class STANDARD \\\n'
        '    --server-side-encryption AES256\n'
        '}\n',
        'object_size_limit() {\n'
        '  local key="$1"\n\n'
        '  if [[ "$key" == *.sql.gz ]]; then\n'
        '    printf \'%s\\n\' "$MAX_SINGLE_UPLOAD_BYTES"\n'
        '  else\n'
        '    printf \'%s\\n\' "$MAX_METADATA_OBJECT_BYTES"\n'
        '  fi\n'
        '}\n\n'
        'put_object() {\n'
        '  local key="$1"\n'
        '  local file="$2"\n'
        '  local response="$WORK_DIR/aws-response"\n'
        '  local size\n'
        '  local maximum_size\n\n'
        '  size="$(stat -c \'%s\' "$file")"\n'
        '  maximum_size="$(object_size_limit "$key")"\n'
        '  (( size <= maximum_size )) \\\n'
        '    || die "backup object exceeds its approved S3 upload size limit"\n\n'
        '  aws_capture "$response" s3api put-object \\\n'
        '    --bucket "$S3_BUCKET" --region "$AWS_REGION" \\\n'
        '    --key "$key" --body "$file" \\\n'
        '    --storage-class STANDARD \\\n'
        '    --server-side-encryption AES256\n'
        '}\n',
    )
    replace_exact(
        path,
        'head_object() {\n'
        '  local key="$1"\n'
        '  local expected_size="$2"\n'
        '  local actual_size\n\n'
        '  actual_size="$(head_object_size "$key" "$MAX_SINGLE_UPLOAD_BYTES")"\n'
        '  [[ "$actual_size" == "$expected_size" ]] || die "uploaded S3 object size does not match"\n'
        '}\n',
        'head_object() {\n'
        '  local key="$1"\n'
        '  local expected_size="$2"\n'
        '  local actual_size\n'
        '  local maximum_size\n\n'
        '  maximum_size="$(object_size_limit "$key")"\n'
        '  actual_size="$(head_object_size "$key" "$maximum_size")"\n'
        '  [[ "$actual_size" == "$expected_size" ]] || die "uploaded S3 object size does not match"\n'
        '}\n',
    )
    replace_exact(
        path,
        '  create_dump "$container" "$dump"\n'
        '  write_source_manifest "$container" "$manifest"\n'
        '  write_checksum "$dump"',
        '  create_dump "$container" "$dump"\n'
        '  check_restore_capacity\n'
        '  create_restore_mysql\n'
        '  import_dump\n'
        '  write_restore_manifest "$manifest"\n'
        '  assert_restore_isolation\n'
        '  write_checksum "$dump"',
    )
    for old, new in {
        "restored schema does not match the backup-time source schema": "restored schema does not match the backup snapshot manifest",
        "restored Flyway history does not match the backup-time source": "restored Flyway history does not match the backup snapshot manifest",
        "restored core table count does not match the backup-time source": "restored core table count does not match the backup snapshot manifest",
        "Schema, Flyway history, and core table counts match the backup-time source": "Schema, Flyway history, and core table counts match the backup snapshot manifest",
    }.items():
        replace_exact(path, old, new)


def patch_tests() -> None:
    path = "infra/production/test-db-backup-restore.sh"
    replace_exact(path, 'REGION="ap-northeast-2"\nPREFIX=', 'REGION="ap-northeast-2"\nEXPECTED_BUCKET_OWNER="123456789012"\nPREFIX=')
    replace_exact(
        path,
        '    FAKE_AWS_HEAD_SIZE="${FAKE_AWS_HEAD_SIZE:-}" \\\n'
        '    FAKE_GZIP_FAIL="${FAKE_GZIP_FAIL:-}"',
        '    FAKE_AWS_HEAD_SIZE="${FAKE_AWS_HEAD_SIZE:-}" \\\n'
        '    FAKE_AWS_EXPECTED_BUCKET_OWNER="$EXPECTED_BUCKET_OWNER" \\\n'
        '    FAKE_AFTER_COMPRESS_CONTAINER="${FAKE_AFTER_COMPRESS_CONTAINER:-}" \\\n'
        '    FAKE_AFTER_COMPRESS_MARKER="${FAKE_AFTER_COMPRESS_MARKER:-}" \\\n'
        '    FAKE_GZIP_FAIL="${FAKE_GZIP_FAIL:-}"',
    )
    replace_exact(
        path,
        '    PAWCYCLE_BACKUP_PREFIX="$PREFIX" \\\n'
        '    "$SCRIPT" "$@"',
        '    PAWCYCLE_BACKUP_PREFIX="$PREFIX" \\\n'
        '    PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER="${PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER:-$EXPECTED_BUCKET_OWNER}" \\\n'
        '    "$SCRIPT" "$@"',
    )
    replace_exact(
        path,
        'if [[ "${FAKE_AWS_FAIL_OPERATION:-}" == "$operation" ]]; then\n'
        '  printf \'simulated AWS failure\\n\' >&2\n'
        '  exit 1\n'
        'fi\n\n'
        'case "$operation" in',
        'if [[ "${FAKE_AWS_FAIL_OPERATION:-}" == "$operation" ]]; then\n'
        '  printf \'simulated AWS failure\\n\' >&2\n'
        '  exit 1\n'
        'fi\n\n'
        'owner="$(argument --expected-bucket-owner "$@" || true)"\n'
        'if [[ "$owner" != "${FAKE_AWS_EXPECTED_BUCKET_OWNER:-}" ]]; then\n'
        '  printf \'unexpected bucket owner\\n\' >&2\n'
        '  exit 1\n'
        'fi\n\n'
        'case "$operation" in',
    )
    replace_exact(
        path,
        'done\nexec "$REAL_GZIP" "\\$@"\nEOF',
        'done\n'
        'if [[ "\\$#" == "0" && -n "\\${FAKE_AFTER_COMPRESS_CONTAINER:-}" && -n "\\${FAKE_AFTER_COMPRESS_MARKER:-}" && ! -e "\\$FAKE_AFTER_COMPRESS_MARKER" ]]; then\n'
        '  set +e\n'
        '  "$REAL_GZIP"\n'
        '  status=\\$?\n'
        '  set -e\n'
        '  if (( status == 0 )); then\n'
        '    docker exec "\\$FAKE_AFTER_COMPRESS_CONTAINER" sh -eu -c \'export MYSQL_PWD="\\$(cat /run/secrets/mysql-root-password)"; exec mysql --protocol=SOCKET --user=root "\\$MYSQL_DATABASE" --execute="INSERT INTO members VALUES (2);"\' >/dev/null 2>&1\n'
        '    : >"\\$FAKE_AFTER_COMPRESS_MARKER"\n'
        '  fi\n'
        '  exit "\\$status"\n'
        'fi\n'
        'exec "$REAL_GZIP" "\\$@"\nEOF',
    )
    replace_exact(
        path,
        'backup_output="$(run_ops013 backup --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX")"',
        'if run_ops013 backup --bucket "$BUCKET" --region us-west-2 --prefix "$PREFIX" >/dev/null 2>&1; then\n'
        '  fail "non-Seoul backup region was reported as success"\n'
        'fi\n'
        'if PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER=210987654321 run_ops013 backup --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" >/dev/null 2>&1; then\n'
        '  fail "unexpected S3 bucket owner was reported as success"\n'
        'fi\n\n'
        'backup_output="$(FAKE_AFTER_COMPRESS_CONTAINER="$SOURCE_CONTAINER" FAKE_AFTER_COMPRESS_MARKER="$TEMP_DIR/after-dump-write" run_ops013 backup --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX")"',
    )
    replace_exact(
        path,
        'test "$(mysql --protocol=SOCKET --user=root --batch --skip-column-names "$MYSQL_DATABASE" --execute="SELECT COUNT(*) FROM members;")" = "1"',
        'test "$(mysql --protocol=SOCKET --user=root --batch --skip-column-names "$MYSQL_DATABASE" --execute="SELECT COUNT(*) FROM members;")" = "2"',
    )


def patch_validator() -> None:
    path = "infra/production/validate-production-contracts.py"
    replace_exact(
        path,
        '    require("verify_challenge_path\\n  approve_https_domain" not in https, "local bootstrap challenge validation must not approve the domain")\n',
        '    require("verify_challenge_path\\n  approve_https_domain" not in https, "local bootstrap challenge validation must not approve the domain")\n'
        '    require(\n'
        '        \'validate_https_certificate "$HTTPS_DOMAIN"\\n    approve_https_domain\' in https,\n'
        '        "HTTPS domain approval must happen only after certificate validation",\n'
        '    )\n',
    )
    replace_exact(
        path,
        '    require(\n'
        '        backup_restore.index(\'upload_and_verify "${base_key}.complete"\') > backup_restore.index(\'upload_and_verify "${base_key}.verify.sha256"\'),\n'
        '        "S3 completion marker must be uploaded after the verified backup object set",\n'
        '    )',
        '    require(\n'
        '        \'upload_and_verify "${base_key}.complete"\' in backup_restore\n'
        '        and \'upload_and_verify "${base_key}.verify.sha256"\' in backup_restore\n'
        '        and backup_restore.index(\'upload_and_verify "${base_key}.complete"\')\n'
        '        > backup_restore.index(\'upload_and_verify "${base_key}.verify.sha256"\'),\n'
        '        "S3 completion marker must be uploaded after the verified backup object set",\n'
        '    )',
    )
    replace_exact(
        path,
        '    require(\n'
        '        backup_restore.index(\'production MySQL changed during backup verification\')\n'
        '        < backup_restore.index(\'upload_and_verify "${base_key}.complete"\'),\n'
        '        "production MySQL identity and health must be rechecked before publishing the completion marker",\n'
        '    )',
        '    require(\n'
        '        \'production MySQL changed during backup verification\' in backup_restore\n'
        '        and \'upload_and_verify "${base_key}.complete"\' in backup_restore\n'
        '        and backup_restore.index(\'production MySQL changed during backup verification\')\n'
        '        < backup_restore.index(\'upload_and_verify "${base_key}.complete"\'),\n'
        '        "production MySQL identity and health must be rechecked before publishing the completion marker",\n'
        '    )',
    )
    replace_exact(
        path,
        '        "MAX_SINGLE_UPLOAD_BYTES=5000000000" in backup_restore\n'
        '        and "backup object exceeds the approved single-request S3 upload limit" in backup_restore,',
        '        "MAX_SINGLE_UPLOAD_BYTES=5000000000" in backup_restore\n'
        '        and "MAX_METADATA_OBJECT_BYTES=1048576" in backup_restore\n'
        '        and "object_size_limit" in backup_restore\n'
        '        and "backup object exceeds its approved S3 upload size limit" in backup_restore,',
    )
    replace_exact(
        path,
        '        "head_object_size" in backup_restore\n'
        '        and "S3 object exceeds the approved download size limit" in backup_restore\n'
        '        and backup_restore.index(\'complete_size="$(head_object_size\') < backup_restore.index(\'get_object "${base_key}.complete"\'),',
        '        "head_object_size" in backup_restore\n'
        '        and "S3 object exceeds the approved download size limit" in backup_restore\n'
        '        and \'complete_size="$(head_object_size\' in backup_restore\n'
        '        and \'get_object "${base_key}.complete"\' in backup_restore\n'
        '        and backup_restore.index(\'complete_size="$(head_object_size\')\n'
        '        < backup_restore.index(\'get_object "${base_key}.complete"\'),',
    )
    anchor = '    require("get-bucket-versioning" in backup_restore, "bucket versioning must be rejected by the retention preflight")\n'
    replace_exact(
        path,
        anchor,
        anchor
        + '    require(\n'
        + '        \'APPROVED_AWS_REGION="ap-northeast-2"\' in backup_restore\n'
        + '        and \'[[ "$1" == "$APPROVED_AWS_REGION" ]]\' in backup_restore,\n'
        + '        "backup execution must fail closed outside the approved Seoul region",\n'
        + '    )\n'
        + '    require(\n'
        + '        "PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" in backup_restore\n'
        + '        and "--expected-bucket-owner" in backup_restore\n'
        + '        and "12-digit AWS account ID" in backup_restore,\n'
        + '        "every S3 API request must bind to the expected bucket owner",\n'
        + '    )\n'
        + '    require(\n'
        + '        "write_restore_manifest" in backup_restore\n'
        + '        and "write_source_manifest" not in backup_restore\n'
        + '        and \'create_restore_mysql\\n  import_dump\\n  write_restore_manifest "$manifest"\' in backup_restore,\n'
        + '        "backup metadata must be generated from the isolated import of the dump snapshot",\n'
        + '    )\n',
    )
    replace_exact(
        path,
        '        "--preserve-env=PAWCYCLE_BACKUP_BUCKET,PAWCYCLE_BACKUP_REGION,PAWCYCLE_BACKUP_PREFIX" in runbook',
        '        "--preserve-env=PAWCYCLE_BACKUP_BUCKET,PAWCYCLE_BACKUP_REGION,PAWCYCLE_BACKUP_PREFIX,PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" in runbook',
    )
    replace_exact(
        path,
        '        "source production volume was removed",',
        '        "source production volume was removed",\n'
        '        "non-Seoul backup region was reported as success",\n'
        '        "unexpected S3 bucket owner was reported as success",',
    )


def patch_runbook() -> None:
    path = "docs/runbook/OPS-013-production-db-backup-restore.md"
    replace_exact(
        path,
        '운영 MySQL application DB를 중지하거나 변경하지 않고 압축 논리 dump를 비공개 S3에 저장한다. 같은 EC2·EBS에서 production network와 분리된 임시 MySQL·named volume에 복원해 schema, Flyway history와 핵심 table count가 백업 시점 원본과 일치하는지 확인한다.',
        '운영 MySQL application DB를 중지하거나 변경하지 않고 압축 논리 dump를 비공개 S3에 저장한다. dump를 production network와 분리된 임시 MySQL·named volume에 먼저 복원해 같은 snapshot에서 schema, Flyway history와 핵심 table count manifest를 만들고, 이후 독립 복원 검증에서도 일치하는지 확인한다.',
    )
    replace_exact(
        path,
        '- S3 bucket: OPS-013 전용으로 새로 생성한 빈 private bucket\n',
        '- AWS region: 승인된 서울 region `ap-northeast-2`\n'
        '- S3 bucket: OPS-013 전용으로 새로 생성한 빈 private bucket과 예상 bucket owner 일치\n',
    )
    replace_exact(
        path,
        '- AWS region이 현재 EC2와 bucket에서 일치하는지 확인할 수 없음\n',
        '- AWS region이 승인된 서울 region `ap-northeast-2`가 아니거나 EC2와 bucket에서 일치하는지 확인할 수 없음\n'
        '- S3 bucket owner가 현재 승인 AWS account와 일치하는지 확인할 수 없음\n',
    )
    replace_exact(
        path,
        "read -r -s -p 'Backup bucket: ' BACKUP_BUCKET\n"
        "printf '\\n'\n"
        "read -r -s -p 'AWS region: ' BACKUP_REGION\n"
        "printf '\\n'\n"
        "read -r -s -p 'Backup prefix: ' BACKUP_PREFIX\n"
        "printf '\\n'\n\n"
        '[[ "$BACKUP_BUCKET" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]]\n'
        '[[ "$BACKUP_REGION" =~ ^[a-z]{2}(-gov)?-[a-z]+-[0-9]+$ ]]\n'
        '[[ "$BACKUP_PREFIX" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]*[A-Za-z0-9]$ ]]\n\n'
        'export PAWCYCLE_BACKUP_BUCKET="$BACKUP_BUCKET"\n'
        'export PAWCYCLE_BACKUP_REGION="$BACKUP_REGION"\n'
        'export PAWCYCLE_BACKUP_PREFIX="$BACKUP_PREFIX"\n'
        'unset BACKUP_BUCKET BACKUP_REGION BACKUP_PREFIX',
        "read -r -s -p 'Backup bucket: ' BACKUP_BUCKET\n"
        "printf '\\n'\n"
        "read -r -s -p 'Backup prefix: ' BACKUP_PREFIX\n"
        "printf '\\n'\n\n"
        '[[ "$BACKUP_BUCKET" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]]\n'
        '[[ "$BACKUP_PREFIX" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]*[A-Za-z0-9]$ ]]\n\n'
        'export PAWCYCLE_BACKUP_BUCKET="$BACKUP_BUCKET"\n'
        'export PAWCYCLE_BACKUP_REGION="ap-northeast-2"\n'
        'export PAWCYCLE_BACKUP_PREFIX="$BACKUP_PREFIX"\n'
        'export PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER="$(aws sts get-caller-identity --query Account --output text)"\n'
        '[[ "$PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" =~ ^[0-9]{12}$ ]]\n'
        'unset BACKUP_BUCKET BACKUP_PREFIX',
    )
    replace_exact(
        path,
        '`PAWCYCLE_BACKUP_REGION`은 EC2와 bucket이 함께 위치한 승인된 서울 region이어야 한다.',
        '`PAWCYCLE_BACKUP_REGION`은 승인된 서울 region `ap-northeast-2`로 고정한다. `PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER`는 현재 승인 AWS account에서 자동 조회하며 값을 문서·로그에 복사하지 않는다.',
    )
    for operation in (
        "get-bucket-location",
        "get-public-access-block",
        "get-bucket-encryption",
        "get-bucket-versioning",
        "get-bucket-lifecycle-configuration",
    ):
        old = (
            f"aws s3api {operation} \\\n"
            '  --bucket "$PAWCYCLE_BACKUP_BUCKET" --region "$PAWCYCLE_BACKUP_REGION" \\\n'
        )
        new = old + '  --expected-bucket-owner "$PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" \\\n'
        replace_exact(path, old, new)
    replace_exact(
        path,
        'sudo --preserve-env=PAWCYCLE_BACKUP_BUCKET,PAWCYCLE_BACKUP_REGION,PAWCYCLE_BACKUP_PREFIX \\\n',
        'sudo --preserve-env=PAWCYCLE_BACKUP_BUCKET,PAWCYCLE_BACKUP_REGION,PAWCYCLE_BACKUP_PREFIX,PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER \\\n',
        count=2,
    )
    replace_exact(
        path,
        '5. schema·Flyway fingerprint와 핵심 table count manifest 생성\n'
        '6. dump·manifest와 각각의 SHA-256 checksum 업로드',
        '5. dump를 `none` network 임시 MySQL에 import하고 같은 snapshot의 schema·Flyway·핵심 table count manifest 생성\n'
        '6. metadata object는 1 MiB, dump object는 5,000,000,000 byte 한도를 확인한 뒤 dump·manifest와 checksum 업로드',
    )
    replace_exact(
        path,
        '복원 뒤 schema fingerprint, Flyway fingerprint·history count와 네 핵심 table count를 backup-time manifest와 비교한다.',
        '복원 뒤 schema fingerprint, Flyway fingerprint·history count와 네 핵심 table count를 dump snapshot에서 생성한 manifest와 비교한다.',
    )
    replace_exact(
        path,
        '| schema·Flyway·table count 불일치 | 복원 검증 실패 | 성공으로 기록하지 말고 DDL·동시 쓰기·dump 손상 조사 |',
        '| schema·Flyway·table count 불일치 | 복원 검증 실패 | 성공으로 기록하지 말고 DDL·dump 또는 manifest 손상 조사 |',
    )
    replace_exact(
        path,
        'S3 contract: region match, PAB 4/4, SSE-S3, 14-day prefix lifecycle PASS|FAIL',
        'S3 contract: Seoul region, expected owner, PAB 4/4, SSE-S3, 14-day prefix lifecycle PASS|FAIL',
    )


def patch_docs() -> None:
    report = "docs/reports/OPS-013/sre-report.md"
    replace_exact(
        report,
        '- 일관된 압축 논리 dump, schema·Flyway·핵심 table count manifest와 SHA-256',
        '- 일관된 압축 논리 dump를 격리 import해 같은 snapshot에서 생성한 schema·Flyway·핵심 table count manifest와 SHA-256',
    )
    replace_exact(
        report,
        '- bucket region, Public Access Block 네 항목, SSE-S3, versioning 비활성과 지정 prefix 14일 lifecycle을 upload 전에 검사한다.',
        '- 승인 서울 region과 expected bucket owner, Public Access Block 네 항목, SSE-S3, versioning 비활성과 지정 prefix 14일 lifecycle을 upload 전에 검사한다.\n'
        '- dump를 임시 MySQL에 먼저 import해 manifest를 생성하므로 dump 이후 production row 쓰기가 backup snapshot 정합성을 깨뜨리지 않는다.\n'
        '- dump 이외 metadata object는 upload 전부터 1 MiB로 제한한다.',
    )
    replace_exact(
        report,
        '- dump 뒤 생성한 count manifest와 restore 결과가 동시 row 쓰기로 다르면 안전하게 실패하며 운영 저부하 시점 재시도가 필요하다.',
        '- dump snapshot manifest를 만들기 위한 backup-time isolated import가 같은 EC2의 CPU·memory·disk I/O를 추가 사용하므로 저부하 시점에 실행한다.',
    )
    replace_exact(
        report,
        '이후 리뷰에서 test-owned volume cleanup, S3 lifecycle·policy 교체 경계, 환경변수 전달, decimal 5 GB, 실제 압축 해제 크기, download 전 object preflight, completion marker 순서, ambient AWS 설정과 checksum target 검증을 추가 보완했다.',
        '이후 리뷰에서 test-owned volume cleanup, S3 lifecycle·policy 교체 경계, 환경변수 전달, decimal 5 GB, 실제 압축 해제 크기, download 전 object preflight, completion marker 순서, ambient AWS 설정과 checksum target 검증을 추가 보완했다. 마지막으로 dump와 live manifest의 snapshot 불일치, 서울 region·expected bucket owner, metadata 1 MiB upload 한도와 HTTPS 승인 순서 회귀 검증을 보완했다.',
    )

    handoff = "docs/handoffs/OPS-013/sre-to-tl.md"
    replace_exact(
        handoff,
        '- 압축 consistent logical dump, schema·Flyway·핵심 table count manifest와 SHA-256',
        '- 압축 consistent logical dump를 격리 import해 같은 snapshot에서 생성한 schema·Flyway·핵심 table count manifest와 SHA-256',
    )
    replace_exact(
        handoff,
        '- 같은 서울 region에 새로 생성한 OPS-013 전용 빈 private S3 bucket\n',
        '- 승인 서울 region `ap-northeast-2`에 새로 생성한 OPS-013 전용 빈 private S3 bucket과 expected owner\n',
    )
    replace_exact(
        handoff,
        '- bucket region·PAB·SSE-S3·versioning 비활성·14일 lifecycle 또는 최소 role 권한 불명확',
        '- bucket이 승인 서울 region·expected owner와 일치하지 않거나 PAB·SSE-S3·versioning 비활성·14일 lifecycle 또는 최소 role 권한이 불명확',
    )
    replace_exact(
        handoff,
        '- schema·Flyway history·핵심 table count가 backup-time manifest와 일치하는가?',
        '- schema·Flyway history·핵심 table count가 dump snapshot에서 생성한 manifest와 일치하는가?',
    )
    replace_exact(
        handoff,
        '- dump 중 DDL은 금지되며 동시 row 쓰기는 count mismatch를 일으켜 안전 실패할 수 있다.',
        '- dump 중 DDL은 금지되며 backup-time snapshot manifest 생성을 위한 isolated import가 추가 자원을 사용한다.',
    )


def main() -> None:
    patch_backup_script()
    patch_tests()
    patch_validator()
    patch_runbook()
    patch_docs()
    print("OPS-013 follow-up patch v2 applied")


if __name__ == "__main__":
    main()
