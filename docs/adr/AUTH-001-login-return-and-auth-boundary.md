# AUTH-001 로그인 복귀와 인증 경계 설계

## 문서 상태

- 작업 ID: `AUTH-001`
- 역할: Tech Lead
- 결정 상태: Proposed Authentication Design
- 기준 브랜치: 최신 `main`
- 기준 입력 문서:
  - `AGENTS.md`
  - `docs/product/PS-002-first-mvp-requirements.md`
  - `docs/product/PS-003-ux-product-decisions.md`
  - `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
  - `docs/design/UX-001-first-mvp-subscription-experience.md`
  - `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
  - `docs/data/DATA-001-first-mvp-data-model.md`
  - `docs/api/API-001-first-mvp-api-contract.md`
  - `docs/reports/API-001/be-report.md`
  - `docs/handoffs/API-001/be-to-fe-qa.md`

사용자 승인 전까지 이 문서와 인증 정책은 `Approved`로 표시하지 않는다. DATA-001과 API-001이 Proposed 상태인 경우에도 AUTH-001은 그 상태를 변경하지 않는다.

Tech Lead 전용 역할 문서와 Tech Lead 전용 `.agents/skills/**`는 저장소에서 확인되지 않았다. 따라서 루트 `AGENTS.md`, 선행 설계 문서, 사용자 지시를 기준으로 작성한다.

## 작업 목적

첫 번째 수직 MVP의 공개 기능과 보호 기능 경계, 로그인 후 복귀 정책, 보호 API 접근 규칙, Open Redirect 방지 기준을 후속 Backend, Frontend, QA가 추측 없이 사용할 수 있게 정리한다.

이번 작업은 인증 설계 제안이다. Spring Security, 세션, 토큰, 쿠키, 로그인 화면, Next.js 라우팅, Controller, Service, Repository, JPA Entity, Flyway, 테스트 코드는 구현하지 않는다.

## 승인된 입력

- 상품 목록과 상품 상세는 비회원과 로그인 회원 모두 접근 가능하다.
- SKU와 구독 가능 여부 확인은 공개 탐색 범위다.
- 구독 생성, 내 구독 목록, 내 구독 상세는 로그인 회원만 접근 가능하다.
- 비회원이 보호 기능에 접근하면 로그인 화면으로 이동한다.
- 로그인 성공 후 안전한 서비스 내부 GET 경로로만 복귀한다.
- 유효한 내부 복귀 경로가 없으면 상품 목록으로 이동한다.
- 외부 URL과 검증되지 않은 임의 경로로 복귀하지 않는다.
- 첫 MVP에서는 입력 폼 상태 자동 복원과 이전 POST 요청 자동 재실행을 하지 않는다.
- API-001의 인증 실패 후보는 `AUTH_REQUIRED`와 `401 Unauthorized`다.
- 존재하지 않는 구독과 다른 회원 소유 구독은 사용자에게 같은 `SUBSCRIPTION_NOT_FOUND` 후보로 표현한다.
- Spring Security, 세션, 토큰, 쿠키, 로그인 복귀 경로 저장 방식은 아직 확정하지 않았다.

## 인증 경계

### 공개 기능

| 기능 | API 후보 | 인증 | 설명 |
| --- | --- | --- | --- |
| 상품 목록 조회 | `GET /api/products` | 불필요 | 비회원과 로그인 회원 모두 접근 가능 |
| 상품 상세 조회 | `GET /api/products/{productId}` | 불필요 | SKU와 구독 가능 여부도 공개 탐색 범위 |

공개 기능은 사용자 식별 없이 호출할 수 있다. 로그인 회원이 호출하더라도 공개 API의 응답은 회원별 구독 정보나 개인정보를 포함하지 않는다.

### 보호 기능

| 기능 | API 후보 | 인증 | 인가 |
| --- | --- | --- | --- |
| 구독 생성 | `POST /api/subscriptions` | 필요 | 인증 회원 본인으로 생성 |
| 내 구독 목록 조회 | `GET /api/subscriptions` | 필요 | 인증 회원 본인 구독만 조회 |
| 내 구독 상세 조회 | `GET /api/subscriptions/{subscriptionId}` | 필요 | 인증 회원 본인 구독만 조회 |

보호 API는 인증 회원의 `memberId` 또는 동등한 회원 식별자를 기준으로 처리한다. 이 식별자는 요청 본문이나 클라이언트가 보낸 임의 파라미터가 아니라 인증 컨텍스트에서 얻어야 한다.

회원 식별자를 인증 컨텍스트에서 얻는 구체 구현은 Backend 구현으로 넘긴다. Spring Security `Authentication`, 세션, 토큰, 쿠키, 커스텀 principal, 필터 또는 인터셉터 중 무엇을 사용할지는 이번 문서에서 확정하지 않는다.

## 인가와 소유자 검증 경계

- 구독 생성은 인증 회원의 회원 식별자를 새 구독의 소유자 기준으로 사용한다.
- 내 구독 목록은 인증 회원의 회원 식별자로 필터링한다.
- 내 구독 상세는 `subscriptionId`만으로 조회한 뒤 소유자를 비교해 분기하는 방식보다, 가능하면 `subscriptionId`와 인증 회원 식별자를 함께 조건으로 조회해 존재 여부와 소유자 정보를 노출하지 않는다.
- 존재하지 않는 구독과 다른 회원 소유 구독은 사용자와 API 후보 모두 `SUBSCRIPTION_NOT_FOUND`, `404 Not Found` 후보로 동일하게 표현한다.
- 관리자 조회, 권한 등급, 회원 탈퇴, soft delete, 보관, 익명화 정책은 이번 MVP 인증 경계에서 확정하지 않는다.

## 로그인 후 복귀 정책

### 기본 흐름

```text
비회원이 보호 기능 접근
→ 로그인 화면으로 이동
→ 로그인 성공
→ 저장 또는 전달된 복귀 경로 검증
→ 안전한 서비스 내부 GET 화면 경로이면 해당 경로로 이동
→ 유효하지 않거나 없으면 상품 목록으로 이동
```

보호 기능 접근은 화면 접근과 API 호출에서 다르게 표현될 수 있다.

- 브라우저 화면 흐름에서는 로그인 화면 이동이 사용자 경험 기준이다.
- 보호 API 호출에서는 API-001 후보인 `AUTH_REQUIRED`, `401 Unauthorized`를 유지한다.
- 인증 실패 응답과 브라우저 리다이렉트의 최종 분기는 후속 Backend/Frontend 인증 구현에서 결정한다.

### 복귀 대상 제한

로그인 성공 후 복귀 대상은 안전한 서비스 내부 GET 화면 경로로 제한한다.

허용 경로 후보:

| 화면 후보 | 허용 이유 | 비고 |
| --- | --- | --- |
| 상품 목록 화면 | 기본 fallback | 유효한 복귀 경로가 없을 때 이동 |
| 상품 상세 화면 | 공개 GET 화면 | `productId`가 필요하면 라우팅 구현에서 검증 |
| 구독 생성 입력 화면 | 보호 기능 진입 맥락 복귀 | GET 화면으로만 허용하고 이전 POST는 재실행하지 않음 |
| 내 구독 목록 화면 | 보호 GET 화면 | 로그인 후 본인 목록 조회 |
| 내 구독 상세 화면 | 보호 GET 화면 | 로그인 후 API 소유자 검증으로 다시 보호 |

구체 화면 URI와 Next.js 라우트 문자열은 아직 확정하지 않는다. 위 표는 구현팀이 allowlist 또는 동등한 검증 기준을 만들 때 사용할 개념 경로 후보이며, 실제 라우트가 확정되면 AUTH-001 기준을 재검토한다.

### 금지 사항

- 로그인 후 이전 `POST /api/subscriptions` 요청을 자동으로 다시 실행하지 않는다.
- 구독 생성 폼 입력 상태를 자동 복원하지 않는다.
- 클라이언트가 저장한 요청 본문, localStorage, sessionStorage, URL query만을 신뢰해 구독을 생성하지 않는다.
- 유효하지 않은 복귀 경로를 에러 화면으로 노출하지 않고 상품 목록으로 fallback한다.

### 복귀 경로 저장 방식 후보

아래 방식은 후보이며 이번 작업에서 확정하지 않는다.

| 후보 | 장점 | 주의 |
| --- | --- | --- |
| 로그인 URL의 `returnTo` 후보 쿼리 | FE 라우팅에서 단순 | URL 노출, 조작 가능성, 엄격한 검증 필요 |
| 서버 세션 또는 인증 플로우 상태 | 서버에서 검증·수명 관리 가능 | 세션 방식 확정 필요 |
| 임시 쿠키 | 브라우저 이동 흐름과 결합 쉬움 | 쿠키 속성, CSRF, 만료 정책 결정 필요 |
| 클라이언트 sessionStorage | FE 구현이 단순 | 신뢰 원천으로 쓰지 말고 재검증 필요 |

## Open Redirect 방지 정책

복귀 경로 검증은 allowlist 우선으로 설계한다. 단순히 문자열이 `/`로 시작하는지만 확인하는 방식은 충분하지 않다.

### 차단 규칙

다음 입력은 복귀 경로로 사용할 수 없다.

- `https://example.com/path`, `http://example.com/path`처럼 프로토콜을 포함한 외부 URL
- `javascript:`, `data:`, `file:` 등 URL scheme이 포함된 값
- `//example.com/path` 같은 scheme-relative URL
- `\example.com`, `/\example.com`, `%5C%5Cexample.com`처럼 역슬래시를 포함하거나 인코딩으로 우회하는 값
- `%2F%2Fexample.com`, `%252F%252Fexample.com`처럼 디코딩 후 외부 URL 또는 scheme-relative URL이 되는 값
- CR, LF, tab, null 등 제어 문자를 포함한 값
- 공백 trim 또는 URL decode 후 값이 달라져 위험 패턴이 생기는 값
- 서비스 내부 경로처럼 보이지만 allowlist에 없는 임의 경로
- GET 화면 경로가 아닌 API path, POST 처리 path, 로그아웃 path, 관리자 path 후보

### 허용 규칙

안전한 복귀 경로 후보는 다음 조건을 모두 만족해야 한다.

- 서비스 내부 상대 경로다.
- 단일 `/`로 시작하고 `//`로 시작하지 않는다.
- 프로토콜, 호스트, 포트 정보를 포함하지 않는다.
- 역슬래시와 제어 문자를 포함하지 않는다.
- URL decode와 정규화 후에도 같은 보안 조건을 만족한다.
- 첫 MVP에서 허용한 GET 화면 경로 allowlist에 포함된다.
- query string이 필요하다면 라우트별로 허용된 키와 값 형태만 받는다.

검증 실패 시 기본 fallback은 상품 목록 화면이다. 실패 이유, 원본 외부 URL, 조작된 값을 사용자 화면이나 로그에 과도하게 노출하지 않는다.

### 구현 후보

- Backend 또는 auth boundary에 `ReturnPathValidator`와 동등한 검증 유틸 후보를 둔다.
- Frontend도 사용자 경험상 사전 필터링을 할 수 있지만, 최종 신뢰 경계는 Backend 또는 인증 경계 검증이어야 한다.
- allowlist는 화면 라우트가 확정된 뒤 Backend/Frontend가 공유 가능한 상수 또는 계약으로 관리할 수 있다.
- 검증 유틸의 실제 코드, 테스트, 패키지 위치는 Backend 또는 Frontend 구현 작업으로 넘긴다.

## API 오류 표현 영향

AUTH-001은 API-001의 Proposed API Contract와 충돌하지 않는다.

| 상황 | API-001 후보 유지 | 사용자 흐름 기준 |
| --- | --- | --- |
| 비회원이 보호 API 호출 | `AUTH_REQUIRED`, `401 Unauthorized` | 로그인 필요 상태 또는 로그인 화면 이동 |
| 비회원이 보호 화면 접근 | API 응답 또는 화면 라우팅 분기 후속 결정 | 로그인 화면 이동 |
| 로그인 회원이 다른 회원 구독 상세 접근 | `SUBSCRIPTION_NOT_FOUND`, `404 Not Found` | 구독을 확인할 수 없음 |
| 존재하지 않는 구독 상세 접근 | `SUBSCRIPTION_NOT_FOUND`, `404 Not Found` | 구독을 확인할 수 없음 |

인증 실패와 인가 실패는 내부적으로 다르게 다룬다.

- 인증 실패: 사용자를 식별할 수 없는 상태다. 보호 기능 접근을 중단하고 로그인 필요로 표현한다.
- 인가 실패: 사용자는 식별됐지만 요청 대상에 접근할 수 없는 상태다. 첫 MVP의 구독 상세에서는 존재하지 않는 구독과 동일하게 표현해 정보 노출을 줄인다.

최종 오류 응답 JSON은 API-001 Proposed 상태를 유지한다. AUTH-001은 `code`, `message`, `fieldErrors` 후보를 변경하지 않는다.

## Frontend 입력

- 상품 목록과 상품 상세는 비회원도 접근 가능한 화면으로 유지한다.
- 보호 화면 또는 보호 액션 접근 시 로그인 화면 이동을 처리한다.
- 로그인 성공 후 내부 GET 경로만 복귀 대상으로 사용한다.
- 잘못된 복귀 경로, 외부 URL, 검증되지 않은 경로는 상품 목록으로 이동한다.
- 구독 생성 `POST`는 로그인 후 자동 재실행하지 않는다.
- 구독 생성 폼 입력 상태는 첫 MVP에서 자동 복원하지 않는다.
- 보호 API에서 `AUTH_REQUIRED` 후보를 받으면 로그인 필요 흐름으로 연결할 수 있다.
- `SUBSCRIPTION_NOT_FOUND`는 존재하지 않는 구독과 다른 회원 소유 구독을 같은 사용자 상태로 표시한다.
- 구체 라우트 문자열과 Next.js 라우팅 방식은 후속 FE 구현에서 확정한다.

## Backend 입력

- 보호 API는 인증 컨텍스트에서 회원 식별자를 얻어 처리한다.
- 요청 본문이나 query parameter로 전달된 `memberId`를 신뢰해 구독 소유자를 결정하지 않는다.
- 구독 생성은 인증 회원 식별자, SKU 존재와 구독 가능 여부, 수량, 배송 주기, 다음 주문 예정일 계산을 조정한다.
- 내 구독 목록은 인증 회원 식별자로 필터링한다.
- 내 구독 상세는 다른 회원 소유 구독의 존재 여부를 노출하지 않는 조회 방식이 필요하다.
- 보호 API 필터, 인터셉터, Spring Security 설정, 세션/토큰 구현은 이번 작업에서 구현하지 않는다.
- Open Redirect 방지 검증 유틸은 후속 구현 작업에서 코드와 테스트로 확정한다.

## QA 입력

QA는 다음 기준을 검증한다.

- 비회원이 `GET /api/products`에 접근할 수 있다.
- 비회원이 `GET /api/products/{productId}`에 접근할 수 있고 SKU와 구독 가능 여부를 확인할 수 있다.
- 비회원은 `POST /api/subscriptions`, `GET /api/subscriptions`, `GET /api/subscriptions/{subscriptionId}`에 접근할 수 없다.
- 비회원이 보호 화면에 접근하면 로그인 화면으로 이동한다.
- 로그인 성공 후 안전한 내부 GET 경로가 있으면 해당 경로로 복귀한다.
- 유효한 복귀 경로가 없으면 상품 목록으로 이동한다.
- 외부 URL 복귀가 차단된다.
- scheme-relative URL, 프로토콜 포함 URL, 역슬래시, 인코딩 우회, 제어 문자 후보가 fallback 처리된다.
- allowlist에 없는 내부 경로는 상품 목록으로 fallback한다.
- 로그인 후 구독 생성 POST 요청이 자동 재실행되지 않는다.
- 로그인 후 구독 생성 폼 입력 상태가 자동 복원되지 않는다.
- 다른 회원 구독 상세 접근과 존재하지 않는 구독 상세 접근은 `SUBSCRIPTION_NOT_FOUND` 후보로 동일하게 표현된다.

## Deferred Technical Decision

- 세션 기반인지 토큰 기반인지
- 쿠키 속성
- CSRF 정책
- 실제 Spring Security 설정
- 로그인 화면 구현 위치
- Next.js 라우팅 방식
- 인증 실패 응답과 브라우저 리다이렉트의 최종 분기
- 로그인 복귀 경로 저장 위치와 수명
- 안전한 내부 GET 경로 allowlist의 실제 라우트 문자열
- Open Redirect 검증 유틸 구현 방식
- 인증 컨텍스트에서 `memberId` 또는 동등 식별자를 꺼내는 principal 구조

## 위험과 제한

- 이번 작업은 설계 제안이며 구현이 아니다.
- API-001과 DATA-001은 Proposed 상태이며 AUTH-001에서 `Approved`로 변경하지 않는다.
- 인증 구현 방식 미확정으로 인해 Backend 구현 전 추가 결정이 필요할 수 있다.
- Frontend 라우팅 구조가 정해지면 복귀 경로 허용 목록을 재검토해야 한다.
- Open Redirect 방지 규칙은 코드와 테스트로 검증되기 전까지 위험 후보가 남아 있다.
- 브라우저 리다이렉트와 API `401 Unauthorized`의 최종 분기는 후속 인증 구현에서 확정해야 한다.

## 후속 작업

1. 사용자 검토로 AUTH-001 Proposed Authentication Design 승인 또는 수정
2. Backend 인증 설계 구현 전 세션/토큰, 쿠키, CSRF, Spring Security 설정 결정
3. Backend 보호 API 인증 컨텍스트, 소유자 조회, Open Redirect 검증 유틸 구현과 테스트 작성
4. Frontend 로그인 이동, 복귀 경로 처리, `AUTH_REQUIRED` 처리, POST 자동 재실행 방지 구현
5. QA 인증·인가·Open Redirect 검증 시나리오 작성
