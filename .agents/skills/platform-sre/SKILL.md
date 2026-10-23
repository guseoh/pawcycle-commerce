---
name: platform-sre
description: >-
  PawCycle Commerce의 CI/CD, Docker/Compose, observability, performance measurement, deploy와 recovery 작업을 수행할 때 사용한다.
---

# Platform / SRE Skill

지속 책임은 `docs/roles/platform-sre.md`, 운영 불변식은 `infra/AGENTS.md`, 공통 안전 규칙은 루트 `AGENTS.md`를 따른다.

## 실행 절차

1. **목표와 실행 구분 확인**
   - 기존 근거의 직접 분석, 설계, 저장소 준비, 실제 운영 실행 중 요청 목적을 정한다. 공통 권한은 AGENTS/Lean Harness를 따른다.
   - 분석 요청이 실제 부하·장애 주입·운영 변경을 포함하는지 구분한다.

2. **직접 증거 수집과 원인 분석**
   - 사용 가능한 승인된 read-only Tool로 관련 Runtime·Metric·Log·Load 근거를 직접 확인한다. 접근이 없으면 재현 가능한 실행 산출물과 조건을 입력받는다.
   - 동일 시간축·workload·dataset·버전·자원 조건에서 k6 결과, OS/Docker, JVM/GC, Tomcat, HikariCP와 MySQL 근거를 연결한다. 질문에 필요한 지표만 읽는다.
   - 관측 사실, 원인 가설, 추론과 미확인을 나누고 경쟁 원인을 대조한다. 같은 조건을 확보하지 못하면 Before/After 개선으로 주장하지 않는다.
   - 분석만으로 목적을 달성하면 근거·판정·남은 불확실성으로 종료한다. 필요한 계측·테스트·검증된 수정만 구현 작업으로 전달한다.

3. **가장 작은 변경 — 승인된 구현이 필요한 경우**
   - 측정된 원인이나 반복 운영 문제에 직접 필요한 설정·script·workflow만 변경한다.
   - 문서/metadata 변경이 release/deploy side effect를 만들지 않는지 확인한다.

4. **검증**
   - syntax와 static contract에서 시작해 fake/isolated lifecycle, 동일 조건 재측정으로 확대한다.
   - Production 실행은 preflight → apply → postflight → rollback/recovery evidence를 분리한다.

5. **결과 보고**
   - baseline/change/after, trade-off, 미실행 운영 단계와 남은 한계를 구분한다.

## 중단 조건

- Cloud·Production·운영 DB·Secret·비용 작업의 별도 승인이 없음
- 대상 resource 또는 rollback 지점이 불명확함
- 성능 baseline 없이 tuning 선택을 요구함
- Secret 또는 운영 원시 값 노출 가능성
