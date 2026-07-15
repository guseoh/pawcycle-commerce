# PawCycle Commerce

개와 고양이용 소모품의 일반 구매와 정기배송을 지원하는 이커머스 프로젝트입니다.

현재는 공개 상품 탐색부터 세션 로그인, 구독 생성과 조회까지 연결한 **1차 수직 MVP**를 완료했습니다.

## 기술 스택

### Backend

![Java](https://img.shields.io/badge/Java_25-007396?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Bean Validation](https://img.shields.io/badge/Bean_Validation-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)

### Frontend

![Next.js](https://img.shields.io/badge/Next.js-000000?style=for-the-badge&logo=nextdotjs&logoColor=white)
![React](https://img.shields.io/badge/React-61DAFB?style=for-the-badge&logo=react&logoColor=000000)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![Node.js](https://img.shields.io/badge/Node.js_24-339933?style=for-the-badge&logo=nodedotjs&logoColor=white)

### Database · Infrastructure

![MySQL](https://img.shields.io/badge/MySQL_8.4-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=for-the-badge&logo=flyway&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Docker Compose](https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)

## 1차 MVP

다음 사용자 흐름을 Backend, Frontend, Database와 세션 인증으로 연결했습니다.

```text
공개 상품 탐색
→ 세션 로그인
→ SKU·수량·배송 주기 선택
→ 구독 생성
→ 내 구독 목록·상세 조회
→ 로그아웃
```

### QA 결과

| 결과 | 건수 |
| --- | ---: |
| 통과 | 17 |
| 일부 또는 전체 미실행 | 8 |
| 실패 | 0 |
| 차단 | 0 |

1차 MVP 완료는 첫 사용자 가치 흐름의 구현과 통합 검증이 완료되었다는 의미입니다.

PawCycle Commerce의 전체 기능이나 운영 배포가 완료되었다는 의미는 아닙니다.

## 구현된 기능

### 공개 상품

- 비회원도 공개 상품 목록을 조회할 수 있습니다.
- 공개 상품 상세와 SKU 정보를 조회할 수 있습니다.
- SKU 가격과 구독 가능 여부를 확인할 수 있습니다.
- 서버가 제공하는 배송 주기 선택지를 사용할 수 있습니다.
- 비공개 상품과 존재하지 않는 상품 정보를 노출하지 않습니다.

### 인증과 보안

- 세션 기반 로그인과 로그아웃을 지원합니다.
- 현재 로그인한 회원 정보를 조회할 수 있습니다.
- 로그인 성공 시 세션 식별자를 변경합니다.
- 상태 변경 요청에 CSRF 보호를 적용합니다.
- 로그인 후 요청 이전 화면으로 안전하게 복귀할 수 있습니다.
- 외부 URL과 허용되지 않은 경로로의 이동을 차단합니다.
- 구독 소유자는 요청 값이 아닌 인증된 회원 정보로 결정합니다.

### 정기배송 구독

- 상품 상세 화면에서 구독을 생성할 수 있습니다.
- 본인의 구독 목록을 조회할 수 있습니다.
- 본인의 구독 상세 정보를 조회할 수 있습니다.
- 구독 수량을 1개부터 10개까지 검증합니다.
- 배송 주기를 2주, 4주 또는 8주로 검증합니다.
- 다음 주문 예정일을 서버에서 계산합니다.
- 존재하지 않는 구독과 타인 소유 구독을 동일한 방식으로 처리합니다.
- 타인의 구독 존재 여부와 회원 정보를 노출하지 않습니다.
- 빠른 중복 입력으로 동일 요청이 반복 전송되는 것을 제한합니다.

### 로컬 통합 환경

- MySQL 8.4와 Flyway migration을 사용합니다.
- Hibernate가 schema를 생성하지 않고 기존 schema를 검증합니다.
- Backend, Frontend, MySQL과 Nginx를 Docker Compose로 실행합니다.
- Nginx를 통해 Frontend와 Backend를 같은 origin으로 연결합니다.
- QA 전용 회원·상품·SKU fixture를 생성할 수 있습니다.
- 일반 재시작 후 구독 데이터가 보존되는지 검증합니다.
- 명시적 초기화 후 빈 구독 상태를 검증합니다.
- MySQL named volume은 명시적으로 삭제하지 않는 한 보존합니다.

## 저장소 구조

```text
backend/        Spring Boot Backend
frontend/       Next.js Frontend
infra/          Docker Compose와 Nginx 로컬 통합 환경
qa/             QA 규칙과 검증 자료
docs/           요구사항, 도메인, ADR, API, Runbook과 작업 보고서
scripts/        저장소와 작업 산출물 검증 스크립트
.github/        GitHub Actions와 PR 자동화
.agents/        역할별 AI 작업 Skill
```

## 실행과 검증

Docker Compose 기반 로컬 통합 환경의 실행 방법은 다음 Runbook을 따릅니다.

```text
docs/runbook/FOUNDATION-004-local-integration.md
```

### Backend 검증

```bash
cd backend
./gradlew test
./gradlew build
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat build
```

### Frontend 검증

```bash
cd frontend
npm ci
npm run lint
npm run build
```

실제 비밀번호, DB credential, session ID와 CSRF token은 저장소에 커밋하지 않습니다.

## 자동 검증

GitHub Actions의 Repository Validation은 다음 항목을 검증합니다.

- PR 제목과 commit message 규칙
- 작업 보고서와 필요한 인수인계
- Backend test와 build
- MySQL, Flyway와 JPA schema
- Spring Security와 CSRF
- 공개 상품 API
- 인증 API
- 정기배송 구독 API
- Frontend lint와 production build
- whitespace와 민감정보 경계

## 주요 문서

| 구분 | 경로 |
| --- | --- |
| 제품 요구사항 | `docs/product/PS-002-first-mvp-requirements.md` |
| UX 제품 결정 | `docs/product/PS-003-ux-product-decisions.md` |
| 구독 도메인 | `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md` |
| Backend 구현 승인 입력 | `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md` |
| 세션 인증 결정 | `docs/adr/AUTH-003-session-authentication-approved-inputs.md` |
| 공개 상품 API | `docs/api/API-002-public-product-api-contract-proposal.md` |
| 구독 API | `docs/api/API-003-subscription-api-contract-decision-request.md` |
| 로컬 통합 Runbook | `docs/runbook/FOUNDATION-004-local-integration.md` |
| 브라우저 QA 계획 | `docs/qa/FOUNDATION-004/first-mvp-browser-test-plan.md` |
| QA 결과 | `docs/reports/FOUNDATION-004/qa-report.md` |
| 1차 MVP 완료 기준 | `docs/reports/FOUNDATION-005/tl-report.md` |

## 알려진 제한

- 구독 변경, 일시정지, 재개와 해지는 아직 구현하지 않았습니다.
- 일반 구매, 장바구니와 주문은 아직 구현하지 않았습니다.
- 결제, 재고와 배송은 아직 구현하지 않았습니다.
- 운영 배포와 운영 환경의 Secret 관리는 아직 구성하지 않았습니다.
- 관측성과 성능 기준선은 아직 측정하지 않았습니다.
- 일부 접근성, 장애와 세션 만료 브라우저 시나리오는 미실행 상태입니다.
- 구독 생성 요청 timeout에 대한 멱등성 정책은 아직 결정하지 않았습니다.

## 다음 단계

```text
미실행 QA와 접근성 보강
→ 로컬 성능 기준선 측정
→ 구독 변경·일시정지·재개·해지
→ 일반 구매와 주문
→ 가상 결제와 재고
```

성능 최적화와 신규 인프라는 측정된 병목과 실제 필요성을 확인한 뒤 도입합니다.
