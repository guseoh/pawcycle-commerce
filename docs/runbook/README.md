# 런북(Runbooks)

이 디렉터리는 운영 런북과 장애 대응 메모를 보관한다.

실제 운영 워크플로(Operational Workflow), 배포 경로(Deployment Path), 반복 장애, 모니터링(Monitoring) 또는 알림(Alert) 표면이 생겼을 때 작성한다.

## 현재 런북

- `collaboration-automation.md`: 역할 브랜치, commit·push, 검증, Discord, Obsidian 자동화
- `repository-onboarding.md`: 로컬 저장소, Git Hook, Obsidian, Discord, 검증 명령
- `github-repository-settings.md`: GitHub Settings에서 사용자가 확인할 저장소 설정
- `OPS-009-aws-operations-foundation.md`: DEPLOY-001 AWS 운영 기반의 생성 전 게이트, 사용자 실행, 검증과 안전 정리

## 최소 런북 구조

```markdown
# 런북 제목

## 범위

## 증상

## 사용자 영향

## 첫 확인 절차

## 완화 조치

## 롤백

## 에스컬레이션

## 보존할 증거

## 후속 작업
```
