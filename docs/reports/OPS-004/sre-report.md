# OPS-004 SRE 작업 보고서

## 작업 정보

- 작업 ID: `OPS-004`
- 역할: Platform/SRE
- 기준 브랜치: 최신 `main`
- 작업 브랜치: `ops/sre`
- 선행 PR: PR #15 `docs(architecture): 첫 수직 MVP 아키텍처 결정`
- PR #15 병합 확인: `2026-07-05T14:36:36Z`

## 작업 목적

DATA-001 데이터 설계 작업 전에 CodeRabbit AI를 PawCycle Commerce 저장소에 적용할 수 있도록 리뷰 자동화 하네스를 추가했다.

이번 작업은 구현 작업이 아니라 PR 리뷰 품질을 높이기 위한 협업·검토 자동화 설정 작업이다. Codex Review는 유지하고, CodeRabbit은 PR 내부 변경사항 요약, 라인별 코드 리뷰, 테스트 누락, 보안 문제, 인증/인가 누락, 도메인 규칙 위반, 유지보수성 문제를 자세히 검토하는 자동 리뷰어로 사용하도록 정리했다.

## 승인된 입력

- CodeRabbit은 무료 범위에서만 사용한다.
- 가능하면 저장소는 public repository로 운영한다.
- GitHub App 설치 시 모든 저장소가 아니라 `guseoh/pawcycle-commerce` 하나만 selected repository로 선택한다.
- CodeRabbit 리뷰 언어는 `ko-KR`로 설정한다.
- CodeRabbit review profile은 `assertive`로 설정한다.
- Draft PR 리뷰는 비활성화한다.
- 기본 브랜치 `main`은 기본 리뷰 대상으로 보고, 추가 base branch로 `develop`을 설정한다.
- Codex Review는 계속 사용한다.
- CodeRabbit은 최종 결정자가 아니라 PR 검토를 돕는 자동 리뷰어다.
- 결제, 주문 상태 변경, 정기배송 상태 전이, 인증/인가, 개인정보 관련 코드는 AI 리뷰 결과를 참고하되 반드시 사람이 다시 검토한다.

## 변경 범위

- `.coderabbit.yaml`
- `AGENTS.md`
- `.github/pull_request_template.md`
- `docs/reports/OPS-004/sre-report.md`
- `docs/handoffs/OPS-004/sre-to-tl.md`

## 변경하지 않은 범위

- DATA-001 데이터 설계를 수행하지 않았다.
- API-001 API 계약을 수행하지 않았다.
- Backend 코드를 변경하지 않았다.
- Frontend 코드를 변경하지 않았다.
- DB 마이그레이션을 추가하지 않았다.
- GitHub Actions 워크플로를 변경하지 않았다.
- Secret을 추가하지 않았다.
- CodeRabbit 유료 플랜 설정을 수행하지 않았다.
- CodeRabbit GitHub App 설치 자체는 수행하지 않았다.
- Codex Review를 제거하거나 비활성화하지 않았다.
- 제품·도메인·UX·아키텍처 정책을 변경하지 않았다.
- 신규 의존성을 추가하지 않았다.
- 자동 병합하지 않는다.

## CodeRabbit 적용 목적

- PR 내부 변경사항 요약 보강
- 라인별 코드 리뷰 보강
- 테스트 누락 감지
- 보안 문제 감지
- 인증/인가 누락 감지
- 도메인 규칙 위반 감지
- 유지보수성 문제 감지
- Codex Review와 병행해 AI 리뷰 이중화

## 무료 사용 기준

- CodeRabbit은 무료 범위에서만 사용한다.
- 가능하면 이 저장소는 public repository로 운영한다.
- GitHub App 설치 시 모든 저장소가 아니라 `guseoh/pawcycle-commerce` selected repository 하나만 선택한다.
- 먼저 PawCycle Commerce에만 적용한다.
- 리뷰 품질과 비용·제한을 확인한 뒤 다른 public 프로젝트 확대 여부를 결정한다.

## 변경 내용

- 루트 `.coderabbit.yaml`을 추가해 한국어 리뷰, `assertive` profile, Draft PR 자동 리뷰 비활성화, `develop` 추가 base branch, 경로별 리뷰 지침을 설정했다.
- CodeRabbit 공식 설정 참조와 자동 리뷰 제어 문서를 확인하고, `tone_instructions`는 공식 길이 제한에 맞게 짧게 작성했다.
- 참고한 공식 문서:
  - https://docs.coderabbit.ai/reference/configuration
  - https://docs.coderabbit.ai/configuration/auto-review
- 루트 `AGENTS.md`의 기존 규칙과 OPS-003 `Review guidelines`를 유지하면서 CodeRabbit review guidelines를 추가했다.
- 기존 `.github/pull_request_template.md`를 확장해 도메인 규칙, API 변경 여부, 테스트 여부, 리뷰 중점 영역, CodeRabbit summary placeholder, 병합 전 확인 항목을 추가했다.
- OPS-004 작업 보고서와 SRE → TL 인수인계를 작성했다.

## Codex Review와 CodeRabbit 운영 흐름

Codex Review:

- 설계 방향 검토
- 구현 초안 작성
- 리팩터링 방향 논의
- CodeRabbit 리뷰 반영 여부 판단
- 후속 작업 프롬프트 작성

CodeRabbit AI:

- PR 내부 변경사항 요약
- 라인별 코드 리뷰
- 테스트 누락 지적
- 보안 문제 지적
- 인증/인가 누락 지적
- 도메인 규칙 위반 지적
- 유지보수성 문제 지적

운영 흐름:

1. Issue를 작성한다.
2. Codex로 설계와 구현 초안을 만든다.
3. 사용자가 직접 코드를 확인한다.
4. PR을 생성한다.
5. CodeRabbit의 상세 리뷰를 확인한다.
6. 필요한 리뷰만 선별해서 반영한다.
7. Codex Review로 수정 방향을 다시 검토한다.
8. 테스트를 통과시킨 뒤 사용자가 직접 판단해서 merge한다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| `git status --short --branch` | 통과, `ops/sre`에서 OPS-004 설정·문서 변경만 확인 |
| `git diff --check` | 통과 |
| `git diff --stat` | 통과 |
| `git diff --name-status` | 통과 |
| `Write-Output 'OPS-004' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "chore(review): CodeRabbit 리뷰 설정 추가"` | 통과 |
| `.coderabbit.yaml` 존재 확인 | 통과 |
| `language: "ko-KR"` 확인 | 통과 |
| `profile: "assertive"` 확인 | 통과 |
| `drafts: false` 확인 | 통과 |
| `develop` 추가 base branch 확인 | 통과 |
| `main` 중복 base branch 미포함 확인 | 통과 |
| `AGENTS.md` 기존 내용 유지와 CodeRabbit 리뷰 우선순위 확인 | 통과 |
| `AGENTS.md`에 CodeRabbit이 최종 결정자가 아니라는 원칙 확인 | 통과 |
| `.github/pull_request_template.md` 존재 확인 | 통과 |
| PR template의 `@coderabbitai summary` 확인 | 통과 |
| PR template의 도메인 규칙, API 변경 여부, 테스트 여부, 리뷰 중점 영역 확인 | 통과 |
| Codex Review 제거 또는 비활성화 변경 없음 확인 | 통과 |
| GitHub Actions 변경 없음 확인 | 통과 |
| 제품·도메인·UX·아키텍처 문서 변경 없음 확인 | 통과 |
| Backend, Frontend, DB, Infra 구현 변경 없음 확인 | 통과 |
| 실제 Secret, token, webhook URL 패턴 확인 | 통과 |
| YAML 문법 검증 | 미실행, 로컬 Python에 `yaml` 모듈이 없어 `ModuleNotFoundError: No module named 'yaml'` 발생. 지시상 실패로 보지 않고 기록 |

PowerShell의 `bash` 명령은 WSL의 `C:\Windows\system32\bash.exe`로 해석되어 실행 오류가 발생했다. 동일 검증 스크립트는 Git for Windows Bash에서 통과했다.

## 위험과 제한

- CodeRabbit GitHub App 설치 자체는 이 작업에서 수행하지 않았다.
- CodeRabbit 유료 플랜 설정은 수행하지 않았다.
- 무료 범위와 public repository 운영 가능 여부는 사용자가 GitHub/CodeRabbit UI에서 확인해야 한다.
- CodeRabbit 설정 키가 실제 설치 후 오류를 보고하면 공식 문서 기준으로 최소 수정해야 한다.
- CodeRabbit 리뷰는 자동 보조 도구이며 최종 결정자는 Product Owner/Tech Lead다.
- 보안, 인증/인가, 결제, 주문 상태, 정기배송 상태 전이, 개인정보 관련 변경은 사람이 반드시 다시 검토해야 한다.

## Git 결과

- 기존 로컬 `ops/sre` SHA: `8ccc1bf426dc3ea3c09ceaf6600c8d2c8710f242`
- 기존 원격 `origin/ops/sre` SHA: `8ccc1bf426dc3ea3c09ceaf6600c8d2c8710f242`
- 기존 `origin/main..origin/ops/sre` 로그:

```text
8ccc1bf docs(report): OPS-003 PR 상태 갱신
cfe989c docs(review): PR 리뷰 한국어 지침 추가
```

- 원격 백업 브랜치: `backup/ops-sre-before-OPS-004`
- 로컬 백업 브랜치: `backup/local-ops-sre-before-OPS-004`
- 원격 `origin/ops/sre` 삭제 완료
- 로컬 `ops/sre` 삭제 완료
- 최신 `main`에서 새 `ops/sre` 생성 완료
- 작업 커밋: `1a169fafdffbd78c04e8b19d15b457dd59663a68`
- push: `origin/ops/sre` 업스트림 설정 완료

## PR 결과

- Draft PR: #16
- PR URL: https://github.com/guseoh/pawcycle-commerce/pull/16
- title: `chore(review): CodeRabbit 리뷰 설정 추가`
- head/base: `ops/sre` -> `main`
- 상태: Open Draft
- PR 생성 직후 확인한 checks:
  - `Repository Validation / Commit and PR conventions`: queued
  - `Collaboration Notification / Discord collaboration notification`: queued
- 자동 병합하지 않았다.
