# 런북(Runbooks)

이 디렉터리는 반복 실행·중단·복구 절차 또는 지속적인 운영 확인이 실제로 필요한 경우의 Runbook을 보관한다. 일회성 저장소 변경이나 PR만으로 충분한 작업은 별도 Runbook을 만들지 않으며, 공통 판단 기준은 `lean-harness.md`를 따른다.

## 현재 Production 상태

- Production deployment target: **OCI app01**
- CD: **DEFER**
- 무중단 배포: **DEFER**
- OCI app01은 단일 서버·수동 배포 상태다. Release/Rollback 저장소 기반과 격리 검증은 실제 운영 적용 또는 Production Verified를 의미하지 않는다.
- 종료된 AWS Production의 EC2/SSM/RDS/S3/CloudWatch 실행 Runbook은 active Runbook에서 제거했다.

현재 Production 경계는 `docs/architecture/production-operations-overview.md`를 따른다. Runbook 파일이 존재한다는 사실만으로 실제 Production 실행이 승인되거나 현재 환경에 그대로 적용 가능하다고 해석하지 않는다.

AWS에서 수행했던 운영 과정과 검증 결과는 `docs/reports/**`, `docs/learning/**`, 관련 ADR, Git 및 PR 이력에 보존한다. 이 기록은 향후 회고·포트폴리오·기술 글 작성의 근거이며, 현재 실행 절차로 재사용하지 않는다.

## Harness / Collaboration

- `lean-harness.md`: 작업 등급, 실행 구분, 최소 PR evidence, 조건부 산출물, AI Prompt, Review와 feedback loop의 권위 원본
- `github-mcp-agent.md`: GitHub Connector/MCP read/write preflight와 branch 안전
- `collaboration-automation.md`: Repository Validation, Discord, release side effect와 병합 PR 기록 정책
- `repository-onboarding.md`: 새 환경의 최소 저장소·Harness 준비 절차
- `github-repository-settings.md`: 사용자가 GitHub Settings에서 확인할 보호 설정

## Operations

Cloud provider 전환 중에는 각 Runbook의 전제와 현재 상태를 먼저 확인한다. 공급자 중립적으로 유지되는 절차도 새 Production target이 승인되기 전에는 실제 운영 명령으로 간주하지 않는다.

대표적으로 계속 유지하는 문서:

- `OPS-OCI-004-production-https-renewal.md`
- `OPS-OCI-CD-02-release-recovery.md`: 승인된 OCI Release/Preflight/Rollback, 보호 상태와 중단 복구 경계
- `SUB-AUTO-001-subscription-automation.md`
- `MVP4-DATA-002-demo-catalog-import.md`
- `PERF-V7-003-isolated-catalog-scale.md`: live Catalog와 분리된 I0/I10K OCI Scale Dataset runtime/import/load/cleanup 계약
- 관측·진단·성능 측정 관련 Runbook

실제 Production·Cloud·운영 DB·Secret·비용 작업은 별도의 명시적 사용자 승인과 적용 전후·복구 evidence가 필요하다.

## 새 Runbook을 만들 때

필요한 항목만 포함한다. 일반적인 운영 Runbook은 `범위`, `시작 조건`, `확인 절차`, `실행 또는 완화`, `중단 조건`, `복구`, `보존할 증거`, `남은 위험`을 필요한 만큼만 포함한다. 장기 기술 선택과 trade-off가 핵심이면 Runbook이 아니라 ADR을 사용한다.
