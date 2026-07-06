# AUTH-001 TL → Backend/Frontend/QA 인수인계

## 전달 목적

Backend가 첫 번째 수직 MVP 보호 API와 소유자 검증을 구현할 때 사용할 인증 경계를 전달한다.

Frontend가 보호 기능 접근, 로그인 이동, 로그인 후 안전한 내부 GET 복귀, `AUTH_REQUIRED` 처리 흐름을 구현할 때 사용할 정책을 전달한다.

QA가 인증·인가·Open Redirect 시나리오를 검증할 때 사용할 기준을 전달한다.

## 공통 상태

- AUTH-001은 Proposed Authentication Design이다.
- 사용자 승인 전 `Approved`로 표시하지 않는다.
- API-001은 Proposed API Contract다.
- DATA-001은 Proposed Data Design이다.
- AUTH-001은 API-001과 DATA-001의 Proposed 상태를 변경하지 않는다.

## Backend 구현 입력

### 인증 경계

| API | 인증 | Backend 기준 |
| --- | --- | --- |
| `GET /api/products` | 불필요 | 공개 상품 목록 조회 |
| `GET /api/products/{productId}` | 불필요 | 공개 상품 상세와 SKU 조회 |
| `POST /api/subscriptions` | 필요 | 인증 회원 식별자를 구독 소유자로 사용 |
| `GET /api/subscriptions` | 필요 | 인증 회원 식별자로 본인 목록 필터링 |
| `GET /api/subscriptions/{subscriptionId}` | 필요 | 인증 회원 식별자로 본인 구독 상세만 조회 |

보호 API는 인증 컨텍스트에서 얻은 `memberId` 또는 동등한 회원 식별자를 사용한다. 요청 본문, query parameter, path variable로 전달된 회원 식별자를 신뢰해 소유자를 결정하지 않는다.

### 소유자 검증

- 내 구독 목록은 인증 회원의 구독만 반환한다.
- 내 구독 상세는 가능하면 `subscriptionId`와 인증 회원 식별자를 함께 조건으로 조회한다.
- 존재하지 않는 구독과 다른 회원 소유 구독은 같은 `SUBSCRIPTION_NOT_FOUND`, `404 Not Found` 후보로 표현한다.
- 소유자 이름, 다른 회원 식별자, 실제 존재 여부를 오류 응답에 노출하지 않는다.

### Backend 중단 조건

- 인증 컨텍스트에서 회원 식별자를 얻는 방식을 확정해야만 진행 가능한 경우
- 세션/토큰, 쿠키, CSRF, Spring Security 설정을 사용자 승인 없이 확정해야 하는 경우
- API-001 또는 DATA-001을 `Approved`로 바꿔야 구현 가능한 경우
- 다른 회원 구독 접근을 `403 Forbidden` 또는 별도 오류로 바꾸려면 제품/API 정책 재결정이 필요한 경우
- Open Redirect 검증 유틸 구현에 신규 의존성이 필요한 경우
- Secret 또는 민감정보 노출이 의심되는 경우

## Frontend 구현 입력

### 로그인 복귀 정책

- 상품 목록과 상품 상세는 비회원에게도 열려 있다.
- 비회원이 보호 기능에 접근하면 로그인 화면으로 이동한다.
- 로그인 성공 후 안전한 서비스 내부 GET 화면 경로로만 복귀한다.
- 유효한 복귀 경로가 없거나 검증에 실패하면 상품 목록으로 이동한다.
- 구독 생성 `POST`는 로그인 후 자동 재실행하지 않는다.
- 구독 생성 폼 입력 상태는 첫 MVP에서 자동 복원하지 않는다.

### `AUTH_REQUIRED` 처리 후보

- 보호 API 호출에서 `AUTH_REQUIRED`, `401 Unauthorized` 후보를 받으면 로그인 필요 흐름으로 연결한다.
- GET 보호 화면 접근 중이면 로그인 후 해당 GET 화면 경로 복귀를 후보로 사용할 수 있다.
- POST 제출 중이면 원래 POST 요청과 요청 본문을 복귀 경로로 저장하지 않는다.
- 구독 생성 제출 중 인증이 필요해진 경우에도 로그인 후 자동 생성하지 않고, 안전한 GET 화면 또는 fallback으로 이동한다.

### Frontend 중단 조건

- 구체 Next.js 라우트 문자열을 확정해야만 정책을 표현할 수 있는 경우
- 로그인 복귀 경로 저장 위치를 사용자 승인 없이 확정해야 하는 경우
- 입력 폼 상태 자동 복원이나 POST 자동 재실행이 필요하다고 판단되는 경우
- 외부 URL 또는 allowlist 밖 내부 경로를 복귀 대상으로 허용해야 하는 경우
- API-001 Proposed 계약을 변경해야 하는 경우

## Open Redirect 방지 기준

복귀 경로는 allowlist 기반으로 검증한다.

차단 대상:

- 외부 URL
- 프로토콜 포함 URL
- `//example.com` 같은 scheme-relative URL
- `javascript:`, `data:`, `file:` 등 위험 scheme 후보
- 역슬래시 포함 값
- URL 인코딩 또는 이중 인코딩 후 위험 패턴이 되는 값
- CR, LF, tab, null 등 제어 문자
- allowlist에 없는 내부 경로
- API path, POST 처리 path, 로그아웃 path, 관리자 path 후보

허용 후보:

- 상품 목록 GET 화면
- 상품 상세 GET 화면
- 구독 생성 입력 GET 화면
- 내 구독 목록 GET 화면
- 내 구독 상세 GET 화면

실제 라우트 문자열은 FE 구현에서 확정하며, 확정 뒤 AUTH-001 allowlist 후보를 재검토한다.

## QA 검증 입력

### 공개 접근

- 비회원이 `GET /api/products`를 호출할 수 있다.
- 비회원이 `GET /api/products/{productId}`를 호출할 수 있다.
- 로그인 회원도 같은 공개 API를 호출할 수 있다.

### 보호 접근

- 비회원은 `POST /api/subscriptions`를 사용할 수 없다.
- 비회원은 `GET /api/subscriptions`를 사용할 수 없다.
- 비회원은 `GET /api/subscriptions/{subscriptionId}`를 사용할 수 없다.
- 보호 API 인증 실패는 `AUTH_REQUIRED`, `401 Unauthorized` 후보로 표현된다.
- 보호 화면 접근은 로그인 화면 이동으로 표현된다.

### 로그인 복귀

- 안전한 내부 GET 복귀 경로가 있으면 로그인 성공 후 해당 경로로 이동한다.
- 복귀 경로가 없으면 상품 목록으로 이동한다.
- 검증되지 않은 내부 경로는 상품 목록으로 fallback한다.
- 외부 URL은 상품 목록으로 fallback한다.
- 프로토콜 포함 URL은 상품 목록으로 fallback한다.
- `//example.com` 형태는 상품 목록으로 fallback한다.
- 역슬래시, 인코딩 우회, 제어 문자 포함 후보는 상품 목록으로 fallback한다.
- 로그인 후 구독 생성 POST가 자동 재실행되지 않는다.
- 로그인 후 구독 생성 폼 입력 상태가 자동 복원되지 않는다.

### 인가

- 로그인 회원의 내 구독 목록에는 본인 구독만 포함된다.
- 본인 구독 상세는 조회할 수 있다.
- 존재하지 않는 구독 상세는 `SUBSCRIPTION_NOT_FOUND`, `404 Not Found` 후보로 표현된다.
- 다른 회원 소유 구독 상세도 `SUBSCRIPTION_NOT_FOUND`, `404 Not Found` 후보로 동일하게 표현된다.
- 오류 응답이나 화면에 다른 회원 식별자, 소유자 정보, 실제 존재 여부가 노출되지 않는다.

## 아직 확정하지 않은 기술 결정

- 세션 기반인지 토큰 기반인지
- 쿠키 속성
- CSRF 정책
- 실제 Spring Security 설정
- 로그인 화면 구현 위치
- Next.js 라우팅 방식
- 인증 실패 응답과 브라우저 리다이렉트의 최종 분기
- 로그인 복귀 경로 저장 방식과 수명
- Open Redirect 검증 유틸 구현 방식
- 인증 principal 구조와 회원 식별자 추출 방식

## API-001, DATA-001과 충돌하지 말아야 할 규칙

- API-001의 공개 API 후보 `GET /api/products`, `GET /api/products/{productId}`를 보호 API로 바꾸지 않는다.
- API-001의 보호 API 후보 `POST /api/subscriptions`, `GET /api/subscriptions`, `GET /api/subscriptions/{subscriptionId}`는 인증 필요로 유지한다.
- API-001의 `AUTH_REQUIRED`, `401 Unauthorized` 후보를 AUTH-001에서 최종 승인하거나 제거하지 않는다.
- API-001의 `SUBSCRIPTION_NOT_FOUND`, `404 Not Found` 후보를 AUTH-001에서 최종 승인하거나 제거하지 않는다.
- DATA-001의 `members`, `subscriptions.member_id` 또는 동등한 소유자 기준을 유지한다.
- 요청 본문으로 회원 식별자를 받아 소유자를 결정하는 방식으로 DATA-001의 소유자 기준을 우회하지 않는다.
- DATA-001 또는 API-001을 사용자 승인 없이 `Approved`로 표시하지 않는다.
