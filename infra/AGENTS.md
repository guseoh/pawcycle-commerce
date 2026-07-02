# 플랫폼/SRE 에이전트 규칙

## 책임

플랫폼/SRE(Platform/SRE)는 로컬 개발 환경, Docker, Docker Compose, CI/CD, 배포 설정(Deployment Configuration), 헬스 체크(Health Check), 메트릭(Metric), 로그(Log), 트레이싱(Tracing), 부하 테스트(Load Test), 성능 측정, 대시보드(Dashboard), 알림(Alert), 장애 대응 문서, 운영 런북(Runbook)을 담당한다.

명시적으로 승인된 작업이 있기 전에는 인프라 구현 파일을 생성하지 않는다.

## 공통 운영 기준

- 공통 Git, commit·push, 보고서, 인수인계 규칙은 루트 `AGENTS.md`를 따른다.
- 플랫폼/SRE 역할 브랜치는 `ops/sre`다.
- `ops/sre`에는 하나의 활성 운영 작업만 둔다.
- PR 병합 후에는 `ops/sre`를 삭제하고 다음 운영 작업에서 최신 `main` 기준으로 다시 만든다.

## 측정 우선

근거 없이 Redis, 큐(Queue), 캐시(Cache), 재시도(Retry), 타임아웃(Timeout) 증가, 쿼리 튜닝(Query Tuning), 스케일링(Scaling)을 도입하지 않는다.

성능 문제는 다음 순서로 다룬다.

1. 문서화된 조건에서 기준 성능(Baseline)을 측정한다.
2. 병목 증거(Bottleneck Evidence)를 수집한다.
3. 가설(Hypothesis)을 작성한다.
4. 애플리케이션 변경이 필요하면 담당 역할에 요청한다.
5. 동일 조건으로 재측정한다.
6. 개선 전후 결과를 기록한다.

## 운영 안전

- 비밀 값(Secret), 자격 증명(Credential), 토큰(Token), 개인 키(Private Key), 운영 환경 값을 저장소에 커밋하지 않는다.
- 운영 변경에는 롤백(Rollback) 고려 사항을 포함한다.
- 운영 지표를 좋게 보이게 하려고 애플리케이션 제품 코드를 바꾸지 않는다.
- 결과를 유리하게 만들기 위해 부하 조건을 바꾸지 않는다.

## CI/CD와 배포

- 워크플로(Workflow)는 최소 범위와 작업 목적에 맞게 유지한다.
- 프로젝트에 해당 애플리케이션 또는 인프라 표면이 있을 때만 검사를 추가한다.
- 필요한 환경 변수(Environment Variable)는 문서화하되 비밀 값은 저장하지 않는다.

## 런북

런북에는 증상(Symptom), 영향(Impact), 확인 절차(Check), 완화 조치(Mitigation), 롤백, 에스컬레이션(Escalation), 후속 학습을 포함한다.

## 허용 경로

- `infra/**`
- `.github/workflows/**`
- `docs/performance/**`
- `docs/runbook/**`
- `docs/handoffs/**`
- 승인된 운영 설정 파일

## 금지 경로

- 애플리케이션 제품 코드 변경
- 측정 없는 성능 변경
- 저장소에 비밀 값 기록
- 롤백 메모 없는 배포 변경
- 승인되지 않은 인프라 의존성 추가
