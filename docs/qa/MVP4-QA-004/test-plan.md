# MVP4-QA-004 최종 상품 경험 브라우저 QA 계획

## 작업 범위

- 기준선: `6aa399e0632f93e9c146e6db741f386882471127`
- 역할/등급: QA Engineer / 일반
- 실행: `pawcycle-mvp4-final-qa` 격리 로컬 실행
- 브라우저: In-app Browser 실제 세션, desktop `1440x900`, mobile `375x812`
- 대상: MVP4 최종 상품 경험의 공개 탐색, 인증 사용자 여정, 관리자 읽기/수정 권한, 상호작용 저장 증거
- 제외: Product/Backend/Infra 코드 변경, API-only 판정, Production·AWS/RDS·외부 결제 실행

## 고정 fixture

`seed-final-product-fixtures.sql`은 기존 QA bootstrap member를 재사용하고, DOG pet 1건, 현재 public/on-sale DOG 상품·SKU·plan version, 일반 ACTIVE subscription과 SCHEDULED 일정, 재고 부족 HELD 일정과 add-on, 3건의 ONE_TIME 성공 주문, 3건의 SUBSCRIPTION 성공 이력 주문, reminder를 QA volume에만 생성한다. `verify-final-product-fixtures.ps1`가 브라우저 시작 전 결정적 조건을 검증한다.

## 시나리오 및 판정 기준

| ID | 시나리오 | 핵심 확인 |
| --- | --- | --- |
| A | 인증/세션 | 로그인, 새로고침·직접 URL·로그아웃, 사용자/관리자 경계 |
| B | 홈 추천 | 명시적 DOG pet 선택, pet 없는 자동 첫 pet 금지, impression/click request ID 귀속 |
| C | 카탈로그 | 검색, 구조화 filter, 정렬·pagination back/forward, invalid price boundary, raw query 미저장 |
| D | 비교 | 2~3개 비교, 최대 3개 제한, canonical facts 선표시 후 AI fallback |
| E | 상품 상세 | 옵션, related/complementary, 리뷰·리뷰 요약 fallback, A→B view attribution |
| F | 반려동물 | create/edit, nullable breed/weight, null clear, invalid input |
| G | reorder | 과거 성공 주문에서 재주문 시점·현재 상품 상태 확인 |
| H | order→subscription | eligible option과 prefill, 자동 subscription 생성 금지 |
| I | cycle suggestion | 추천 CTA가 suggestion만 수행하고 command를 실행하지 않음 |
| J | add-on | SCHEDULED schedule에 SET quantity 2, REMOVE, conflict/unavailable error |
| K | HELD | stock unavailable 상태 및 action UI가 server state를 따름 |
| L | reminder | 실제 schedule과 subscriptionId/local date 기반 route |
| M | admin | 일반 사용자 거부, ADMIN assignment readback, 안전한 기존 assignment 변경 |
| N | responsive/a11y | desktop/mobile overflow, focus/label/landmark/expanded state |

## 증거 수집

- Browser DOM snapshot은 각 action 직후 다시 취득한다.
- DB 검증은 `verify-final-product-interactions.ps1`로 요약 count, UUID 형식, 구조화 context, 추천 impression/click 매칭, DOG pet 귀속만 판정한다.
- 비밀번호·token·원시 payment 값·raw query는 로그와 보고서에 출력하지 않는다.
- 실패는 `FAIL`, 환경/브라우저 capability 부재는 `BLOCKED`, 미실행은 `NOT_RUN`으로 구분한다.
