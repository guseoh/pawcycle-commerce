# FOUNDATION-000 Tech Lead 작업 보고서

## 작업 목적

첫 번째 수직 MVP 구현 전에 FOUNDATION-001 애플리케이션 기반 작업이 사용할 기술 버전과 개발 기반 경계를 제안했다.

이번 작업은 문서 작업이며 Spring Boot, Next.js, Gradle, package.json, Docker Compose, GitHub Actions, Flyway migration, 애플리케이션 코드는 생성하지 않았다.

## 승인된 입력

- 서비스 방향: 반려동물 소모품 이커머스
- 구매 방식: 일반 구매와 정기배송
- Backend: Spring Boot
- Frontend: Next.js + TypeScript
- Database: MySQL
- 개발 방식: 하네스 엔지니어링
- 성능 개선: 측정 후 필요 시 진행
- PR #19 `docs(auth): 로그인 복귀와 인증 경계 설계` 병합 결과

## PR #19와 ops/tl 정리

| 항목 | 결과 |
| --- | --- |
| PR #19 상태 | `MERGED` |
| 병합 시각 | `2026-07-06T06:23:37Z` |
| head | `ops/tl` |
| base | `main` |
| head SHA | `382d0a589b134c166ce49b2f59396cf954699f99` |
| merge commit | `fe089338da44a3419870e803b70b4fbf4b35cde8` |
| stale local `ops/tl` | PR #19 head 또는 조상만 포함함을 확인하고 삭제 |
| stale remote `origin/ops/tl` | PR #19 head 또는 조상만 포함함을 확인하고 삭제 |
| 새 작업 브랜치 | 최신 `main`에서 새 `ops/tl` 생성 |

삭제 전 `main..ops/tl`과 `main..origin/ops/tl`에는 다음 PR #19 잔여 커밋만 있었다.

```text
382d0a5 docs(auth): AUTH-001 리뷰 반영
09f142c docs(report): AUTH-001 PR 상태 갱신
6211679 docs(auth): 로그인 복귀와 인증 경계 설계
```

`git diff --name-status 382d0a589b134c166ce49b2f59396cf954699f99 fe089338da44a3419870e803b70b4fbf4b35cde8` 결과는 비어 있어 PR head와 merge commit의 tree 차이가 없음을 확인했다.

## 확인한 문서

- `AGENTS.md`
- `docs/product/**`
- `docs/domain/**`
- `docs/design/**`
- `docs/adr/**`
- `docs/api/**`
- `docs/data/**`
- `docs/reports/AUTH-001/tl-report.md`
- `docs/handoffs/AUTH-001/tl-to-be-fe-qa.md`
- `docs/reports/API-001/be-report.md`
- `docs/handoffs/API-001/**`
- `docs/reports/DATA-001/**`
- `docs/handoffs/DATA-001/**`
- `docs/reports/ARCH-001/**`
- `docs/handoffs/ARCH-001/**`

## 확인하지 못한 입력

- `docs/**/AGENTS.md`: 저장소에 없음
- `docs/ux/**`: 저장소에 없음. UX 문서는 `docs/design/**`에서 확인
- Tech Lead 역할 문서와 Skill: `docs/roles/**`, `.agents/skills/**`에서 전용 Tech Lead 문서를 찾지 못함
- backend/frontend/infra 기술 설정 파일: 현재 저장소에 Gradle, Spring Boot, Next.js, package manager 설정 파일이 없어 기존 버전 근거 없음

## 변경 파일

- `docs/adr/FOUNDATION-000-technology-version-decision.md`
- `docs/reports/FOUNDATION-000/tl-report.md`
- `docs/handoffs/FOUNDATION-000/tl-to-be-fe-sre.md`

## 변경하지 않은 범위

- backend 코드와 Spring Boot 프로젝트 생성
- frontend 코드와 Next.js 프로젝트 생성
- infra 코드와 Docker Compose 생성
- GitHub Actions 변경
- Gradle wrapper 추가 또는 변경
- package.json 추가 또는 변경
- Flyway migration 작성
- JPA Entity, Controller, Service, Repository 작성
- OpenAPI 파일 생성
- 신규 의존성 추가
- 배포 설정, Secret, 성능 최적화
- DATA-001, API-001, AUTH-001 상태 변경

## 주요 추천안

| 항목 | 추천 | 상태 |
| --- | --- | --- |
| Java | 25 LTS | Proposed |
| Spring Boot | 4.1.x | Proposed |
| Gradle | 9.1 이상 9.x 최신 patch | Proposed |
| Backend test stack | `spring-boot-starter-test` | Proposed |
| Node.js | 24 LTS | Proposed |
| Next.js | 16.x | Proposed |
| TypeScript | 6.0.x | Proposed |
| Frontend package manager | npm | Proposed |
| Frontend test/lint baseline | ESLint CLI, TypeScript type check, `next build` | Proposed |
| MySQL | 8.4 LTS | Proposed |
| Migration tool | Flyway | Proposed |
| Local dev DB | 로컬 MySQL 8.4 LTS 수동 실행 | Proposed |
| CI baseline | 프로젝트 생성 후 Backend test와 Frontend lint/typecheck/build 후보 | Proposed |

## 공식 확인 근거

- [Oracle Java Downloads](https://www.oracle.com/java/technologies/downloads/): JDK 26은 최신 릴리스, JDK 25는 최신 LTS, JDK 21은 이전 LTS
- [Adoptium Temurin Support](https://adoptium.net/support): Java 25 LTS 제공 가능 기간 최소 2031년 9월
- [Spring Boot Installing](https://docs.spring.io/spring-boot/installing.html)과 [System Requirements](https://docs.spring.io/spring-boot/system-requirements.html): Spring Boot 4.1은 Java 17 이상과 Gradle 8.14 이상 또는 9.x 조합 지원
- [Gradle Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html): Gradle 실행 JVM은 17부터 26까지 지원하며, Java 25는 toolchain과 Gradle 실행 모두 9.1.0 이상에서 지원
- [Spring Boot Testing](https://docs.spring.io/spring-boot/reference/testing/index.html): `spring-boot-starter-test`가 JUnit Jupiter, AssertJ, Hamcrest 등을 제공
- [Node.js Previous Releases](https://nodejs.org/en/about/previous-releases): Node.js 24는 LTS, Node.js 26은 Current
- [Next.js 16](https://nextjs.org/blog/next-16): Node.js 20.9 이상과 TypeScript 5.1 이상 필요
- [TypeScript 6.0 공식 발표](https://devblogs.microsoft.com/typescript/announcing-typescript-6-0/): stable release
- [MySQL EOL 공지](https://www.mysql.com/support/eol-notice.html): MySQL 8.0 이후 MySQL 8.4 LTS 또는 9.7 LTS 업그레이드 안내
- [Flyway 공식 저장소](https://github.com/flyway/flyway): DB schema migration 도구

## 미확정 결정

- 배포 플랫폼
- Docker Compose 도입 여부
- GitHub Actions 상세 workflow
- Redis, Kafka, 검색, 캐시, 메시징 전략
- 결제 외부 연동
- Spring Security 세션, 토큰, 쿠키, CSRF 구현 방식
- Testcontainers, E2E 테스트, 브라우저 테스트 도입 여부
- MySQL 9.7 LTS 채택 여부
- API URL, HTTP 상태, 오류 응답 JSON 최종 승인

## 위험과 제한

- 추천 버전은 Proposed 상태이며 사용자 최종 승인이 필요하다.
- FOUNDATION-001에서 실제 scaffold 시 최신 patch와 호환성을 다시 확인해야 한다.
- TypeScript 6.0은 stable이지만 Next.js scaffold와 React 타입 생태계 조합을 실제로 검증해야 한다.
- MySQL 8.4 LTS는 보수적 선택이며, 9.7 LTS는 운영/드라이버/Flyway 호환성 검증 전 채택하지 않는다.
- DATA-001, API-001, AUTH-001은 Proposed 상태이므로 구현에서 최종 승인 문서처럼 사용하지 않는다.

## 다음 작업

FOUNDATION-001 애플리케이션 기반 작업에서 다음을 진행할 수 있다.

- Backend 프로젝트 생성 전 Java/Spring Boot/Gradle 최신 patch 확인
- Frontend 프로젝트 생성 전 Node.js/Next.js/TypeScript 최신 patch 확인
- MySQL 8.4 LTS 로컬 개발 기준 확인
- 최소 CI 검증 후보 확정
- 생성하면 안 되는 범위 재확인

## 검증 명령과 결과

| 명령 | 결과 |
| --- | --- |
| `git status --short --branch` | `## ops/tl`, FOUNDATION-000 문서 3개 추가 |
| `git diff --check` | 통과 |
| `git diff --stat` | 문서 3개 추가, 341 insertions |
| `git diff --name-status` | `A` 3건 |
| `echo "FOUNDATION-000" \| python scripts/validate-task-artifacts.py --from-stdin` | Windows PowerShell에서 `Python`만 출력하고 실패 |
| `Write-Output 'FOUNDATION-000' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과: `task artifacts validated for FOUNDATION-000` |
| `sh scripts/validate-commit-message.sh --message "docs(foundation): 기술 버전 결정"` | Windows PowerShell에서 `sh`를 찾지 못해 실패 |
| `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "docs(foundation): 기술 버전 결정"` | 통과 |

## Git 결과

- 설계 커밋: `38286e7 docs(foundation): 기술 버전 결정`
- Push: `origin/ops/tl` 새 브랜치 생성 및 upstream 설정 완료
- PR 상태 갱신: 이 보고서에서 PR #20 생성 결과를 기록함

## PR 결과

- PR 번호: #20
- PR 제목: `docs(foundation): 기술 버전 결정`
- PR 링크: https://github.com/guseoh/pawcycle-commerce/pull/20
- base: `main`
- head: `ops/tl`
- 자동 병합: 수행하지 않음
