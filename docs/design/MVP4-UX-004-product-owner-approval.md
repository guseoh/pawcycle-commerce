# MVP4-UX-004 Product Owner Approval

> Status: Approved — 2026-08-29
> Scope: MVP4-UX-004 Customer Commerce UI/UX design decisions
> Approval authority: Product Owner

## 승인 대상

PR #254의 MVP4-UX-004 Customer Commerce 설계 계약과 최종 검토 결과를 기준으로, 남아 있던 Product Owner 결정 6개를 다음과 같이 승인한다.

1. **Mobile bottom navigation**: MVP4에서는 도입하지 않는다.
2. **Home 사회적 증거**: `인기 상품` 1개 section만 사용한다.
3. **Checkout CTA copy**: Phase A는 `주문 및 결제 준비`, Phase B Toss 승인 CTA는 `결제하기`를 사용한다.
4. **Anonymous Wishlist**: 이유를 inline으로 설명한 뒤 `로그인하기`로 유도한다. mutation 자동 replay는 하지 않는다.
5. **신규 Subscription 주 진입점**: Order Detail의 `정기배송으로 다시 받기`를 primary entry로 사용한다. `/subscriptions/new` direct entry는 유지한다.
6. **Contextual support**: Footer `/support`를 유지하고 Order Detail과 Subscription Detail에 상황별 support link를 제공한다.

## 설계 승인 범위

위 결정으로 `MVP4-UX-004`의 Product Owner 미결 항목은 해소되며, 현재 A–D 설계 계약은 **Frontend 구현을 시작할 수 있는 Approved Design Contract**로 취급한다.

기존 A–D 및 supporting 문서에 남아 있는 `Pending Product Owner Approval`, `PENDING PO`, `Recommended Default` 표현은 이번 승인 이전 Draft 상태를 기록한 문구이며, 위 6개 항목의 현재 상태를 의미하지 않는다. 구현 시에는 이 승인 기록과 현재 사용자 지시를 우선한다.

## 승인하지 않은 것

이번 승인은 다음을 의미하지 않는다.

- PR #254 병합 승인
- Frontend 구현 완료 또는 검증 완료
- MVP4 Product Complete 선언
- Backend/API/DB/보안 계약 변경 승인
- Production 배포 또는 운영 실행 승인

Frontend 구현 후에는 실제 browser에서 visual quality, responsive, accessibility, 핵심 Commerce/Subscription journey를 별도로 검증한다.
