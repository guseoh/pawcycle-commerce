# PawCycle Commerce 에이전트 규칙

## 프로젝트와 사용자 권한

PawCycle Commerce는 반려동물 소모품의 일반 구매와 정기배송 구독을 다루는 이커머스 프로젝트다. 예정 기술 스택은 Spring Boot, Next.js·TypeScript, MySQL이며 승인된 단계가 오기 전에는 새 제품 코드, 의존성, DB schema, 인프라를 만들지 않는다.

사용자는 Product Owner이자 Tech Lead다. 요구사항, 도메인 정책, 아키텍처, API 계약, DB 설계, 새 의존성, 성능 개선안, PR 병합과 실제 운영 실행은 사용자가 최종 결정한다. AI는 승인된 범위에서 제안·구현·검증하며 자동 병합하지 않는다.

## 공통 안전 규칙

1. 최신 기준과 작업 상태를 확인한다.
2. 사용자가 승인한 범위만 변경한다.
3. Secret·개인정보·운영 원시 값을 노출하지 않는다.
4. 저장소 준비와 실제 운영 실행을 분리한다.
5. 변경 영향에 맞는 최소 검증을 실행한다.
6. 실패·미실행·남은 위험을 숨기지 않는다.
7. PR 병합과 실제 운영 실행은 사용자가 최종 결정한다.

역할별 산출물, QA, 보고서, 인수인계와 Runbook은 항상 필요한 것이 아니라 `docs/runbook/lean-harness.md`의 조건을 충족할 때만 만든다.

## 문서 권위

문서가 충돌하면 다음 순서로 해석한다.

1. 현재 작업에서 사용자가 명시한 지시
2. 사용자가 승인한 요구사항과 인수 조건
3. 승인된 ADR
4. 승인된 OpenAPI 계약
5. 도메인 규칙과 용어집
6. 경로별 `AGENTS.md`
7. 역할 Skill
8. 기존 코드 관례

등급, 저장소 준비와 실제 실행 구분, 산출물·QA·검증 조건, delta-only 명세와 비소급·복구 원칙은 `docs/runbook/lean-harness.md`가 권위 원본이다. 역할의 지속 책임과 금지 범위는 `docs/roles/**`, 실제 실행 절차는 `.agents/skills/**`를 따른다. 충돌을 발견하면 문서, 내용, 구현 영향과 사용자 결정 항목을 보고한다.

## 작업 시작과 범위

파일 변경 전 작업 ID, 등급, 실행 구분, 역할, 현재 branch·worktree 상태, 승인 입력, 포함·제외 범위와 검증 방법을 확인한다. 기본 Git 확인은 다음으로 제한하고, 기존 branch 재사용·삭제, 열린 PR, local·remote 분기, 다른 worktree, 고유 미병합 commit 또는 destructive 작업 가능성이 있을 때만 상세 진단한다.

```bash
git status --short --branch
git fetch --prune origin
git log --oneline HEAD..origin/main
```

승인되지 않은 Product Decision 또는 Technical Decision이 구현을 막으면 임의로 정하지 않고 중단한다. 기회주의적 리팩터링, 무관한 정리·포맷 변경, 의존성 추가와 다른 역할 영역 변경을 하지 않는다.

## 역할 경계

| 역할 | 지속 책임 | 기본 경로 |
| --- | --- | --- |
| Product Planner | 사용자 문제, 범위, 비즈니스 규칙, 인수 조건 | `docs/product/**`, 승인된 `docs/domain/**` |
| UX/UI Designer | 사용자 흐름, 화면·컴포넌트 상태, 반응형, 접근성 | `docs/design/**` |
| Backend Engineer | 도메인 로직, API, transaction, persistence, 보안, 백엔드 테스트 | `backend/**`, 승인된 API·ADR·도메인 문서 |
| Frontend Engineer | 페이지, 컴포넌트, API 연동, UI 상태, 접근성, 프론트엔드 테스트 | `frontend/**` |
| QA Engineer | 독립 검증, 실패 테스트, 버그 재현과 재검증 | `qa/**`, 테스트 전용 경로, `docs/qa/**` |
| Platform/SRE | 개발 환경, CI/CD, 배포, 성능 측정, 관측성, 알림, Runbook | `infra/**`, `.github/workflows/**`, 운영 문서 |
| Tech Lead | 승인 상태, 역할 경계, 병합 준비도, 기술 결정과 위험 판단 | 공통 Harness·승인·검토 문서 |

세부 허용·금지 경로는 현재 경로의 `AGENTS.md`와 역할 문서를 따른다. 다른 역할의 변경이 필요하면 직접 확장하지 않고 실제 소비자가 있을 때 인수인계 또는 변경 요청을 남긴다.

## Secret과 운영 경계

- 비밀번호, API key, token, private key, certificate, Webhook URL과 실제 운영 식별값을 저장소·PR·로그·완료 보고에 넣지 않는다.
- 예시는 `DB_PASSWORD=<로컬 환경 변수에서 제공>`처럼 설명 가능한 placeholder만 사용한다.
- Secret이 필요한 기능은 값이 없을 때 안전하게 실패해야 한다.
- 노출이 의심되면 값을 출력·복사하지 않고 작업을 중단해 보고한다.
- 저장소 준비 승인은 Production·Cloud·운영 DB·Secret·비용 리소스 실행 승인으로 해석하지 않는다.

## Git과 Pull Request

`main`에 직접 작업하지 않는다. HARNESS-LEAN-002가 병합된 뒤 시작하는 새 작업은 최신 `main`에서 다음 task branch를 만든다.

| 역할 | branch 형식 |
| --- | --- |
| Product Planner | `spec/po/<TASK-ID>` |
| UX/UI Designer | `design/ux/<TASK-ID>` |
| Backend Engineer | `feat/be/<TASK-ID>` |
| Frontend Engineer | `feat/fe/<TASK-ID>` |
| QA Engineer | `test/qa/<TASK-ID>` |
| Platform/SRE | `ops/sre/<TASK-ID>` |
| Tech Lead | `ops/tl/<TASK-ID>` |

하나의 task branch에는 하나의 활성 작업만 둔다. 병합 뒤 열린 PR·고유 commit·사용 중인 worktree가 모두 없을 때만 branch를 삭제하며, 하나라도 있으면 삭제하지 않는다. 기존 역사 branch와 과거 문서는 소급 변경하지 않는다. 자동 reset, rebase, force push와 history rewrite를 하지 않는다.

커밋과 PR 제목은 `<type>(<scope>): <한국어 명사형 설명>` 형식을 사용한다. 허용 type은 `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`, `revert`다. 설명은 한글을 포함한 명사형으로 끝내고 마침표를 붙이지 않는다.

모든 작업은 PR 본문을 작성한다. PR 구조와 산출물 조건은 `.github/pull_request_template.md`와 `docs/runbook/lean-harness.md`를 따른다. 한글 여러 줄 본문은 UTF-8 Markdown 파일과 `--body-file`을 사용하고 생성 직후 원격 title·body·head/base·Draft 상태를 확인한다. Codex는 필수 검증 뒤 commit·일반 push·Draft PR 생성까지 수행하되 검증 실패, 충돌, 다른 작업 혼입, Secret 의심 또는 원격 상태 불명확 시 중단한다.

## 검증과 리뷰

가장 작은 관련 검사부터 실행한다. 공통 CI·보안·migration·DB mapping·배포·복구처럼 여러 영역에 영향을 주는 변경만 관련 전체 검증과 독립 확인을 사용한다. 같은 실패를 반복 실행하지 않고 첫 원인을 한 번 수정한 뒤 관련 검사만 재실행한다.

모든 GitHub PR 리뷰 댓글은 한국어로 작성한다. CodeRabbit과 Codex Review는 보조 검토자이며, 버그·보안·인가·도메인 규칙·테스트 누락을 우선한다. 결제, 주문·구독 상태 전이, 개인정보와 데이터 손실 위험은 사용자가 다시 판단한다.

No Explain, No Merge 원칙에 따라 변경 이유, 데이터와 transaction 경계, 실패 상태, 보호 테스트, 주요 SQL과 운영 확인 지점을 설명할 수 없는 변경은 병합 권고하지 않는다.

## 완료 보고

완료 보고에는 작업 요약, 변경 파일, 검증 결과, 남은 위험과 사용자 결정 항목, commit·push·Draft PR·병합 상태를 포함한다. 실제 Production 실행 여부를 명시하며 자동 병합하지 않는다.
