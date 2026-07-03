# PS-001 PO 작업 보고서

## 작업 정보

- 작업 ID: `PS-001`
- 역할: Product Planner
- 기준 브랜치: `main`
- 작업 브랜치: `spec/po`
- PR 대상: `main`
- 기존 PR: `#4`
- 자동 병합: 하지 않음

## 작업 목적

첫 번째 MVP에서 실제로 다룰 제품 도메인과 대표 사용자 흐름을 확정하고, 후속 요구사항 정의와 DOMAIN-001 도메인 상세 설계에서 사용할 입력을 저장소에 남긴다.

이번 작업은 요구사항 정의서, ERD, API 명세, DB 설계, 애플리케이션 구현을 작성하지 않는다.

## 입력 문서

- `AGENTS.md`
- `docs/roles/product-planner.md`
- `.agents/skills/product-planner/SKILL.md`
- `README.md`
- `CONTRIBUTING.md`
- `docs/runbook/collaboration-automation.md`
- `docs/reports/BOOTSTRAP-004/tl-report.md`
- `docs/handoffs/BOOTSTRAP-004/tl-to-sre.md`
- `docs/product/README.md`
- `docs/product/**`
- `docs/domain/glossary.md`
- `docs/domain/rules.md`
- `docs/adr/README.md`
- `docs/adr/adr-template.md`

필수 입력 경로는 모두 존재했다. 존재하지 않는 문서의 내용을 추측해서 만들지 않았다.

## 기존 PS-001 브랜치 검토 결과

`origin/spec/po`에는 커밋 `352427d docs(product): 제품 비전과 MVP 범위 정리`와 열린 PR `#4`가 있었다. 해당 산출물은 단건 구매, 모의 결제, 재고, 운영자 기능, 구독 변경까지 포함해 이번에 승인된 첫 번째 MVP 범위보다 넓었다.

이번 작업에서는 기존 `spec/po` 산출물을 최종 PR diff에서 제외되도록 삭제하고, 승인된 범위에 맞는 새 문서로 대체했다.

`origin/spec/PS-001-service-vision-mvp`에는 커밋 `0516933 docs: PS-001 비전과 MVP 정의`가 있었다. 해당 브랜치는 제품 문서 외에 현재 `main`의 부트스트랩 문서와 자동화 파일을 대량으로 되돌리는 차이를 포함했다. 따라서 merge 또는 cherry-pick하지 않았다.

재사용한 내용은 반려동물 소모품, 반복 구매, SKU 기반 구독이라는 상위 맥락뿐이다. 일반 구매, 결제, 재고, 배송, 관리자, 구독 변경을 포함한 오래된 MVP 범위는 제외했다.

## 변경 범위

- 1차 MVP 도메인 범위 문서 작성
- 1차 MVP 흐름 도식 문서 작성
- PO에서 BE로 넘기는 인수인계 문서 갱신
- 본 작업 보고서 갱신
- 기존 `spec/po`의 넓은 PS-001 문서 제거

## 변경하지 않은 범위

- 요구사항 정의서
- 기능 요구사항 목록
- 비기능 요구사항 목록
- 상세 인수 조건
- ERD
- DB 테이블, 컬럼, PK, FK
- JPA Entity와 연관관계
- API URI, 요청 DTO, 응답 DTO
- Flyway SQL
- Spring Boot 또는 Next.js 프로젝트
- Docker Compose
- 인증·인가 구현
- 실제 주문, 결제, 재고, 배송 구현
- 구독 변경 기능
- 운영자 화면과 관리자 API

## 확정된 1차 MVP 도메인

- 회원
- 상품
- 상품 옵션 또는 SKU
- 구독
- 배송 주기
- 다음 주문 예정일

배송 주기와 다음 주문 예정일은 별도 엔티티가 아니라 구독을 설명하는 제품 개념 또는 값으로만 다뤘다.

## 도메인별 제품 의미

- 회원: 로그인한 사용자이며 상품을 탐색하고 특정 SKU 구독을 생성한 뒤 자신의 구독과 다음 주문 예정일을 확인한다.
- 상품: 사용자에게 하나의 반려동물 소모품으로 인식되는 공통 상품 정보다.
- 상품 옵션 또는 SKU: 용량, 구성, 맛 등 선택 항목이 확정된 실제 판매 단위다.
- 구독: 회원이 특정 SKU를 정해진 수량과 배송 주기로 반복해서 받기 위해 만든 약속이다.
- 배송 주기: 회원이 구독 생성 과정에서 선택하는 반복 간격이며 다음 주문 예정일의 입력이다.
- 다음 주문 예정일: 다음 주문이 생성될 예정인 날짜를 사용자에게 보여주는 제품 개념이다.

## 도메인 간 제품 관계

- 회원은 상품을 탐색한다.
- 상품은 하나 이상의 상품 옵션 또는 SKU를 제공할 수 있다.
- 회원은 상품의 SKU를 선택한다.
- 회원은 선택한 SKU, 수량, 배송 주기로 구독을 생성한다.
- 구독은 회원, SKU, 수량, 배송 주기와 다음 주문 예정일을 설명한다.
- 회원은 자신의 구독과 다음 주문 예정일을 확인한다.

이 관계를 ERD, 테이블 관계, JPA 연관관계로 확정하지 않았다.

## 대표 사용자 흐름

```text
회원
→ 상품 탐색
→ 상품 옵션 또는 SKU 선택
→ 수량과 배송 주기 선택
→ 구독 생성
→ 자신의 구독 확인
→ 다음 주문 예정일 확인
```

## 확정된 Product Decision

| 항목 | 결정 |
| --- | --- |
| 상위 제품 영역 | 반려동물 소모품 이커머스와 정기배송 |
| 1차 MVP 사용자 | 로그인한 회원 |
| 상품 선택 구조 | 상품을 탐색하고 상품 옵션 또는 SKU를 선택 |
| 구독 대상 | 상품 옵션 또는 SKU |
| 구독 단위 | 하나의 구독에 하나의 SKU |
| 구독 입력 | SKU, 수량, 배송 주기 |
| 구독 결과 | 자신의 구독과 다음 주문 예정일 확인 |
| 일반 구매 구현 | 1차 MVP 제외 |
| 주문·결제·재고·배송 구현 | 1차 MVP 제외 |

## 미확정 Product Decision

- 배송 주기를 2주, 4주, 8주로 제한할지
- 첫 번째 MVP에서 한 가지 배송 주기만 제공할지
- 사용자가 최초 주문 예정일을 선택할지
- 구독 생성일을 기준으로 예정일을 자동 계산할지
- 주문 예정일과 배송 예정일을 구분할지
- SKU의 판매 가능 상태를 어떤 제품 용어로 표현할지
- 첫 번째 MVP 상품 범위를 사료 등 특정 카테고리로 제한할지

## MVP 포함 범위

- 로그인한 회원
- 반려동물 소모품 상품 탐색
- 상품별 옵션 또는 SKU 확인
- 하나의 SKU를 대상으로 한 구독
- 구독 수량 선택
- 배송 주기 선택
- 구독 생성
- 자신의 구독 조회
- 다음 주문 예정일 확인

## MVP 제외 범위

- 일반 구매 주문 생성
- 장바구니
- 여러 상품을 한 번에 주문하는 기능
- 정기 주문 자동 생성
- 실제 결제
- PG 연동
- 결제 실패
- 결제 재시도
- 재고 예약
- 재고 차감
- 품절 후속 처리
- 실제 배송 처리
- 배송 추적
- 구독 수량 변경
- 다음 회차 건너뛰기
- 구독 일시정지
- 구독 재개
- 구독 해지
- 가격 변경 정책
- 구독 할인
- 쿠폰
- 포인트
- 추천 시스템
- 여러 SKU 묶음 구독
- 운영자 기능
- 관리자 화면

## 작성한 도식

- `docs/product/diagrams/PS-001-first-mvp-flow.md`의 제품 범위 Mermaid 도식
- `docs/product/diagrams/PS-001-first-mvp-flow.md`의 사용자 흐름 Mermaid 도식
- `docs/product/diagrams/PS-001-first-mvp-flow.md`의 텍스트 블록 도식

도식에는 제품 관점의 개념과 사용자 행동만 표현했다. 데이터베이스, 테이블, JPA Entity, Controller, Service, Repository, API URI, DTO, 배치 스케줄러, 트랜잭션, 이벤트 발행, 결제 호출, 재고 차감은 포함하지 않았다.

## 변경 파일

- `docs/product/PS-001-first-mvp-domain-scope.md`
- `docs/product/diagrams/PS-001-first-mvp-flow.md`
- `docs/handoffs/PS-001/po-to-be.md`
- `docs/reports/PS-001/po-report.md`

제거한 기존 `spec/po` 산출물은 다음과 같다.

- `docs/product/service-vision.md`
- `docs/product/target-users.md`
- `docs/product/mvp-scope.md`
- `docs/product/user-journeys.md`
- `docs/product/product-decisions/PS-001-mvp-policy.md`

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| `git status --short --branch` | 통과. `spec/po`에서 의도한 문서 변경만 확인 |
| `git diff --check` | 통과 |
| `git diff --stat` | 통과. 제품 문서, 도식, 보고서, 인수인계 변경만 확인 |
| `"PS-001" | python scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "docs(product): 1차 MVP 도메인과 흐름 정리"` | 통과 |
| Secret 패턴 검색 | 실제 Secret 없음. 스크립트의 변수명과 검증 문자열만 확인 |

## 위험과 제한

- 기존 `spec/po` PR에는 더 넓은 MVP 범위가 있었으므로 PR 본문을 새 승인 범위로 갱신해야 한다.
- 배송 주기 선택지와 다음 주문 예정일 계산 규칙은 아직 Product Decision으로 남아 있다.
- 전역 도메인 용어집은 일반 제품 방향을 담고 있으므로 DOMAIN-001에서 첫 번째 MVP 범위와 구분해 정리해야 한다.
- 이번 문서는 요구사항 정의서가 아니므로 후속 작업에서 성공 조건, 실패 조건, 접근 제어, 표시 규칙을 별도로 구체화해야 한다.

## 다음 작업

요구사항 정의서 작성 작업에서는 확정된 도메인과 사용자 흐름을 기능 요구사항, 성공·실패 조건과 인수 조건으로 구체화한다.

DOMAIN-001에서는 회원, 상품, SKU와 구독의 책임, 상태 필요성, 불변 조건, 판매 불가능한 SKU 처리, 배송 주기 표현 방식, 다음 주문 예정일 계산 책임을 구체화한다.

## Git 결과

- 브랜치: `spec/po`
- 커밋: 검증 후 작성
- push: 검증 후 수행
- PR: 기존 PR `#4` 갱신 예정

## PR 상태

기존 PR `#4`를 새 승인 범위에 맞게 갱신했다. 자동 병합은 하지 않는다.
