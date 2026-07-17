# PERF-005 Platform/SRE → Tech Lead 인수인계

## 전달 목적

PERF-004 cold 부분 결과와 warm 계측 래퍼 진단을 전달하고, QA 초기 상태와 별도 재실행 경계 A·B·C 중 하나의 사용자/Tech Lead 결정을 요청한다.

## 다음 역할

- 사용자/Product Owner 겸 Tech Lead

## 입력 문서

- `docs/performance/PERF-005-local-baseline-rerun-decision-request.md`
- `docs/reports/PERF-005/sre-report.md`
- `docs/performance/PERF-004-local-baseline-results.md`
- `docs/reports/PERF-004/sre-report.md`
- `docs/handoffs/PERF-004/sre-to-tl.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`

## 사용 가능한 결과

- PERF-004 기준 commit과 환경 fingerprint
- Seed-before-cold 순서 이탈이 있는 cold start 부분 관측 3회: 25,917 ms, 25,155 ms, 25,293 ms
- Warm 결과 `미완료·사용 불가` 경계
- Warm wrapper 1의 PowerShell body parameter 구성 결함 분류
- Warm wrapper 2의 container stats parsing·집계에서 scalar factor 보장 실패 유형 분류
- QA seed와 첫 route warm-up으로 최초 state가 소비된 사실
- 재실행 선택지 A·B·C와 Platform/SRE 권고안 B
- main 대상 Open·Ready PR #52

## 미결정 사항과 승인 필요 항목

다음 중 하나를 명시적으로 결정한다.

- A: PERF-004 cold를 순서 이탈이 있는 부분 관측으로만 보존하고, 별도 사용자 수용 아래 volume 보존 기동·healthy 확인·30초 안정화 뒤 warm 전체 실행. 승인된 하나의 완전한 기준선은 제공하지 않음
- B: QA reset → reset 비활성 복원 → seed 생성·확인 → cold 3회 → healthy 확인·30초 안정화 → warm 전체 순서의 완전 재실행
- C: PERF-004 cold 부분 결과와 warm 미완료만 보존하고 측정 종료

권고안은 B다. A는 seed-before-cold 순서 이탈을 수용하는 별도 사용자 결정이 있어야 하며 PERF-002·003 조건을 그대로 유지하는 기본안이 아니다. A와 B 모두 PERF-004 종료 상태인 volume 보존 `down`에서 volume을 삭제하지 않고 승인된 Compose 명령으로 기동해 네 service의 `healthy`를 확인하고, warm 측정 직전 측정 제외 30초 안정화를 수행해야 한다.

## 검증 포인트

- 두 오류의 실제 예외와 실패 표현식이 기록됐는지
- 제품 결함이나 Docker engine 실패로 근거 없이 분류하지 않았는지
- 원본 함수가 없어 미확정인 파일·행 번호와 switch 분기를 추측하지 않았는지
- Cold 부분 결과와 warm 사용 불가 상태가 분리됐는지
- Seed와 warm-up으로 최초 QA·warm state가 소비됐는지
- A·B·C의 조건과 위험, B 권고 조건이 비교 가능한지
- 순수 객체 최소 재현·수정 방향 객체 검증은 완료됐지만 실제 재실행용 수정 래퍼 아티팩트 검증은 미완료로 구분됐는지
- PERF-005에서 endpoint, reset, seed, warm-up과 성능 측정을 실행하지 않았는지
- Raw record와 민감정보가 Git diff에 없는지

## 중단 조건

- 사용자/Tech Lead가 A·B·C를 결정하지 않음
- A에서 seed-before-cold 순서 이탈을 사용자가 별도로 수용하지 않음
- 실제 재실행용 수정 래퍼 아티팩트를 재현할 수 없거나 request parameter 구성·container stats parsing의 상태 변경 없는 로컬 입력 검증을 통과하지 못함
- 재실행 중 상태 변경 이후 다시 실패함
- PERF-002·003 조건, 제품 코드, 실행 설정, CI, dependency 또는 repository script 변경이 필요함
- Raw record·log나 민감정보 보존이 필요함

## 남은 위험과 주의 사항

- 원본 process-memory 래퍼가 없어 line-by-line 수정 검증은 불가능하다.
- A는 환경이 같아도 승인된 하나의 완전한 기준선을 제공하지 못하며 cold와 warm을 결합할 수 없다.
- B는 승인 순서의 완전한 run을 제공하지만 비용과 state 소비가 더 크다.
- C는 warm 기준선이 없는 상태를 수용한다.
- SLO, threshold, 병목, 최적화와 capacity는 이번 결정 범위가 아니다.

## 다음 권장 작업

사용자/Tech Lead가 다음 형식으로 결정한다.

```text
PERF-005 결정: A / B / C
추가 조건: <없음 또는 승인 조건>
```

결정 전에는 재실행하지 않는다.
