# GitHub MCP 에이전트 운영 Runbook

## 상태와 적용 범위

- 상태: Repository Prepared
- 작업 ID: `HARNESS-AGENT-003`
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Tech Lead

이 문서는 Codex에서 GitHub MCP를 사용할 때의 업무 흐름, Tool Allowlist, 보안 경계와 중단 조건을 정의한다. 실제 MCP 설치·인증·권한 부여나 GitHub 쓰기를 수행한 증거가 아니며, 연결 시점의 실제 Tool 이름과 권한은 별도 검증한다.

작업 등급, 저장소 준비와 실제 운영 실행 구분, 산출물 조건은 `docs/runbook/lean-harness.md`가 우선한다. 역할 책임과 Git 절차는 현재 경로 `AGENTS.md`와 역할 Skill을 따른다.

## 목적

GitHub 동적 상태를 조회할 때 사용자의 반복 복사·설명을 줄이되, 최소 권한과 명시적 증거 경로로 AI의 잘못된 쓰기·병합·재실행·Secret 접근을 차단한다.

## 사용 판단

GitHub MCP는 다음처럼 동적 원격 상태가 답의 정확성에 필요한 경우에만 사용한다.

- PR의 Draft·head/base·mergeable·변경 파일·리뷰 상태 확인
- Issue의 상태·범위·관련 PR 추적
- commit의 Workflow Run·Job·Step·최초 오류 분석
- 고정 ref의 저장소 파일·규칙·계약 탐색
- branch·commit·PR 관계 확인

로컬 코드 탐색과 테스트만으로 충분하거나 최신 GitHub 상태가 필요하지 않으면 MCP를 활성화하지 않는다.

## 기본 업무 흐름

```text
사용자 요청 또는 승인된 작업 명세
→ 작업 ID·등급·실행 구분·역할 확인
→ GitHub 동적 상태 필요성 판정
→ 저장소·대상 PR/Issue·고정 ref 확인
→ 최소 읽기 Tool만 활성화
→ 원본 evidence path 조회
→ 확인 사실·추론·미확인 분리
→ 로컬 변경과 관련 검증
→ 승인된 경우에만 role branch commit·push·Draft PR
→ CI·리뷰 원본 재확인
→ 사용자 병합 판단
```

MCP 조회는 로컬 구현과 검증을 대체하지 않는다. PR·Issue 본문, 리뷰와 CI 상태도 승인된 제품·API·ADR·데이터 계약을 자동 변경하지 않는다.

## Tool Allowlist

실제 MCP Tool 이름은 연결 시점에 확인한다. 아래 표는 허용 capability 계약이다.

### 기본 허용: 읽기 전용

| capability | 목적 | 필수 입력 |
| --- | --- | --- |
| repository metadata read | 저장소·기본 branch 확인 | 저장소 전체 이름 |
| branch·commit read | 기준 ref·head 관계 확인 | branch 또는 SHA |
| file·tree read | 고정 ref의 권위 원본 탐색 | 경로와 ref |
| commit compare read | 변경 범위 확인 | base·head |
| PR metadata·diff read | Draft·head/base·변경 확인 | PR 번호 |
| PR review·thread read | 차단 리뷰와 해결 상태 확인 | PR 번호 |
| Issue read | 후속 작업과 운영 경계 확인 | Issue 번호 |
| commit status·Workflow read | CI 상태와 실패 Run 식별 | commit SHA |
| Workflow Job·Step·Log read | 최초 오류와 수정·재실행 판단 | Run·Job ID |

읽기 결과는 저장소, ref, 관측 시각과 조회 한계를 함께 기록한다. 목록 API가 첫 페이지만 반환하면 전체 생명주기 총합으로 표현하지 않는다.

### 조건부 허용: 승인된 저장소 쓰기

다음 capability는 작업 명세가 대상·범위·branch를 명시한 경우에만 활성화한다.

- 승인된 role branch의 파일 생성·수정
- 일반 commit·push
- 요청된 Draft PR 생성과 본문·제목 수정
- 현재 HEAD에서 수정 완료가 확인된 review thread 해결
- 사용자 지시에 따른 PR Ready 전환

조건부 쓰기는 `main` 직접 변경, 권한 확대, 실제 운영 실행을 허용하지 않는다.

### 기본 금지

- PR 자동 병합 또는 승인 없는 병합
- branch·tag·release 삭제
- force push, reset, rebase, history rewrite
- Workflow rerun·cancel·dispatch
- Secret, variable, deploy key, environment, permission 변경
- repository visibility·ruleset·branch protection 변경
- Issue·PR·댓글·리뷰의 목적 없는 생성
- Production·AWS·운영 DB·Docker·SSH·SSM 실행

현재 작업에서 사용자가 특정 병합을 명시적으로 위임한 경우에도 대상 PR의 required CI, 차단 리뷰와 expected head SHA를 확인한 뒤 해당 작업에만 적용한다. 이 예외는 지속 정책으로 확대하지 않는다.

## 읽기 evidence path

### PR 상태 파악

```text
PR metadata
→ head/base·Draft·state
→ head SHA의 required CI
→ review thread
→ PR 본문의 실행·미실행·Production 경계
```

### Issue 추적

```text
Issue metadata·본문
→ 열린 체크 항목
→ 관련 PR·승인 원본
→ 실행 금지 범위
→ 다음 사용자 또는 역할 행동
```

### 권위 문서 탐색

```text
고정 ref
→ 제품 요구사항
→ API·ADR·데이터 계약
→ 경로별 AGENTS.md
→ 역할 Skill
→ 충돌 시 저장소 권위 순서 적용
```

### CI 실패 분석

```text
PR 최종 head SHA
→ Workflow Run 목록
→ 실패 Run
→ 실패 Job
→ 최초 실패 Step
→ Job Log의 최초 원인
→ 코드·문서 수정 / 환경 재실행 / 추가 결정 중 하나로 분류
```

Aggregate Job의 후속 실패보다 최초 원인을 우선한다. 같은 payload를 다시 실행하기 전에 transient·flaky 근거가 있는지 확인한다.

## CI 반복 실패 제어

동일 PR에서 CI가 3회 이상 실패하면 추가 rerun 또는 의미 없는 빈 commit을 중단한다.

```text
CI 반복 실행 중지
→ 실패 Run·Job·Log 수집
→ 최초 공통 원인 분류
→ 코드·계약·PR 본문·환경·flaky 구분
→ 원인 수정 또는 측정 가능한 재현
→ 새 HEAD 또는 원인이 바뀐 이벤트에서 재검증
```

- 코드·문서·validator 실패: 원인을 수정한 뒤 새 HEAD로 검증한다.
- PR 본문 metadata 실패: 본문을 수정하고 새 pull request 이벤트를 확인한다.
- 외부 네트워크·registry 장애: 로그 근거를 남기고 동일 코드의 제한된 재실행을 허용할 수 있다.
- 원인을 식별하지 못하면 반복 실행하지 않고 증거와 차단 상태를 보고한다.

## 보안·민감정보 경계

- Tool 입력, 출력, 로그, 문서와 PR에 Secret·token·private key·Webhook URL·원시 인증 값을 넣지 않는다.
- MCP 설정 파일에는 실제 credential 대신 환경 변수 이름과 placeholder만 둔다.
- 로그에 Secret 의심 문자열이 있으면 복사·인용·재출력하지 않고 조회를 중단한다.
- private repository 또는 운영 식별자를 포트폴리오 증거에 그대로 노출하지 않는다.
- GitHub 읽기 권한은 대상 저장소에 한정하고 조직·계정 전체 권한을 기본 요구하지 않는다.
- 쓰기 권한은 읽기 검증이 끝난 뒤 별도 단계로 최소화한다.

## 중단 조건

다음 중 하나면 Tool 호출과 저장소 쓰기를 중단하고 현재 증거를 보고한다.

- 대상 저장소, PR, Issue 또는 ref가 승인 입력과 다름
- branch·worktree·열린 PR·고유 commit 관계가 불명확함
- 승인되지 않은 제품·API·DB·보안 결정이 필요함
- Secret·개인정보·운영 원시 값 노출 가능성
- 예상하지 않은 쓰기·삭제·병합·권한 확대 Tool이 선택됨
- 실제 Production·Cloud·운영 DB·Secret·비용 작업이 필요함
- GitHub 결과와 최신 `main` 승인 원본이 충돌함
- 필요한 페이지·로그·권한이 없어 근거를 완성할 수 없음
- 동일 CI 실패 3회 이후 최초 원인을 식별할 수 없음

중단은 완료 실패를 숨기는 수단이 아니다. 확인한 사실, 미확인 항목, 마지막 안전 상태와 재개 조건을 남긴다.

## 검증

저장소 준비 단계:

- Runbook 내부 경로 존재 확인
- Skill frontmatter와 연결 경로 확인
- UTF-8, Markdown과 `git diff --check`
- Repository Validation과 AI 리뷰

실제 MCP 연결 단계:

- 대상 저장소 read-only 연결
- 기본 허용 capability별 성공·실패 확인
- 금지 capability가 노출되거나 호출되지 않는지 확인
- A·B·C·D Benchmark 각 3회
- 범위 밖 쓰기와 Production 접근 0건

저장소 준비 검증을 실제 MCP 인증·Tool Allowlist 적용 완료로 표현하지 않는다.

## 복구

이 Runbook과 Skill 변경은 일반 revert PR로 되돌린다. 실제 MCP credential·권한·운영 리소스는 이 작업에서 생성하지 않으므로 별도 운영 rollback 대상이 없다.
