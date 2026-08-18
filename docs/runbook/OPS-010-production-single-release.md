# OPS-010 운영 단일 release 배포 Runbook

## 목적과 상태

이 Runbook은 DEPLOY-002의 첫 수동 단일 release를 준비·배포·확인·복구하는 사용자 실행 절차다. GitHub Actions는 병합된 `main`의 Backend와 Frontend를 동일한 40자 commit SHA tag로 공개 GHCR에 게시하고, EC2는 두 image를 pull한다. MySQL은 같은 EC2의 고정 named volume을 사용하며 보존한다.

- 작업 등급: 고위험
- Region: `ap-northeast-2`
- 대상: Ubuntu Server 24.04 LTS x86_64, `t3.small`
- 외부 공개: Nginx HTTP `80`만 허용
- 내부 전용: MySQL `3306`, Backend `8080`, Frontend `3000`
- 배포 방식: 사용자 수동 단일 release
- 실제 운영 검증: OPS-010의 HTTP 대상 release `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6` 활성화·재부팅 복구와, OPS-012의 검증용 release `f80e29293146fae13bda1c01d18131d651ede1d1` 배포 후 원래 release 복귀를 사용자/Tech Lead가 확인함. OPS-012의 HTTPS 확인은 OPS-011에서 이미 구성된 HTTPS가 release 전환 뒤에도 보존됐는지 재검증한 것이며 OPS-010 구현 범위를 확대한 것이 아님. 상세 rollback 증거는 `docs/reports/OPS-012/sre-report.md`, 판단 인수인계는 `docs/handoffs/OPS-012/sre-to-tl.md`에 기록

TLS와 `443`, DNS는 OPS-010 구현 범위 밖이었고 이후 OPS-011에서 HTTPS 구성을 완료했다. 자동 배포, Blue·Green, Spring Session, DB migration 변경과 DB rollback도 OPS-010 범위 밖이다. HTTP 단계에서도 Backend session cookie `Secure=true`를 유지하므로 로그인 기반 운영 검증은 TLS 작업 뒤 수행한다.

## 파일과 고정 계약

| 파일 | 용도 |
| --- | --- |
| `infra/production/compose.yaml` | production 단일 Compose project |
| `infra/production/nginx.conf` | HTTP bootstrap과 내부 release smoke reverse proxy |
| `infra/production/nginx.https.conf` | 승인된 HTTPS hostname과 reverse proxy template |
| `infra/production/backend.Dockerfile` | Backend `linux/amd64` image build |
| `infra/production/frontend.Dockerfile` | Frontend `linux/amd64` image build |
| `.github/workflows/publish-production-images.yml` | 동일 `github.sha` image build·push |
| `infra/production/materialize-ssm-env.sh` | SSM SecureString을 저장소 밖 runtime bundle로 변환 |
| `infra/production/release-common.sh` | Control·Release 계약, image, health와 smoke 공통 검증 |
| `infra/production/deploy.sh` | preflight, pull, health, smoke, 자동 복귀 |
| `infra/production/rollback.sh` | 이전 SHA image rollback |

release 식별자는 소문자 40자 commit SHA 하나다. Backend와 Frontend는 반드시 같은 SHA를 사용한다. `latest`, branch tag, 서로 다른 SHA, tag만 있고 registry digest가 없는 image는 배포하지 않는다. 같은 SHA의 digest는 최초 검증 기록과 달라질 수 없으며 MySQL·Nginx도 Compose에 기록된 manifest digest로 고정한다.

production 고정 데이터 volume은 `pawcycle-production-mysql-data`다. 배포와 rollback script는 `docker compose down`, `--volumes`, `docker volume rm`, schema 복원을 호출하지 않는다.

Application Release 상태와 Production Control 상태는 분리한다.

- `current-sha`: 현재 실행 중인 Backend·Frontend Application Release SHA
- `previous-sha`: 마지막 성공 전환 직전의 Application Release SHA
- `contract-sha`: 사용자가 승인하고 검증한 현재 `/opt/pawcycle/control` HEAD
- `previous-contract-sha`: `previous-sha`가 마지막으로 실행됐을 때 사용한 승인 Control SHA

Release 호환성 비교 대상은 실제 Container 활성화 계약인 `compose.yaml`, `nginx.conf`, `nginx.https.conf`다. Dockerfile은 image build 결과의 SHA tag·OCI revision·registry digest로 검증한다. Control Script는 Application Release SHA와 비교하지 않지만, 현재 checkout의 다음 파일이 모두 Git HEAD 기준으로 깨끗해야 한다.

```text
infra/production/compose.yaml
infra/production/nginx.conf
infra/production/nginx.https.conf
infra/production/release-common.sh
infra/production/deploy.sh
infra/production/rollback.sh
infra/production/subscription-automation-control.sh
infra/production/subscription-automation-preflight.sh
infra/production/production-db-restore.sh
infra/production/materialize-ssm-env.sh
infra/production/rds-read-only-preflight.sh
infra/production/rds-transition-gate.sh
```

## 생성·적용 전 게이트

다음 중 하나라도 확인할 수 없으면 Secret 조회와 배포를 시작하지 않는다.

1. PR #57이 병합된 최신 `main`이며 OPS-009의 Budget, IAM, Security Group, EC2·EBS·EIP, SSM, Docker와 stop/start 사용자 검증이 완료됐다.
2. Security Group inbound는 HTTP `80`만 이번 서비스에 공개하고 `3306`, `8080`, `3000` 규칙이 없다. SSH `22`는 기본 폐쇄다.
3. EC2 CPU credit mode는 `standard`이고 EBS 여유 공간, memory, Docker 서비스 상태를 확인했다.
4. 대상 Release SHA는 원격 `main`에 포함되고 해당 SHA의 image publish workflow가 성공했다.
5. 두 GHCR package를 사용자가 Public으로 전환했다. 공개 GHCR은 EC2에서 registry credential 없이 pull할 수 있어야 한다.
6. 대상과 현재 Release 사이에 새 Flyway migration 또는 DB schema 결정이 없다. 발견하면 배포·rollback을 중단하고 별도 DB migration 승인을 요청한다.
7. SSM prefix와 네 leaf parameter가 준비됐고 실제 값, 이메일, 계정 ID, 전체 ARN은 저장소나 증거에 기록하지 않는다.
8. `/opt/pawcycle/runtime`과 `/opt/pawcycle/state`는 저장소 checkout 밖이며 root만 접근할 수 있다.
9. 현재 정상 SHA와 이전 SHA, Backend·Frontend·MySQL·Nginx digest를 비민감 운영 증거로 기록할 위치가 있다.
10. `/opt/pawcycle/control`은 승인된 원격 `main` commit의 clean detached checkout이며 지정된 Control 파일에 staged·unstaged·untracked 변경이 없다.
11. `/opt/pawcycle/state/contract-sha`는 현재 승인된 Control HEAD를 가리키는 mode `600` regular non-symlink file이다.
12. `contract-sha`가 아직 없다면 `--adopt-contract-sha`에는 사용자가 이미 승인한 기존 운영 Control 기준 SHA를 전달한다. Script는 그 기준과 현재 clean Control HEAD의 Release 계약이 같은지, 현재 실행 Release의 image identity·digest·health·HTTP·HTTPS smoke가 정상인지 검증한 뒤 현재 Control HEAD를 `contract-sha`로 기록한다.
13. `contract-sha`가 존재하지만 현재 Control HEAD와 다르면 `--adopt-contract-sha`에는 현재 Control HEAD를 정확히 전달한다. Script는 저장된 승인 Control과 현재 Control의 Release 계약이 같은지 검증하고 현재 실행 Release를 재검증한 뒤 상태를 갱신한다.
14. 승인된 현재 `contract-sha`와 대상 Release SHA의 Release 계약이 다르면 image pull과 Container 변경 전에 중단한다.

## GitHub Actions image 게시

`.github/workflows/publish-production-images.yml`은 `main` push 또는 `main`을 선택한 수동 실행에서만 동작한다. workflow 권한은 다음 두 개뿐이다.

```yaml
permissions:
  contents: read
  packages: write
```

두 build는 같은 `${{ github.sha }}`를 tag와 `org.opencontainers.image.revision` label에 사용하며 `linux/amd64`만 게시한다.

```text
ghcr.io/<github-owner>/<repository>-backend:<40-character-sha>
ghcr.io/<github-owner>/<repository>-frontend:<40-character-sha>
```

사용자는 병합 뒤 GitHub Actions에서 대상 SHA의 run 성공과 두 digest 출력을 확인한다. package visibility는 GitHub Packages 설정에서 각각 Public으로 바꾼다. workflow run 번호, 현재 check 개수와 화면 상태는 저장소 문서에 복제하지 않고 GitHub를 권위 원본으로 둔다.

## SSM Parameter Store와 최소 권한 경계

사용자가 정한 production prefix 아래에 다음 leaf를 각각 `SecureString`으로 저장한다.

```text
<ssm-prefix>/MYSQL_DATABASE
<ssm-prefix>/MYSQL_USER
<ssm-prefix>/MYSQL_PASSWORD
<ssm-prefix>/MYSQL_ROOT_PASSWORD
```

instance role에는 선택한 prefix의 읽기만 추가한다. 아래 placeholder를 실제 값으로 바꾸는 AWS 작업은 병합 뒤 사용자가 수행한다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadPawcycleProductionParameters",
      "Effect": "Allow",
      "Action": "ssm:GetParameter",
      "Resource": "arn:aws:ssm:ap-northeast-2:<account-id>:parameter/<approved-prefix>/*"
    }
  ]
}
```

기본 AWS managed key `alias/aws/ssm`만 이번 기본안으로 사용한다. customer managed KMS key와 별도 `kms:Decrypt`가 필요하면 권한을 임의 추가하지 않고 중단한다. Parameter 생성·수정·삭제 권한, 다른 prefix 조회, `ssm:*`는 instance role 기본안이 아니다.

SSM parameter 이름에는 선행 `/`를 사용하지만 IAM ARN의 `parameter/` 뒤 resource path에는 선행 `/`를 반복하지 않는다.

## 서버 준비

SSM Session Manager로 접속하고 다음 비민감 경로를 준비한다. AWS CLI 설치와 instance role 적용 여부도 확인한다.

```bash
sudo install -d -m 755 /opt/pawcycle/control
sudo install -d -m 700 /opt/pawcycle/runtime /opt/pawcycle/state
sudo chown "$(id -un):$(id -gn)" /opt/pawcycle/control
aws --version
aws sts get-caller-identity >/dev/null
sudo systemctl is-enabled docker
sudo systemctl is-active docker
df -h / /var/lib/docker
free -h
```

`get-caller-identity` 출력은 계정 ID가 포함되므로 증거에 복사하지 않는다. AWS CLI가 없으면 AWS 공식 Linux 설치 절차로 설치한 뒤 버전만 기록한다.

`/opt/pawcycle/control`에는 공개 저장소를 clone하고 승인된 Control SHA를 detached checkout한다. Control SHA는 배포 대상 Application Release SHA와 다를 수 있다. 최초 한 번 `git clone <public-repository-url> /opt/pawcycle/control`을 실행한다.

```bash
cd /opt/pawcycle/control
git fetch --prune origin main
git merge-base --is-ancestor <control-sha> origin/main
git merge-base --is-ancestor <release-sha> origin/main
git checkout --detach <control-sha>
test "$(git rev-parse HEAD)" = "<control-sha>"
test -z "$(git status --porcelain --untracked-files=all -- \
  infra/production/compose.yaml \
  infra/production/nginx.conf \
  infra/production/nginx.https.conf \
  infra/production/release-common.sh \
  infra/production/deploy.sh \
  infra/production/rollback.sh \
  infra/production/subscription-automation-control.sh \
  infra/production/subscription-automation-preflight.sh \
  infra/production/production-db-restore.sh \
  infra/production/materialize-ssm-env.sh \
  infra/production/rds-read-only-preflight.sh \
  infra/production/rds-transition-gate.sh)"
```

명령이 실패하거나 Control 계약 worktree가 불결하면 배포하지 않는다. `/opt/pawcycle/control`에서 직접 파일을 수정하지 않는다.

## Secret materialize

SSM prefix는 실행 입력으로만 전달한다. script는 runtime directory의 `flock`을 먼저 획득해 동시 materialize를 거부하고, 네 parameter를 각각 `--with-decryption`으로 조회한다. 하나라도 누락·빈 값·조회 실패면 현재 runtime symlink를 바꾸지 않고 실패한다. 기본 datasource는 `mysql:3306`, `DISABLED`이며 backend.env에 명시한다. 미래 RDS는 별도 고위험 승인 후 `--datasource-host`, `--datasource-port 3306`, `--datasource-ssl-mode REQUIRED`만 사용할 수 있다. RDS 전환 경계는 [OPS-DB-002](OPS-DB-002-rds-migration-cutover.md)를 따른다. 성공 시 `mysql.env`, `backend.env`, `.complete`를 mode `600`으로 만든 뒤 `current` symlink를 원자적으로 교체한다. 기존 symlink target이 관리 경로 안의 `.bundle.*`인지 resolved path로 확인한 뒤 이전 평문 bundle을 제거한다. 값은 stdout에 출력하지 않는다.

```bash
cd /opt/pawcycle/control
sudo bash infra/production/materialize-ssm-env.sh \
  --ssm-prefix '<approved-ssm-prefix>' \
  --output-dir /opt/pawcycle/runtime \
  --region ap-northeast-2

sudo stat -c '%a %n' \
  /opt/pawcycle/runtime/current/mysql.env \
  /opt/pawcycle/runtime/current/backend.env \
  /opt/pawcycle/runtime/current/.complete
```

성공 기준은 세 파일 모두 `600`이고 파일 내용이 화면·shell history·로그에 출력되지 않은 것이다. `set -x`, `cat`, `env`, `docker inspect`로 Secret 값을 출력하지 않는다.

## release preflight와 배포

비민감 image repository와 대상 Release SHA를 입력한다.

```bash
cd /opt/pawcycle/control
sudo bash infra/production/deploy.sh \
  --sha '<release-sha>' \
  --backend-image 'ghcr.io/<github-owner>/<repository>-backend' \
  --frontend-image 'ghcr.io/<github-owner>/<repository>-frontend' \
  --runtime-dir /opt/pawcycle/runtime \
  --state-dir /opt/pawcycle/state

# contract-sha가 없는 최초 전환에서만 별도 승인 후 추가
#   --adopt-contract-sha '<approved-prior-control-baseline-sha>'

# contract-sha가 존재하지만 현재 Control HEAD와 다를 때만 별도 승인 후 추가
#   --adopt-contract-sha '<current-control-sha>'
```

### Release contract·Flyway boundary의 Production Deploy 승인 경로

GitHub `Production Deploy`는 일반 Release에도 먼저 SSM `preflight`, 그 성공 뒤 같은 target·승인 SHA로 `deploy`를 호출한다. `preflight`는 image를 pull·검증할 수 있지만 Container, MySQL, state 파일을 바꾸지 않는다. `deploy`는 preflight 결과를 신뢰하지 않고 동일 검사를 다시 수행한다. 실제 Production 실행은 이 Runbook의 별도 고위험 사용자 승인이 있어야 하며, 이 저장소 변경 자체는 실행 승인이 아니다.

Release contract boundary가 감지되면 Control checkout 전환을 자동화하지 않는다. 사용자는 승인된 clean detached Control SHA로 별도 전환한 뒤 workflow dispatch에 아래 SHA를 정확히 입력한다.

- `approved_contract_from_sha`: 현재 state의 `contract-sha`
- `approved_control_sha`: 전환한 clean detached Control HEAD
- `approved_migration_target_sha`: Flyway migration bundle도 바뀐 경우에만 target Release SHA

contract boundary는 `stored contract-sha == approved_contract_from_sha`, `current clean Control HEAD == approved_control_sha`, 새 Control의 Release contract가 target Release와 같음, `target != current-sha`를 모두 만족해야 한다. migration boundary는 `approved_migration_target_sha == target_sha`여야 한다. 두 boundary가 함께 있으면 세 승인값 모두 필요하다. 빈 값, 불일치, dirty Control, target 외 SHA 또는 `--adopt-contract-sha`는 boundary 승인이 아니며 Container·DB·release state 전환 전에 거부한다.

`--adopt-contract-sha`는 기존처럼 동일 Release contract에서 Control SHA를 채택하는 경로만 의미한다. boundary를 통과한 새 Control SHA도 target activation·health·smoke가 성공한 뒤에만 `contract-sha`로 기록하며, 그 전에는 기존 `contract-sha`와 `previous-contract-sha`를 보존한다. contract 또는 migration boundary에서 activation이 실패하면 이전 Release 자동복귀를 하지 않고 Application services를 중지한다. Scheduler는 OFF로 남고 MySQL·named volume은 보존한다.

script는 실행 중인 Container를 바꾸기 전에 다음을 수행한다.

1. SHA, GHCR repository 형식, runtime file mode와 완료 marker를 검증하고 release lock을 획득한다.
2. 현재 Control 계약 파일의 staged·unstaged·untracked 변경이 없는지 확인하고 Control HEAD를 계산한다.
3. `contract-sha`가 없으면 전달한 기존 운영 기준 SHA와 현재 Control HEAD의 Release 계약을 비교한다. `contract-sha`가 현재 Control HEAD와 다르면 전달한 값이 현재 HEAD와 정확히 같은지와 저장된 계약의 호환성을 확인한다.
4. 동일-contract Control 전환이 필요한 경우 현재 Release의 image identity·digest·health·HTTP·HTTPS smoke를 확인해 새 Control SHA를 activation 뒤 기록할 후보로만 보관한다.
5. 승인된 `contract-sha`와 대상 Release SHA의 `compose.yaml`, `nginx.conf`, `nginx.https.conf`가 같은지 확인한다. commit 부재·Git 오류·차이 발견 시 image pull과 Container 변경 전에 중단한다.
6. Compose config를 검증하고 digest로 고정된 MySQL·Nginx와 현재 복귀 Release image를 pull·검증해 복귀 가능성을 먼저 확보한다.
7. 대상 Backend·Frontend의 정확한 SHA tag를 pull하고 revision label과 GHCR `sha256` RepoDigest를 확인한다.
8. 네 image digest 후보를 `/opt/pawcycle/state/<sha>.images`와 비교한다. 기존 기록과 한 글자라도 다르면 기록을 덮어쓰지 않고 중단하며, 최초 SHA만 mode `600` 파일로 원자적으로 기록한다.

계약 차이·Control drift·dirty worktree·digest drift·preflight 실패 시 `docker compose up`을 호출하지 않으므로 기존 정상 Release state, Container와 named volume은 변하지 않는다.

preflight 뒤 같은 Compose project를 `--pull never`로 갱신한다. MySQL과 Backend·Frontend health를 먼저 확인하고 Nginx를 강제 재생성해 새 upstream 주소를 사용하게 한 뒤 Nginx health를 기다린다. 전체 health 대기는 service별 최대 240초이며 다음 HTTP smoke를 확인한다.

```text
GET http://127.0.0.1/products
GET http://127.0.0.1/api/products
```

두 smoke는 각각 명시적으로 실패를 반환한다. 앞선 `/products` 실패 뒤 `/api/products`가 우연히 성공해도 Release 성공으로 바뀌지 않는다. 모든 service가 healthy이고 실행 Container의 Application image reference·revision label과 MySQL·Nginx pinned reference가 일치하며 두 smoke가 모두 성공할 때만 `current-sha`를 바꾼다. 실패하면 계약이 같은 이전 SHA만 자동 재기동한다. 첫 배포 실패로 이전 SHA가 없으면 Backend·Frontend·Nginx만 정지하고 MySQL과 named volume은 보존하며 성공 state를 기록하지 않는다.

## 상태·port·digest 확인

배포 성공 직후 다음을 확인한다. runtime env 원문은 열지 않는다.

```bash
sudo cat /opt/pawcycle/state/current-sha
sudo cat /opt/pawcycle/state/contract-sha
sudo ls -l \
  /opt/pawcycle/state/current-sha \
  /opt/pawcycle/state/contract-sha \
  "/opt/pawcycle/state/<release-sha>.images"
sudo docker ps --filter label=com.docker.compose.project=pawcycle-production \
  --format 'table {{.Names}}\t{{.Ports}}\t{{.Status}}'
curl --fail --silent --show-error http://127.0.0.1/products >/dev/null
curl --fail --silent --show-error http://127.0.0.1/api/products >/dev/null
```

위 두 `curl`은 EC2 내부 loopback 검증이다. 외부 smoke는 별도의 외부 사용자 PC에서 EC2 외부 HTTP `80`의 `/products`와 `/api/products`를 호출해 확인하고 공인 IP는 증거에 기록하지 않는다.

성공 기준은 proxy에만 host `0.0.0.0:80->80/tcp`가 있고 `3306`, `8080`, `3000` host publish가 없는 것이다. `.images` 파일에는 SHA와 네 image digest만 있으며 Secret이 없다. 같은 SHA를 재검증할 때 파일 내용이 바뀌면 안 된다. `contract-sha`는 실행한 clean Control HEAD와 같아야 한다.

## 중지·재기동과 재부팅 복구

단순 점검 중지는 `docker compose stop`만 사용한다. 재기동은 같은 SHA의 `deploy.sh`를 다시 실행해 Control·Release 계약, config, digest, health와 smoke를 재검증한다.

EC2 재부팅 전 `current-sha`, `contract-sha`, Container health와 volume 이름을 기록한다. 재부팅 뒤 Docker `enabled/active`, 네 Container의 `healthy`, HTTP smoke, 동일 volume을 확인한다. `restart: unless-stopped`이므로 명시적으로 `stop`한 Container는 재부팅만으로 다시 시작되지 않을 수 있다. 이 경우 같은 SHA의 `deploy.sh`를 실행한다.

```bash
sudo systemctl is-enabled docker
sudo systemctl is-active docker
sudo docker volume inspect pawcycle-production-mysql-data --format '{{.Name}}'
```

## 이전 SHA rollback

기본 rollback 대상은 마지막 성공 배포가 기록한 `previous-sha`다. 무인자 rollback은 공유 `deploy.lock`을 획득한 뒤에만 `previous-sha`를 읽으며, 파일이 symlink이거나 mode `600`이 아니거나 내용이 40자 SHA가 아니면 Container 변경 전에 실패한다. 명시적 `--sha`도 반드시 40자 SHA이고 두 GHCR image가 모두 존재해야 한다.

> 현재 OPS-012 최종 `previous-sha`는 원래 Release와 Backend·Frontend 기능 차이가 없고 OPS-010 문서만 다른 검증용 Release다. rollback 메커니즘은 검증됐지만 이 기본 대상은 Application regression 복구 후보가 아니다. 향후 기능 차이가 있는 정상 Release가 `previous-sha`를 갱신하기 전에는 무인자 rollback에 의존하지 말고, 사용자 승인을 받은 대상 SHA를 `--sha`로 명시해 Application 차이·GHCR image 존재·Production 계약·DB schema 호환성을 모두 확인한다. state 파일을 수동 편집해 이 경계를 우회하지 않는다.

```bash
cd /opt/pawcycle/control
sudo bash infra/production/rollback.sh \
  --backend-image 'ghcr.io/<github-owner>/<repository>-backend' \
  --frontend-image 'ghcr.io/<github-owner>/<repository>-frontend' \
  --runtime-dir /opt/pawcycle/runtime \
  --state-dir /opt/pawcycle/state
```

rollback은 현재 Control worktree가 깨끗하고 HEAD가 `contract-sha`와 정확히 같은지 먼저 검증한다. `deploy.sh`와 `rollback.sh`는 공유 `deploy.lock` 아래 `previous-sha`, `previous-contract-sha`를 먼저 기록하고 `current-sha`를 성공 전환의 마지막 commit marker로 기록한다. 따라서 대상이 기록된 `previous-sha`이고 `previous-sha != current-sha`이며 `previous-contract-sha`가 존재할 때만 기록된 이전 Control을 사용한다. 이 경우 `previous-contract-sha`와 현재 `contract-sha`의 `compose.yaml`, `nginx.conf`, `nginx.https.conf` Release 계약이 같아야 rollback을 허용한다. Control SHA 문자열이 달라도 세 계약 파일이 같으면 허용하고, 다르면 Container·state·volume 변경 전에 거부한다.

기록된 `previous-sha`가 아닌 명시적 대상, `previous-contract-sha` 누락 또는 `previous-sha == current-sha`인 부분 기록 상태는 이전 Control 경로를 사용하지 않는다. 이 경우 기존처럼 현재 `contract-sha`와 대상 commit의 Release 계약 세 파일이 같아야 한다. 조건을 통과한 뒤 현재 복구 Release와 rollback 대상을 모두 preflight하고 Application image를 바꾼다. 활성화 실패 시에는 검증을 통과한 직전 현재 SHA를 다시 복구한다. MySQL Container가 Compose 판단에 따라 재사용 또는 재생성될 수 있어도 동일 named volume을 사용한다.

Control HEAD가 `contract-sha`와 다르면 rollback에서 계약을 채택하지 않는다. 먼저 실제 Production 실행 승인을 받고 `deploy.sh --adopt-contract-sha '<current-control-sha>'`로 현재 Release를 검증해 Control 계약을 전환하거나, 기존 승인 Control checkout으로 복귀한 뒤 rollback한다.

rollback은 다음을 하지 않는다.

- MySQL volume 삭제
- DB dump restore 또는 schema downgrade
- Flyway history 수정
- `docker compose down --volumes`
- `latest`나 서로 다른 Backend·Frontend SHA 사용

대상 code가 현재 DB schema와 호환되지 않거나 migration rollback이 필요하면 즉시 중단하고 DB 담당 결정과 별도 backup·restore 작업을 요청한다.

## 장애 증상과 안전 대응

| 증상 | 영향 | 확인 | 안전 대응 |
| --- | --- | --- | --- |
| SSM parameter 누락 | 새 runtime bundle 생성 불가 | parameter 이름·role prefix 권한만 확인 | 값 출력 없이 수정 후 materialize 재실행 |
| GHCR tag·digest 누락 | 대상 Release 식별 불가 | workflow와 package visibility 확인 | running Container를 바꾸지 않고 중단 |
| 같은 SHA digest drift | 이미 검증한 Release 식별자 변조 가능 | 기존 `.images`와 registry digest 비교 | 기록을 덮어쓰지 않고 Container 변경 전 중단 |
| MySQL·Nginx pinned digest 불일치 | base image 불변성 훼손 | Compose pin과 pull 결과 비교 | tag로 우회하지 않고 중단 |
| Control worktree 불결 | 승인되지 않은 로컬 Script·설정 실행 가능 | 지정된 Control 파일의 `git status --porcelain` 확인 | 직접 수정 금지, clean 승인 checkout으로 복구 |
| Control HEAD와 `contract-sha` 불일치 | 승인 Control 추적 불가 | 현재 HEAD와 mode `600` state 비교 | 별도 승인 후 현재 HEAD를 명시적으로 채택하거나 승인 checkout 복구 |
| Release 계약 차이 | Application-only 전환 안전성 없음 | SSM preflight와 deploy에서 `contract-sha`, clean Control HEAD, target의 Compose·Nginx 세 파일 비교 | 승인된 detached Control SHA와 두 contract SHA를 dispatch에 정확히 입력; activation 실패 시 자동복귀 없이 Application 중지·MySQL 보존 |
| MySQL unhealthy | Backend 기동 차단 | `docker compose logs --tail 100 mysql` | volume 삭제 금지, disk·memory·credential 확인 |
| Backend·Frontend unhealthy | proxy 전환 실패 | 해당 service 최근 로그와 health 확인 | 이전 SHA 자동 복귀 결과 확인 |
| HTTP smoke 실패 | 외부 요청 처리 불가 | proxy와 upstream health·최근 로그 확인 | 이전 SHA 복귀, SG 확대 금지 |
| rollback도 실패 | 서비스 중단 지속 | current/target digest, Container health 확인 | MySQL 보존, 사용자/Tech Lead 에스컬레이션 |

로그를 공유할 때 Secret, cookie, 전체 account ID·ARN·IP를 가린다. `docker compose logs --tail 100 <service>`처럼 범위를 제한하고 runtime env는 출력하지 않는다.

## 정리와 원상 복구 경계

일시 중지는 `docker compose stop`으로 수행한다. Release 실패나 rollback 중에는 volume과 state 파일을 삭제하지 않는다. 서비스 영구 폐기, MySQL volume 삭제, EBS 삭제, schema 복구는 OPS-010의 권한이 아니며 별도 승인과 backup·restore 계획이 필요하다.

materialize는 `flock` 범위 안에서 새 `current` symlink를 게시한 뒤 resolved path가 관리 경로 안으로 확인된 직전 `.bundle.*`만 제거한다. 동시 실행은 새 bundle 생성 전에 중단한다. symlink target이 예상 형식이 아니거나 경로 밖으로 해석되면 삭제하지 않고 실패한다. cleanup 실패 메시지는 새 bundle이 이미 active라는 상태를 명시하므로, 사용자는 현재 symlink와 과거 bundle을 확인한 뒤 추가 materialize를 중단하고 에스컬레이션한다.

## 비민감 증거와 실패 기록

보존 가능:

- 대상 Release SHA와 Backend·Frontend가 같은 SHA라는 확인
- 승인 Control SHA와 `contract-sha` 일치, 지정 Control worktree clean 결과
- Backend·Frontend·MySQL·Nginx digest와 같은 SHA 기록 비교 결과
- Compose config 통과, service health, HTTP status
- 공개 port 목록과 named volume 이름
- runtime·state 파일 mode `600` 확인
- 배포 전후 `current-sha`, `previous-sha`, `contract-sha`, `previous-contract-sha`와 rollback 결과
- 실제 AWS·GHCR·EC2 검증의 실행 또는 미실행 구분

보존 금지:

- SSM parameter 값과 runtime env 내용
- account ID, 전체 ARN, 사용자 IP, 이메일
- session cookie, AWS credential, token
- 동적인 GitHub review thread 개수와 check 개수

실패 기록 형식:

```text
시각(Asia/Seoul):
단계:
Control SHA:
대상 Release SHA:
비민감 증상·상태:
기존 Release 영향:
자동 복귀 결과:
MySQL volume 보존 확인:
중단·에스컬레이션:
실제 AWS 검증: 미실행|실행
```

## 완료와 에스컬레이션

대상 Release `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6`의 OPS-010 HTTP 최초 활성화·재부팅 복구와 2026-07-27 OPS-012의 실제 rollback 검증에 더해, 2026-07-30 OPS-021 Control 계약 아래 같은 이전 Release로 rollback한 뒤 원래 Application Release `2e9222b568a3469e8ccc5edce1b5301218c6888e`로 재배포한 결과를 사용자가 확인했다. rollback·재배포 후 health, 내부 Smoke, 외부 HTTPS, state와 Production MySQL volume 보존이 정상이다. DB restore·schema downgrade·Flyway 수정·volume 삭제는 수행하지 않았다. 실제 중단 시간과 Actual Production DB restore는 여전히 미검증이다. 상세 비민감 증거는 `docs/reports/OPS-012/sre-report.md`와 `docs/reports/OPS-021/production-execution-report.md`를 따른다.

다음은 사용자/Tech Lead 결정이 필요하다.

- customer managed KMS key나 현재 prefix 밖 SSM 권한 필요
- `t3.small`에서 OOM, 지속 swap, disk 부족으로 자원 상향 필요
- 새 DB migration 또는 schema rollback 필요
- private GHCR 인증, 신규 registry 또는 유료 서비스 필요
- 기존 HTTPS domain·certificate·`443` 계약 변경 또는 재발급 필요
- 자동 배포, Blue·Green, Spring Session 필요
- MySQL volume·EBS 삭제 또는 backup·restore 필요

## 공식 근거

- [GitHub Container registry 인증과 GitHub Actions 게시](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [GitHub Packages public container의 익명 pull](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages)
- [AWS Systems Manager GetParameter와 WithDecryption](https://docs.aws.amazon.com/systems-manager/latest/APIReference/API_GetParameter.html)
- [Parameter Store prefix 최소 권한](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-access.html)
- [SecureString과 AWS KMS 경계](https://docs.aws.amazon.com/systems-manager/latest/userguide/secure-string-parameter-kms-encryption.html)
- [Docker Compose health 기반 시작 순서](https://docs.docker.com/compose/how-tos/startup-order/)
- [AWS CLI Linux 설치](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
