# BOOTSTRAP-006 TL → BE 인수인계

## 전달 목적

BOOTSTRAP-006에서 Repository Validation과 로컬 작업 산출물 검증기가 `DOMAIN-001`과 `API-001` 작업 ID를 인식하도록 보완한 결과를 Backend Engineer에게 전달한다.

## DOMAIN-001 시작 조건

- PR `#5 docs(product): PS-002 MVP 요구사항 정리`가 `main`에 병합되어 있어야 한다.
- BOOTSTRAP-006 PR이 `main`에 병합되어 있어야 한다.
- 최신 `main`에서 Backend Engineer 역할 브랜치 `feat/be`를 새로 준비한다.
- `main`에 `docs/product/PS-002-first-mvp-requirements.md`가 존재해야 한다.

## 작업 ID 검증 상태

- `DOMAIN-001`은 Repository Validation의 작업 산출물 검증에서 인식 가능하다.
- `API-001`도 같은 검증기에서 인식 가능하다.
- 기존 작업 ID 접두사 `BOOTSTRAP`, `PS`, `ARCH`, `FOUNDATION`, `BUG`, `PERF`, `OPS`, `SEC`는 유지됐다.
- 작업 ID 형식은 `<PREFIX>-<3자리 숫자>`다.

## DOMAIN-001 산출물 경로

DOMAIN-001 작업에서는 다음 경로를 준비해야 Repository Validation을 통과할 수 있다.

```text
docs/reports/DOMAIN-001/
docs/handoffs/DOMAIN-001/
```

각 디렉터리에는 하나 이상의 Markdown 파일이 필요하다.

## 권장 DOMAIN-001 브랜치

```text
feat/be
```

## DOMAIN-001 주요 입력

- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/handoffs/PS-002/po-to-be.md`
- `docs/domain/glossary.md`
- `docs/domain/rules.md`
- `docs/reports/BOOTSTRAP-006/tl-report.md`

## 실행한 검증 명령

```powershell
py -3 scripts/test-validate-task-artifacts.py
py -3 -m py_compile .github/scripts/record-merged-pr.py scripts/validate-task-artifacts.py scripts/test-validate-task-artifacts.py
py -3 scripts/validate-obsidian-record.py
git diff --check
git diff --stat
```

검증 결과는 모두 통과했다.

## 남아 있는 제한

- BOOTSTRAP-006은 `DOMAIN-001`과 `API-001`의 작업 ID 인식만 보완했다.
- 실제 `docs/reports/DOMAIN-001` 또는 `docs/handoffs/DOMAIN-001` 산출물은 만들지 않았다.
- DOMAIN-001 도메인 설계, API-001 API 계약, 백엔드 코드는 작성하지 않았다.
- HTTP 상태, 오류 코드, API 응답 JSON은 API-001에서 확정해야 한다.
- 도메인 책임, 값 객체 필요성, 날짜 계산 책임은 DOMAIN-001에서 구체화해야 한다.
