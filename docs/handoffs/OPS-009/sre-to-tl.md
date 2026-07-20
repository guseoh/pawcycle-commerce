# OPS-009 Platform/SRE → 사용자/Tech Lead 인수인계

## 작업 정보

- 작업 ID: OPS-009
- 작업 등급: 고위험
- 기능군: DEPLOY-001 AWS 운영 기반

## 전달 목적

사용자/Tech Lead가 OPS-009 Runbook의 비용, IAM, 네트워크, 복구 경계를 검토하고, 병합 뒤 실제 운영자로서 AWS 기반을 안전하게 생성·검증·정리할 수 있도록 전달한다.

## 대상 역할 또는 운영자

- 사용자/Tech Lead: PR 승인과 AWS 비용·인프라 결정
- 실제 운영자: 병합된 `main` 기준 Runbook 실행과 비민감 증거 기록

현재는 사용자가 두 역할을 수행한다.

## 입력 문서

- `docs/runbook/OPS-009-aws-operations-foundation.md`
- `docs/reports/OPS-009/sre-report.md`
- 루트 `AGENTS.md`, `infra/AGENTS.md`
- `docs/roles/platform-sre.md`, Platform/SRE Skill
- Runbook의 AWS·Docker 공식 근거 링크
- 현재 OPS-009와 OPS-009 후속 수정 사용자 지시: 명시적 승인 출처이며 별도 PR·대화 URL은 제공되지 않아 링크를 만들지 않음

## 완료된 작업

- 생성 전 비용·권한·네트워크·복구 게이트 정의
- Budget부터 Docker와 stop/start까지의 사용자 실행 순서 정의
- 단계별 성공 기준, 실패 중단, 역순 정리 연결
- 실제 AWS 미실행과 비민감 증거 형식 분리
- Credit specification `standard`, 리소스 식별 계약과 IAM Console same-name 정리 경계 반영
- Docker 공식 저장소·Docker Hub outbound 게이트와 Budget 비민감 증거 규칙 일치
- PR #57의 미해결 Codex·CodeRabbit 스레드 7개를 재검증하고 유효 지적 반영

## 사용 가능한 결과

- `docs/runbook/OPS-009-aws-operations-foundation.md`: 적용·검증·정리의 실행 원본
- `docs/reports/OPS-009/sre-report.md`: 승인, 범위, 검증, 위험 기록
- GitHub Checks: 저장소 정적 검증의 권위 원본
- GitHub Review Threads: 리뷰 판단과 해결 상태의 권위 원본

## 관련 파일

- `docs/runbook/README.md`
- `docs/runbook/OPS-009-aws-operations-foundation.md`
- `docs/reports/OPS-009/sre-report.md`
- `docs/handoffs/OPS-009/sre-to-tl.md`

## 인수 조건과 추적성

| 확인 대상 | 인수 기준 |
| --- | --- |
| 비용 | Gross 30 USD·Net 1 USD, Credits·Refunds, 알림 기준과 만료일 검토 여부를 보존하고 이메일·잔액·계정 ID는 제외 |
| IAM | 현재는 EC2 trust와 `AmazonSSMManagedInstanceCore` 하나뿐임을 확인 |
| Network | 80·443 공개, 22·3306·8080·3000 기본 비공개, `download.docker.com`·Docker Hub outbound 확인 |
| Compute cost | `t3.small` Credit specification `standard`와 CPU credit 고갈 시 baseline 제한 확인 |
| Identification | 이름·지원 tag·저장소 외부 매핑을 생성과 역순 정리에 동일하게 사용 |
| Recovery | EBS 보존, Elastic IP 비용·Release 위험, 역순 정리 확인 |
| Execution | 실제 AWS는 병합 후 사용자만 단계별 실행 |

## 확정된 결정

- Region, EC2·EBS, Elastic IP, SSM 우선 접속, 비상 SSH 경계
- `t3.small` Credit specification `standard`
- Budget·role/profile·Security Group·EC2·EBS·EIP의 이름·지원 tag·저장소 외부 매핑 계약
- IAM Console에서 만든 same-name role/profile만 Console로 정리하고 예외 시 중단
- 같은 EC2의 MySQL과 RDS 제외
- Gross/Net Budget, 실제 결제 0 USD 목표
- production 배포 구현과 실제 AWS 실행은 현재 PR 제외

## 미확정 결정

- 실제 계정의 기존 VPC/public subnet 사용 가능 여부
- 실행 시점의 AMI ID, 가격, credits 적용 범위와 quota
- production Compose, TLS·DuckDNS, Secret materialize와 backup·restore 세부 설계
- 단일 release 측정 이후 Blue·Green 가능 여부

## 승인 필요 항목

- 이 Runbook을 `main`에 병합할지 여부
- 병합 뒤 사용자가 AWS 생성 전 게이트를 통과했는지 여부
- 실제 EBS 삭제, Elastic IP Release, Snapshot 생성이 필요한 경우의 개별 승인
- OPS-009 기반 검증 뒤 후속 production 배포 작업의 범위

## 소비자 입력

실제 운영자는 다음 입력을 저장소 밖에서 준비한다.

- MFA가 적용된 AWS Console 세션
- Billing·IAM·EC2·Systems Manager에 필요한 권한
- Budget 알림 수신 이메일
- 기존 VPC와 public subnet 확인 결과
- 리소스 논리 이름, Console 실제 식별자·연결 관계와 생성·정리 상태를 보관할 저장소 외부 비공개 운영 기록
- 비상 SSH가 승인된 경우에만 현재 사용자 공인 IP와 안전하게 보관한 private key

계정 ID, ARN, 실제 IP, 이메일, key와 Secret은 저장소에 제공하지 않는다.

## 지켜야 할 규칙

- 병합된 최신 `main`의 Runbook만 사용한다.
- 한 단계의 성공을 확인한 뒤 다음 단계로 이동한다.
- 생성과 정리에 같은 이름·지원 tag·외부 매핑 계약을 사용하고 불일치하면 삭제하지 않는다.
- IAM role/profile 이름이 다르거나 Console 삭제 뒤 profile이 남으면 CLI/API로 보정하지 않고 중단한다.
- SSM 실패를 이유로 SSH를 넓게 열지 않는다.
- 실제 AWS 검증을 수행하지 않았으면 통과로 기록하지 않는다.
- EBS 삭제와 Elastic IP Release는 복구가 보장되지 않으므로 사용자 판단 전 실행하지 않는다.
- production 배포, AWS 추가 서비스와 권한 확대는 별도 승인 전 수행하지 않는다.

## 적용·실행 방법

1. PR과 GitHub Checks, Review Threads를 검토한다.
2. 병합 뒤 `docs/runbook/OPS-009-aws-operations-foundation.md`의 생성 전 게이트를 완료한다.
3. Budget → IAM → Security Group → EC2/EBS → EIP → SSM → Docker → reboot → stop/start 순서로 실행한다.
4. 단계마다 비민감 성공 증거 또는 실패·정리 결과를 기록한다.
5. 실패하면 즉시 다음 단계로의 진행을 멈추고 Runbook의 역순 정리를 사용한다.

## 소비자 검증 포인트

- Budget 두 개가 Gross/Net 비용 유형과 알림을 정확히 반영하는가?
- `t3.small`의 Credit specification이 `standard`이며 증거에 기록됐는가?
- role이 사람용 자격 증명이나 광범위 S3·CloudWatch 권한을 포함하지 않는가?
- role/profile이 same-name 쌍이고 Console 정리 뒤 둘 다 제거됐는가?
- Security Group에서 SSH와 내부 port가 기본 폐쇄됐는가?
- Budget부터 EIP까지 이름·지원 tag·외부 매핑이 생성과 정리에서 일치하는가?
- EBS가 암호화·보존되고 EIP 비용과 Release 위험이 이해됐는가?
- SSM 세션, Docker 자동 시작, stop/start 뒤 복구가 실제로 확인됐는가?
- 스크린샷과 기록에서 계정 ID, ARN, IP, 이메일, Secret이 제거됐는가?

## QA 필요 여부

제품 QA는 필요하지 않다. 고위험 운영 변경이므로 사용자/Tech Lead 독립 검토와 실제 운영자의 단계별 적용 검증이 필요하다.

## AI 리뷰에서 남은 확인 항목

후속 수정에서 확인한 Codex 1개와 CodeRabbit 6개 스레드는 모두 유효로 판정해 문서에 반영했다. 스레드별 판단 댓글, 최신 해결 상태와 추가 review 발생 여부는 GitHub Review Threads에서 확인한다.

## 알려진 위험

- Budget은 비용을 차단하지 않고 비용 데이터 반영이 지연될 수 있다.
- EC2 Stop 이후에도 EBS와 public IPv4 비용이 남을 수 있다.
- Elastic IP Release와 EBS 삭제는 동일 자원 복구가 보장되지 않는다.
- AWS UI, AMI와 가격은 실행 시점에 달라질 수 있다.
- Credit specification `standard`는 surplus credit 추가 과금을 막지만 credit 고갈 시 CPU를 baseline으로 제한할 수 있다.

## 남은 위험과 주의 사항

- 실제 AWS 계정과 credits, VPC, quota는 아직 검증하지 않았다.
- production 배포, TLS, Secret, monitoring, backup·restore가 준비되지 않아 이 기반만으로 서비스를 공개하면 안 된다.
- 실제 resource identifier를 repository나 PR에 붙이지 않는다.

## 다음 권장 작업

- PR #57의 후속 commit, 해결된 review thread와 Repository Validation을 사용자가 확인한 뒤 병합 여부를 결정한다.
- 병합 뒤 사용자가 OPS-009를 실행하고 비민감 결과와 실패 기록을 제공한다.
- 기반 검증을 통과한 뒤 production Compose·GHCR SHA image·수동 단일 release와 rollback을 별도 작업으로 승인한다.
- 단일 release 자원 측정 뒤에만 Blue·Green 작업을 검토한다.

## 완료 조건

- PR의 Repository Validation과 사용자/Tech Lead 검토가 끝났다.
- 병합 뒤 실제 운영자가 생성 전 게이트를 다시 확인할 수 있다.
- 실제 적용 결과를 미실행·성공·실패로 구분하고 정리 증거를 남길 수 있다.

## 중단 조건

- 승인되지 않은 AWS 서비스, 비용, 권한 또는 네트워크 설계가 필요하다.
- Secret이나 계정 식별자를 저장소에 기록해야 한다.
- SSM 없이 광범위 SSH 공개가 필요하다.
- EBS·EIP의 안전 정리나 rollback을 확인할 수 없다.
- production 배포 구현 또는 제품 코드 변경이 필요하다.
