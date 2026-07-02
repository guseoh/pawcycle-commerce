# Contributing

## 저장소 목적

PawCycle Commerce는 반려동물 소모품 이커머스와 정기배송 구독 도메인을 학습·구현하기 위한 저장소다. 현재는 애플리케이션 코드보다 제품·도메인·협업 기반을 먼저 정리하는 단계다.

## 작업 전 읽을 문서

- `AGENTS.md`
- 관련 하위 `AGENTS.md`
- 관련 역할 문서: `docs/roles/**`
- 관련 Skill: `.agents/skills/**`
- `docs/runbook/collaboration-automation.md`
- 현재 작업의 입력 문서와 인수 조건

## 작업 ID

모든 작업은 작업 ID를 사용한다.

예:

- `BOOTSTRAP-004`
- `PS-001`
- `DOMAIN-001`
- `ARCH-001`
- `FOUNDATION-000`

## 역할 선택

작업 성격에 맞는 역할을 선택한다.

- PO: 제품 기획
- UX: 사용자 흐름과 화면 상태
- BE: 백엔드
- FE: 프론트엔드
- QA: 검증
- SRE: 운영
- TL: 공통 저장소 정책과 기술 결정

## 역할 브랜치

작업마다 긴 브랜치 이름을 만들지 않는다.

| 브랜치 | 담당 역할 |
| --- | --- |
| `spec/po` | Product Planner |
| `design/ux` | UX/UI Designer |
| `feat/be` | Backend Engineer |
| `feat/fe` | Frontend Engineer |
| `test/qa` | QA Engineer |
| `ops/sre` | Platform/SRE |
| `ops/tl` | Tech Lead와 공통 저장소 작업 |

하나의 역할 브랜치에는 하나의 활성 작업만 둔다. PR 병합 후 역할 브랜치를 삭제하고 다음 작업에서 최신 `main` 기준으로 같은 이름을 다시 만든다.

## 커밋과 PR 제목

형식:

```text
<type>(<scope>): <한국어 명사형 설명>
```

예:

```text
docs(harness): 역할별 작업 흐름 추가
ci(commit): 명사형 제목 검증 추가
```

서술형 종결은 사용하지 않는다.

## 보고서와 인수인계

작업 보고서는 다음 위치에 작성한다.

```text
docs/reports/<작업 ID>/
```

역할 인수인계는 다음 위치에 작성한다.

```text
docs/handoffs/<작업 ID>/
```

PR에는 작업 보고서와 역할 인수인계 경로를 연결한다.

## 테스트와 검증

현재 애플리케이션 프로젝트는 아직 없다. 존재하는 검증만 실행한다.

```bash
sh scripts/test-commit-message-convention.sh
python scripts/validate-discord-payloads.py
python scripts/validate-obsidian-record.py
```

실행할 수 없는 검증은 이유를 보고서와 PR에 적는다.

## Secret 금지

Secret, 인증 정보, Webhook URL, 개인 키, 개인정보, 실제 결제 정보는 저장소에 커밋하지 않는다. 예시에는 실제 값 대신 placeholder를 사용한다.

## PR 작성 방법

PR 대상 브랜치는 `main`이다. PR 본문에는 작업 ID, 담당 역할, 작업 보고서, 역할 인수인계, 변경 범위, 변경하지 않은 범위, 검증 결과, 적용 방법, 위험, 다음 작업을 포함한다.

No Explain, No Merge 원칙을 따른다. 설명할 수 없는 변경은 병합하지 않는다.

## 병합 후 정리

병합은 사용자가 최종 검토 후 수행한다. 병합 후 역할 브랜치는 삭제하고 다음 작업에서 다시 생성한다.

## 현재 단계의 기여 방식

Spring Boot, Next.js, MySQL, Docker Compose, 실제 API, DB 스키마, 인증·인가, 결제 연동은 아직 생성하지 않는다. 외부 기여자는 제품 정책을 임의로 확정하지 않고 Product Decision 또는 Technical Decision으로 남긴다.
