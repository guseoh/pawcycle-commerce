# ARCH-001 Tech Lead 작업 보고서

## 작업 정보

- 작업 ID: `ARCH-001`
- 역할: Tech Lead
- 기준 브랜치: 최신 `main`
- 작업 브랜치: `ops/tl`
- 선행 PR:
  - PR #5 `PS-002` 요구사항 정리
  - PR #7 `DOMAIN-001` 구독 도메인 설계
  - PR #11 `UX-001` 구독 사용자 흐름 설계
  - PR #13 `PS-003` UX 제품 결정 확정
  - PR #14 `UX-002` PS-003 제품 결정 UX 반영
- PR #14 병합 확인: `2026-07-05T14:13:05Z`
- Tech Lead 전용 역할 문서와 Skill은 저장소에서 확인되지 않아 루트 `AGENTS.md`, ADR 템플릿, 사용자 지시를 기준으로 진행했다.

## 작업 목적

첫 번째 수직 MVP의 시스템 경계, 책임 분리, 인증·인가 경계, 로그인 복귀 경계, 날짜 책임, 오류 처리 경계와 후속 DATA-001/API-001 입력을 문서화했다.

이번 작업은 구현 작업이 아니며 후속 DATA-001, API-001, Backend, Frontend, QA가 추측 없이 설계와 검증을 이어갈 수 있도록 승인 제품·도메인·UX 입력을 아키텍처 관점으로 정리했다.

## 승인된 입력

- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/product/PS-003-ux-product-decisions.md`
- `docs/handoffs/UX-002/ux-to-tl.md`

## 변경 범위

- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/reports/ARCH-001/tl-report.md`
- `docs/handoffs/ARCH-001/tl-to-data-api.md`

## 변경하지 않은 범위

- 구현은 수행하지 않았다.
- 백엔드 코드를 변경하지 않았다.
- 프론트엔드 코드를 변경하지 않았다.
- API 계약은 확정하지 않았다.
- API 요청·응답 JSON은 확정하지 않았다.
- HTTP 상태와 오류 코드는 확정하지 않았다.
- DB 설계는 확정하지 않았다.
- 테이블명, 컬럼명, FK, 인덱스는 확정하지 않았다.
- 인증 구현 방식은 확정하지 않았다.
- URL과 라우트 문자열은 확정하지 않았다.
- 신규 의존성은 추가하지 않았다.
- PS-002, PS-003 제품 정책을 변경하지 않았다.
- DOMAIN-001 도메인 규칙을 변경하지 않았다.
- UX-002 화면 정책을 변경하지 않았다.

## 주요 아키텍처 결정

- 첫 번째 수직 MVP 시스템 경계를 `사용자 → 프론트엔드 → 백엔드 API → 도메인/애플리케이션 서비스 → 영속성`으로 정리했다.
- 상품 목록, 상품 상세, SKU와 구독 가능 여부 확인은 공개 조회 경계로 정리했다.
- 구독 생성, 내 구독 목록, 내 구독 상세는 로그인 회원 보호 기능으로 정리했다.
- 내 구독 목록과 상세는 본인 구독만 조회하도록 소유자 검증이 필요하다고 정리했다.
- 로그인 후 복귀는 안전한 서비스 내부 GET 경로로 제한하고, 유효한 경로가 없으면 상품 목록으로 이동하는 인증 설계 책임으로 분리했다.
- 입력 폼 자동 복원과 이전 POST 요청 자동 재실행은 첫 번째 MVP에서 제외했다.
- 다음 주문 예정일은 백엔드 도메인 또는 애플리케이션 경계에서 계산하고, 프론트엔드는 생성 전 정확한 날짜를 계산해 표시하지 않도록 정리했다.
- 오류 처리 범주는 정리했지만 HTTP 상태, 오류 코드, 오류 응답 JSON은 API-001로 넘겼다.
- DATA-001, API-001, FE, QA 입력을 별도 섹션으로 분리했다.

## 요구사항 추적성

| 요구사항 | ARCH-001 연결 |
| --- | --- |
| `REQ-PRODUCT-001` | 상품 목록 공개 조회 경계 |
| `REQ-PRODUCT-002` | 상품 상세·SKU·구독 가능 여부 공개 조회 경계 |
| `REQ-SUB-001` | 구독 생성 인증, SKU 하나, 수량·배송 주기 검증, 생성 트랜잭션 후보 |
| `REQ-SUB-002` | 내 구독 목록 인증과 본인 구독 조회 |
| `REQ-SUB-003` | 내 구독 상세 인증, 소유자 검증, 생성 성공 후 이동 대상 |
| `REQ-SUB-004` | 다음 주문 예정일 서버 계산과 생성 전 미표시 |
| `REQ-AUTH-001` | 공개 탐색과 보호 기능 경계, 로그인 후 안전한 내부 GET 복귀 |
| `REQ-AUTH-002` | 다른 회원 구독 접근 차단과 조회할 수 없는 구독 표현 |

## 후속 작업 영향

- DATA-001은 Product, SKU, Member, Subscription 저장 구조와 관계, 수량·배송 주기·날짜·구독 가능 여부 저장 방식을 정해야 한다.
- API-001은 상품 목록, 상품 상세, 구독 생성, 내 구독 목록, 내 구독 상세 계약과 인증·인가 실패, 오류 코드, HTTP 상태, 날짜 표현을 정해야 한다.
- 인증 설계는 로그인 복귀 경로 저장 방식, 안전한 내부 GET 경로 검증, Open Redirect 방지를 정해야 한다.
- Backend는 ARCH-001과 API-001 이후 도메인·애플리케이션·API 구현과 테스트를 진행해야 한다.
- Frontend는 UX-002와 API-001 이후 공개 탐색, 보호 기능 이동, 생성 후 상세 이동, 생성 전 예정일 미표시를 구현해야 한다.
- QA는 요구사항, DOMAIN-001, UX-002, ARCH-001, API-001을 기준으로 공개 접근, 보호 기능, 소유자 검증, 날짜 책임과 오류 흐름을 검증해야 한다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| `git status --short --branch` | 통과, `ops/tl`에서 ARCH-001 문서·보고서·인수인계 추가 확인 |
| `git diff --check` | 통과 |
| `git diff --stat` | 통과 |
| `git diff --name-status` | 통과 |
| `Write-Output 'ARCH-001' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| `scripts/validate-commit-message.sh --message "docs(architecture): 첫 수직 MVP 아키텍처 결정"` | 통과 |
| ARCH-001 산출물 3개 존재 확인 | 통과 |
| 요구사항 ID 8개 포함 확인 | 통과 |
| PS-003 네 가지 Product Decision 반영 확인 | 통과 |
| API JSON, HTTP 상태, URL 문자열 미확정 확인 | 통과 |
| DB 테이블과 컬럼 미확정 확인 | 통과 |
| 인증 구현 방식 미확정 확인 | 통과 |
| 코드 변경 없음 확인 | 통과 |
| 신규 의존성 없음 확인 | 통과 |
| Secret 또는 민감정보 패턴 확인 | 통과 |

## 위험과 제한

- ARCH-001은 Proposed Architecture Decision이며 사용자 승인 전 `Approved`가 아니다.
- API 계약, DB 설계, 인증 구현 방식이 확정되지 않아 구현 작업은 후속 설계 이후 진행해야 한다.
- 로그인 복귀 경로 저장과 Open Redirect 방지는 후속 인증 설계에서 반드시 검증해야 한다.
- 구독 생성 성공 응답의 정확한 구조가 확정되지 않아 API-001이 후속 상세 이동에 필요한 식별자를 계약으로 정해야 한다.
- Tech Lead 전용 역할 문서와 Skill은 저장소에서 확인되지 않았다.

## Git 결과

- 기존 로컬 `ops/tl` SHA: `cc2b4127b6d4bdc1de3730a5cfa28fd8b3ee9909`
- 기존 원격 `origin/ops/tl` SHA: `c577096be7db7e77e8a9f910653786d444f12017`
- 기존 `origin/main..origin/ops/tl` 로그:

```text
c577096 docs(report): BOOTSTRAP-007 PR 상태 갱신
73fabe5 fix(validation): UX와 DATA 작업 ID 인식 추가
```

- 원격 백업 브랜치: `backup/ops-tl-before-ARCH-001`
- 로컬 백업 브랜치: `backup/local-ops-tl-before-ARCH-001`
- 원격 `origin/ops/tl` 삭제 완료
- 로컬 `ops/tl` 삭제 완료
- 최신 `main`에서 새 `ops/tl` 생성 완료
- commit, push는 검증 후 수행한다.

## PR 결과

Draft PR은 검증과 push 후 생성한다. 자동 병합하지 않는다.
