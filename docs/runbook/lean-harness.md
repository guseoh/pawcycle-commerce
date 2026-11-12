# 위험 기반 Lean Harness

## 1. 목적과 권위

- 상태: Approved
- 기준: PCC_V9

이 문서는 PawCycle의 **작업 등급, 저장소 변경과 실제 운영 실행 구분, 최소 PR evidence, 조건부 산출물, AI Prompt, 검증과 feedback loop**의 권위 원본이다.

Harness의 목적은 문서와 체크리스트를 늘리는 것이 아니라 다음을 보장하는 것이다.

1. 사용자가 목표와 범위를 통제한다.
2. 위험한 결정과 운영 실행이 승인 없이 진행되지 않는다.
3. AI 결과가 테스트·리뷰·실행 evidence로 검증된다.
4. 실패·미실행·남은 위험이 숨겨지지 않는다.
5. 반복 마찰이 확인되면 규칙을 더 쓰기보다 guardrail 위치를 개선한다.

기존 역사 문서·branch·보고서는 소급 수정하지 않는다.

## 2. Coherent Work Unit

하나의 사용자 목적을 같은 승인·검증·rollback 경계로 묶는다.

- 파일 수, 계층 수, Backend/Frontend 같은 역할 수만으로 작업을 분할하지 않는다.
- 하나의 목적이 여러 경로를 가로지르면 관련 경로의 `AGENTS.md`를 모두 적용한다.
- 구현을 막는 승인 결정만 사용자에게 요청한다.
- 무관한 리팩터링·dependency·architecture는 함께 넣지 않는다.
- AI reviewer의 파일 수 제한 때문에 coherent PR을 억지로 쪼개지 않는다.

## 3. 작업 등급

| 등급 | 기준 | 기본 검증 |
| --- | --- | --- |
| 경량 | 외부 계약·데이터·보안·운영 영향이 없는 작은 변경 | 가장 작은 관련 검사 |
| 일반 | 하나의 사용자 목적을 완성하는 저장소 변경 | 관련 test와 CI |
| 고위험 | 인증·결제·재고·migration·공통 CI·복구·운영·데이터·capacity·scale 위험 | 관련 전체 검증과 명시적 복구 경계 |

불명확하면 일반으로 시작하고 위험이 발견되면 승격한다. 문서 비용을 줄이기 위해 등급을 낮추지 않는다.

## 4. 실행 구분

| 실행 유형 | 목적과 기본 결과 | 실행 경계 |
| --- | --- | --- |
| `Read / Analyze` | 코드·Issue·PR·CI·허용된 운영 근거의 조사, 원인 분석·판정 | 기본적으로 외부 상태를 변경하지 않는다. 분석만으로 완료할 수 있다. |
| `Design / Plan` | 설계·대안·기술 판단·필요한 실행 Prompt | 대화 내 결과가 기본이다. 문서 작성·저장 위치가 승인된 경우에만 그 범위에 기록한다. |
| `Repository Change` (`저장소 변경`) | 승인된 코드·테스트·설정·문서 변경과 검증 | 명시적 Task Branch를 사용한다. commit/push/Draft PR 등 결과물의 권한은 루트 AGENTS를 따른다. |
| `Operations Execution` (`실제 운영 실행`) | Production·Cloud·운영 DB·Secret·비용 자원, 실제 부하·장애 주입·restore/cutover/recovery | 항상 고위험이며 대상·실행·중단·복구 경계에 대한 별도 명시 승인이 필요하다. |

직접 운영 분석·성능 측정 근거의 해석·리버스 엔지니어링도 독립적인 결과다. 기존 근거를 읽는 분석과 실제 부하·장애 주입은 구분한다. 저장소 준비 승인은 실제 운영 실행 승인으로 확장되지 않는다. 외부 기록과 상태 변경의 권한은 루트 `AGENTS.md`가 소유한다.

## 5. 작업 ID

작업 ID는 **추적용 식별자**이지 제품·기술 taxonomy를 CI가 관리하기 위한 장치가 아니다.

- 사람이 읽을 수 있는 대문자 segment와 숫자 suffix를 사용한다. 예: `AUTH-004`, `OPS-OCI-002`, `BACKEND-REFACTOR-004`, `PERF-PH10-008`.
- 새 업무 family를 추가하기 위해 validator·Discord·PR 기록 parser를 수정해야 하는 구조를 만들지 않는다.
- Validator는 식별자의 구조와 일관성만 확인하고 허용 업무 목록을 유지하지 않는다.

## 6. 최소 PR 계약

PR은 사용자가 변경과 위험을 판단할 수 있는 최소 정보만 필수로 둔다.

```text
작업
- 작업 ID
- 작업 등급
- 실행 구분

변경
- 목적
- 포함 범위
- 제외 범위

검증
- 실행 결과
- 실패·미실행

위험
- 남은 위험
- 복구 경계
```

역할, 중요한 결정, 외부 계약 영향, QA, migration, reviewer 한계는 실제로 필요한 경우에만 추가한다. `병합 판단`처럼 동적 상태가 빠르게 낡는 구획을 장기 필수 형식으로 만들지 않는다.

## 7. 조건부 산출물

### 보고서

보고서는 기본 생략한다. 다음 중 하나일 때만 `docs/reports/<TASK-ID>/`에 작성한다.

- 실제 Production·Cloud·운영 DB·Secret·비용 실행
- migration, restore, rollback 또는 데이터 변환 결과
- PR만으로 보존하기 어려운 장기 판정
- 후속 작업의 권위 입력이 되는 실행 evidence
- 사용자가 별도로 요구

실제 운영 실행 보고서에는 최소한 다음 evidence가 필요하다.

- 명시적 승인 근거
- 적용 전 확인
- 적용 후 확인
- 독립 확인
- rollback/recovery
- 미실행 항목
- 남은 위험

### QA

다음에 필요하다.

- 새 사용자 흐름
- 인증·인가
- 결제·재고·구독 상태 전이
- migration/data-loss 위험
- 여러 모듈 통합
- 실제 결함 독립 재현·재검증

독립 확인은 별도 QA, 독립 CI 환경 또는 사용자가 승인한 동등한 evidence로 충족할 수 있다.

### Runbook / ADR / Handoff

- 반복 실행·중단·복구 절차가 있을 때 Runbook
- 지속적인 기술 선택과 trade-off가 있을 때 ADR
- 실제 다음 역할·운영자가 결과를 입력으로 사용할 때 Handoff

생략 사유 문서를 별도로 요구하지 않는다.

## 8. Validator 경계

Validator는 **기계적으로 판별 가능한 안전 누락**만 차단한다.

차단 가치가 높은 것:

- 작업 ID/등급/실행 구분 누락 또는 충돌
- 실제 운영 실행의 보고서·필수 evidence 누락
- Secret/민감정보 노출 가능성
- 변경 분류 누락으로 필요한 CI lane이 실행되지 않는 경우
- 명백한 malformed PR body 또는 invalid workflow contract

기본적으로 차단하지 않을 것:

- 한국어 사용 여부와 문장 종결 형태
- 특정 업무 prefix allowlist
- 선택적 PR 소제목
- Prompt 길이
- reviewer 서비스 실행 여부
- QA·ADR·Handoff가 실제 필요한지에 대한 의미 판단

Validator는 품질 판단을 대체하지 않는다.

## 9. CI 선택 원칙

CI는 **변경이 영향을 줄 수 있는 동작**을 검증한다.

- Backend 내부 변경 → Backend
- Frontend 변경 → Frontend
- Backend 외부 HTTP contract → Backend + Frontend
- Production runtime/contract → Production
- Harness 문서·역할·등록된 Skill의 Markdown 문서 → Harness
- `qa/**`, `infra/performance/**`의 Markdown 근거·설명 → Harness
- QA 실행·fixture·Compose와 성능 workload·계측·실행 입력 → 보수적으로 전체 영향 lane 유지; 기존 전용 contract workflow가 해당 경로를 다루면 함께 확인
- classifier/workflow 자체처럼 lane 선택 의미를 바꾸는 변경 → 전체 영향 lane
- 미분류 경로 → fail-safe 전체

등록되지 않은 Skill, Skill 실행 코드·데이터, QA/성능의 새 형식 등 판단되지 않은 입력은 fail-safe 전체 검증을 유지한다. 전체 lane 선택이 실제 성능 부하 실행이나 모든 QA 스크립트 검증을 대신하지는 않는다. 변경한 실행 입력의 전용 contract·syntax 검사와 측정 필요성을 별도로 판단한다.

PR metadata 오류는 최종 merge gate를 막을 수 있지만, 관련 제품 test 실행 자체를 불필요하게 직렬 차단하지 않는 방향으로 구성한다.

## 10. Spec ≠ Prompt

상세 분석과 최종 AI 실행 Prompt는 같은 산출물이 아니다. 아래 Spec → Delta → Prompt 흐름은 Codex 구현을 수행하거나 전달하는 경우에만 적용한다. Read / Analyze 또는 Design / Plan으로 목적을 달성했다면 실행 Prompt·Task Branch·PR 없이 분석이나 판단으로 종료할 수 있다.

### 상세 분석 / 작업 명세

필요하면 길어도 된다. 문제 배경, 대안, trade-off, API/DB/동시성 의미와 검증 설계를 충분히 설명한다.

### Final Lightweight Delta Prompt

실행 직전에는 이미 결정된 배경과 공통 규칙을 제거하고 **현재 작업에서 실제로 달라질 내용만** 남긴다.

필수 흐름:

```text
문제
→ 최신 상태
→ 승인 입력
→ 목표와 Delta
→ 포함 / 제외
→ Acceptance Criteria
→ 검증
→ 중단 조건
→ Git / PR 결과물
```

공통 안전·Git 절차는 현재 경로 `AGENTS.md`를 따른다고 한 번 참조한다. PCC 원문, 역할 문서, Skill, PR template 전체를 Prompt에 복사하지 않는다.

모델 이름, 추론 수준, subagent 사용 판단과 작업 등급 설명은 실행 Prompt 밖의 orchestration metadata로 관리한다.

Prompt 길이는 품질의 대리 지표가 아니므로 CI가 차단하지 않는다.

## 11. 후속 수정 Prompt

리뷰나 CI에서 유효한 문제가 발견되면 원래 명세를 다시 전송하지 않는다.

```text
현재 HEAD / finding
→ 왜 유효한지
→ 필요한 동작 변화
→ 보호할 기존 계약
→ regression test
→ 검증 / 중단 조건
```

같은 목적의 유효 finding은 가능한 한 하나의 후속 수정으로 묶는다.

## 12. Review

CodeRabbit, Codex Review, ChatGPT와 사람 리뷰는 defect-discovery input이다.

- finding은 최신 HEAD와 계약에 대조한다.
- outdated/false-positive는 근거를 남기고 수용하지 않는다.
- reviewer가 파일 수·rate limit·서비스 상태로 실행되지 않았으면 `review 미실행`으로 기록한다.
- 외부 reviewer 미실행을 숨기지 않지만, 그 서비스 제한에 맞추려고 작업 구조를 왜곡하지 않는다.
- 고위험 작업은 독립 검토와 관련 regression evidence를 강화한다.

## 13. GitHub Tool 쓰기

구체적인 branch write precondition은 루트 `AGENTS.md`와 `docs/runbook/github-mcp-agent.md`를 따른다.

핵심은 다음과 같다.

- write 전 repository와 target branch를 다시 확인
- file write에서 branch 생략 금지
- 일반 작업의 direct `main` write 금지
- expected SHA/file SHA로 stale write 방지
- 특정 PR merge는 사용자가 명시 위임하고 최신 CI/review/head를 다시 확인한 경우에만 수행

## 14. Harness Feedback Loop

Harness 변경은 실제 반복 문제 또는 재현 가능한 실패를 근거로 한다.

좋은 feedback loop:

```text
실제 실패/마찰
→ 원인 분류
→ 규칙 추가가 필요한지 판단
→ 가능하면 실행 guardrail/CI/test로 이동
→ 같은 유형 재발 여부 확인
```

한 작업의 취향이나 일회성 예외를 공통 규칙으로 승격하지 않는다. 규칙을 추가할 때는 기존 중복 규칙을 제거할 수 있는지도 함께 본다.

## 15. 복구와 비소급

- 저장소 변경은 일반 revert PR로 복구한다.
- 실제 운영 실행은 승인 Runbook의 rollback/recovery를 따른다.
- reset, rebase, force push, history rewrite로 복구하지 않는다.
- 과거 정상 산출물과 역사 문서는 현재 규칙에 맞추기 위해 소급 수정하지 않는다.

## 16. LLM Wiki Capture — 작업 종료의 경량 맥락 보존

PawCycle의 `full` 캡처 정책은 이 Harness의 **작업 종료 단계**로 적용한다. 이 단계는 `Read / Analyze`, `Design / Plan`, `Repository Change`, `Operations Execution`의 권한·작업 등급을 바꾸지 않는다. 이 정책으로 저장소 밖의 쓰기 또는 운영 실행을 새로 승인받은 것으로 간주하지 않는다. 공통 기록 규격은 [중앙 Wiki capture policy](https://github.com/guseoh/cs/blob/main/Wiki/capture-policy.md)와 [Wiki handoff](https://github.com/guseoh/cs/blob/main/Wiki/handoff.md)를 참조한다.

### 종료 시점과 입력

작업을 **실제로 마치거나**, 검증·승인·권한 문제로 중단했을 때, 사용자가 매번 "Wiki에 기록"이라고 지시하지 않아도 다음을 간결하게 평가한다. 이 평가는 별도 설계 문서나 PR 생성을 요구하지 않으며, **진행 중인 AI 대화를 감시한다는 뜻도 아니다**.

1. **확인된 사건:** 무엇을 설계/구현/검증/조사했는지, 근거가 되는 Task ID·PR·commit·CI·ADR와 실제 검증 범위를 확인한다. 코드가 없는 설계도 기록할 수 있으나 `proposed`와 `accepted`를 혼동하지 않는다.
2. **분류:** 사소한 수정, 테스트 실패/재검증, 조사 결과도 의미 있는 완료 단위면 한두 문장의 `Wiki/journal/pawcycle/YYYY-MM.md` 후보로 분류한다. 장기 판단이 변경된 경우에만 별도 Decision 후보로 기록하고, 재사용 가능한 실패 분석은 Lesson 후보로 분리한다.
3. **중복과 출처:** `repo#PR`, commit SHA, Task ID와 기존 Wiki ID를 확인한다. 중앙 Actions가 수집하는 공개 병합 PR의 단순 활동 정보는 다시 복제하지 않고 필요하면 근거 링크와 추가 판단만 남긴다.
4. **정확성·공개성:** Repo/CI/PR evidence에서 확인되지 않은 배포 결과는 `unverified`로 유지한다. Production 적용은 별도 승인과 사후 검증 근거 없이 성공으로 기록하지 않는다. 공개 Wiki로 반출할 수 없는 사실은 **게시하지 않는다**.
5. **저장 또는 전달:** 현재 요청·승인된 작업 산출물·도구 권한이 중앙 저장소에 검토 PR을 작성할 수 있는 범위라면 **review PR만 제출**한다. 읽기/설계 요청만으로 외부 Issue/PR 쓰기를 확대하지 않는다. 권한/공개성/검증이 부족하면 기록 후보를 내부적으로 정리하고 필요한 경우 간단한 미반영 사유를 최종 결과에 남긴다. 중앙 Wiki의 자동 GitHub PR 수집과 이 절차는 서로 보완하지만 동일한 동작이 아니다.

### 산출물 및 완료 기준

- **Journal은 가벼운 원천 사건 이력**으로서 `docs/reports`, `docs/handoffs`, `docs/adr`의 작성 조건을 변경하지 않는다. PR 하나로 충분한 변경에 별도 보고서를 추가하지 않는다. 승인되지 않은 새 기술 선택을 Decision의 `accepted`로 승격하지 않는다.
- 결과 보고에서 Wiki 상태는 `review PR 제출`, `기존 PR 자동 수집 대상`, `미반영(권한/공개/근거 제한)`, `기록 대상 없음` 등으로 사실에 맞게 구분한다. **PR을 생성한 것과 Wiki main에 기록이 확정된 것은 별개**다.
- Wiki 기록 실패 또는 검토 PR 생성 실패는 이미 통과한 **제품 코드 검증/실제 운영 검증을 소급해서 실패로 만들지 않는다**. 실패 이유를 명시하되 무한 재시도·승인 우회·다른 저장소 직접 main write는 하지 않는다.
- 위 절차는 공통 정책의 한 번뿐인 정의이며 역할·Skill·Codex Prompt에 장문 복제하지 않는다. 반복되는 누락/중복/오분류는 §14 Feedback Loop에서 evidence를 검토하고 필요한 경우 Wiki parser, test 또는 CI guardrail로 개선한다.
