# MVP4-FINAL-003 Backend Engineer 작업 보고서

## 작업

- 작업 ID: MVP4-FINAL-003
- 작업 등급: 일반
- 실행 구분: 저장소 변경
- 역할: Backend Engineer

## 목적

현재 RecommendationService의 날짜 기반 bounded exploration 정책을 변경하지 않고, exploration이 필요한 경우의 테스트가 정책에 맞게 결정적으로 검증하도록 보정한다.

## 결과 또는 증거

- `RecommendationServiceTests.aiReceivesOnlyPersonalizedTopNineWhenExplorationIsNeeded()`만 수정했다.
- AI 입력은 personalized product ID `1..9`인 9개인지 검증한다.
- 응답은 10개이며 `PERSONALIZED` 9개와 `EXPLORATION` 1개인지 검증한다.
- exploration product는 personalized 결과와 중복되지 않고 eligible 잔여 후보 `10` 또는 `11` 중 하나인지 검증한다. 최종 결과를 `1..10`으로 고정하지 않는다.
- 집중 테스트 1회 및 `--rerun-tasks` 반복 3회 통과.
- Backend `build -x test` 통과.
- 제품 로직, API, DB schema, Frontend, QA 산출물은 변경하지 않았다.

## 위험 또는 제한

- task 전용 MySQL 8.4에서 재실행한 로컬 Backend 전체 test는 365건 중 7건 실패했다. 모두 `ProductionAuthSmokeMemberBootstrapProcessTests`가 Windows child-process command-line 길이 제한(`CreateProcess error=206`)으로 시작되지 않은 환경 제한이며, 변경 테스트·제품 로직·DB 연결 실패는 아니다.
- MySQL 8.4 기준 전체 test의 최종 통과 여부와 Repository Validation은 Draft PR의 Linux CI에서 확인해야 한다.
- 로컬 test/build 실행에는 task 전용 disposable MySQL만 사용했으며 Production/AWS/RDS/Provider는 실행하지 않았다.
