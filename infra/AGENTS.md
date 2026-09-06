# Platform / SRE 경로 규칙

이 파일은 `infra/**`와 직접 연결된 운영·측정 경계만 정의한다. 공통 승인·Git·PR·산출물·병합 규칙은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따른다.

## 책임

- local integration과 Docker/Compose
- CI/CD와 release contract
- health, metrics, logs, dashboard, alert
- performance workload와 측정 harness
- deploy, rollback, backup/restore 등 반복 운영 절차

## 측정 우선

성능 문제는 다음 흐름을 기본으로 한다.

1. 동일 조건의 baseline
2. bottleneck evidence
3. 원인 가설
4. 가장 작은 변경
5. 동일 조건 재측정
6. trade-off와 남은 한계 기록

성능 개선 목적의 cache, Redis, async, queue, retry, timeout, scaling이나 application tuning은 baseline과 evidence 없이 도입하지 않는다. 보안·가용성·실패 복구에 필요한 retry/timeout 변경은 별도 위험 분석과 regression 검증을 적용한다.

## 운영 안전

- 저장소 변경과 실제 운영 실행을 분리한다.
- Production·Cloud·운영 DB·Secret·비용 리소스 실행은 별도 고위험 사용자 승인 없이는 수행하지 않는다.
- 운영 mutation에는 대상 식별, preflight, 성공 기준, 중단 조건과 rollback/recovery 경계가 있어야 한다.
- 성능 결과를 좋게 만들기 위해 workload·dataset·측정 조건을 임의로 바꾸지 않는다.
- Secret과 실제 운영 식별값을 저장소·Prompt·로그·보고서에 복사하지 않는다.

## Release side effect

문서·학습 기록·Harness metadata 같은 non-runtime 변경은 Production image build/push나 deploy를 자동 시작해서는 안 된다. Release workflow는 `main` push를 받더라도 checkout된 commit diff에서 실제 runtime/build input 변경을 판정한 뒤 publish job을 실행한다.

## 검증

- Compose/script/workflow: syntax + contract regression
- deploy/recovery: fake/isolated lifecycle + fail-closed path
- performance: reproducible workload + evidence completeness
- 실제 운영 실행: 승인된 Runbook의 적용 전후와 독립 확인

승인된 cross-stack 작업에서 application code 수정이 필요한 경우 해당 경로의 `AGENTS.md`도 함께 적용한다. 운영 지표를 맞추기 위한 임의의 제품 동작 변경은 허용하지 않는다.
