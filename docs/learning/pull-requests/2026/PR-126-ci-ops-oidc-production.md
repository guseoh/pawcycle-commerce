---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 126
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/OPS-AUTO-003
mergedAt: 2026-08-11T10:17:06Z
mergeCommit: 3acd70b5fa3401195c63f497b41b9780451f8281
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #126 ci(ops): OIDC Production 배포 계약 추가

## 작업 목적

## 작업  - 작업 ID: OPS-AUTO-003 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: GitHub Environment 승인 뒤 OIDC와 제한 SSM Document로 기존 Production deploy.sh를 호출하는 저장소 계약 준비 - 변경 범위: Production deploy workflow, PawCycle 전용 SSM Document 정의, Production 계약 정적 검증 - 제외 범위: AWS IAM Role/OIDC Provider/Environment protection/SSM Document 생성, AWS·EC2·Production 실행, Secret 변경, 기존 deploy.sh 변경  ## 결정과 영향  - 중요한 결정: target_sha만 수용하고 main 포함·GHCR 이미지 확인 뒤 Environment variables의 Role/Region/고정 tag ta…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/production-deploy.yml
- infra/production/pawcycle-production-deploy-ssm-document.json
- infra/production/validate-production-contracts.py

## 리뷰 결과

- COMMENTED: 1

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

https://github.com/guseoh/pawcycle-commerce/pull/126
