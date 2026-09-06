# 🐾 PawCycle Commerce

> **반려동물 반복 구매 상품의 일반 Commerce와 Subscription을 실제 운영·검증·개선하는 이커머스 프로젝트**

PawCycle Commerce는 상품 탐색부터 구매, 주문 이후 처리, 정기배송까지 이어지는 고객 경험과 이를 운영하는 관리자 기능을 함께 구현합니다.

단순히 기능 수를 늘리는 것보다 **정합성, 실패 복구, 운영 안전성, 성능 측정, 아키텍처 선택의 근거**를 실제 코드와 검증 증거로 남기는 것을 중요하게 봅니다.

또한 AI가 구현을 돕는 환경에서도 사용자가 제품 목표·범위·위험·검증을 통제할 수 있도록 **Risk-based Lean Harness**를 함께 운영합니다.

---

## 📌 Project Snapshot

| 구분 | 현재 범위 |
| --- | --- |
| Product | Catalog · Search · PLP · PDP · Review/Q&A · Wishlist · Cart · Checkout · Order · After-sales · Subscription · Member |
| Admin | Catalog · Inventory · Coupon · Membership · Order · Delivery · Return · Refund · Payment/Billing Recovery · Review/Q&A · Audit |
| Customer Catalog | 100 Products · 166 SKUs · 10 Brands · 27 Customer Categories |
| Backend | Java 25 · Spring Boot 4.1 · Spring MVC · Spring Security · Spring Data JPA |
| Frontend | Next.js 16 · React 19 · TypeScript 6 |
| Data / Cache | MySQL · Flyway · Redis |
| Payment / AI | Toss Payments · Spring AI |
| Operations | Docker Compose · Nginx · GHCR · GitHub Actions · Prometheus · Grafana · Alertmanager |

현재 저장소는 고객·관리자 Commerce 흐름을 연결한 뒤 Backend persistence와 HTTP/Frontend client 구조를 정리한 상태입니다.

---

## 🛒 Product

PawCycle의 핵심은 정기배송 하나가 아니라, **일반 구매와 반복 구매가 하나의 Commerce 흐름 안에서 이어지는 것**입니다.

### 고객 흐름

- 상품 검색, 카테고리·Facet 필터, 정렬, 비교
- 상품 상세, SKU 선택, 재고·구매 가능 상태 확인
- 리뷰와 상품 문의
- Wishlist와 Cart
- 배송지, Coupon, Checkout
- Toss Payments 기반 결제 흐름
- 주문 조회와 배송 상태
- 취소, 반품, 환불
- 재구매와 정기배송 전환
- 정기배송 생성, 조회, 주기·배송지·Plan 변경
- Billing / Schedule / Reconciliation 상태 관리
- Pet, Address, Notification 등 회원 기능

### 관리자 흐름

- 상품·카테고리·Facet·SKU 관리
- Inventory 조정
- Coupon / Membership 운영
- 주문·배송·취소·반품·환불 관리
- Payment reconciliation / retry
- Billing recovery
- Review / Q&A 운영
- Audit Log 확인

Customer Commerce의 주요 화면과 관리자 운영 진입점은 실제 Backend API와 연결된 상태입니다.

---

## 🧱 Architecture

PawCycle은 하나의 Spring Boot Backend와 Next.js Frontend를 중심으로 한 **Modular Monolith** 형태를 유지합니다.

Backend는 기능별 Controller → Application Service → Persistence Boundary로 책임을 나누고, 관계형 데이터 접근은 JPA를 기본으로 사용합니다. Lock, CAS, 운영 조회처럼 의미가 명확한 일부 경계에는 직접 SQL을 제한적으로 유지합니다.

Frontend는 feature API module이 공통 HTTP transport를 사용하며, Session·CSRF·Idempotency·ETag 같은 서버 계약을 그대로 보존합니다.

Runtime에서는 Nginx가 외부 진입점 역할을 하고 Frontend와 Backend를 분리해 전달하며, Backend만 MySQL과 통신합니다. Redis는 상품 조회 Cache에 사용하고, Micrometer 기반 Metric은 Prometheus/Grafana/Alertmanager로 연결합니다.

> README용 Architecture Visual은 현재 텍스트 도식을 제거한 뒤 별도 이미지 자산으로 교체할 예정입니다.

상세 구조:

- [Backend Persistence Convergence](docs/architecture/backend-persistence-convergence.md)
- [Production Operations Overview](docs/architecture/production-operations-overview.md)
- [Commerce Runtime Refactoring ADR](docs/adr/ARCH-009-commerce-runtime-refactoring.md)

---

## 🔐 Commerce Correctness

Commerce에서는 기능이 동작하는 것뿐 아니라 **중복 요청, 동시성, 상태 전이, 실패 복구**가 중요합니다.

PawCycle에서는 다음 경계를 코드와 테스트로 다룹니다.

- Session Authentication / Authorization / CSRF
- Idempotency reservation과 성공 결과 replay
- Cart version conflict
- Order / Payment / Inventory / Subscription 상태 전이
- Pessimistic Lock과 조건부 mutation
- Atomic upsert
- Subscription reconciliation
- Schedule별 실패 격리
- UTC 기반 시간 계약
- Validation과 공통 API error contract

최근 Backend persistence 수렴에서는 `JdbcTemplate` 기반 구현을 단순 치환하지 않고 기존 lock·idempotency·transaction 의미를 보존하는 것을 우선했습니다.

**Evidence**

- [PR #275 - Backend 구조 리팩터링](https://github.com/guseoh/pawcycle-commerce/pull/275)
- [PR #278 - Backend 구조 및 기술 부채 정리](https://github.com/guseoh/pawcycle-commerce/pull/278)
- [PR #279 - Persistence JPA 수렴](https://github.com/guseoh/pawcycle-commerce/pull/279)
- [PR #280 - HTTP Contract / Frontend Client 수렴](https://github.com/guseoh/pawcycle-commerce/pull/280)

---

## 📈 Performance & Trade-off Topics

성능과 아키텍처는 기술을 먼저 선택한 뒤 이유를 붙이지 않고, **문제를 재현하고 측정한 뒤 선택**하는 방향으로 진행합니다.

아직 현재 Product Completion 구조를 기준으로 한 성능 개선 글과 Trade-off 정리는 작성하지 않았기 때문에 README에서는 결과를 먼저 결론 내리지 않고, 앞으로 검증할 주제만 기록합니다.

- 10K / 100K / 1M Scale Dataset과 Performance Re-baseline
- SQL / Index / JPA Query Plan
- N+1 / Pagination / Projection 비용
- Transaction / Lock Contention / Deadlock
- Connection Pool / JVM / GC
- Redis Cache hit·miss·staleness와 유지 범위
- Async / Queue / Kafka가 필요한 실제 조건
- Multi-instance / Load Balancer / Container Platform 확장 기준
- HA / Failover / Backup / Restore의 비용과 복구 목표
- 각 선택의 Before / After, Trade-off, 남은 한계

---

## 🔄 Reliability & Recovery

### Subscription 실패 격리

여러 Subscription을 하나의 Transaction으로 처리할 때 하나의 실패가 전체 Batch에 영향을 줄 수 있어, Subscription별 독립 Transaction으로 처리하도록 변경했습니다.

- 실패 Subscription만 rollback
- 이후 Subscription 계속 처리
- 실패 대상 식별
- 재처리와 장애 분석 경계 확보

**Evidence:** [PR #106](https://github.com/guseoh/pawcycle-commerce/pull/106)

### Idempotency Retention

중복 요청 Replay를 위해 성공 결과를 보관하되 영구 증가하지 않도록 Retention과 bounded cleanup을 적용했습니다.

- 성공 완료 시각 기록
- 30일 Retention
- bounded cleanup
- replay가 retention을 연장하지 않음
- 미완료 reservation 보호
- cleanup / replay concurrency 검증

**Evidence:** [PR #108](https://github.com/guseoh/pawcycle-commerce/pull/108)

### MySQL Lock 검증

Migration과 동시성 작업에서는 `FOR UPDATE`의 효과를 코드만 보고 추측하지 않고 격리된 MySQL 환경에서 실제 Lock footprint를 확인한 작업도 진행했습니다.

**Evidence:** [PR #104](https://github.com/guseoh/pawcycle-commerce/pull/104)

---

## 🔭 Production Operations

PawCycle은 저장소 안에서만 끝나는 프로젝트가 아니라 실제 운영 절차와 복구 경계도 함께 다뤘습니다.

AWS 환경에서 다음 운영 경험과 Evidence를 확보했습니다.

- Docker Compose 기반 단일 Release 운영
- Nginx / HTTPS
- commit SHA + image digest 기반 Release 식별
- Application deploy / rollback
- Logical Backup / Isolated Restore
- Session / Auth Production Smoke
- Prometheus / Grafana Observability
- Alertmanager 기반 장애 알림
- Incident 재현과 Recovery Runbook

Production 운영과 저장소 준비는 항상 분리합니다. CI가 통과하거나 Runbook이 존재한다는 이유만으로 실제 운영 검증을 완료했다고 표현하지 않습니다.

현재 장기 운영 비용을 낮추기 위한 OCI 전환 후보를 별도 저장소 작업에서 검증하고 있으며, 실제 OCI 계정·리소스·DB·Object Storage·Secret 실행은 Repository Readiness와 구분합니다.

**관련 문서 / Evidence**

- [Production Operations Overview](docs/architecture/production-operations-overview.md)
- [PR #112 - Backend Observability](https://github.com/guseoh/pawcycle-commerce/pull/112)
- [PR #113 - Prometheus / Grafana](https://github.com/guseoh/pawcycle-commerce/pull/113)
- [PR #116 - Incident Reproduction / Recovery](https://github.com/guseoh/pawcycle-commerce/pull/116)
- [PR #118 - Prometheus Alert](https://github.com/guseoh/pawcycle-commerce/pull/118)
- [PR #120 - Discord Alert](https://github.com/guseoh/pawcycle-commerce/pull/120)
- [Draft PR #277 - OCI Repository Migration Readiness](https://github.com/guseoh/pawcycle-commerce/pull/277)

---

## 🤖 AI Harness Engineering

PawCycle에서는 AI에게 저장소 전체를 자유롭게 맡기지 않습니다.

AI를 많이 사용하는 것이 목표가 아니라, **AI가 구현을 돕더라도 사용자가 목표·범위·위험·검증을 계속 통제할 수 있는 개발 방식**을 만드는 것이 목적입니다.

현재 Harness의 핵심 원칙은 다음과 같습니다.

- 제품·도메인·보안·비용·운영 결정은 사용자가 최종 승인
- 저장소 작업은 `경량 / 일반 / 고위험`으로 분류
- `저장소 변경`과 `실제 운영 실행`을 별도 승인 경계로 분리
- Codex 실행 전 현재 작업의 Delta만 추출
- 프로젝트 전체 설명을 반복하지 않고 Final Lightweight Delta Prompt 사용
- 변경 영향에 맞는 최소 테스트와 CI 실행
- AI Review와 Human Review를 보조 증거로 사용
- 실패·미실행·남은 위험을 PR에 남김
- 실제 운영 증거가 없으면 `Production Verified`라고 표현하지 않음
- 반복적으로 확인된 Harness 결함만 공통 규칙으로 승격

세부 절차를 README에 복제하지 않고 권위 원본에서 관리합니다.

- [Risk-based Lean Harness](docs/runbook/lean-harness.md)
- [Repository Agent Rules](AGENTS.md)

Harness 자체도 완성된 것으로 가정하지 않습니다. 실제 작업에서 불필요한 절차, 중복 규칙, 검증 누락 또는 AI 작업 통제 문제가 확인되면 Evidence를 기준으로 다시 줄이거나 보강합니다.

---

## 🧪 Validation

Repository 변경은 영향 영역에 따라 필요한 검증만 실행합니다.

현재 주요 validation 경계:

- Backend: Gradle test / build + MySQL integration
- Frontend: lint / typecheck / test / production build
- Harness: task artifact / convention / classifier regression
- Production repository contract: Compose / release / recovery / auth lifecycle static validation
- Browser QA: 실제 사용자 흐름이 필요한 경우 독립 검증

CI Green만으로 완료를 선언하지 않고 작업 성격에 따라 다음 수준을 구분합니다.

| 상태 | 의미 |
| --- | --- |
| Implemented | 저장소 변경 완료 |
| Verified | 정의한 테스트·CI·통합 검증 완료 |
| Production Verified | 실제 운영 적용 전후와 복구 증거까지 확보 |

---

## 🛠 Tech Stack

### Backend

- Java 25
- Spring Boot 4.1
- Spring MVC
- Spring Security
- Spring Data JPA
- Spring Validation
- Spring AI
- Micrometer
- Gradle

### Frontend

- Next.js 16
- React 19
- TypeScript 6
- Toss Payments SDK

### Data / Infrastructure

- MySQL
- Flyway
- Redis
- Docker / Docker Compose
- Nginx
- GHCR
- GitHub Actions

### Observability / Collaboration

- Prometheus
- Grafana
- Alertmanager
- CodeRabbit
- ChatGPT
- Codex

---

## 🔎 Selected Evidence

README에는 전체 PR을 나열하지 않고 현재 PawCycle의 성격을 보여주는 작업만 선별합니다.

| Topic | Engineering Point | Evidence |
| --- | --- | --- |
| Product Completion | Customer/Admin Commerce flow 연결 | [PR #267](https://github.com/guseoh/pawcycle-commerce/pull/267) |
| Customer UX | 실제 Commerce 기준 Visual Closure | [PR #269](https://github.com/guseoh/pawcycle-commerce/pull/269) |
| Concurrency | 실제 MySQL Lock footprint 검증 | [PR #104](https://github.com/guseoh/pawcycle-commerce/pull/104) |
| Reliability | Subscription 실패 격리 / Reconciliation | [PR #106](https://github.com/guseoh/pawcycle-commerce/pull/106) |
| Idempotency | Retention / Cleanup / Concurrency | [PR #108](https://github.com/guseoh/pawcycle-commerce/pull/108) |
| Operations | Observability / Incident / Alert | [#112](https://github.com/guseoh/pawcycle-commerce/pull/112), [#116](https://github.com/guseoh/pawcycle-commerce/pull/116), [#120](https://github.com/guseoh/pawcycle-commerce/pull/120) |
| Backend Refactoring | 동작 보존 JPA persistence 수렴 | [PR #279](https://github.com/guseoh/pawcycle-commerce/pull/279) |
| HTTP Contract | Validation / REST / Frontend client 수렴 | [PR #280](https://github.com/guseoh/pawcycle-commerce/pull/280) |

---

## ➕ 앞으로 더 구현할 내용

현재 제품 이후 더 검증하거나 구현하려는 주제만 간단히 기록합니다.

- 장기 운영 환경 전환과 OCI 실제 배포 검증
- 현재 Product Completion 구조 기준 Performance Re-baseline
- Scale Dataset 기반 병목 재현과 개선
- SQL / Index / JPA / Transaction / Connection Pool / JVM 최적화
- Redis / Async / Queue / Kafka의 유지·도입 기준 검증
- Multi-instance / Load Balancer 등 Scale-out 필요성 검증
- Payment / Reconciliation / Locking의 남은 correctness·recovery 시나리오
- 장애 주입과 Failure / Recovery Evidence 확장
- 성능·아키텍처·운영 선택의 Trade-off 문서화
- AI Harness의 절차·Prompt·검증 구조 재평가와 경량화

---

## 🎯 What I Want to Prove

PawCycle Commerce에서 보여주고 싶은 것은 특정 기술을 많이 사용했다는 사실이 아닙니다.

**실제 Commerce / Subscription 제품을 만들고, 운영하고, 실패와 병목을 측정하며, 필요한 경우에만 코드·데이터·아키텍처를 바꿀 수 있는가**가 핵심입니다.

그리고 AI가 구현을 돕는 환경에서도:

- 제품 목표와 범위를 사람이 통제하고
- 위험한 결정을 자동화하지 않으며
- 테스트·Review·운영 Evidence로 결과를 검증하고
- 실제 문제를 근거로 개발 방식 자체도 개선하는 것

까지 프로젝트의 일부로 다룹니다.
