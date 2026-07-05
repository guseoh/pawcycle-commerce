# DATA-001 Backend Engineer 작업 보고서

## 작업 정보

- 작업 ID: `DATA-001`
- 역할: Backend Engineer
- 기준 브랜치: 최신 `main`
- 작업 브랜치: `feat/be`
- 작업 유형: 데이터 모델 설계 문서화
- 결정 상태: Proposed Data Design

## 작업 목적

PS-002, DOMAIN-001, UX-001, PS-003, UX-002, ARCH-001에서 승인된 입력을 바탕으로 첫 번째 수직 MVP의 데이터 모델을 설계했다.

후속 API-001, Backend 구현, QA가 Product, SKU, Member, Subscription의 저장 구조, 관계, 제약 조건 후보, 인덱스 후보, 날짜 저장 기준, 구독 가능 여부 저장 방식을 추측하지 않도록 문서화했다.

## 승인된 입력

- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/product/PS-003-ux-product-decisions.md`
- `docs/reports/UX-002/ux-report.md`
- `docs/handoffs/UX-002/ux-to-tl.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/handoffs/ARCH-001/tl-to-data-api.md`

선행 PR 확인:

| PR | 제목 | 상태 |
| --- | --- | --- |
| #15 | `docs(architecture): 첫 수직 MVP 아키텍처 결정` | `MERGED` |
| #16 | `chore(review): CodeRabbit 리뷰 설정 추가` | `MERGED` |

## 변경 범위

- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/reports/DATA-001/be-report.md`
- `docs/handoffs/DATA-001/be-to-api.md`

## 변경하지 않은 범위

- 구현은 수행하지 않았다.
- Flyway 마이그레이션은 작성하지 않았다.
- JPA Entity는 작성하지 않았다.
- Repository, Service, Controller는 작성하지 않았다.
- API 계약은 확정하지 않았다.
- API 요청·응답 JSON은 확정하지 않았다.
- HTTP 상태와 오류 코드는 확정하지 않았다.
- URL과 라우트 문자열은 확정하지 않았다.
- 인증 구현 방식은 확정하지 않았다.
- 신규 의존성은 추가하지 않았다.
- 결제, 재고, 배송, 구독 상태 모델은 추가하지 않았다.
- CodeRabbit 설정은 변경하지 않았다.
- GitHub Actions는 변경하지 않았다.

## 주요 데이터 설계 결과

- 첫 MVP 테이블 후보를 `members`, `products`, `skus`, `subscriptions`로 정리했다.
- Product는 상품 목록·상세 표시의 기준으로 설계했다.
- SKU는 실제 구독 선택 단위이며 `subscribable` 구독 가능 여부 후보를 가진다.
- Member는 구독 소유자 검증의 기준으로 설계했다.
- Subscription은 회원과 SKU 하나를 참조하며 수량, 배송 주기, 구독 생성일, 다음 주문 예정일을 보존한다.
- 수량 1~10과 배송 주기 2/4/8은 도메인 검증과 DB 제약 후보로 함께 기록했다.
- 다음 주문 예정일은 서버가 계산한 확정값으로 저장하는 방향을 우선 검토하도록 기록했다.
- 날짜는 `Asia/Seoul` 기준 날짜 단위로 다루고, 휴일·주말·영업일 보정과 다음 배송 예정일 저장은 제외했다.
- 내 구독 목록·상세의 소유자 검증을 위해 `subscriptions.member_id`와 관련 인덱스 후보를 정리했다.

## 요구사항 추적성

| 요구사항 | DATA-001 반영 |
| --- | --- |
| `REQ-PRODUCT-001` | `products`, `skus`와 SKU 구독 가능 요약 후보 |
| `REQ-PRODUCT-002` | Product 상세, SKU 목록, 가격, `skus.subscribable` |
| `REQ-SUB-001` | `subscriptions` 생성 데이터, 회원·SKU 참조, 수량·주기·날짜 제약 후보 |
| `REQ-SUB-002` | 본인 구독 목록 조회를 위한 `subscriptions(member_id, id)` 후보 |
| `REQ-SUB-003` | 단건 상세와 소유자 검증을 위한 `subscriptions.member_id` |
| `REQ-SUB-004` | `created_date`, `next_order_date`, `Asia/Seoul` 날짜 단위 |
| `REQ-AUTH-001` | 보호 기능 데이터는 인증 회원 식별자를 기준으로 생성·조회 |
| `REQ-AUTH-002` | 다른 회원 구독 조회 차단을 위한 소유자 검증 근거 |

## 후속 작업 영향

- API-001은 DATA-001의 테이블·필드 후보를 참고해 응답 구조와 오류 계약을 확정해야 한다.
- Backend 구현은 실제 JPA Entity, Flyway DDL, 값 객체, 트랜잭션 경계, Repository 쿼리를 코드와 테스트로 확정해야 한다.
- QA는 DATA-001의 제약 후보와 조회 흐름별 데이터 요구사항을 테스트 계획의 데이터 기준으로 사용할 수 있다.
- 인증 설계는 Member 후보와 실제 인증 테이블·권한 모델의 관계를 별도로 확정해야 한다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| `git status --short --branch` | 통과, DATA-001 산출물 3개만 변경 확인 |
| `git diff --check` | 통과 |
| `git diff --stat` | 통과 |
| `git diff --name-status` | 통과 |
| `Write-Output 'DATA-001' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "docs(data): 첫 수직 MVP 데이터 모델 설계"` | 통과 |
| 필수 산출물 존재 확인 | 통과 |
| 요구사항 ID 8개 포함 확인 | 통과 |
| Product, SKU, Member, Subscription 포함 확인 | 통과 |
| 구독 하나가 SKU 하나를 참조한다는 규칙 확인 | 통과 |
| 수량 1~10, 배송 주기 2/4/8, SKU 구독 가능 여부 확인 | 통과 |
| 다음 주문 예정일, `Asia/Seoul`, 휴일·주말·영업일 보정 없음, `YYYY. M. D.` 기준 확인 | 통과 |
| 구현·마이그레이션·API 계약 미작성 확인 | 통과 |
| CodeRabbit 설정 변경 없음 확인 | 통과 |
| Secret 또는 민감정보 패턴 확인 | 통과 |

## 위험과 제한

- 이 작업은 데이터 설계 문서화이며 DB 스키마 적용이 아니다.
- SQL DDL, MySQL CHECK 제약 실제 지원 여부, 인덱스명은 후속 마이그레이션 작업에서 검증해야 한다.
- API 요청·응답 JSON, HTTP 상태와 오류 코드를 확정하지 않아 API-001에서 추가 결정이 필요하다.
- 인증 구현 방식이 확정되지 않아 Member와 실제 인증 모델의 관계는 후속 인증 설계 또는 Backend 구현에서 결정해야 한다.
- CodeRabbit 리뷰는 Draft PR에서는 자동 실행되지 않을 수 있으며, 사용자가 Ready for review 전환 후 확인한다.

## CodeRabbit 확인 계획

`.coderabbit.yaml`에서 Draft PR 리뷰가 비활성화되어 있으므로 DATA-001 PR은 Draft로 생성한다.

검증 후 사용자가 Ready for review로 전환하면 CodeRabbit 리뷰를 확인한다. CodeRabbit 지적은 전부 반영하지 않고, 제품·도메인·아키텍처 승인 범위와 충돌하지 않는 필요한 항목만 선별한다.

## Git 결과

- 작업 시작 전 브랜치: `ops/sre`
- 최신 `main`으로 fast-forward 후 새 `feat/be` 생성
- 기존 로컬 `feat/be`: 없음
- 기존 원격 `origin/feat/be` SHA: `1ab6f33e2179625007a8fe7dfe2ebe227224a369`
- 기존 `origin/main..origin/feat/be` 로그:

```text
1ab6f33 docs(report): DOMAIN-001 PR 상태 갱신
f58bf8f docs(domain): DOMAIN-001 구독 도메인 설계
```

- 원격 백업 브랜치: `backup/feat-be-before-DATA-001`
- 로컬 백업 브랜치: `backup/feat-be-before-DATA-001`
- 로컬 `feat/be`가 없어 `backup/local-feat-be-before-DATA-001`는 만들지 않음
- 원격 `origin/feat/be` 삭제 완료
- 새 `feat/be` 시작 SHA: `db1d149bdb442c2c99808ac166e80be5af0b79d3`
- 1차 작업 커밋: `e40d7344c03b9d100e3ccd4143aa6198370e8fd0`
- push: `origin/feat/be` 업스트림 설정 완료
- PR 상태 갱신은 후속 보고서 커밋으로 수행한다.

## PR 결과

- Draft PR: #17
- PR URL: https://github.com/guseoh/pawcycle-commerce/pull/17
- title: `docs(data): 첫 수직 MVP 데이터 모델 설계`
- head/base: `feat/be` -> `main`
- 상태: Open Draft
- PR checks:
  - `Repository Validation / Commit and PR conventions`: pass
  - `Collaboration Notification / Discord collaboration notification`: pass
  - `CodeRabbit`: pass, `Review skipped: draft pull request`
- 자동 병합하지 않았다.
