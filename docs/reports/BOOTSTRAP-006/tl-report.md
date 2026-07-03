# BOOTSTRAP-006 TL 작업 보고서

## 작업 정보

- 작업 ID: `BOOTSTRAP-006`
- 역할: Tech Lead
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- PR 대상: `main`
- 선행 PR: `#5 docs(product): PS-002 MVP 요구사항 정리`
- 자동 병합: 하지 않음

## 작업 목적

Repository Validation과 로컬 작업 산출물 검증기가 PS-002 후속 작업 ID인 `DOMAIN-001`과 `API-001`을 정상적으로 인식하도록 최소 수정한다.

이 작업은 DOMAIN-001 도메인 설계를 시작하기 위한 선행 하네스 보완이다.

## 시작 전 상태

- PR `#5`: 2026-07-03 08:05:21Z 병합 확인
- `origin/main`: PS-002 요구사항 문서, 보고서, PO → BE 인수인계 존재 확인
- 기존 `origin/spec/po`: PR `#5` 병합 확인 후 삭제
- 기존 `origin/ops/tl`: PR `#3` 병합 확인 후 삭제
- 새 `ops/tl`: 최신 `origin/main`에서 재생성 후 `origin/ops/tl`로 push
- 시작 전 작업 트리: clean
- force push: 하지 않음
- reset과 history rewrite: 하지 않음

## 입력 문서와 원본 확인

- `AGENTS.md`
- `docs/runbook/collaboration-automation.md`
- `docs/runbook/repository-onboarding.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/handoffs/PS-002/po-to-be.md`
- `scripts/validate-task-artifacts.py`
- `.github/workflows/validate-conventions.yml`
- `.github/workflows/notify-collaboration.yml`
- `.github/scripts/record-merged-pr.py`
- `scripts/validate-obsidian-record.py`

작업 산출물 검증의 원본은 `scripts/validate-task-artifacts.py`다. Repository Validation은 해당 스크립트를 `--from-stdin` 방식으로 호출한다.

작업 ID 정규식은 Discord 알림 워크플로와 Obsidian PR 기록 스크립트에도 중복되어 있어, `DOMAIN`과 `API` 접두사만 같은 범위로 정합화했다.

## 변경 범위

- `scripts/validate-task-artifacts.py`
- `scripts/test-validate-task-artifacts.py`
- `.github/scripts/record-merged-pr.py`
- `.github/workflows/notify-collaboration.yml`
- `docs/reports/BOOTSTRAP-006/tl-report.md`
- `docs/handoffs/BOOTSTRAP-006/tl-to-be.md`

## 변경하지 않은 범위

- `docs/product/**`
- `docs/domain/**`
- PS-002 요구사항
- DOMAIN-001 도메인 설계
- API-001 API 계약
- `backend/**`
- `frontend/**`
- `infra/**`
- 작업 ID 체계 전면 재설계
- 새로운 의존성 추가
- 관련 없는 GitHub Actions 리팩터링

## 구현 내용

- 기존 작업 ID 접두사를 유지했다.
- `DOMAIN`과 `API` 접두사를 `scripts/validate-task-artifacts.py`에 추가했다.
- `DOMAIN-001`, `DOMAIN-999`, `API-001`, `API-999`를 감지하도록 검증했다.
- `DOMAIN-01`, `DOMAIN-0001`, `DOMAIN001`, `API-01`, `API001`은 감지하지 않도록 검증했다.
- 기존 작업 ID인 `BOOTSTRAP-001`, `PS-002`, `ARCH-001`, `FOUNDATION-001`, `BUG-001`, `PERF-001`, `OPS-001`, `SEC-001`의 회귀가 없도록 검증했다.
- 임시 디렉터리에 `docs/reports/<TASK-ID>/`와 `docs/handoffs/<TASK-ID>/` fixture를 만들고 경로 검사를 수행하는 표준 라이브러리 기반 테스트를 추가했다.
- Obsidian PR 기록 스크립트와 Discord 알림 워크플로의 중복 작업 ID 정규식에도 `DOMAIN`과 `API`를 추가했다.

## 검증 결과

| 검증 | 명령 | 결과 |
| --- | --- | --- |
| DOMAIN/API와 기존 작업 ID 감지 | `py -3 scripts/test-validate-task-artifacts.py` | 통과 |
| DOMAIN/API 잘못된 형식 거부 | `py -3 scripts/test-validate-task-artifacts.py` | 통과 |
| DOMAIN-001 보고서·인수인계 경로 검사 | `py -3 scripts/test-validate-task-artifacts.py` | 통과. 임시 디렉터리 사용 |
| API-001 보고서·인수인계 경로 검사 | `py -3 scripts/test-validate-task-artifacts.py` | 통과. 임시 디렉터리 사용 |
| Python 문법 검사 | PowerShell에서 `.github/scripts/*.py`, `scripts/validate-task-artifacts.py`, `scripts/test-validate-task-artifacts.py` 파일 목록을 펼쳐 `py -3 -m py_compile` 실행 | 통과 |
| Discord payload 회귀 확인 | `py -3 scripts/validate-discord-payloads.py` | 통과 |
| Obsidian 기록 회귀 확인 | `py -3 scripts/validate-obsidian-record.py` | 통과 |
| 공백 오류 확인 | `git diff --check`, `git diff --cached --check` | 통과 |
| 변경 통계 확인 | `git diff --cached --stat` | 통과 |
| BOOTSTRAP-006 산출물 확인 | `Write-Output 'BOOTSTRAP-006' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| 커밋 메시지 확인 | `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "fix(validation): DOMAIN과 API 작업 ID 인식 추가"` | 통과 |

## 위험과 제한

- 실제 `docs/reports/DOMAIN-001` 또는 `docs/handoffs/DOMAIN-001` 디렉터리는 만들거나 커밋하지 않았다.
- API-001과 DOMAIN-001 자체의 산출물은 후속 작업에서 작성해야 한다.
- Issue 템플릿과 루트 운영 원칙의 작업 ID 예시는 이번 최소 수정 범위에서 변경하지 않았다.
- 작업 ID 접두사 정규식은 아직 여러 자동화 파일에 중복되어 있다. 이번 작업에서는 기존 구조를 유지하고 필요한 접두사 정합화만 수행했다.

## Git 결과

- 브랜치: `ops/tl`
- 커밋: 검증 후 작성
- push: 검증 후 수행
- PR: 생성 예정
- 자동 병합: 하지 않음

## PR 상태

PR 생성 전이다. 자동 병합하지 않는다.

## 다음 작업

### DOMAIN-001

- 역할: Backend Engineer
- 작업 브랜치: `feat/be`
- 선행 조건: PR `#5`와 BOOTSTRAP-006 PR 병합 확인
- 목표: PS-002 요구사항을 바탕으로 첫 번째 MVP 도메인 책임, 불변 조건, 값 표현과 경계를 구체화

### API-001

- 역할: Backend Engineer 또는 Tech Lead
- 선행 조건: PR `#5`와 BOOTSTRAP-006 PR 병합 확인
- 목표: PS-002 요구사항을 바탕으로 API 요청·응답, HTTP 상태, 오류 코드와 날짜 표현을 확정
