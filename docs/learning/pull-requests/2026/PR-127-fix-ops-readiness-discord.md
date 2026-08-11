---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 127
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/OPS-AUTO-004
mergedAt: 2026-08-11T13:35:59Z
mergeCommit: 26c46c296f231ed2d23dcab735eaafdc22bd4169
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #127 fix(ops): Readiness Discord 실이벤트 분류 보완

## 작업 목적

## 작업  - 작업 ID: OPS-AUTO-004 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: 실제 `Production Release Readiness` workflow_run에서 `run-name`이 SHA를 포함해 `workflow_run.name`으로 전달될 때 Discord 알림이 suppressed 되는 회귀를 수정한다. - 변경 범위: `.github/scripts/collect-discord-context.py`, `scripts/test_discord_context.py` - 제외 범위: Discord sender·Webhook Secret·payload contract·Production Deploy·AWS·EC2·SSM·운영 DB 변경  ## 결정과 영향  - 중요한 결정: Readiness workflow 정체성은 표시 이름이 아니라 고정 workflow path `.github/work…

## 주요 변경

기록 없음

## 변경 파일

- .github/scripts/collect-discord-context.py
- scripts/test_discord_context.py

## 리뷰 결과

기록 없음

## CI 및 검증

- publish: queued

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

기록 없음

## 연결된 Issue

기록 없음

## GitHub 링크

https://github.com/guseoh/pawcycle-commerce/pull/127
