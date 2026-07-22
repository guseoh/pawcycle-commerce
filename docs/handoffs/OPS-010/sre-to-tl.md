# OPS-010 Platform/SRE → 사용자·Tech Lead 인수인계

## 작업 정보

- 작업 ID: OPS-010
- 작업 등급: 고위험

## 전달 목적

DEPLOY-002 단일 release 기반을 사용자와 Tech Lead가 병합 전 검토하고, 병합 뒤 실제 AWS·GHCR·SSM·EC2에 안전하게 적용·검증·rollback할 수 있도록 전달한다.

## 대상 역할 또는 운영자

- Product Owner이자 Tech Lead인 사용자
- 병합 뒤 AWS와 EC2 명령을 직접 수행하는 운영자

## 입력 문서

- 현재 OPS-010 사용자 승인
- `docs/runbook/OPS-009-aws-operations-foundation.md`
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/reports/OPS-010/sre-report.md`

## 완료된 작업

- production Compose·Nginx와 Backend·Frontend Dockerfile
- 동일 `github.sha` 공개 GHCR build workflow
- SSM SecureString runtime bundle materialize
- revision·digest preflight, health·smoke, 자동 복귀 deploy
- 이전 SHA rollback과 MySQL named volume 보존 경계
- 정적 계약, shell 상태기계와 실제 local Compose lifecycle 검증

## 사용 가능한 결과

- 외부 port는 Nginx HTTP `80` 하나이고 내부 service port는 publish하지 않는다.
- MySQL volume 이름은 `pawcycle-production-mysql-data`로 고정된다.
- Backend와 Frontend는 하나의 40자 release SHA로만 함께 전환된다.
- Secret 파일은 저장소 밖 root 전용 runtime bundle이며 mode `600`이다.
- materialize 성공 뒤 관리 경로로 검증된 직전 평문 bundle은 제거된다.
- 현재·이전 SHA와 image digest는 Secret 없이 state directory에 남는다.

## 관련 파일

- `.github/workflows/publish-production-images.yml`
- `infra/production/**`
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/reports/OPS-010/sre-report.md`

## 인수 조건과 추적성

| 검토 항목 | 확인 위치 |
| --- | --- |
| workflow 최소 권한·동일 SHA | publish workflow와 contract validator |
| GHCR digest preflight | `release-common.sh`와 Runbook preflight |
| Secret 누락 fail-closed·mode `600` | materialize script와 shell test |
| 내부 port 비공개 | Compose와 JSON contract validator |
| health·smoke·restart | Compose lifecycle test와 deploy script |
| 이전 SHA 복구 | deploy·rollback state-machine test |
| MySQL 보존 | named volume, sentinel stop/start·rollback test |

## 확정된 결정

- 첫 배포는 수동 단일 release다.
- image publication만 GitHub Actions가 담당하고 서버 배포는 자동화하지 않는다.
- Backend·Frontend는 동일 commit SHA tag를 사용하고 `latest`는 금지한다.
- MySQL은 같은 EC2의 named volume을 사용한다.
- HTTP `80`만 외부 공개하고 session cookie 보안은 낮추지 않는다.
- rollback은 application image만 되돌리고 DB schema·data는 복원하지 않는다.

## 확인된 운영 결과

- 사용자/Tech Lead가 release `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6`의 활성화, 동일 `current-sha`, 네 container health, proxy의 host HTTP `80` 단독 공개와 내부·외부 두 HTTP smoke를 확인했다.
- 재부팅 뒤 Docker 자동 시작, 동일 MySQL volume, 동일 SHA, health와 smoke 복구를 확인했다.

## 미확정 결정

- 실제 SSM prefix와 parameter 값
- GHCR package Public 설정 결과와 실제 image digest
- EC2의 실제 memory·disk·CPU 여유
- TLS·DNS, backup·restore와 이후 Blue·Green

## 승인 필요 항목

현재 승인 밖인 customer managed KMS, private registry, DB migration·rollback, 자원 상향, TLS, backup·restore와 자동 배포는 별도 결정이 필요하다.

## 소비자 입력

- 병합된 최신 `main`의 40자 SHA
- 공개 GHCR Backend·Frontend repository 경로
- 사용자 선택 SSM prefix
- 저장소 밖 `/opt/pawcycle/runtime`, `/opt/pawcycle/state`
- OPS-009 실제 gate 통과 증거

실제 Secret, account ID, 전체 ARN, 사용자 IP와 이메일은 저장소나 PR에 입력하지 않는다.

## 지켜야 할 규칙

- AWS·GHCR·EC2 작업은 병합 뒤 사용자만 수행한다.
- 두 image SHA가 다르거나 digest·revision label이 없으면 중단한다.
- `3306`, `8080`, `3000` Security Group 규칙과 Compose host publish를 추가하지 않는다.
- runtime env를 `cat`, `env`, shell trace 또는 완료 보고로 출력하지 않는다.
- `docker compose down --volumes`, volume 삭제와 schema 복원을 rollback에 사용하지 않는다.
- GitHub check·review thread의 동적 개수는 저장소 문서에 고정하지 않는다.

## 적용·실행 방법

Runbook의 순서를 변경하지 않는다.

1. OPS-009와 OPS-010 생성 전 gate를 확인한다.
2. 병합 SHA의 image workflow와 두 Public package를 확인한다.
3. 선택 prefix에 네 SecureString과 최소 `ssm:GetParameter` role 권한을 준비한다.
4. SSM Session Manager에서 runtime bundle을 materialize하고 mode `600`을 확인한다.
5. `deploy.sh`에 동일 SHA와 두 GHCR repository를 전달한다.
6. health, HTTP smoke, host port, digest, state와 volume을 확인한다.
7. stop/start와 EC2 재부팅 복구를 확인한다.
8. 이전 SHA rollback 뒤 동일 MySQL volume과 application health를 확인한다.

## 소비자 검증 포인트

- workflow 권한이 `contents: read`, `packages: write`뿐인가?
- 두 image tag·revision label·state의 SHA가 같은가?
- GHCR RepoDigest가 두 image 모두 기록됐는가?
- proxy만 HTTP `80`을 publish하는가?
- 네 service가 healthy이고 두 HTTP smoke가 성공하는가?
- runtime file 세 개가 mode `600`이고 Secret이 출력되지 않았는가?
- stop/start, 재부팅과 rollback 뒤 volume 이름과 데이터가 유지되는가?
- 실패 시 이전 정상 SHA가 복구되거나 첫 배포 application service만 정지되는가?

## QA 필요 여부

별도 QA 문서 대신 독립 Repository Validation과 사용자/Tech Lead 운영 gate가 필요하다. 실제 AWS 환경 검증은 자동 검사로 대체할 수 없다.

## AI 리뷰에서 남은 확인 항목

PR 생성 전 `latest`, Secret, workflow 권한, 내부 port publish, volume 삭제, log 무제한, health 대기, SHA 불일치, DB 손상과 동적 GitHub 상태 복제를 자체 검사한다. PR 생성 뒤 정확한 리뷰 상태는 GitHub Review Threads를 권위 원본으로 확인한다.

## 알려진 위험

- 로컬 lifecycle은 통과했지만 실제 `t3.small` resource 검증은 미실행이다.
- HTTP-only 단계에서는 secure session cookie 때문에 login 운영 검증이 제한된다.
- rollback은 DB schema를 복구하지 않는다.
- backup·restore가 아직 없으므로 EBS·DB 손상 복구는 보장하지 않는다.

## 남은 위험과 주의 사항

실제 SSM IAM 최소 권한, 공개 GHCR anonymous pull, 이전 SHA rollback과 자원 사용량은 별도 증거가 없어 통과로 표시하지 않는다. memory 부족이나 migration 차이를 발견하면 배포를 중단한다.

## 다음 권장 작업

단일 release 활성화와 재부팅 복구는 확인됐다. 실제 이전 SHA rollback과 자원 여유를 확인하고, TLS·DNS와 backup·restore는 별도 승인하며 자원 여유가 검증된 뒤 Blue·Green을 검토한다.

## 완료 조건

- 실제 두 image가 동일 병합 SHA와 digest로 확인됨
- 모든 health·HTTP smoke·reboot가 통과함
- runtime file mode와 Secret 비노출이 확인됨
- rollback 뒤 MySQL volume·data와 service가 정상임
- 실제 실행·미실행·실패 기록이 비민감 형식으로 남음

## 중단 조건

- customer managed KMS나 승인 prefix 밖 권한 필요
- 실제 Secret·account 식별자 저장 필요
- 서로 다른 image SHA 또는 digest 부재
- 내부 port 공개 필요
- migration·DB rollback·volume 삭제 필요
- `t3.small` 자원 상향 필요
- destructive Git 또는 자동 병합 필요
