# BOOTSTRAP-004 TL 작업 보고서

## 작업 정보

- 작업 ID: `BOOTSTRAP-004`
- 역할: TL
- 작업명: 초기 개발 하네스 및 저장소 기반 정비
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- 대상 PR: `main`

## 작업 목적

Spring Boot, Next.js, MySQL 애플리케이션을 생성하기 전에 저장소의 운영·협업·문서·검증 기반을 정비한다.

## 입력 문서

- 사용자 첨부 지시서: `BOOTSTRAP-004: 초기 개발 하네스 및 저장소 기반 정비`
- 루트 `AGENTS.md`
- 하위 `AGENTS.md`
- 역할 문서와 Skill
- 기존 GitHub Actions, Discord, Obsidian 자동화
- 기존 `.gitignore`

## 변경 범위

- 역할 브랜치 정책을 `spec/po`, `design/ux`, `feat/be`, `feat/fe`, `test/qa`, `ops/sre`, `ops/tl`로 정리
- 필수 검증 통과 후 commit·push 기본 수행 정책 반영
- 커밋과 PR 제목의 한국어 명사형 규칙 반영
- 작업 보고서와 역할 인수인계 구조 추가
- PR 템플릿, 검증 스크립트, GitHub Actions 산출물 검증 개선
- README, SECURITY, CONTRIBUTING, Issue Form, 저장소 런북 추가
- `.editorconfig`, `.gitattributes` 추가

## 변경하지 않은 범위

- Spring Boot 프로젝트 생성
- Next.js 프로젝트 생성
- MySQL Docker Compose
- 실제 DB 스키마
- API 명세
- JPA Entity
- 인증·인가
- 제품 기능
- PS-001 제품 정책 변경
- 실제 PG 연동
- Redis, Kafka, RabbitMQ, Elasticsearch, Kubernetes
- 배포 환경
- Dependabot, CodeQL, CODEOWNERS, LICENSE

## 주요 결과

- 루트 `AGENTS.md`를 역할 브랜치와 commit·push 정책의 단일 기준으로 정리했다.
- 하위 역할 문서와 Skill은 루트 기준을 참조하도록 연결했다.
- `scripts/validate-commit-message.sh`가 명확한 서술형 종결을 거부하도록 개선했다.
- `scripts/validate-task-artifacts.py`로 PR 본문 작업 ID를 기준으로 보고서와 인수인계를 확인한다.
- GitHub Issue Form을 YAML 기반으로 구성했다.
- 현재 애플리케이션 실행 명령이 없음을 README에 명시했다.

## 변경 파일

- `AGENTS.md`
- `backend/AGENTS.md`, `frontend/AGENTS.md`, `qa/AGENTS.md`, `infra/AGENTS.md`
- `docs/roles/**`
- `.agents/skills/**`
- `.github/pull_request_template.md`
- `.github/workflows/validate-conventions.yml`
- `.github/workflows/record-merged-pr.yml`
- `.github/ISSUE_TEMPLATE/**`
- `scripts/validate-commit-message.sh`
- `scripts/test-commit-message-convention.sh`
- `scripts/validate-task-artifacts.py`
- `README.md`
- `.editorconfig`
- `.gitattributes`
- `SECURITY.md`
- `CONTRIBUTING.md`
- `docs/runbook/**`
- `docs/reports/**`
- `docs/handoffs/**`

## 결정 상태

- 확정: 역할 브랜치 이름과 생명주기
- 확정: 필수 검증 통과 후 commit·push 기본 수행
- 확정: PR 병합은 사용자 최종 검토 후 수행
- 확정: 커밋과 PR 제목의 한국어 명사형 규칙
- 미확정: LICENSE 선택
- 미확정: CODEOWNERS 적용 대상
- 미확정: Dependabot과 CodeQL 적용 시점의 세부 설정
- 미확정: 애플리케이션 기술 버전

## API 영향

없음.

## DB 영향

없음.

## 보안 영향

- Secret과 개인정보 입력 금지 문서를 강화했다.
- `.gitignore`의 민감 파일 보호 정책은 유지했다.
- `SECURITY.md`를 추가했다.

## 운영 영향

- GitHub Actions `Repository Validation`에 작업 산출물 검증이 추가된다.
- Discord와 Obsidian 자동화는 기존 흐름을 유지한다.
- GitHub UI 설정은 문서화만 했고 직접 변경하지 않았다.

## 성능 영향

없음.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| `git status --short --branch` | `ops/tl`에서 작업 중, 시작 전 작업 트리는 깨끗했음 |
| `git diff --check` | 통과 |
| `git diff --stat` | 예상 범위의 문서·자동화 변경 |
| `git diff --numstat` | 대규모 전체 파일 재정규화 없음 |
| `sh scripts/test-commit-message-convention.sh` | Git Bash로 통과 |
| 유효/무효 커밋 메시지 fixture | 요청된 유효·무효 사례 포함 |
| `python -m py_compile .github/scripts/*.py scripts/validate-task-artifacts.py` | 통과 |
| `python scripts/validate-discord-payloads.py` | 통과 |
| `python scripts/validate-obsidian-record.py` | 통과 |
| `python scripts/validate-task-artifacts.py --from-stdin` | `BOOTSTRAP-004` 기준 통과 |
| Issue Form YAML 구조 확인 | 외부 YAML 파서 없이 필수 키와 탭 금지 기본 검사 통과 |
| 오래된 규칙 검색 | 남은 항목은 금지 예시, 검증 패턴, 일반 문장, 과거 PR 기록, PS-001 이전 계획 |
| 보안 검색 | 실제 Secret 값 없음. 검증 스크립트의 탐지 문자열만 확인 |

## 적용 방법

- Git Hook: `sh scripts/setup-git-hooks.sh` 또는 `.\scripts\setup-git-hooks.ps1`
- 역할 브랜치: 최신 `main`에서 역할 브랜치를 생성해 한 작업만 수행
- PR: 작업 보고서와 인수인계를 본문에 연결
- GitHub Ruleset: `docs/runbook/github-repository-settings.md` 기준으로 사용자가 UI에서 설정
- Discord: `DISCORD_WEBHOOK_URL` Secret을 GitHub Actions에 등록
- Obsidian: `docs` 디렉터리를 Vault로 열고 Git으로 동기화

## 위험과 제한

- `gh` CLI는 현재 로컬에서 사용할 수 없어 PR 생성은 Codex GitHub 앱 도구를 사용해야 한다.
- 작업 산출물 검증은 PR 본문에서 작업 ID를 찾는 가벼운 방식이다.
- 개인 저장소에서는 필수 승인 리뷰 수를 무조건 1로 설정하면 본인 PR 승인 제약이 생길 수 있다.
- `.gitattributes`는 추가했지만 대규모 줄바꿈 재정규화는 수행하지 않았다.

## 기존 브랜치 이전 계획

- `ops/bootstrap-002`: 현재 `main`과 비교했을 때 최신 자동화 파일을 대거 제거하는 오래된 차이가 있으므로 cherry-pick하지 않는다. 유효한 역할 브랜치 단순화 취지만 이번 작업에 수동 반영했다. 사용자 확인 전 원격 브랜치를 삭제하지 않는다.
- `spec/PS-001-service-vision-mvp`: 이번 작업에서 제품 문서를 수정하지 않는다. 최신 `main`에서 `spec/po`를 생성하고 필요한 PS-001 문서만 안전하게 이전한 뒤 Product Owner 검토와 Draft PR을 진행한다.

## 다음 작업

1. `BOOTSTRAP-005` 또는 저장소 UI 설정, SRE
2. `PS-001`, PO
3. `DOMAIN-001`, BE 또는 TL
4. `ARCH-001`, TL
5. `FOUNDATION-000`, TL + BE + FE + SRE

## Git 결과

- 커밋 SHA: 커밋 후 최종 응답과 PR 본문에 기록
- push 결과: push 후 최종 응답과 PR 본문에 기록
- 보고서 재귀 갱신 방지: 이 보고서에는 커밋 전 `예정` 상태를 남기고, 실제 SHA와 PR 번호는 PR 본문과 완료 보고에 기록한다.

## PR 상태

- PR 번호: 생성 후 최종 응답과 PR 본문에 기록
- PR 상태: 생성 예정
- 자동 병합: 수행하지 않음
