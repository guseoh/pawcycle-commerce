# FOUNDATION-004 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: `FOUNDATION-004`
- 역할: Platform/SRE
- 기준 브랜치: `main`
- 작업 브랜치: `ops/sre`
- 대상 환경: Docker Desktop Linux Engine 기반 로컬 개발·QA

## 작업 목적

PR #44로 준비된 local-only QA bootstrap을 MySQL, Backend, Frontend와 연결하고, Nginx reverse proxy의 한 origin에서 첫 구독 MVP를 재현할 수 있는 Docker Compose 환경과 QA Runbook을 제공한다.

## 입력 문서

- `docs/handoffs/FOUNDATION-004/be-to-sre.md`
- `docs/handoffs/FRONTEND-001/fe-to-qa.md`
- `docs/reports/FOUNDATION-004/be-report.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `backend/src/main/resources/application-local-integration.properties`
- Platform/SRE 역할 문서, Skill과 `infra/AGENTS.md`

## 변경 범위

- MySQL 8.4.10, Java 25 Backend, Node.js 24 Frontend와 Nginx stable reverse proxy의 Docker Compose 구성
- Backend·Frontend multi-stage container build 설정과 build context 제외 설정
- MySQL named volume, healthcheck와 서비스 시작 순서
- `/api/**`는 Backend, 나머지 요청은 Frontend로 전달하는 same-origin proxy
- `local-integration` profile, QA bootstrap enable, reset과 로컬 HTTP cookie 환경 변수 연결
- 실제 값이 없는 `.env.example`과 Git에서 제외되는 `.env.local` 사용 절차
- cookie와 CSRF 값을 메모리에만 유지하는 PowerShell smoke 시나리오
- 최초 시작, 반복 시작, 로그, reset, 데이터 보존, 전체 삭제, 장애 진단과 롤백 Runbook
- Platform/SRE에서 QA로 전달하는 환경 인수인계

## 변경하지 않은 범위

- Backend·Frontend 제품 코드
- API 요청·응답·오류 계약
- DB schema와 Flyway migration
- 인증·인가·session·CSRF·CORS 정책
- production Docker, 배포, TLS, 도메인과 클라우드 인프라
- GitHub Actions와 Repository Validation 구조
- 신규 애플리케이션 dependency, Actuator와 브라우저 E2E 프레임워크
- Board 프로젝트의 `board-mysql-dev` 컨테이너

## 구성 결과

- Compose project: `pawcycle-local-integration`
- 서비스: `mysql`, `backend`, `frontend`, `proxy`
- 단일 origin 기본 구조: `http://localhost:8080`
- Backend는 한 인스턴스만 실행하고 외부 host port를 직접 열지 않는다.
- MySQL 데이터는 `pawcycle-local-integration-mysql-data` named volume에 보존된다.
- 일반 `stop`, `start`, `down`, `up`은 volume을 삭제하지 않으며 `down --volumes`만 전체 삭제 절차로 문서화했다.
- `SESSION_COOKIE_SECURE=false`는 Compose의 `local-integration` Backend 프로세스에만 전달된다.
- reset 기본값과 최종 실행값은 `false`다.

## 검증 결과

- Docker Desktop Linux Engine 연결과 Docker Compose v2 사용 가능 확인
- 고정 공식 image pull 성공
  - MySQL `8.4.10`
  - Eclipse Temurin `25.0.3_9` JDK·JRE
  - Node.js `24.18.0-alpine3.23`
  - Nginx stable `1.30.3-alpine3.23`
- Backend image build 성공: Java 25 Gradle `bootJar`
- Frontend image build 성공: Node.js 24, Next.js production build와 TypeScript 검사
- Compose 설정 검사 통과
- `mysql`, `backend`, `frontend`, `proxy` 모두 `healthy`
- 같은 origin의 Full smoke 통과
  - Frontend 응답
  - 공개 상품 목록·상세와 구독 가능 SKU
  - CSRF token 획득
  - QA 회원 로그인과 현재 회원 조회
  - 구독 생성·목록·상세
  - 로그아웃
- 일반 `down`·`up` 뒤 Preserved smoke 통과: 구독과 MySQL volume 데이터 보존, fixture 중복 없음
- reset `true` 뒤 Empty smoke 통과: QA 회원 구독이 비어 있음
- reset을 `false`로 복원하고 Backend·proxy를 재생성한 뒤 Empty smoke 재통과
- 최종 Backend 한 인스턴스와 reset `false` 확인
- 생성한 datasource·QA credential이 container 로그에 없는지 값 일치 방식으로 확인
- 직접 MySQL 검증 결과
  - Compose project `pawcycle-local-integration`
  - Compose service `mysql`
  - MySQL `8.4.10`
  - server charset `utf8mb4`
  - server collation `utf8mb4_0900_ai_ci`
- 직접 MySQL 검증에서는 PawCycle Compose와 컨테이너를 재시작하지 않았고 named volume을 보존했다.

## 실패 후 수정

1. 첫 Compose 검사에서 Frontend healthcheck의 JavaScript 표현식이 YAML 문자열로 해석되지 않았다. 해당 항목을 명시적 문자열로 수정한 뒤 Compose 검사가 통과했다.
2. 첫 Full smoke에서 Backend JSON은 UTF-8로 정상 응답했지만 Windows PowerShell 5.1이 charset 없는 `application/json`의 한글 fixture 이름을 잘못 디코딩했다. 상품 식별을 예약 ASCII 접두사와 구독 가능 플래그로 변경하고 ASCII SKU 이름은 정확히 비교하도록 수정한 뒤 Full smoke가 통과했다.
3. Windows PowerShell → Docker → `sh -c` 중첩 경로의 SQL 인용부호가 손상돼 MySQL version·charset·collation 조회가 실패했다. SQL을 단순화해 한 번 집중 수정했지만 같은 인용 문제가 반복돼 규칙에 따라 작업을 중단했다.
4. 재개 진단에서 Compose 환경 변수가 검증 shell에 전달되지 않은 문제와 다른 프로젝트 컨테이너를 선택할 수 있는 문제를 분리했다. `com.docker.compose.project=pawcycle-local-integration`과 `com.docker.compose.service=mysql` label로 대상 컨테이너를 한정하고, 중첩 `sh -c` 없이 해당 컨테이너에 직접 `docker exec`하는 방식으로 해결했다.
5. 직접 검증은 `board-mysql-dev`를 선택하거나 변경하지 않았으며 PawCycle 서비스 재시작 없이 MySQL 계약을 확인했다.

## 반복하지 않은 검증과 이유

사용자 확인 시점 이후 관련 설정이 바뀌지 않았으므로 다음 검증은 반복하지 않았다.

- 공식 image pull·build
- Compose 전체 기동과 모든 서비스 healthy 확인
- Full smoke
- 일반 재시작 뒤 데이터 보존과 fixture 멱등성
- reset `true` 뒤 빈 상태
- reset `false` 복원 뒤 빈 상태

동일 검증을 다시 실행하면 근거가 늘지 않고 로컬 상태와 검증 비용만 불필요하게 바뀌므로 기존 성공 결과를 사용했다.

## 실행하지 못한 검증과 이유

- QA의 실제 브라우저·좁은 viewport·keyboard-only 최종 검증은 역할 경계상 실행하지 않았다. 이 작업의 smoke는 연결과 핵심 API 수직 흐름만 확인한다.
- 다른 회원과 비fixture 데이터 보존은 새로 만든 빈 로컬 volume에서 직접 재현하지 않았다. PR #44의 `LocalQaBootstrapIntegrationTests`와 성공한 Repository Validation이 해당 Backend reset 경계를 보호하며, 기존 회원 데이터를 추가하거나 삭제해 로컬 smoke를 확장하지 않았다.
- production, TLS, 도메인과 cloud 배포 검증은 local-only 승인 범위 밖이므로 실행하지 않았다.

## 적용 방법과 롤백

- 시작·종료·재시작·reset·전체 삭제는 `docs/runbook/FOUNDATION-004-local-integration.md`를 따른다.
- 평상시 reset은 `false`로 유지하고 빈 구독 준비 시에만 `true`로 Backend를 한 번 재생성한 뒤 즉시 `false`로 복원한다.
- 운영 변경 롤백은 `docker compose ... down`으로 stack을 내리고 FOUNDATION-004 Git 변경만 revert한다.
- 일반 롤백은 named volume을 보존한다. 전체 삭제가 명시적으로 승인된 경우에만 `down --volumes`를 사용한다.

## 위험과 제한

- local bootstrap은 Backend 한 인스턴스를 전제로 한다. 기존 QA 회원 row에는 잠금이 적용되지만 빈 DB의 동시 최초 bootstrap은 직렬화되지 않는다.
- 여러 Backend 인스턴스가 동시에 최초 기동하면 UNIQUE 제약 또는 fixture 충돌로 한 인스턴스가 시작 실패할 수 있다. 재시도·분산 잠금은 제공하지 않는다.
- 실제 credential은 `.env.local` 또는 현재 프로세스 환경 변수에만 존재한다. 분실한 credential로 기존 fixture 회원을 재사용하면 password 충돌로 시작이 실패하며 row를 덮어쓰지 않는다.
- Nginx와 Compose 구성은 local-only이며 production 배포 결정을 의미하지 않는다.
- 최종 QA 판정과 병합은 사용자가 결정한다.

## 다음 작업

1. QA가 SRE 인수인계와 Runbook으로 실제 브라우저 수직 흐름을 검증한다.
2. QA가 desktop·좁은 viewport·keyboard-only와 접근성 기준을 확인한다.
3. 제품 결함이나 승인 계약 위반이 발견되면 재현 근거와 함께 담당 역할에 전달한다.
4. 사용자가 diff와 Repository Validation을 확인한 뒤 병합 여부를 결정한다.

## Git 결과

- 작업 브랜치: `ops/sre`
- 최신 `main`에서 시작했으며 reset, rebase, force push와 history rewrite를 사용하지 않았다.
- FOUNDATION-004의 승인된 11개 파일만 명시적으로 stage했다.
- 구현 commit: `8770526123bcfa9a6029d7ec946fe6baffaf7dd0` (`chore(infra): 로컬 QA 통합 환경 구성`)
- `origin/ops/sre`로 일반 push하고 upstream 추적을 설정했다.
- 이 보고서의 최종 Git·PR 상태 갱신 commit은 자기참조를 피하고 PR #45의 GitHub 커밋 이력을 권위 있는 원본으로 사용한다.

## PR 상태

- PR: #45 `https://github.com/guseoh/pawcycle-commerce/pull/45`
- base: `main`
- head: `ops/sre`
- 제목: `chore(infra): 로컬 QA 통합 환경 구성`
- Draft 상태로 생성했다.
- 생성 직후 GitHub에 저장된 UTF-8 제목·본문, base·head와 Draft 상태를 다시 확인했다.
- 최종 Repository Validation과 review 상태는 GitHub checks를 권위 있는 원본으로 확인한다.
- 자동 병합하지 않는다.
