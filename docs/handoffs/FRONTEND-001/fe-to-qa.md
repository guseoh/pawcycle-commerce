# FRONTEND-001 Frontend → QA 인수인계

## 전달 목적

승인된 상품·session 인증·구독 API를 연결한 첫 Frontend 수직 MVP의 브라우저 통합 검증 기준을 전달한다.

## 대상 역할

- QA Engineer

## 입력 문서

- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/api/API-003-subscription-api-contract-decision-request.md`
- `docs/handoffs/SUBSCRIPTION-001/be-to-fe.md`
- `docs/reports/FRONTEND-001/fe-report.md`

## 완료된 작업

- 공개 상품 목록·상세와 루트 redirect
- 로그인·로그아웃·현재 회원과 메모리 CSRF token 관리
- 안전한 로그인 복귀 경로 검증
- 상품 상세의 구독 생성과 생성 상세 이동
- 보호된 내 구독 목록·상세
- 로딩·빈 상태·오류·재시도·생성 성공 상태
- semantic form, label, fieldset, 오류 요약 포커스와 키보드 포커스 표시

## 사용 가능한 결과

- 공개 화면: `/products`, `/products/{productId}`
- 인증 화면: `/login?returnTo=...`
- 보호 화면: `/subscriptions`, `/subscriptions/{subscriptionId}`
- 모든 API 호출: same-origin `/api/**`

## 관련 파일

- `frontend/src/app/`
- `frontend/src/components/`
- `frontend/src/lib/api.ts`
- `frontend/src/lib/auth-context.tsx`
- `frontend/src/lib/frontend-utils.ts`
- `frontend/src/lib/frontend-utils.test.mts`
- `docs/reports/FRONTEND-001/fe-report.md`

## 인수 조건과 추적성

- API-002의 공개 상품 응답은 상품 카드와 SKU 표시 가격에 직접 사용한다.
- AUTH-003의 session·CSRF 계약은 메모리 provider와 로그인·로그아웃·구독 생성 POST에 적용한다.
- API-003의 구독 생성·목록·상세 응답과 오류 코드는 화면 상태에 직접 대응한다.
- UX-001의 내부 GET 복귀, 날짜 표시, 생성 전 다음 주문일 비표시와 접근성 기준을 적용한다.

## 확정된 결정

- 공개 상품 화면은 비회원도 접근한다.
- 보호 화면은 비회원이면 `/login?returnTo=...`로 이동한다.
- 복귀 대상은 승인된 서비스 내부 GET 화면만 허용하며 유효하지 않으면 `/products`로 이동한다.
- CSRF token은 메모리에만 두고 초기 공개 화면에서는 선취득하지 않는다. 로그인·로그아웃·구독 생성 직전에 필요할 때 획득하고 로그인 성공 직후 다시 획득한다.
- `AUTH_REQUIRED`로 비회원 전환 시 기존 회원 ID와 CSRF token을 함께 폐기한다.
- `CSRF_INVALID` 뒤 기존 token을 먼저 폐기한다. 새 token 획득에 성공한 경우에만 수동 재시도를 안내하며, 실패하면 보안 정보 갱신 실패를 안내하고 실패한 POST는 자동 재실행하지 않는다.
- 구독 생성 전 가격 합계, 다음 주문일, 가능 여부를 클라이언트에서 계산하지 않는다.
- 날짜는 timezone 변환 없이 `YYYY. M. D.`로 표시한다.

## 미확정 결정

- 없음.

## 승인 필요 항목

- 없음. 새로운 문구·정책·API가 필요하면 구현을 확장하지 말고 제품·Tech Lead 승인으로 돌린다.

## 다음 역할의 입력

- `/api/**`와 Frontend가 같은 origin에서 동작하는 통합 환경
- 승인된 QA 회원 계정과 공개·구독 가능 SKU 데이터
- 빈 구독 회원 또는 테스트 데이터 초기화 방법

## 지켜야 할 규칙

- 실제 비밀번호, session cookie와 CSRF token을 문서·이슈·로그에 남기지 않는다.
- POST timeout 뒤 자동 또는 즉시 반복 호출하지 않는다.
- Backend 응답, DB 정책, CORS와 cookie 설정을 Frontend 결함 수정 범위로 변경하지 않는다.
- 새 dependency, 자동 병합과 범위 밖 구독 변경·해지 기능을 추가하지 않는다.

## 적용·실행 방법

```powershell
Set-Location frontend
npm ci
npm test
npm run typecheck
npm run lint
npm run build
npm run dev
```

## 다음 역할의 검증 포인트

1. 비회원이 `/products`와 공개 상품 상세를 보고 상품·SKU 표시 가격·구독 가능 여부를 확인할 수 있다.
2. `/subscriptions` 또는 조회 가능한 상세에 비회원으로 접근하면 로그인으로 이동하고, 성공 뒤 원래 내부 GET 화면으로 복귀한다.
3. `returnTo=https://example.com`, `//example.com`, `/login`, 0·음수 ID 등은 `/products`로 대체된다.
4. 로그인 성공 직후 현재 회원 UI가 갱신되고 로그아웃 성공 뒤 보호 화면 접근이 다시 로그인으로 연결된다.
5. 구독 가능한 SKU, 수량 1~10과 제공된 배송 주기를 선택할 수 있고 생성 중 버튼을 다시 눌러도 POST가 중복 전송되지 않는다.
6. 생성 전 화면에 `nextOrderDate`가 계산·표시되지 않고, 성공 뒤 응답 `subscriptionId`의 상세에서 서버 날짜가 표시된다.
7. 공개 상품 진입만으로 `/api/auth/csrf`가 호출되지 않고 로그인·로그아웃·구독 생성 직전에 token이 없을 때 호출된다.
8. session 만료로 `AUTH_REQUIRED`를 받은 뒤 기존 token이 폐기되고, 로그인은 새 token을 사용한 POST 한 번으로 성공한다.
9. `CSRF_INVALID` 뒤 새 token 획득에 성공하면 수동 재시도를 안내하고, 획득 실패 시 보안 정보 갱신 실패를 안내한다. 두 경우 모두 원래 POST는 자동 재실행되지 않는다.
10. 목록의 `subscriptions: []`는 빈 상태이고, 값이 있으면 서버가 준 내림차순을 그대로 표시한다.
11. 미존재·타인 소유·비숫자 구독 ID는 같은 조회 불가 화면이며 소유권 정보를 노출하지 않는다.
12. 일시적 상품·목록·상세 오류는 명시적 재시도 후 회복하고, 오류 요약은 키보드 포커스를 받는다.
13. ISO 날짜가 로컬 timezone 이동 없이 `YYYY. M. D.`이고 가격은 서버 반환값과 일치한다.
14. 좁은 화면, keyboard-only, label/fieldset, focus-visible과 skip link를 확인한다.

## QA 필요 여부

- 필요. session cookie와 CSRF를 포함한 same-origin 브라우저 통합 검증이 필수다.

## AI 리뷰에서 남은 확인 항목

- 현재 없음. 최신 GitHub PR review thread를 권위 있는 원본으로 다시 확인한다.

## 알려진 위험

- MVP에는 idempotency key가 없어 timeout 뒤 사용자의 새 제출은 별도 구독을 만들 수 있다.
- 클라이언트는 처리 중 중복 클릭만 방지하며 네트워크 실패 뒤 자동 POST 재실행을 하지 않는다.

## 남은 위험과 주의 사항

- 로컬 Next.js 단독 실행은 `/api/**` same-origin Backend 연결을 제공하지 않을 수 있으므로 승인된 통합 환경에서 API 흐름을 검증한다.
- 동적 CI·Draft/Ready·review 상태는 문서에 복제하지 않고 GitHub PR에서 확인한다.

## 다음 권장 작업

- 위 시나리오를 desktop·좁은 viewport와 keyboard-only 조건에서 수행한다.
- 실제 버그·보안·계약 위반만 재현 근거와 함께 Frontend에 반환한다.

## 완료 조건

- 공개·보호 화면, 로그인 복귀, CSRF 수동 재시도와 구독 생성·목록·상세 핵심 시나리오가 승인 계약과 일치한다.
- Repository Validation이 성공하고 유효한 미해결 리뷰가 없다.

## 중단 조건

- 실제 Backend 응답이 승인 API와 다르다.
- cross-origin, CORS, cookie 또는 인프라 정책 변경이 필요하다.
- 새 제품 문구·화면 정책, 신규 dependency 또는 Backend 변경이 필요하다.
- 필수 시나리오 실패를 범위 내 Frontend 수정으로 해결할 수 없다.
