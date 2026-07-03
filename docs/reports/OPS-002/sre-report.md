# OPS-002 SRE 작업 보고서

## 작업 정보

- 작업 ID: `OPS-002`
- 역할: Platform/SRE
- 기준 브랜치: `main`
- 작업 브랜치: `ops/sre`
- PR 대상: `main`
- 작업 저장소 경로: `C:\Users\guseo\IdeaProjects\pawcycle-commerce`
- 원격 저장소: `https://github.com/guseoh/pawcycle-commerce.git`
- 자동 병합: 하지 않음

## 작업 목적

Discord Webhook 전송이 `HTTP 403`으로 실패하는 문제를 완화하기 위해 Discord HTTP API가 식별할 수 있는 명시적 `User-Agent` 헤더를 전송 요청에 추가한다.

요청 헤더는 자동 테스트로 보호하고, PR 병합 후 `main`의 `Collaboration Notification` 수동 실행으로 실제 Discord 채널 수신 여부를 확인할 수 있게 운영 인수인계를 남긴다.

## 승인된 입력

- 사용자 요청: OPS-002 Discord Webhook `HTTP 403` 실패 수정
- 사용자 승인: 병합 완료된 PR #9의 잔여 원격 `ops/sre` 브랜치 삭제
- 선행 근거: PR #9 `fix(discord): 협업 알림 실패 감지와 연결 테스트 추가`가 `main`에 병합됨
- 범위 확장: PR #10의 한글 본문 손상 복구, UTF-8 PR 본문 규칙, Repository Validation 문자 손상 검증 추가 승인
- Secret 정책: Webhook URL, 오류 응답 본문, Secret 값은 출력하지 않음
- 신규 의존성: 추가하지 않음

## 시작 전 저장소 확인

- `git rev-parse --show-toplevel`: `C:/Users/guseo/IdeaProjects/pawcycle-commerce`
- `origin`: `https://github.com/guseoh/pawcycle-commerce.git`
- 시작 전 작업 트리: clean
- `main` 동기화: `origin/main`으로 fast-forward 완료
- 최신 `main`: `0a26be8 docs(obsidian): PR #9 기록 추가`
- 열린 PR: GitHub API 기준 0개
- PR #9 상태: closed, merged
- PR #9 head SHA: `f009a9e0684445187b053a875958f19a50b6d10d`

## 역할 브랜치 정리

원격 `ops/sre`는 병합 완료된 PR #9의 head SHA와 일치했지만, squash merge 때문에 Git 조상 관계로는 `main`에 포함된 것으로 보이지 않았다.

사용자 승인에 따라 다음을 수행했다.

- 삭제 전 원격 `ops/sre`: `f009a9e0684445187b053a875958f19a50b6d10d`
- 원격 `ops/sre` 삭제: 완료
- `git fetch origin --prune`: 완료
- 로컬 `ops/sre`: 존재하지 않음 확인
- 최신 `main`에서 새 `ops/sre` 생성: 완료

force push, reset, history rewrite는 수행하지 않았다.

## 기존 HTTP 403 실패 증거

GitHub Actions `Collaboration Notification` 최근 실패 실행을 확인했다.

- run ID: `28654703785`
- workflow: `Collaboration Notification`
- event: `push`
- branch: `main`
- head SHA: `28ea9d8e54c7bb623078e8b860f46a85484da498`
- title: `fix(discord): 협업 알림 실패 감지와 연결 테스트 추가 (#9)`
- started: `2026-07-03T10:29:15Z`
- conclusion: `failure`
- failed job: `Discord collaboration notification`
- job ID: `84980868689`

확인한 실패 로그:

```text
Discord 알림 전송 실패: HTTP 403
Process completed with exit code 1.
```

Webhook URL, 오류 응답 본문, Secret 값은 조회하거나 기록하지 않았다.

## 원인 분석

확정한 사실:

- Discord가 HTTP `403`을 반환했다.
- Secret 누락 경로가 아니라 전송 요청이 Discord 서버에서 거부된 상태였다.
- 기존 요청 헤더에는 `Content-Type: application/json`만 있었고 명시적 `User-Agent`가 없었다.
- 기존 실패 로그는 Webhook URL과 오류 응답 본문을 출력하지 않았다.

추정한 범위:

- Discord 또는 중간 보호 계층이 식별 가능한 HTTP 클라이언트 정보가 없는 요청을 차단했을 가능성이 있다.
- `User-Agent` 추가 후 실제 운영 성공 여부는 PR 병합 후 `main`에서 수동 워크플로를 실행해야 확정할 수 있다.

## 변경 범위

- `.github/scripts/send-discord-notification.py`
- `.github/workflows/validate-conventions.yml`
- `.github/pull_request_template.md`
- `AGENTS.md`
- `scripts/validate-pr-body-encoding.py`
- `scripts/test_validate_pr_body_encoding.py`
- `scripts/test_send_discord_notification.py`
- `docs/runbook/collaboration-automation.md`
- `docs/reports/OPS-002/sre-report.md`
- `docs/handoffs/OPS-002/sre-to-tl.md`

## 변경하지 않은 범위

- Webhook URL 조회 또는 출력
- Discord 서버, 채널, Webhook 생성 또는 재발급
- GitHub Secret 직접 수정
- Discord payload 디자인 변경
- Discord 알림 이벤트 종류 변경
- CI 성공·미통과 알림 구조 변경
- GitHub Actions 전체 재설계
- Obsidian 기록 구조 변경
- 모든 인코딩 문제를 추측으로 탐지하는 복잡한 문자 분석기 도입
- UX-001, DATA-001 작업
- 제품·도메인·API 설계
- `backend/**`, `frontend/**`, `infra/**`
- 신규 외부 의존성

## 적용한 변경

`build_request()`에서 기존 JSON UTF-8 인코딩과 POST 방식은 유지하고, 다음 요청 헤더를 보낸다.

```text
Content-Type: application/json
User-Agent: DiscordBot (https://github.com/guseoh/pawcycle-commerce, 1.0)
```

`User-Agent` 값은 코드에 고정된 공개 정보만 사용한다.

- 저장소 공개 URL 포함
- 정적 버전 값 포함
- Secret, Webhook URL, 사용자 개인정보, 실행별 동적 값 미포함

기존 성공·실패·재시도 정책은 변경하지 않았다.

- HTTP `2xx`: 성공
- HTTP `400`, `401`, `403`, `404`: 즉시 실패
- HTTP `429`, `5xx`, 네트워크 오류: 기존 제한 내 재시도
- Webhook URL과 오류 응답 본문: 로그 미출력

## 테스트 변경

`scripts/test_send_discord_notification.py`에 다음 확인을 추가했다.

- 생성된 요청이 `POST`인지 확인
- `Content-Type: application/json` 유지 확인
- `User-Agent` 존재 확인
- `User-Agent`가 `DiscordBot (` 형식으로 시작하는지 확인
- `User-Agent`에 `https://github.com/guseoh/pawcycle-commerce`가 포함되는지 확인
- `User-Agent`에 버전 값이 포함되는지 확인
- 요청 헤더에 Webhook URL 또는 테스트용 Webhook 고유 조각이 포함되지 않는지 확인

기존 HTTP `204`, `400`, `401`, `403`, `404`, `429`, `500`, 네트워크 오류, Secret 누락, 로그 비노출 테스트는 유지했다.

## PR 본문 UTF-8 확장 변경

PR #10 생성 후 원격 본문을 다시 읽어 확인한 결과 한글 본문이 다수의 `?` 문자로 치환된 손상 패턴을 발견했다.

- 원격 본문은 UTF-8 strict decoding 자체는 성공했다.
- U+FFFD replacement character와 NUL 문자는 없었다.
- `??` 반복 패턴이 다수의 제목과 문장에 걸쳐 나타났다.
- 손상 원인 범위는 Windows 또는 명령 전달 과정에서 한글 UTF-8 본문이 `?`로 치환된 것으로 판단했다.

이에 따라 다음 공통 방어를 추가했다.

- `AGENTS.md`에 PR 본문 UTF-8 파일 작성과 `--body-file` 사용 규칙 추가
- 기존 `.github/pull_request_template.md`를 공통 PR 템플릿으로 정리
- `scripts/validate-pr-body-encoding.py` 추가
- `scripts/test_validate_pr_body_encoding.py` 추가
- `Repository Validation` 워크플로에 PR 본문 인코딩 검증 단계 추가
- Runbook에 Windows PowerShell 기준 UTF-8 PR 작성·수정·원격 확인 절차 추가

검증기는 다음을 차단한다.

- UTF-8 strict decoding 실패
- U+FFFD replacement character
- NUL 문자
- 여러 줄에 걸친 명백한 반복 `??` 문자 손상 패턴

검증기는 정상 한글 Markdown, 정상 영문 Markdown, 한글 질문 문장, 단일 물음표, 코드 블록 내부의 일반적인 물음표 사용을 허용한다.

실패 메시지는 UTF-8 Markdown 파일과 `gh pr create/edit --body-file` 사용, 원격 본문 재확인을 안내하되 PR 본문 전체를 출력하지 않는다.

## 검증 결과

| 검증 | 명령 | 결과 |
| --- | --- | --- |
| 시작 전 단위 테스트 | `py -3 -m unittest scripts.test_send_discord_notification` | 통과, 13 tests |
| 변경 후 단위 테스트 | `py -3 -m unittest scripts.test_send_discord_notification` | 통과, 15 tests |
| PR 본문 인코딩 검증기 테스트 | `py -3 -m unittest scripts.test_validate_pr_body_encoding` | 통과, 12 tests |
| 브랜치와 작업 트리 확인 | `git status --short --branch` | 통과 |
| 공백 오류 확인 | `git diff --check` | 통과 |
| 변경 통계 확인 | `git diff --stat` | 통과 |
| Discord payload fixture 회귀 | `py -3 scripts/validate-discord-payloads.py` | 통과 |
| Python 문법 검사 | `py -3 -m py_compile .github/scripts/build-discord-payload.py .github/scripts/send-discord-notification.py scripts/test_send_discord_notification.py scripts/validate-pr-body-encoding.py scripts/test_validate_pr_body_encoding.py` | 통과 |
| OPS-002 산출물 확인 | `Write-Output 'OPS-002' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| 커밋 메시지 확인 | `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "fix(discord): Webhook 요청 User-Agent 추가"` | 통과 |
| 확장 커밋 메시지 확인 | `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "fix(collaboration): PR 본문 UTF-8 검증 추가"` | 통과 |

## 실행하지 못한 검증과 이유

- 실제 Discord 채널 수신 확인: PR 병합 전에는 `main`의 최신 워크플로와 GitHub Repository Secret을 사용하는 운영 확인을 완료했다고 판단하지 않는다.
- Secret 값 검증: Secret 값 조회와 출력은 작업 범위 밖이며 보안 정책상 수행하지 않는다.

## 롤백 메모

문제가 발생하면 이 PR의 변경을 revert한다.

예상 영향은 Discord Webhook 요청 헤더에서 명시적 `User-Agent`가 제거되는 것이다. 기존 전송 성공·실패·재시도 정책은 이번 변경에서 바꾸지 않았으므로 롤백 범위도 요청 헤더와 문서·테스트 추가분으로 제한된다.

## 남은 위험과 제한

- Webhook이 삭제됐거나 비활성화된 경우 `User-Agent` 추가 후에도 HTTP `403`이 계속 발생할 수 있다.
- GitHub Secret에 오래된 Webhook URL이 등록된 경우 실제 전송은 계속 실패한다.
- Discord 서버 또는 채널 권한이 변경된 경우 실제 수신은 실패할 수 있다.
- PR 병합 전에는 실제 Discord 수신 완료를 확정하지 않는다.
- 반복 `??` 기반 검증은 명백한 손상 패턴을 차단하기 위한 방어이며, 모든 가능한 인코딩 문제를 완전하게 판별하지는 않는다.
- PR 생성 또는 수정 직후 원격 본문 확인을 생략하면 손상된 본문이 늦게 발견될 수 있다.

## PR 병합 후 운영 확인 절차

1. GitHub 저장소에서 `Actions`를 연다.
2. `Collaboration Notification` 워크플로를 선택한다.
3. `Run workflow`를 선택한다.
4. `Use workflow from: main`을 유지한다.
5. 실행 후 `Send Discord notification` 로그에서 HTTP `2xx` 성공 메시지를 확인한다.
6. Discord 채널에서 연결 테스트 Embed 수신을 확인한다.

성공 조건은 다음 두 가지를 모두 만족하는 것이다.

```text
GitHub Actions:
Discord 알림 전송 완료: HTTP 2xx

Discord 채널:
PawCycle Commerce 알림 연결 테스트 Embed 수신
```

## Git 결과

- 브랜치: `ops/sre`
- 주요 변경 커밋: `5ec4325e224648e6c47b0513df05c8a1474178ce`
- 주요 변경 커밋 메시지: `fix(discord): Webhook 요청 User-Agent 추가`
- push: `origin/ops/sre` 반영 완료
- PR: `#10` 생성 완료
- PR URL: `https://github.com/guseoh/pawcycle-commerce/pull/10`
- PR 상태: Open, Draft
- mergeable: MERGEABLE
- PR 제목: `fix(collaboration): Discord 요청과 PR 본문 UTF-8 보완`
- PR 본문 복구: `.git\PR-10-body.md` UTF-8 파일과 `gh pr edit 10 --body-file`로 완료
- 원격 PR 본문 확인: 한글 정상 표시, U+FFFD 없음, NUL 없음, 반복 `??` 손상 패턴 없음
- 자동 병합: 하지 않음

보고서 자신을 최종 갱신하는 커밋 SHA는 재귀적으로 기록하지 않는다.

## PR CI 상태

PR 생성 후 체크 상태를 확인했다.

- Commit and PR conventions: 통과
- Discord collaboration notification: 실패

실패한 Discord 알림 체크:

- run ID: `28665868262`
- event: `pull_request_target`
- head branch: `ops/sre`
- head SHA: `5ec4325e224648e6c47b0513df05c8a1474178ce`
- conclusion: `failure`

확인한 실패 로그:

```text
Discord 알림 전송 실패: HTTP 403
Process completed with exit code 1.
```

이 실패는 `pull_request_target` 워크플로가 보안상 기본 브랜치 스크립트를 checkout하기 때문에, PR 브랜치의 `User-Agent` 수정이 PR 알림 체크 실행에는 아직 적용되지 않는 구조와 관련된다.

따라서 PR 단계에서는 `Commit and PR conventions` 통과와 로컬 검증 통과를 기준으로 수정 내용을 검토하고, 실제 Discord 전송 해결 여부는 PR 병합 후 `main`의 `Collaboration Notification` 수동 실행으로 확인해야 한다.

## OPS-002 범위 확장 후 PR 상태

확장 커밋과 PR 본문 복구 후 다음 상태를 확인했다.

- 확장 커밋: `687837493ccd4209cb8719e1f81a8ec1c641c6ad`
- 확장 커밋 메시지: `fix(collaboration): PR 본문 UTF-8 검증 추가`
- PR head: `ops/sre`
- PR base: `main`
- Repository Validation 최신 실행: 통과
- PR 본문 인코딩 검증: 통과
- Discord collaboration notification: HTTP `403` 실패 유지

Discord 알림 실패는 `pull_request_target` 이벤트가 기본 브랜치의 기존 전송 스크립트를 checkout하는 구조와 관련된다. 이 PR의 실제 Discord 전송 해결 여부는 병합 후 `main`에서 수동 실행으로 확인한다.
