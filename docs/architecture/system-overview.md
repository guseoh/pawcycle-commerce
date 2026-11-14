# 시스템 개요 (현재 상태)

PawCycle Commerce는 반려동물 용품을 일반 구매하거나 정기배송으로 구독하는 커머스다. 이 문서는 **지금 코드 기준의 구성 요소와 경계**를 한 장으로 보여 주고, 세부 내용은 각 현재 상태 문서로 연결한다.

- 확인 기준: `main` `70af0c3d`, 2026-11-14
- 기술 스택: Java 25, Spring Boot 4.1.0, Spring Security(세션·CSRF), JPA(validate) + Querydsl + 제한적 JDBC, Flyway, MySQL 8.4, Redis(상품 목록 캐시), Spring AI 2.0(OpenAI, 기본 꺼짐), Next.js 16 / React 19, Micrometer·Prometheus·Grafana

## 구성 요소

```text
[브라우저] ──HTTPS──> [Nginx proxy] ──> [Frontend: Next.js]  (화면, /api 호출 중계)
                              └────────> [Backend: Spring Boot] ──> [MySQL 8.4]
                                              ├──> [Redis] 상품 목록 캐시
                                              ├──> [Toss Payments] 결제 승인·조회·취소·정기결제
                                              ├──> [OpenAI via Spring AI] 추천·리뷰 요약·상품 비교 (플래그로 켬)
                                              └──> [Prometheus] 지표 수집 ──> [Grafana]
```

실제 운영 배치(OCI `app01`, Managed MySQL, 관측 스택 위치)와 그 검증 범위는 [운영 개요](production-operations-overview.md)와 ARCH-012가 원본이다.

## Backend 모듈

패키지는 `feature/{api, application, domain, persistence, infrastructure}` 구조를 따른다([Backend Architecture V2](backend-architecture-v2.md)).

| 모듈 | 책임 |
| --- | --- |
| `member` | 회원, 배송지, 세션 인증 Provider |
| `catalog` | 브랜드·카테고리·facet·상품·SKU·이미지·상세 섹션, 상품 탐색(목록·검색·비교), 리뷰·문의, 관리자 카탈로그, 대량 import(maintenance) |
| `commerce` | 장바구니, Checkout, 주문, 결제(Toss), 재고, 쿠폰, 배송, 취소·반품·환불, 정기결제 수단, 멤버십, 알림, 찜, 감사 로그, 운영 조회, 지표 |
| `subscription` | 펫·플랜·구독 생성과 명령, 배송 회차, 자동 주문, 배송 알림, 멱등성 |
| `recommendation` | 개인화 상품 추천(서버가 후보 결정, AI는 순서와 이유만) |
| `interaction` | 노출·조회·검색·추천 클릭 이벤트 수집 |
| `common`, `foundation` | 보안 설정, 공통 오류 응답, 로컬·QA 부트스트랩 |

## 핵심 일관성 경계

- **외부 호출은 DB 트랜잭션 밖에서 한다.** 결제·환불·정기결제·AI 호출은 "준비 트랜잭션 → 트랜잭션 밖 호출 → 확정 트랜잭션" 구조다. 상세는 [상태 전이](../domain/state-machines.md)에 있다.
- **재고는 SKU 행 잠금**으로, **구독 명령은 버전·멱등성·행 잠금**으로 보호한다.
- **멱등성:** Checkout, 빠른 재주문, 구독 생성·명령은 요청 키별 결과를 저장하고 같은 요청에는 같은 응답을 다시 돌려준다.
- **스케줄러 7개**가 결제 만료, 정기결제, 구독 주문 생성, 회차 보정, 배송 알림, 지표를 처리한다. 분산 잠금은 없다.

## 화면(Frontend 경로)

| 영역 | 경로 |
| --- | --- |
| 탐색 | `/`, `/products`, `/products/[productId]`, `/compare`, `/wishlist` |
| 구매 | `/cart`, `/checkout`, `/checkout/success`, `/checkout/fail`, `/orders`, `/orders/[orderId]`, `/returns` |
| 구독 | `/pets`, `/subscriptions`, `/subscriptions/new`, `/subscriptions/[subscriptionId]`, `/billing-methods` |
| 계정 | `/login`, `/my`, `/addresses`, `/notifications` |
| 안내 | `/faq`, `/notice`, `/shipping`, `/support` |
| 관리자 | `/admin/catalog`, `/admin/catalog/products/[productId]`, `/admin/commerce`, `/admin/engagement`, `/admin/operations` |

## 현재 상태 문서 지도

| 알고 싶은 것 | 문서 |
| --- | --- |
| 주문·결제·재고·구독의 상태와 전이 | [docs/domain/state-machines.md](../domain/state-machines.md) |
| 테이블 목록과 데이터 원칙 | [docs/data/current-schema.md](../data/current-schema.md) |
| 제공 기능과 제외 범위 | [docs/product/current-scope.md](../product/current-scope.md) |
| 알려진 고위험 결함·검증 공백 | [docs/architecture/known-risks.md](known-risks.md) |
| ADR별 실제 상태 | [docs/adr/README.md](../adr/README.md) |
| 패키지 구조와 영속성 경계 | [docs/architecture/backend-architecture-v2.md](backend-architecture-v2.md) |
| 운영 배치와 검증 한계 | [docs/architecture/production-operations-overview.md](production-operations-overview.md) |
