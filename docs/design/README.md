# 디자인 문서(Design Documents)

이 디렉터리는 UX/UI의 **제안안, Draft, Product Owner 승인 계약**을 함께 보관한다. 각 문서의 상태를 본문에 명시하며, Draft를 승인된 구현 권위로 해석하지 않는다.

사용 범위는 다음과 같다.

- 사용자 흐름(User Flow)
- 정보 구조(Information Architecture)
- 화면 목록(Screen List)
- 와이어프레임(Wireframe) 설명
- 컴포넌트 상태(Component State)
- 로딩(Loading), 빈 상태(Empty State), 오류(Error), 성공(Success), 재시도(Retry) 상태
- 반응형 동작(Responsive Behavior)
- 접근성 기준(Accessibility Criteria)
- 실제 구현/API 계약을 소비하는 UI 상태·상호작용의 추적성

백엔드 정책, API 계약(API Contract), 제품 범위 자체를 이 디렉터리에서 새로 결정하지 않는다. 기존 API·도메인 계약과 충돌을 발견하면 설계를 현재 승인 계약에 맞추거나 Product Owner 결정으로 돌린다.

## 현재 MVP4 Customer Commerce 설계 제안안

현재 `MVP4-UX-004` 전체 상태는 **`Proposed Design Contract / Draft / Pending Product Owner Approval`**이다. 아직 Product Owner가 Design Approved로 확정한 문서가 아니며 Frontend 구현 승인으로 해석하지 않는다.

### 화면·공통 구현 계약

- [`MVP4-UX-004-customer-commerce-redesign.md`](./MVP4-UX-004-customer-commerce-redesign.md): immutable 감사 snapshot, 전체 방향, visual system, 공통 상호작용, PENDING PO
- [`MVP4-UX-004-a-discovery-purchase-entry.md`](./MVP4-UX-004-a-discovery-purchase-entry.md): Header/Search, Home, PLP, Product Card, PDP, Compare, Wishlist
- [`MVP4-UX-004-b-cart-checkout-orders.md`](./MVP4-UX-004-b-cart-checkout-orders.md): Cart, Checkout/Toss, 결제 결과, Order List/Detail, Reorder
- [`MVP4-UX-004-c-subscription-account.md`](./MVP4-UX-004-c-subscription-account.md): Subscription List/Detail/Create, My, Pets, Notifications, Addresses, Billing, Login, Trust/Support
- [`MVP4-UX-004-d-shared-responsive-accessibility.md`](./MVP4-UX-004-d-shared-responsive-accessibility.md): 공통 components, breakpoint/container/header/sticky SSOT, accessibility, focus, motion

### 보조 근거·변경 기록

- [`MVP4-UX-004-benchmark-evidence.md`](./MVP4-UX-004-benchmark-evidence.md): 실제 Commerce 관찰과 PawCycle 내부 file/endpoint/type 추적 근거
- [`MVP4-UX-004-visual-interaction-correction.md`](./MVP4-UX-004-visual-interaction-correction.md): A–D를 보조하는 interaction/composition specification. A–D를 덮어쓰지 않는다.
- [`MVP4-UX-004-contract-alignment-correction.md`](./MVP4-UX-004-contract-alignment-correction.md): API 정합성 correction의 역사·변경 기록. 현재 구현 시 A–D보다 우선하는 override 문서가 아니다.

### 읽기 순서

현재 검토에서는:

`현재 사용자 지시 → customer-commerce-redesign → A/B/C → D SSOT → visual supplement → benchmark evidence → historical correction`

을 사용한다.

Product Owner가 향후 MVP4-UX-004를 승인하면 Frontend 구현 task는 승인된 revision/commit을 먼저 고정한 뒤 동일 순서로 읽는다. 오래된 correction 문장을 찾아 최신 A–D를 다시 뒤집지 않는다.

`MVP4-UX-002`의 제품 방향과 `MVP4-UX-003`의 감사 발견사항은 위 Draft에서 보존·조정·대체 여부를 명시한다. 기존 FE-004 Draft도 역사·감사 입력이며 자동 구현 정답이 아니다.

## 최소 디자인 문서 구조

```markdown
# [작업 ID] 디자인 제목

## 상태 / 승인 주체

## 승인된 제품 입력

## 사용자 흐름

## 화면

## 와이어프레임 메모

## 컴포넌트 상태

## 로딩 상태

## 빈 상태

## 오류 상태

## 성공 상태

## 반응형 동작

## 접근성 기준

## 열린 질문
```
