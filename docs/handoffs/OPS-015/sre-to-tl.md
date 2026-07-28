# OPS-015 Platform/SRE → Tech Lead 인수인계

## 작업 정보

- 작업 ID: OPS-015
- 작업 등급: 고위험

## 전달 목적

Production의 최소 EC2 장애 email 알림을 Verified로 판단할 수 있는 비민감 증거와 남은 제한을 전달한다.

## 대상 역할 또는 운영자

사용자/Tech Lead

## 입력 문서

OPS-015 보고서, OPS-015 Runbook, production 알림 script 계약과 production 운영 아키텍처다.

## 완료된 작업

사용자가 기존 `StatusCheckFailed` alarm의 ALARM·OK email 수신, confirmed subscription과 alarm 계약 일치를 확인했다. STS 호출에는 검증된 서울 region을 명시하고 fake AWS test로 회귀를 차단했다.

## 사용 가능한 결과

첫 NoRegion 실패가 리소스 변경 전이었다는 사실, 이후 기존 계약이 변경 없이 일치했다는 사실과 ALARM·OK email 수신 결과다. 이번 실행에서 새 AWS 리소스를 생성하지 않았다.

## 관련 파일

`docs/reports/OPS-015/sre-report.md`, `docs/runbook/OPS-015-ec2-status-check-alarm.md`, `infra/production/ec2-status-check-alarm-common.sh`와 관련 fake AWS test다.

## 확정된 결정

승인된 서울 region의 단일 EC2 기본 `StatusCheckFailed` alarm은 같은 SNS topic으로 ALARM·OK email을 전달했다. Verify는 승인 email subscription의 confirmed 상태와 alarm 계약 일치를 확인했다.

## 미확정 결정

실제 EC2 장애 발생 시 탐지·복구 시간, cleanup 실행, 다른 metric·application 알림과 자동 복구는 검증하거나 결정하지 않았다.

## 승인 필요 항목

Cleanup, 알림 범위 확대나 자동 복구는 각각 별도 고위험 사용자 승인이 필요하다.

## 소비자 검증 포인트

Tech Lead는 첫 실패가 변경 전이었는지, 후속 확인이 새 생성이 아닌 기존 계약 검증인지, ALARM·OK 수신과 confirmed subscription이 분리돼 기록됐는지 확인한다.

## 검증 결과

Bash 문법, fake AWS 계약 test, production contract validator, 고위험 task artifact validator, Markdown·UTF-8 검사와 `git diff --check`가 통과했다. GitHub의 동적 SHA·CI·PR 상태는 저장소에 고정하지 않는다.

## 지켜야 할 규칙

실제 account ID, instance ID, email, ARN, alarm 이름과 원시 email 내용을 기록하지 않는다. 수동 alarm state 전이를 실제 EC2 장애 복구 증거로 확대하지 않는다.

## 적용·실행 방법

추가 AWS 실행 없이 보고서와 변경 diff를 검토한다.

## 알려진 위험

수동 ALARM·OK email 수신만 확인했으며 실제 `StatusCheckFailed` 장애를 유발하지 않았다. Email 채널과 단일 host 장애 도메인에 의존한다.

## 남은 위험과 주의 사항

Cleanup은 실행하지 않았고 CPU·메모리·디스크·application 알림, 자동 복구와 actual production DB restore는 완료 범위가 아니다.

## 다음 권장 작업

증거 범위를 승인하면 최소 EC2 장애 알림만 Verified로 유지한다. 확대된 관측성이나 복구는 별도 작업으로 정의한다.

## 완료 조건

최소 `StatusCheckFailed` ALARM·OK email 검증 완료와 미검증 범위가 혼동 없이 승인된다.

## 중단 조건

추가 AWS 실행, 리소스 변경, 민감정보 기록이나 OPS-015 밖의 운영 완료 상태 변경이 필요하면 중단하고 사용자 승인을 요청한다.
