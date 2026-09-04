# 🐾 PawCycle Commerce

> **반려동물 소모품의 반복 구매를 정기배송으로 관리하는 Commerce 프로젝트**  
> 제품 기능 구현에서 출발해 데이터 일관성, 운영 안정성, 장애 대응, 관측성, 운영 자동화와 AI Harness까지 확장하고 있습니다.

PawCycle Commerce는 단순한 쇼핑몰 CRUD를 만드는 것보다  
**정기배송 서비스가 시간이 지나면서 마주치는 문제를 발견하고 해결하는 과정**에 초점을 둡니다.

```text
제품 기능
→ 안정적인 운영
→ 장애 관측과 복구
→ 반복 업무 자동화
→ AI Harness 개선
→ Evidence 기반 회고
→ 다음 제품 개선
```

---

## 📑 목차

- [프로젝트 소개](#-프로젝트-소개)
- [핵심 구현](#-핵심-구현)
- [기술 스택](#-기술-스택)
- [아키텍처](#-아키텍처)
- [주요 설계와 문제 해결](#-주요-설계와-문제-해결)
- [성능 개선](#-성능-개선)
- [운영과 관측성](#-운영과-관측성)
- [AI Harness Engineering](#-ai-harness-engineering)
- [대표 PR](#-대표-pr)
- [현재 상태와 Roadmap](#-현재-상태와-roadmap)

---

# 🐶 프로젝트 소개

정기배송 서비스는 구독 데이터를 저장하는 것만으로 끝나지 않습니다.

구독 변경, 일정 계산, Scheduler 실행, 중복 요청, 실패 복구와 데이터 보정까지  
**시간에 따라 상태가 계속 변하는 도메인**입니다.

PawCycle에서는 이 과정에서 발생하는 문제를 실제 코드와 테스트로 다루고 있습니다.

### 프로젝트의 세 가지 축

| Product | Operations | AI Harness |
| --- | --- | --- |
| 정기배송 Commerce 기능 | 배포·관측·장애 대응·복구 | AI 작업 범위·검증·Review 통제 |
| Subscription Lifecycle | Production Safety | Risk-based Workflow |
| Idempotency / Reconciliation | Prometheus / Grafana / Alert | Codex / CI / Evidence |

---

# ✨ 핵심 구현

### Product

- 공개 상품 목록·상세
- Session Login / Logout
- CSRF 보호
- 정기배송 구독 생성·조회·관리
- Subscription Snapshot과 Schedule
- 회원별 구독 소유권 보호

### Reliability

- Idempotency 기반 중복 요청 방지
- 성공 결과 Replay
- Idempotency Retention / Cleanup
- Reconciliation
- Subscription 단위 실패 격리
- Migration 및 동시성 회귀 검증

### Operations

- Docker Compose 기반 운영
- Nginx / HTTPS
- Application Rollback
- Logical Backup / Isolated Restore
- Prometheus / Grafana
- Alertmanager / Discord Alert
- 장애 재현 및 복구 Runbook

---

# 🛠 기술 스택

### Backend

<p>
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/java/java-original.svg" width="45" alt="Java"/>
  &nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/spring/spring-original.svg" width="45" alt="Spring"/>
  &nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/gradle/gradle-original.svg" width="45" alt="Gradle"/>
</p>

**Java · Spring Boot · Spring Security · Spring Data JPA · Micrometer · Gradle**

### Frontend

<p>
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/nextjs/nextjs-original.svg" width="45" alt="Next.js"/>
  &nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/react/react-original.svg" width="45" alt="React"/>
  &nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/typescript/typescript-original.svg" width="45" alt="TypeScript"/>
  &nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/nodejs/nodejs-original.svg" width="45" alt="Node.js"/>
</p>

**Next.js · React · TypeScript · Node.js**

### Database & Infrastructure

<p>
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/mysql/mysql-original.svg" width="45" alt="MySQL"/>
  &nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/docker/docker-original.svg" width="45" alt="Docker"/>
  &nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/nginx/nginx-original.svg" width="45" alt="Nginx"/>
  &nbsp;
</p>

**MySQL · Flyway · Docker · Docker Compose · Nginx · OCI**

### Observability & Development

<p>
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/prometheus/prometheus-original.svg" width="45" alt="Prometheus"/>
  &nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/grafana/grafana-original.svg" width="45" alt="Grafana"/>
  &nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/github/github-original.svg" width="45" alt="GitHub"/>
  &nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/githubactions/githubactions-original.svg" width="45" alt="GitHub Actions"/>
</p>

**Prometheus · Grafana · Alertmanager · GitHub Actions · CodeRabbit · ChatGPT · Codex · GitHub MCP**

---

# 🏗 아키텍처

> `docs/images/readme/architecture.png` 추가 예정

```text
Internet
   │
   ▼
 OCI VCN / Public Application Subnet
   │
 Nginx :80/:443
   ├───────────────┐
   ▼               ▼
Next.js       Spring Boot
Frontend         Backend
                   │ database-egress / TLS REQUIRED
                   ▼
             Private DB Subnet
             MySQL HeatWave / MySQL.Free
                   │
            ┌──────┴──────┐
            ▼             ▼
      Reconciliation   Idempotency
            │
            ▼
        Micrometer → metrics-proxy
                         │
                         ▼
                 Trial Observability host
                 Prometheus / Grafana
```

<details>
<summary><strong>Production 운영 구조 자세히 보기</strong></summary>

<br>

Production target에서는 Application Release와 운영 Control 상태를 구분합니다.

- Backend / Frontend image는 commit SHA 기준으로 식별
- GHCR에 Application image 저장
- Nginx에서 외부 HTTPS 처리
- Application Compose는 backend/frontend/proxy만 소유
- MySQL HeatWave / MySQL.Free private endpoint는 database-egress와 TLS REQUIRED로 사용
- OCI Run Command는 operator-approved 실행 경계
- Object Storage logical backup과 isolated restore-verify를 Application lifecycle과 분리
- 운영 Secret은 저장소에서 분리
- 이전 Application Release Rollback 경로 검증

GitHub Actions가 Production에 자동 배포하지 않습니다.

실제 운영 실행은 승인된 Runbook과 별도 실행 승인을 기준으로 수행합니다.

</details>

---

# 🧩 주요 설계와 문제 해결

<details>
<summary><strong>1. 하나의 구독 실패가 Scheduler 전체로 전파되는 문제</strong></summary>

<br>

### 문제

여러 Subscription을 하나의 Transaction에서 Reconciliation하면  
한 구독의 실패가 Batch 전체에 영향을 줄 수 있습니다.

```text
Subscription A → 성공
Subscription B → 실패
Subscription C → 처리되지 않음
```

### 해결

Batch 전체 Transaction을 제거하고  
각 Subscription을 독립적인 `REQUIRES_NEW` Transaction으로 처리했습니다.

```text
Batch
 ├─ Subscription A → Commit
 ├─ Subscription B → Rollback
 └─ Subscription C → Commit
```

### 결과

- 실패 구독만 Rollback
- 이후 구독 처리 지속
- 실패한 Subscription 식별 가능
- 재처리와 장애 분석 경계 확보

**Evidence:** [PR #106](https://github.com/guseoh/pawcycle-commerce/pull/106)

</details>

<details>
<summary><strong>2. Idempotency 데이터가 계속 증가하는 문제</strong></summary>

<br>

### 문제

중복 요청을 안전하게 Replay하기 위해 성공 결과를 보관하지만  
영구 보관하면 데이터가 계속 증가합니다.

반대로 너무 빨리 삭제하면 Replay 안전성을 잃습니다.

### 해결

성공 결과의 최초 완료 시각을 기록하고 **30일 Retention + Bounded Cleanup**을 적용했습니다.

```text
Reservation
    │
    ▼
Command Success
    │
    ▼
completed_at 기록
    │
    ▼
30일 Retention
    │
    ▼
Bounded Cleanup
```

추가 규칙:

- Replay는 retention 기간을 연장하지 않음
- 미완료 Reservation은 삭제하지 않음
- 과거 데이터는 제한된 범위에서 Repair
- Cleanup과 Replay 경쟁을 동시성 테스트로 검증

**Evidence:** [PR #108](https://github.com/guseoh/pawcycle-commerce/pull/108)

</details>

<details>
<summary><strong>3. Migration의 실제 Lock 범위를 확인한 과정</strong></summary>

<br>

MVP2 Legacy Migration에서 `FOR UPDATE`가 어느 범위까지 Lock을 잡는지 추측하지 않고  
격리된 MySQL 환경에서 실제로 측정했습니다.

확인된 범위에는 다음 상황이 포함됐습니다.

- 관리 대상 Row Update
- 인접 Insert
- Legacy Target Update

이 결과를 근거로 Production Migration을 단순 실행하지 않고  
별도의 고위험 검증 대상으로 유지했습니다.

**Evidence:** [PR #104](https://github.com/guseoh/pawcycle-commerce/pull/104)

</details>

---

# 📈 성능 개선

N+1 가능성을 발견했을 때 바로 최적화하지 않고  
**먼저 Page Size별 SQL Query 수를 측정한 뒤 개선했습니다.**

| API | Before 10 | Before 20 | Before 100 | After 10 | After 20 | After 100 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Plans | 32 | 52 | 212 | 14 | 14 | 14 |
| Subscriptions | 51 | 91 | 411 | 15 | 15 | 19 |

```text
Measurement
    ↓
N+1 판정
    ↓
Batch 조회 적용
    ↓
동일 조건 재측정
```

API 응답 구조, Pagination, Authorization, DB Schema는 변경하지 않았습니다.

**Evidence**

- [PR #102 - N+1 측정](https://github.com/guseoh/pawcycle-commerce/pull/102)
- [PR #103 - Batch 조회 개선](https://github.com/guseoh/pawcycle-commerce/pull/103)

> 측정값은 Local Representative Fixture 기준이며 Production 성능이나 SLO로 표현하지 않습니다.

---

# 🔭 운영과 관측성

현재 운영 흐름은 단순 배포에서 끝나지 않습니다.

```text
Deploy
  ↓
Health / Smoke
  ↓
Metrics
  ↓
Incident Detection
  ↓
Diagnosis
  ↓
Recovery
  ↓
Evidence
```

<details>
<summary><strong>Prometheus / Grafana Observability</strong></summary>

<br>

Backend에서 다음 영역을 Metric으로 제공합니다.

- HTTP
- JVM
- CPU
- JDBC / HikariCP
- Subscription Reconciliation
- Idempotency Cleanup

고카디널리티를 피하기 위해 `subscriptionId` 같은 개별 식별자는  
Prometheus Label로 사용하지 않습니다.

Local Docker 환경에서:

```text
Spring Boot
    ↓
Actuator / Micrometer
    ↓
Prometheus
    ↓
Grafana
```

흐름을 검증했습니다.

**Evidence**

- [PR #112 - Backend Observability](https://github.com/guseoh/pawcycle-commerce/pull/112)
- [PR #113 - Prometheus / Grafana](https://github.com/guseoh/pawcycle-commerce/pull/113)

</details>

<details>
<summary><strong>장애 재현과 복구</strong></summary>

<br>

다음 장애를 Local Disposable Environment에서 재현했습니다.

- Backend unavailable
- MySQL connection failure
- Reconciliation failure

Reconciliation 장애 재현은 Shared Local DB 대신  
실행별 독립 Compose Project와 Fixture를 사용합니다.

```text
장애 발생
   ↓
Metric / Alert
   ↓
Log / Target 확인
   ↓
원인 구분
   ↓
복구
   ↓
정상 상태 확인
```

**Evidence:** [PR #116](https://github.com/guseoh/pawcycle-commerce/pull/116)

</details>

<details>
<summary><strong>Alertmanager → Discord</strong></summary>

<br>

Dashboard를 사람이 계속 보고 있어야만 장애를 발견할 수 있는 구조에서 벗어나기 위해  
Alert 흐름을 구성했습니다.

```text
Backend / Reconciliation
          ↓
      Prometheus
          ↓
        Alert
          ↓
     Alertmanager
          ↓
       Discord
```

Local 환경에서 다음 상태 변화를 직접 확인했습니다.

- Backend unavailable `firing → resolved`
- Reconciliation failure `firing → resolved`
- Discord 전달

Production Threshold, Escalation, Repeat Policy는 별도 운영 결정으로 남겨두었습니다.

**Evidence**

- [PR #118 - Prometheus Alert](https://github.com/guseoh/pawcycle-commerce/pull/118)
- [PR #120 - Discord Alert](https://github.com/guseoh/pawcycle-commerce/pull/120)

</details>

---

# 🤖 AI Harness Engineering

PawCycle에서는 AI에게 저장소 전체를 자유롭게 맡기지 않습니다.

**제품 결정은 사람이 하고, AI는 승인된 범위만 구현하도록 개발 과정 자체를 Harness로 관리합니다.**

```text
User / Product Owner / Tech Lead
              │
              ▼
        Scope Approval
              │
              ▼
       Risk Classification
              │
              ▼
       Task Specification
              │
              ▼
            Codex
              │
              ▼
      Repository Change
              │
              ▼
       Local Validation
              │
              ▼
        GitHub Actions
              │
              ▼
          AI Review
              │
              ▼
         Human Review
              │
              ▼
         Manual Merge
              │
              ▼
           Evidence
```

> `docs/images/readme/ai-harness-workflow.png` 추가 예정

<details>
<summary><strong>AI 역할과 책임 경계</strong></summary>

<br>

### User

- Product Owner
- Tech Lead
- 제품·도메인·API·DB 결정
- 위험 수용
- 실제 Production 실행
- 최종 Merge

### ChatGPT

- Scope 분석
- 설계 검토
- 작업 위험 등급 결정
- Codex 작업 명세
- PR / CI / Review 분석
- 운영 결과 분석
- Evidence 기반 회고

### Codex

- 승인된 Repository 변경
- Test / Validation
- Commit / Push
- 요청된 PR 생성

AI가 제품 결정, 운영 위험 수용, Production 실행 또는 최종 Merge를 대신하지 않습니다.

</details>

<details>
<summary><strong>Risk-Based Lean Harness</strong></summary>

<br>

모든 저장소 작업을 위험도에 따라 분류합니다.

| Grade | 기준 |
| --- | --- |
| 경량 | 외부 계약을 변경하지 않는 작은 내부 변경 |
| 일반 | 하나의 사용자 목적을 위한 비파괴 변경 |
| 고위험 | 인증·Migration·Production·복구·보안 |

작업 등급에 따라:

- 검증 깊이
- 활성 역할
- Report
- QA
- Handoff
- 운영 실행 경계

를 다르게 적용합니다.

Codex에는 전체 프로젝트를 설명하는 거대한 Prompt 대신  
현재 작업의 **Delta**를 중심으로 명세합니다.

```text
Goal
+ Scope
+ Exclusions
+ Verification
+ Completion Condition
+ Stop Condition
```

새로운 제품·보안·DB 결정이 필요해지면 구현을 계속하지 않고 사용자 결정으로 돌아갑니다.

</details>

<details>
<summary><strong>Harness 자체를 개선한 과정</strong></summary>

<br>

Harness 역시 처음부터 완성된 시스템으로 가정하지 않았습니다.

실제 작업 중 Harness가 개발을 방해하는 문제가 발견되면  
그 문제도 하나의 Software Defect로 취급했습니다.

```text
실제 작업
   ↓
Harness 결함 발견
   ↓
Parser / Validator 실패
   ↓
계약 수정
   ↓
Regression Test
   ↓
다음 작업에서 재사용
```

대표 사례:

- `OBS-BASE` Task ID를 Validator가 인식하지 못한 문제
- `INC-BASE` Task ID parser 계약 불일치
- PR Metadata와 Validator 계약 불일치
- Agent Benchmark Schema 진화

**Evidence**

- [PR #111 - OBS Task ID 지원](https://github.com/guseoh/pawcycle-commerce/pull/111)
- [PR #115 - INC Task ID 지원](https://github.com/guseoh/pawcycle-commerce/pull/115)

</details>

<details>
<summary><strong>GitHub MCP와 Agent Benchmark</strong></summary>

<br>

AI가 GitHub 상태를 추측하지 않고 실제 Repository Evidence를 읽도록  
Connector와 GitHub MCP 기반 Workflow를 실험했습니다.

```text
ChatGPT Connector Baseline
            ↓
     Benchmark Contract
            ↓
    GitHub MCP Boundary
            ↓
  Codex GitHub MCP Benchmark
            ↓
       Actual Pilot
```

측정 또는 검증 항목:

- Accuracy
- Tool Call
- Execution Time
- Scope Violation
- User Intervention
- Production Access
- Read Tool Allowlist

대표 작업:

- [PR #91 - Agent Before 기준선](https://github.com/guseoh/pawcycle-commerce/pull/91)
- [PR #96 - Connector 대조군](https://github.com/guseoh/pawcycle-commerce/pull/96)
- [PR #97 - GitHub MCP 운영 경계](https://github.com/guseoh/pawcycle-commerce/pull/97)
- [PR #98 - Benchmark Tool](https://github.com/guseoh/pawcycle-commerce/pull/98)
- [PR #100 - Codex GitHub MCP Benchmark](https://github.com/guseoh/pawcycle-commerce/pull/100)
- [PR #101 - Pilot 계약](https://github.com/guseoh/pawcycle-commerce/pull/101)

제한된 Benchmark 결과를 일반적인 AI Agent 성능으로 확대 해석하지 않습니다.

</details>

---

# 🔧 대표 PR

README에 전체 PR을 나열하지 않고  
**설계 판단이나 문제 해결 과정이 드러나는 작업만 선별했습니다.**

| Topic | Engineering Point | PR |
| --- | --- | ---: |
| MVP2 Integration | 실제 HTTP / DTO / Replay 계약 검증 | [#89](https://github.com/guseoh/pawcycle-commerce/pull/89) |
| N+1 | 측정 → 개선 → 재측정 | [#102](https://github.com/guseoh/pawcycle-commerce/pull/102), [#103](https://github.com/guseoh/pawcycle-commerce/pull/103) |
| Migration Lock | 실제 MySQL Lock Footprint 측정 | [#104](https://github.com/guseoh/pawcycle-commerce/pull/104) |
| Reconciliation | Subscription별 Transaction 격리 | [#106](https://github.com/guseoh/pawcycle-commerce/pull/106) |
| Idempotency | Retention + Cleanup + Concurrency | [#108](https://github.com/guseoh/pawcycle-commerce/pull/108) |
| Observability | Metric → Prometheus → Grafana | [#112](https://github.com/guseoh/pawcycle-commerce/pull/112), [#113](https://github.com/guseoh/pawcycle-commerce/pull/113) |
| Incident | 장애 재현 → 진단 → 복구 | [#116](https://github.com/guseoh/pawcycle-commerce/pull/116) |
| Alert | Prometheus → Alertmanager → Discord | [#118](https://github.com/guseoh/pawcycle-commerce/pull/118), [#120](https://github.com/guseoh/pawcycle-commerce/pull/120) |
| AI Harness | 실제 작업에서 Harness 결함 발견·개선 | [#111](https://github.com/guseoh/pawcycle-commerce/pull/111), [#115](https://github.com/guseoh/pawcycle-commerce/pull/115) |

---

# 🚦 현재 상태와 Roadmap

```text
Product MVP
   ✅
   ↓
Production Safety Baseline
   ✅
   ↓
MVP2 Subscription
   ✅
   ↓
Idempotency / Reconciliation
   ✅
   ↓
Performance Measurement & Improvement
   ✅
   ↓
Observability
   ✅
   ↓
Incident Response
   ✅
   ↓
Alerting
   ✅
   ↓
Subscription Operations Automation
   🚧
   ↓
Codebase & Harness Stabilization
   ⬜
   ↓
Deployment / Operations Automation
   ⬜
   ↓
Evidence-Based Retrospective
   ⬜
   ↓
Portfolio V1
   ⬜
   ↓
MVP3
   ⬜
```

<details>
<summary><strong>MVP3 후보</strong></summary>

<br>

MVP3 기능은 아직 확정하지 않았습니다.

현재 프로젝트의 Evidence를 해체한 뒤  
Commerce 흐름에서 가장 큰 공백을 기준으로 결정할 예정입니다.

현재 후보:

- 주문
- 결제
- 정기결제
- 재고
- 관리자 운영

결제가 선택된다면 단순 PG 결제창 연동보다는:

```text
Subscription
    ↓
Scheduler
    ↓
Recurring Payment
    ↓
Order
    ↓
Idempotency
    ↓
Reconciliation
    ↓
Metric / Alert
```

까지 기존 정기배송 구조와 연결하는 것을 검토합니다.

</details>

---

## 🎯 What I Want to Prove

PawCycle Commerce의 목표는 기능 수가 많은 쇼핑몰을 만드는 것이 아닙니다.

**제품을 만들고, 운영하고, 실패를 관측하고, 반복 업무를 자동화하며,  
그 과정에서 AI 개발 환경 자체도 개선할 수 있는 Backend Engineer의 개발 과정을 증명하는 것**이 목표입니다.

```text
Build
→ Operate
→ Observe
→ Recover
→ Automate
→ Improve the Harness
→ Learn from Evidence
```
