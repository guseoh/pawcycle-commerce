# OPS-015 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-015
- 작업 등급: 일반
- 역할: Platform/SRE

## 작업 목적

EC2 기본 지표 `StatusCheckFailed`의 ALARM·OK 전이를 동일 SNS email topic으로 알리는 저장소 기반과 사용자 실행 Runbook을 제공한다.

## 승인 입력

사용자가 승인한 `StatusCheckFailed >= 1`, 60초, `EvaluationPeriods=2`·`DatapointsToAlarm=2`, ALARM·OK action, account ID·email 환경 변수 입력, 실제 AWS 미실행 및 제외 범위를 따른다.

## 변경 범위

AWS account·EC2 대상 사전 검증, 생성·계약 확인·부분 생성 정리 script, fake AWS 계약 test, production validation 연결과 OPS-015 Runbook을 추가한다.

## 변경하지 않은 범위

AWS 리소스·email confirmation 실제 실행, IAM, Agent, CPU·메모리·디스크·custom metric, 앱·컨테이너·로그·dashboard, Lambda·Discord, 자동 복구는 변경하지 않는다.

## 실행한 검증

`bash -n`, OPS-015 정적 계약 test, production contract validator와 task artifact validator를 실행한다. 실제 AWS CLI 호출은 실행하지 않는다.

## 실행하지 못한 검증과 이유

실제 SNS topic·subscription·CloudWatch alarm 생성, email confirmation, ALARM·OK 전이는 사용자의 AWS 계정에서만 실행 가능하며 이 작업의 명시적 제외 범위다.

## QA 필요 여부

별도 QA 문서는 생략한다. 제품 동작 변경이 없고 production static contract test와 Repository Validation으로 확인한다.

## QA 문서 경로 또는 생략 사유

제품·API·DB 변경이 없으며 실제 AWS 적용은 사용자 수동 Runbook 검증으로 분리한다.

## 인수인계 생략

후속 역할이 즉시 소비할 제품 계약이 없고, 실제 AWS 적용은 사용자/Tech Lead가 Runbook으로 수행하므로 별도 역할 인수인계를 작성하지 않는다.

## 위험과 제한

SNS email confirmation과 실제 ALARM·OK delivery는 병합 후 사용자 검증이 필요하다. cleanup은 전용 topic의 단일 입력 email subscription만 삭제한다.

## Git 결과

`ops/sre`는 `b2d6c189` 기준으로 재생성했고 `d657da3` (`feat(sre): EC2 상태 점검 알림 기반 구성`)을 push했다.

## PR 결과

[`#65`](https://github.com/guseoh/pawcycle-commerce/pull/65)를 `main` 대상으로 생성했으며 Draft가 아니다. 병합은 사용자 검토 뒤에만 수행한다.
