# OPS-010 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-010
- 작업 등급: 고위험
- 역할: Platform/SRE

## 작업 목적

DEPLOY-002의 production 단일 release 기반을 구현해 병합된 `main`의 Backend·Frontend를 동일 commit SHA로 GHCR에 게시하고, 사용자가 같은 EC2에서 Secret 분리, MySQL 데이터 보존, 상태 확인과 이전 SHA rollback을 수행할 수 있게 한다.

## 입력 문서

- 현재 OPS-010 사용자 지시
- 현재 OPS-010 후속 수정 사용자 지시
- 루트와 `infra/AGENTS.md`
- `docs/roles/platform-sre.md`
- `platform-sre` Skill
- `docs/runbook/OPS-009-aws-operations-foundation.md`
- `docs/runbook/lean-harness.md`
- 기존 `infra/local-integration/**`와 Repository Validation 관례

## 승인 입력

- Ubuntu 24.04 LTS x86_64, `t3.small`, 같은 EC2의 MySQL
- GitHub Actions 공개 GHCR, Backend·Frontend 동일 commit SHA tag
- SSM Parameter Store에서 저장소 밖 runtime 파일 materialize
- HTTP `80`만 공개, `3306`·`8080`·`3000` 비공개
- 수동 단일 release, 이전 SHA rollback
- 실제 AWS·SSM·GHCR 설정과 EC2 배포는 병합 후 사용자 수행
- 사용자/Tech Lead가 대상 release `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6`의 활성화와 재부팅 복구 결과를 비민감 증거로 제공
- 사용자/Tech Lead가 배포 전 단일 시점에 disk 38G 중 3.0G 사용·35G 여유·8%, available memory 약 1.4 GiB와 swap 0B를 확인
- `/pawcycle-commerce/prod` 아래 네 SecureString 존재, 해당 prefix의 `ssm:GetParameter` 조회와 runtime materialize 성공을 값 비출력 방식으로 확인
- 외부 사용자 PC에서 EC2 외부 HTTP `80`의 `/products`와 `/api/products`를 확인했으며 공인 IP는 기록하지 않음

## 명시적 승인 근거 (고위험 필수)

초기 OPS-010 사용자 지시와 후속 수정 지시가 production Compose·Nginx, image workflow, SSM materialize, 수동 deploy·rollback script, release 불변성 gate와 사용자 Runbook 구현을 명시적으로 승인했다. 현재 후속 지시는 사용자 운영 결과 반영과 Runbook 명령 두 건의 수정을 승인했다. 새 migration, DB rollback, TLS, 자동 배포, AWS 리소스 변경과 서버 재배포는 승인 범위에서 제외됐다.

## 변경 범위

- production Dockerfile, Compose, Nginx와 비민감 example
- 같은 `github.sha`로 두 `linux/amd64` image를 게시하는 workflow
- fail-closed SSM runtime bundle materialize
- image revision·digest preflight, health·smoke와 자동 복귀 deploy
- 같은 SHA image 기록 비교, MySQL·Nginx digest pin과 release 계약 호환성 gate
- 이전 SHA rollback과 MySQL volume 보존 경계
- 정적 계약 validator, shell 상태기계 test와 실제 local Compose lifecycle test
- OPS-010 Runbook, 보고서와 사용자/Tech Lead 인수인계
- 사용자 제공 운영 증거 반영과 Session Manager 사용자·root 전용 state 경계의 Runbook 명령 수정

## 변경하지 않은 범위

- 실제 AWS, SSM parameter, IAM 적용, GHCR visibility와 EC2 배포
- DNS, TLS, `443`, CloudWatch, Discord와 S3 backup·restore
- 자동 deployment, Blue·Green과 Spring Session
- DB migration, schema, 제품 코드와 API
- private registry credential과 Secret

## 인수 조건 매핑

| 인수 조건 | 구현·검증 |
| --- | --- |
| Compose config와 local lifecycle | config validator와 실제 build·health·smoke·stop/start·SHA 전환 검증 |
| 동일 commit SHA | workflow tag·label, Compose 단일 `RELEASE_SHA`, running image 확인 |
| 최소 GitHub 권한 | `contents: read`, `packages: write`만 선언 |
| Secret fail-closed와 mode `600` | 필수 leaf 개별 조회, atomic symlink, 누락 test, file mode test |
| 내부 port 비공개 | proxy HTTP만 publish하는 Compose JSON 검사 |
| digest와 health 실패 안전성 | activation 전 RepoDigest·revision 검증, 이전 release 선검증과 자동 복귀 |
| release 불변성 | 같은 SHA 네 image digest 비교, base digest pin, `infra/production/**` Git diff 선검증 |
| MySQL 보존 rollback | 고정 named volume, schema 복원·volume 삭제 금지, local sentinel 유지 검증 |
| 실제 운영 검증 구분 | 사용자 제공 증거와 저장소 자동 검증을 구분하고 미확정 항목을 별도 기록 |

## 주요 결과

- Backend와 Frontend image는 동일한 40자 `${{ github.sha }}` tag와 revision label을 사용한다.
- workflow는 `main`에서만 게시하고 최소 `GITHUB_TOKEN` 권한만 사용한다.
- Compose는 proxy의 HTTP만 외부 bridge에 연결하고 app·data network는 internal로 분리했다.
- MySQL, Backend, Frontend, Nginx에 health, `unless-stopped`, log rotation, memory·CPU·PID 제한을 적용했다.
- runtime bundle은 MySQL과 Backend 파일을 분리해 Backend에 root password를 전달하지 않는다.
- 새 runtime bundle 게시 뒤 관리 경로로 검증된 직전 평문 bundle을 제거한다.
- runtime materialize는 `flock`으로 동시 writer를 거부해 평문 orphan bundle 경쟁을 차단한다.
- deploy는 현재 정상 release의 복귀 가능성을 먼저 검증하고 대상 실패 시 이전 SHA를 자동 복구한다.
- 각 HTTP smoke 실패는 즉시 실패를 반환하고 성공 state 기록 전 이전 SHA 복귀 또는 첫 배포 application 중지를 수행한다.
- 같은 SHA의 네 image digest drift는 기존 기록을 덮어쓰지 않고 중단하며 MySQL·Nginx는 manifest digest로 고정한다.
- 현재·대상 SHA의 `infra/production/**`가 다르면 image pull과 container·release state·volume 변경 전에 중단한다.
- rollback은 application image만 교체하며 MySQL volume과 schema를 복원·삭제하지 않는다.
- 사용자/Tech Lead가 대상 release 활성화, 동일 `current-sha`, 네 container의 `healthy`와 proxy의 host HTTP `80` 단독 공개를 확인했다.
- EC2 내부 loopback `127.0.0.1`에서 두 endpoint를 확인했고, 별도로 외부 사용자 PC 브라우저에서 EC2 외부 HTTP `80`의 `/products`와 `/api/products`를 확인했다.
- `/pawcycle-commerce/prod` 아래 네 SecureString과 EC2 role의 해당 prefix `ssm:GetParameter` 조회, Secret 값 비출력과 runtime materialize 성공을 확인했다.
- 사용자/Tech Lead가 재부팅 뒤 Docker 자동 시작, 동일 MySQL volume과 동일 SHA, 네 container health와 EC2 내부 loopback 두 smoke의 복구를 확인했다.

## 변경 파일

- `.github/workflows/publish-production-images.yml`
- `.github/workflows/validate-conventions.yml`
- `infra/production/**`
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/runbook/README.md`
- `docs/reports/OPS-010/sre-report.md`
- `docs/handoffs/OPS-010/sre-to-tl.md`

## 결정 상태

- Approved: 현재 사용자 지시의 단일 release, 공개 GHCR SHA tag, SSM runtime file, HTTP `80`, named volume과 rollback 경계
- Deferred: TLS, DNS, backup·restore, CloudWatch, 자동 배포, Blue·Green, Spring Session
- Stop gate: customer managed KMS, migration·DB rollback, 자원 상향과 private registry가 필요할 때 사용자 결정

## API 영향

API 요청·응답, status와 인증·인가 코드는 변경하지 않았다. Nginx는 기존 `/api/**` same-origin proxy 계약을 유지한다.

## DB 영향

제품 migration과 schema는 변경하지 않았다. production Compose는 기존 Flyway 동작과 MySQL named volume을 사용한다. local 검증에서만 validation 전용 probe table을 만들고 검증용 volume과 함께 정리했다.

## 보안 영향

- 실제 Secret과 account 식별자를 저장하지 않았다.
- SSM role 예시는 선택 prefix의 `ssm:GetParameter`만 허용한다.
- runtime 파일은 mode `600`, root 전용 경로, shell trace 비활성, 값 비출력을 강제한다.
- `3306`, `8080`, `3000`은 host에 publish하지 않는다.
- HTTP-only 단계에서도 session cookie `Secure=true`를 낮추지 않았다.
- action은 commit SHA로 pin하고 workflow 권한을 최소화했다.

## 운영 영향

사용자/Tech Lead가 병합 뒤 실제 EC2에서 대상 release를 활성화하고 `/opt/pawcycle/state`의 SHA, Compose health, 공개 port, EC2 내부 loopback smoke, 외부 사용자 PC의 HTTP `80` smoke와 재부팅 복구를 확인했다. 계정 ID·ARN·공인 IP와 Secret 값은 증거에 포함하지 않았다.

## 성능 영향

`t3.small`의 2 vCPU·2 GiB 경계를 고려해 MySQL 640 MiB, Backend 640 MiB, Frontend 256 MiB, Nginx 128 MiB 상한과 총 CPU 2.0을 적용했다. 사용자가 배포 전 단일 시점에 disk 38G 중 3.0G 사용·35G 여유·8%, available memory 약 1.4 GiB와 swap 0B를 확인했다. 이 수치는 해당 시점의 여유만 나타내며 지속 CPU, 부하 중 memory, OOM과 장기 성능은 미확정이고 자원 상향은 승인되지 않았다.

## 실행한 검증

- `docker compose ... config --quiet`: 통과
- `py -3 infra/production/validate-production-contracts.py`: 통과
- `bash -n infra/production/*.sh`: 통과
- Ubuntu 24.04 container의 `test-production-scripts.sh`: 통과
- `/products`·`/api/products` 개별 실패, 자동 복귀와 첫 배포 중단 회귀 test: 통과
- 같은 SHA application digest drift, pinned base digest drift와 release 계약 불일치 회귀 test: 통과
- runtime materialize 동시 실행 fail-closed와 단일 bundle 보존 회귀 test: 통과
- 실제 Docker Hub MySQL·Nginx pinned manifest pull·RepoDigest inspect: 통과
- 실제 production Dockerfile 두 SHA build: 통과
- validation 전용 Compose initial health와 Frontend·Backend HTTP smoke: 통과
- 전체 stop/start 뒤 MySQL sentinel 보존: 통과
- SHA A → SHA B → SHA A rollback 뒤 sentinel 보존: 통과
- 후속 pinned base image 실제 Compose lifecycle 재검증: local Docker Desktop health 지연으로 미통과, 불변성 관련 Compose config·digest inspect와 독립 shell 회귀로 대체 확인
- Backend production Dockerfile `bootJar`: 통과
- Backend local Gradle test/build: Windows Java 25 toolchain 부재로 미실행
- GitHub Repository Validation의 Java 25 Backend test/build와 Frontend 검증: 통과
- Frontend lint/build: 통과
- OPS-010 고위험 산출물 validator와 Repository Validation: 최종 단계에서 실행·확인
- 사용자 제공 운영 검증: 대상 SHA 활성화, 동일 `current-sha`, 네 container `healthy`, proxy만 host HTTP `80` 공개
- 사용자 제공 HTTP 검증: EC2 내부 loopback `127.0.0.1`의 두 endpoint 성공과 외부 사용자 PC 브라우저의 EC2 외부 HTTP `80` `/products`·`/api/products` 성공
- 사용자 제공 SSM 검증: `/pawcycle-commerce/prod` 아래 네 SecureString 존재, 해당 prefix `ssm:GetParameter` 값 비출력 조회와 runtime materialize 성공
- 사용자 제공 자원 검증: 배포 전 단일 시점 disk 38G 중 3.0G 사용·35G 여유·8%, available memory 약 1.4 GiB, swap 0B
- 사용자 제공 재부팅 검증: Docker 자동 시작, 동일 MySQL volume, 동일 SHA, 네 container health와 두 smoke 복구

## 적용 전 검증 (고위험 필수)

- PR #58 병합과 대상 release인 최신 `origin/main` `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6`을 확인했다.
- 기존 `ops/sre` 내용이 `main`에 모두 포함되고 worktree가 깨끗함을 확인한 뒤 병합 완료 브랜치를 삭제하고 최신 `main`에서 새 `ops/sre`를 만들었다.
- 기존 local integration health, port, Dockerfile과 backend/frontend build 계약을 확인했다.
- 실제 AWS·SSM·GHCR·EC2 접근과 Secret 없이도 검증 가능한 범위를 분리했다.

## 적용 후 검증 (고위험 필수)

- Compose JSON에서 proxy 기본 HTTP `80`만 publish하고 내부 service port가 비공개임을 확인했다.
- 실제 local container가 모두 healthy이고 두 HTTP smoke가 성공했다.
- stop/start와 두 SHA image 교체 뒤 validation 전용 MySQL sentinel이 유지됐다.
- SSM 누락 시 기존 runtime symlink가 유지되고 unhealthy·각 HTTP smoke 실패 시 이전 SHA가 복구되는 상태기계 test가 통과했다.
- 같은 SHA digest drift는 기존 `.images`를 보존했고 계약 불일치는 Compose activation과 release state 변경 전에 중단됐다.
- 사용자/Tech Lead의 실제 운영 확인에서 대상 SHA와 `current-sha`가 일치하고 네 container가 healthy였으며 proxy 외 host port는 공개되지 않았다.
- EC2 내부 loopback 두 HTTP smoke와 외부 사용자 PC 브라우저의 EC2 외부 HTTP `80` 두 endpoint가 각각 성공했다.
- 네 SecureString 존재, 해당 prefix `ssm:GetParameter` 값 비출력 조회와 runtime materialize가 성공했다.
- 배포 전 단일 시점의 disk·available memory·swap을 확인했고, 재부팅 뒤 Docker, 동일 MySQL volume, 동일 SHA, health와 내부 loopback smoke가 복구됐다.

## 독립 검증 (고위험 필수)

구현 로직과 분리된 `validate-production-contracts.py`가 Compose JSON, workflow 최소 권한·동일 SHA, base digest pin, 기존 digest 기록 비교, 계약 diff gate, smoke·rollback·Secret·volume 금지 계약을 검사한다. GitHub Repository Validation은 같은 validator와 Linux shell test, Backend·Frontend 회귀 검증을 독립 runner에서 수행했다. 구현자와 분리된 사용자/Tech Lead가 실제 release 활성화, SSM materialize, EC2 내부 loopback·외부 사용자 PC HTTP와 재부팅 복구를 확인했다.

## 실행하지 못한 검증과 이유

- SSM prefix의 네 Parameter와 `ssm:GetParameter` 성공은 확인했지만 IAM 정책 전체의 최소 권한 여부는 독립 검토하지 못함
- GHCR Public visibility·anonymous pull은 별도 비민감 증거가 제공되지 않아 독립 확인하지 못함
- 실제 이전 SHA rollback은 미실행이며 미충족 후속 게이트
- 지속 CPU, 부하 중 memory, OOM과 장기 성능은 측정하지 않아 미확인
- Backend local Gradle test/build: Windows에 Java 25 toolchain이 없어 미실행, Dockerfile `bootJar`와 Java 25 Repository Validation으로 대체 확인
- 운영 DB backup·restore와 schema rollback: 명시적 제외 범위라 미실행
- TLS·login session: TLS와 `443`가 제외돼 미실행

## QA 필요 여부

별도 QA 문서는 작성하지 않는다. 독립 CI와 운영자/Tech Lead의 실제 인프라 gate를 사용했으며, 사용자 제공 증거는 Runbook 체크리스트의 release 활성화와 재부팅 복구 항목을 충족했다.

## QA 문서 경로 또는 생략 사유

제품 기능·API·schema 변경이 없고 고위험 독립 검증을 Repository Validation과 사용자/Tech Lead의 실제 Runbook 실행으로 수행했으므로 별도 QA 산출물은 생략했다.

## AI 리뷰 반영 여부

PR 생성 전 전체 diff를 독립 리뷰 관점으로 검사했다. PR #58의 기존 CodeRabbit 지적과 현재 후속 사용자 승인에서 확인된 release·rollback 불변성 결함은 현재 파일과 실제 계약을 기준으로 선별했으며, 유효 항목을 최소 변경과 회귀 test로 반영했다.

## AI 리뷰 미반영 항목과 이유

- 기존 Repository Validation job은 같은 단계와 후속 validator에서 이미 `python`을 사용하고 최신 runner 검증이 반복 통과하므로 별도 `actions/setup-python` 추가 제안은 미반영했다.
- 정확한 thread 상태와 답변은 GitHub Review Threads를 권위 원본으로 확인한다.

## 적용 방법

사용자/Tech Lead가 `docs/runbook/OPS-010-production-single-release.md`의 release 활성화 → health·port → EC2 내부 loopback smoke → 외부 사용자 PC의 HTTP `80` smoke → reboot 복구 절차를 실행했다. 실제 이전 SHA rollback은 미실행 후속 게이트이며 현재 완료 조건에 포함하지 않는다.

## 복구·롤백 증거 (고위험 필수)

- 상태기계 test에서 두 번째 SHA 성공 뒤 이전 SHA rollback과 unhealthy 대상의 자동 복귀를 확인했다.
- 두 smoke 각각의 실패에서 성공 state가 기록되지 않고, 이전 release 자동 복귀와 첫 배포 application 중지가 수행됨을 확인했다.
- 동일 SHA·base digest drift가 기존 기록을 보존하고 중단되며, 계약 불일치가 container activation 전에 중단됨을 확인했다.
- 실제 Compose 검증에서 SHA A → SHA B → SHA A로 application image를 바꾸고 MySQL sentinel이 유지됨을 확인했다.
- Nginx 강제 재생성 보완 뒤 장시간 command가 로컬 10분 상한에 도달했지만, 같은 run의 최종 container가 모두 healthy이고 두 smoke와 MySQL sentinel이 통과함을 후속 확인했다.
- production deploy·rollback 경로에는 volume 삭제와 schema 복원 명령이 없다.
- 첫 배포 실패 시 application service만 정지하고 MySQL과 named volume을 보존한다.
- 실제 재부팅 뒤 동일 MySQL volume과 동일 SHA, 네 container health와 두 smoke가 복구됨을 사용자/Tech Lead가 확인했다.

## 위험과 제한

- 후속 실제 lifecycle 재검증은 첫 실행에서 local MySQL 초기화가 기존 health 구간을 넘겼고, 동일 조건 재시도에서는 MySQL이 healthy가 된 뒤 Docker Desktop의 장시간 CPU 지연으로 Backend·Frontend가 기존 health 한도를 넘겨 미통과했다. 이번 후속 변경으로 health 값을 바꾸거나 `t3.small` 자원 결정을 추론하지 않으며, 불변성 경로는 독립 shell 상태기계와 Compose config·pinned digest 검사로 검증한다.
- 첫 local HTTP 검증은 proxy가 internal network에만 있어 host port가 닫힌 결함을 발견했고, proxy 전용 edge bridge를 분리해 해결했다.
- 다음 local run에서 Backend health가 한 번 일시 실패했으나 진단 로그를 추가한 동일 구성 재검증은 전체 lifecycle을 통과했다. 실제 `t3.small`의 배포 전 단일 시점 memory·disk·swap은 확인했지만 부하 중 memory와 OOM은 계속 확인해야 한다.
- Nginx 강제 재생성 뒤 최종 장시간 run은 command timeout 후 같은 container의 health·smoke·sentinel을 확인해 성공을 확정했으며 검증용 자원만 정리했다.
- HTTP `80`만 제공하므로 secure session cookie 기반 login 운영 검증은 TLS 전까지 완료할 수 없다.
- rollback은 DB schema를 되돌리지 않으므로 새 migration이 있으면 이 절차를 사용하면 안 된다.

## 남은 위험

단일 시점의 disk·available memory·swap과 SSM prefix 조회 성공은 확인했지만 지속 CPU, 부하 중 memory, OOM, 장기 성능과 IAM 정책 전체의 최소 권한은 미확정이다. GHCR 공개 anonymous pull과 실제 이전 SHA rollback도 별도 증거가 없으며 rollback은 미충족 후속 게이트다. backup·restore가 없으므로 단일 EC2·EBS 장애에 대한 데이터 복구는 OPS-010으로 보장하지 않으며 HTTP `80`만 제공하므로 전송 보안과 로그인 운영 검증은 OPS-011 전까지 보장하지 않는다.

## 다음 작업

실제 release 활성화, SSM materialize, EC2 내부 loopback·외부 사용자 PC HTTP와 재부팅 복구는 확인됐다. 실제 이전 SHA rollback은 완료 조건이 아닌 미충족 후속 게이트로 남긴다. 지속 자원 측정, TLS·DNS, backup·restore 또는 Blue·Green은 필요할 때 별도 작업과 승인을 사용한다.

## Git 결과

- 초기 구현 commit: `a473b6abd62e915f39ebc86b58f4acd0764bd4ff`
- 제목: `feat(sre): OPS-010 운영 단일 release 배포 기반 구성`
- 리뷰 후속 commit: `29f8579`, 배포 진단과 이전 Secret bundle 정리 보완
- release 불변성 후속 commit: `d096e17`, smoke·digest·base image·계약 gate 보완
- PR #59 운영 실행 결과·Runbook commit: `0240d7e`, 세 문서의 사용자 증거와 권한 경계 명령 보완
- PR #59 보고서 상태 commit: `8206bc5`, SRE 보고서의 Git·PR 결과 기록
- 원격: 최신 `main`에서 재생성한 `origin/ops/sre` push 완료

## PR 결과

- 초기 구현 PR: `#58`, `main` 병합 완료
- 현재 후속 PR: `#59`, `main` 대상
- URL: `https://github.com/guseoh/pawcycle-commerce/pull/59`
- 초기 구현 PR #58 최종 head의 Repository Validation: 통과
- PR #59의 제목·본문·head/base와 현재 review·check·상태: GitHub를 권위 원본으로 확인
- AI review: 정확한 comment와 thread 상태는 GitHub Review Threads를 권위 원본으로 확인
- 자동 병합: 비활성
