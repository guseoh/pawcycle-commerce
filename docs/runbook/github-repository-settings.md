# GitHub 저장소 설정 런북

## 목적

GitHub UI 설정은 저장소 코드와 달리 AI가 임의 변경하지 않는다. 이 문서는 사용자가 확인할 최소 보호 설정과 PawCycle Harness가 기대하는 경계를 정리한다.

## General

확인 후보:

- 기본 브랜치 `main`
- Squash Merge 사용
- 병합 후 branch 자동 삭제 여부
- Pull Request 기반 변경 흐름

Merge/Rebase 허용 여부와 자동 branch 삭제는 저장소 사용 방식에 맞게 사용자가 결정한다.

## Ruleset / Branch Protection

PawCycle에서 우선순위가 높은 보호는 다음이다.

- `main` direct push 제한
- Pull Request 필수
- force push 금지
- required check로 `Repository Validation` 사용
- 필요하면 최신 base 반영 요구

개인 포트폴리오 저장소에서는 필수 승인 리뷰 수를 무조건 1로 두지 않는다. 본인이 자신의 PR을 승인할 수 없는 설정이 작업을 막을 수 있기 때문이다.

`Repository Validation`은 최종 merge gate이며 metadata/convention 결과와 classifier가 선택한 component 검증을 함께 집계한다.

## Actions

확인 항목:

- workflow permission 최소화
- Fork PR Secret 접근 제한
- `pull_request_target`/`workflow_run`에서 신뢰되지 않은 PR head 코드를 Secret과 함께 실행하지 않음
- `DISCORD_WEBHOOK_URL` Secret 존재 여부

병합 PR Markdown을 만들기 위해 GitHub Actions에 `contents: write`를 주는 자동화는 사용하지 않는다. 과거 `docs/learning/pull-requests/**` 기록은 보존하지만 새 병합 기록의 권위 원본은 GitHub PR이다.

Production image publish는 runtime 변경에만 반응해야 한다.

```text
backend/**
frontend/**
infra/production/**
.github/workflows/publish-production-images.yml
```

README, AGENTS, 일반 Runbook 같은 문서-only push가 Production image/deploy chain을 시작하면 Harness 결함으로 본다.

## Security

사용 가능한 범위에서 확인한다.

- Secret scanning
- Push protection
- Dependabot
- CodeQL
- Private Vulnerability Reporting

도구 존재 자체를 완료 조건으로 삼지 않고 실제 dependency/code와 운영 필요에 맞춰 사용한다.

## Repository Metadata

README와 실제 구현에 맞게 description, Topics, homepage를 갱신한다. 존재하지 않는 기능이나 아직 Production 검증하지 않은 운영 상태를 metadata에서 먼저 확정하지 않는다.

## 사용자 확인 체크리스트

- [ ] 기본 브랜치가 `main`이다.
- [ ] 일반 변경은 PR을 통해 `main`으로 들어간다.
- [ ] force push를 허용하지 않는 방향을 검토했다.
- [ ] `Repository Validation` required check를 검토했다.
- [ ] Actions workflow permission이 최소 권한이다.
- [ ] `DISCORD_WEBHOOK_URL`은 Secret으로만 관리된다.
- [ ] 문서-only push가 Production release chain을 시작하지 않는다.
- [ ] Secret scanning / push protection 가능 여부를 확인했다.

## 설정 변경 후 문제 발생 시

1. 실패한 Actions run과 실제 event/ref를 확인한다.
2. Repository Validation, 알림, package publish, Production dispatch를 분리해서 본다.
3. 예상하지 않은 Production/Cloud side effect 가능성이 있으면 추가 변경보다 실제 실행 여부 확인을 우선한다.
4. 보호 규칙을 약화하기 전에 workflow·권한·경로 trigger가 잘못된 것은 아닌지 먼저 검토한다.
5. Secret 값은 로그나 문서에 출력하지 않는다.
