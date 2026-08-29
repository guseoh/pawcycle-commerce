# MVP4-UX-004 구현 계약 정합성 교정

상태: `Proposed Design Contract / Draft / Pending Product Owner Approval`

## 목적과 권위

이 문서는 `ee45d8a`까지 작성된 MVP4-UX-004 설계를 최신 `main`의 실제 API·상태 계약과 다시 대조한 최종 delta correction이다. 기존 A–D, 재설계 제안안, Visual + Interaction Correction의 유효한 visual·interaction 규칙은 유지한다.

**아래 항목과 기존 문서가 충돌하면 이 문서가 우선한다.** 이 correction은 제품/API/DB 기능을 추가하지 않으며, 현재 `main`이 실제 제공하는 기능을 UI/UX 계약에 정확히 맞추기 위한 것이다.

보존하는 핵심 계약:

- URL 기반 PLP 상태와 stale guard
- category/filter expand·collapse와 overlay focus 계약
- PLP↔PDP scroll/focus 복원
- cart version conflict와 checkout idempotency
- payment `verifying / confirmed / unknown / failed`
- subscription ETag/If-Match, 날짜/주기 command 분리
- 독립 async section, reduced motion, 320px/200% 확대
- 카드 남용 제거와 continuous PDP

## 1. New Subscription 생성 흐름 정정

기존 C3의 `상품·수량 → 첫/다음 배송일 → 배송지 → 결제수단 → 생성`은 현재 V2 create API보다 넓으므로 폐기한다.

### 실제 구현 계약

현재 생성 command의 입력은 다음 세 값이다.

- `petId`
- `planVersionId`
- `deliveryCycleWeeks`

생성 화면의 최종 흐름은 다음으로 고정한다.

```text
진입 context 확인
→ 반려동물 선택/필요 시 등록
→ 선택한 반려동물과 호환되는 판매 중 플랜 조회
→ 플랜 선택 및 상세 재검증
→ 서버가 허용한 배송 주기 선택
→ 생성 summary 확인
→ idempotent create
→ 서버가 반환한 subscription detail로 이동
```

- 주문 상세에서 들어온 `petId / planVersionId / deliveryCycleWeeks` context는 **prefill 후보**일 뿐이며 현재 판매·호환 상태를 다시 검증한다.
- `productId / skuId` context가 있어도 create API의 직접 입력으로 사용하지 않는다. 호환 플랜 탐색을 돕는 설명 context로만 사용한다.
- 생성 전 사용자가 `첫 배송일`을 직접 선택하지 않는다. 다음 배송일은 생성 후 서버가 확정한 값만 표시한다.
- 생성 flow 안에서 배송지나 결제수단을 선택·변경하지 않는다. 해당 값은 create command의 입력이 아니다.
- 플랜 구성 수량을 create 화면에서 임의 편집하지 않는다. 플랜의 `items`와 가격은 서버 판매 계약을 읽기 전용으로 표시한다.
- 추천 주기는 자동 적용하지 않는다. prefill이 있더라도 사용자가 radio 선택 상태를 확인할 수 있어야 한다.

### 생성 화면 composition

`반려동물 → 호환 플랜 → 배송 주기 → 최종 확인/생성`의 progressive section을 사용한다. 완료 section은 compact read-only summary로 접고 현재 section만 편집한다. mobile에서도 동일한 의미 순서를 유지한다.

## 2. Billing Methods 범위 정정

기존 C8의 `결제수단 목록 / 기본 결제수단 / 기타 수단 / 삭제` 계약은 현재 API에 없으므로 폐기한다.

### 실제 구현 계약

Billing 화면은 현재 다음 두 기능만 표현한다.

1. Toss billing provider의 `configured / registered` 상태 확인
2. provider가 configured인 경우 등록 준비 command 시작

따라서 UI는 다음 상태만 가진다.

- loading
- configured + registered
- configured + not registered
- not configured
- prepare pending
- prepare success
- prepare error / unknown

현재 provider client가 최종 등록 완료 단계를 지원하지 않는 환경에서는 `등록 준비가 완료됨`과 `등록 완료`를 구분한다. 준비 성공만으로 결제수단 등록 성공을 표현하지 않는다.

금지:

- 여러 결제수단 목록
- 기본값 변경
- 삭제/해제
- 카드 브랜드·끝자리 등 현재 API가 반환하지 않는 정보 추정
- checkout에서 저장된 billing method selector를 요구
- `준비 중` 상태를 실제 결제 가능 상태로 오표현

## 3. Checkout → Toss 결제 단계 정정

기존 B2와 Visual Correction의 일부 문장은 Toss widget을 checkout create 이전의 `결제수단 section`처럼 읽힐 수 있으므로 다음 흐름으로 덮어쓴다.

### Phase A — 주문/결제 context 생성

```text
Cart server state
→ 배송지 선택
→ 선택적 coupon
→ 현재 cartVersion과 최종 금액 확인
→ checkout command
→ Order + Payment context 생성
→ CheckoutResult 수신
```

- checkout command는 `addressId`, optional `memberCouponId`, `cartVersion`, `Idempotency-Key`를 사용한다.
- cart가 비었거나 구매 불가 item이 있거나 배송지가 없으면 Phase A를 진행하지 않는다.
- `CART_CHANGED`이면 checkout 진행을 중단하고 최신 cart를 다시 읽은 뒤 사용자가 다시 확인한다.
- unknown/network 실패에서는 같은 request identity를 재확인할 수 있도록 같은 idempotency intention을 유지한다.

### Phase B — Toss 결제

`CheckoutResult`가 생성된 뒤에만 Toss payment widget을 렌더한다.

```text
CheckoutResult
→ Toss widget loading
→ payment methods/agreement ready
→ 사용자 `결제하기`
→ provider redirect
→ /checkout/success 또는 /checkout/fail
→ 서버 confirm 결과 확인
```

- Phase A의 버튼 문구는 `주문 및 결제 준비` 계열로 사용하고 실제 Toss 승인 CTA인 `결제하기`와 구분한다.
- 저장된 billing method가 없다는 이유로 checkout을 차단하지 않는다.
- Toss widget이 unavailable이면 주문/결제 context가 생성된 사실과 실제 결제가 완료되지 않았다는 사실을 분리해 설명한다.
- success URL은 redirect 자체로 성공을 선언하지 않고 서버 confirmation 후에만 완료로 표시한다.
- fail/unknown에서 기존 provider approval을 자동 재전송하지 않는다.

### Checkout composition

Desktop:

`주문 상품 요약 → 배송지 → 쿠폰 → 서버 최종 금액/주문 준비 → Toss payment panel`

Mobile:

동일 순서의 단일 열. Phase A와 Phase B 각각 active CTA는 하나만 둔다. keyboard가 열린 동안 fixed CTA가 field/error를 가리지 않는다.

## 4. PDP Option → SKU 결정 계약 보강

기존 A5의 `옵션 API가 보장되지 않는다`는 전제를 폐기한다. 현재 상품 상세에는 `optionGroups[]`, 각 SKU의 `selectedOptions[]`, 가격·할인·재고·구매가능 여부가 존재한다.

### 선택 알고리즘

1. `optionGroups`가 없고 SKU가 하나면 해당 SKU를 즉시 선택한다.
2. `optionGroups`가 있으면 group의 `displayOrder` 순서대로 값을 선택한다.
3. 각 선택 조합은 `skus[].selectedOptions`와 대조한다.
4. 현재까지의 선택과 일치하는 SKU가 하나도 없는 option value는 disabled 처리하고 `현재 조합에서는 선택할 수 없음`을 접근 가능한 이름/설명으로 제공한다.
5. 모든 필수 group을 선택해 정확히 하나의 SKU가 결정되기 전에는 quantity와 Cart CTA를 활성화하지 않는다.
6. SKU가 결정되면 해당 SKU의 `price`, `compareAtPrice`, `discountRate`, `availableQuantity`, `purchasable`을 즉시 purchase summary의 권위 값으로 사용한다.
7. quantity 최대값은 선택된 SKU의 `availableQuantity`다. 재고가 0이거나 `purchasable=false`면 Cart CTA를 disabled하고 이유를 표시한다.
8. 선택 변경으로 SKU가 바뀌면 quantity가 새 최대값을 넘는 경우 안전하게 최대값으로 낮추고 변경 사실을 알린다.

### UI 패턴

- option group이 하나이고 선택지가 단순하면 native select를 사용할 수 있다.
- 여러 option group은 각 group별 visible label + select/disclosure 한 패턴을 사용하되, **한 개의 통합 SKU select로 option 의미를 숨기지 않는다.**
- 품절 조합을 제거해서 존재하지 않는 것처럼 보이게 하지 않고 disabled+이유를 제공한다.
- SKU 결정 전 대표가격을 확정 구매가격처럼 표시하지 않는다. `최저가` 의미가 실제 contract에서 보장되지 않으면 대표가격이라는 설명을 사용한다.

### Gallery 확정

- main image click/Enter는 image가 존재할 때 명시적 lightbox dialog를 연다.
- hover-only zoom은 사용하지 않는다.
- lightbox는 이전/다음, Escape, focus trap, trigger 복귀, reduced-motion 계약을 따른다.

## 5. PDP Review / Q&A 작성 기능 복원

기존 A5의 `제출 기능이 범위에 없다면 CTA 미노출` 문장은 현재 API와 맞지 않으므로 폐기한다.

### Reviews

현재 제공 기능:

- 목록 조회
- 내 리뷰 조회
- 리뷰 생성
- 내 리뷰 수정
- 내 리뷰 삭제

PDP Review section은 다음을 지원한다.

- anonymous: 리뷰 목록은 읽을 수 있으나 작성/수정 action은 로그인 안내 + safe GET `returnTo`를 사용한다.
- authenticated + 내 리뷰 없음: `리뷰 작성` action.
- authenticated + 내 리뷰 있음: 내 리뷰를 구분해 `수정`, `삭제` action 제공.
- create/update는 rating + content form, pending 중 중복 submit 방지, field/server error 시 입력 유지.
- delete는 대상과 결과를 설명하는 confirm dialog 후 서버 성공 시에만 제거한다.
- 서버가 구매 자격 등 business rule로 거절하면 해당 error를 고객 문장으로 보여주며 UI가 자격을 임의 추정하지 않는다.
- 0건은 평균 `null`과 count `0`을 그대로 사용해 `0점`으로 표시하지 않는다.

### Q&A

현재 제공 기능:

- 목록 조회
- 질문 생성

따라서:

- authenticated 사용자에게 `상품 문의하기`를 제공한다.
- anonymous 사용자는 로그인 후 현재 PDP GET으로 복귀한다.
- 작성 form은 content만 요구하고 pending/error 시 입력을 유지한다.
- 질문 수정/삭제/답변 작성 UI는 현재 계약에 없으므로 추가하지 않는다.
- 답변이 있는 행은 질문→답변 disclosure를 사용할 수 있으며 0건 empty가 지나치게 큰 card가 되지 않게 한다.

Review와 Q&A의 loading/error는 서로 독립이며 한 section 실패가 PDP 구매 영역을 막지 않는다.

## 6. Order List 범위 정정

기존 B4의 기간/상태 filter와 pagination 설계는 현재 `GET /api/orders` 계약에 파라미터와 page response가 없으므로 제거한다.

### 실제 구현 계약

```text
heading + 총/요약 정보
→ 서버가 반환한 주문 목록
→ 각 행: 날짜/주문번호/상태/금액/상세 CTA
```

- 서버가 반환한 순서를 UI가 임의로 재정렬하지 않는다.
- 기간 filter, status filter, pagination, infinite scroll을 만들지 않는다.
- 목록이 커져 실제 사용성/성능 문제가 측정되면 별도 Product/API 결정으로 확장한다.
- detail → back에서 목록 scroll/focus는 복원한다.
- 주문 0건, loading, 최초 실패, 재인증 상태는 계속 구분한다.

## 7. 잔여 문서 정합성 correction

### Address editor

- desktop 주소 create/edit dialog는 **560px modal**로 고정한다.
- 767px 이하는 full-screen dialog를 사용한다.
- inline address edit는 사용하지 않는다.

### Pets

현재 Pet API에 delete가 없으므로 `DeleteConfirm`과 pet 삭제 action을 설계에서 제거한다. create와 name/breed/weightKg patch만 제공하고 `petType`은 생성 후 read-only다.

### Notifications

`readAll` API가 존재하므로 `모두 읽음`은 조건부 후보가 아니라 정식 interaction이다.

- unreadCount가 0이면 disabled + 이유를 제공한다.
- 실행 중 중복 action을 막는다.
- 서버 성공 후 목록과 count를 다시 확인한다.
- 실패하면 기존 unread 상태를 유지한다.

### PLP 열 수

A3의 과거 `5/4/3/2/1` 문구는 폐기한다. D8만 SSOT다.

- 1440+ : 4열
- 1200–1439 : 3열
- 1024–1199 : 3열
- 768–1023 : 2열
- 600–767 : 2열
- 360–599 : 2열
- 320–359 : 1열 행형 card

### Home motion

Home hero는 **정적 1개, 자동재생 없음**으로 확정한다. `자동재생하지 않거나 pause 제공`이라는 선택지는 폐기한다. 상품 rail도 자동재생하지 않는다.

### text-muted 대비

기존 `text-muted #5F6F66`은 `canvas`에서는 통과하지만 `surface-soft #EFE9DA`, `selected-soft #DCEFE6` 위의 일반 텍스트에서 4.5:1에 미달한다.

`text-muted`를 **`#5C6A62`**로 변경한다.

검증 대비:

- `text-muted/canvas`: 약 5.17:1
- `text-muted/surface-soft`: 약 4.69:1
- `text-muted/selected-soft`: 약 4.75:1
- `text-muted/white`: 약 5.69:1

일반 본문 4.5:1을 하한으로 유지한다.

## 8. 구현 전 최종 계약 읽기 순서

Frontend 구현 작업에서는 다음 순서로 읽는다.

1. 현재 사용자 승인
2. 이 `MVP4-UX-004-contract-alignment-correction.md`
3. `MVP4-UX-004-visual-interaction-correction.md`
4. A–D 화면/공통 계약
5. `MVP4-UX-004-customer-commerce-redesign.md`
6. benchmark evidence
7. 기존 UX-003/FE-004는 역사·감사 입력으로만 사용

이 correction에 의해 덮어써진 과거 문장을 구현 근거로 사용하지 않는다.

## 9. 아직 Product Owner 결정인 항목

기존 6개 PENDING PO는 유지한다. 이번 correction은 이를 임의 승인하지 않는다.

- 모바일 하단 내비게이션
- Home 대표 사회적 증거
- 최종 실제 결제 CTA 용어
- anonymous wishlist 처리
- 신규 Subscription 주 진입점
- Order/Subscription contextual support 링크

다만 Checkout에서는 실제 단계 구분상 **Phase A의 주문/결제 준비 CTA와 Phase B의 Toss `결제하기`를 서로 다른 동작으로 표현해야 한다.** PO의 CTA 용어 결정은 이 구조를 깨지 않는 범위에서 확정한다.

## 10. 구현 인수 조건 추가

기존 인수 조건에 다음을 추가한다.

- Subscription create가 현재 V2 create request를 넘어서는 배송지/결제/날짜 입력을 요구하지 않는다.
- Billing UI가 `configured/registered/prepare` 밖의 결제수단 CRUD를 만들지 않는다.
- Checkout이 Order/Payment context 생성과 Toss 결제를 한 submit으로 오인하지 않는다.
- PDP option 선택이 실제 SKU 조합, 가격, 재고, 구매 가능 상태를 정확히 따른다.
- Review create/update/delete와 Question create가 redesign에서 유실되지 않는다.
- Order List에 지원되지 않는 filter/pagination이 생기지 않는다.
- Pet 삭제 UI가 없다.
- Notification `모두 읽음`이 실제 server action으로 동작한다.
- text-muted가 허용 surface에서 WCAG AA 일반 텍스트 대비를 충족한다.

`NO FRONTEND IMPLEMENTATION PERFORMED`
