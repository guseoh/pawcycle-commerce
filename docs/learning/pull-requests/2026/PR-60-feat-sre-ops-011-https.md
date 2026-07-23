---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 60
status: merged
taskId: OPS-011
author: guseoh
base: main
head: ops/sre
mergedAt: 2026-07-23T07:04:27Z
mergeCommit: 2504ebfe445afba854cb7c4e9757d5aca2109e39
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #60 feat(sre): OPS-011 HTTPS 운영 기반 구성

## 작업 목적

## 작업 정보  - 작업 ID: OPS-011 - 작업 등급: 고위험 - 역할: Platform/SRE - 작업 브랜치: `ops/sre` - 대상 브랜치: `main`  ## 목적  OPS-010 단일 release와 MySQL volume을 유지하면서 DuckDNS·Let's Encrypt HTTP-01 기반 HTTPS bootstrap, 발급, 갱신과 release 복구 절차를 추가하고 PR 리뷰에서 확인된 hostname·release gate 결함을 보완합니다.  ## 변경 사항  - 인증서 없는 bootstrap HTTP와 HTTP-01 challenge 경로 - 인증서 검증 뒤 HTTP→HTTPS redirect와 기존 Frontend·Backend HTTPS proxy - root state의 mode `600` 승인 DuckDNS hostname과 생성 Nginx config - Certbot HTTP-01 발급과 후보 인증서 SAN·최소 유효기간 검증 뒤에만 h…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/validate-conventions.yml
- docs/handoffs/OPS-011/sre-to-tl.md
- docs/reports/OPS-011/sre-report.md
- docs/runbook/OPS-011-production-https.md
- docs/runbook/README.md
- infra/production/compose.yaml
- infra/production/https.sh
- infra/production/nginx.conf
- infra/production/nginx.https.conf
- infra/production/release-common.sh
- infra/production/test-production-compose.sh
- infra/production/test-production-nginx.sh
- infra/production/test-production-scripts.sh
- infra/production/validate-production-contracts.py

## 리뷰 결과

- COMMENTED: 17

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

https://github.com/guseoh/pawcycle-commerce/pull/60
