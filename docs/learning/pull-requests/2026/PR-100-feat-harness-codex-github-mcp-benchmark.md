---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 100
status: merged
taskId: HARNESS-AGENT-006
author: guseoh
base: main
head: ops/sre/HARNESS-AGENT-006
mergedAt: 2026-08-08T05:02:28Z
mergeCommit: a5cfa28bbc5f24a4d1162fbe52e6e420989d9dd9
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #100 feat(harness): Codex GitHub MCP 읽기 검증과 Benchmark

## 작업 목적

## 작업  - 작업 ID: HARNESS-AGENT-006 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: Codex GitHub MCP의 대상 저장소 읽기 연결과 실제 Tool allowlist를 검증하고, 고정 Benchmark A·B·C·D를 각각 3회 실행해 ChatGPT Connector 대조군과 분리 비교한다. - 변경 범위: schema 3.0 Codex 실험군 JSONL, 비교 요약·SVG, renderer arm 분리와 회귀 테스트 - 제외 범위: 실제 업무 시범 운영, GitHub MCP 쓰기 확대, 제품·API·DB 변경, Production·Cloud·운영 DB·Secret·비용 리소스 실행, 자동 병합  ## 결정과 영향  - 중요한 결정: connector에 쓰기 권한과 Tool이 함께 노출되므로 credential 수준 read-only로 표현하지 않고, 대상 저장소 인자와 25개 읽…

## 주요 변경

기록 없음

## 변경 파일

- docs/reports/HARNESS-AGENT-006/benchmark-results-codex-github-mcp.jsonl
- docs/reports/HARNESS-AGENT-006/benchmark-summary.md
- docs/reports/HARNESS-AGENT-006/comparison.svg
- scripts/render-agent-benchmark-charts.py
- scripts/test_agent_benchmark_tools.py

## 리뷰 결과

- COMMENTED: 11

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

https://github.com/guseoh/pawcycle-commerce/pull/100
