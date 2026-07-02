# 프론트엔드 에이전트 규칙

## 책임

프론트엔드(Frontend) 영역은 Next.js 페이지, React 컴포넌트(Component), TypeScript 타입, API 연동, 사용자 입력 처리, UI 상태, 반응형 동작(Responsive Behavior), 접근성(Accessibility), 프론트엔드 테스트를 담당한다.

명시적으로 승인된 작업이 있기 전에는 프론트엔드 애플리케이션 코드를 생성하지 않는다.

## API 계약

- 승인된 API 계약(API Contract)만 사용한다.
- 백엔드 동작을 추측하지 않는다.
- 프론트엔드 작업에서 API 계약을 변경하지 않는다. 필요한 변경은 인수인계로 요청한다.
- 생성 타입(Generated Type)이나 공유 타입을 사용할 경우 승인된 계약과 일치시킨다.

## 비즈니스 규칙

다음 서버 비즈니스 규칙을 프론트엔드에 중복 구현하지 않는다.

- 가격(Pricing)
- 할인(Discount)
- 재고 가능 여부(Stock Availability)
- 구독 가능 여부(Subscription Eligibility)
- 결제 재시도 정책(Payment Retry Policy)
- 해지 정책(Cancellation Policy)

프론트엔드는 서버가 제공한 결과를 표시하고 사용자 편의를 위한 입력 검사는 할 수 있다. 비즈니스 결정의 기준은 백엔드다.

## 상태와 UX 동작

- 서버 상태(Server State)와 로컬 UI 상태(Local UI State)를 분리한다.
- 로딩(Loading), 빈 상태(Empty State), 오류(Error), 성공(Success), 재시도(Retry) 상태를 표현한다.
- 복구 가능한 오류 이후에는 가능한 범위에서 사용자 입력을 보존한다.
- 반응형 동작은 디자인 문서나 구현 메모에 명시한다.
- 승인 전에는 상태 관리 라이브러리(State Management Library)를 선택하지 않는다.

## 접근성

프론트엔드 작업은 시맨틱 HTML(Semantic HTML), 키보드 접근, 포커스 관리(Focus Management), 레이블(Label), 오류 안내, 색상 대비(Color Contrast), 모션 감소(Reduced Motion)를 고려한다.

## 테스트

프론트엔드 변경에는 위험도에 맞는 집중 테스트를 포함한다.

- 재사용 UI 동작에 대한 컴포넌트 테스트(Component Test)
- 중요한 상호작용에 대한 페이지 또는 흐름 테스트(Flow Test)
- UI 버그 수정에 대한 회귀 테스트(Regression Test)

프론트엔드 프로젝트가 존재하면 타입 검사(Type Check), 린트(Lint), 테스트, 빌드(Build)를 실행한다. 아직 프론트엔드 프로젝트가 없으면 검증이 문서 검토로 제한된다고 보고한다.

## 허용 경로

- `frontend/**`
- `docs/handoffs/**`
- 승인된 프론트엔드 관련 문서

## 금지 경로

- `backend/**`
- `infra/**`
- 승인 없는 API 계약 변경
- 서버 비즈니스 규칙 중복 구현
- 승인 없는 디자인 변경
- 승인 없는 의존성 추가
