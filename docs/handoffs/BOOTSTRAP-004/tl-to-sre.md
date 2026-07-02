# BOOTSTRAP-004 TL → SRE 인수인계

## 전달 목적

BOOTSTRAP-004에서 정리한 저장소 하네스 정책을 바탕으로 다음 SRE 작업에서 GitHub 저장소 UI 설정과 운영 자동화 확인을 수행할 수 있게 한다.

## 완료된 작업

- 역할 브랜치 정책 정리
- commit·push 기본 수행 정책 정리
- 명사형 커밋과 PR 제목 규칙 적용
- 작업 보고서와 인수인계 산출물 구조 추가
- PR 템플릿과 Repository Validation 산출물 검증 추가
- README, SECURITY, CONTRIBUTING, Issue Form, 런북 추가

## 관련 파일

- `AGENTS.md`
- `docs/runbook/collaboration-automation.md`
- `docs/runbook/repository-onboarding.md`
- `docs/runbook/github-repository-settings.md`
- `.github/workflows/validate-conventions.yml`
- `.github/pull_request_template.md`
- `scripts/validate-commit-message.sh`
- `scripts/validate-task-artifacts.py`

## 확정된 결정

- 역할 브랜치는 `spec/po`, `design/ux`, `feat/be`, `feat/fe`, `test/qa`, `ops/sre`, `ops/tl`만 사용한다.
- 하나의 역할 브랜치에는 하나의 활성 작업만 둔다.
- 필수 검증과 산출물이 준비되면 Codex는 commit과 push를 수행한다.
- PR 병합은 사용자 최종 검토 후 수행한다.
- 커밋과 PR 제목은 한국어 명사형으로 끝난다.

## 미확정 결정

- GitHub Ruleset 또는 Branch Protection의 최종 적용값
- Squash Merge 외 병합 방식 허용 여부
- 필수 Status Check 구성
- 저장소 설명과 Topics
- Discord Secret 실제 설정 여부
- Dependabot, CodeQL, CODEOWNERS 적용 시점

## 다음 역할의 입력

- GitHub 저장소 Settings 접근 권한
- 사용자가 원하는 저장소 설명과 Topics
- Discord Webhook Secret 설정 여부
- Branch Protection 또는 Ruleset 사용 가능 여부

## 지켜야 할 규칙

- GitHub UI 설정은 Codex가 임의로 변경하지 않는다.
- Secret 값은 문서나 로그에 출력하지 않는다.
- 개인 저장소에서 필수 승인 리뷰 수를 무조건 1로 설정하지 않는다.
- 애플리케이션 프로젝트가 생기기 전에는 Dependabot과 CodeQL을 무리하게 적용하지 않는다.

## 적용·실행 방법

- `docs/runbook/github-repository-settings.md`의 체크리스트를 따라 사용자가 GitHub UI에서 설정한다.
- 로컬 Hook은 `sh scripts/setup-git-hooks.sh` 또는 `.\scripts\setup-git-hooks.ps1`로 설치한다.
- Discord는 GitHub Actions Secret `DISCORD_WEBHOOK_URL`을 사용한다.
- Obsidian은 `docs` 디렉터리를 Vault로 열어 Git 동기화한다.

## 검증 결과

BOOTSTRAP-004 PR에서 Repository Validation, Discord 알림, Obsidian 기록 생성 경로를 확인한다. Secret이 없으면 Discord 실제 전송은 확인 불가로 보고한다.

## 알려진 위험

- `gh` CLI가 로컬에 없어 GitHub CLI 기반 확인은 불가하다.
- Ruleset 적용값이 너무 강하면 GitHub Actions의 Obsidian 기록 push가 막힐 수 있다.
- 개인 저장소에서 승인 리뷰 요구를 켜면 본인이 자신의 PR을 승인할 수 없는 제약이 생길 수 있다.

## 다음 권장 작업

`BOOTSTRAP-005` 또는 저장소 UI 설정 작업으로 진행한다.

## 완료 조건

- `main` 보호 정책이 설정됐다.
- `Repository Validation` 필수 Status Check가 확인됐다.
- Squash Merge와 병합 후 브랜치 삭제 정책이 검토됐다.
- Discord Secret 설정 여부가 확인됐다.
- 저장소 설명과 Topics가 정리됐다.

## 중단 조건

- GitHub Settings 접근 권한이 없다.
- Secret 값을 저장소에 기록해야만 진행할 수 있다.
- Ruleset 변경이 기존 자동화를 막는지 판단할 수 없다.
