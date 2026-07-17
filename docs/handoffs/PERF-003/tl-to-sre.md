# PERF-003 Tech Lead → Platform/SRE 인수인계

## 전달 목적

PERF-002에서 남은 container sampling 방식과 warm-up 적용 단위의 사용자 승인을 전달하고, 별도 Platform/SRE 작업의 일회성 로컬 성능 기준선 측정을 차단 해제한다.

## 대상 역할

- Platform/SRE

## 입력 문서

- `AGENTS.md`
- `infra/AGENTS.md`
- `docs/roles/platform-sre.md`
- `.agents/skills/platform-sre/SKILL.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/performance/PERF-001-local-baseline-decision-request.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

## 완료된 작업

- Event-based container sampling 세부 승인
- 각 읽기 route별 순차 warm-up 5회 승인
- PERF-001·002·003 결정 상태와 실행 게이트 동기화

## 사용 가능한 결과

- `before`·`mid`·`after` 3회 sampling 시점과 `timestamp_utc` 규칙
- CPU·memory, 누적 network·block counter, nullable PIDs와 restart count 집계 규칙
- 다섯 읽기 route별 warm-up 실행 시점과 집계 제외 규칙
- 측정 가능한 범위와 변경 불가 조건

## 인수 조건과 추적성

| 승인 결정 | 다음 측정 적용 |
| --- | --- |
| Container sampling | 각 warm 측정 단위의 첫 iteration 직전, 30회 중 15번째·10회 중 5번째 직후, 마지막 iteration 직후 |
| 표본 식별 | 각 표본에 `timestamp_utc`와 `before`·`mid`·`after` 기록 |
| CPU·memory | 가용 표본 수, 평균, 최댓값 |
| Network·block I/O | 누적 counter 최초값, 최종값, 차이만 기록하고 처리량으로 표현하지 않음 |
| PIDs | 제공될 때만 가용 표본 수·평균·최댓값, 미제공은 `null` |
| Restart | 측정 전후 값과 차이 |
| Warm-up | 다섯 읽기 route 각각의 측정 cohort 직전 순차 5회, 모든 집계 제외 |

## 확정된 결정

- PERF-002 D1-A~D6-A는 유지한다.
- Container sampling과 warm-up 적용 단위는 `Approved by PERF-003`다.
- 실행 게이트는 `Open for One-time Local Baseline Measurement`다.

## 미결정 사항

- SLO, latency 목표, 오류 예산과 regression threshold
- concurrency 2 이상, arrival rate와 장시간·고부하 실행
- 신규 도구, repository benchmark script와 관측성 stack
- 병목과 최적화

위 항목은 이번 측정의 시작을 막지 않지만 임의로 확정하거나 실행 범위에 포함하지 않는다.

## 승인 필요 항목

PERF-002·003 조건 그대로 일회성 측정을 수행하는 데 추가 제품·기술 승인은 필요하지 않다. 승인 조건 변경, 신규 도구, 고부하, 목표 설정과 최적화는 별도 승인이 필요하다.

## 지켜야 할 규칙

- 승인된 cohort, 반복 횟수, concurrency 1과 계산법을 변경하지 않는다.
- Warm-up 표본을 latency, status, 오류율 또는 표본 수에 포함하지 않는다.
- 인증 lifecycle과 구독 상태 변경 cohort에 별도 warm-up을 추가하지 않는다.
- PIDs 미제공을 0으로 대체하지 않는다.
- 누적 counter 차이를 처리량으로 표현하지 않는다.
- Cold start·health convergence를 HTTP elapsed 또는 warm container sampling과 합산하지 않는다.
- 실제 ID, credential, cookie, session ID, CSRF token, body와 raw log를 보존하지 않는다.

## 적용·실행 방법

1. 별도 Platform/SRE 작업 ID와 깨끗한 작업 트리를 준비한다.
2. PERF-002의 동일 commit·환경·QA fixture·reset·seed 조건을 고정한다.
3. PERF-003의 route별 warm-up과 event-based sampling을 적용해 일회성 측정을 실행한다.
4. 원시 record는 process memory에서 집계 후 폐기하고 집계 Markdown과 재현 근거만 보존한다.

## 다음 역할의 검증 포인트

- 각 warm 측정 단위에 `before`·`mid`·`after`가 정확히 한 번씩 존재하는가
- 30회는 15번째, 10회는 5번째 iteration 직후가 `mid`인가
- 각 container 표본에 `timestamp_utc`와 관측 위치가 있는가
- CPU·memory와 PIDs의 가용 표본 수·평균·최댓값 계산이 nullable 규칙을 지키는가
- Network·block I/O가 최초·최종·차이로만 보고되고 처리량으로 오표기되지 않았는가
- 각 읽기 route 측정 직전 warm-up 5회가 실행되고 모든 집계에서 제외됐는가
- 인증 lifecycle·구독 상태 변경에 별도 warm-up이 없는가
- raw record·log와 민감정보가 저장소에 남지 않는가

## QA 필요 여부

측정 실행 작업에서 기존 QA fixture와 smoke를 사전 검증으로 사용한다. 제품 동작 변경이 없으므로 PERF-003 승인 문서 자체의 별도 QA 산출물은 필요하지 않다.

## AI 리뷰에서 남은 확인 항목

PERF-003 PR의 CI와 AI 리뷰가 완료된 문서를 측정 입력으로 사용한다. 유효한 미해결 리뷰가 있으면 측정을 시작하지 않는다.

## 남은 위험과 주의 사항

- 세 번의 event-based 표본은 짧은 자원 peak를 놓칠 수 있다.
- 로컬 결과는 운영 capacity나 SLO가 아니다.
- PIDs는 환경에 따라 `null`일 수 있다.
- client·container 관측만으로 내부 병목을 확정하지 않는다.

## 다음 권장 작업

Platform/SRE가 별도 작업 ID로 일회성 로컬 성능 기준선을 측정하고 결과·제한·재현 조건을 보고한다.

## 완료 조건

- 승인된 route별 warm-up과 event-based sampling을 그대로 적용한다.
- 실제 측정값, 오류와 nullable 값을 삭제·대체하지 않는다.
- 집계 Markdown과 재현 근거만 저장소에 남긴다.
- SLO·threshold·병목·최적화를 확정하지 않는다.

## 중단 조건

- PERF-002 또는 PERF-003 승인 조건 변경이 필요함
- 제품 코드, 실행 설정, CI, dependency 또는 repository benchmark script 변경이 필요함
- SLO·threshold·고부하·신규 도구·병목·최적화를 함께 결정해야 함
- QA 회원 외 데이터 삭제, volume 삭제 또는 iteration별 reset이 필요함
- 원시 record·log나 민감정보의 커밋·장기 보존이 필요함
