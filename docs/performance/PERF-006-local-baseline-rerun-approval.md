# PERF-006 로컬 기준선 전체 재실행 승인

## 문서 정보

- 작업 ID: `PERF-006`
- 역할: Tech Lead
- 상태: `Approved by PERF-006`
- 승인 선택: `B. Cold와 warm 전체 재실행`
- 실행 게이트: `Open for PERF-007 Full Local Baseline Rerun`
- 승인 주체: 사용자/Product Owner 겸 Tech Lead
- 승인 근거: PERF-006 작업 요청의 `PERF-005 결정: B`와 추가 조건 명시 승인
- 다음 실행 작업: `PERF-007`, Platform/SRE
- 선행 결정 요청: `docs/performance/PERF-005-local-baseline-rerun-decision-request.md`

이 문서가 PERF-005 선택지 B와 추가 실행 조건의 승인 원본이다. PERF-002의 reset·seed·cardinality 조건과 PERF-003의 warm-up·container sampling 조건은 변경하지 않는다. 실제 측정, 계측 래퍼 실행과 QA 상태 변경은 이 승인 기록 작업에서 수행하지 않는다.

## 승인 결정

사용자는 `B. Cold와 warm 전체 재실행`을 승인한다. PERF-007은 QA reset부터 cold와 warm 전체를 하나의 새 run으로 수행한다. 선택지 A의 부분 결과 연속안과 선택지 C의 측정 종료안은 선택하지 않는다.

- PERF-004 cold start 3회는 실제 측정된 사실 기반 부분 관측으로 보존한다.
- PERF-004 cold 값은 승인된 seed-before-cold 순서를 따르지 않았으므로 승인 cold 기준선이 아니다.
- PERF-004 cold 값은 PERF-007의 새 cold·warm 결과와 합산하거나 하나의 기준선으로 결합하지 않는다.
- PERF-004 warm HTTP·container 결과는 계속 `미완료·사용 불가`다.
- PERF-004 결과를 SLO, threshold, 병목, regression, 최적화 또는 운영 capacity 판단에 사용하지 않는다.

## 측정 전 필수 게이트

PERF-007은 다음 조건을 순서대로 확인하고 모두 통과한 경우에만 QA 상태 변경과 실제 측정을 시작한다.

1. 깨끗한 작업 트리에서 최신 `origin/main`의 실제 SHA를 기준 commit으로 고정해 결과에 기록한다.
2. OS, CPU·memory, Docker·Compose 버전, Docker Desktop 자원 할당, 전원 모드와 background workload를 환경 fingerprint로 기록한다.
3. PERF-004 기준 commit `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d` 이후 제품 코드 또는 실행 설정 변경 여부를 확인한다.
4. 제품 코드 또는 실행 설정 변경이 있으면 측정을 시작하지 않고 사용자 결정을 요청한다.
5. 실제 재실행용 수정 래퍼 아티팩트를 재현하고 request parameter 구성과 container stats parsing을 상태 변경 없는 로컬 입력으로 검증한다.
6. 래퍼 검증 실패 또는 아티팩트 재현 실패 시 endpoint 호출, QA reset·seed, cold start와 warm 측정을 시작하지 않는다.

PERF-006 작성 시점의 `origin/main`은 `54eb9230b20556cfdd01c443b8c2749fe0b119fe`이며 PERF-004 기준 이후 변경은 문서 경로로 제한됐다. 이 확인은 PERF-007의 기준 commit 고정을 대신하지 않으며 PERF-007 시작 시 다시 수행한다.

## 승인된 단일 전체 재실행 순서

필수 게이트 통과 뒤 PERF-007은 다음 순서를 한 번만 실행한다.

1. QA 회원 구독을 run 시작 상태로 한 번 reset한다.
2. reset 설정을 즉시 `false`로 복원한다.
3. 측정 집계에서 제외되는 seed 구독 1건을 생성하고 목록·상세로 확인한다.
4. Volume을 삭제하지 않는 `down`과 승인된 `up --detach --wait --wait-timeout 180`으로 cold start를 3회 수행한다.
5. 세 번째 cold start 뒤 네 service의 `healthy`를 확인한다.
6. Warm 측정 직전 측정 집계에서 제외되는 30초 안정화를 수행한다.
7. 다섯 읽기 route 각각에 cohort 직전 warm-up 5회를 적용한 뒤 승인된 읽기 측정을 수행한다.
8. 별도 warm-up 없이 인증 lifecycle 측정을 수행한다.
9. 별도 warm-up 없이 구독 상태 변경 측정을 수행한다.

PERF-002에서 승인된 반복 횟수, concurrency 1, think time 없음, 계산법, 실패 처리와 증거 보존 정책을 유지한다. PERF-003의 `before`·`mid`·`after` event-based container sampling과 nullable 규칙을 그대로 적용한다.

## 실패와 재실행 경계

- 상태 변경 이전의 래퍼 사전검증이 실패하면 측정을 시작하지 않는다.
- QA reset 또는 seed 생성 이후 실패하면 임의 reset, 성공 표본 대체 또는 재재실행 없이 즉시 중단한다.
- 실패, timeout, transport error와 expected status 불일치를 삭제하거나 성공 재실행 결과로 대체하지 않는다.
- 원시 record·log, 실제 ID, credential, cookie, session ID, CSRF token, header와 body를 저장소에 기록하거나 장기 보존하지 않는다.
- 승인 조건을 변경해야 완료할 수 있으면 PERF-007을 중단하고 사용자 결정을 요청한다.

## 변경하지 않은 승인과 미승인 범위

- PERF-002의 reset·seed·cardinality, cohort, 반복 횟수와 계산 규칙은 유지한다.
- PERF-003의 route별 warm-up과 container sampling 규칙은 유지한다.
- 제품 코드, 테스트, Compose, Nginx, Dockerfile, CI와 dependency 변경은 승인하지 않는다.
- Repository benchmark script, 신규 도구와 외부 service 도입은 승인하지 않는다.
- SLO, latency 목표, 오류 예산, regression threshold, 병목, 최적화와 운영 capacity는 미승인이다.
- Concurrency 2 이상, arrival rate, 장시간·고부하 실행은 미승인이다.

## 실행 게이트 승인 결과

PERF-005의 `Blocked Pending Rerun Decision`은 선택지 B 승인으로 해소됐다. 실행 게이트는 별도 Platform/SRE 작업 `PERF-007`의 한 번의 전체 재실행에 한해 `Open for PERF-007 Full Local Baseline Rerun`이다.

이 게이트는 실제 수정 래퍼 아티팩트의 상태 변경 없는 검증, 최신 기준 commit·환경 fingerprint 고정과 제품 코드·실행 설정 변경 부재 확인을 면제하지 않는다. 어느 하나라도 충족하지 못하면 측정을 시작하지 않는다.

## 중단 조건

- 최신 `origin/main`의 기준 commit 또는 원격 관계가 불명확함
- PERF-004 이후 제품 코드 또는 실행 설정 변경이 확인됨
- 실제 수정 래퍼 아티팩트를 재현할 수 없거나 상태 변경 없는 사전검증에 실패함
- PERF-002·003 승인 조건 변경이 필요함
- QA 회원 외 데이터 삭제, volume 삭제 또는 iteration별 reset이 필요함
- 상태 변경 이후 실패해 임의 reset 또는 재재실행이 필요함
- 제품 코드, 실행 설정, CI, dependency 또는 repository script 변경이 필요함
- Raw record·log나 민감정보 보존이 필요함
- SLO·threshold·고부하·신규 도구·병목·최적화를 함께 결정해야 함

## 승인 결과

PERF-005 선택지 B와 추가 조건은 `Approved by PERF-006`다. PERF-007은 새 기준 commit과 환경 fingerprint 아래에서 승인된 순서로 cold와 warm 전체를 하나의 run으로 측정할 수 있다. PERF-004 cold 부분 관측과 warm 미완료 결과의 상태는 변경하지 않는다.
