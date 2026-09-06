# Backend 경로 규칙

이 파일은 `backend/**`를 수정할 때의 **코드 경계와 불변식**만 정의한다. 공통 승인·Git·PR·산출물·병합 규칙은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따른다.

## Backend 책임

- 도메인 규칙과 application use case
- HTTP API와 validation/error contract
- transaction·persistence·동시성·멱등성
- 인증·인가와 서버 권위의 보안 규칙
- Backend unit/integration/regression test

## 코드 경계

- Controller는 HTTP mapping, validation, 인증 context 전달에 집중한다.
- 정적 API 응답은 의미 있는 타입을 사용하며 JPA Entity나 raw `Map<String, Object>`를 직접 노출하지 않는다.
- Application Service는 use case와 transaction을 조율하고 SQL·JSON·HTTP mapping을 동시에 소유하지 않는다.
- Domain은 승인된 invariant와 상태 전이를 보호한다.
- Persistence 세부사항은 Repository·query/persistence adapter 내부에 둔다.
- JPA를 관계형 persistence의 기본 경계로 사용하되, locking·CAS·복잡한 projection처럼 의미가 더 명확한 SQL은 제한적으로 허용한다.

## 제품·데이터 안전

- 가격, 할인, 재고, 결제, 구독 정책을 임의로 만들거나 바꾸지 않는다.
- transaction·lock·idempotency 의미가 바뀌면 동시 요청과 실패 경계를 함께 검증한다.
- 주문·결제·구독 history가 현재 mutable entity 값에 의해 소급 변경되지 않도록 snapshot 의미를 보존한다.
- API나 schema가 바뀌면 가장 가까운 canonical 문서와 consumer 영향도 함께 갱신한다.

## 로깅과 민감정보

- SLF4J parameterized logging을 사용하고 같은 exception을 여러 경계에서 중복 기록하지 않는다.
- Secret, credential, token, session·CSRF 값, payment/billing key, raw request body와 불필요한 개인정보를 로그에 남기지 않는다.

## 성능

측정 없이 Redis, cache, async, queue, index, retry, timeout 증가나 query rewrite를 성능 개선으로 추가하지 않는다. 문제 조건과 baseline을 먼저 확보한다.

## 검증

위험에 맞는 가장 작은 테스트부터 실행한다.

- 도메인·application 규칙: unit test
- HTTP·security: contract/integration test
- persistence·transaction·lock: 실제 MySQL integration test
- 버그: 재현 가능한 regression test

승인된 cross-stack 작업에서 `frontend/**` 또는 `infra/**`도 함께 수정할 수 있지만, 그 경로의 `AGENTS.md`를 추가로 적용한다. Backend 역할이라는 이유만으로 하나의 승인된 사용자 목적을 인위적으로 분리하지 않는다.
