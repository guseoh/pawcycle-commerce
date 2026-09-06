---
name: platform-sre
description: >-
  PawCycle Commerce의 CI/CD, Docker/Compose, observability, performance measurement, deploy와 recovery 작업을 수행할 때 사용한다.
---

# Platform / SRE Skill

지속 책임은 `docs/roles/platform-sre.md`, 운영 불변식은 `infra/AGENTS.md`, 공통 안전 규칙은 루트 `AGENTS.md`를 따른다.

## 실행 절차

1. **목표와 실행 구분 확인**
   - 측정/저장소 준비인지 실제 운영 실행인지 구분한다.
   - 실제 운영 실행이면 별도 고위험 사용자 승인을 확인한다.

2. **현재 상태와 기준선 확보**
   - 관련 runtime, CI, metric, workload와 recovery contract를 확인한다.
   - 성능 작업은 같은 조건의 baseline을 먼저 확보한다.

3. **가장 작은 변경**
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
