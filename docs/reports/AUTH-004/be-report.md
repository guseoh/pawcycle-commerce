# AUTH-004 Backend 작업 보고서

## 작업 정보

- 작업 ID: `AUTH-004`
- 역할: Backend Engineer
- 기준 브랜치: `main`
- 작업 브랜치: `feat/be`
- 상태: 코드 구현 완료, Java 25·MySQL 원격 검증 대기

## 목적

AUTH-003에서 승인한 최소 session 인증 API와 session·CSRF 생명주기를 구현한다.

## 승인 입력

- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/handoffs/AUTH-003/tl-to-be.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/handoffs/FOUNDATION-003/be-to-qa.md`

## 변경 범위

- `GET /api/auth/csrf`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- email 정규화·검증, BCrypt credential 확인
- memberId 전용 principal, session fixation과 SecurityContext 저장
- 로그인·로그아웃 CSRF token 교체·폐기와 logout 정리
- JSON validation·credential·security 오류 응답
- 집중 단위·통합 테스트

## 변경하지 않은 범위

- 공개 상품 API와 회원 가입
- JWT, OAuth2, CORS, remember-me
- DB schema, V1 migration, 신규 dependency
- local bootstrap, production seed와 실제 credential

## 계층과 트랜잭션

- API 계층은 HTTP 요청·응답과 인증 principal 매핑만 담당한다.
- application 계층은 정규화, 회원 조회, BCrypt, session strategy와 SecurityContext repository를 조율한다.
- login 회원 조회는 read-only transaction이며 session·SecurityContext 변경은 DB transaction과 분리된다.
- logout은 DB transaction 없이 Spring Security logout handler로 session·context·CSRF token·cookie를 정리한다.

## 검증 결과

- 로컬 focused test: Java 25 toolchain 부재로 미실행
- JDK downgrade, H2와 Testcontainers를 사용하지 않음
- `git diff --check`: 통과
- 최초 Repository Validation run `29152545987`: AUTH-004 산출물 부재로 conventions 실패, Application validation skip
- Repository Validation run `29152600218`: Java 25 compile 통과, validation fieldErrors 배열 assertion 1건 실패
- 집중 수정: fieldErrors 순서·크기를 개별 JSON path로 검증하도록 assertion 구체화
- Java 25·MySQL 8.4 코드 검증: 대기

## 실행하지 못한 검증과 이유

- 로컬 Java 25 test/build: Java 25 toolchain이 설치되지 않았고 download repository가 구성되지 않았다.
- 로컬 격리 MySQL 검증: 승인된 격리 credential과 실행 환경이 없어 접근하지 않았다.

## 위험과 제한

- 원격 Java 25·MySQL 검증 전이므로 현재 head를 병합 준비 완료로 판단하지 않는다.
- same-origin 또는 reverse proxy 전제만 지원하며 cross-site 배포는 별도 결정이 필요하다.
- 실제 deployment cookie와 reverse proxy 동작은 운영 환경에서 재검증해야 한다.

## 민감정보와 DB 영향

- principal과 응답에는 memberId만 포함한다.
- password hash는 BCrypt 비교에만 사용하며 session·응답에 저장하지 않는다.
- session id와 CSRF token은 승인된 위치 밖에 기록하지 않는다.
- DB schema와 migration 변경 없음

## Git 결과

- 구현 commit: `6c0f5dafab1d5e985edcd7a8e47a2c0525ba1ac6`
- PR #34 Draft, `main` ← `feat/be`
- 필수 검증 완료 후 실제 head·run·리뷰 결과로 갱신한다.
- 자동 병합하지 않는다.

## PR 상태

- PR #34는 검증 대기 중인 Draft다.
