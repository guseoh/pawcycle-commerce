# 위험 기반 Lean Harness

## 1. 목적과 권위

- 상태: Approved
- 기준: PCC_V6

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

- `저장소 변경`: 코드·설정·문서·Runbook·test를 준비하고 검증한다. Production·Cloud·운영 DB·Secret·비용 리소스를 실행하지 않는다.
- `실제 운영 실행`: Production·Cloud·운영 DB·Secret·비용 또는 실제 restore/cutover/recovery를 수행한다.

실제 운영 실행은 항상 고위험이며 별도 명시적 사용자 승인이 필요하다. 저장소 준비 승인은 실제 운영 실행 승인으로 확장되지 않는다.

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
- Harness 문서·역할·Prompt 규칙 → Harness
- classifier/workflow 자체처럼 lane 선택 의미를 바꾸는 변경 → 전체 영향 lane
- 미분류 경로 → fail-safe 전체

PR metadata 오류는 최종 merge gate를 막을 수 있지만, 관련 제품 test 실행 자체를 불필요하게 직렬 차단하지 않는 방향으로 구성한다.

## 10. Spec ≠ Prompt

상세 분석과 최종 AI 실행 Prompt는 같은 산출물이 아니다.

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
