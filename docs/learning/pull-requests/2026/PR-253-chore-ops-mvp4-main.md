---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 253
status: merged
taskId: OPS-010
author: guseoh
base: main
head: ops/sre/MVP4-CD-001
mergedAt: 2026-08-29T06:53:04Z
mergeCommit: 7f7b68b7f39d16fa6b77e500c2bfd89bf4a05bf7
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #253 chore(ops): MVP4 임시 main 자동 배포

## 작업 목적

## 작업  - 작업 ID: `MVP4-CD-001` - 작업 등급: **고위험** - 실행 구분: 저장소 변경  ## 목적과 범위  목적: MVP4 기능·UI 수정 기간 동안 매 merge마다 `target_sha`를 복사해 `Production Deploy`를 수동 실행하는 반복을 제거하고, 성공한 main image release를 기존 검증된 Production Deploy 경로로 자동 연결한다.  변경 범위: `.github/workflows/mvp4-temporary-auto-production-deploy.yml`과 `docs/runbook/MVP4-CD-001-temporary-auto-production-deploy.md`만 추가·수정한다. 기존 `production-deploy.yml`, `publish-production-images.yml`, AWS/EC2/RDS/SSM/Secret, control-adopt, migration 승인, rollback/rest…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/mvp4-temporary-auto-production-deploy.yml
- docs/runbook/MVP4-CD-001-temporary-auto-production-deploy.md

## 리뷰 결과

기록 없음

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

https://github.com/guseoh/pawcycle-commerce/pull/253
