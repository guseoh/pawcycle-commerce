---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 156
status: merged
taskId: OPS-DB-003
author: guseoh
base: main
head: ops/sre/OPS-DB-003
mergedAt: 2026-08-18T08:14:22Z
mergeCommit: 7ddf7322283752956d27dc6dbfd7e26b22029021
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #156 fix(sre): Windows AWS CLI CRLF preflight 호환성 보정

## 작업 목적

## 작업  - 작업 ID: OPS-DB-003 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE - Related: #155  ## 목적과 범위  - 목적: Windows AWS CLI v2의 `--output text`가 CRLF를 반환할 때 RDS read-only preflight의 EC2 Security Group 비교가 오탐 실패하는 문제를 보정합니다. - 변경 범위: `rds-read-only-preflight.sh`의 describe-only AWS 출력에서 `\r`만 제거해 LF/CRLF 실행 환경을 동일하게 처리합니다. - 제외 범위: 실제 AWS/RDS/Production mutation, SG/subnet/RDS 생성, datasource 변경, unrelated refactor입니다.  ## 결정과 영향  - 기존 AWS describe-only allowlist와 `set -Eeuo pipefail` fail-closed …

## 주요 변경

기록 없음

## 변경 파일

- infra/production/rds-read-only-preflight.sh
- infra/production/test-rds-readiness.sh

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

https://github.com/guseoh/pawcycle-commerce/pull/156
