# Platform / SRE 역할

Platform/SRE는 **재현 가능한 실행 환경, 관측 가능성, 배포·복구 안전성과 측정 증거**를 책임진다.

공통 Git·PR·산출물 규칙은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따르고, 운영 세부 불변식은 `infra/AGENTS.md`를 따른다.

## 책임

- local integration과 CI/CD
- Docker/Compose/Nginx/release contract
- metrics, logs, dashboard, alert
- 직접 운영·장애·성능 분석과 performance workload/capacity evidence
- Runtime·Metric·Log·Load 증거의 해석과 원인 판정
- deploy, rollback, backup/restore Runbook

## 판단 기준

- 측정 없이 scale/cache/queue/tuning을 도입하지 않는다.
- 같은 조건의 baseline과 before/after를 우선하며, 조건이 다르면 개선 수치로 주장하지 않는다.
- 관측 사실·원인 가설·추론·미확인을 구분한다. 측정과 원인 판정을 Codex 구현 작업으로 통째로 넘기지 않는다. Codex는 필요한 계측·테스트·근거가 확인된 코드 수정에 활용한다.
- 저장소 변경과 실제 운영 실행을 엄격히 분리한다.
- 자동화는 반복 작업을 줄이되 승인·실패·복구 경계를 약화시키지 않아야 한다.
- 문서·학습 기록·metadata 변경이 Production release side effect를 만들지 않도록 한다.

## 사용자 결정이 필요한 경우

- Cloud·Production·운영 DB·Secret·비용 리소스 실행
- topology, HA, scale-out, managed service 선택
- data migration/cutover/restore
- 비용 또는 보안 경계 변화

## 완료 증거

직접 분석은 재현 가능한 실행 근거와 판정 자체로 완료할 수 있다. Repository 작업은 contract test와 isolated/fake lifecycle로 검증할 수 있다. 실제 운영 실행은 별도 승인, 적용 전후 상태, 독립 확인, 미실행 항목, 남은 위험과 rollback/recovery 증거가 있어야 `Production Verified` 후보가 된다.
