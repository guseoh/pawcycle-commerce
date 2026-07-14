---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 45
status: merged
taskId: FOUNDATION-004
author: guseoh
base: main
head: ops/sre
mergedAt: 2026-07-14T15:02:35Z
mergeCommit: 94f6ee7679d5c82808280d164457a65bf2ebc034
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #45 chore(infra): 로컬 QA 통합 환경 구성

## 작업 목적

## 작업 정보  - 작업 ID: FOUNDATION-004 - 역할: Platform/SRE - 작업 브랜치: `ops/sre` - 대상 브랜치: `main`  ## 관련 이슈  - 없음  ## 목적  - PR #44의 local-only QA bootstrap을 MySQL, Backend, Frontend와 한 origin으로 연결하는 재현 가능한 Docker Compose 환경과 QA Runbook을 제공한다. - PR #45의 유효한 AI 리뷰 지적을 최소 수정해 사용자 병합 검토가 가능한 상태로 만든다.  ## 변경 사항  - MySQL 8.4.10, Java 25 Backend, Node.js 24 Frontend와 Nginx stable의 local-only Compose 구성 - `/api/**`는 Backend, 나머지 요청은 Frontend로 전달하는 same-origin proxy - Nginx `Host`와 `X-Forwarded-Host`에 외부 port를 …

## 주요 변경

기록 없음

## 변경 파일

- docs/handoffs/FOUNDATION-004/sre-to-qa.md
- docs/reports/FOUNDATION-004/sre-report.md
- docs/runbook/FOUNDATION-004-local-integration.md
- infra/local-integration/.env.example
- infra/local-integration/backend.Dockerfile
- infra/local-integration/backend.Dockerfile.dockerignore
- infra/local-integration/compose.yaml
- infra/local-integration/frontend.Dockerfile
- infra/local-integration/frontend.Dockerfile.dockerignore
- infra/local-integration/nginx.conf
- infra/local-integration/smoke.ps1

## 리뷰 결과

- COMMENTED: 9

## CI 및 검증

기록 없음

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

기록 없음

## 연결된 Issue

기록 없음

## GitHub 링크

https://github.com/guseoh/pawcycle-commerce/pull/45
