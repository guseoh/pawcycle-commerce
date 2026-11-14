# 인수인계(Handoffs)

이 디렉터리는 대화 이후에도 남아야 하는 역할 간 인수인계를 보관한다.

경로 형식은 다음과 같다.

```text
docs/handoffs/<작업 ID>/<source-role>-to-<target-role>.md
```

예시는 다음과 같다.

- `docs/handoffs/PS-001/product-to-design.md`
- `docs/handoffs/PS-001/backend-to-frontend.md`
- `docs/handoffs/PS-001/frontend-to-qa.md`

다른 역할에 실제로 전달할 정보가 있을 때만 인수인계를 만든다. 모든 작업마다 모든 조합의 인수인계를 만들지 않는다.

새 인수인계는 `docs/handoffs/handoff-template.md`를 기준으로 작성한다.

역할 브랜치, commit·push, 보고서 필수 규칙은 루트 `AGENTS.md`를 기준으로 한다.

## 완료된 인수인계

- [FOUNDATION-001](FOUNDATION-001/tl-to-be-fe-sre.md)은 초기 애플리케이션 구성 시점의 기록이다. 현재 개발 환경은 [로컬 개발 Runbook](../runbook/FOUNDATION-001-local-development.md)을 따른다.
- [OPS-001](OPS-001/sre-to-tl.md)은 최초 Discord 연동 확인을 위한 기록이다. 현재 알림 절차는 [협업 자동화 Runbook](../runbook/collaboration-automation.md)을 따른다.
- [OPS-007](OPS-007/sre-to-tl.md)은 단일 Discord 협업 알림 workflow와 보고서형 embed 구조를 Tech Lead에게 넘긴 기록이다. 실행 결과는 [OPS-007 보고서](../reports/OPS-007/sre-report.md)에 있고, 현재 알림 절차는 [협업 자동화 Runbook](../runbook/collaboration-automation.md)을 따른다.
