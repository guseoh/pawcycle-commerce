# PERF-005 로컬 기준선 재실행 결정 요청

## 결정 상태

- 작업 ID: `PERF-005`
- 요청 역할: Platform/SRE
- 결정 역할: 사용자/Product Owner 겸 Tech Lead
- 상태: `Approved by PERF-006`
- 선택: `B. Cold와 warm 전체 재실행`
- 실행 게이트: `Open for PERF-007 Full Local Baseline Rerun`
- 승인 원본: `docs/performance/PERF-006-local-baseline-rerun-approval.md`

이번 결정은 PERF-004의 cold 부분 결과 보존 범위와 별도 작업의 재실행 경계만 정한다. PERF-002의 reset·seed·cardinality 경계와 PERF-003의 warm-up·container sampling 조건은 변경하지 않는다.

## 결정이 필요한 이유

PERF-004에서 cold start 3회는 실제 측정됐지만 QA seed가 cold 뒤에 생성돼 승인된 seed-before-cold 순서를 따르지 않았다. 이후 warm 계측 래퍼가 첫 유효 표본 전에 실패했고, 두 번째 실패 전에 QA seed 1건과 `GET /products`의 집계 제외 warm-up 5회가 실행돼 최초 QA·warm 상태도 소비됐다. Cold 값은 부분 관측으로 보존할 수 있지만 승인된 하나의 완전한 기준선 구성요소로 확정할 수 없으므로 별도 실행 경계를 승인해야 한다.

## 보존할 PERF-004 증거

- 기준 commit: `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d`
- Cold start 전체 경과 부분 관측: 25,917 ms, 25,155 ms, 25,293 ms. Seed-before-cold 순서 이탈로 승인 cold 기준선이 아님
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

원본 래퍼는 process memory에서만 실행됐고 현재 session과 지속 history에 남지 않았다. 따라서 원본 파일·행 번호와 `$factor` 배열을 만든 정확한 switch 분기 조합은 `원인 미확정`으로 남긴다. 완료된 검증은 상태 변경 없는 순수 PowerShell 객체로 body binding과 배열 곱셈을 재현하고 body 미첨부 조건과 scalar factor라는 수정 방향을 확인한 것까지다. 실제 재실행용 수정 래퍼 아티팩트의 request parameter 구성과 container stats parsing 검증은 미완료다. 제품 endpoint, Docker engine 또는 성능 병목의 결함이라는 증거는 없다.

## 변경하지 않는 승인 조건

- 완전한 run의 승인 순서: QA 회원 구독 reset 1회 → reset 설정 즉시 `false` 복원 → 읽기 측정에서 제외되는 seed 구독 1건 생성·확인 → cold 3회 → 네 service `healthy` 확인 → 측정 제외 30초 안정화 → warm 전체
- 상태 변경 iteration별 `subscription_count_before` 기록과 증가 cardinality 분리
- 각 읽기 route의 cohort 직전 순차 warm-up 5회
- 인증 lifecycle과 구독 상태 변경에는 별도 warm-up 없음
- 각 warm 단위의 `before`·`mid`·`after` event-based container sampling
- 실패·timeout·unexpected status 비대체, raw record·민감정보 비보존

## 선택지 비교

| 선택지 | Cold 처리 | QA 초기 상태 | 실행 범위 | 선택 조건 | 주요 위험 |
| --- | --- | --- | --- | --- | --- |
| A. Cold 관측 보존 + warm 전체 실행 | PERF-004 3회를 순서 이탈이 있는 부분 관측으로만 보존, 재측정 안 함 | Run 시작 시 QA 회원 구독 1회 reset → 즉시 reset 비활성 복원 → seed 1건 재생성·확인 | Volume 보존 기동·healthy 확인·30초 안정화 뒤 warm 전체 | Seed-before-cold 순서 이탈을 수용한다는 별도 사용자 결정과 실제 수정 래퍼 아티팩트의 상태 변경 없는 검증 통과 | 승인된 하나의 완전한 기준선을 만들 수 없고 cold와 warm을 결합할 수 없음 |
| B. Cold와 warm 전체 재실행 | PERF-004 cold는 부분 관측으로만 보존하고 cold 3회부터 새로 측정 | QA reset → reset 비활성 복원 → seed 1건 생성·확인 | Cold 3회 → healthy 확인·30초 안정화 → warm 전체 | PERF-002·003 승인 순서를 유지해 하나의 완전한 기준선이 필요함 | 실행 시간과 QA state 소비 증가, 새 run도 실패하면 다시 중단 필요 |
| C. 기준선 측정 종료 | PERF-004 cold 부분 관측만 보존 | 변경 없음 | 추가 측정 없음 | 추가 측정 가치보다 재실행 비용·위험이 큼 | Warm 기준선이 영구적으로 없고 route·인증·상태 변경 비교 불가 |

## 선택지 A 상세

1. Seed-before-cold 순서 이탈이 있는 PERF-004 cold 관측과 새 warm 결과를 하나의 승인 기준선으로 결합하지 않는 부분 결과 연속안임을 사용자가 별도로 수용한다.
2. 실제 재실행용 수정 래퍼 아티팩트의 request parameter 구성과 container stats parsing을 상태 변경 없는 로컬 입력으로 검증한다. 검증에 실패하거나 아티팩트를 재현할 수 없으면 측정을 시작하지 않는다.
3. PERF-004 기준 commit과 환경 fingerprint가 동일한지 확인한다.
4. PERF-004 종료 상태인 volume 보존 `down`에서 volume을 삭제하지 않고 승인된 Compose `up --detach --wait --wait-timeout 180`으로 기동하고 네 service의 `healthy`를 확인한다.
5. 새 작업 ID에서 QA 회원 구독을 run 시작 상태로 한 번 초기화하고 reset 설정을 즉시 비활성 상태로 복원한다.
6. 읽기 측정에서 제외되는 seed 구독 1건을 새로 생성하고 목록·상세로 확인한다.
7. 네 service가 계속 `healthy`인지 확인하고 warm 측정 직전 측정 제외 30초 안정화를 수행한다.
8. 다섯 읽기 route 각각의 warm-up 5회부터 30회 측정을 순서대로 새로 시작한다.
9. 인증 lifecycle 30회와 구독 상태 변경 10회를 PERF-002·003 조건 그대로 이어서 실행한다.
10. 재실행 중 상태 변경 이후 다시 실패하면 임의 reset 또는 재재실행하지 않고 중단한다.

## 선택지 B 상세

B는 PERF-002·003 승인 순서를 그대로 지키는 완전 재실행안이며 Platform/SRE 기본 권고안이다.

1. 실제 재실행용 수정 래퍼 아티팩트의 request parameter 구성과 container stats parsing을 상태 변경 없는 로컬 입력으로 검증한다. 검증에 실패하거나 아티팩트를 재현할 수 없으면 측정을 시작하지 않는다.
2. PERF-004 종료 상태인 volume 보존 `down`에서 volume을 삭제하지 않고 승인된 Compose `up --detach --wait --wait-timeout 180`으로 기동하고 네 service의 `healthy`를 확인한다.
3. QA 회원 구독을 한 번 reset하고 reset 설정을 즉시 비활성 상태로 복원한다.
4. 측정에서 제외되는 seed 구독 1건을 생성하고 목록·상세로 확인한다.
5. Volume을 삭제하지 않는 `down`과 `up --detach --wait --wait-timeout 180`으로 cold start 3회를 수행한다.
6. 세 번째 cold start 뒤 네 service의 `healthy`를 확인하고 warm 측정 직전 측정 제외 30초 안정화를 수행한다.
7. 다섯 읽기 route의 warm-up·측정, 인증 lifecycle과 구독 상태 변경까지 warm 전체를 승인 조건 그대로 실행한다.
8. 새 run은 PERF-004 cold 부분 관측과 합산하지 않는다. 상태 변경 이후 다시 실패하면 임의 reset 또는 재재실행하지 않고 중단한다.

## 선택지 C 상세

PERF-004 cold 3회를 부분 관측으로만 보존하고 warm 기준선은 `미완료·사용 불가`로 확정한다. 추가 endpoint 호출, QA state 변경과 성능 측정은 수행하지 않는다. 이 경우 PERF-004 결과를 SLO, threshold, 병목, 최적화 또는 capacity 판단에 사용하지 않는다.

## Platform/SRE 권고안

`B. Cold와 warm 전체 재실행`을 권고한다. PERF-004 cold 값은 사실 기반 관측이지만 seed-before-cold 승인 순서를 따르지 않아 새 warm과 결합 가능한 승인 cold cohort가 아니다. B만 `QA reset → reset 비활성 복원 → seed 생성·확인 → cold 3회 → healthy 확인·30초 안정화 → warm 전체` 순서를 지켜 하나의 완전한 기준선을 만들 수 있다.

B도 다음 재실행 전 게이트를 충족해야 한다.

- 관찰된 body binding과 배열 곱셈 결함 유형은 순수 객체로 재현됐고, body 미첨부 조건과 scalar factor라는 수정 방향은 객체로 검증됐음을 유지함
- 원본 파일·행·switch 분기는 증거 부족으로 미확정임을 유지함
- 실제 재실행용 수정 래퍼 아티팩트의 request parameter 구성과 container stats parsing이 상태 변경 없는 로컬 입력 검증을 통과함
- 검증 전 실제 수정 래퍼 아티팩트의 로컬 경로 또는 이름, 적용 가능한 version·commit과 SHA-256을 기록하고 검증 명령·비민감 입력 설명·결과를 같은 식별자에 연결함
- 실제 측정 직전에 아티팩트 SHA-256이 검증한 값과 같은지 다시 확인하고 다르면 측정을 시작하지 않음
- 해당 검증에 실패하거나 아티팩트를 재현할 수 없으면 측정을 시작하지 않음
- 재실행은 새 작업 ID에서 한 번만 시작하고 상태 변경 이후 다시 실패하면 임의 reset 또는 재재실행하지 않음

A는 사용자가 seed-before-cold 순서 이탈을 별도로 수용할 때만 선택할 수 있는 부분 결과 연속안이며 PERF-002·003 조건을 그대로 유지하는 기본 권고안이 아니다. C는 추가 측정을 종료할 때 선택한다.

## 사용자/Tech Lead 승인 결과

PERF-006에서 `B. Cold와 warm 전체 재실행`이 승인됐다. 별도 Platform/SRE 작업 `PERF-007`은 다음 조건을 모두 충족한 뒤 승인 순서에 따른 단일 전체 재실행을 시작할 수 있다.

- 작업 시작 시 최신 `origin/main`의 실제 SHA를 기준 commit으로 고정하고 환경 fingerprint를 기록한다.
- `git status --short --branch`로 작업 트리가 깨끗하고 `HEAD`가 고정한 `origin/main` SHA와 일치하는지 확인한다.
- PERF-004 기준 이후 제품 코드 또는 실행 설정 변경이 확인되면 측정을 시작하지 않고 사용자 결정을 요청한다.
- 실제 재실행용 수정 래퍼 아티팩트의 request parameter 구성과 container stats parsing이 상태 변경 없는 로컬 입력 검증을 통과해야 한다.
- 검증·실행할 아티팩트의 로컬 경로 또는 이름, 적용 가능한 version·commit과 SHA-256, 검증 명령·비민감 입력 설명을 기록하고 측정 직전 동일 SHA-256을 재확인한다.
- 검증 실패 또는 아티팩트 재현 실패 시 reset, seed, cold start와 warm 측정을 시작하지 않는다.
- 상태 변경 이후 실패하면 임의 reset 또는 재재실행 없이 중단한다.

상세 승인 범위와 실행 게이트는 `docs/performance/PERF-006-local-baseline-rerun-approval.md`를 따른다. PERF-004 cold 값은 계속 순서 이탈이 있는 부분 관측으로만 보존하며 PERF-007 결과와 합산하지 않는다.

## 미승인 범위

- SLO, latency 목표, 오류 예산과 regression threshold
- 병목, 최적화와 운영 capacity
- 신규 도구, dependency, 외부 service와 repository benchmark script
- 제품 코드, 테스트, Compose, Nginx, Dockerfile과 CI 변경
- raw record·log와 민감정보 보존
