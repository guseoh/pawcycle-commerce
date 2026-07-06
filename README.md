# PawCycle Commerce

## 프로젝트 소개

PawCycle Commerce는 반려동물 소모품을 일반 구매와 정기배송 구독으로 판매하는 이커머스 프로젝트다.

현재는 제품·도메인·저장소 기반 위에 최소 Backend와 Frontend 애플리케이션 기반을 생성한 단계다.

## 해결하려는 문제

반려동물 소모품은 반복 구매 주기가 뚜렷하고 품절, 배송일, 수량 변경, 결제 실패가 사용자 경험에 직접 영향을 준다. 이 프로젝트는 반복 구매와 정기배송의 운영 복잡도를 명확한 도메인 규칙, 검증 가능한 계약, 설명 가능한 코드로 다루는 것을 목표로 한다.

## 프로젝트 목표

- 제품 정책과 도메인 규칙을 먼저 명확히 한다.
- Spring Boot, Next.js, MySQL 기반 구현 전에 협업 하네스를 정비한다.
- AI 역할별 작업, 보고서, 인수인계, 검증, PR 기록을 일관되게 남긴다.
- Secret과 개인정보가 저장소에 들어가지 않도록 한다.

## 현재 진행 단계

Phase 0 운영·협업 기반 구성 단계다. Spring Boot 백엔드와 Next.js 프론트엔드의 최소 실행 기반은 생성했지만, 제품 기능, API, 인증, DB schema, Docker 기반 로컬 환경은 아직 생성하지 않았다.

## 핵심 기능 계획

- 상품 탐색과 구매
- 정기배송 구독 생성과 변경
- 배송 주기와 다음 배송일 관리
- 한 회차 건너뛰기, 일시정지, 재개, 해지
- 정기 주문 생성
- 결제 실패와 재시도
- 재고 부족 처리

위 항목은 계획이며 아직 구현된 기능이 아니다.

## 예정 기술 스택

- Backend: Java, Spring Boot, Spring Security, Spring Data JPA, Bean Validation, Gradle
- Frontend: Next.js, TypeScript
- Database: MySQL
- Operations: Docker, CI/CD, 알림, 운영 런북, 성능 측정

기술 버전과 의존성은 별도 FOUNDATION 작업에서 확정한다.

## 저장소 구조

```text
.agents/        역할 Skill
.github/        GitHub Actions, PR 템플릿, Issue Form
backend/        Spring Boot 백엔드 애플리케이션 기반과 백엔드 역할 규칙
frontend/       Next.js 프론트엔드 애플리케이션 기반과 프론트엔드 역할 규칙
infra/          플랫폼/SRE 역할 규칙
qa/             QA 역할 규칙
docs/           제품, 도메인, ADR, 런북, 보고서, 인수인계
scripts/        로컬 및 CI 검증 스크립트
```

## 하네스 엔지니어링 방식

작업은 사용자 요청 해석, 작업 ID와 역할 결정, 최신 `main` 확인, 역할 브랜치 준비, 관련 문서 확인, 최소 범위 변경, 자동 검증, 보고서와 인수인계 작성, commit, push, PR 생성 순서로 진행한다.

## AI 역할

- PO: 제품 기획과 Product Decision
- UX: 사용자 흐름과 화면 상태
- BE: 백엔드 도메인, API, 영속성
- FE: 프론트엔드 UI와 API 사용
- QA: 검증, 버그 리포트, 재검증
- SRE: 운영, CI/CD, 런북, 알림
- TL: 공통 저장소 정책과 기술 결정

## 역할 브랜치

| 브랜치 | 담당 역할 |
| --- | --- |
| `main` | 기준 브랜치 |
| `spec/po` | Product Planner |
| `design/ux` | UX/UI Designer |
| `feat/be` | Backend Engineer |
| `feat/fe` | Frontend Engineer |
| `test/qa` | QA Engineer |
| `ops/sre` | Platform/SRE |
| `ops/tl` | Tech Lead와 공통 저장소 작업 |

역할 브랜치에는 하나의 활성 작업만 둔다. PR 병합 후 역할 브랜치를 삭제하고 다음 작업에서 같은 이름으로 다시 만든다.

## 커밋 규칙

커밋 메시지와 PR 제목은 다음 형식이다.

```text
<type>(<scope>): <한국어 명사형 설명>
```

예:

```text
docs(repository): 초기 저장소 문서 구성
ci(convention): 명사형 제목 검증 추가
```

`~한다`, `~했다`, `~합니다` 같은 서술형 종결은 사용하지 않는다.

## 협업 자동화

- 로컬 commit-msg Hook
- GitHub Actions 커밋 메시지와 PR 제목 검증
- 작업 보고서와 인수인계 존재 검증
- Discord Rich Embed 알림
- 병합 PR Obsidian 기록 생성

자세한 내용은 `docs/runbook/collaboration-automation.md`를 따른다.

## 주요 문서

- `AGENTS.md`
- `CONTRIBUTING.md`
- `SECURITY.md`
- `docs/runbook/collaboration-automation.md`
- `docs/runbook/FOUNDATION-002-ci-validation.md`
- `docs/runbook/repository-onboarding.md`
- `docs/runbook/github-repository-settings.md`
- `docs/reports/README.md`
- `docs/handoffs/README.md`

## 로컬 시작 방법

Backend 위치:

```text
backend/
```

Frontend 위치:

```text
frontend/
```

로컬 검증 요약:

```bash
cd backend
./gradlew test
./gradlew build
cd ../frontend
npm ci
npm run lint
npm run build
cd ..
```

자세한 도구 버전, Windows PowerShell 명령, Docker와 DB 제외 범위는 `docs/runbook/FOUNDATION-001-local-development.md`를 따른다.

## 현재 실행 가능 상태

현재 실행 가능한 것은 저장소 문서 자동화 검증, Spring Boot 기본 애플리케이션 테스트와 빌드, Next.js 기본 애플리케이션 lint와 빌드다. 제품 기능, API 서버 기능, 웹 UI, DB 스키마, Docker Compose는 아직 없다.

## 다음 작업

- BOOTSTRAP-005: GitHub 저장소 UI 설정
- PS-001: 제품 비전과 MVP 범위 검토
- DOMAIN-001: 도메인 용어와 상태 전이 정리
- ARCH-001: 시스템 컨텍스트와 모듈 경계 정리
- FOUNDATION-000: Java, Spring Boot, Node.js, Next.js, MySQL 버전 결정
