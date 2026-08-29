# 디자인 문서(Design Documents)

이 디렉터리는 승인된 제품 작업을 위한 UX/UI 설계 문서를 보관한다.

사용 범위는 다음과 같다.

- 사용자 흐름(User Flow)
- 정보 구조(Information Architecture)
- 화면 목록(Screen List)
- 와이어프레임(Wireframe) 설명
- 컴포넌트 상태(Component State)
- 로딩(Loading), 빈 상태(Empty State), 오류(Error), 성공(Success), 재시도(Retry) 상태
- 반응형 동작(Responsive Behavior)
- 접근성 기준(Accessibility Criteria)

백엔드 정책, API 계약(API Contract), 제품 범위는 이 디렉터리에서 변경하지 않는다.

## 현재 MVP4 Customer Commerce 설계 제안안

- [`MVP4-UX-004-contract-alignment-correction.md`](./MVP4-UX-004-contract-alignment-correction.md): 최신 `main` API·상태 계약과 대조한 최종 delta correction. 기존 MVP4-UX-004 문서와 충돌하면 이 문서가 우선한다.
- [`MVP4-UX-004-customer-commerce-redesign.md`](./MVP4-UX-004-customer-commerce-redesign.md): `Proposed Design Contract / Draft / Pending Product Owner Approval`, 감사 인벤토리, 시각 시스템, 공통 상호작용과 미결 결정
- [`MVP4-UX-004-a-discovery-purchase-entry.md`](./MVP4-UX-004-a-discovery-purchase-entry.md): Header/Search, Home, PLP, Product Card, PDP, Compare, Wishlist
- [`MVP4-UX-004-b-cart-checkout-orders.md`](./MVP4-UX-004-b-cart-checkout-orders.md): Cart, Checkout, 결제 결과, 주문, 재주문
- [`MVP4-UX-004-c-subscription-account.md`](./MVP4-UX-004-c-subscription-account.md): Subscription, My, Pets, Notifications, Addresses, Billing, Login, Trust/Support
- [`MVP4-UX-004-d-shared-responsive-accessibility.md`](./MVP4-UX-004-d-shared-responsive-accessibility.md): 공통 컴포넌트, breakpoint/container SSOT, accessibility, 상태·focus·motion 계약
- [`MVP4-UX-004-benchmark-evidence.md`](./MVP4-UX-004-benchmark-evidence.md): 실제 Commerce component·interaction 관찰, Primary/Secondary 근거, 증거 등급
- [`MVP4-UX-004-visual-interaction-correction.md`](./MVP4-UX-004-visual-interaction-correction.md): 상세 상태 전이, annotated composition, 변경된 기존 계약

구현 전 읽기 우선순위는 `현재 사용자 승인 → contract-alignment-correction → visual-interaction-correction → A–D → customer-commerce-redesign → benchmark evidence`다. correction에서 명시적으로 폐기한 과거 문장은 구현 근거로 사용하지 않는다.

`MVP4-UX-002`의 제품 방향과 `MVP4-UX-003`의 감사 발견사항은 위 문서에서 명시적으로 보존·조정·대체한다. `MVP4-UX-004`는 Product Owner 승인 전 제안안이며 구현 권위가 아니다.

## 최소 디자인 문서 구조

```markdown
# [작업 ID] 디자인 제목

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
