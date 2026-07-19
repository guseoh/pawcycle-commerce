# OPS-009 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-009
- 작업 등급: 고위험
- 역할: Platform/SRE
- 기능군: DEPLOY-001 AWS 운영 기반

## 작업 목적

사용자가 병합된 `main`을 기준으로 AWS Budget, 최소 권한 EC2 role, Security Group, EC2·암호화 EBS, Elastic IP, SSM과 Docker 기반을 단계별로 생성·검증·정리할 수 있는 Runbook과 적용 전 게이트를 제공한다. 이 작업은 실제 AWS 리소스를 변경하지 않는다.

## 입력 문서

- 루트 `AGENTS.md`, `infra/AGENTS.md`
- `docs/roles/platform-sre.md`, Platform/SRE Skill
- `docs/runbook/README.md`, `docs/runbook/lean-harness.md`
- 기존 local integration Compose·Nginx·smoke 관례
- AWS Budgets, IAM, Systems Manager, EC2, EBS, Elastic IP 공식 문서
- Docker Engine과 Compose 공식 문서

## 승인 입력

- Region `ap-northeast-2`, Ubuntu Server 24.04 LTS, `t3.small`
- 암호화된 gp3 40 GiB, Elastic IP
- SSM 기본 접속과 사용자 IP 제한 비상 SSH
- 같은 EC2의 MySQL, RDS 제외
- Gross 30 USD·Net 1 USD Budget과 실제 결제 0 USD 목표
- AWS 생성·Secret 입력·서버 접속은 PR 병합 후 사용자가 직접 수행
- 첫 배포는 수동 단일 release이며 production 배포 구현은 이번 범위에서 제외

## 명시적 승인 근거 (고위험 필수)

사용자가 OPS-009 준비 게이트에서 운영 입력 18개를 확정했고, 최신 `main` 기반 `ops/sre` 재생성·게시를 별도로 승인했다. 이어진 OPS-009 요청에서 Runbook, 적용 전 게이트, 보고서, 인수인계와 PR까지의 문서 작업을 명시적으로 승인했으며 실제 AWS 실행은 금지했다.

## 변경 범위

- OPS-009 AWS 운영 기반 Runbook과 Runbook 색인
- Budget·IAM·Security Group·EC2/EBS/EIP·SSM·Docker 실행 및 검증 절차
- 실패 중단, 역순 정리, 비민감 증거 형식
- Platform/SRE 작업 보고서와 사용자/Tech Lead 인수인계

## 변경하지 않은 범위

- AWS 리소스와 AWS 계정 설정
- production Compose, 배포 script, GHCR workflow
- MySQL·TLS·DuckDNS·SSM Parameter Store·CloudWatch·S3 구성
- Blue·Green, Spring Session JDBC, 제품 코드, API, DB schema
- 준비 과정의 비추적 PowerShell wrapper와 다른 역할 브랜치

## 인수 조건 매핑

| 인수 조건 | 결과 |
| --- | --- |
| 생성 전 게이트와 단계별 성공·실패 연결 | 각 실행 단계에 성공 기준과 중단·정리를 연결 |
| 최소 IAM과 미래 권한 경계 | SSM Core만 현재 연결하고 S3·CloudWatch는 별도 승인 경계로 분리 |
| 공개·비공개 port와 비상 SSH | 80·443만 공개, 22 임시 단일 IP, 3306·8080·3000 비공개 |
| EBS·EIP·Docker 복구 확인 | EBS 보존, EIP 비용·Release 위험, 재부팅과 stop/start 검증 포함 |
| 실제 AWS 미실행 구분 | 모든 AWS 증거는 사용자 실행 후 기록하며 현재는 미실행으로 명시 |

## 주요 결과

- AWS Budget을 Gross와 Net으로 분리하고 Credits·Refunds 포함 여부와 알림을 명시했다.
- SSM instance role에 현재 필요한 관리형 정책 하나만 허용하고 미래 S3·CloudWatch 권한을 분리했다.
- SSM 우선, SSH 기본 폐쇄, 내부 서비스 port 비공개라는 접속 경계를 고정했다.
- EBS 보존과 Elastic IP Release의 비가역 위험을 역순 정리 절차에 반영했다.
- production 배포로 넘어가기 전 stop/start와 Docker 자동 시작 게이트를 추가했다.

## 변경 파일

- `docs/runbook/OPS-009-aws-operations-foundation.md`
- `docs/runbook/README.md`
- `docs/reports/OPS-009/sre-report.md`
- `docs/handoffs/OPS-009/sre-to-tl.md`

## 결정 상태

- 승인된 AWS 기반값은 Runbook에 반영했다.
- VPC 신규 설계, 고객 관리형 KMS key, 미래 S3·CloudWatch 권한은 승인 입력 밖이므로 중단 또는 후속 작업으로 분리했다.
- Budget 알림은 강제 차단이 아니라 관측 게이트로 다룬다.

## API 영향

없음. API와 외부 계약을 변경하지 않았다.

## DB 영향

없음. MySQL 실행과 DB schema를 변경하지 않았다.

## 보안 영향

- 실제 Secret, 계정 ID, ARN, 공인 IP, 이메일을 기록하지 않았다.
- SSM 기본 접속과 IMDSv2를 사용하고 SSH는 임시 단일 사용자 IP로 제한했다.
- 현재 instance role은 SSM용 정책 하나만 허용하고 광범위 S3·CloudWatch 권한을 배제했다.
- 3306과 내부 서비스 port를 외부에 공개하지 않는다.

## 운영 영향

문서가 병합된 뒤 사용자가 AWS Console에서 실행할 때만 비용과 리소스 상태가 변한다. EBS와 public IPv4는 instance 중지 또는 분리 뒤에도 비용이 발생할 수 있으며 Budget 알림은 지출을 차단하지 않는다.

## 성능 영향

없음. `t3.small`은 승인된 시작값만 문서화했고 실제 workload나 자원 사용량을 측정하지 않았다. Blue·Green은 단일 release 후 측정 게이트로 보류했다.

## 실행한 검증

| 검증 | 결과 |
| --- | --- |
| 관련 Markdown 경로와 민감 패턴 정적 확인 | 통과 |
| `py -3 -m py_compile` validator 문법 검사 | 통과 |
| `py -3 scripts/test_validate_task_artifacts.py` 전체 37개 회귀 테스트 | 통과 |
| `py -3 scripts/test_validate_pr_body_encoding.py` 전체 12개 회귀 테스트 | 통과 |
| `py -3 scripts/validate-task-artifacts.py --task-id OPS-009 --task-grade 고위험` | 통과 |
| commit 제목 검사와 `git diff --check` | 통과 |
| GitHub Repository Validation | push와 PR 생성 뒤 GitHub Checks에서 확인 |

## 적용 전 검증 (고위험 필수)

- `ops/sre`가 최신 `main`에서 시작했고 작업 전 worktree가 깨끗함을 확인했다.
- 기존 local integration 파일은 개발·검증용이며 production 배포 구현이 아님을 확인했다.
- 기존 AWS Runbook이나 OPS-009 산출물과 충돌하지 않음을 확인했다.
- 실제 AWS Console·CLI, Secret 조회와 서버 접속을 수행하지 않았다.

## 적용 후 검증 (고위험 필수)

- 변경된 문서만 대상으로 구조, 승인값, 공개 port, 미실행 표기와 역순 정리를 검토했다.
- 저장소 validator로 고위험 보고서와 필수 인수인계를 검사해 통과했다.
- 실제 AWS 적용 후 검증은 Runbook의 성공 확인표에 따라 사용자가 수행하며 현재 통과로 기록하지 않는다.

## 독립 검증 (고위험 필수)

- validator 회귀 테스트와 GitHub의 Repository Validation을 독립 검증 경로로 사용한다.
- PR 병합 전 사용자/Tech Lead가 Runbook의 비용, IAM, 네트워크, 복구 경계를 검토한다.
- 동적 check 상태를 문서에 복사하지 않고 GitHub Checks를 권위 원본으로 둔다.

## 실행하지 못한 검증과 이유

- AWS Budget, IAM, Security Group, EC2·EBS, Elastic IP, SSM, Docker, stop/start 검증: 실제 AWS 변경은 PR 병합 뒤 사용자가 수행하도록 승인돼 있어 미실행했다.
- production deployment와 rollback: production Compose·script·workflow가 제외 범위여서 미실행했다.
- 비용 0 USD 확인: 리소스를 생성하지 않았고 AWS 비용 데이터는 사용자 계정에서만 확인할 수 있어 미실행했다.

## QA 필요 여부

별도 제품 QA는 필요하지 않다. 다만 고위험 운영 문서이므로 사용자/Tech Lead의 독립 검토와 GitHub Repository Validation 통과가 필요하다.

## QA 문서 경로 또는 생략 사유

제품 동작, API, DB, UI 변경이 없는 사용자 실행용 운영 Runbook이므로 별도 QA 문서는 생략한다. 운영 검증 절차와 증거 형식은 `docs/runbook/OPS-009-aws-operations-foundation.md`에 포함했다.

## AI 리뷰 반영 여부

PR의 AI review thread는 GitHub Review Threads를 권위 원본으로 검토하고 유효한 운영 안전 지적만 반영한다.

## AI 리뷰 미반영 항목과 이유

현재 보고서에는 동적 review 개수나 상태를 고정하지 않는다. 미반영 판단이 생기면 해당 GitHub Review Thread에 근거를 남긴다.

## 적용 방법

1. 사용자가 PR을 검토하고 병합한다.
2. 병합된 최신 `main`에서 OPS-009 Runbook의 생성 전 게이트를 확인한다.
3. 사용자가 AWS Console에서 한 단계씩 실행하고 성공 기준과 비민감 증거를 기록한다.
4. 실패하면 다음 단계로 진행하지 않고 역순 정리와 에스컬레이션을 수행한다.

## 복구·롤백 증거 (고위험 필수)

- 저장소 변경은 병합 전 PR을 닫거나 병합 후 일반 revert PR로 복구할 수 있으며 history rewrite가 필요 없다.
- 이번 작업은 AWS 리소스를 생성하지 않아 현재 제거할 외부 리소스가 없다.
- 향후 사용자 실행 시 EBS 보존, Elastic IP Disassociate·Release, instance termination과 role·Security Group 정리를 Runbook의 역순 절차로 검증한다.
- EBS 삭제와 Elastic IP Release는 복구가 보장되지 않으므로 사용자 확인 전 실행하지 않는다.

## 위험과 제한

- Budget 알림은 비용 차단 장치가 아니며 갱신 지연이 있을 수 있다.
- AWS Console UI, AMI ID와 가격은 바뀔 수 있으므로 실행 시 공식 화면을 다시 확인해야 한다.
- 기존 VPC와 public subnet이 없으면 승인되지 않은 네트워크 설계가 필요하므로 중단한다.
- public IPv4와 보존 EBS는 사용하지 않는 동안에도 비용이 발생할 수 있다.

## 남은 위험

- 실제 AWS 계정의 권한, 크레딧 적용 범위와 quota는 문서 작성 시 확인하지 않았다.
- production Compose, TLS, Secret, backup·restore, CloudWatch와 deployment rollback은 후속 승인 없이는 준비되지 않는다.
- 실제 단일 release의 CPU, memory, disk 측정 전에는 Blue·Green 수용 가능성을 판단할 수 없다.

## 다음 작업

- 사용자/Tech Lead가 PR과 독립 검증 결과를 확인한 뒤 병합 여부를 결정한다.
- 병합 후 사용자가 Runbook의 생성 전 게이트부터 실행하고 비민감 결과를 제공한다.
- OPS-009 실제 기반 검증이 끝난 뒤 production Compose·GHCR SHA image·수동 단일 release를 별도 승인 작업으로 정의한다.

## Git 결과

변경은 `ops/sre`에서만 commit·push하고 `main`에는 PR로만 반영한다. 정확한 commit과 branch 상태는 Git 기록을 권위 원본으로 삼고 보고서에 중간 SHA를 고정하지 않는다.

## PR 결과

`main` 대상 PR을 생성하고 자동 병합하지 않는다. Draft·check·review 상태와 최종 변경은 GitHub PR, GitHub Checks와 GitHub Review Threads를 권위 원본으로 삼는다.
