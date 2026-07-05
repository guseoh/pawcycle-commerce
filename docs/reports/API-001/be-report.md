# API-001 Backend Engineer 작업 보고서

## 작업 정보

- 작업 ID: `API-001`
- 역할: Backend Engineer
- 기준 브랜치: 최신 `main`
- 작업 브랜치: `feat/be`
- 작업 유형: API 계약 문서화
- 결정 상태: Proposed API Contract

## 작업 목적

PS-002, DOMAIN-001, UX-001, PS-003, UX-002, ARCH-001, DATA-001에서 승인·제안된 입력을 바탕으로 첫 번째 수직 MVP API 계약 후보를 설계했다.

공개 상품 목록/상세 조회, 로그인 회원의 구독 생성, 내 구독 목록 조회, 내 구독 상세 조회에 필요한 HTTP 메서드, URI 후보, 인증·인가 요구사항, 요청 필드, 응답 필드, 성공 상태, 오류 상태, 오류 코드 후보, 날짜 표현과 추적성을 정리했다.

## 승인된 입력

- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/product/PS-003-ux-product-decisions.md`
- `docs/handoffs/UX-002/ux-to-tl.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/handoffs/ARCH-001/tl-to-data-api.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/handoffs/DATA-001/be-to-api.md`

선행 PR 확인:

| PR | 제목 | 상태 |
| --- | --- | --- |
| #15 | `docs(architecture): 첫 수직 MVP 아키텍처 결정` | `MERGED` |
| #16 | `chore(review): CodeRabbit 리뷰 설정 추가` | `MERGED` |
| #17 | `docs(data): 첫 수직 MVP 데이터 모델 설계` | `MERGED` |

## 변경 범위

- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/reports/API-001/be-report.md`
- `docs/handoffs/API-001/be-to-fe-qa.md`

## 변경하지 않은 범위

- Backend 구현은 수행하지 않았다.
- Frontend 구현은 수행하지 않았다.
- Flyway 마이그레이션은 작성하지 않았다.
- JPA Entity는 작성하지 않았다.
- Repository, Service, Controller는 작성하지 않았다.
- 테스트 코드는 작성하지 않았다.
- OpenAPI 생성 도구 또는 신규 의존성은 추가하지 않았다.
- Spring Security 구현 방식은 확정하지 않았다.
- 로그인 화면과 Next.js 라우팅은 구현하지 않았다.
- 결제, 재고, 배송, 구독 상태 모델은 추가하지 않았다.
- soft delete, `deleted_at`, `is_deleted`는 추가하지 않았다.
- soft delete, hard delete, 삭제·탈퇴·보관·익명화 정책은 확정하지 않았다.
- CodeRabbit 설정은 변경하지 않았다.
- GitHub Actions는 변경하지 않았다.
- 자동 병합하지 않는다.

## 주요 API 계약 결과

- `GET /api/products` 상품 목록 조회 후보를 공개 API로 제안했다.
- `GET /api/products/{productId}` 상품 상세 조회 후보를 공개 API로 제안했다.
- `POST /api/subscriptions` 구독 생성 후보를 로그인 필요 API로 제안했다.
- `GET /api/subscriptions` 내 구독 목록 조회 후보를 로그인 필요 API로 제안했다.
- `GET /api/subscriptions/{subscriptionId}` 내 구독 상세 조회 후보를 로그인 필요 API로 제안했다.
- API 날짜 표현 후보를 `YYYY-MM-DD`로 제안하고 화면 표시는 `YYYY. M. D.`로 분리했다.
- 구독 생성 성공 응답 후보에 `subscriptionId`, `nextOrderDate`를 포함했다.
- 존재하지 않는 구독과 다른 회원 소유 구독을 같은 `SUBSCRIPTION_NOT_FOUND` 후보로 표현하도록 제안했다.
- 필수 입력 누락, 수량 범위 위반, 배송 주기 허용값 위반, 구독 불가능 SKU, 존재하지 않는 리소스 오류 후보를 정리했다.

## 요구사항 추적성

| 요구사항 | API-001 반영 |
| --- | --- |
| `REQ-PRODUCT-001` | `GET /api/products`, 상품 목록 표시 필드 후보 |
| `REQ-PRODUCT-002` | `GET /api/products/{productId}`, SKU 목록·가격·구독 가능 여부·배송 주기 후보 |
| `REQ-SUB-001` | `POST /api/subscriptions`, `skuId`, `quantity`, `deliveryCycleWeeks`, `subscriptionId`, `nextOrderDate` |
| `REQ-SUB-002` | `GET /api/subscriptions`, 본인 구독 목록 |
| `REQ-SUB-003` | `GET /api/subscriptions/{subscriptionId}`, 본인 구독 상세 |
| `REQ-SUB-004` | `createdDate`, `nextOrderDate`, `YYYY-MM-DD`, `Asia/Seoul` |
| `REQ-AUTH-001` | 공개 API와 보호 API 분리, 보호 API 인증 필요 오류 후보 |
| `REQ-AUTH-002` | 다른 회원 구독 상세 접근을 조회할 수 없는 구독으로 통합 표현 |

## 후속 작업 영향

- Frontend는 API-001 승인 후 필드명과 상태 후보를 기준으로 API 연동과 화면 상태를 구현할 수 있다.
- QA는 정상·오류·인증·인가·날짜 기준 검증 항목을 API-001에서 도출할 수 있다.
- Backend 구현은 Controller, DTO, Service, Repository, 예외 처리와 테스트를 API-001 승인 뒤 작성해야 한다.
- 인증 설계는 보호 API 인증 실패 표현, 로그인 복귀 경로, Open Redirect 방지 구현 방식을 별도로 확정해야 한다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| `git status --short --branch` | 통과, API-001 산출물 3개만 변경 확인 |
| `git diff --check` | 통과 |
| `git diff --stat` | 통과 |
| `git diff --name-status` | 통과 |
| `Write-Output 'API-001' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "docs(api): 첫 수직 MVP API 계약 설계"` | 통과 |
| 필수 산출물 존재 확인 | 통과 |
| 필수 API 5개 포함 확인 | 통과 |
| 요구사항 ID 8개 포함 확인 | 통과 |
| 인증·인가, 필수 입력, 값 범위, 구독 불가능 SKU, 존재하지 않는 리소스 오류 후보 확인 | 통과 |
| 날짜 표현, `Asia/Seoul`, 다음 배송 예정일 제외 확인 | 통과 |
| DATA-001 매핑 확인 | 통과 |
| 구현·마이그레이션·신규 의존성 미작성 확인 | 통과 |
| CodeRabbit 설정 변경 없음 확인 | 통과 |
| Secret 또는 민감정보 패턴 확인 | 통과 |

## 위험과 제한

- 이 작업은 API 계약 후보 문서화이며 실제 API 구현이 아니다.
- URI, 필드명, HTTP 상태, 오류 코드 후보는 사용자 승인 전 최종 계약이 아니다.
- 인증 구현 방식과 Spring Security 설정은 확정하지 않았다.
- 삭제 정책은 API-001에서 확정하지 않았다.
- 결제, 재고, 배송, 구독 상태가 후속 MVP에 추가되면 API 계약 재검토가 필요하다.
- CodeRabbit 리뷰는 Draft PR에서는 자동 실행되지 않을 수 있으며, 사용자가 Ready for review 전환 후 확인한다.

## CodeRabbit 확인 계획

`.coderabbit.yaml`에서 Draft PR 리뷰가 비활성화되어 있으므로 API-001 PR은 Draft로 생성한다.

검증 후 사용자가 Ready for review로 전환하면 CodeRabbit 리뷰를 확인한다. CodeRabbit 지적은 전부 반영하지 않고, 제품·도메인·아키텍처 승인 범위와 충돌하지 않는 필요한 항목만 선별한다.

## Git 결과

- 작업 시작 전 브랜치: `feat/be`
- 기존 로컬 `feat/be` SHA: `af3fd10adf486cf6c9b5a934d8877708b69cc24d`
- 기존 원격 `origin/feat/be` SHA: `af3fd10adf486cf6c9b5a934d8877708b69cc24d`
- 기존 `origin/main..origin/feat/be` 로그:

```text
af3fd10 docs(data): DATA-001 리뷰 반영
986bfb3 docs(report): DATA-001 PR 상태 갱신
e40d734 docs(data): 첫 수직 MVP 데이터 모델 설계
```

- 원격 백업 브랜치: `backup/feat-be-before-API-001`
- 로컬 백업 브랜치: `backup/feat-be-before-API-001`
- 로컬 추가 백업 브랜치: `backup/local-feat-be-before-API-001`
- 원격 `origin/feat/be` 삭제 완료
- 로컬 `feat/be` 삭제 완료
- 최신 `main` fast-forward 완료
- 새 `feat/be` 시작 SHA: `5d94f9a9744b30a724821da66210490ecd187783`
- commit, push는 검증 후 수행한다.

## PR 결과

Draft PR은 검증과 push 후 생성한다. 자동 병합하지 않는다.
