# MVP4-FE-003 FE → QA

## 확인 범위

- 고객 경로: `/`, `/products`, `/compare`, `/products/{productId}`, `/pets`, `/my`, `/orders/{orderId}`, `/subscriptions/{subscriptionId}`, `/subscriptions/new`, `/notifications`
- 관리자 경로: `/admin/catalog`의 Category Facet, SKU 옵션 배정, Product Facet 값 편집 영역
- 독립 Browser QA는 아래 fixture를 준비한 뒤 실행한다. FE는 로컬 Next 서버에서 route/인증 fallback과 desktop, 375px mobile shell만 smoke했다.

## Fixture와 사전 조건

- 공개 상품 2~3개: 서로 다른 가격·브랜드·Facet·구매/정기배송 상태를 포함하고 비교 API가 canonical facts를 반환하도록 준비한다.
- 추천 응답: `requestId`, `strategy`가 있는 personalized/popular/trending/related/complementary 응답과 empty/error 응답.
- 인증 회원: DOG/CAT Pet, breed·weight 입력/clear, 최근 `ONE_TIME` 구매 3회 이상, 주문 subscription options, ACTIVE subscription.
- 구독 fixture: 다음 배송 `SCHEDULED`와 Add-on 포함 `nextDelivery`, Add-on stock `HELD`(`STOCK_UNAVAILABLE`) 및 `REMOVE_NEXT_DELIVERY_ADDON`만 허용되는 응답.
- 관리자: 현재 SKU option value, Product facet option, Category facet/displayOrder가 존재하는 계정과 403/GET readback failure 응답.

## 시나리오

- 익명: 홈 public 추천, catalog search/filter/sort, 비교 tray와 `/compare`, 상품 상세 related/complementary를 확인한다. 비교는 2~3개만 허용하며 잘못된/중복/0 ID는 API를 호출하지 않는다.
- 인증: 추천 Pet을 직접 선택했을 때 requestId와 petId가 impression/click에 유지되는지, `/pets`의 create/read/partial edit와 breed·weight blank→`null`, petType 불변·삭제 없음, `/my` reorder timing을 확인한다.
- 주문/구독: 주문 subscription option에서 호환 Pet·Plan·cycle만 `/subscriptions/new`에 additive prefill되고 서버 검증을 통과해야 한다. cycle suggestion은 폼 값만 채우며 자동 command를 보내지 않는다.
- Add-on: `SCHEDULED`에서 검색→상품 detail→SKU→1~10개 SET, 기존 Add-on REMOVE, known conflict/limit/not-available 오류 copy, ETag/If-Match/Idempotency/CSRF를 확인한다. Add-on은 current/pending Snapshot과 분리되어야 한다.
- 알림: `SUBSCRIPTION_DELIVERY_REMINDER`의 `referenceType=SCHEDULE`가 projection의 `subscriptionId`로 구독 상세에 연결되고 `scheduledDate`를 표시하는지 확인한다.
- 관리자: 세 편집기가 GET readback을 먼저 표시하고, readback 실패 시 빈 배정으로 오인하지 않으며 save/replace를 비활성화하는지 확인한다. Category는 기존 순서 변경·해제·신규 추가를 확인한다.

## Fallback 기대값

- 추천/related/complementary/reorder/options/summary 실패는 해당 보조 section만 error·retry로 남고 핵심 상품/주문 화면을 비우지 않는다.
- AI disabled/unavailable이면 canonical 비교 facts와 raw reviews는 계속 보이고 AI summary만 neutral fallback을 표시한다. 리뷰 3개 미만도 요약 없이 raw reviews를 보여준다.
- Add-on 검색/detail 실패는 입력을 유지하고 재시도 가능해야 한다. `HELD` stock unavailable에서는 정확한 재고 보류 안내와 서버가 허용한 Add-on 제거/취소만 표시하며 수동 retry 버튼을 만들지 않는다.
- 인증/소유권 오류는 로그인 또는 중립적인 not-found/error 상태로 표시하고 내부 member ID, raw query, 내부 hold reason을 노출하지 않는다.
