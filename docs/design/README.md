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

## 현재 MVP4 Customer Commerce 설계 기준

- [`MVP4-UX-004-customer-commerce-redesign.md`](./MVP4-UX-004-customer-commerce-redesign.md): 정식 권위, 감사 인벤토리, 벤치마크, 시각 시스템, 공통 상호작용과 미결 결정
- [`MVP4-UX-004-a-discovery-purchase-entry.md`](./MVP4-UX-004-a-discovery-purchase-entry.md): Header/Search, Home, PLP, Product Card, PDP, Compare, Wishlist
- [`MVP4-UX-004-b-cart-checkout-orders.md`](./MVP4-UX-004-b-cart-checkout-orders.md): Cart, Checkout, 결제 결과, 주문, 재주문
- [`MVP4-UX-004-c-subscription-account.md`](./MVP4-UX-004-c-subscription-account.md): Subscription, My, Pets, Notifications, Addresses, Billing, Login, Trust/Support
- [`MVP4-UX-004-d-shared-responsive-accessibility.md`](./MVP4-UX-004-d-shared-responsive-accessibility.md): 공통 컴포넌트, responsive, accessibility, 상태·focus·motion 계약

`MVP4-UX-002`의 제품 방향과 `MVP4-UX-003`의 감사 발견사항은 위 문서에서 명시적으로 보존·조정·대체한다. 화면 구성과 시각 규칙이 충돌하면 `MVP4-UX-004`를 우선하되, Product Owner 미결 항목은 승인 전 구현하지 않는다.

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
