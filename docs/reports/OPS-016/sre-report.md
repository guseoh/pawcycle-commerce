# OPS-016 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-016
- 작업 등급: 고위험
- 역할: Platform/SRE
- 실제 검증일: 2026-07-27

## 작업 목적

사용자가 production에서 직접 검증한 EC2 `StatusCheckFailed` SNS email 알림 결과를 비민감 증거로 기록하고, 실행 중 확인된 AWS CLI NoRegion 결함과 PR review 지적을 재발 방지한다.

## 입력 문서

OPS-016 사용자 승인과 실행 결과, OPS-015 Runbook, production 알림 script 계약, production 운영 아키텍처와 PR review thread를 입력으로 사용했다.

## 승인 입력

OPS-015 저장소 기반 준비는 일반 작업의 역사 기록으로 보존한다. 실제 production 알림 검증과 NoRegion 보완은 사용자가 별도로 승인한 고위험 OPS-016으로 기록하며, 이번 보완에서는 추가 AWS 실행을 승인하지 않았다.

## 명시적 승인 근거 (고위험 필수)

사용자는 기존 production alarm의 계약 확인, 수동 ALARM·OK 전이와 email 수신 확인을 승인했다. 현재 요청에는 최초 NoRegion 실패, 환경 region 설정 후 기존 계약 확인, ALARM·OK email 수신, confirmed subscription 검증과 cleanup 미실행 결과가 명시됐다.

## 변경 범위

STS 호출의 승인 region 명시를 유지하고 fake AWS가 region 인자를 엄격히 검증하도록 보완한다. OPS-016 보고서·인수인계와 현재 운영 상태 문서에 실제 검증 결과를 분리한다.

## 변경하지 않은 범위

기존 OPS-015 보고서의 등급·내용은 변경하지 않는다. 이번 보완에서 AWS CLI create·verify·set-alarm-state·cleanup과 AWS 리소스 생성·변경·삭제를 실행하지 않았다. IAM, workflow, dependency, application·DB 동작도 변경하지 않았다.

## 주요 결과

최초 create는 AWS CLI 기본 region 미설정으로 NoRegion에서 중단됐으며 리소스 변경 전 단계였다. 사용자가 `AWS_REGION`·`AWS_DEFAULT_REGION`을 승인된 서울 region으로 설정한 뒤 create는 기존 alarm 계약이 변경 없이 일치한다고 확인했다. 이번 실행에서 새 리소스를 생성하지 않았다.

사용자는 수동 ALARM 전이와 OK 전이의 email을 모두 수신했다. Verify는 단일 승인 email subscription이 confirmed 상태이고 alarm 계약이 변경 없이 일치한다고 확인했다. Cleanup은 실행하지 않았다.

## 변경 파일

알림 공통 script의 기존 region 전달을 유지하고 fake AWS test, OPS-016 보고서·인수인계와 production 운영 아키텍처를 변경한다. OPS-015 Runbook의 명시적 STS region 설명은 절차 원본으로 유지한다.

## 결정 상태

단일 EC2의 기본 `StatusCheckFailed` 지표에서 동일 SNS topic으로 전송되는 ALARM·OK email 알림은 Verified 판단 근거가 준비됐다. 자동 복구나 포괄적 관측성 완료로 확대하지 않는다.

## API 영향

Application API 계약 변경은 없다.

## DB 영향

DB·schema·Flyway·volume 변경은 없다. Actual production DB restore의 미완료 상태도 변경하지 않는다.

## 보안 영향

실제 account ID, instance ID, email, ARN, alarm 이름, 화면과 원시 email 내용을 저장소나 보고서에 기록하지 않았다.

## 운영 영향

STS 대상 확인은 `PAWCYCLE_ALERT_REGION`에서 검증된 서울 region을 명시적으로 사용한다. Fake AWS는 정확히 하나의 `--region`과 바로 다음의 정확한 값을 검사해 누락·중복·접두사 오값 회귀를 차단한다. 알림 리소스의 계약과 동작은 변경하지 않는다.

## 성능 영향

성능 측정이나 변경은 없다.

## 실행한 검증

- Bash 문법 검사: 통과
- OPS-015 fake AWS 계약 test: 통과
- production contract validator: 통과
- OPS-016 고위험 task artifact validator: 통과
- 기존 OPS-015 보고서와 `origin/main` 비교: 일치
- 관련 Markdown·UTF-8 문자 인코딩 검사: 통과
- `git diff --check`: 통과
- 원격 Repository Validation: 동적 상태를 문서에 고정하지 않고 GitHub Checks를 권위 원본으로 확인

## 적용 전 검증 (고위험 필수)

사용자의 첫 create는 AWS CLI 기본 region이 없어 NoRegion으로 실패했으며 STS 대상 확인 단계에서 중단돼 AWS 리소스 변경 전 상태였다. 이후 승인 region을 환경에 설정하고 같은 입력 경계에서 재검증했다.

## 적용 후 검증 (고위험 필수)

재검증한 create는 기존 alarm 계약이 변경 없이 일치한다고 확인했다. ALARM·OK email을 모두 수신했고 verify는 confirmed subscription과 기존 alarm 계약 일치를 확인했다. 기존 리소스 검증이므로 새 리소스 생성으로 기록하지 않는다.

## 독립 검증 (고위험 필수)

구현자가 아닌 사용자/Tech Lead가 production에서 ALARM·OK email 수신과 verify 결과를 직접 확인했다. 저장소에서는 fake AWS가 create, confirmed verify, pending verify와 cleanup 성공·사전 중단 경로의 STS 서울 region marker를 검증한다.

## 실행하지 못한 검증과 이유

추가 AWS 실행은 명시적 제외 범위이므로 create·verify·set-alarm-state·cleanup을 다시 실행하지 않았다. 실제 EC2 장애를 유발하지 않았고 실제 `StatusCheckFailed` 발생 시 탐지 시간이나 복구 시간도 측정하지 않았다. Application 전체 test는 application 동작을 변경하지 않아 로컬에서 반복하지 않았다.

## QA 필요 여부

별도 QA 문서는 생략한다. 사용자/Tech Lead의 실제 운영 확인과 fake AWS 계약 test를 독립 검증으로 사용한다.

## QA 문서 경로 또는 생략 사유

제품·API·DB 변경이 없고 production 알림의 제한된 운영 계약과 비민감 증거만 보완한다.

## AI 리뷰 반영 여부

OPS-015 역사 기록 보존, STS region 인자 단위 검증, confirmed verify와 cleanup 경로 회귀 검증이라는 세 유효 지적을 반영했다.

## AI 리뷰 미반영 항목과 이유

일반 Docstring Coverage 경고는 Bash·Markdown 중심 변경이며 Python 공개 API를 추가하지 않아 적용하지 않는다.

## 적용 방법

추가 운영 실행 없이 변경된 script 계약과 비민감 검증 문서를 검토한다.

## 복구·롤백 증거 (고위험 필수)

최초 실패는 AWS 리소스 변경 전이었고 후속 실행도 기존 계약이 변경 없이 일치함을 확인했으므로 운영 리소스 rollback은 발생하지 않았다. Cleanup은 실행하지 않아 기존 알림 리소스가 유지됐다. 저장소 변경은 일반 revert PR로 복구할 수 있다.

## 위험과 제한

수동 `set-alarm-state` 전이의 email 수신을 확인했을 뿐 실제 EC2 장애를 유발하지 않았다. 이 알림은 EC2 기본 `StatusCheckFailed`만 다루며 CPU·메모리·디스크·application 상태와 자동 복구를 제공하지 않는다.

## 남은 위험

Cleanup의 실제 운영 실행은 검증하지 않았다. 단일 host 장애 도메인과 email 전달 채널 의존성이 남고, actual production DB restore 등 OPS-016 밖의 기존 미완료 운영 항목은 그대로다.

## 다음 작업

Tech Lead가 비민감 증거와 제한을 검토해 최소 EC2 장애 알림의 Verified 상태를 최종 판단한다.

## Git 결과

기존 `ops/sre`의 PR #68에 하나의 후속 commit을 push한다. 정확한 head는 Git과 PR을 권위 원본으로 확인한다.

## PR 결과

PR #68을 `main` 대상으로 유지하고 자동 병합하지 않는다. 현재 head와 Repository Validation 상태는 GitHub를 권위 원본으로 확인한다.
