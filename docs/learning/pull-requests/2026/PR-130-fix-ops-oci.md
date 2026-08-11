---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 130
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/OPS-AUTO-006
mergedAt: 2026-08-11T16:47:50Z
mergeCommit: 14d326d6bbaca7c7646969e713f7b5405bcb46f3
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #130 fix(ops): OCI 이미지 검증 호환성 보완

## 작업 목적

## 작업 - 작업 ID: `OPS-AUTO-006` - 작업 등급: 고위험 - 실행 구분: 저장소 변경  ## 목적과 범위 - 목적: Production Deploy run #31512736184에서 실제 OCI image가 존재함에도 GitHub runner의 `docker manifest inspect`가 Frontend manifest를 `unsupported manifest format`으로 거부한 검증 호환성 결함을 제거합니다. - 변경 범위: Production Deploy와 Production Release Readiness의 image 존재 검증을 `docker buildx imagetools inspect`로 교체합니다. - 제외 범위: Release contract, Control contract, SSM document, IAM, DB migration, Production resource와 application state는 변경하지 않습니다.  ## 검증 -…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/production-deploy.yml
- .github/workflows/production-release-readiness.yml

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

https://github.com/guseoh/pawcycle-commerce/pull/130
