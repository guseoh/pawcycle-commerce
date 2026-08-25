# MVP4-UX-002 Warm Utility Commerce

- 작업 ID: `MVP4-UX-002`
- 상태: 승인된 Frontend 디자인 delta
- 기준: 최신 `origin/main`

## 방향

PawCycle의 cream + green identity를 유지하면서 상품 탐색, 주문, 정기배송 관리의 정보 밀도를 높인다. 화면은 큰 hero나 장식용 카드보다 명확한 제목, 짧은 설명, 서버가 제공한 가격·상태·action을 우선한다.

- Desktop content max-width: 1200~1280px
- 일반 page title: 32~40px, Home display: 40~48px
- 기본 surface는 약 10~12px radius, shadow 없음
- button/input은 약 8px radius
- pill은 status, tag, chip에만 사용
- motion은 최소화하고 `prefers-reduced-motion`을 유지한다

## 화면 원칙

- Header는 64~68px 수준으로 compact하게 유지하고 상품·정기배송·주문을 primary로, 찜·장바구니·알림·내 정보를 utility로 둔다. Logout은 My에 둔다.
- Home은 축소한 hero 다음에 상품 탐색과 정기배송 CTA를 배치한다. 로그인 회원에게는 Pet 기반 추천을 핵심 영역으로 보여주되 AI/provider 구현 정보는 표시하지 않는다.
- Products는 Desktop 4열, Tablet 3열, Mobile 2열을 기본으로 한다. 카드 정보 순서는 이미지, 상품명, 서버 첫 가격, pet/category, 구독 여부, action이다. 내부 ID와 SKU code는 노출하지 않는다.
- Detail은 gallery와 구매 영역을 유지하되 중첩 카드를 줄인다. Cart는 explicit `적용` 후에만 수량을 반영하고 서버가 제공하지 않는 subtotal·할인·배송비를 계산하지 않는다.
- Checkout은 배송지, 주문 상품, `주문 생성` CTA를 중심으로 한다. 결제 전 주문 생성 상태를 유지하며 Toss confirm/Billing은 구현하지 않는다.
- Subscription은 compact management list와 상태·다음 배송·pendingChange·issue·server `availableActions`를 분리해 표시한다. 없는 retry/action을 추가하지 않는다.
- My는 쇼핑·정기배송·기타·계정 settings list로 구성하고 Logout을 이곳에 둔다.
- Orders, Notifications, Addresses, Wishlist, Billing은 기존 API와 stale-response guard를 보존하면서 compact list/form으로 표현한다.

## Responsive / accessibility

- 320px에서도 header, heading, input, CTA가 viewport 안에 놓이고 텍스트는 wrap된다. `overflow-x: hidden`으로 문제를 숨기지 않는다.
- 320px 상품 grid는 카드당 약 140px 이상을 확보할 수 있을 때 2열을 유지한다. 확보할 수 없는 경우 안전한 재배치로 clipping을 피한다.
- 좁은 Desktop/Tablet에서는 주요 2열 영역을 1열로 바꾸고, form과 error/retry action은 읽기 순서를 보존한다.
- semantic navigation, visible focus, label, error announcement, keyboard action을 유지한다.
- reduced-motion에서는 transition과 animation을 최소화한다.

## 금지 패턴

- 과도하게 큰 heading/hero, 전 영역의 큰 rounded card, 일반 navigation/button pill, 과도한 whitespace/shadow/gradient를 사용하지 않는다.
- Error/Empty/Loading을 큰 장식 panel로 만들지 않는다.
- client가 가격·할인·재고·구독 eligibility·payment retry/cancel 정책을 계산하지 않는다.
- 새 UI/state/animation/icon dependency, API 계약, Backend/infra를 추가하거나 변경하지 않는다.
