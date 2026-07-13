# PRODUCT-001 QA 독립 검증 보고서

## 작업 정보

- 작업 ID: `PRODUCT-001`
- 역할: QA Engineer
- 기준 브랜치: `main`
- 기준 commit: `4e953bd549b7e1adac671956b7a340513a49cbac`
- 작업 브랜치: `test/qa`
- 선행 조건: PR #36이 사용자 승인으로 `main`에 병합됨

## 작업 목적

- PR #36으로 `main`에 병합된 공개 상품 목록·상세 API를 구현과 분리된 QA 관점에서 독립 검증한다.
- PRODUCT-001 요구사항과 API-002 D1~D7을 실제 구현, 자동 테스트와 검증 결과에 직접 대응시킨다.
- Backend 보고 내용을 그대로 승인하지 않고 요구사항·기술 안정성·Security 회귀 증거에 근거한 QA 판정을 남긴다.

## 입력 문서

- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/handoffs/API-002/tl-to-be.md`
- `docs/handoffs/PRODUCT-001/be-to-qa.md`
- `docs/reports/PRODUCT-001/be-report.md`
- PR #36 최종 diff와 병합된 `main` 구현·테스트

## 변경 범위

- 승인 요구사항과 API-002 D1~D7을 병합 구현·테스트와 독립 비교
- 공개 상품 정상·빈 값·공개 상태·오류·Security·query 경계 검토
- 미존재 상품의 SKU Repository 전체 미호출 단위 테스트 강화
- 빈 상품 목록의 HTTP 빈 배열과 prepared statement 1개 통합 테스트 추가
- 숫자가 아닌 상품 ID의 승인된 404 body 회귀 검증 강화

## 변경하지 않은 범위

- Controller, Service, Repository, Entity와 Security 제품 코드
- API-002 D1~D7과 제품 요구사항
- DB schema, Flyway migration, dependency와 CI workflow
- 검색·필터·페이지네이션, 구독·재고·결제·배송·관리자 기능
- AUTH-004 전체 QA 재수행

## 검증 대상과 요구사항 대응

| 승인 항목 | 구현과 자동 증거 | QA 결과 |
| --- | --- | --- |
| `GET /api/products`, `GET /api/products/{productId}` 공개 접근 | `ProductController`, `SecurityConfig`, 익명·인증 MockMvc 통합 테스트 | 계약 일치 |
| D1 목록 SKU별 가격과 구독 가능 요약 | `ProductListView`, batch SKU mapping, JSON shape·number assertion | 계약 일치 |
| D2 배송 주기 | 구독 가능 `[2, 4, 8]`, 불가능 `[]`의 service·integration assertion | 계약 일치 |
| D3 정확한 `PUBLIC`과 동일 404 | `BINARY display_status = 'PUBLIC'`, 소문자·혼합·공백·기타 상태와 미존재 비교 테스트 | 계약 일치 |
| D4 결정적 정렬 | Product native query `id ASC`, SKU query method 정렬과 동률 ID 순서 통합 테스트 | 계약 일치 |
| D5 빈 배열·명시적 null·SKU 없는 상품 유지 | read model과 목록·상세 통합 assertion, 빈 목록 통합 테스트 보완 | 계약 일치 |
| D6 JSON number 가격 | `BigDecimal` 응답과 `jsonPath(...).isNumber()` | 계약 일치 |
| D7 404와 endpoint별 안전한 500 | 고정 code·message·빈 `fieldErrors`, 내부 원인 비노출 Controller 테스트 | 계약 일치 |

검색·필터·페이지네이션, 구독·재고·결제·배송·관리자 API와 승인되지 않은 상품 상태·가격·통화 정책은 구현 diff에 포함되지 않았다. 응답은 JPA Entity가 아니라 `ProductListView`와 `ProductDetailView` 전용 read model을 사용한다.

## 주요 결과

- PRODUCT-001 요구사항과 API-002 D1~D7은 병합된 구현과 일치한다.
- 실제 회귀 증거가 부족했던 빈 목록, 미존재 상품의 SKU 조회 중단과 숫자가 아닌 상품 ID 오류 계약을 테스트로 보완했다.
- Java 25·MySQL 8.4 Repository Validation과 관련 Security 회귀가 통과했으며 재현 가능한 제품 코드 결함은 없다.

## 요구사항 관점 결과

- 목록과 상세의 승인 필드, 공개 접근, 빈 값과 배송 주기 관계가 `REQ-PRODUCT-001`, `REQ-PRODUCT-002`에 일치한다.
- SKU가 없는 공개 상품을 유지하고 컬렉션은 빈 배열, nullable 필드는 JSON `null`로 제공한다.
- 비공개와 미존재 상품은 동일한 404를 사용하고 공개 상태 필드를 노출하지 않는다.
- 승인되지 않은 기능이나 정책 확장은 확인되지 않았다.

## 기술 안정성 관점 결과

- 목록은 Product 1회와 SKU batch 1회로 최대 2개 statement이며 상품 수에 따른 N+1이 없다.
- 빈 목록은 Product statement 1개 후 조기 반환하도록 통합 테스트로 보완했다.
- 상세는 공개 Product 1회와 정렬된 SKU 1회로 최대 2개 statement다.
- 미존재 상품은 SKU Repository와 전혀 상호작용하지 않도록 단위 테스트를 강화했다.
- Mock Repository 단위 테스트는 예상 Product·SKU 호출을 확인하고, MySQL 통합 테스트는 실제 목록 조회가 Product 1회와 SKU batch 1회인 statement 2개로 제한돼 N+1이 발생하지 않음을 별도로 검증한다. 핵심 SQL query 수 계약이 통합 증거로 보호되므로 내부 구현을 더 강하게 고정하는 `verifyNoMoreInteractions`는 추가하지 않았다.

## Security 회귀 관점 결과

- 비회원 목록·상세와 로그인 회원 목록·상세 GET은 CSRF token 없이 허용된다.
- `/api/auth/me` 익명 요청은 401 JSON을 유지하고 인증 요청은 승인된 principal로 접근한다.
- 로그인·로그아웃·CSRF, 세션 무효화와 기본 `/logout` 비활성화는 기존 `AuthIntegrationTests`와 `SecurityFoundationIntegrationTests`가 보호한다.
- 숫자가 아닌 상품 ID의 익명 접근은 404뿐 아니라 승인 code·message·빈 `fieldErrors`까지 확인하도록 강화했다.
- PRODUCT-001 영향 경계 밖의 AUTH-004 전체 시나리오는 복제하지 않았다.

## 추가하거나 수정한 테스트

- `ProductQueryServiceTests.missingOrNonPublicProductUsesNotFoundException`: 특정 인수의 단일 메서드 `never()` 대신 `verifyNoInteractions(skuRepository)` 적용
- `ProductApiIntegrationTests.anonymousEmptyListReturnsEmptyArrayAndUsesOneQuery`: `{ "products": [] }`와 statement 1개 검증
- `SecurityFoundationIntegrationTests.publicProductAndAuthenticationBoundariesAllowAnonymousRequests`: 숫자가 아닌 ID의 승인된 404 body 검증

## 검증 결과

- `java -version`: OpenJDK 17 확인
- `mysql --version`: 실행 파일 없음
- `docker version --format '{{.Server.Version}}'`: Docker engine 없음
- `backend\\gradlew.bat test --tests "com.pawcycle.backend.catalog.product.application.ProductQueryServiceTests"`: 테스트 실행 전 Java 25 toolchain 탐색 실패
- 승인 원본, PR #36 최종 diff, 병합 구현과 기존 자동 테스트 수동 대조: 결함 없음
- `py scripts\\validate-task-artifacts.py --task-id PRODUCT-001`: 통과
- `git diff --check`: 통과
- 2026-07-13 `test/qa`의 최종 QA 테스트 트리와 보고서 후속 갱신을 GitHub Actions Repository Validation로 확인: Java 25·MySQL 8.4 Backend test·build, Frontend install·lint·build, conventions와 task artifact validator 전체 통과

## 실행하지 못한 검증과 이유

- focused Controller·통합 테스트, Backend 전체 test·build는 Java 25 toolchain이 로컬에 없고 download repository도 구성되지 않아 실행하지 못했다.
- 로컬 MySQL 8.4 통합 검증은 MySQL 실행 파일과 Docker engine이 없어 실행하지 못했다.
- 같은 toolchain 원인의 명령을 반복하지 않았고 프로젝트 설정 변경이나 도구 설치도 수행하지 않았다.
- QA 테스트의 실제 Java 25·MySQL 8.4 실행은 QA PR의 Repository Validation로 보완했다.

## 발견한 결함과 심각도

- 재현 가능한 제품 코드 결함은 확인되지 않았다.
- 테스트 증거 공백 세 건만 테스트 전용 코드로 보완했으며 제품 코드 수정은 수행하지 않았다.

## 재검증 결과

- 로컬 재검증은 Java 25·MySQL 8.4 환경 부재로 차단됐다.
- 추가한 테스트는 QA PR Repository Validation의 Java 25·MySQL 8.4 환경에서 통과했다.
- 공개 상품 API 관련 기존 Backend·Security 회귀와 전체 build도 함께 통과했다.

## 최종 판정

- 최종 판정: **통과**
- 승인 계약과 병합 구현의 정적·자동 증거가 일치하고 재현 가능한 기능·보안·회귀 결함이 없다.
- 실제 공백에 한정해 추가한 테스트와 Repository Validation 전체가 통과했다.

## 적용 방법

- 추가한 테스트는 기존 Backend test suite에서 지속적인 PRODUCT-001 회귀 검증으로 사용한다.
- 빈 상품 목록의 배열 계약과 statement 1개, 미존재 상품의 SKU 조회 중단, 숫자가 아닌 상품 ID의 승인 404 오류 계약을 보호한다.
- 이 QA 보고서는 PRODUCT-001 요구사항·구현·테스트 대응과 최종 통과 판정의 근거로 사용한다.
- 런타임 설정, 배포 절차와 제품 코드에는 별도 적용 사항이 없다.

## 위험과 제한

- 추가한 테스트를 로컬 Java 25·MySQL 8.4에서 직접 실행한 근거가 없다.
- 로컬 환경 공백은 동일 테스트를 실행한 GitHub Actions Java 25·MySQL 8.4 성공 이력으로 보완했다.
- PRODUCT-001 승인 범위 안의 알려진 기능 결함은 없다.

## 다음 작업

- 사용자가 QA PR의 테스트와 보고서를 검토하고 병합 여부를 결정한다.

## Git 결과

- 최신 `main`에서 새 `test/qa`를 생성했다.
- QA 테스트와 최초 보고서를 commit `f347f44e225fec13b1ceda1f046dd93b64416779`로 일반 push했다.
- 후속 테스트 리뷰 반영과 QA 보고서 갱신도 일반 commit과 일반 push로 반영했다.
- history rewrite와 force push를 수행하지 않았다.
- 현재 head와 push 상태는 GitHub PR을 권위 있는 원본으로 확인한다.

## PR 상태

- PRODUCT-001 QA PR #37을 `main` ← `test/qa` Draft로 생성했다.
- 현재 PR 상태와 원격 검증 결과는 GitHub를 권위 있는 원본으로 확인한다.
- 자동 병합하지 않는다.
