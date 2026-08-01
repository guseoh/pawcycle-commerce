# HARNESS-LEAN-002 Tech Lead 보고서

## 작업

- 작업 ID: HARNESS-LEAN-002
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Tech Lead
- 시작 기준 `main` 역사 스냅샷: 2026-08-01 KST에 PR #82 시작 전 확인한 [`54dcde1f`](https://github.com/guseoh/pawcycle-commerce/commit/54dcde143c81227e8845a1a23cb81b5697ac43a5)

## 목적

PCC_V3 기준에 맞춰 AI 통제에 필요한 안전 관문은 유지하면서 중복 규칙, 형식적 산출물, 긴 PR 양식과 변경 무관 CI 실행을 줄인다. 이 보고서는 전환 전 규칙과 2026-08-01 KST 개선 전 측정 기준을 보존하기 위해 사용자가 별도로 요구했다.

## 승인 입력과 범위

사용자가 HARNESS-LEAN-002를 고위험 저장소 변경으로 명시 승인했다. 권위 문서, 역할 문서·Skill, PR template, task artifact validator·회귀, Repository Validation, 직접 결합된 Discord parser, 운영 상태 판정 구획과 관련 Runbook만 변경한다.

Backend·Frontend 제품 코드, 제품 요구사항·도메인·API 계약, DB schema·data, application dependency와 실제 AWS·Production Docker·운영 DB·Secret·비용 리소스는 변경하거나 실행하지 않는다.

## 결과 또는 증거

- 공통 안전 규칙을 루트 `AGENTS.md`의 일곱 항목으로 정리하고 상세 등급·실행·산출물 조건을 `docs/runbook/lean-harness.md` 한 곳에 모았다.
- 일반·고위험 저장소 준비의 보고서·QA·인수인계·생략 사유 의무를 제거하고 실제 운영 실행 보고서 관문을 유지했다.
- PR template을 6개 핵심 구획으로 줄였다.
- 역할 문서는 지속 책임과 금지 범위, Skill은 6개 실행 단계와 역할별 중단 조건 중심으로 축소했다.
- 새 작업 branch는 `<role-prefix>/<TASK-ID>`를 사용하고 과거 branch·문서는 비소급으로 보존한다.
- PR 본문 `edited`는 `PR Metadata Validation`의 `PR metadata validation`만 실행하고, code event는 `Repository Validation`에서 Harness, Backend+MySQL, Frontend, Production 계약을 변경 경로에 따라 병렬 실행한다. 두 workflow의 concurrency를 분리하고 기존 required check 이름과 aggregate 실패 전파를 유지한다.
- Discord role parser는 기존 고정 역할 branch와 새 task branch를 모두 인식한다. Obsidian 작업 ID parser는 변경된 PR 구조와 독립적이라 수정하지 않았다.
- Production 운영 개요는 OPS-024·OPS-026을 역사 판정, OPS-029를 현재 `OPS-VERIFY-001 = Verified` 권위 원본으로 구분했다.

## 2026-08-01 KST 개선 전 기준

확인된 정성 기준은 다음과 같다.

- 문서 PR에서도 Production·MySQL·Backend·Frontend 전체 검증이 한 application job에서 순차 실행됐다.
- PR metadata `edited`와 commit `synchronize`가 같은 workflow를 생성할 수 있었고 concurrency 분리가 없었다.
- 일반 저장소 작업도 보고서, QA·인수인계 경로 또는 생략 사유를 요구했다.
- PR template, 역할 문서와 Skill에 동일한 검토·산출물 규칙이 반복됐다.

다음 표는 2026-08-01 KST에 [PR #81](https://github.com/guseoh/pawcycle-commerce/pull/81)에서 확인한 개선 전 역사 스냅샷이다. 현재 PR·CI 상태를 나타내지 않는다. GitHub run 목록은 action 세부 종류를 제공하지 않으므로 각 run을 `edited` 또는 `synchronize`로 추정하지 않는다.

| PR head | KST 시작 | run | 결론 | 경과 시간 |
| --- | --- | --- | --- | --- |
| [`525f9cd`](https://github.com/guseoh/pawcycle-commerce/commit/525f9cd) | 2026-08-01 14:13:18 | [`30685422883`](https://github.com/guseoh/pawcycle-commerce/actions/runs/30685422883) | success | 11분 14초 |
| [`525f9cd`](https://github.com/guseoh/pawcycle-commerce/commit/525f9cd) | 2026-08-01 14:18:08 | [`30685577660`](https://github.com/guseoh/pawcycle-commerce/actions/runs/30685577660) | success | 10분 14초 |
| [`525f9cd`](https://github.com/guseoh/pawcycle-commerce/commit/525f9cd) | 2026-08-01 14:19:03 | [`30685604394`](https://github.com/guseoh/pawcycle-commerce/actions/runs/30685604394) | success | 10분 26초 |
| [`16c8034`](https://github.com/guseoh/pawcycle-commerce/commit/16c8034) | 2026-08-01 14:35:01 | [`30686107838`](https://github.com/guseoh/pawcycle-commerce/actions/runs/30686107838) | success | 10분 26초 |
| [`16c8034`](https://github.com/guseoh/pawcycle-commerce/commit/16c8034) | 2026-08-01 14:35:48 | [`30686135235`](https://github.com/guseoh/pawcycle-commerce/actions/runs/30686135235) | success | 10분 28초 |

같은 PR·head에서 `525f9cd`는 3회, `16c8034`는 2회 실행됐다. 이는 중복 실행 기준선이며 인위적인 재실행 없이 기존 GitHub 기록만 사용했다.

후속 동일 유형 작업에서는 PR당 workflow 수, 중복·자동 취소 run, 대기·실행 시간, docs-only 검증 시간, component별 시간, 전체 Actions 사용 시간, 무관 job 수, Codex prompt 길이, 보고서·PR 중복량과 리뷰 왕복 횟수를 측정한다.

## 검증 결과

- 변경 Python 파일 `py_compile`: PASS
- task artifact, PR body encoding, workflow·classifier, Discord context·payload·sender 회귀 108개: PASS
- Discord normalized payload fixture 20개: PASS
- Obsidian PR record fixture: PASS
- 경량·일반·고위험 저장소 변경 무보고서, 실제 운영 실행 무보고서 실패, 최소 보고서, QA·handoff 생략, conflict·placeholder 시나리오: PASS
- `BOOTSTRAP-004 --allow-legacy-without-grade`: PASS
- HARNESS-LEAN-002 보고서 validator: PASS
- PR template UTF-8 encoding: PASS
- code·metadata workflow YAML parser 검증: PASS
- `git diff --check`: PASS
- 추가된 diff와 신규 파일의 실제 Secret·개인 절대 경로 검색: PASS. 기존 onboarding의 개인 절대 경로 예시도 `<repository-root>`로 교체했다.

## 복구 경계

이 변경은 실제 운영 상태를 바꾸지 않는다. 결함이 있으면 HARNESS-LEAN-002 commit을 일반 revert PR로 되돌린다. 과거 산출물 수정, branch history rewrite, reset·rebase·force push는 복구 수단이 아니다.

## 위험과 제한

- GitHub Actions의 실제 path 분류와 branch protection 호환성은 [PR #82](https://github.com/guseoh/pawcycle-commerce/pull/82)의 최신 head와 최신 code validation을 권위 상태로 확인한다.
- 저장소 결함은 현재 head SHA를 고정하지 않고 HARNESS-LEAN-002 병합 commit을 대상으로 한 별도 revert PR로 복구한다.
- `OPS-VERIFY-001 = Verified`는 일곱 최소 운영 안전성 기준선에 한정된다. Actual Production DB restore, RTO, 무중단, 자동복구, 고가용성과 물리 장애 복구는 미완료다.
- OPS-028의 Certbot external/named volume 경고는 미해결 상태로 유지한다.
- Production 실행, Secret 조회, DB 변경과 비용 리소스 작업은 수행하지 않았다.
