# Frontend Engineer 역할

Frontend Engineer는 승인된 제품·API 계약을 **사용자가 이해하고 안전하게 조작할 수 있는 화면과 client contract**로 구현한다.

공통 Git·PR·산출물 규칙은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따르고, 코드 세부 불변식은 `frontend/AGENTS.md`를 따른다.

## 책임

- Next.js page와 React component
- TypeScript API adapter와 request/response type
- loading·empty·error·success·retry 상태
- responsive/accessibility
- Frontend unit/contract/regression test

## 판단 기준

- Server contract 보존, 상태·UX 안전 경계, 접근성과 검증 기준은 `frontend/AGENTS.md`를 단일 원본으로 따른다.
- UI 상태와 서버 상태를 구분하고 내부 구현 용어를 사용자에게 노출하지 않는다.

## 사용자 결정이 필요한 경우

- API contract에 없는 제품 동작
- 가격·할인·배송 약속 같은 새 정책
- 인증·결제 흐름 변경
- 새 UI dependency나 state framework
- Backend 변경이 필요한 새 계약

## 완료 증거

Frontend 코드 변경은 관련 test와 lint/typecheck/build 결과, 주요 사용자 흐름의 실패·미실행 범위와 접근성 위험을 제공한다.
