# ARCH-015 AI 상품 추천 경계

- 작업 ID: `MVP4-REC-001`
- 상태: Accepted
- 등급: 고위험
- 실행 구분: 저장소 변경

## 결정

개인화 추천의 후보 선정·소유권·구매 가능 여부·의료 관련 제외·응답 ID 검증은 서버가 담당한다. AI는 서버가 정렬한 최대 10개 결과 후보의 순서와 짧은 한국어 이유만 제안한다. `memberId`, 회원·Pet 이름, 연락처·주소, 결제정보와 주문번호는 AI 입력에 포함하지 않는다.

추천 조회는 DB 후보와 성향을 읽은 뒤 외부 AI를 호출하는 동안 DB transaction이나 connection을 계속 점유하지 않는다. AI 응답을 받은 뒤에는 `PUBLIC` Product, 재고가 남은 `ACTIVE` SKU 조건을 다시 조회해 최신 구매 가능 상태를 재검증하고, 더 이상 유효하지 않은 AI 결과는 버린 뒤 일반 추천으로 채운다.

기본값은 `pawcycle.recommendation.ai.enabled=false`, `spring.ai.model.chat=none`이다. 실제 AI를 활성화할 때만 `PAWCYCLE_RECOMMENDATION_AI_ENABLED=true`, `SPRING_AI_MODEL_CHAT=openai`, `SPRING_AI_OPENAI_API_KEY`를 외부 환경에 설정한다. 모델·Secret은 저장소에 고정하지 않는다. ChatClient 구조화 응답을 한 번만 호출하고 `spring.ai.retry.max-attempts=1`로 둔다.

AI 응답은 후보 productId 집합에 다시 대조하고, 중복·알 수 없는 ID·의료 용어가 포함된 이유·한국어가 아닌 이유를 제거한다. 이유까지 포함해 하나라도 안전 fallback을 사용하면 fallback metric으로 기록한다. 부족하거나 장애가 나면 정기배송 카테고리, 결제 완료 구매 카테고리, Wishlist 카테고리, 기본 상품 순서의 결정적 추천으로 채운다. 추천 전용 Redis, Queue, RAG, Embedding과 비동기 처리는 도입하지 않는다.

## 결과

- 상품 후보는 요청 Pet과 같은 타입, `PUBLIC` Product, 재고가 남은 `ACTIVE` SKU 조건을 모두 만족해야 한다.
- AI에 전달하는 후보는 최대 10개이며, 일반 fallback 계산에는 최신 전체 구매 가능 후보를 사용할 수 있다.
- Category가 아직 지정되지 않은 기존 PUBLIC 상품도 Product 조회 계약을 깨지 않는다. 추천에서는 Category가 없는 상품을 기본 순위 후보로 취급한다.
- AI 성공·fallback counter와 실제 AI 호출 timer를 Micrometer에 기록한다. Prompt와 성향 원자료는 로그에 남기지 않는다.
- API 호출은 읽기 전용이며, 추천 결과의 cache·저장·상품 상태 변경을 만들지 않는다.

## 복구

저장소 변경은 일반 revert PR로 되돌린다. Production AI 활성화, API Key 설정, 모델 비용 발생과 배포는 이 결정에 포함하지 않는다.
