# 🐾 PawCycle Commerce

> **반려동물 소모품의 반복 구매를 정기배송으로 관리하는 Commerce 프로젝트**

PawCycle Commerce는 Commerce/Subscription 제품을 구현한 뒤 데이터 정합성, 운영 안정성, 성능 측정, 장애·복구, AI Lean Harness까지 실제 문제와 evidence를 기준으로 발전시키는 프로젝트다. 기술을 먼저 늘리기보다 현재 구조에서 문제를 재현·측정하고, 필요가 확인될 때 다음 기술과 아키텍처를 선택한다.

## 현재 상태

- Product: 일반 구매와 정기배송 Commerce 기능 및 MVP4 제품 흐름 구현
- Production deployment target: **없음**
- CD: **DEFER**
- 무중단 배포: **DEFER**
- OCI: 승인된 후속 배포 방향이지만 아직 active Production target 또는 Production Verified 상태가 아님
- AWS: 실제 운영 리소스는 종료했으며, active AWS 실행 경로·전용 검증·실행 Runbook은 저장소에서 retire

AWS에서 수행했던 배포·관측·backup/restore·장애 대응·RDS 전환 준비 과정은 삭제하지 않는다. 당시의 실행 결과와 판단은 `docs/reports/**`, `docs/learning/**`, `docs/adr/**`, Git 및 Pull Request 이력에 역사적 evidence로 보존한다.

## 프로젝트 핵심 축

| Product | Operations | AI Lean Harness |
| --- | --- | --- |
| Commerce / Subscription | 배포·관측·장애 대응·복구 | 목표·범위·위험·검증 통제 |
| Subscription Lifecycle | 측정 기반 성능 개선 | Risk-based Workflow |
| Idempotency / Reconciliation | Production Safety | Codex / CI / Review / Evidence |

## 주요 구현

### Product

- 공개 상품 목록·상세와 고객 Commerce 흐름
- Session Login / Logout와 CSRF 보호
- 정기배송 구독 생성·조회·변경·일정 관리
- Subscription Snapshot과 Schedule
- 주문·결제·환불·배송 관련 Commerce 상태 모델
- 회원별 자원 소유권과 권한 경계

### Reliability

- Idempotency 기반 중복 요청 방지와 성공 결과 replay
- Idempotency retention / bounded cleanup
- Subscription reconciliation과 단위 실패 격리
- Migration 및 동시성 회귀 검증
- Local disposable environment 기반 장애 재현

### Observability / Operations

- Docker / Docker Compose 기반 container 계약
- Nginx reverse proxy와 GHCR immutable image 계약
- Micrometer / Prometheus / Grafana / Alertmanager
- Health / smoke / diagnosis 검증 자산
- 장애 재현·복구와 운영 evidence 기록

현재 `infra/production/**`의 남은 자산은 새 Cloud target에 자동 적용되는 배포 계약이 아니다. Dockerfile, Compose, Nginx, smoke·진단·관측처럼 공급자 중립적으로 재사용 가능한 저장소 자산이며, 실제 OCI deployment 계약은 별도 고위험 작업으로 다시 설계·승인한다.

## 기술 스택

- Backend: **Java · Spring Boot · Spring Security · Spring Data JPA · Micrometer · Gradle**
- Frontend: **Next.js · React · TypeScript · Node.js**
- Data: **MySQL · Flyway**
- Runtime/Packaging: **Docker · Docker Compose · Nginx · GHCR**
- Observability: **Prometheus · Grafana · Alertmanager**
- Engineering: **GitHub Actions · CodeRabbit · ChatGPT · Codex · GitHub MCP**

## 아키텍처 방향

현재 저장소가 유지하는 기본 application topology는 다음과 같다.

```text
Client
  │
  ▼
Nginx
  ├──────────────► Next.js
  │
  └──────────────► Spring Boot
                       │
                       ▼
                     MySQL
                       │
              ┌────────┴────────┐
              ▼                 ▼
        Reconciliation      Idempotency
              │
              ▼
           Metrics
              │
       Prometheus / Grafana
```

이 topology가 현재 특정 Cloud에서 Production으로 실행 중이라는 의미는 아니다. 현재 운영 경계와 후속 target 활성화 조건은 `docs/architecture/production-operations-overview.md`를 따른다.

## 대표 문제 해결 Evidence

### Subscription 단위 실패 격리

여러 Subscription을 하나의 transaction에서 처리할 때 하나의 실패가 batch 전체로 전파되는 문제를 분리했다. 각 Subscription을 독립 transaction 경계에서 처리하여 실패 구독만 rollback하고 이후 구독 처리를 계속할 수 있도록 검증했다.

- Evidence: [PR #106](https://github.com/guseoh/pawcycle-commerce/pull/106)

### Idempotency retention과 cleanup

성공 결과 replay 안전성을 유지하면서 데이터가 무한 증가하지 않도록 완료 시각, 30일 retention, bounded cleanup을 적용하고 cleanup/replay 경쟁을 검증했다.

- Evidence: [PR #108](https://github.com/guseoh/pawcycle-commerce/pull/108)

### N+1 측정 후 개선

최적화를 먼저 적용하지 않고 page size별 SQL query 수를 측정한 뒤 batch 조회를 적용하고 같은 조건에서 재검증했다.

| API | Before 10 | Before 20 | Before 100 | After 10 | After 20 | After 100 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Plans | 32 | 52 | 212 | 14 | 14 | 14 |
| Subscriptions | 51 | 91 | 411 | 15 | 15 | 19 |

- [PR #102 - N+1 측정](https://github.com/guseoh/pawcycle-commerce/pull/102)
- [PR #103 - Batch 조회 개선](https://github.com/guseoh/pawcycle-commerce/pull/103)

측정값은 당시 Local Representative Fixture 기준이며 Production SLO나 현재 Production 성능으로 표현하지 않는다.

## AI Lean Harness

PawCycle의 AI 활용 목표는 코드 생성량이 아니라 **사람이 목표·범위·위험·검증을 통제할 수 있는가**에 있다.

```text
문제
→ 최신 상태 확인
→ 승인 입력
→ 설계·범위
→ Acceptance Criteria
→ 검증·중단 조건
→ Delta 추출
→ 경량화
→ Final Lightweight Delta Prompt
→ 구현·Review·Evidence
```

저장소 작업은 위험에 따라 경량·일반·고위험으로 분류하고, 저장소 준비와 실제 Cloud/Production 실행을 분리한다. CI Green만으로 Production Verified를 선언하지 않는다.

## 현재 Working Roadmap

```text
Product Completion
→ Performance Re-baseline
→ Measured Optimization
→ Technology Decision
→ Scale when justified
→ Operations Evolution
→ Failure / Recovery Validation
→ Harness Evaluation
→ Portfolio Evidence
```

OCI 배포 재구축은 별도의 Calendar/Operations constraint로 관리한다. 새 Production target을 활성화할 때도 기존 AWS 구조를 이름만 바꾸어 옮기지 않고, 현재 OCI 자원·비용·보안·복구 제약을 다시 확인한 뒤 필요한 계약만 구성한다.

## 문서

- 운영 현재 상태: `docs/architecture/production-operations-overview.md`
- Runbook index: `docs/runbook/README.md`
- 실행·검증 evidence: `docs/reports/**`
- PR별 학습·변경 기록: `docs/learning/**`
- Architecture Decision Record: `docs/adr/**`
