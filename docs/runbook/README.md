# 런북(Runbooks)

이 디렉터리는 **반복 실행·중단·복구 절차 또는 지속적인 운영 확인이 실제로 필요한 경우**의 Runbook을 보관한다.

일회성 저장소 변경이나 PR만으로 충분한 작업은 별도 Runbook을 만들지 않는다. 공통 판단 기준은 `lean-harness.md`를 따른다.

## Harness / Collaboration

- `lean-harness.md`: 작업 등급, 실행 구분, 최소 PR evidence, 조건부 산출물, AI Prompt, Review와 feedback loop의 권위 원본
- `github-mcp-agent.md`: GitHub Connector/MCP read/write preflight와 branch 안전
- `collaboration-automation.md`: Repository Validation, Discord, release side effect와 병합 PR 기록 정책
- `repository-onboarding.md`: 새 환경의 최소 저장소·Harness 준비 절차
- `github-repository-settings.md`: 사용자가 GitHub Settings에서 확인할 보호 설정

## Operations

운영 Runbook은 각 파일의 현재 상태와 superseded 표기를 확인한다. Cloud provider 전환이나 운영 구조 변경 중에는 과거 Runbook을 최신 실행 절차로 추정하지 않는다.

대표 문서:

- `OPS-009-aws-operations-foundation.md`
- `OPS-010-production-single-release.md`
- `OPS-011-production-https.md`
- `OPS-DB-002-rds-migration-cutover.md`
- `SUB-AUTO-001-subscription-automation.md`
- `SUB-AUTO-002-production-subscription-automation.md`
- `MVP4-DATA-002-demo-catalog-import.md`

실제 Production·Cloud·운영 DB·Secret·비용 작업은 Runbook 존재만으로 승인된 것으로 해석하지 않는다. 별도의 명시적 사용자 승인과 적용 전후·복구 evidence가 필요하다.

## 새 Runbook을 만들 때

필요한 항목만 포함한다. 일반적인 운영 Runbook은 다음 정도면 충분하다.

```markdown
# 제목

## 범위
## 시작 조건
## 확인 절차
## 실행 또는 완화
## 중단 조건
## 복구
## 보존할 증거
## 남은 위험
```

장기 기술 선택과 trade-off가 핵심이면 Runbook이 아니라 ADR을 사용한다.
