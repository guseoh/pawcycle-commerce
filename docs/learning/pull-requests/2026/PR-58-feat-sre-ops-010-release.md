---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 58
status: merged
taskId: OPS-010
author: guseoh
base: main
head: ops/sre
mergedAt: 2026-07-20T11:02:32Z
mergeCommit: 7e243a12a768c23020770748c090f85877074eed
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #58 feat(sre): OPS-010 운영 단일 release 배포 기반 구성

## 작업 목적

## 작업 정보  - 작업 ID: OPS-010 - 작업 등급: 고위험 - 역할: Platform/SRE - 작업 브랜치: `ops/sre` - 대상 브랜치: `main`  ## 목적  DEPLOY-002 production 단일 release 기반을 구현해 Backend·Frontend를 동일 commit SHA로 GHCR에 게시하고, 사용자가 EC2에서 Secret 분리·MySQL 보존·상태 확인·이전 SHA rollback을 수행할 수 있게 합니다.  ## 변경 사항  - production Compose·Nginx와 Backend·Frontend Dockerfile - `contents: read`, `packages: write`만 사용하는 GHCR image workflow - 같은 `github.sha` tag·revision label과 registry digest preflight - SSM 필수 parameter fail-closed, mode 600 runtim…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/publish-production-images.yml
- .github/workflows/validate-conventions.yml
- docs/handoffs/OPS-010/sre-to-tl.md
- docs/reports/OPS-010/sre-report.md
- docs/runbook/OPS-010-production-single-release.md
- docs/runbook/README.md
- infra/production/backend.Dockerfile
- infra/production/backend.Dockerfile.dockerignore
- infra/production/compose.yaml
- infra/production/deploy.sh
- infra/production/frontend.Dockerfile
- infra/production/frontend.Dockerfile.dockerignore
- infra/production/materialize-ssm-env.sh
- infra/production/nginx.conf
- infra/production/release-common.sh
- infra/production/release.env.example
- infra/production/rollback.sh
- infra/production/ssm-parameters.env.example
- infra/production/test-production-compose.sh
- infra/production/test-production-scripts.sh
- 외 1개

## 리뷰 결과

- COMMENTED: 10

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

https://github.com/guseoh/pawcycle-commerce/pull/58
