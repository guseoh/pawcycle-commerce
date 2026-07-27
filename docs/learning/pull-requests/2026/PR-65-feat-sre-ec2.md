---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 65
status: merged
taskId: OPS-015
author: guseoh
base: main
head: ops/sre
mergedAt: 2026-07-27T07:00:57Z
mergeCommit: 664dc097be7dc73f1aa454778f533034b95f6c7f
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #65 feat(sre): EC2 상태 점검 알림 기반 구성

## 작업 목적

## 작업 정보  - 작업 ID: OPS-015 - 작업 등급: 일반 - 역할: Platform/SRE - 대상 브랜치: `main`  ## 목적  EC2 `StatusCheckFailed`의 ALARM·OK 전이를 같은 SNS email topic으로 알리는 저장소 기반과 사용자 수동 Runbook을 추가합니다.  ## 변경 범위  - `PAWCYCLE_ALERT_ACCOUNT_ID`와 STS Account·EC2 instance 사전검증 - `DatapointsToAlarm=2`, `ActionsEnabled=true`, 단일 confirmed email subscription 계약 - 읽기 전용 사전검사, 부분 생성 정리, fake AWS 회귀 테스트 - `docs/reports/OPS-015/sre-report.md`: PCC_V2의 기본 요구가 아니라 현재 task artifact validator 호환을 위해 포함  ## 검증  - `bash infra/productio…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/validate-conventions.yml
- docs/reports/OPS-015/sre-report.md
- docs/runbook/OPS-015-ec2-status-check-alarm.md
- infra/production/cleanup-ec2-status-check-alarm.sh
- infra/production/create-ec2-status-check-alarm.sh
- infra/production/ec2-status-check-alarm-common.sh
- infra/production/test-ec2-status-check-alarm.sh
- infra/production/validate-production-contracts.py

## 리뷰 결과

- COMMENTED: 15

## CI 및 검증

- publish: in_progress

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

기록 없음

## 연결된 Issue

기록 없음

## GitHub 링크

https://github.com/guseoh/pawcycle-commerce/pull/65
