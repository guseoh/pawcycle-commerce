# OPS-020 Platform/SRE → Tech Lead·운영자 인수인계

## 작업 정보

- 작업 ID: OPS-020
- 작업 등급: 고위험
- 문서 성격: OPS-020 저장소 준비 완료 시점의 역사적 인수인계
- 현재 실행 결과: `docs/reports/OPS-020/production-execution-report.md`
- 후속 Session Smoke 결과: `docs/reports/OPS-018/sre-report.md`

## 전달 목적

Production 인증 Smoke 회원 one-shot wrapper의 저장소 준비 결과와 실제 실행 승인 경계를 전달한다.

## 대상 역할 또는 운영자

사용자/Tech Lead와 별도 고위험 승인을 받은 Platform/SRE 운영자

## 입력 문서

OPS-019 Backend→SRE 인수인계와 OPS-020 보고서·Runbook이다.

## 완료된 작업

Context 이전 Backend gate를 소비하는 root·TTY wrapper, 공유 deploy lock, immutable image·실행 중 Backend identity·runtime/state·MySQL preflight, hardened one-shot Container, fake PTY와 격리 lifecycle 검증을 준비했다.

## 사용 가능한 결과

- Wrapper: `infra/production/create-production-auth-smoke-member.sh`
- Runbook: `docs/runbook/OPS-020-production-auth-smoke-member.md`
- 성공 계약: `PASS: production auth smoke member created`
- 당시 실제 상태: 저장소 준비만 완료, Production 회원 생성 미완료
- 현재 결과: Production 회원 생성은 `docs/reports/OPS-020/production-execution-report.md`, OPS-018 Session Smoke는 `docs/reports/OPS-018/sre-report.md`에 각각 완료 결과가 기록돼 있다.

## 확정된 결정

Credential은 `/dev/tty`에서 한 번씩 받고 stdin 두 줄로만 전달한다. 승인 SHA는 current Production SHA와 같아야 하며 Backend image는 기록된 registry digest와 OCI revision이 일치해야 한다. 성공 회원은 삭제하지 않고 OPS-018에 유지한다.

## 미확정 결정

저장소 준비 당시 실제 실행 시점, 실제 전용 email/password, 실행 뒤 OPS-018 착수 여부는 별도 고위험 사용자 결정이었다. 현재 완료 결과는 실행 보고서와 OPS-018 보고서를 따른다.

## 승인 필요 항목

저장소 준비 당시 Production DB에 회원 한 건을 생성하는 wrapper 실행과 이후 OPS-018 인증·Session Smoke는 각각 명시적 승인이 필요했다. 두 실행의 현재 완료 결과는 각 권위 보고서를 따른다.

## 소비자 검증 포인트

- TTY가 Docker 접근 전에 요구되고 password echo가 모든 종료 경로에서 복구되는가
- Credential이 argv·env·file·Docker log에 없고 stdin pipe에만 있는가
- current SHA·OCI revision·registry digest와 runtime/state mode가 일치하는가
- 실행 중인 Backend image identity·health가 승인 release와 일치하고 배포·rollback과 공유 lock으로 직렬화되는가
- one-shot Container에 port·restart·volume이 없고 security/resource limit이 완전한가
- Compose runtime env가 새 파일 없이 제한된 pipe로 전달되고 실행·cleanup이 유한 시간으로 제한되는가
- running Production service를 중지·재시작·변경하는 명령이 없는가
- PASS가 없거나 duplicate·성공 여부 불명확일 때 재실행을 금지하는가

## 검증 결과

Fake Docker·PTY와 격리 MySQL 8.4·Backend image lifecycle, production validator, 고위험 산출물 validator와 Repository Validation 결과를 OPS-020 보고서와 GitHub Checks에서 확인한다.

## 지켜야 할 규칙

`set -x`, credential 복제, raw Docker/DB 로그 공유, 직접 SQL 생성·수정, 회원 삭제·password reset, service·volume·schema 변경을 하지 않는다.

## 적용·실행 방법

실제 승인 전에는 Runbook 명령을 실행하지 않는다. 승인 후 root TTY에서 current SHA와 lowercase GHCR repository만 인자로 전달하고 credential은 prompt에만 입력한다.

## 실패와 정리 경계

Wrapper는 task-owned one-shot Container와 terminal echo만 정리한다. PASS가 없으면 회원 존재 여부를 추측하거나 재실행하지 않는다. Production 데이터 정리는 자동 수행하지 않는다.

## 알려진 위험

Docker client 중단과 DB commit 경계가 겹치면 PASS 없이 회원이 존재할 가능성을 운영에서 아직 검증하지 않았다. 이 경우 Backend/Tech Lead 판단 전까지 중단한다.

## 남은 위험과 주의 사항

저장소 준비 당시 실제 Production 회원 생성, credential 보관 책임, 로그인 가능성, CSRF/session rotation과 logout은 미검증이었다. 현재 회원 생성과 OPS-018 Session Smoke는 각 권위 보고서에 완료 결과가 기록돼 있으며, credential 수명과 운영자 관리 책임은 남아 있다. OPS-020 결과를 OPS-018 결과와 합쳐 기록하지 않는다.

## 다음 권장 작업

저장소 준비 당시 권장 순서는 Tech Lead가 PR의 TTY·immutable image·Container hardening과 테스트 격리를 검토하고, 병합 뒤 별도 고위험 승인으로 회원을 한 번 생성한 다음 OPS-018을 판단하는 것이었다. 현재 완료 결과와 남은 위험은 실행 보고서와 OPS-018 보고서를 따른다.

## 완료 조건

실제 실행 전 current release가 OPS-019를 포함하고 모든 preflight가 통과하며 운영자가 duplicate·불명확 결과 중단 기준을 이해해야 한다.

## 중단 조건

Mutable tag, non-TTY 입력, credential 외부 전달, current SHA 불일치, unhealthy MySQL, Production service·volume·schema 변경 또는 실제 실행 승인 부재면 중단한다.
