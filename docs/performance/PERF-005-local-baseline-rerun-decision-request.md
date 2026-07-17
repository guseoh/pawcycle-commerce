# PERF-005 로컬 기준선 재실행 결정 요청

## 결정 상태

- 작업 ID: `PERF-005`
- 요청 역할: Platform/SRE
- 결정 역할: 사용자/Product Owner 겸 Tech Lead
- 상태: `Pending User/Tech Lead Decision`
- 실행 게이트: `Blocked Pending Rerun Decision`

이번 결정은 PERF-004의 cold 부분 결과 보존 범위와 별도 작업의 재실행 경계만 정한다. PERF-002의 reset·seed·cardinality 경계와 PERF-003의 warm-up·container sampling 조건은 변경하지 않는다.

## 결정이 필요한 이유

PERF-004에서 cold start 3회는 완료됐지만 warm 계측 래퍼가 첫 유효 표본 전에 실패했다. 두 번째 실패 전에 QA seed 1건과 `GET /products`의 집계 제외 warm-up 5회가 실행돼 승인된 최초 QA·warm 상태가 소비됐다. 같은 상태에서 임의로 계속 실행하면 하나의 비교 가능한 run으로 볼 수 없으므로 별도 실행 경계를 승인해야 한다.

## 보존할 PERF-004 증거

- 기준 commit: `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d`
- Cold start 전체 경과: 25,917 ms, 25,155 ms, 25,293 ms
- service별 health convergence와 환경 fingerprint
- Warm HTTP·container 결과: `미완료·사용 불가`
- 중단 직전 state: QA seed 생성 완료, 첫 route의 집계 제외 warm-up 5회 완료, 첫 measured iteration과 승인된 `before` sample은 완료되지 않음
- 종료 상태: Docker stack은 volume을 삭제하지 않는 `down`으로 종료
- 보존 정책: raw record·log, 실제 ID와 민감정보 없음

## 계측 래퍼 진단 결과

| 오류 | 실제 증거 | 분류 |
| --- | --- | --- |
| GET body | `System.Net.ProtocolViolationException: Cannot send a content-body with this verb-type.` | `[string]$Body = $null`이 빈 문자열로 바인딩되고 GET에 body를 첨부한 PowerShell 함수·매개변수·HTTP parameter 구성 결함 |
| Container size | `Convert-SizeToBytes`의 `$number * $factor`에서 `[System.Object[]]` `op_Multiply` 실패 | scalar factor를 보장하지 못한 container stats parsing 결함 |

원본 래퍼는 process memory에서만 실행됐고 현재 session과 지속 history에 남지 않았다. 따라서 원본 파일·행 번호와 `$factor` 배열을 만든 정확한 switch 분기 조합은 `원인 미확정`으로 남긴다. 상태 변경 없는 순수 PowerShell 객체 최소 재현으로 두 type·연산 결함과 수정 방향만 확인했다. 제품 endpoint, Docker engine 또는 성능 병목의 결함이라는 증거는 없다.

## 변경하지 않는 승인 조건

- Run 시작 전 QA 회원 구독 reset 1회, reset 설정 즉시 `false` 복원
- 읽기 측정에서 제외되는 seed 구독 1건 생성
- 상태 변경 iteration별 `subscription_count_before` 기록과 증가 cardinality 분리
- 각 읽기 route의 cohort 직전 순차 warm-up 5회
- 인증 lifecycle과 구독 상태 변경에는 별도 warm-up 없음
- 각 warm 단위의 `before`·`mid`·`after` event-based container sampling
- 실패·timeout·unexpected status 비대체, raw record·민감정보 비보존

## 선택지 비교

| 선택지 | Cold 처리 | QA 초기 상태 | 실행 범위 | 선택 조건 | 주요 위험 |
| --- | --- | --- | --- | --- | --- |
| A. Cold 유지 + warm 전체 재실행 | PERF-004 3회 유지, 재측정 안 함 | Run 시작 시 QA 회원 구독 1회 reset → 즉시 reset 비활성 복원 → seed 1건 재생성 | 다섯 읽기 route warm-up부터 인증 lifecycle·구독 상태 변경까지 warm 전체 | 기준 commit과 환경 fingerprint가 PERF-004와 동일하고 래퍼가 상태 변경 없는 검증을 통과함 | 환경 동일성 판단이 틀리면 cold와 warm 비교 가능성 훼손 |
| B. Cold와 warm 전체 재실행 | Cold 3회부터 새로 측정 | A와 동일 | Cold 3회, 안정화, warm 전체 | commit, Docker 자원, 전원 모드, background workload가 달라졌거나 하나의 완전한 run이 필요함 | 실행 시간과 QA state 소비 증가, 새 run도 실패하면 다시 중단 필요 |
| C. 기준선 측정 종료 | PERF-004 cold 부분 결과만 보존 | 변경 없음 | 추가 측정 없음 | 추가 측정 가치보다 재실행 비용·위험이 큼 | Warm 기준선이 영구적으로 없고 route·인증·상태 변경 비교 불가 |

## 선택지 A 상세

1. PERF-004의 기준 commit과 전체 환경 fingerprint가 동일한지 확인한다.
2. 수정된 래퍼의 body 첨부 조건과 size factor 반환 type을 endpoint·Docker·DB를 사용하지 않는 로컬 입력으로 먼저 검증한다.
3. 새 작업 ID에서 QA 회원 구독을 run 시작 상태로 한 번 초기화한다.
4. reset 설정을 즉시 비활성 상태로 복원하고 확인한다.
5. 읽기 측정에서 제외되는 seed 구독 1건을 새로 생성한다.
6. 다섯 읽기 route 각각의 warm-up 5회부터 30회 측정을 순서대로 새로 시작한다.
7. 인증 lifecycle 30회와 구독 상태 변경 10회를 PERF-002·003 조건 그대로 이어서 실행한다.
8. 재실행 중 상태 변경 이후 다시 실패하면 임의 reset 또는 재재실행하지 않고 중단한다.

## 선택지 B 상세

다음 중 하나라도 해당하면 B를 선택한다.

- 기준 commit이 `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d`와 다름
- Docker Desktop CPU·memory, Docker·Compose version, 전원 모드 또는 background workload가 PERF-004와 다름
- Cold와 warm을 하나의 연속된 완전한 run으로 통일해야 함

B도 QA 초기화와 reset 비활성 복원, seed 1건, 상태 변경 없는 래퍼 사전검증을 먼저 수행한다. 새 run은 cold start 3회부터 시작하며 PERF-004 cold 값과 합산하지 않는다.

## 선택지 C 상세

PERF-004 cold 3회를 부분 관측으로만 보존하고 warm 기준선은 `미완료·사용 불가`로 확정한다. 추가 endpoint 호출, QA state 변경과 성능 측정은 수행하지 않는다. 이 경우 PERF-004 결과를 SLO, threshold, 병목, 최적화 또는 capacity 판단에 사용하지 않는다.

## Platform/SRE 권고안

`A. Cold 결과 유지 + warm 전체 재실행`을 권고한다. Cold 3회가 승인 조건으로 완료됐고 실패는 warm client-side harness에서 첫 유효 표본 전에 발생했기 때문이다.

단, 다음 조건을 모두 만족해야 A를 실행할 수 있다.

- 기준 commit과 환경 fingerprint가 PERF-004와 동일함
- 정확한 래퍼 직접 원인이 확인됐고 수정 로직이 상태 변경 없는 입력 검증을 통과함
- Warm 재실행은 새 작업 ID에서 한 번만 시작함
- QA 회원 구독을 run 시작 상태로 한 번 초기화하고 reset을 즉시 비활성화한 뒤 seed 1건을 재생성함
- 다섯 읽기 route warm-up부터 인증 lifecycle과 구독 상태 변경까지 warm 전체를 처음부터 실행함
- 상태 변경 이후 다시 실패하면 임의 reset 또는 재재실행하지 않고 중단함

조건 하나라도 충족하지 않으면 A를 시작하지 않고 B 또는 C를 다시 결정한다.

## 사용자/Tech Lead 결정 요청

다음 중 하나를 명시적으로 승인한다.

```text
PERF-005 결정: A / B / C
추가 조건: <없음 또는 승인 조건>
```

결정 전에는 reset, seed, warm-up, cold start와 warm 성능 측정을 실행하지 않는다.

## 미승인 범위

- SLO, latency 목표, 오류 예산과 regression threshold
- 병목, 최적화와 운영 capacity
- 신규 도구, dependency, 외부 service와 repository benchmark script
- 제품 코드, 테스트, Compose, Nginx, Dockerfile과 CI 변경
- raw record·log와 민감정보 보존
