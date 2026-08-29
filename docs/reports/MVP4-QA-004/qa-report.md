# MVP4-QA-004 최종 상품 경험 QA 보고서

## 목적

승인된 MVP4 최종 상품 경험의 authenticated Browser 흐름과 상호작용/state 저장 결과를 새 disposable local QA 환경에서 독립 검증한다.

## 판정 요약

- Browser QA 실행 기준 `main` SHA: `6aa399e0632f93e9c146e6db741f386882471127`
- PR 최종 통합 기준 `main` SHA: `52a410623a3ead5e8daa8a4e036ef36ae3bb75cb`
- 실행 브랜치: `test/qa/MVP4-QA-004`
- 실행 환경: `pawcycle-mvp4-final-qa` Compose project, disposable volume `pawcycle-mvp4-final-qa-mysql-data`
- 브라우저: 실제 authenticated In-app Browser session, desktop `1440x900`, mobile `375x812`
- Browser QA Gate: `GREEN`
- Repository merge gate: `GREEN` — Backend handoff가 PR #249에서 테스트-only correction으로 해소된 뒤 최신 `main`을 통합한 Repository Validation #1392가 전체 통과했다.
- 시나리오 수: 14
- PASS: 14 / FAIL: 0 / BLOCKED: 0 / NOT_RUN: 0

실패한 직전 실행의 증거는 사용하지 않았다. 이번 Browser 결과는 새 volume에 fixture를 다시 준비하고, fixture pre-verification 후 authenticated Browser QA와 DB post-verification을 처음부터 실행한 결과다.

PR #249는 Recommendation 제품 로직을 변경하지 않고 실패한 테스트 기대값만 현재 bounded exploration 정책에 맞게 보정했다. 따라서 #249 병합 후 Browser QA를 재실행했다고 소급 표현하지 않으며, 기존 Browser 실행 증거와 최신 통합 branch의 CI 증거를 함께 사용한다.

## 시나리오 결과

| ID | 결과 | 확인 내용 |
| --- | --- | --- |
| A | PASS | 로그인 session, 보호 URL 접근, 로그아웃 및 세션 경계 |
| B | PASS | 명시적 DOG 선택, 자동 첫 pet 금지, personalized recommendation impression/click 귀속 |
| C | PASS | 검색·DOG/category/subcategory/brand/facet/filter, 정렬·pagination·back/forward, invalid price boundary, raw query 미저장 |
| D | PASS | 2~3개 비교, 4번째 차단, canonical facts 우선 및 fallback |
| E | PASS | 상품 상세 옵션, related/complementary, review summary fallback, 상세 간 view 귀속 |
| F | PASS | CAT create/edit, breed·weight 저장, null clear, 음수 weight validation 및 저장 차단 |
| G | PASS | 과거 성공 주문 기반 reorder 시점, 상품 상태, cart 이동 |
| H | PASS | order→subscription pet/cycle prefill, 시작 화면에서 자동 생성 없음 |
| I | PASS | cycle suggestion은 선택값만 변경하고 command를 실행하지 않음 |
| J | PASS | SCHEDULED add-on SET quantity 2, REMOVE, base 구성 conflict 오류 |
| K | PASS | 재고 부족 HELD 상태, server-authoritative availableActions, 허용되지 않은 pause/skip 숨김 |
| L | PASS | 실제 scheduled reminder가 올바른 subscription detail로 routing |
| M | PASS | USER의 admin 접근 거부 및 disposable DB에서 ADMIN catalog fixture readback |
| N | PASS | desktop/mobile landmark·skip link·menu expanded state, mobile 수평 overflow 없음 |

## 결과 또는 증거

`verify-final-product-interactions.ps1`가 다음을 모두 guard assertion으로 확인했다.

- interaction summary: `PRODUCT_IMPRESSION=94`, `PRODUCT_VIEW=3`, `SEARCH=1`, `FILTER=3`, `RECOMMENDATION_IMPRESSION=35`, `RECOMMENDATION_CLICK=2`
- interaction event ID UUID v4 형식
- SEARCH/FILTER context의 구조화된 pet/sort context와 raw query 미저장
- recommendation impression/click의 동일 request/product attribution 및 DOG pet 귀속
- CAT fixture profile의 breed/weight null clear 및 DOG fixture 불변
- active subscription 2건 유지, normal SCHEDULED 일정의 add-on 제거, HELD 일정의 재고 부족 및 add-on 보존
- order→subscription 화면에서 실제 subscription 생성 없음, historical QA order 6건 보존
- reminder의 schedule 및 `+2일` local date 연결
- 최초 PR CI `Repository Validation` run `33220181352`는 `RecommendationServiceTests > aiReceivesOnlyPersonalizedTopNineWhenExplorationIsNeeded()` 단일 실패로 Backend/Application validation이 실패했다.
- Backend handoff 후 `MVP4-FINAL-003` / PR #249에서 제품 로직 변경 없이 해당 테스트를 현재 날짜 기반 bounded exploration 계약에 맞게 보정했고, PR #249 Repository Validation #1390이 통과했다.
- PR #249 병합 및 자동 기록 커밋까지 반영된 `main` `52a410623a3ead5e8daa8a4e036ef36ae3bb75cb`을 QA branch에 일반 merge로 통합했고, 통합 HEAD `4098973363186ba08e69b0a25c8dc761cae8bb51`의 Repository Validation #1392가 Backend/MySQL test+build, Frontend, Harness, Production contract lanes와 Application validation까지 전체 통과했다.

## 결함 및 handoff

Browser QA 범위에서 재현 가능한 제품 결함은 발견되지 않았다. 최초 PR CI에서 확인된 Backend 테스트 실패는 [MVP4-QA-004 Backend handoff](../../handoffs/MVP4-QA-004/recommendation-ci-failure.md)로 전달했고, `MVP4-FINAL-003` / PR #249에서 test expectation 문제로 보정·병합되어 해소됐다.

## 위험·제한

- QA harness는 credential 값 대신 credential key 존재 여부만 확인하고, non-secret 환경값은 지정 키의 기대값만 assertion한다.
- credential 값, 전체 container environment, raw payment 값, raw query는 출력·보고서·PR에 기록하지 않았다.
- QA-only reminder flag는 disposable Compose overlay에만 적용했다.
- Production, AWS, RDS, Secret/실제 Provider 실행은 없었다.
- `pawcycle-mvp4-final-qa` containers/network와 `pawcycle-mvp4-final-qa-mysql-data` volume은 완료 후 제거했다. shared local/customer volume은 건드리지 않았다.
- 이 QA Gate Green은 `MVP4 Product Complete` 또는 `Production Verified`를 의미하지 않는다.
- Toss Test provider core Commerce E2E 등 별도 승인된 후속 gate는 별도로 검증한다.
