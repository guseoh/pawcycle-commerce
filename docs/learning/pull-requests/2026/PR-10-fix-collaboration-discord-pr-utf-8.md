---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 10
status: merged
taskId: OPS-002
author: guseoh
base: main
head: ops/sre
mergedAt: 2026-07-03T14:41:02Z
mergeCommit: 0df520c7e55645561e894474b11191d4bd32d1c9
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #10 fix(collaboration): Discord 요청과 PR 본문 UTF-8 보완

## 작업 목적

## 작업 정보  - 작업 ID: `OPS-002` - 역할: Platform/SRE - 작업 브랜치: `ops/sre` - 대상 브랜치: `main`  ## 목적  Discord Webhook 요청의 HTTP 403 문제를 확인하기 위해 요청에 명시적인 `User-Agent`를 추가한다.  Windows를 포함한 작업 환경에서 PR 한글 본문이 손상되지 않도록 UTF-8 본문 파일 기반 PR 작성 규칙과 검증을 추가한다.  ## 변경 내용  - Discord Webhook 요청에 고정 공개 `User-Agent` 추가 - 필수 요청 헤더와 Webhook URL 비노출 테스트 추가 - PR 본문 UTF-8 작성 규칙 추가 - 공통 Pull Request 템플릿 정리 - 명백한 PR 본문 문자 손상 검증 추가 - Repository Validation에 PR 본문 인코딩 검증 연동 - HTTP 403 재확인 및 병합 후 운영 확인 절차 추가 - OPS-002 보고서와 TL 인수인계 …

## 주요 변경

기록 없음

## 변경 파일

- .github/pull_request_template.md
- .github/scripts/send-discord-notification.py
- .github/workflows/validate-conventions.yml
- AGENTS.md
- docs/handoffs/OPS-002/sre-to-tl.md
- docs/reports/OPS-002/sre-report.md
- docs/runbook/collaboration-automation.md
- scripts/test_send_discord_notification.py
- scripts/test_validate_pr_body_encoding.py
- scripts/validate-pr-body-encoding.py

## 리뷰 결과

기록 없음

## CI 및 검증

- Discord collaboration notification: in_progress

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

기록 없음

## 연결된 Issue

기록 없음

## GitHub 링크

https://github.com/guseoh/pawcycle-commerce/pull/10
