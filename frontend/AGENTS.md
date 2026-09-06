# Frontend 경로 규칙

이 파일은 `frontend/**`를 수정할 때의 **사용자 경험과 client contract 경계**만 정의한다. 공통 승인·Git·PR·산출물·병합 규칙은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따른다.

## Frontend 책임

- Next.js page와 React component
- TypeScript request/response type과 API adapter
- loading·empty·error·success·retry 상태
- responsive behavior와 accessibility
- Frontend unit/contract/regression test

## Server contract

- 승인된 API contract를 소비하며 Backend 동작을 추측해 보정하지 않는다.
- 가격, 할인, 재고 가능 여부, 구독 가능 여부, 결제·취소 정책 같은 서버 비즈니스 규칙을 Frontend에 별도 권위로 복제하지 않는다.
- HTTP method, path, body, status, CSRF, Idempotency-Key, If-Match, ETag 등 서버 계약을 보존한다.
- 새로운 Backend contract가 필요한 경우 cross-stack 작업으로 승인된 범위에서 함께 수정하거나 사용자 결정이 필요하면 중단한다.

## 상태와 UX

- 서버 상태와 local UI state를 구분한다.
- 실패 후 재시도에서 안전한 범위의 사용자 입력과 작업 의도를 보존한다.
- mutation을 인증 복귀 뒤 자동 재실행하지 않는 등 서버 안전 경계를 UI convenience로 우회하지 않는다.
- UI에 없는 제품 약속, 가격·배송 보장, 가짜 data/state를 만들지 않는다.

## 접근성

semantic HTML, keyboard, focus, label, error announcement, contrast, reduced motion, touch target과 주요 responsive breakpoint를 고려한다.

## 검증

변경 영향에 맞게 다음을 사용한다.

- shared helper/client: unit 또는 contract test
- API adapter: method/path/body/header regression
- 중요한 UI interaction: focused flow test
- 최종 Frontend gate: lint, typecheck, test, build

승인된 cross-stack 작업에서 `backend/**` 또는 `infra/**`도 함께 수정할 수 있지만, 그 경로의 `AGENTS.md`를 추가로 적용한다. Frontend 역할이라는 이유만으로 하나의 승인된 사용자 목적을 인위적으로 분리하지 않는다.
