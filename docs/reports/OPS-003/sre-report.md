# OPS-003 SRE 작업 보고서

## 작업 정보

- 작업 ID: `OPS-003`
- 역할: Platform/SRE
- 기준 브랜치: `main`
- 작업 브랜치: `ops/sre`
- 원격 저장소: `https://github.com/guseoh/pawcycle-commerce.git`
- 자동 병합: 하지 않음

## 작업 목적

루트 `AGENTS.md`에 GitHub Pull Request 리뷰 댓글 작성 언어 지침을 추가한다.

앞으로 PR 리뷰 댓글, 리뷰 제목, 리뷰 본문, 수정 제안과 위험 설명은 한국어 작성을 기본으로 한다.

## 승인된 입력

- 모든 GitHub Pull Request 리뷰 댓글은 한국어로 작성한다.
- 리뷰 제목, 본문, 수정 제안, 위험 설명은 한국어로 작성한다.
- 코드 식별자, 파일 경로, 요구사항 ID, API path, 커밋 SHA는 원문을 유지한다.
- 심각도 표기는 `P0`, `P1`, `P2` 형식을 유지한다.
- 자동 리뷰의 기본 문구도 가능한 범위에서 한국어로 작성한다.
- 영어 원문 반복 대신 한국어 사용자와 Product Owner/Tech Lead가 바로 판단할 수 있게 작성한다.

## 변경 범위

- `AGENTS.md`
- `docs/reports/OPS-003/sre-report.md`
- `docs/handoffs/OPS-003/sre-to-tl.md`

## 변경하지 않은 범위

- PS-003 Product Decision 작성
- PR #11 수정 또는 병합
- `docs/product/**`
- `docs/design/**`
- `backend/**`
- `frontend/**`
- `infra/**`
- GitHub Actions 자동 리뷰 로직 변경
- 리뷰 봇 또는 외부 서비스 설정 변경
- 신규 의존성 추가

## 변경 내용

- 루트 `AGENTS.md`에 `Review guidelines` 섹션을 추가했다.
- 저장소 검증이 OPS-003 인수인계를 요구해 `docs/handoffs/OPS-003/sre-to-tl.md`을 작성했다.
- PR 리뷰 댓글과 리뷰 설명은 한국어를 기본으로 한다고 명시했다.
- 코드 식별자, 파일 경로, 요구사항 ID, API path, 커밋 SHA는 원문을 유지한다고 명시했다.
- `P0`, `P1`, `P2` 심각도 표기는 유지한다고 명시했다.
- 자동 리뷰 문구는 가능한 범위에서 한국어로 작성한다고 명시했다.
- GitHub Actions나 리뷰 봇 동작은 변경하지 않았다.
- PS-003 제품 결정은 수행하지 않았다.

## 검증 결과

| 검증 | 명령 | 결과 |
| --- | --- | --- |
| 저장소 루트 확인 | `git rev-parse --show-toplevel` | 통과 |
| 원격 저장소 확인 | `git remote -v` | 통과 |
| 작업 트리 확인 | `git status --short --branch` | 통과 |
| 원격 정리 | `git fetch origin --prune` | 통과 |
| 기존 역할 브랜치 확인 | `gh pr list --state open --head ops/sre` | 통과. 열린 PR 없음 |
| 병합 완료 브랜치 확인 | `gh pr view 10 --json number,state,mergedAt,headRefOid` | 통과. PR #10 병합 완료 |
| 공백 오류 확인 | `git diff --check` | 통과 |
| 변경 통계 확인 | `git diff --stat` | 통과 |
| 변경 파일 확인 | `git diff --name-only` | 통과 |
| OPS-003 산출물 확인 | `Write-Output 'OPS-003' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 1차 실패 후 인수인계 추가, 재검증 통과 |
| 커밋 메시지 확인 | `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "docs(review): PR 리뷰 한국어 지침 추가"` | 통과 |
| PR 본문 인코딩 확인 | `py -3 scripts/validate-pr-body-encoding.py --body-file ".git\OPS-003-pr-body.md"` | 통과 |
| 추가 문구 확인 | UTF-8 strict 읽기, `Review guidelines` 단일 섹션, `P0`/`P1`/`P2` 유지, 범위 외 변경 없음, 민감정보 패턴 없음 | 통과 |

## 위험과 제한

- 영어 리뷰를 자동 차단하는 CI는 추가하지 않았다.
- GitHub Actions 자동화나 리뷰 봇 설정은 변경하지 않았다.
- 자동 리뷰 문구는 도구가 허용하는 범위에서 한국어 작성을 기본으로 한다.
- 코드 식별자, 파일 경로, 요구사항 ID, API path, 커밋 SHA는 원문을 유지한다.
- Secret, 토큰, Webhook URL 등 민감정보는 추가하지 않았다.

## Git 결과

- 커밋 메시지: `docs(review): PR 리뷰 한국어 지침 추가`
- push 대상: `origin/ops/sre`
- 최종 커밋과 push 결과는 PR 생성 후 완료 보고에서 확정한다.

## PR 결과

- PR 제목: `docs(review): PR 리뷰 한국어 지침 추가`
- PR 방향: `ops/sre` → `main`
- PR 상태: Draft 예정
- 자동 병합: 하지 않음
- PR 생성 결과는 완료 보고에서 확정한다.
