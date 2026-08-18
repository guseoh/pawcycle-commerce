#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(mktemp -d)"; trap 'rm -rf -- "$ROOT"' EXIT
BIN="$ROOT/bin"; STATE="$ROOT/state"; RDS="$ROOT/rds"; DOCKER="$ROOT/docker"; EVIDENCE="$ROOT/rds-target-verified"; mkdir -p "$BIN" "$STATE" "$RDS/current" "$DOCKER/current"
SHA=1111111111111111111111111111111111111111; HASH=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa; DB_HASH="$(printf '%s' pawcycle | sha256sum | awk '{print $1}')"; IMAGE='mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d'
cat >"$BIN/aws" <<'EOF'
#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >>"$FAKE_AWS_LOG"
fail(){ printf '%s\n' "$*" >>"$FAKE_AWS_ERROR"; exit 96; }
common(){ [[ " $* " == *" --region ap-northeast-2 "* && " $* " == *" --output text "* ]] || fail "invalid common AWS arguments: $*"; }
case "$1:$2" in
 ec2:describe-instances) common "$@"; [[ " $* " == *" --instance-ids i-1234abcd "* ]] || fail 'invalid instance ID'; case "$*" in *Placement.AvailabilityZone*) printf 'ap-northeast-2d\n';; *VpcId*) printf 'vpc-1234abcd\n';; *State.Name*) printf 'running\n';; *SecurityGroups*) printf 'sg-deadbeef\tsg-1234abcd\n';; *) fail 'unexpected instance scalar query';; esac;;
 ec2:describe-vpcs) common "$@"; [[ " $* " == *" --vpc-ids vpc-1234abcd "* && "$*" == *'Vpcs[0].State'* ]] || fail 'invalid VPC query'; printf 'available\n';;
 ec2:describe-subnets) common "$@"; [[ " $* " == *" --subnet-ids subnet-1234abcd subnet-abcd1234 "* && "$*" == *'Subnets[].['* ]] || fail 'invalid subnet query'; case "${FAKE_AWS_SUBNET_MODE:-ok}" in one) printf 'subnet-1234abcd\tvpc-1234abcd\tap-northeast-2d\tavailable\t20\nsubnet-abcd1234\tvpc-1234abcd\tap-northeast-2d\tavailable\t20\n';; unavailable) printf 'subnet-1234abcd\tvpc-1234abcd\tap-northeast-2d\tavailable\t20\nsubnet-abcd1234\tvpc-1234abcd\tap-northeast-2c\tpending\t20\n';; *) printf 'subnet-1234abcd\tvpc-1234abcd\tap-northeast-2d\tavailable\t20\nsubnet-abcd1234\tvpc-1234abcd\tap-northeast-2c\tavailable\t20\n';; esac;;
 rds:describe-orderable-db-instance-options) common "$@"; [[ " $* " == *" --engine mysql "* && " $* " == *" --db-instance-class db.t4g.micro "* && " $* " == *" --vpc "* && "$*" == *MinStorageSize* && "$*" == *MaxStorageSize* ]] || fail 'invalid RDS orderability query'; [[ "${FAKE_AWS_ORDERABLE:-ok}" == ok ]] && printf 'mysql\t8.4.1\tdb.t4g.micro\tgp3\tTrue\tTrue\t20\t65536\n' || printf 'mysql\t8.3.1\tdb.t4g.micro\tgp2\tTrue\tTrue\t20\t65536\n';;
 ec2:describe-security-groups) common "$@"; [[ " $* " == *" --group-ids sg-abcd1234 "* ]] || fail 'invalid RDS SG ID'; case "$*" in *'SecurityGroups[0].VpcId'*) printf 'vpc-1234abcd\n';; *'length(SecurityGroups[0].IpPermissions)'*) printf '1\n';; *'.IpProtocol'*) printf 'tcp\n';; *'.FromPort'*) printf '3306\n';; *'.ToPort'*) printf '3306\n';; *'length(SecurityGroups[0].IpPermissions[0].UserIdGroupPairs)'*) printf '1\n';; *'UserIdGroupPairs[0].GroupId'*) printf 'sg-1234abcd\n';; *'length(SecurityGroups[0].IpPermissions[0].IpRanges)'*) [[ "${FAKE_AWS_SG:-ok}" == ok ]] && printf '0\n' || printf '1\n';; *'Ipv6Ranges)'*|*'PrefixListIds)'*) printf '0\n';; *) fail 'unexpected security group scalar query';; esac;;
 *) printf 'MUTATION:%s\n' "$*" >>"$FAKE_AWS_MUTATION"; exit 95;;
esac
EOF
cat >"$BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -eu
case "$1" in
 volume) [[ "$2" == inspect && "${FAKE_DOCKER_VOLUME_MISSING:-false}" == false ]] && exit 0; exit 1;;
 ps) printf 'mysql-fixture\n';;
 inspect) case "$*" in *'.State.Health'*) printf '%s\n' "${FAKE_DOCKER_HEALTH:-healthy}";; *'.Config.Image'*) printf '%s\n' "${FAKE_DOCKER_IMAGE:-$FAKE_DOCKER_DEFAULT_IMAGE}";; *'.Mounts'*) printf '%s\n' "${FAKE_DOCKER_MOUNT:-pawcycle-production-mysql-data}";; esac;;
 *) exit 90;;
esac
EOF
cat >"$BIN/stat" <<'EOF'
#!/usr/bin/env bash
if [[ "$1" == -c && "$2" == %a ]]; then printf '600\n'; else exec /usr/bin/stat "$@"; fi
EOF
cat >"$BIN/git" <<EOF
#!/usr/bin/env bash
set -eu
args="\$*"
if [[ "\$args" == *status* ]]; then [[ "\${FAKE_GIT_DIRTY:-false}" == false ]] || printf ' M control\n'; exit 0; fi
if [[ "\$args" == *rev-parse* ]]; then printf '$SHA\n'; exit 0; fi
exit 0
EOF
cat >"$BIN/flock" <<'EOF'
#!/usr/bin/env bash
[[ "${FAKE_LOCK_BUSY:-false}" == false ]] || exit 1
if [[ "$*" == *--shared* ]]; then
  [[ "${FAKE_RUNTIME_LOCK_BUSY:-false}" == false ]] || exit 1
  key="$PPID:${!#}"
  grep -Fxq "$key" "$FAKE_FLOCK_LOG" && exit 91
  printf '%s\n' "$key" >>"$FAKE_FLOCK_LOG"
fi
exit 0
EOF
chmod +x "$BIN/aws" "$BIN/docker" "$BIN/stat" "$BIN/git" "$BIN/flock"; export PATH="$BIN:$PATH" FAKE_AWS_LOG="$ROOT/aws.log" FAKE_AWS_MUTATION="$ROOT/aws-mutation.log" FAKE_AWS_ERROR="$ROOT/aws-error.log" FAKE_FLOCK_LOG="$ROOT/flock.log" FAKE_DOCKER_DEFAULT_IMAGE="$IMAGE"; : >"$FAKE_AWS_MUTATION"; : >"$FAKE_AWS_ERROR"; : >"$FAKE_FLOCK_LOG"
write(){ local f=$1; shift; printf '%s\n' "$@" >"$f"; chmod 600 "$f"; }
write "$RDS/.materialize.lock" lock
write "$DOCKER/.materialize.lock" lock
write "$STATE/deploy.lock" lock
write "$STATE/active-mysql-volume" pawcycle-production-mysql-data
write "$STATE/current-sha" "$SHA"
write "$STATE/contract-sha" "$SHA"
write "$STATE/db-restore-verified" "FORMAT_VERSION=1" "RECORD_KIND=verified" "BACKUP_ID_SHA256=$HASH" "MANIFEST_SHA256=$HASH" "MYSQL_IMAGE=$IMAGE"
write "$STATE/db-restore-candidate" "FORMAT_VERSION=1" "RECORD_KIND=candidate" "BACKUP_ID_SHA256=$HASH" "MANIFEST_SHA256=$HASH" "MYSQL_IMAGE=$IMAGE" "SOURCE_VOLUME=pawcycle-production-mysql-data" "CANDIDATE_VOLUME=pawcycle-production-mysql-candidate-1234567890abcdef" "SCHEMA_SHA256=$HASH" "FLYWAY_SHA256=$HASH" "FLYWAY_COUNT=1" "TABLE_members=2" "TABLE_products=2" "TABLE_skus=2" "TABLE_subscriptions=2"
write "$RDS/current/backend.env" "PAWCYCLE_DATASOURCE_HOST='pawcycle-db.ap-northeast-2.rds.amazonaws.com'" "PAWCYCLE_DATASOURCE_PORT='3306'" "PAWCYCLE_DATASOURCE_SSL_MODE='REQUIRED'" "SPRING_DATASOURCE_URL='jdbc:mysql://pawcycle-db.ap-northeast-2.rds.amazonaws.com:3306/pawcycle?sslMode=REQUIRED&serverTimezone=UTC'" "SPRING_DATASOURCE_USERNAME='pawcycle_rds'" "SPRING_DATASOURCE_PASSWORD='rds-fixture-secret'" "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'" "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='7'" "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='12345'"
write "$DOCKER/current/backend.env" "PAWCYCLE_DATASOURCE_HOST='mysql'" "PAWCYCLE_DATASOURCE_PORT='3306'" "PAWCYCLE_DATASOURCE_SSL_MODE='DISABLED'" "SPRING_DATASOURCE_URL='jdbc:mysql://mysql:3306/pawcycle?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC'" "SPRING_DATASOURCE_USERNAME='pawcycle'" "SPRING_DATASOURCE_PASSWORD='fixture-secret'" "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'" "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='7'" "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='12345'"
write "$RDS/current/mysql.env" "MYSQL_DATABASE='pawcycle'" "MYSQL_USER='pawcycle_rds'" "MYSQL_PASSWORD='rds-fixture-secret'" "MYSQL_ROOT_PASSWORD='rds-fixture-root'"
write "$DOCKER/current/mysql.env" "MYSQL_DATABASE='pawcycle'" "MYSQL_USER='pawcycle'" "MYSQL_PASSWORD='fixture-secret'" "MYSQL_ROOT_PASSWORD='fixture-root'"
write "$RDS/current/.complete" complete
write "$DOCKER/current/.complete" complete
write "$EVIDENCE" "FORMAT_VERSION=1" "RECORD_KIND=rds-target-verified" "EVIDENCE_PHASE=REHEARSAL" "FINAL_CONSISTENCY_VERIFIED=false" "BACKUP_ID_SHA256=$HASH" "MANIFEST_SHA256=$HASH" "TARGET_HOST=pawcycle-db.ap-northeast-2.rds.amazonaws.com" "TARGET_PORT=3306" "TARGET_DATABASE_SHA256=$DB_HASH" "SCHEMA_SHA256=$HASH" "FLYWAY_SHA256=$HASH" "FLYWAY_COUNT=1" "TABLE_members=2" "TABLE_products=2" "TABLE_skus=2" "TABLE_subscriptions=2" "APPLICATION_SHA=$SHA" "CONTROL_SHA=$SHA" "CONTRACT_SHA=$SHA" "CONNECTIVITY_VERIFIED=true" "IMPORT_VERIFIED=true" "BACKEND_HEALTH_VERIFIED=true" "API_SMOKE_VERIFIED=true" "SOURCE_TARGET_DISTINCT=true" "PRODUCTION_CUTOVER=false"
base_preflight(){ bash "$SCRIPT_DIR/rds-read-only-preflight.sh" --ec2-instance-id i-1234abcd --vpc-id vpc-1234abcd --subnet-id subnet-1234abcd --subnet-id subnet-abcd1234 --ec2-security-group-id sg-1234abcd "$@"; }
full_preflight(){ base_preflight --rds-security-group-id sg-abcd1234 "$@"; }
base_preflight >/dev/null
full_preflight >/dev/null
gate(){ bash "$SCRIPT_DIR/rds-transition-gate.sh" "$@" --state-dir "$STATE" --application-sha "$SHA" --control-sha "$SHA"; }
expect_fail(){ if "$@" >/dev/null 2>&1; then printf 'expected fail-closed path was reported as success\n' >&2; exit 1; fi; }
gate rehearsal --evidence "$EVIDENCE" --rds-runtime-dir "$RDS" --rollback-runtime-dir "$DOCKER" >/dev/null
expect_fail gate cutover --evidence "$EVIDENCE" --rds-runtime-dir "$RDS" --rollback-runtime-dir "$DOCKER"
sed -i 's/EVIDENCE_PHASE=REHEARSAL/EVIDENCE_PHASE=CUTOVER/; s/FINAL_CONSISTENCY_VERIFIED=false/FINAL_CONSISTENCY_VERIFIED=true/' "$EVIDENCE"
expect_fail gate rehearsal --evidence "$EVIDENCE" --rds-runtime-dir "$RDS" --rollback-runtime-dir "$DOCKER"
gate cutover --evidence "$EVIDENCE" --rds-runtime-dir "$RDS" --rollback-runtime-dir "$DOCKER" >/dev/null
gate rollback --rollback-runtime-dir "$DOCKER" >/dev/null
expect_fail bash "$SCRIPT_DIR/rds-read-only-preflight.sh" --region us-east-1 --ec2-instance-id i-1234abcd --vpc-id vpc-1234abcd --subnet-id subnet-1234abcd --subnet-id subnet-abcd1234 --ec2-security-group-id sg-1234abcd --rds-security-group-id sg-abcd1234
expect_fail bash "$SCRIPT_DIR/rds-read-only-preflight.sh" --ec2-instance-id i-1234abcd --vpc-id vpc-1234abcd --subnet-id subnet-1234abcd --subnet-id subnet-1234abcd --ec2-security-group-id sg-1234abcd --rds-security-group-id sg-abcd1234
export FAKE_AWS_SUBNET_MODE=one; expect_fail full_preflight; unset FAKE_AWS_SUBNET_MODE
export FAKE_AWS_SUBNET_MODE=unavailable; expect_fail full_preflight; unset FAKE_AWS_SUBNET_MODE
export FAKE_AWS_ORDERABLE=missing; expect_fail full_preflight; unset FAKE_AWS_ORDERABLE
export FAKE_AWS_SG=cidr; expect_fail full_preflight; unset FAKE_AWS_SG
sed -i 's/EVIDENCE_PHASE=CUTOVER/EVIDENCE_PHASE=REHEARSAL/; s/FINAL_CONSISTENCY_VERIFIED=true/FINAL_CONSISTENCY_VERIFIED=false/; s/PRODUCTION_CUTOVER=false/PRODUCTION_CUTOVER=true/' "$EVIDENCE"
if gate rehearsal --evidence "$EVIDENCE" --rds-runtime-dir "$RDS" --rollback-runtime-dir "$DOCKER" >/dev/null 2>&1; then printf 'cutover claim evidence did not fail closed\n' >&2; exit 1; fi
sed -i 's/PRODUCTION_CUTOVER=true/PRODUCTION_CUTOVER=false/; s/EVIDENCE_PHASE=REHEARSAL/EVIDENCE_PHASE=CUTOVER/; s/FINAL_CONSISTENCY_VERIFIED=false/FINAL_CONSISTENCY_VERIFIED=true/' "$EVIDENCE"
sed -i 's/TARGET_HOST=pawcycle-db/TARGET_HOST=wrong-db/' "$EVIDENCE"
expect_fail gate cutover --evidence "$EVIDENCE" --rds-runtime-dir "$RDS" --rollback-runtime-dir "$DOCKER"
sed -i 's/TARGET_HOST=wrong-db/TARGET_HOST=pawcycle-db/' "$EVIDENCE"
sed -i 's/TARGET_DATABASE_SHA256=.*/TARGET_DATABASE_SHA256=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/' "$EVIDENCE"
expect_fail gate cutover --evidence "$EVIDENCE" --rds-runtime-dir "$RDS" --rollback-runtime-dir "$DOCKER"
sed -i "s/TARGET_DATABASE_SHA256=.*/TARGET_DATABASE_SHA256=$DB_HASH/" "$EVIDENCE"
printf 'UNKNOWN_SECRET=fixture\n' >>"$EVIDENCE"
expect_fail gate cutover --evidence "$EVIDENCE" --rds-runtime-dir "$RDS" --rollback-runtime-dir "$DOCKER"
sed -i '$d' "$EVIDENCE"
printf 'TABLE_members=2\n' >>"$EVIDENCE"
expect_fail gate cutover --evidence "$EVIDENCE" --rds-runtime-dir "$RDS" --rollback-runtime-dir "$DOCKER"
sed -i '$d' "$EVIDENCE"
sed -i 's/BACKUP_ID_SHA256=aaaaaaaa/BACKUP_ID_SHA256=bbbbbbbb/' "$STATE/db-restore-verified"
expect_fail gate rollback --rollback-runtime-dir "$DOCKER"
sed -i 's/BACKUP_ID_SHA256=bbbbbbbb/BACKUP_ID_SHA256=aaaaaaaa/' "$STATE/db-restore-verified"
export FAKE_GIT_DIRTY=true; expect_fail gate rollback --rollback-runtime-dir "$DOCKER"; unset FAKE_GIT_DIRTY
export FAKE_LOCK_BUSY=true; expect_fail gate rollback --rollback-runtime-dir "$DOCKER"; unset FAKE_LOCK_BUSY
export FAKE_RUNTIME_LOCK_BUSY=true; expect_fail gate rollback --rollback-runtime-dir "$DOCKER"; unset FAKE_RUNTIME_LOCK_BUSY
export FAKE_DOCKER_VOLUME_MISSING=true; expect_fail gate rollback --rollback-runtime-dir "$DOCKER"; unset FAKE_DOCKER_VOLUME_MISSING
export FAKE_DOCKER_IMAGE=bad-image; expect_fail gate rollback --rollback-runtime-dir "$DOCKER"; unset FAKE_DOCKER_IMAGE
sed -i "s/SPRING_DATASOURCE_PASSWORD='rds-fixture-secret'/SPRING_DATASOURCE_PASSWORD='different-secret'/" "$RDS/current/backend.env"
if gate cutover --evidence "$EVIDENCE" --rds-runtime-dir "$RDS" --rollback-runtime-dir "$DOCKER" >/dev/null 2>&1; then printf 'secret identity mismatch did not fail closed\n' >&2; exit 1; fi
[[ ! -s "$FAKE_AWS_MUTATION" ]] || { printf 'AWS mutation was invoked\n' >&2; exit 1; }
[[ ! -s "$FAKE_AWS_ERROR" ]] || { printf 'fake AWS argument assertion failed\n' >&2; exit 1; }
while IFS= read -r aws_call; do [[ "$aws_call" =~ ^(ec2[[:space:]]describe-(instances|vpcs|subnets|security-groups)|rds[[:space:]]describe-orderable-db-instance-options) ]] || { printf 'non-describe AWS operation was logged\n' >&2; exit 1; }; done <"$FAKE_AWS_LOG"
printf '%s\n' 'OPS-DB-002 fake AWS and RDS readiness gates passed without AWS mutation'
