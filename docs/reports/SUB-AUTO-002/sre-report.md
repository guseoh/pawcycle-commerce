# SUB-AUTO-002 Platform/SRE 보고서

## 작업

- 작업 ID: `SUB-AUTO-002`
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Platform/SRE

## 목적

SUB-AUTO-001 Application을 Scheduler OFF로 배포하고, V9~V11과 aggregate invariant를 확인한 뒤 별도 명시 입력으로만 활성화·중단할 수 있는 Production 저장소 계약을 준비한다.

## 결과 또는 증거

- Production runtime의 automation enabled, batch size, fixed delay를 명시 SSM/materialized env로 고정하고 Application deploy·rollback에서 OFF를 강제했다.
- 현재와 target Release의 Flyway migration bundle이 다르면 target 실패 자동복귀와 수동 pre-migration rollback을 차단하고 MySQL volume을 보존한다.
- read-only preflight는 V9~V11, table/index/unique, due candidate count·oldest date, Order·Schedule·snapshot·future Schedule aggregate invariant와 automation metric만 출력한다.
- activation/deactivation은 Application SHA 변경과 분리된 command이며 candidate maximum, health와 기존 smoke를 fail-closed gate로 사용한다.
- migration 실패·부분 적용에서 retry·repair·down migration·DROP·직접 데이터 수정을 금지하고 forward-fix 또는 기존 승인 restore를 별도 사용자 결정으로 남겼다.
- 변경 Shell `bash -n`, `test-production-scripts.sh`, `test-rollback-control-compatibility.sh`, `validate-production-contracts.py`, SUB-AUTO-002 task artifact validator를 로컬에서 통과했다.
- 실제 Production, AWS, 운영 DB, Secret과 restore 실행: 0건.

## 위험 또는 제한

- batch size, fixed delay, candidate 예상 규모와 failure 이상 판정값은 확정하지 않았다. 실제 관측과 사용자 승인이 필요하다.
- read-only aggregate query와 fake lifecycle test는 Production 데이터 안전성이나 실제 V9~V11 적용 성공을 증명하지 않는다.
- migration partial application 뒤 old Release 자동·수동 rollback은 의도적으로 사용할 수 없다. forward-fix 또는 OPS-025 restore는 별도 고위험 실제 운영 승인과 증거가 필요하다.
- Scheduler 활성화 뒤 주문·구독 상태 전이, 데이터 손실 또는 고객 영향 판정은 Product Owner/Tech Lead가 다시 결정한다.
