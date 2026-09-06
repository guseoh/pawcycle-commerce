# GitHub 에이전트 운영 Runbook

## 목적

GitHub 동적 상태 조회와 승인된 저장소 쓰기를 최소 권한으로 수행하고, **잘못된 repository/ref/branch에 대한 write와 승인 없는 운영 side effect**를 차단한다.

작업 등급·Prompt·산출물 기준은 `docs/runbook/lean-harness.md`, 전역 권위와 Git 안전 경계는 루트 `AGENTS.md`가 우선한다.

## 기본 원칙

- 최신 GitHub 상태가 정확성에 필요할 때만 원격 Tool을 사용한다.
- 읽기로 해결할 수 있으면 쓰기 권한을 사용하지 않는다.
- Tool 이름이 비슷하다는 이유로 다른 capability를 대체 호출하지 않는다.
- 모든 write는 **repository + target + expected state**가 명확해야 한다.
- GitHub 작업은 Production·Cloud·운영 DB 실행 권한을 자동 포함하지 않는다.

## Read evidence path

### PR

```text
PR metadata
→ head/base/Draft/state
→ final head SHA의 CI
→ review/thread
→ diff와 PR evidence
```

### CI 실패

```text
Workflow Run
→ 실패 Job
→ 최초 실패 Step
→ 필요한 Log
→ code / contract / metadata / environment / flaky 분류
```

Aggregate failure보다 최초 원인을 우선한다. 같은 payload를 원인 없이 반복 실행하지 않는다.

### 권위 파일

고정 ref에서 현재 질문에 필요한 파일만 읽는다. 동적 GitHub 상태를 오래된 보고서나 과거 대화로 대체하지 않는다.

## 조건부 저장소 쓰기

승인된 task의 branch 작업, Draft PR, PR 본문 수정, 해결된 review thread 정리와 사용자가 특정 PR에 위임한 merge는 조건부로 수행할 수 있다.

### File write preflight

`create_file`, `update_file`, `delete_file` 또는 동등한 쓰기 Tool 직전에 매번 확인한다.

1. repository가 정확히 `guseoh/pawcycle-commerce`인지 확인
2. 현재 task branch 이름을 원격에서 확인
3. Tool 인자에 branch를 **명시적으로 전달**
4. branch가 `main`이면 중단
5. update/delete는 현재 blob SHA 사용
6. branch HEAD가 예상 기준에서 움직였으면 새 상태를 읽고 재판정

branch 인자 생략은 default branch write로 이어질 수 있으므로 허용하지 않는다.

### Ref write

branch 생성·이동은 기존 SHA와 목적을 확인한 경우에만 수행한다. force ref update, reset, rebase와 history rewrite는 일반 복구 수단으로 사용하지 않는다.

### PR write

- Draft PR 생성과 title/body 수정은 승인된 task scope에서 허용
- Ready 전환은 사용자 지시 또는 승인된 작업 절차가 있을 때만 수행
- review thread는 최신 HEAD에서 실제 문제가 해결됐음을 확인한 뒤 resolve
- 외부 reviewer 미실행을 `review 완료`로 표현하지 않음

### Merge

사용자가 현재 요청에서 특정 PR의 병합까지 명시적으로 위임한 경우에만 수행한다.

병합 직전:

- PR head SHA 재확인
- base drift와 mergeable 확인
- required/정의된 CI 성공 확인
- unresolved valid blocker 확인
- expected head SHA를 merge request에 사용

자동 merge 정책으로 확대하지 않는다.

## Workflow action

Workflow rerun/cancel/dispatch는 기본 쓰기 범위가 아니다.

- rerun: transient/flaky 근거 또는 원인 수정 뒤 필요한 경우
- dispatch: 사용자 승인된 저장소/운영 workflow에 한정
- Production deploy/restore/cutover workflow: 별도 실제 운영 실행 승인 없이는 dispatch 금지

CI가 반복 실패하면 같은 payload를 계속 돌리지 않고 최초 공통 원인을 분석한다.

## Release side effect 확인

`main` push를 만드는 자동화나 merge를 검토할 때는 해당 push가 다음 workflow를 trigger하는지 확인한다.

```text
main mutation
→ image publish?
→ deploy dispatch?
→ Production environment?
```

문서·학습 기록·metadata commit이 release chain을 시작하면 Harness/Workflow 결함으로 취급한다.

## 민감정보

Secret, credential, token, private key, Webhook URL, raw auth/payment 값과 원시 운영 데이터를 Tool 입력·출력·PR·보고서에 복사하지 않는다. 로그에 의심 값이 있으면 해당 값을 재출력하지 않고 필요한 최소 상태만 보고한다.

## 중단 조건

- repository/ref/branch가 승인된 대상과 다름
- write Tool에서 branch 인자를 명시할 수 없음
- target이 `main`인 file write
- 예상하지 않은 delete/force/permission/Secret capability 필요
- 승인되지 않은 제품·API·DB·보안 결정 필요
- Production·Cloud·운영 DB·Secret·비용 실행 필요
- 최신 상태를 확인할 권한·페이지·로그가 부족함

중단 시 마지막 안전 상태, 이미 발생한 side effect, 미확인 항목과 재개 조건을 숨기지 않는다.

## 검증

GitHub Harness 변경에는 최소한 다음 회귀를 둔다.

- docs-only 변경이 Production image/deploy chain을 시작하지 않음
- Harness-only 변경이 불필요한 application lane을 선택하지 않음
- public HTTP contract 변경은 Backend+Frontend를 선택함
- unknown path는 fail-safe함
- write 절차는 branch 명시와 non-main 경계를 문서·Skill에서 일관되게 유지함
