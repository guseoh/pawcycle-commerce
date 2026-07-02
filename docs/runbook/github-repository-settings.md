# GitHub 저장소 설정 런북

## 목적

Codex가 GitHub UI 설정을 임의로 변경하지 않고, 사용자가 저장소 Settings에서 확인할 항목을 정리한다.

## General

확인 항목:

- 기본 브랜치가 `main`인지 확인
- Squash Merge 사용 권장
- Merge Commit과 Rebase Merge 허용 여부 검토
- 병합 후 브랜치 자동 삭제 활성화 검토
- Pull Request 제목이 최종 Squash commit 제목으로 쓰일 수 있는지 확인

## Ruleset 또는 Branch Protection

권장 후보:

- `main` 직접 push 제한
- Pull Request 필수
- force push 금지
- branch deletion 금지
- `Repository Validation` 필수 Status Check
- branch 최신 상태 요구 여부 검토
- 관리자 우회 정책 검토

현재 우선 추천 구성:

```text
Pull Request 필수
+ Repository Validation 필수
+ 수동 검토 체크리스트
+ No Explain, No Merge
```

개인 저장소에서는 필수 승인 리뷰 수를 무조건 `1`로 설정하지 않는다. 본인이 자신의 PR을 승인할 수 없는 제약이 생길 수 있기 때문이다.

## Security

확인 항목:

- Private Vulnerability Reporting 사용 가능 여부
- Secret scanning 사용 가능 여부
- Push protection 사용 가능 여부
- Dependabot은 의존성 파일 생성 후 적용
- CodeQL은 애플리케이션 코드 생성 후 적용

현재는 실제 애플리케이션 의존성 파일이 없으므로 Dependabot과 CodeQL을 보류한다.

## Actions

확인 항목:

- Workflow permissions는 최소 권한으로 설정
- Fork PR의 Secret 접근 제한 확인
- `pull_request_target` 워크플로는 base 브랜치 코드만 실행하는지 확인
- `DISCORD_WEBHOOK_URL` Secret 등록 여부 확인
- Obsidian 자동 기록 워크플로가 `contents: write` 권한을 사용하는지 확인

주의:

- PR head 코드를 `pull_request_target`에서 checkout하지 않는다.
- Discord Webhook URL을 로그나 문서에 출력하지 않는다.
- Branch Protection이 GitHub Actions의 Obsidian 기록 커밋을 막을 수 있으므로 적용 후 병합 PR에서 확인한다.

## Repository Metadata

확인 항목:

- 저장소 설명 작성
- Topics 설정
- 홈페이지 URL은 실제 배포 후 설정
- Social preview는 추후 설정

설명 후보:

```text
AI-native commerce project for pet supplies and recurring subscription workflows.
```

Topics 후보:

```text
spring-boot
nextjs
mysql
commerce
subscription
ai-native-engineering
```

실제 기술 스택 파일이 생성되기 전에는 Topics를 과하게 확정하지 않는다.

## 사용자 확인 체크리스트

- [ ] 기본 브랜치가 `main`이다.
- [ ] PR 필수 정책이 설정됐다.
- [ ] `Repository Validation`이 필수 Status Check 후보다.
- [ ] Squash Merge 정책을 검토했다.
- [ ] 병합 후 브랜치 자동 삭제를 검토했다.
- [ ] `DISCORD_WEBHOOK_URL` Secret 설정 여부를 확인했다.
- [ ] Private Vulnerability Reporting 가능 여부를 확인했다.
- [ ] Secret scanning과 push protection 가능 여부를 확인했다.
- [ ] 저장소 설명과 Topics를 검토했다.

## 롤백과 복구

설정 변경 후 자동화가 실패하면 다음 순서로 확인한다.

1. Actions 로그에서 실패한 워크플로를 확인한다.
2. `Repository Validation` 실패인지, Discord Secret 문제인지, Obsidian 기록 push 문제인지 분리한다.
3. Branch Protection이 자동 기록을 막는다면 보호 규칙을 약화하기 전에 Artifact 업로드 방식 또는 별도 PR 기록 방식을 검토한다.
4. Secret 값을 로그에 출력하지 않는다.
