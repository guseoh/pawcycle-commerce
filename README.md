# PawCycle Commerce

개와 고양이용 소모품의 일반 구매와 정기배송을 목표로 하는 이커머스 프로젝트입니다. 현재 구현은 공개 상품 탐색부터 세션 인증, 정기배송 구독 생성·조회까지 연결한 1차 수직 MVP이며, 일반 구매는 아직 제공하지 않습니다.

이 README는 현재 저장소와 운영 검증의 경계를 설명합니다. 저장소에 준비된 계약, 실제 운영에서 검증된 최소 기준, 아직 실행하지 않은 고도화 항목과 다음 제품 단계를 서로 구분합니다.

## 포트폴리오 핵심 가치

- 제품 기능은 Backend·Frontend·MySQL·Flyway·세션 보안을 하나의 사용자 흐름으로 연결합니다.
- 운영은 서울 리전 단일 EC2에서 보수적인 Docker Compose release, HTTPS, Secret 분리, backup·격리 restore, application rollback과 최소 장애 알림을 검증합니다.
- 작업은 사용자가 범위와 승인을 통제하고, AI는 delta-only 명세·자동 검증·리뷰 입력을 보조하는 위험 기반 Lean Harness로 관리합니다.
- `Verified`와 `완료`는 같은 뜻이 아닙니다. 현재 판정은 정의된 최소 운영 안전성 기준에만 한정되며, 고가용성·자동복구·RPO/RTO·Actual Production DB restore 완료를 의미하지 않습니다.

## 현재 상태 요약

| 영역 | 현재 상태 | 범위와 경계 |
| --- | --- | --- |
| 1차 MVP | 구현·통합 및 조건부 QA 위험 수용 기준선 | 공개 상품, 세션 로그인/로그아웃, CSRF, 구독 생성·목록·상세, 다음 주문 예정일 계산 |
| Production 기준선 | `OPS-VERIFY-001 = Verified` | [OPS-029 판정](docs/reports/OPS-029/tl-report.md)의 일곱 최소 운영 안전성 기준에 한정 |
| Application rollback | 운영 검증 완료 | 이전 Application Release rollback과 원래 Release 재배포를 확인했으며 DB schema downgrade는 하지 않음 |
| Logical backup·isolated restore | 운영 검증 완료 | Production DB가 아닌 격리 MySQL에 복원·비교 |
| Actual Production DB restore | 미실행·미완료 | 별도 고위험 승인과 복구 계획이 필요한 영역 |
| Lean Harness | 저장소에 적용 | 등급·branch·delta-only·조건부 산출물·code/metadata 검증 분리 |
| MVP2 | Planned | 제품·도메인 승인 후 Backend → Frontend → 통합 QA → Production 적용 준비 순서 |

`OPS-VERIFY-001 = Verified`는 `OPS-026`에서 정의한 일곱 최소 운영 안전성 기준 충족을 뜻합니다. 전체 운영 완성, 무중단 배포, 자동복구, 고가용성, RPO/RTO, 물리 volume·EBS 장애 복구 또는 Actual Production DB restore 완료로 확대 해석하지 않습니다.

1차 MVP의 완료 경계도 전체 QA 완료를 뜻하지 않습니다. [FOUNDATION-005 완료 판정](docs/reports/FOUNDATION-005/tl-report.md)은 [FOUNDATION-004 브라우저 QA 결과](docs/reports/FOUNDATION-004/qa-report.md)의 `조건부 통과`를 사용자가 수용한 **조건부 QA 위험 수용 기준선**입니다. 당시 브라우저 QA는 통과 17건, 일부 또는 전체 미실행 8건, 실패 0건이었고, keyboard-only 전체 순회·session 만료·브라우저 `CSRF_INVALID`·구독 생성 POST timeout은 단위 테스트나 대체 증거에 의존해 실제 브라우저에서 재현하지 않았습니다. 이후 Production 인증·Session·CSRF Smoke가 보강됐어도 이 브라우저 QA 전체를 대체하지 않습니다.

## 1차 MVP 사용자 흐름

```text
공개 상품 목록·상세
→ 세션 로그인
→ SKU·수량·배송 주기 선택
→ 구독 생성
→ 내 구독 목록·상세 조회
→ 로그아웃
```

### 구현 범위

- 비회원 공개 상품 목록·상세와 SKU 가격·구독 가능 여부 조회
- 세션 로그인·로그아웃, 현재 회원 식별, 로그인 성공 시 session rotation
- 상태 변경 요청 CSRF 보호와 안전한 return path 처리
- SKU·수량·배송 주기 검증과 서버의 다음 주문 예정일 계산
- 인증된 회원 기준 구독 생성, 본인 구독 목록·상세 조회와 소유권 보호
- 존재하지 않는 구독과 타인 소유 구독의 정보 노출 방지
- Backend·Frontend·MySQL·Flyway 통합과 로컬 Docker Compose 환경

### 아직 1차 MVP에 없는 것

결제, 재고 차감, 실제 배송, 일반 구매·장바구니·주문, 구독 변경·일시정지·재개·해지는 아직 구현하지 않았습니다.

## Production 아키텍처

현재 Production은 서울 리전의 단일 EC2와 보존 EBS 위에서 Docker Compose project 하나를 수동 운영합니다.

```text
Internet :443 / :80
        ↓
Nginx proxy
        ├── Frontend :3000
        └── Backend :8080 ─── MySQL :3306
```

- 외부 공개 포트는 80·443이며 Frontend·Backend·MySQL 포트는 Docker 내부 network에만 노출합니다.
- Compose 서비스는 Nginx, Frontend, Backend, MySQL이고 MySQL 데이터는 named volume으로 보존합니다.
- Backend·Frontend image는 GHCR에서 같은 commit SHA release tag로 가져오며 OCI revision과 registry digest를 확인합니다.
- 운영자는 Session Manager로 접속하고, SSM Parameter Store `SecureString`을 root 전용 runtime Secret bundle로 materialize합니다.
- HTTPS는 Let’s Encrypt를 사용합니다.
- GitHub Actions는 image를 게시하지만 EC2 배포를 자동 실행하지 않습니다. 배포·rollback은 승인된 운영자가 Runbook의 preflight와 smoke를 확인하며 수행합니다.

세부 구조와 운영 경계는 [Production 운영 아키텍처 개요](docs/architecture/production-operations-overview.md)를 따릅니다.

## 실제 운영 검증 범위

- 단일 EC2 Compose release, SSM Secret materialize, 내부·외부 smoke와 재부팅 복구
- HTTPS 발급·SAN·경로, 수동 갱신 rehearsal과 재부팅 복구
- 논리 DB backup, private S3 저장, 격리 MySQL restore·manifest 비교와 Production volume 보존
- 이전 Application SHA rollback, 현재 Control 계약 채택, 원래 Release 재배포와 최종 health·Smoke 확인
- EC2 `StatusCheckFailed` ALARM·OK SNS email 알림
- Production 인증·Session·CSRF Smoke와 전용 Smoke 회원 생성

이 결과는 [OPS-029 Tech Lead 보고서](docs/reports/OPS-029/tl-report.md)를 권위 원본으로 사용하고, 실제 절차는 아래 주요 Production Runbook을 직접 따릅니다. 운영 식별자, hostname, 계정, Secret, backup ID와 원시 로그는 저장소에 기록하지 않습니다.

## 위험 기반 Lean Harness

저장소 작업은 사용자가 최종 승인·병합·운영 실행을 통제하는 절차로 관리합니다.

- 작업 등급은 `경량`, `일반`, `고위험`으로 나누며 등급에 따라 검증 깊이와 산출물을 결정합니다.
- 저장소 변경과 실제 운영 실행을 분리합니다. 저장소 준비가 운영 적용 승인을 대신하지 않습니다.
- 작업 branch는 역할별 `<role-prefix>/<TASK-ID>` 형식을 사용합니다.
- 작업 명세는 이번 delta와 제외 범위를 명시합니다. 제품·도메인·API·DB 결정을 승인 없이 새로 만들지 않습니다.
- PR metadata 검증과 code validation을 별도 workflow로 실행하고, 변경 경로에 관련된 Component만 병렬 검증합니다.
- 보고서·QA·인수인계는 실제 소비자와 필요성이 있을 때만 생성합니다.
- AI Review는 자동 승인 장치가 아니라 결함 탐색 입력입니다. 최종 판단과 병합은 사용자가 합니다.
- Harness 개선 효과의 수치 평가는 MVP2 실제 작업 이후 수행합니다. 현재 README는 개선 완료 수치를 주장하지 않습니다.

절차와 산출물 조건은 [Lean Harness Runbook](docs/runbook/lean-harness.md)을, 저장소 작업 순서는 [Repository onboarding](docs/runbook/repository-onboarding.md)을 따릅니다.

## 기술 스택

| 영역 | 기술 | 현재 기준 |
| --- | --- | --- |
| Backend | Java, Spring Boot, Spring Security, Spring Data JPA, Bean Validation, Gradle | Java 25, Spring Boot 4.1.0 |
| Frontend | Next.js, React, TypeScript, Node.js | Next.js 16.2.10, React 19.2.4, TypeScript 6.0.3, Node.js 24.18.0 image |
| Database | MySQL, Flyway | MySQL 8.4.10 image, Flyway migration |
| Runtime | Docker Compose, Nginx | local integration·production compose |
| CI | GitHub Actions, GHCR | commit SHA·OCI revision·digest 검증 |

버전은 `backend/build.gradle`, `frontend/package.json`, `infra/*/Dockerfile`과 Compose 파일에서 확인한 값입니다.

## 저장소 구조

```text
backend/        Spring Boot Backend와 Flyway migration
frontend/       Next.js·React·TypeScript Frontend
infra/          local integration·production Compose와 운영 Script
qa/             QA 규칙과 브라우저 검증 자료
docs/           제품·도메인·API·ADR·설계·Runbook·보고서
scripts/        저장소·PR·작업 산출물 검증 Script
.github/        GitHub Actions와 PR 자동화
.agents/        역할별 AI 작업 Skill
```

## 실행과 검증

### 로컬 통합 환경

로컬 통합 환경은 [FOUNDATION-004 Runbook](docs/runbook/FOUNDATION-004-local-integration.md)의 환경 변수·fixture·정리 절차를 따른 뒤 실행합니다.

```bash
docker compose --file infra/local-integration/compose.yaml config --quiet
docker compose --file infra/local-integration/compose.yaml up --build
```

### Backend

```bash
cd backend
./gradlew test
./gradlew build
```

Windows PowerShell:

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat build
```

### Frontend

```bash
cd frontend
npm ci
npm run typecheck
npm run lint
npm run build
npm test
```

### 저장소 검증

PR에서는 변경 경로에 따라 [Repository Validation](.github/workflows/validate-conventions.yml)과 PR metadata validation이 실행됩니다.

- `git diff --check`
- commit·PR title/body·작업 ID·등급·실행 구분 검증
- 관련 Backend·Frontend·MySQL·Production·Harness Component 검증
- whitespace와 변경 경로에 따른 component·계약 검증

Secret·민감 운영 식별자 비노출은 작업자 검토와 필요한 수동 검사 경계입니다. Repository Validation이 전체 diff의 모든 운영 식별자를 자동 탐지한다고 해석하지 않습니다. 실제 비밀번호, DB credential, session ID, CSRF token과 운영 식별자를 저장소·로그에 기록하지 않습니다.

## 주요 권위 문서

| 구분 | 문서 |
| --- | --- |
| 1차 MVP 요구사항 | [PS-002](docs/product/PS-002-first-mvp-requirements.md) |
| UX 제품 결정 | [PS-003](docs/product/PS-003-ux-product-decisions.md) |
| 구독 도메인 | [DOMAIN-001](docs/domain/DOMAIN-001-first-mvp-subscription-domain.md) |
| Backend 승인 입력 | [ARCH-006](docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md) |
| 세션 인증 결정 | [AUTH-003](docs/adr/AUTH-003-session-authentication-approved-inputs.md) |
| 공개 상품 API | [API-002](docs/api/API-002-public-product-api-contract-proposal.md) |
| 구독 API | [API-003](docs/api/API-003-subscription-api-contract-decision-request.md) |
| Production 구조 | [운영 아키텍처 개요](docs/architecture/production-operations-overview.md) |
| 최소 운영 기준 판정 | [OPS-029 Tech Lead 보고서](docs/reports/OPS-029/tl-report.md) |
| 1차 MVP 브라우저 QA 결과 | [FOUNDATION-004 QA 보고서](docs/reports/FOUNDATION-004/qa-report.md) |
| 1차 MVP 완료 판정 | [FOUNDATION-005 Tech Lead 보고서](docs/reports/FOUNDATION-005/tl-report.md) |
| Lean Harness | [lean-harness.md](docs/runbook/lean-harness.md) |
| 로컬 통합 | [FOUNDATION-004 Runbook](docs/runbook/FOUNDATION-004-local-integration.md) |
| Production 단일 Release | [OPS-010](docs/runbook/OPS-010-production-single-release.md) |
| Production HTTPS | [OPS-011](docs/runbook/OPS-011-production-https.md) |
| Production DB backup·isolated restore | [OPS-013](docs/runbook/OPS-013-production-db-backup-restore.md) |
| EC2 장애 알림 | [OPS-015](docs/runbook/OPS-015-ec2-status-check-alarm.md) |
| Production 인증·Session Smoke | [OPS-017](docs/runbook/OPS-017-production-auth-session-smoke.md) |
| Production Smoke 회원 | [OPS-020](docs/runbook/OPS-020-production-auth-smoke-member.md) |
| Production DB restore | [OPS-025](docs/runbook/OPS-025-production-db-restore.md) |

존재하지 않는 MVP2 상세 문서를 미리 링크하지 않습니다. MVP2는 승인된 제품·도메인 문서가 생긴 뒤 해당 문서로 연결합니다.

## 현재 한계

- Actual Production DB restore와 schema downgrade, 복구 훈련은 미실행·미완료입니다.
- 확정된 RPO·RTO, backup schedule·실패 알림·cross-region·장기 보존 정책은 없습니다.
- 무중단 배포, 자동 서버 배포, 자동복구, Blue/Green, Load Balancer·다중 EC2·DB replica는 미구현입니다.
- 물리 MySQL volume·EBS·instance 장애와 복구를 검증하지 않았습니다.
- 전체 관측성, 장기 부하·capacity·성능 기준선과 credential 수명 관리는 미완료입니다.
- HTTPS 자동 갱신 schedule과 certificate backup은 미완료입니다.
- OPS-028에서 관찰된 Certbot external/named volume 경고의 근본 원인은 해결되지 않아 인증서 저장·갱신 경로의 후속 확인이 필요합니다.
- RDS는 후보 방향이며 현재 Production DB는 Docker MySQL입니다.
- 정기배송 Batch와 운영 자동화는 미구현입니다.
- Harness 개선 후 수치 평가는 아직 실행하지 않았습니다.
- 성능은 관측된 병목과 실제 필요성이 확인된 뒤 선택적으로 개선합니다.

## 2차 MVP와 이후 로드맵

2차 MVP는 승인 전 `Planned`입니다.

- Pet 등록과 DOG·CAT 구분
- `SubscriptionPlan`·`PlanVersion`·`PlanItem`
- 반려동물과 플랜을 선택한 구독, 가격·구성 snapshot
- 플랜 변경과 다음 회차 건너뛰기
- 일시정지·재개·해지와 상태·명령 이력
- 예정·건너뜀·보류·취소 Schedule
- 멱등성, 낙관적 잠금과 소유권 보호

2차 MVP에서 제외하는 항목:

- 실제 PG 결제, 카드·환불·위약금
- 실제 재고 차감
- 택배사 연동과 배송 완료
- 관리자 백오피스
- 추천 AI

다음 단계는 다음 순서로 진행합니다.

```text
MVP2 제품·도메인 승인 문서
→ Backend
→ Frontend
→ 통합 QA
→ Production 적용 준비
→ 사용자 승인 기반 실제 Production 적용
→ 성능 기준선과 Harness 전후 평가
→ 관측성·장애 대응·운영 자동화
```
