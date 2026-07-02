# 백엔드 에이전트 규칙

## 책임

백엔드(Backend) 영역은 Spring Boot 애플리케이션 동작, 도메인 규칙(Domain Rule), API 동작, 영속성(Persistence), 트랜잭션(Transaction), 보안(Security), 인증(Authentication), 인가(Authorization), 동시성(Concurrency), 백엔드 테스트, API 문서, DB 변경 문서, 백엔드 인수인계를 담당한다.

명시적으로 승인된 작업이 있기 전에는 백엔드 애플리케이션 코드를 생성하지 않는다.

## 공통 운영 기준

- 공통 Git, commit·push, 보고서, 인수인계 규칙은 루트 `AGENTS.md`를 따른다.
- 백엔드 역할 브랜치는 `feat/be`다.
- `feat/be`에는 하나의 활성 백엔드 작업만 둔다.
- PR 병합 후에는 `feat/be`를 삭제하고 다음 백엔드 작업에서 최신 `main` 기준으로 다시 만든다.

## 도메인과 비즈니스 규칙

- 승인된 도메인 규칙만 구현한다.
- 가격, 할인, 재고, 결제, 구독 정책을 임의로 만들지 않는다.
- 비즈니스 규칙(Business Rule)은 컨트롤러(Controller)나 프론트엔드가 아니라 백엔드 도메인 또는 애플리케이션 서비스(Application Service)에 둔다.
- 해결되지 않은 정책 질문은 Product Decision으로 기록한다.

## 계층별 책임

- 컨트롤러(Controller): HTTP 요청과 응답 매핑, 검증 진입점, 인증 컨텍스트(Authentication Context) 전달
- 서비스(Service) 또는 애플리케이션 계층(Application Layer): 유스케이스(Use Case) 조율, 트랜잭션 경계, 비즈니스 규칙 조합
- 도메인 모델(Domain Model): 승인된 도메인 상태와 불변식(Invariant) 보호
- 리포지토리(Repository): 영속성 접근

JPA 엔티티(Entity)를 API 응답으로 직접 노출하지 않는다. 승인된 API 계약에 맞는 요청 모델(Request Model)과 응답 모델(Response Model)을 사용한다.

## 트랜잭션, 스냅숏, 멱등성

- 트랜잭션 경계를 구현 문서나 인수인계에 명시한다.
- 주문 이력이 상품 정보 변경 이후에도 보존되어야 하면 주문 정보 스냅숏(Snapshot)을 고려한다.
- 정기 주문 생성, 결제 재시도, 건너뛰기, 일시정지, 재개, 해지는 멱등성(Idempotency)을 고려한다.
- 동시성 제어(Concurrency Control)를 선택하면 근거를 문서화한다.

## API 및 DB 변경

- API 계약이 변경되면 `docs/api/**`를 갱신한다.
- 장기 설계에 영향을 주는 기술 결정은 `docs/adr/**`에 ADR(Architecture Decision Record)로 기록한다.
- DB 변경과 마이그레이션(Migration) 기대 사항을 문서화한 뒤 구현에 의존한다.
- 프론트엔드, QA, SRE 후속 작업이 필요하면 `docs/handoffs/<작업 ID>/`에 인수인계를 작성한다.

## 테스트

백엔드 변경에는 위험도에 맞는 집중 테스트를 포함한다.

- 도메인 규칙과 서비스에 대한 단위 테스트(Unit Test)
- 영속성, 트랜잭션, 보안, API 동작에 대한 통합 테스트(Integration Test)
- 버그 수정에 대한 회귀 테스트(Regression Test)

백엔드 프로젝트가 존재하면 관련 Gradle 검사를 실행한다. 아직 백엔드 프로젝트가 없으면 검증이 문서 검토로 제한된다고 보고한다.

## 성능

측정 근거와 사용자 승인 없이 캐시(Cache), Redis, 비동기 메시징, 인덱스(Index), 재시도(Retry), 타임아웃(Timeout), 쿼리(Query) 재작성 같은 성능 작업을 추가하지 않는다.

## 허용 경로

- `backend/**`
- `docs/api/**`
- `docs/adr/**`
- `docs/handoffs/**`
- 승인된 범위의 `docs/domain/**`

## 금지 경로

- `frontend/**`
- `infra/**`
- 사용자가 승인하지 않은 제품 정책 변경
- 사용자가 승인하지 않은 프로덕션 의존성(Production Dependency)
- 작업 범위를 벗어난 광범위한 리팩터링
