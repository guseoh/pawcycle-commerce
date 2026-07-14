# FRONTEND-001 Frontend 작업 보고서

## 작업 정보

- 작업 ID: `FRONTEND-001`
- 역할: Frontend Engineer
- 기준 브랜치: `main`
- 작업 브랜치: `feat/fe`
- 상태: 구현과 로컬 검증 완료. 현재 CI·Draft/Ready 상태는 GitHub PR을 권위 있는 원본으로 확인한다.

## 작업 목적

승인된 상품, session 인증, CSRF와 구독 API를 same-origin 상대 경로로 연결해 상품 탐색부터 로그인, 구독 생성, 내 구독 목록·상세까지 이어지는 첫 Frontend 수직 MVP를 제공한다.

## 입력 문서

- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/api/API-003-subscription-api-contract-decision-request.md`
- `docs/handoffs/SUBSCRIPTION-001/be-to-fe.md`

## 변경 범위

- `/`에서 `/products`로 이동하는 공개 상품 목록·상세 화면
- `/login?returnTo=...` 로그인과 안전한 내부 GET 화면 복귀
- 현재 회원, 로그인, 로그아웃과 메모리 기반 CSRF token 생명주기
- 상품 상세의 구독 가능한 SKU, 수량 1~10, 서버 제공 배송 주기 선택과 구독 생성
- 보호된 `/subscriptions`, `/subscriptions/[subscriptionId]` 목록·상세 화면
- 로딩, 빈 상태, 조회 불가, API 오류, 재시도와 생성 성공 상태
- label, fieldset, 오류 요약 포커스, skip link와 키보드 포커스 표시
- dependency 없는 안전 복귀 경로·날짜 표시·구독 입력 순수 로직 테스트

## 변경하지 않은 범위

- Backend, DB, API 계약, CORS, cookie, CI와 인프라
- 회원가입, OAuth2, JWT, 결제, 재고, 배송과 구독 변경·해지
- 클라이언트의 가격·다음 주문일·구독 가능 여부 재계산
- CSRF 실패나 로그인 복귀 뒤 이전 구독 생성 POST 자동 재실행
- 입력 폼 자동 복원, 전역 상태 관리·UI·테스트 dependency 추가

## 주요 결과

- 상품 목록과 상세는 인증과 무관하게 `/api/products`, `/api/products/{productId}`를 호출한다.
- API client는 모두 상대 경로 `/api/**`, `credentials: same-origin`, `cache: no-store`를 사용하고 승인 오류 응답을 `ApiError`로 보존한다.
- 인증 상태와 CSRF token은 React provider 메모리에만 보관한다. 초기화 시 현재 회원만 확인하며 공개 상품 조회만으로 CSRF token을 선취득하지 않는다.
- 로그인·로그아웃·구독 생성 직전에 token이 없을 때만 획득하고 로그인 성공 직후 새 token을 다시 획득한다.
- `AUTH_REQUIRED`로 비회원 전환 시 회원 ID와 기존 CSRF token을 함께 폐기한다.
- `CSRF_INVALID`를 받으면 기존 token을 먼저 폐기하고 새 token을 요청한다. 갱신 성공과 실패를 구분해 안내하며 실패한 POST는 재실행하지 않는다.
- `returnTo`는 `/products`, `/products/{양의 정수}`, `/subscriptions`, `/subscriptions/{양의 정수}`만 허용하고 그 밖의 값은 `/products`로 대체한다.
- 구독 생성은 중복 제출을 막고 응답의 `subscriptionId`로 상세 화면에 이동한다. 생성 전 `nextOrderDate`를 계산하거나 표시하지 않는다.
- 목록은 서버 순서를 그대로 표시하고 빈 배열을 별도 상태로 처리한다. 상세의 미존재·타인 소유·비숫자 ID는 `SUBSCRIPTION_NOT_FOUND` 동일 조회 불가 화면으로 처리한다.
- 날짜는 ISO local date 문자열을 timezone 변환 없이 `YYYY. M. D.`로 표시한다. 가격은 서버가 반환한 값만 표시한다.

## 오류와 재시도 경계

- `AUTH_REQUIRED`: 메모리 회원 ID와 CSRF token을 폐기하고 비회원으로 바꾼 뒤 안전한 로그인 복귀 경로로 이동한다.
- `CSRF_INVALID`: 기존 token을 폐기한 뒤 갱신에 성공한 경우에만 버튼을 다시 누르도록 안내한다. 갱신 실패는 별도 보안 정보 오류로 안내하고 token을 비운 상태로 유지한다. POST 자동 재실행은 없다.
- `SKU_NOT_FOUND`, `SKU_NOT_SUBSCRIBABLE`: 선택을 지우고 상품 정보를 다시 조회한다.
- `VALIDATION_FAILED`: 서버의 실제 `fieldErrors.field`를 해당 입력에 연결하고 오류 요약으로 포커스를 이동한다.
- `SUBSCRIPTION_NOT_FOUND`: 정보 노출 없이 고정된 조회 불가 화면을 표시하며 동일 조회 재시도 버튼은 제공하지 않는다.
- 일시적 목록·상세·상품 오류: 사용자가 명시적으로 다시 시도할 수 있다.

## 검증 결과

- Node.js `v22.17.1`, npm `10.9.2`
- `npm ci --no-audit --no-fund`: 통과, lockfile 기준 343 packages 설치
- `npm test`: 순수 로직 테스트 8개 통과. 인증 만료 token 폐기, 새 token 로그인 1회 실행, CSRF 갱신 성공·실패, 성공한 로그인 보존과 POST 미재실행을 포함한다.
- `npm run typecheck`: 통과
- `npm run lint`: 통과
- `npm run build`: 통과, 승인 화면 route 생성 확인
- `py -m py_compile scripts\validate-task-artifacts.py`: 통과
- `"FRONTEND-001" | py scripts\validate-task-artifacts.py --from-stdin`: 통과
- `py scripts\validate-task-artifacts.py --task-id FRONTEND-001`: 통과
- `git diff --check`: 통과

## 실행하지 못한 검증과 이유

- session cookie와 실제 CSRF token을 포함한 브라우저 E2E는 실행하지 않았다. 로컬 Next.js 단독 개발 서버에는 `/api/**`를 Backend로 연결하는 승인된 same-origin 구성이 없고, proxy·CORS·인프라 변경은 작업 범위 밖이므로 QA 통합 환경의 검증 항목으로 전달했다.

## Git 결과

- 최신 `origin/main` `5f095b3f60372135ca1d1b8ebe677eed49d36497`에서 로컬 `feat/fe`를 생성했다.
- 구현 commit: `f9e391c feat(frontend): 첫 구독 수직 MVP 구현`
- 인증·CSRF 생명주기 수정 commit: `7eff94a fix(frontend): 인증 CSRF 생명주기 수정`
- Ready 리뷰 반영 commit: `1dee48a fix(frontend): 리뷰 접근성과 인증 경쟁 반영`
- `origin/feat/fe`를 새로 만들고 tracking push했다.
- push 직후 로컬 `feat/fe`와 `origin/feat/fe`는 ahead 0 / behind 0이다.
- rebase, force push와 자동 병합은 사용하지 않았다.

## PR 결과

- `main` ← `feat/fe` Draft PR #43을 생성했다.
- PR URL: `https://github.com/guseoh/pawcycle-commerce/pull/43`
- 생성 직후 상태: `OPEN`, Draft, 미병합, head `f9e391c0b5de03e6c9276e9b5ae58dac15e6a16e`
- Repository Validation이 `FRONTEND-001` prefix를 인식하지 못한 차단을 해소하기 위해 validator의 prefix 목록에 `FRONTEND` 한 줄만 추가했으며 기존 섹션·판정 규칙은 변경하지 않았다.
- 최신 head의 CI·review·Draft/Ready 상태는 GitHub PR을 권위 있는 원본으로 확인한다.
- 최신 Repository Validation 성공과 유효한 미해결 review thread가 없음을 확인한 뒤 Ready for review로 전환했다.
- CodeRabbit 지적 7개는 6개 최소 수정과 1개 dependency 제외 근거 답변으로 정리했으며 모든 thread가 resolved다.
- 자동 병합은 수행하지 않았다.

## 적용·실행 방법

```powershell
git fetch origin --prune
git switch feat/fe
git pull --ff-only origin feat/fe
Set-Location frontend
npm ci
npm run dev
```

브라우저 통합 확인 환경은 `/api/**`가 Backend로 연결되는 same-origin 구성을 사용해야 한다. Frontend 자체는 cross-origin 주소나 별도 proxy 정책을 추가하지 않는다.

## 알려진 위험과 후속 확인

- 로컬 정적 검사와 production build는 통과했지만 실제 session cookie·CSRF를 포함한 브라우저 E2E는 same-origin 통합 환경에서 QA가 확인해야 한다.
- 구독 생성은 MVP 계약상 idempotency key가 없으므로 timeout 뒤 사용자가 다시 제출하면 별도 구독이 생성될 수 있다. UI는 처리 중 중복 클릭만 방지한다.
- Repository Validation, Draft/Ready와 review 상태는 실행 시점의 GitHub PR에서 확인한다.

## 다음 권장 작업

- QA가 `docs/handoffs/FRONTEND-001/fe-to-qa.md`의 공개·인증·CSRF·구독 상태 시나리오를 same-origin 통합 환경에서 검증한다.
- Repository Validation 성공과 유효한 미해결 리뷰가 없는 것을 확인한 뒤 Ready 전환 여부를 판단한다. 자동 병합은 하지 않는다.
