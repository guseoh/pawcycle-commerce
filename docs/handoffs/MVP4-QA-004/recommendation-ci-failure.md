# MVP4-QA-004 Backend CI 실패 인수인계

## 작업 ID와 담당

- 작업 ID: MVP4-QA-004
- 요청 역할: Backend Engineer
- 발견 역할: QA Engineer
- 심각도: Medium — Backend CI를 차단하며 recommendation contract 영향은 확인이 필요하지만, 이번 authenticated Browser 흐름은 통과함

## 확인된 사실

- 대상 commit: `3870731565c65be2da02e8d576bfe922569333d8`
- GitHub Actions `Repository Validation` run `33220181352`에서 `Backend and MySQL validation`의 `Backend test` step이 실패했다.
- `365 tests completed, 1 failed`이며 실패 테스트는 `RecommendationServiceTests > aiReceivesOnlyPersonalizedTopNineWhenExplorationIsNeeded()`이다.
- 실패 위치는 `backend/src/test/java/com/pawcycle/backend/recommendation/RecommendationServiceTests.java:109`이다.
- `Application validation`은 Backend 결과를 집계해 실패했다. 다른 repository validation job들은 통과했다.
- 이 QA commit은 Backend product/test 파일을 변경하지 않았다.

## 재현 절차

1. 대상 branch `test/qa/MVP4-QA-004`의 commit `3870731565c65be2da02e8d576bfe922569333d8`를 checkout한다.
2. Backend validation과 동일한 Java/Gradle test command로 `RecommendationServiceTests`를 실행한다.
3. `aiReceivesOnlyPersonalizedTopNineWhenExplorationIsNeeded()`의 line 109 assertion 결과를 확인한다.

## 기대와 실제

- 테스트 기대: AI에 personalized top nine을 전달하고, 응답은 product IDs `1..10`을 포함한다.
- 실제: GitHub Actions에서 해당 assertion이 실패했다. 로그에는 assertion의 실제 ID 목록이 출력되지 않았다.

## 조사 메모와 의심 영역

현재 구현은 exploration 후보를 날짜/hash 기반으로 남은 후보에서 선택한다. 따라서 line 109의 고정 ID 기대가 구현의 의도와 일치하는지, 또는 테스트가 날짜에 따라 비결정적인지 Backend가 판정해야 한다. 이는 코드 읽기에 기반한 추론이며, 이번 QA에서 제품 결함으로 확정하지 않는다.

## 증거

- Run: https://github.com/guseoh/pawcycle-commerce/actions/runs/33220181352
- Job: https://github.com/guseoh/pawcycle-commerce/actions/runs/33220181352/job/99012444261
- 이번 Browser QA의 recommendation impression/click attribution과 DOG pet 귀속은 별도 disposable DB post-verification에서 통과했다.

## 요청 사항

- Backend Engineer가 고정 ID 기대와 날짜/hash exploration 정책의 정합성을 확인한다.
- 필요한 경우 테스트를 결정적으로 고치거나 제품 동작의 의도된 contract를 명확히 한 뒤 같은 validation 경로로 재검증한다.
- QA는 제품 코드와 기존 Backend 테스트를 수정하지 않는다.
