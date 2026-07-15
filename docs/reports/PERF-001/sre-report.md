# PERF-001 Platform/SRE 작업 보고서

## 작업 목적

PR #48 병합 후 최신 첫 MVP의 로컬 관측 가능 상태를 실제 설정과 실행 표면에서 조사하고, 성능 측정이나 최적화 전에 사용자 승인이 필요한 D1~D6 결정 요청과 재현 절차 초안을 작성한다.

## 입력 문서

- PR #48 병합 commit `04abf543902c3c1a9260fa47825a842c3b692e22`
- 최신 기준 commit `c7179ca28d7b8628635c9e06973aded940eb15ea`
- `docs/reports/FOUNDATION-005/tl-report.md`
- `docs/qa/FOUNDATION-006/first-mvp-follow-up-test-plan.md`
- `docs/reports/FOUNDATION-006/qa-report.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`
- `docs/performance/README.md`
- `docs/performance/experiment-template.md`
- 현재 Compose, Nginx, Dockerfile, Backend·Frontend 설정, smoke와 CI workflow

## 시작 기준 상태

- Git root와 origin은 PawCycle 저장소와 `https://github.com/guseoh/pawcycle-commerce.git`로 일치했다.
- 시작 branch는 병합 완료된 `test/qa`, 작업 트리는 깨끗했고 열린 PR은 없었다.
- fetch 후 `origin/main`은 PR #48 병합 commit과 후속 기록을 포함한 `c7179ca`였다.
- 로컬 `main`을 `git pull --ff-only origin main`으로 fast-forward했고 `main == origin/main`을 확인했다.
- 기존 로컬·원격 `ops/sre`는 열린 PR 없이 관련 PR이 모두 병합됐고 최신 `main`에서 산출물이 삭제되지 않았음을 확인한 뒤 정리했다.
- 최신 `main`에서 새 `ops/sre`를 생성했다.

## 조사 범위

- `infra/local-integration/compose.yaml`
- `infra/local-integration/nginx.conf`
- `infra/local-integration/backend.Dockerfile`
- `infra/local-integration/frontend.Dockerfile`
- `infra/local-integration/smoke.ps1`
- `backend/src/main/resources/application*.properties`
- `frontend/next.config.ts`, `frontend/package.json`
- `.github/workflows/validate-conventions.yml`
- 실행 중인 local-integration의 non-secret inspect·health·stats·log availability

## 주요 결과

- 네 service는 healthcheck와 dependency ordering을 제공하며 조사 시 모두 healthy였다.
- healthcheck는 MySQL `SELECT 1`, Backend 공개 상품 API, Frontend·proxy 상품 화면을 사용한다. 전용 liveness/readiness metric은 없다.
- Nginx 기본 access log는 method·request·status를 제공하지만 effective log format에 request elapsed time이 없다.
- Backend에는 Actuator, Micrometer, Prometheus, tracing과 요청별 timing 설정이 없다.
- Docker `json-file` log, inspect health·restart와 stats CPU·memory·network·block I/O·PIDs는 현재 도구로 볼 수 있다.
- Compose에 container CPU·memory limit이 없어 Docker Desktop 전체 할당과 background workload가 결과에 직접 영향을 준다.
- smoke는 정확한 MVP endpoint 순서를 제공하지만 latency를 수집하지 않고 Full 반복은 구독 데이터를 증가시킨다.
- CI는 test·build·lint와 MySQL health를 검증하지만 runtime 성능 artifact와 threshold는 없다.
- 현재 최소 기준선은 PowerShell client elapsed와 Docker 관측을 결합할 수 있으나 proxy·Backend·DB·browser 구간 분해는 할 수 없다.

조사 중 단일 `docker stats --no-stream` snapshot을 도구 표면 확인에 사용했지만 workload와 sampling 조건이 통제되지 않아 기준선 결과로 보존하거나 해석하지 않았다.

## 변경 범위

- `docs/performance/PERF-001-local-baseline-decision-request.md`
- `docs/reports/PERF-001/sre-report.md`
- `docs/handoffs/PERF-001/sre-to-tl.md`

## 변경하지 않은 범위

- Backend·Frontend 제품 코드와 테스트 코드
- Docker Compose, Nginx, Dockerfile과 CI 동작
- dependency, 외부 service, Secret과 운영 배포
- 실제 기준선·고부하·장시간 부하 측정
- SLI·SLO, 성능 목표와 threshold 승인
- FOUNDATION-006 미실행 QA 위험 재검증
- 원시 log와 request record 저장

## 결정 요청 결과

- D1: Frontend 경계, 공개·인증·읽기, 상태 변경의 세 cohort 분리 추천
- D2: latency 분포, status·오류 비율, cold health와 container 자원 SLI candidate 추천; 목표값은 미결정
- D3: cold 3회, warm-up 5회, 읽기 30회, 쓰기 10회, concurrency 1 추천; 모두 승인 전 미실행
- D4: method·정규화 route·status·client elapsed allowlist와 민감정보 제외 추천
- D5: raw 미커밋, process-memory 집계와 Markdown 결과·재현 명령만 보존 추천
- D6: 기존 PowerShell·Docker 최소안 우선, repository script·k6·metric stack은 별도 승인 추천

모든 결정 상태는 `Decision Required`다. SLI candidate를 Approved SLI나 SLO로 오표기하지 않았다.

## 실행한 검증

- Git root·origin·branch·작업 트리 확인
- PR #48 병합과 `origin/main` 포함 확인
- `git pull --ff-only origin main`, `main == origin/main` 확인
- 기존 `ops/sre` PR·branch 관계와 diff 확인 후 최신 main에서 재생성
- Compose config·healthcheck·depends_on·resource limit 정적 조사
- Nginx effective `log_format`·`access_log` 조사
- smoke endpoint·session·CSRF·상태 변경 순서 조사
- Backend·Frontend dependency와 timing·metric 설정 부재 검색
- CI test/build·health와 performance artifact 부재 조사
- 실행 중 service health, Docker log driver, resource limit, restart와 stats field 가용성 확인
- 실제 raw log 내용은 문서·저장소에 복사하지 않고 tail line availability만 확인
- `docker compose --env-file infra/local-integration/.env.local -f infra/local-integration/compose.yaml config --quiet`: 통과
- `py -3 scripts/validate-task-artifacts.py --task-id PERF-001`: 통과
- `scripts/validate-commit-message.sh --message 'docs(sre): PERF-001 로컬 성능 기준선 결정 요청'`: 통과
- `.env.local`의 password 계열 실제 값과 산출물 세 파일의 일치 여부 검사: 일치 없음
- 변경 경로 검사: PERF-001 문서 세 파일만 확인
- `py -3 scripts/validate-pr-body-encoding.py --from-stdin`: 통과
- `git diff --cached --check`: 통과

## 실행하지 못한 검증과 이유

- 실제 latency·percentile·오류율 기준선: D1~D6 측정 조건이 사용자 승인 전이므로 미실행
- cold start 반복: 횟수와 상태 보존 조건이 미승인이므로 미실행
- concurrency·부하 테스트: 승인 범위 밖이므로 미실행
- server·DB·browser 내부 timing: 현재 instrumentation이 없고 신규 구현이 제외 범위라 미실행
- GET 오류 재시도와 session 만료: FOUNDATION-006 미실행 위험을 유지하며 이번 작업에서 재검증하지 않음

## 적용 방법

사용자/Tech Lead가 결정 요청 D1~D6을 검토해 추천안 승인 또는 수정 조건을 기록한다. 승인 후 별도 성능 측정 작업에서 문서의 실험 절차를 고정하고, 조건을 바꾸지 않은 채 기준선을 수집한다.

## 롤백

제품·실행 설정을 변경하지 않은 문서 작업이다. 병합 전에는 PR을 병합하지 않는 것으로 적용을 중단할 수 있고, 병합 후에는 별도 revert PR로 PERF-001 문서 세 개만 되돌린다.

## 위험과 제한

- local baseline은 해당 노트북과 Docker Desktop 할당의 비교 기준이며 운영 capacity를 대표하지 않는다.
- client elapsed만으로 proxy·Backend·DB 병목을 구분할 수 없다.
- container stats는 sampling 간격과 background workload에 민감하다.
- 상태 변경 반복은 데이터 규모를 바꾸므로 별도 cohort와 고정된 reset·반복 조건이 필요하다.
- 정상 실행 중 snapshot은 성능 기준선이나 병목 증거가 아니다.
- 오류 재시도 keyboard 접근성과 session 만료는 계속 미실행 위험이다.

## 다음 작업

1. 사용자/Tech Lead가 D1~D6을 결정한다.
2. 승인된 조건을 변경하지 않는 별도 baseline 측정 작업을 만든다.
3. 기준 분포와 병목 증거를 확보한 뒤에만 instrumentation·도구·최적화 후보를 제안한다.
4. 제품·인프라 변경은 담당 역할과 별도 승인·재측정 계획으로 전달한다.

## Git 결과

- 작업 branch: `ops/sre`
- 산출물 commit: `76a4e56a20a09ad87123bf41d07386057e48b73f`
- `origin/ops/sre` push: 완료
- 변경 파일: PERF-001 문서 세 개만 포함

## PR 상태

- PR: `#49`
- 제목: `docs(sre): PERF-001 로컬 성능 기준선 결정 요청`
- base/head: `main` ← `ops/sre`
- 상태: Open, Ready for review
- 원격 제목·본문 UTF-8, base/head와 Draft 상태 확인 완료
- CI와 AI review의 최신 상태는 GitHub PR을 기준으로 확인한다.
- 자동 병합하지 않는다.
