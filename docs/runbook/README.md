# 런북(Runbooks)

운영 절차는 현재 OCI target contract와 과거 실행 증거를 분리해 관리한다. 모든 현재 문서는 **Accepted — Repository Readiness**이며 **Production Verified가 아니다**.

## Current OCI Production

- `OPS-010-production-single-release.md`: runtime materialization, OCI Run Command operator boundary, Application deploy/rollback
- `OPS-011-production-https.md`: Nginx/Certbot HTTPS bootstrap, issue, renew, enable과 recovery
- `OPS-OCI-002-production-db-backup-restore.md`: Object Storage logical backup, isolated restore-verify와 cleanup
- `OPS-OBS-001-production-observability.md`: Application/Observability 2-host Trial baseline과 metrics-proxy
- `OPS-AUTO-010-backend-state-alert.md`: OCI Application backend state 다중 채널 알림 dispatcher
- `MVP4-DATA-002-demo-catalog-import.md`: Application one-shot catalog import 경계
- `SUB-AUTO-001-subscription-automation.md`, `SUB-AUTO-002-production-subscription-automation.md`: Scheduler preflight와 activation 경계

## Superseded AWS Production

다음 문서는 본문을 historical evidence로 보존하되 현재 실행 절차로 사용하지 않는다.

- `OPS-009-aws-operations-foundation.md`
- `OPS-DB-002-rds-migration-cutover.md`
- `OPS-013-production-db-backup-restore.md`
- `OPS-015-ec2-status-check-alarm.md`
- `OPS-025-production-db-restore.md`
- `OPS-AUTO-007-production-ssm-document-rollback.md`
- `MVP4-CD-001-temporary-auto-production-deploy.md`

## Historical evidence

`docs/reports/**`, `docs/handoffs/**`, `docs/learning/**`와 위 superseded 문서의 본문은 과거 실행·검토 증거로 보존한다. 과거 provider, ID, 명령과 결과를 현재 OCI runtime으로 재해석하지 않는다.

## 최소 런북 구조

```markdown
# 런북 제목

## 범위와 상태
## 증상과 사용자 영향
## 첫 확인 절차
## 완화 조치
## 롤백
## 에스컬레이션
## 보존할 증거
## Evidence status
```
