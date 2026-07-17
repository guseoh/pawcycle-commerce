# PERF-004 Platform/SRE → Tech Lead 인수인계

## 전달 목적

PERF-004에서 유효하게 얻은 cold start 부분 결과와 warm 측정 중단 근거를 전달하고, 별도 재실행 승인 여부를 Tech Lead가 판단할 수 있게 한다.

## 다음 역할

- Tech Lead

## 입력 문서

- `docs/performance/PERF-004-local-baseline-results.md`
- `docs/reports/PERF-004/sre-report.md`
- `docs/handoffs/PERF-003/tl-to-sre.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/performance/PERF-001-local-baseline-decision-request.md`

## 사용 가능한 결과

- 기준 commit `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d`와 환경 fingerprint
- Cold start 3회 전체 경과: 25,917 ms, 25,155 ms, 25,293 ms
- 세 회차의 mysql, backend, frontend와 proxy health convergence
- Compose·QA fixture·reset·smoke 통과 근거
- Warm 측정이 첫 유효 표본 전에 중단된 실패 경계

이 결과는 cold start 부분 관측에만 사용할 수 있으며 완료된 PERF-004 기준선이 아니다.

## 미결정 사항과 승인 필요 항목

- `docs/performance/PERF-005-local-baseline-rerun-decision-request.md`의 A·B·C 중 어떤 경계를 승인할지
- 별도 PERF 작업에서 초기 QA state를 다시 구성하고 warm 전체 또는 cold·warm 전체를 새로 실행할지
- 새 실행을 승인한다면 실패 기록을 대체하지 않으면서 계측 래퍼를 사전검증할 경계를 어떻게 둘지

SLO, latency 목표, 오류 예산, regression threshold, 병목, 최적화와 신규 도구는 여전히 미승인이다.

## 검증 포인트

- Cold start 3회가 volume을 삭제하지 않는 `down`과 `up --detach --wait --wait-timeout 180`으로 수행됐는지
- service health convergence와 전체 경과가 분리됐는지
- warm HTTP·container 통계가 0이나 성공값으로 대체되지 않았는지
- seed와 warm-up 뒤 계측 실패가 숨겨지지 않았는지
- raw record, 실제 ID와 인증정보가 문서나 Git diff에 없는지
- commit·push·PR이 수행되지 않았는지
- task artifact validator, whitespace와 민감정보 패턴 검사가 통과했는지
- Warm wrapper 1이 PowerShell body parameter 구성 결함, wrapper 2가 container stats parsing 결함으로 증거에 맞게 분리됐는지
- 원본 래퍼가 남지 않아 미확정인 파일·행 번호와 switch 분기를 추측으로 채우지 않았는지

## 중단 조건

- 현재 state에서 추가 reset 또는 실패한 warm 단위 재실행이 필요함
- 실패 실행을 성공 재실행 결과로 대체해야 함
- PERF-002·003 조건, 제품 코드, 실행 설정, dependency 또는 CI 변경이 필요함
- raw record·log나 민감정보 보존이 필요함
- 신규 도구, SLO·threshold, 병목 또는 최적화 결정이 필요함

## 남은 위험과 주의 사항

- HTTP route, 인증 lifecycle, 상태 변경과 warm container 기준선은 없다.
- Cold start 값만으로 운영 capacity나 병목을 추론하면 안 된다.
- 사후 `Preserved` smoke는 통과했지만 QA subscription state는 승인된 empty 초기 상태가 아니다.
- 재실행은 현재 작업의 연속 실행이 아니라 명시적으로 승인된 새 실행 경계가 필요하다.
