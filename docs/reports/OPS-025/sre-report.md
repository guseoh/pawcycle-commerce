# OPS-025 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-025
- 작업 등급: 고위험
- 역할: Platform/SRE
- 작업 브랜치: `ops/sre`
- 대상 브랜치: `main`

## 작업 목적

OPS-021 실제 Production Control·Application rollback·재배포 결과를 비민감 증거로 기록하고, DB 논리 손상 때 source Production volume을 보존하면서 검증된 backup을 별도 candidate volume에 복원·검증·전환·복귀하는 Actual Production DB restore 계약을 준비한다.

## 입력 문서

- 사용자 제공 2026-07-30 KST OPS-021 Production 실행 결과
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/runbook/OPS-013-production-db-backup-restore.md`
- `docs/runbook/OPS-013-production-db-backup-restore.md`
- `docs/runbook/lean-harness.md`
- 기존 production Compose, backup·restore, deploy·rollback Script와 tests

## 승인 입력

사용자는 OPS-021 비민감 실행 증거 기록과 OPS-025 저장소 Runbook·최소 계약 구현을 승인했다. Actual Production DB restore·훈련과 AWS·Docker·DB·Secret 명령은 별도 고위험 사용자 실행으로 보류했다.

## 명시적 승인 근거

현재 요청에 OPS-025 작업 ID·고위험 등급·Platform/SRE 역할, 포함·제외 범위, restore 불변 조건, 검증·Git·PR 완료 조건과 중단 조건이 명시됐다.

## 변경 범위

- OPS-021 사용자 실행 결과와 역사 보고서·OPS-010 최소 연결
- OPS-025 Actual Production DB 논리 restore Runbook
- OPS-013 사전 `restore-verify` 보호 기록과 production-compatible candidate volume 복원·검증·보존
- `active-mysql-volume` 기반 deploy·rollback 영속 상태와 실제 mount 대조
- 공유 release lock 아래 cutover·source manifest·실패 복귀·명시적 revert
- fake Docker 성공·실패·복귀와 격리 candidate lifecycle
- Repository Validation의 Shell·production 정적 계약 보강

## 변경하지 않은 범위

- Actual Production, AWS, Docker, DB와 Secret 명령 실행
- Actual Production restore·훈련·데이터 변경
- source volume 삭제·초기화·in-place restore
- Flyway history 수정, schema downgrade, raw datadir 복사와 자동 재시도
- 물리 volume·EBS 복구, RPO/RTO, backup schedule·cross-region
- OPS-VERIFY-001 최종 판정, Blue/Green과 PERF-OPS-001
- Backend·Frontend 제품 코드, API와 DB schema

## 주요 결과

- deploy·rollback은 보호된 active volume 상태가 없거나 손상되면 fail-closed하고, Compose 입력과 실행 MySQL mount를 같은 값으로 검증한다.
- candidate 준비는 OPS-013 completion marker·무결성 검증과 동일 backup의 사전 `restore-verify` 보호 기록을 요구한다.
- candidate는 동일 pinned MySQL image, `none` network, host port·source mount 없음과 Production runtime DB identity 계약으로 준비되며 password는 root-only 파일과 MySQL `_FILE` 변수로 주입된다.
- cutover는 공유 `deploy.lock`, 쓰기 중단, source manifest 기록, source MySQL 정지 후에만 active 상태를 바꾼다.
- candidate·source 활성화는 MySQL manifest, Backend·Frontend 시작 후 manifest 재검증을 통과한 뒤에만 Proxy를 열고, cutover 실패 시 같은 Application SHA와 source volume 복귀를 한 번 시도한다.
- 쓰기 중단·MySQL 정지·보호 상태 기록 실패와 source revert 실패도 마지막 정상 volume을 한 번 재활성화하고 즉시 중단한다.
- source·candidate volume과 복구 state는 자동 삭제하지 않는다.

## 변경 파일

- `.github/workflows/validate-conventions.yml`
- `docs/reports/OPS-021/production-execution-report.md`
- `docs/reports/OPS-021/sre-report.md`
- `docs/reports/OPS-025/sre-report.md`
- `docs/handoffs/OPS-025/sre-to-operator.md`
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/runbook/OPS-025-production-db-restore.md`
- `infra/production/db-backup-restore.sh`
- `infra/production/production-db-restore.sh`
- `infra/production/release-common.sh`
- `infra/production/test-db-backup-restore.sh`
- `infra/production/test-production-scripts.sh`
- `infra/production/validate-production-contracts.py`

## API 영향

API 요청·응답과 인증·인가 계약 변경 없음.

## DB 영향

저장소 변경은 DB schema·Flyway·row와 실제 volume을 변경하지 않는다. 후속 승인 실행에서만 논리 dump가 별도 candidate volume에 import된다. source는 in-place 수정하지 않는다.

## 보안 영향

- backup ID는 보호된 runtime state에 SHA-256만 남긴다.
- S3 식별자는 기존 환경 변수 경계를 유지하고 credential은 EC2 role만 사용한다.
- Production runtime DB Secret은 root-only env file에서 root-only 임시 password 파일로 추출한 뒤 MySQL `_FILE` 변수로 candidate Container에 bind되며, Container 환경·CLI 인자·공개 출력·저장소에 값이 기록되지 않는다.
- restore state는 root-only directory의 regular non-symlink mode `600` 계약을 사용한다.

## 운영 영향

새 Control 적용 전 기존 Production MySQL mount를 확인해 `active-mysql-volume`을 한 번 초기화해야 한다. 이후 deploy·rollback은 이 상태가 없으면 실행되지 않는다. Actual cutover에는 명시적 쓰기 중단이 있으며 중단 시간은 아직 측정하지 않았다.

## 실행한 검증

- `bash -n` production 변경 Script·tests: 통과
- `py -3 -m py_compile infra/production/validate-production-contracts.py`: 통과
- `bash infra/production/test-production-scripts.sh`: `OPS-025 production DB restore fake success, failure, cutover, and revert lifecycle tests passed`
- WSL root 격리 `test-db-backup-restore.sh`: `OPS-013 isolated backup and restore plus OPS-025 candidate preservation lifecycle tests passed`
- `py -3 infra/production/validate-production-contracts.py`: OPS-013·OPS-025 계약 통과
- `py -3 scripts/validate-task-artifacts.py --task-id OPS-025 --task-grade 고위험`: 통과
- `git diff --check`: 통과

첫 격리 실행은 비대화형 WSL에서 기존 `sudo`가 credential 입력을 기다려 시간 초과됐다. 정확한 테스트 PID와 fixture를 정리하고 `sudo -n` fail-fast를 추가했다. WSL Docker Desktop client에서 daemon의 `/var/lib/docker`가 host filesystem에 보이지 않는 두 번째 실패는 `local-validation-only`일 때만 격리 work root의 disk 검사를 사용하도록 보정했다. Production 경로의 Docker root fail-closed 계약은 유지했다. 이후 WSL root 재실행은 성공했고 테스트 label Container·volume 부재를 확인했다.

## 적용 전 검증

- 작업 시작 시점 관찰값으로 local `main`, `origin/main`과 승인 기준 `005031a5ad9add7a008f38b52e377812954b6480` 일치. 현재 branch·commit·PR·check 상태는 Git과 GitHub의 동적 상태가 권위 원본
- 작업 트리 clean, 추가 worktree 없음
- 원격 `ops/sre`의 작업 시작 시점 head는 열린 PR이 없고 이전 squash-merge 완료 head임을 GitHub와 commit 관계로 확인
- 최신 `origin/main`에서 로컬 `ops/sre` 재생성
- 기존 OPS-013 completion marker·restore isolation·manifest와 OPS-021 deploy·rollback 상태 계약 분석
- 실제 Production·AWS·DB·Secret 접근 없음

## 적용 후 검증

저장소 변경에 대해 fake Docker에서 기본·candidate active volume의 deploy·rollback 유지, 상태 누락·label mismatch 차단, candidate cutover 성공, source revert 성공, candidate 활성화 실패 후 source 자동 복귀와 source revert 활성화 실패 후 candidate 자동 복귀를 검증했다. 쓰기 중단 실패도 cutover 없이 source Release를 재활성화하는 경계로 검증했다. 실제 Production 적용 후 검증은 별도 사용자 실행 승인 전까지 미실행이다.

## 독립 검증

GitHub Repository Validation의 독립 Linux 환경과 CodeRabbit/Codex AI 리뷰를 PR 병합 gate로 사용한다. 로컬 구현 검증과 원격 CI·AI 리뷰 결과는 구분해 기록하며, PR 생성 전에는 원격 검증을 통과로 기록하지 않는다.

## 복구·롤백 증거

- fake lifecycle에서 candidate 활성화 실패 후 source volume과 기존 Application SHA 복귀 확인
- 명시적 `revert`의 source 활성화 실패 후 candidate volume과 기존 Application SHA 복귀 확인
- candidate와 source 모두 MySQL manifest 및 Backend·Frontend 시작 후 manifest 재검증 전에는 Proxy를 열지 않는 정적 계약 확인
- candidate label mismatch는 서비스 정지 전에 차단
- deploy·rollback 뒤에도 candidate active volume 유지 확인
- source·candidate volume 자동 삭제 명령 부재를 validator로 확인
- 저장소 변경 자체는 revert PR로 복구 가능하며 history rewrite가 필요 없음

## 실행하지 못한 검증과 이유

- Actual Production restore·source 복귀: 별도 고위험 사용자 실행으로 보류
- 외부 사용자 PC HTTPS와 핵심 운영 데이터 의미 검증: Production 실행 범위 밖
- 물리 volume·EBS 장애 복구: 논리 restore 범위 밖이며 인프라 결정 필요
- 중단 시간·RPO/RTO: 측정 작업 범위 밖

## QA 필요 여부

별도 제품 QA 문서는 만들지 않는다. 운영·복구 계약이므로 fake lifecycle, 격리 Docker lifecycle, 정적 validator, 독립 GitHub CI, AI 리뷰와 사용자/Tech Lead 최종 검토를 사용한다.

## QA 문서 경로 또는 생략 사유

제품 화면·API·도메인 동작을 변경하지 않는다. 실제 Production restore 훈련 때는 이 작업 ID 또는 승인된 후속 실행 ID로 사용자 독립 검증과 비민감 실행 보고서를 별도로 남긴다.

## AI 리뷰 반영 여부

PR #76에서 CodeRabbit과 Codex Review가 지적한 공유 lock 이전 상태 읽기, password의 Container 환경 노출, quiesce·상태 기록·revert 실패 복귀 누락, DB manifest 검증 전 Proxy 개방을 반영했다. 최종 미해결 고위험 지적과 check 결과는 동적 GitHub 상태를 권위 원본으로 확인한다.

## AI 리뷰 미반영 항목과 이유

현재 없음. Blue/Green, HA, 자동 배포, RPO/RTO와 물리 EBS 복구는 승인된 제외 범위다.

## 적용 방법

`docs/runbook/OPS-025-production-db-restore.md`의 active volume 상태 최초 도입, 사전 `restore-verify`, candidate 준비, cutover 직전 승인, 쓰기 중단·cutover, 적용 후 검증과 명시적 revert 순서를 사용한다. Actual Production 명령은 별도 고위험 승인 전 실행하지 않는다.

## 인수인계

후속 실제 운영자는 사용자/Product Owner·Tech Lead다. 실행 입력, 승인 지점, 중단·복귀와 비민감 증거 경계는 `docs/handoffs/OPS-025/sre-to-operator.md`에 전달한다.

## 위험과 제한

- logical count manifest는 데이터 의미의 완전성을 보장하지 않는다.
- source와 candidate 동시 보존에 필요한 EBS 여유를 실제 실행 전에 확인해야 한다.
- source 물리 손상은 이 절차로 복구할 수 없다.
- candidate 활성화 후 Application deploy·rollback은 candidate를 유지하지만 DB source 복귀는 전용 `revert`만 사용해야 한다.

## 남은 위험

Actual Production restore·복귀와 외부 HTTPS·핵심 데이터 검증이 미실행이다. OPS-VERIFY-001 최종 판정, PERF-OPS-001, RPO/RTO와 candidate/source 보존·폐기 정책은 사용자 결정으로 남는다.

## 다음 작업

1. GitHub Repository Validation과 AI 재리뷰 확인
2. 사용자/Tech Lead 병합 판단
3. 별도 고위험 승인 후 Actual Production restore 훈련 여부 결정

## Git 결과

`ops/sre`의 최초 구현 commit `d4b4b92321f6fcada1752ebdc8eb7429ebd67dd6`을 push했다. AI 리뷰 보강 commit은 검증 후 같은 역할 branch에 추가한다.

## PR 결과

`main` 대상 PR #76 `feat(sre): Production DB restore 절차 준비`를 생성했다. 자동 병합하지 않으며 사용자 병합 대기 상태를 유지한다.
