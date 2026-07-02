# PawCycle Commerce 에이전트 규칙

## 프로젝트 개요

PawCycle Commerce는 반려동물 소모품을 일반 구매와 정기배송 구독으로 판매하는 이커머스(E-commerce) 프로젝트다.

주요 상품은 사료, 간식, 고양이 모래, 배변 패드, 탈취제, 샴푸 및 위생용품이다.

정기배송 구독에는 배송 주기, 다음 배송일, 상품 및 수량 변경, 배송일 변경, 한 회차 건너뛰기, 일시정지와 재개, 해지, 정기 주문 생성, 결제 실패와 재시도, 재고 부족 처리가 포함될 수 있다.

## 기술 방향

예정 기술 스택(Technology Stack)은 다음과 같다.

- 백엔드(Backend): Java, Spring Boot, Spring Security, Spring Data JPA, Bean Validation, Gradle
- 프론트엔드(Frontend): Next.js, TypeScript
- 데이터베이스(Database): MySQL
- 향후 운영(Operations): Docker, CI/CD, 배포 자동화(Deployment Automation), Redis, 배치 처리(Batch Processing), 비동기 메시징(Asynchronous Messaging), 부하 테스트(Load Testing), 관측성(Observability), 모니터링(Monitoring), 알림(Alert), 운영 런북(Runbook)

승인된 단계가 오기 전에는 위 기술을 실제 파일이나 의존성으로 도입하지 않는다. Phase 0은 운영 구조만 정의한다.

## AI 네이티브 학습 목적

이 저장소의 목적은 AI가 코드를 대신 작성하는 것에 그치지 않는다. 역할, 문서, 테스트, 검토, 인수인계가 있는 AI 네이티브 소프트웨어 엔지니어링(AI-native Software Engineering)을 학습한다.

운영 원칙은 다음과 같다.

- AI는 제안하고 구현한다.
- 문서와 테스트가 소통한다.
- 사용자는 결정하고 검증한다.

각 역할은 책임, 허용 경로, 입력 자료, 산출물, 완료 조건, 검토 관문, 인수인계 방식, 금지 범위를 명확히 가져야 한다.

## 사용자 권한

사용자는 Product Owner이자 Tech Lead다.

다음 항목은 사용자가 최종 결정한다.

- 요구사항(Requirements)과 우선순위(Priority)
- 도메인 정책(Domain Policy)
- 아키텍처(Architecture)
- API 계약(API Contract)
- 데이터베이스 설계(Database Design)
- 새로운 의존성(Dependency)
- 성능 개선안(Performance Improvement Plan)
- 커밋(Commit), 푸시(Push), 병합(Merge), 배포(Deployment)

정책이나 기술 방향이 불명확하면 Product Decision 또는 Technical Decision으로 구분해 보고한다. 승인되지 않은 정책을 임의로 선택하지 않는다.

## 핵심 원칙

- No Explain, No Merge: 설명할 수 없는 코드는 병합하지 않는다.
- 구현자와 최종 검토자를 분리한다.
- 성능 문제는 추측으로 최적화하지 않고 먼저 측정한다.
- 각 역할은 승인된 범위와 허용 경로 안에서만 작업한다.
- 다른 역할의 영역 변경이 필요하면 직접 수정하지 않고 인수인계(Handoff)나 변경 요청(Change Request)을 남긴다.
- 요청되지 않은 도구, 의존성, 인프라를 추가하지 않는다.

## 문서 우선순위

프로젝트 문서가 충돌하면 다음 순서로 해석한다.

1. 현재 작업에서 사용자가 명시한 지시
2. 사용자가 승인한 기능 요구사항과 인수 조건(Acceptance Criteria)
3. 사용자가 승인한 ADR(Architecture Decision Record)
4. 승인된 OpenAPI 계약(OpenAPI Contract)
5. 도메인 규칙과 용어집(Domain Rules and Glossary)
6. 경로별 `AGENTS.md`
7. 역할 Skill
8. 기존 코드 관례(Code Convention)

문서가 충돌하면 다음을 보고한다.

- 충돌하는 문서
- 충돌하는 내용
- 구현에 미치는 영향
- 사용자가 결정해야 할 사항

## 작업 전 확인 절차

파일을 변경하기 전에 다음을 확인한다.

1. 현재 작업의 작업 ID(Task ID)를 확인한다.
2. 현재 브랜치(Branch)와 작업 트리(Worktree) 상태를 확인한다.
3. 수행 중인 역할을 확인한다.
4. 관련 `AGENTS.md`, 역할 문서, Skill을 읽는다.
5. 허용 경로와 금지 경로를 확인한다.
6. 승인된 입력 문서, API 계약, ADR을 확인한다.
7. Product Decision과 Technical Decision을 분리한다.
8. 승인되지 않은 결정이 구현을 막으면 작업을 중단하고 사용자에게 보고한다.

## 작업 범위 통제

변경은 승인된 작업 범위 안으로 제한한다. 기회주의적 리팩터링(Refactoring), 광범위한 정리, 의존성 추가, 포맷 변경을 피한다.

명시적으로 승인된 작업이 있기 전에는 Spring Boot, Next.js, 데이터베이스 스키마(Database Schema), Docker, CI/CD, 모니터링, 배포 파일을 생성하지 않는다.

## 역할 간 경계

- 기획(Product Planning)은 요구사항, 비즈니스 규칙, 인수 조건을 담당한다.
- UX/UI는 사용자 흐름(User Flow), 정보 구조(Information Architecture), 화면 상태(Screen State), 반응형 기준(Responsive Criteria), 접근성 기준(Accessibility Criteria)을 담당한다.
- 백엔드(Backend)는 도메인 로직(Domain Logic), API 동작, 트랜잭션(Transaction), 영속성(Persistence), 인증(Authentication), 인가(Authorization), 백엔드 테스트를 담당한다.
- 프론트엔드(Frontend)는 페이지, 컴포넌트(Component), TypeScript 타입, API 연동, UI 상태, 접근성, 프론트엔드 테스트를 담당한다.
- QA는 검증(Verification), 테스트 계획(Test Plan), 실패 테스트(Failing Test), 버그 리포트(Bug Report), 재검증(Retest)을 담당한다.
- 플랫폼/SRE(Platform/SRE)는 개발 환경, CI/CD, 배포, 성능 측정, 관측성, 알림, 런북을 담당한다.

다른 역할의 작업이 필요하면 인수인계를 사용한다.

## Git 및 Worktree 원칙

- `main`은 프로젝트의 유일한 기본 브랜치이자 통합 기준 브랜치다.
- `main`에는 승인되고 검증된 결과만 포함한다.
- `main`에서 직접 작업하지 않는다.
- 모든 작업 브랜치는 최신 `main`에서 생성한다.
- 브랜치 기본 형식은 `<분류>/<작업-id>`다.
- 브랜치의 작업 ID는 소문자로 작성한다.
- 브랜치 이름에 긴 기능 설명 slug, 작업자 이름, 에이전트 이름, 날짜, 기술 스택, 문서 파일명을 넣지 않는다.
- 허용 브랜치 접두사는 `spec`, `design`, `feat`, `test`, `ops`다.
- `spec/*`는 제품 기획, 요구사항, 도메인 분석, API·DB·아키텍처 설계 초안에 사용한다.
- `design/*`는 UX/UI 흐름과 디자인 작업에 사용한다.
- `feat/*`는 백엔드와 프론트엔드 제품 코드 구현에 사용한다.
- `test/*`는 QA, E2E, 회귀 테스트에 사용한다.
- `ops/*`는 저장소 운영, 인프라, CI/CD, 관측성, 성능 실험에 사용한다.
- 백엔드와 프론트엔드가 같은 작업 ID에서 별도 구현 브랜치를 가져야 할 때만 `-be`, `-fe` 접미사를 사용한다.
- E2E 테스트 분리가 필요할 때만 `-e2e` 접미사를 사용한다.
- 하나의 브랜치는 하나의 작업 ID와 하나의 책임만 가진다.
- 작업 결과는 Pull Request로 `main`에 병합한다.
- 병합된 작업 브랜치는 활성 worktree와 후속 작업 여부를 확인한 뒤 삭제한다.
- 역할별 작업은 가능하면 별도 Codex 스레드와 작업 트리(worktree)에서 수행한다.
- 사용자 승인 없이 커밋, 푸시, 병합, 배포하지 않는다.
- Codex가 자동으로 브랜치를 병합하지 않는다.
- 사용자는 diff, 검증 결과, 설명 가능성을 확인한 뒤 병합 여부를 결정한다.

브랜치 예시는 다음과 같다.

- `spec/ps-001`
- `design/ps-001`
- `feat/ps-001-be`
- `feat/ps-001-fe`
- `test/ps-001`
- `ops/ps-001`
- `ops/perf-001`
- `ops/bootstrap-002`

## 작업 ID와 스레드 규칙

모든 작업에는 작업 ID를 사용한다.

권장 접두사는 다음과 같다.

- `BOOTSTRAP`: 프로젝트 운영 구조
- `PS`: 제품 기능(Product Story)
- `ARCH`: 아키텍처 결정
- `FOUNDATION`: 기술 기반
- `BUG`: 결함
- `PERF`: 성능 실험
- `OPS`: 운영과 인프라
- `SEC`: 보안

스레드 이름 형식은 다음과 같다.

```text
[작업 ID][역할 코드] 작업명
```

역할 코드는 다음과 같다.

- `PO`: 기획
- `UX`: UX/UI 디자인
- `BE`: 백엔드
- `FE`: 프론트엔드
- `QA`: 품질 보증(Quality Assurance)
- `SRE`: 플랫폼/SRE
- `TL`: 사용자 검토 또는 기술 결정

## Definition of Ready

구현 작업은 다음 조건이 준비된 뒤 시작한다.

- 작업 ID가 있다.
- 사용자 문제가 정의됐다.
- 포함 범위와 제외 범위가 정의됐다.
- 사용자 스토리(User Story)가 있다.
- 정상 흐름과 예외 흐름이 있다.
- 비즈니스 규칙(Business Rule)이 있다.
- 인수 조건이 있다.
- UI가 있으면 디자인 흐름이 승인됐다.
- 연동이 있으면 API 계약이 승인됐다.
- Product Decision이 해결됐다.
- 허용 경로와 금지 경로가 정의됐다.
- 검증 방법(Validation Method)이 정의됐다.

작은 문서 작업은 가벼운 Ready 기준을 사용할 수 있지만, 최소 범위와 승인 근거는 명확해야 한다.

## Definition of Done

작업은 다음 조건을 만족해야 완료된다.

- 승인된 범위 안에서 작업했다.
- 인수 조건을 충족했다.
- 관련 테스트와 검사가 통과했다.
- API 변경이 문서화됐다.
- DB 변경이 문서화됐다.
- 변경 이유와 동작을 설명할 수 있다.
- 관련 없는 변경을 만들지 않았다.
- 알려진 위험과 제한을 기록했다.
- 필요한 인수인계를 작성했다.
- 필요한 학습 기록 입력이 준비됐다.
- 커밋, 푸시, 병합은 사용자 승인에 따른다.

## 인수인계 원칙

역할 간 인수인계는 대화에만 의존하지 않는다. 의미 있는 인수인계는 다음 위치에 저장한다.

```text
docs/handoffs/<작업 ID>/
```

실제 전달할 내용이 있을 때만 작성한다. 모든 역할 조합의 문서를 기본으로 만들지 않는다.

## 학습 기록

의미 있는 기능 작업이나 결정 이후에는 `docs/learning/`에 학습 기록(Learning Record)을 남긴다.

학습 기록에는 사용자 예상, AI 제안, 검토한 대안, 최종 선택, 직접 읽은 코드, 실행한 테스트, 확인한 SQL, 로그(Log), 메트릭(Metric), 새로 이해한 개념, 아직 이해하지 못한 부분, 직접 다시 구현하거나 검증할 부분을 포함한다.

## No Explain, No Merge

병합 승인 전 사용자는 다음 질문에 답할 수 있어야 한다.

- 왜 이 변경이 필요한가?
- 왜 이 설계를 선택했는가?
- 어떤 대안을 검토했는가?
- 데이터는 어떤 순서로 변경되는가?
- 트랜잭션은 어디에서 시작하고 종료되는가?
- 실패하면 어떤 상태가 남는가?
- 어떤 테스트가 어떤 규칙을 보호하는가?
- 어떤 SQL이 실행되는가?
- 장애가 발생하면 어떤 로그와 메트릭을 확인하는가?

## 완료 보고 형식

작업 완료 보고에는 다음을 포함한다.

- 작업 요약
- 변경한 파일
- 수행한 검증
- 알려진 위험 또는 제한
- 사용자가 결정해야 할 사항
- 커밋, 푸시, 병합 상태
