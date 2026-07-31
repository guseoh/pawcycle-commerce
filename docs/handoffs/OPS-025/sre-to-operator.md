# OPS-025 Platform/SRE → Production 운영자 인수인계

## 작업 정보

- 작업 ID: OPS-025
- 작업 등급: 고위험
- 전달 역할: Platform/SRE

## 전달 목적

별도 승인된 Actual Production DB 논리 restore 훈련에서 source volume을 보존하며 candidate 준비·cutover·검증·복귀를 수행하도록 안전 경계를 전달한다.

## 대상 역할 또는 운영자

Product Owner·Tech Lead이자 실제 Production 명령을 직접 승인·실행·검증할 사용자다.

## 입력 문서

- `docs/runbook/OPS-025-production-db-restore.md`
- `docs/runbook/OPS-013-production-db-backup-restore.md`
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/reports/OPS-025/sre-report.md`

## 사용 가능한 결과

- OPS-013 completion marker와 사전 `restore-verify` 성공 record에 결합된 candidate 준비 명령
- deploy·rollback이 유지하는 `active-mysql-volume` 상태
- 쓰기 중단·공유 lock·source manifest 후 cutover
- candidate 실패 시 source Application 자동 복귀와 명시적 `revert`
- fake Docker·격리 lifecycle·production validator

## 미결정 사항 또는 승인 필요 항목

- Actual Production restore 훈련 실행 여부와 시점
- 허용 중단 구간과 복귀 판단자
- 핵심 데이터 의미 검증 기준
- source·candidate volume 보존 기간과 별도 삭제 승인
- 물리 EBS 복구, RPO/RTO와 용량·비용 결정

## 검증 포인트

- 실행 전 Control·Application SHA, 네 health·내부 Smoke·외부 HTTPS와 active mount
- OPS-013 completion marker·사전 `restore-verify`
- candidate schema fingerprint·Flyway history·핵심 table manifest와 source 분리
- cutover 후 active state·실제 mount, 네 health·내부 Smoke·외부 HTTPS·핵심 데이터
- 실패 또는 복귀 후 source state·mount·Application SHA와 두 volume 보존

## 중단 조건

- Secret·backup ID·bucket·host·IP·domain·row 값 노출 가능성
- state·label·lock·Control·Application·volume 관계 불명확
- schema downgrade, Flyway history 수정, raw datadir 복사나 in-place restore 필요
- source 또는 EBS 자체가 읽히지 않음
- candidate 검증 불일치나 데이터 의미 판정 필요
- source·candidate 동시 보존 용량 부족 또는 새 비용 결정 필요

## 남은 위험 또는 주의 사항

이 작업은 저장소 계약 준비만 완료하며 Actual Production restore 증거가 아니다. Script 성공은 외부 사용자 경로와 데이터 의미 검증을 대체하지 않는다. 실패한 source·candidate volume과 state record를 자동 또는 임의 삭제하지 않는다.
