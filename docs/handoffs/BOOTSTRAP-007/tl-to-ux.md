# BOOTSTRAP-007 TL → UX 인수인계

## 전달 목적

BOOTSTRAP-007에서 Repository Validation, 협업 알림, 병합 PR 기록 자동화가 `UX-001`과 `DATA-001` 작업 ID를 인식하도록 보완한 결과를 UX/UI Designer에게 전달한다.

이번 작업은 UX 설계가 아니라 UX-001을 시작하기 위한 하네스 보완이다.

## UX-001 시작 조건

- PR `#7 docs(domain): DOMAIN-001 구독 도메인 설계`가 `main`에 병합되어 있어야 한다.
- BOOTSTRAP-007 PR이 `main`에 병합되어 있어야 한다.
- 최신 `main`에서 UX/UI Designer 역할 브랜치 `design/ux`를 새로 준비한다.
- `main`에 DOMAIN-001 도메인 문서가 존재해야 한다.

## 작업 ID 검증 상태

- `UX-001`은 Repository Validation의 작업 산출물 검증에서 인식 가능하다.
- `DATA-001`도 같은 검증기에서 인식 가능하다.
- 기존 작업 ID 접두사 `BOOTSTRAP`, `PS`, `ARCH`, `FOUNDATION`, `BUG`, `PERF`, `OPS`, `SEC`, `DOMAIN`, `API`는 유지됐다.
- 작업 ID 형식은 `<PREFIX>-<3자리 숫자>`다.

## UX-001 산출물 경로

UX-001 작업에서는 다음 경로를 준비해야 Repository Validation을 통과할 수 있다.

```text
docs/reports/UX-001/
docs/handoffs/UX-001/
```

각 디렉터리에는 하나 이상의 Markdown 파일이 필요하다.

## 권장 UX-001 브랜치

```text
design/ux
```

## UX-001 주요 입력 문서

- `docs/product/PS-001-first-mvp-domain-scope.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/domain/glossary.md`
- `docs/domain/rules.md`
- `docs/handoffs/DOMAIN-001/be-to-tl.md`

## 실행한 검증 명령

```powershell
py -3 scripts/test-validate-task-artifacts.py
py -3 -m py_compile .github/scripts/record-merged-pr.py scripts/validate-task-artifacts.py scripts/test-validate-task-artifacts.py
py -3 scripts/validate-discord-payloads.py
py -3 scripts/validate-obsidian-record.py
git diff --check
git diff --stat
Write-Output 'BOOTSTRAP-007' | py -3 scripts/validate-task-artifacts.py --from-stdin
C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "fix(validation): UX와 DATA 작업 ID 인식 추가"
C:\Program Files\Git\bin\bash.exe -lc "printf '%s\n' 'UX-0001' 'UX001' 'UX-001' 'DATA-0001' 'DATA001' 'DATA-001' | grep -Eo '\\b(BOOTSTRAP|PS|ARCH|FOUNDATION|BUG|PERF|OPS|SEC|DOMAIN|API|UX|DATA)-[0-9]{3}\\b'"
```

검증 결과는 모두 통과했다. PR `#8` 생성 후 Commit and PR conventions와 Discord collaboration notification도 통과했다.

## 남아 있는 제한

- BOOTSTRAP-007은 `UX-001`과 `DATA-001`의 작업 ID 인식만 보완했다.
- 실제 `docs/reports/UX-001` 또는 `docs/handoffs/UX-001` 산출물은 만들지 않았다.
- UX-001 화면·사용자 흐름 설계와 DATA-001 데이터 모델 설계는 작성하지 않았다.
- API 계약, 애플리케이션 코드, 백엔드, 프론트엔드, 인프라는 변경하지 않았다.
- `QA`, `BE`, `FE` 등 아직 작업 ID로 승인되지 않은 접두사는 추가하지 않았다.
