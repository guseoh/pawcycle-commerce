# OPS-012 Platform/SRE → Tech Lead 인수인계

## 작업 정보
- 작업 ID: OPS-012
- 작업 등급: 고위험

## 전달 목적
Production application rollback 검증 범위와 남은 DB 복구 위험의 최종 판단을 요청한다.

## 대상 역할 또는 운영자
사용자/Tech Lead

## 입력 문서
OPS-012 보고서, OPS-010 Runbook, production 운영 아키텍처다.

## 완료된 작업
검증용 release 배포, 원래 release rollback과 최종 state·health·volume·HTTPS 결과를 기록했다.

## 사용 가능한 결과
Application rollback 완료와 actual production DB restore 미완료를 분리한 비민감 증거다.

## 관련 파일
`docs/reports/OPS-012/sre-report.md`와 갱신된 현재 상태 문서다.

## 인수 조건과 추적성
원래·검증용 SHA와 적용 전·중간·최종 검증이 보고서에 단계별로 연결된다.

## 확정된 결정
동일 production 계약의 application rollback은 검증 완료다. DB·schema·Flyway·volume은 변경하지 않았다.

## 미확정 결정
Actual production DB restore, RPO/RTO, 무중단·Blue/Green과 자동 배포다.

## 승인 필요 항목
미확정 범위는 별도 고위험 승인과 복구 계획이 필요하다.

## 소비자 입력
Tech Lead는 사용자 실행 사실과 비민감 추적성을 검토한다.

## 지켜야 할 규칙
미측정 중단 시간을 주장하거나 application rollback을 DB restore 완료로 확대하지 않는다.

## 적용·실행 방법
추가 운영 실행 없이 문서만 검토한다.

## 소비자 검증 포인트
최종 SHA state, 네 health, MySQL volume, 내외부 HTTPS와 DB 비변경 증거를 확인한다.

## QA 필요 여부
별도 QA 문서는 생략하고 사용자/Tech Lead 실제 실행을 독립 검증으로 사용한다.

## AI 리뷰에서 남은 확인 항목
PR review thread를 GitHub에서 확인한다.

## 알려진 위험
Actual production DB restore와 schema 호환 복구는 검증되지 않았다. 최종 `previous-sha`인 검증용 release는 원래 release와 Backend·Frontend 기능 차이가 없고 OPS-010 문서만 다르므로, 현재 기본 대상의 무인자 rollback은 application regression 복구를 증명하지 않는다.

## 남은 위험과 주의 사항
단일 장애 도메인, 미확정 RPO/RTO, 미측정 중단 시간과 자동화 부재가 남는다. 기능 차이가 있는 정상 release가 `previous-sha`를 갱신하기 전에는 승인된 대상 SHA를 명시한 `rollback.sh --sha`를 application 차이·GHCR image 존재·production 계약·DB schema 호환성 확인 뒤 사용하고 state 파일을 수동 편집하지 않는다.

## 다음 권장 작업
상태 표현 승인 후 필요하면 DB restore를 별도 고위험 작업으로 정의한다. 다음 기능 release 배포 뒤 `previous-sha`가 실제 기능 복구 후보를 가리키는지 확인한다.

## 완료 조건
Application rollback 완료와 DB restore 미완료가 혼동 없이 승인된다.

## 중단 조건
범위 확대, 실제 명령 또는 DB 변경이 필요하면 사용자 승인을 요청한다.
