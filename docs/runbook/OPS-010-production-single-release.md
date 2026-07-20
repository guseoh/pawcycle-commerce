# OPS-010 운영 단일 release 배포 Runbook

## 목적과 상태

이 Runbook은 DEPLOY-002의 첫 수동 단일 release를 준비·배포·확인·복구하는 사용자 실행 절차다. GitHub Actions는 병합된 `main`의 Backend와 Frontend를 동일한 40자 commit SHA tag로 공개 GHCR에 게시하고, EC2는 두 image를 pull한다. MySQL은 같은 EC2의 고정 named volume을 사용한다.

- 작업 등급: 고위험
- Region: `ap-northeast-2`
- 대상: Ubuntu Server 24.04 LTS x86_64, `t3.small`
- 외부 공개: Nginx HTTP `80`만 허용
- 내부 전용: MySQL `3306`, Backend `8080`, Frontend `3000`
- 배포 방식: 사용자 수동 단일 release
- 실제 AWS·SSM·GHCR 공개 설정·EC2 배포 검증: 미실행

TLS와 `443`, DNS, 자동 배포, Blue·Green, Spring Session, DB migration 변경과 DB rollback은 범위 밖이다. HTTP 단계에서도 Backend session cookie `Secure=true`를 유지하므로 로그인 기반 운영 검증은 TLS 작업 뒤 수행한다.

## 파일과 고정 계약

| 파일 | 용도 |
| --- | --- |
| `infra/production/compose.yaml` | production 단일 Compose project |
| `infra/production/nginx.conf` | `/api/**` Backend, 그 외 Frontend reverse proxy |
| `infra/production/backend.Dockerfile` | Backend `linux/amd64` image |
| `infra/production/frontend.Dockerfile` | Frontend `linux/amd64` image |
| `.github/workflows/publish-production-images.yml` | 동일 `github.sha` image build·push |
| `infra/production/materialize-ssm-env.sh` | SSM SecureString을 저장소 밖 runtime bundle로 변환 |
| `infra/production/deploy.sh` | preflight, pull, health, smoke, 자동 복귀 |
| `infra/production/rollback.sh` | 이전 SHA image rollback |

release 식별자는 소문자 40자 commit SHA 하나다. Backend와 Frontend는 반드시 같은 SHA를 사용한다. `latest`, branch tag, 서로 다른 SHA, tag만 있고 registry digest가 없는 image는 배포하지 않는다.

production 고정 데이터 volume은 `pawcycle-production-mysql-data`다. 배포와 rollback script는 `docker compose down`, `--volumes`, `docker volume rm`, schema 복원을 호출하지 않는다.

## 생성·적용 전 게이트

다음 중 하나라도 확인할 수 없으면 Secret 조회와 배포를 시작하지 않는다.

1. PR #57이 병합된 최신 `main`이며 OPS-009의 Budget, IAM, Security Group, EC2·EBS·EIP, SSM, Docker와 stop/start 사용자 검증이 완료됐다.
2. Security Group inbound는 HTTP `80`만 이번 서비스에 공개하고 `3306`, `8080`, `3000` 규칙이 없다. SSH `22`는 기본 폐쇄다.
3. EC2 CPU credit mode는 `standard`이고 EBS 여유 공간, memory, Docker 서비스 상태를 확인했다.
4. 대상 SHA는 원격 `main`에 포함되고 해당 SHA의 image publish workflow가 성공했다.
5. 두 GHCR package를 사용자가 Public으로 전환했다. 공개 GHCR은 EC2에서 registry credential 없이 pull할 수 있어야 한다.
6. 대상과 현재 release 사이에 새 Flyway migration 또는 DB schema 결정이 없다. 발견하면 배포·rollback을 중단하고 별도 DB migration 승인을 요청한다.
7. SSM prefix와 네 leaf parameter가 준비됐고 실제 값, 이메일, 계정 ID, 전체 ARN은 저장소나 증거에 기록하지 않는다.
8. `/opt/pawcycle/runtime`과 `/opt/pawcycle/state`는 저장소 checkout 밖이며 root만 접근할 수 있다.
9. 현재 정상 SHA와 이전 SHA, 두 image digest를 비민감 운영 증거로 기록할 위치가 있다.

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
sudo chown "$USER":"$USER" /opt/pawcycle/control
aws --version
aws sts get-caller-identity >/dev/null
sudo systemctl is-enabled docker
sudo systemctl is-active docker
df -h / /var/lib/docker
free -h
```

`get-caller-identity` 출력은 계정 ID가 포함되므로 증거에 복사하지 않는다. AWS CLI가 없으면 AWS 공식 Linux 설치 절차로 설치한 뒤 버전만 기록한다.

`/opt/pawcycle/control`에는 공개 저장소를 clone하고 대상 SHA를 detached checkout한다. 최초 한 번 `git clone <public-repository-url> /opt/pawcycle/control`을 실행한다. `<release-sha>`가 `origin/main`의 조상인지 확인한다.

```bash
cd /opt/pawcycle/control
git fetch --prune origin main
git merge-base --is-ancestor <release-sha> origin/main
git checkout --detach <release-sha>
test "$(git rev-parse HEAD)" = "<release-sha>"
```

명령이 실패하거나 worktree가 불결하면 배포하지 않는다.

## Secret materialize

SSM prefix는 실행 입력으로만 전달한다. script는 네 parameter를 각각 `--with-decryption`으로 조회하고, 하나라도 누락·빈 값·조회 실패면 현재 runtime symlink를 바꾸지 않고 실패한다. 성공 시 `mysql.env`, `backend.env`, `.complete`를 mode `600`으로 만든 뒤 `current` symlink를 원자적으로 교체한다. 기존 symlink target이 관리 경로 안의 `.bundle.*`인지 resolved path로 확인한 뒤 이전 평문 bundle을 제거한다. 값은 stdout에 출력하지 않는다.

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

비민감 image repository와 대상 SHA를 입력한다.

```bash
cd /opt/pawcycle/control
sudo bash infra/production/deploy.sh \
  --sha '<release-sha>' \
  --backend-image 'ghcr.io/<github-owner>/<repository>-backend' \
  --frontend-image 'ghcr.io/<github-owner>/<repository>-frontend' \
  --runtime-dir /opt/pawcycle/runtime \
  --state-dir /opt/pawcycle/state
```

script는 실행 중인 container를 바꾸기 전에 다음을 수행한다.

1. SHA, GHCR repository 형식, runtime file mode와 완료 marker를 검증한다.
2. 현재 정상 SHA가 있으면 그 release의 두 image도 다시 pull·검증해 복귀 가능성을 먼저 확보한다.
3. Compose config를 검증하고 MySQL·Nginx base image를 pull한다.
4. 대상 Backend·Frontend의 정확한 SHA tag를 pull한다.
5. 각 image의 revision label이 대상 SHA와 같고 GHCR `sha256` RepoDigest가 있는지 확인한다.
6. 검증한 digest를 `/opt/pawcycle/state/<sha>.images`에 mode `600`으로 기록한다.

preflight가 실패하면 `docker compose up`을 호출하지 않으므로 기존 정상 release는 변하지 않는다.

preflight 뒤 같은 Compose project를 `--pull never`로 갱신한다. MySQL과 Backend·Frontend health를 먼저 확인하고 Nginx를 강제 재생성해 새 upstream 주소를 사용하게 한 뒤 Nginx health를 기다린다. 전체 health 대기는 service별 최대 240초이며 다음 HTTP smoke를 확인한다.

```text
GET http://127.0.0.1/products
GET http://127.0.0.1/api/products
```

모든 service가 healthy이고 실행 container의 image reference·revision label이 대상 SHA와 같을 때만 `current-sha`를 바꾼다. 실패하면 이전 SHA를 자동 재기동한다. 첫 배포 실패로 이전 SHA가 없으면 Backend·Frontend·Nginx만 정지하고 MySQL과 named volume은 보존한다.

## 상태·port·digest 확인

배포 성공 직후 다음을 확인한다. runtime env 원문은 열지 않는다.

```bash
sudo cat /opt/pawcycle/state/current-sha
sudo ls -l /opt/pawcycle/state/*.images
sudo docker ps --filter label=com.docker.compose.project=pawcycle-production \
  --format 'table {{.Names}}\t{{.Ports}}\t{{.Status}}'
curl --fail --silent --show-error http://127.0.0.1/products >/dev/null
curl --fail --silent --show-error http://127.0.0.1/api/products >/dev/null
```

성공 기준은 proxy에만 host `0.0.0.0:80->80/tcp`가 있고 `3306`, `8080`, `3000` host publish가 없는 것이다. `.images` 파일에는 SHA와 digest만 있으며 Secret이 없다.

## 중지·재기동과 재부팅 복구

단순 점검 중지는 `docker compose stop`만 사용한다. 재기동은 같은 SHA의 `deploy.sh`를 다시 실행해 config, digest, health와 smoke를 재검증한다.

EC2 재부팅 전 `current-sha`, container health와 volume 이름을 기록한다. 재부팅 뒤 Docker `enabled/active`, 네 container의 `healthy`, HTTP smoke, 동일 volume을 확인한다. `restart: unless-stopped`이므로 명시적으로 `stop`한 container는 재부팅만으로 다시 시작되지 않을 수 있다. 이 경우 같은 SHA의 `deploy.sh`를 실행한다.

```bash
sudo systemctl is-enabled docker
sudo systemctl is-active docker
sudo docker volume inspect pawcycle-production-mysql-data --format '{{.Name}}'
```

## 이전 SHA rollback

기본 rollback 대상은 마지막 성공 배포가 기록한 `previous-sha`다. 명시적 `--sha`도 반드시 40자 SHA이고 두 GHCR image가 모두 존재해야 한다.

```bash
cd /opt/pawcycle/control
sudo bash infra/production/rollback.sh \
  --backend-image 'ghcr.io/<github-owner>/<repository>-backend' \
  --frontend-image 'ghcr.io/<github-owner>/<repository>-frontend' \
  --runtime-dir /opt/pawcycle/runtime \
  --state-dir /opt/pawcycle/state
```

rollback은 현재 SHA와 대상 SHA를 모두 preflight한 뒤 애플리케이션 image만 이전 SHA로 바꾼다. 실패하면 직전 현재 SHA를 다시 복구한다. MySQL container가 Compose 판단에 따라 재사용 또는 재생성될 수 있어도 동일 named volume을 사용한다.

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
| GHCR tag·digest 누락 | 대상 release 식별 불가 | workflow와 package visibility 확인 | running container를 바꾸지 않고 중단 |
| MySQL unhealthy | Backend 기동 차단 | `docker compose logs --tail 100 mysql` | volume 삭제 금지, disk·memory·credential 확인 |
| Backend·Frontend unhealthy | proxy 전환 실패 | 해당 service 최근 로그와 health 확인 | 이전 SHA 자동 복귀 결과 확인 |
| HTTP smoke 실패 | 외부 요청 처리 불가 | proxy와 upstream health·최근 로그 확인 | 이전 SHA 복귀, SG 확대 금지 |
| rollback도 실패 | 서비스 중단 지속 | current/target digest, container health 확인 | MySQL 보존, 사용자/Tech Lead 에스컬레이션 |

로그를 공유할 때 Secret, cookie, 전체 account ID·ARN·IP를 가린다. `docker compose logs --tail 100 <service>`처럼 범위를 제한하고 runtime env는 출력하지 않는다.

## 정리와 원상 복구 경계

일시 중지는 `docker compose stop`으로 수행한다. release 실패나 rollback 중에는 volume과 state 파일을 삭제하지 않는다. 서비스 영구 폐기, MySQL volume 삭제, EBS 삭제, schema 복구는 OPS-010의 권한이 아니며 별도 승인과 backup·restore 계획이 필요하다.

materialize는 새 `current` symlink를 게시한 뒤 resolved path가 관리 경로 안으로 확인된 직전 `.bundle.*`만 제거한다. symlink target이 예상 형식이 아니거나 경로 밖으로 해석되면 삭제하지 않고 실패한다. cleanup 실패 메시지는 새 bundle이 이미 active라는 상태를 명시하므로, 사용자는 현재 symlink와 과거 bundle을 확인한 뒤 추가 materialize를 중단하고 에스컬레이션한다.

## 비민감 증거와 실패 기록

보존 가능:

- 대상 commit SHA와 Backend·Frontend가 같은 SHA라는 확인
- 두 image digest
- Compose config 통과, service health, HTTP status
- 공개 port 목록과 named volume 이름
- runtime 파일 mode `600` 확인
- 배포 전후 `current-sha`와 rollback 결과
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
대상 SHA:
비민감 증상·상태:
기존 release 영향:
자동 복귀 결과:
MySQL volume 보존 확인:
중단·에스컬레이션:
실제 AWS 검증: 미실행|실행
```

## 완료와 에스컬레이션

Backend·Frontend 동일 SHA/digest, 모든 health, HTTP smoke, 재부팅 복구, MySQL volume 보존, 이전 SHA rollback을 사용자가 확인하기 전에는 단일 release 완료로 판정하지 않는다. 실제 AWS·SSM·GHCR·EC2 단계는 이번 저장소 작업에서 미실행이며 통과로 기록하지 않는다.

다음은 사용자/Tech Lead 결정이 필요하다.

- customer managed KMS key나 현재 prefix 밖 SSM 권한 필요
- `t3.small`에서 OOM, 지속 swap, disk 부족으로 자원 상향 필요
- 새 DB migration 또는 schema rollback 필요
- private GHCR 인증, 신규 registry 또는 유료 서비스 필요
- `443`, TLS, DNS, 자동 배포, Blue·Green, Spring Session 필요
- MySQL volume·EBS 삭제 또는 backup·restore 필요

## 공식 근거

- [GitHub Container registry 인증과 GitHub Actions 게시](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [GitHub Packages public container의 익명 pull](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages)
- [AWS Systems Manager GetParameter와 WithDecryption](https://docs.aws.amazon.com/systems-manager/latest/APIReference/API_GetParameter.html)
- [Parameter Store prefix 최소 권한](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-access.html)
- [SecureString과 AWS KMS 경계](https://docs.aws.amazon.com/systems-manager/latest/userguide/secure-string-parameter-kms-encryption.html)
- [Docker Compose health 기반 시작 순서](https://docs.docker.com/compose/how-tos/startup-order/)
- [AWS CLI Linux 설치](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
