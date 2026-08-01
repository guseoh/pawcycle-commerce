# Contributing

## 저장소 목적과 현재 상태

PawCycle Commerce는 반려동물 소모품의 일반 구매와 정기배송 구독을 다룬다. 현재 저장소에는 Spring Boot Backend, Next.js Frontend, MySQL 로컬 통합 환경과 Production 계약 파일이 있으며, 승인된 범위만 변경한다.

## 작업 전 확인

- 루트와 관련 경로의 `AGENTS.md`
- 관련 `docs/roles/**`와 `.agents/skills/**`
- 승인된 요구사항·계약·인수 조건
- `docs/runbook/lean-harness.md`

## task branch

최신 `main`에서 역할에 맞는 task branch를 만든다.

| 역할 | branch 형식 |
| --- | --- |
| Product Planner | `spec/po/<TASK-ID>` |
| UX/UI Designer | `design/ux/<TASK-ID>` |
| Backend Engineer | `feat/be/<TASK-ID>` |
| Frontend Engineer | `feat/fe/<TASK-ID>` |
| QA Engineer | `test/qa/<TASK-ID>` |
| Platform/SRE | `ops/sre/<TASK-ID>` |
| Tech Lead | `ops/tl/<TASK-ID>` |

하나의 task branch에는 하나의 활성 작업만 둔다. 고정 역할 branch를 삭제한 뒤 같은 이름으로 재생성하지 않는다.

## 커밋과 PR 제목

`<type>(<scope>): <한국어 명사형 설명>` 형식을 사용한다. 설명은 한글을 포함한 명사형으로 끝내고 마침표를 붙이지 않는다.

## PR과 조건부 산출물

모든 작업은 PR 본문을 작성하며 다음 6개 핵심 구획을 사용한다.

```text
작업
목적과 범위
결정과 영향
검증
위험과 복구
병합 판단
```

보고서·인수인계·QA·Runbook·ADR은 `docs/runbook/lean-harness.md`의 조건을 충족할 때만 작성한다. 일반·고위험 저장소 변경도 PR·관련 테스트·CI로 충분하면 보고서가 필요하지 않으며 생략 사유를 기본 요구하지 않는다. 실제 운영 실행은 고위험 등급과 실행 보고서가 필수다.

## 검증

가장 작은 관련 검사부터 실행하고, 공통 CI·보안·DB mapping·배포·복구처럼 여러 영역에 영향을 주는 변경만 관련 전체 검증을 사용한다. 실행하지 못한 필수 검증과 남은 위험은 PR에 기록한다.

## Secret과 병합

Secret, 인증 정보, Webhook URL, 개인 키, 개인정보와 실제 운영 식별값을 저장소·PR·로그에 넣지 않는다. 병합은 사용자가 최종 결정하며 자동 병합하지 않는다.

병합 뒤 열린 PR·고유 commit·사용 중인 worktree가 모두 없을 때만 branch를 삭제한다. 하나라도 있으면 삭제하지 않는다.
