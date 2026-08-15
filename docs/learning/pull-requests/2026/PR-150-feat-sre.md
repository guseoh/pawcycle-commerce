---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 150
status: merged
taskId: OPS-AUTO-010
author: guseoh
base: main
head: ops/sre-OPS-AUTO-010
mergedAt: 2026-08-15T11:01:23Z
mergeCommit: 61e0837123f8b8008de0c20dcf017fd6e3639207
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #150 feat(sre): 백엔드 상태 다중 채널 알림

## 작업 목적

## 작업  - 작업 ID: OPS-AUTO-010 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - OPS-AUTO-009 최종 진단의 비정상 상태를 Discord와 Slack Incoming Webhook으로 독립 전송한다. - `NORMAL`은 무알림, 비정상·불확실 입력은 fail-closed 알림 경로를 사용한다. - 실제 webhook 생성·Secret 등록·cron/systemd 활성화·Production 전송은 이 PR에서 제외한다.  ## 핵심 결정  - 진단 결과를 raw bytes 단계에서 검증하고 NUL/CR, invalid UTF-8, symlink/FIFO 등 non-regular file, 과대·형식 손상 입력을 `UNKNOWN`으로 처리한다. - `diagnose-backend-state.sh`의 상태 결정표와 세 필드 조합이 정확히 일치할 때만 trusted 입력으로 인정한다. - Discor…

## 주요 변경

기록 없음

## 변경 파일

- .github/scripts/send-discord-notification.py
- docs/runbook/OPS-AUTO-010-backend-state-alert.md
- infra/production/dispatch-backend-state-alert.sh
- infra/production/send-slack-notification.py
- infra/production/test-dispatch-backend-state-alert.sh
- infra/production/test_send_slack_notification.py
- scripts/test_send_discord_notification.py

## 리뷰 결과

- COMMENTED: 28

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

https://github.com/guseoh/pawcycle-commerce/pull/150
