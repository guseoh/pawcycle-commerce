# PawCycle Commerce 에이전트 규칙

## 목적과 권한

PawCycle Commerce의 AI Harness는 문서를 많이 만드는 체계가 아니라, AI가 구현을 돕더라도 사용자가 **목표·범위·위험·검증·운영 실행**을 통제할 수 있게 하는 안전 경계다.

사용자는 Product Owner이자 Tech Lead이며 제품 정책, 외부 계약, 데이터·보안·비용 결정, PR 병합과 실제 운영 실행을 최종 승인한다. AI는 승인된 범위에서 조사·설계·구현·검증·리뷰와 사용자가 명시적으로 위임한 저장소 작업을 수행한다.

## 권위 순서

충돌이 있으면 다음 순서로 판단한다.

1. 현재 사용자의 명시적 지시
2. 최신 `main`의 승인된 요구사항·계약·ADR·도메인 규칙
3. 실제 코드·migration·설정·테스트·validator
4. 현재 경로의 `AGENTS.md`
5. 역할 문서와 Skill
6. 과거 보고서·인수인계·대화

GitHub branch, PR, HEAD, CI, review, Production 상태는 장기 문서나 과거 대화로 추정하지 않고 작업 시점에 다시 확인한다.

## 공통 안전 경계

- 승인된 사용자 목적을 하나의 coherent work unit으로 다룬다. 파일 수나 역할 수만으로 작업을 쪼개지 않는다.
- 제품 정책, API·DB·보안·비용·아키텍처 결정을 승인 없이 확정하지 않는다.
- Secret, credential, token, private key, Webhook URL, 개인정보와 원시 운영 데이터를 저장소·Prompt·PR·로그에 넣지 않는다.
- 저장소 변경과 실제 운영 실행을 분리한다. 저장소 준비 승인은 Production·Cloud·운영 DB·Secret·비용 리소스 실행 승인이 아니다.
- 실패, 미실행 검증, 리뷰 한계와 남은 위험을 성공으로 바꾸어 표현하지 않는다.
- 관련 없는 리팩터링·포맷·dependency·기술을 함께 추가하지 않는다.

작업 등급, 실제 운영 실행 관문, 조건부 보고서·QA·Runbook·ADR와 Final Lightweight Delta Prompt 기준은 `docs/runbook/lean-harness.md`가 권위 원본이다.

## 경로와 역할

역할은 기본 책임을 나타내며 승인된 cross-stack 작업을 인위적으로 분리하는 경계가 아니다.

- 작업이 한 영역에 한정되면 해당 역할과 경로 규칙만 적용한다.
- 사용자가 하나의 목적에 Backend·Frontend·Harness 등 여러 영역을 함께 승인했다면 하나의 작업에서 수정할 수 있다.
- 이 경우 수정하는 **각 경로의 `AGENTS.md` 규칙을 모두 적용**한다.
- 다른 영역의 새 제품 결정이 필요해지면 범위를 임의 확장하지 않고 중단한다.

`docs/roles/**`는 역할의 지속 책임과 결정권을, `.agents/skills/**`는 그 역할의 실행 절차를 정의한다. 공통 Git·PR·산출물 규칙을 각 문서에 복제하지 않는다.

## GitHub와 Git 쓰기 안전

`main`에 직접 제품·문서 작업을 작성하지 않는다. 새 저장소 작업은 최신 `main`에서 명시적인 task branch를 사용한다. branch 이름 관례는 `CONTRIBUTING.md`를 따른다.

GitHub 파일 쓰기 또는 ref 변경 Tool을 호출하기 직전에 다음을 확인한다.

1. 대상 저장소가 `guseoh/pawcycle-commerce`인지 확인한다.
2. 대상 branch를 명시적으로 확인한다.
3. 파일 create/update/delete에는 branch 인자를 생략하지 않는다.
4. 파일 create/update/delete의 대상 branch가 `main`이면 중단한다.
5. 예상 HEAD 또는 파일 SHA를 사용해 stale write를 거부한다.

`main` 내용 변경은 승인된 PR merge를 통해서만 수행한다. reset, rebase, force push, history rewrite를 복구 수단으로 사용하지 않는다.

사용자가 특정 PR의 병합까지 명시적으로 위임한 경우에는 최신 head SHA, 필수 CI, 차단 리뷰와 mergeable 상태를 다시 확인한 뒤 그 PR에 한해서 병합할 수 있다. 이것은 자동 병합 정책을 의미하지 않는다.

## 변경과 문서

지속적인 계약은 가장 가까운 권위 원본에 한 번만 기록한다.

- 제품 요구사항: `docs/product/**`
- 도메인 규칙: `docs/domain/**`
- 장기 기술 결정: `docs/adr/**`
- API 계약: `docs/api/**`
- 데이터·migration: `docs/data/**`와 DB 파일
- 반복 운영 절차: `docs/runbook/**`
- 장기 보존이 필요한 실행 증거: `docs/reports/**`
- 실제 다음 역할이 소비할 전달 정보: `docs/handoffs/**`

PR만으로 충분한 저장소 변경에 보고서나 인수인계를 형식적으로 추가하지 않는다.

## 검증

가장 작은 관련 검증에서 시작하고 변경 영향에 따라 확대한다.

- 내부 구현 변경: 관련 unit/integration regression
- 외부 HTTP 계약: Backend contract + Frontend contract
- DB·transaction·동시성: 실제 DB 통합 검증
- 공통 CI·classifier·workflow: Harness 회귀와 영향 받는 component lane
- 실제 운영·복구: 별도 승인된 Runbook과 적용 전후·복구 증거

CI Green만으로 의미상 정확성을 대신하지 않는다. 반대로 PR metadata나 문서 형식 오류가 제품 테스트 실행 자체를 불필요하게 막지 않도록 Harness를 설계한다.

## 리뷰

CodeRabbit, Codex Review와 ChatGPT 독립 검토는 결함 발견을 돕는 보조 수단이다. 지적은 최신 HEAD·계약·테스트와 대조해 유효성을 판정한다.

외부 AI reviewer가 파일 수·rate limit·서비스 상태 때문에 실행되지 않았다는 이유만으로 coherent PR을 분리하지 않는다. 대신 리뷰 미실행을 명시하고 위험에 맞는 독립 검토와 테스트로 보완한다.

고위험 변경은 구현 이유, 상태·데이터·transaction 경계, 실패 모드, 보호 테스트와 복구 경계를 설명할 수 있어야 병합을 권고한다.

## 완료 상태

- `Implemented`: 승인된 저장소 변경 완료
- `Verified`: 정의한 테스트·CI·통합 검증 완료
- `Production Verified`: 실제 운영 적용 전후와 필요한 복구 증거까지 확보

실제 운영을 실행하지 않은 작업에 `Production Verified`를 사용하지 않는다.
