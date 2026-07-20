# OPS-010 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-010
- 작업 등급: 고위험
- 역할: Platform/SRE

## 작업 목적

DEPLOY-002의 production 단일 release 기반을 구현해 병합된 `main`의 Backend·Frontend를 동일 commit SHA로 GHCR에 게시하고, 사용자가 같은 EC2에서 Secret 분리, MySQL 데이터 보존, 상태 확인과 이전 SHA rollback을 수행할 수 있게 한다.

## 입력 문서

- 현재 OPS-010 사용자 지시
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

## 명시적 승인 근거 (고위험 필수)

현재 OPS-010 사용자 지시가 production Compose·Nginx, image workflow, SSM materialize, 수동 deploy·rollback script와 사용자 Runbook 구현을 명시적으로 승인했다. 새 migration, DB rollback, TLS, 자동 배포와 실제 AWS 작업은 승인 범위에서 제외됐다.

## 변경 범위

- production Dockerfile, Compose, Nginx와 비민감 example
- 같은 `github.sha`로 두 `linux/amd64` image를 게시하는 workflow
- fail-closed SSM runtime bundle materialize
- image revision·digest preflight, health·smoke와 자동 복귀 deploy
- 이전 SHA rollback과 MySQL volume 보존 경계
- 정적 계약 validator, shell 상태기계 test와 실제 local Compose lifecycle test
- OPS-010 Runbook, 보고서와 사용자/Tech Lead 인수인계

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
| MySQL 보존 rollback | 고정 named volume, schema 복원·volume 삭제 금지, local sentinel 유지 검증 |
| 실제 운영 검증 구분 | AWS·GHCR·EC2는 미실행으로 기록 |

## 주요 결과

- Backend와 Frontend image는 동일한 40자 `${{ github.sha }}` tag와 revision label을 사용한다.
- workflow는 `main`에서만 게시하고 최소 `GITHUB_TOKEN` 권한만 사용한다.
- Compose는 proxy의 HTTP만 외부 bridge에 연결하고 app·data network는 internal로 분리했다.
- MySQL, Backend, Frontend, Nginx에 health, `unless-stopped`, log rotation, memory·CPU·PID 제한을 적용했다.
- runtime bundle은 MySQL과 Backend 파일을 분리해 Backend에 root password를 전달하지 않는다.
- deploy는 현재 정상 release의 복귀 가능성을 먼저 검증하고 대상 실패 시 이전 SHA를 자동 복구한다.
- rollback은 application image만 교체하며 MySQL volume과 schema를 복원·삭제하지 않는다.

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

사용자는 병합 뒤 GHCR package visibility, SSM parameter·role 권한과 실제 EC2 deploy를 직접 수행한다. 배포 성공 상태는 `/opt/pawcycle/state`의 SHA·digest와 Compose health로 확인한다.

## 성능 영향

`t3.small`의 2 vCPU·2 GiB 경계를 고려해 MySQL 640 MiB, Backend 640 MiB, Frontend 256 MiB, Nginx 128 MiB 상한과 총 CPU 2.0을 적용했다. 실제 EC2의 CPU, memory, disk와 OOM 검증은 미실행이며 자원 상향은 승인되지 않았다.

## 실행한 검증

- `docker compose ... config --quiet`: 통과
- `py -3 infra/production/validate-production-contracts.py`: 통과
- `bash -n infra/production/*.sh`: 통과
- Ubuntu 24.04 container의 `test-production-scripts.sh`: 통과
- 실제 production Dockerfile 두 SHA build: 통과
- validation 전용 Compose initial health와 Frontend·Backend HTTP smoke: 통과
- 전체 stop/start 뒤 MySQL sentinel 보존: 통과
- SHA A → SHA B → SHA A rollback 뒤 sentinel 보존: 통과
- Backend production Dockerfile `bootJar`: 통과
- Backend local Gradle test/build: Windows Java 25 toolchain 부재로 미실행
- GitHub Repository Validation의 Java 25 Backend test/build와 Frontend 검증: 통과
- Frontend lint/build: 통과
- OPS-010 고위험 산출물 validator와 Repository Validation: 최종 단계에서 실행·확인

## 적용 전 검증 (고위험 필수)

- PR #57 병합과 최신 `origin/main`을 확인했다.
- worktree가 깨끗한 상태에서 병합 완료된 기존 `ops/sre`를 삭제하고 최신 `main`에서 새 `ops/sre`를 만들었다.
- 기존 local integration health, port, Dockerfile과 backend/frontend build 계약을 확인했다.
- 실제 AWS·SSM·GHCR·EC2 접근과 Secret 없이도 검증 가능한 범위를 분리했다.

## 적용 후 검증 (고위험 필수)

- Compose JSON에서 proxy 기본 HTTP `80`만 publish하고 내부 service port가 비공개임을 확인했다.
- 실제 local container가 모두 healthy이고 두 HTTP smoke가 성공했다.
- stop/start와 두 SHA image 교체 뒤 validation 전용 MySQL sentinel이 유지됐다.
- SSM 누락 시 기존 runtime symlink가 유지되고 unhealthy image 전환 시 이전 SHA가 복구되는 상태기계 test가 통과했다.

## 독립 검증 (고위험 필수)

구현 로직과 분리된 `validate-production-contracts.py`가 Compose JSON, workflow 최소 권한·동일 SHA, digest·rollback·Secret·volume 금지 계약을 검사한다. GitHub Repository Validation은 같은 validator와 Linux shell test, Backend·Frontend 회귀 검증을 독립 runner에서 수행한다.

## 실행하지 못한 검증과 이유

- 실제 AWS·SSM parameter·IAM: 병합 후 사용자 실행 범위라 미실행
- 실제 GHCR publish·Public visibility·anonymous pull: 병합 후 workflow와 사용자 설정 범위라 미실행
- 실제 EC2 `t3.small` 배포·재부팅·EIP HTTP: AWS 실행 금지로 미실행
- Backend local Gradle test/build: Windows에 Java 25 toolchain이 없어 미실행, Dockerfile `bootJar`와 Java 25 Repository Validation으로 대체 확인
- 운영 DB backup·restore와 schema rollback: 명시적 제외 범위라 미실행
- TLS·login session: TLS와 `443`가 제외돼 미실행

## QA 필요 여부

별도 QA 문서는 작성하지 않는다. 독립 CI와 운영자/Tech Lead의 실제 인프라 gate가 필요하며, 실제 AWS 단계는 Runbook 체크리스트로 사용자가 검증해야 한다.

## QA 문서 경로 또는 생략 사유

제품 기능·API·schema 변경이 없고 고위험 독립 검증을 Repository Validation과 사용자 실행 Runbook으로 수행하므로 별도 QA 산출물은 생략했다.

## AI 리뷰 반영 여부

PR 생성 전 전체 diff를 독립 리뷰 관점으로 검사하고, PR 생성 뒤 Codex Review와 CodeRabbit의 새 지적은 현재 파일과 실제 계약을 기준으로 선별한다.

## AI 리뷰 미반영 항목과 이유

PR 생성 전 미반영 항목 없음. PR 이후 결과는 GitHub Review Threads를 권위 원본으로 확인한다.

## 적용 방법

`docs/runbook/OPS-010-production-single-release.md`의 생성 전 gate → image publish 확인 → SSM materialize → deploy → health·smoke·reboot → rollback 순서를 따른다.

## 복구·롤백 증거 (고위험 필수)

- 상태기계 test에서 두 번째 SHA 성공 뒤 이전 SHA rollback과 unhealthy 대상의 자동 복귀를 확인했다.
- 실제 Compose 검증에서 SHA A → SHA B → SHA A로 application image를 바꾸고 MySQL sentinel이 유지됨을 확인했다.
- Nginx 강제 재생성 보완 뒤 장시간 command가 로컬 10분 상한에 도달했지만, 같은 run의 최종 container가 모두 healthy이고 두 smoke와 MySQL sentinel이 통과함을 후속 확인했다.
- production deploy·rollback 경로에는 volume 삭제와 schema 복원 명령이 없다.
- 첫 배포 실패 시 application service만 정지하고 MySQL과 named volume을 보존한다.

## 위험과 제한

- 첫 local HTTP 검증은 proxy가 internal network에만 있어 host port가 닫힌 결함을 발견했고, proxy 전용 edge bridge를 분리해 해결했다.
- 다음 local run에서 Backend health가 한 번 일시 실패했으나 진단 로그를 추가한 동일 구성 재검증은 전체 lifecycle을 통과했다. 실제 `t3.small`에서는 memory·disk와 OOM을 반드시 확인한다.
- Nginx 강제 재생성 뒤 최종 장시간 run은 command timeout 후 같은 container의 health·smoke·sentinel을 확인해 성공을 확정했으며 검증용 자원만 정리했다.
- HTTP `80`만 제공하므로 secure session cookie 기반 login 운영 검증은 TLS 전까지 완료할 수 없다.
- rollback은 DB schema를 되돌리지 않으므로 새 migration이 있으면 이 절차를 사용하면 안 된다.

## 남은 위험

실제 EC2 자원 여유, GHCR 공개 pull, SSM IAM prefix, 재부팅 복구와 외부 EIP HTTP는 사용자 검증 전 미확정이다. backup·restore가 없으므로 단일 EC2·EBS 장애에 대한 데이터 복구는 OPS-010으로 보장하지 않는다.

## 다음 작업

사용자/Tech Lead가 병합 뒤 Runbook에 따라 실제 OPS-009 gate와 OPS-010 배포를 수행하고 비민감 증거를 확인한다. 단일 release와 자원 검증이 끝난 뒤에만 TLS·DNS, backup·restore 또는 Blue·Green을 별도 승인한다.

## Git 결과

- 초기 구현 commit: `a473b6abd62e915f39ebc86b58f4acd0764bd4ff`
- 제목: `feat(sre): OPS-010 운영 단일 release 배포 기반 구성`
- 원격: `origin/ops/sre` push 완료

## PR 결과

- PR: `#58`, `main` 대상
- URL: `https://github.com/guseoh/pawcycle-commerce/pull/58`
- 초기 구현 head의 Repository Validation: 통과
- AI review: Ready 전환 뒤 확인 예정, 정확한 상태는 GitHub Review Threads가 권위 원본
- 자동 병합: 비활성
