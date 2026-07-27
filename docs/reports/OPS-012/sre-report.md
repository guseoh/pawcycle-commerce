# OPS-012 Platform/SRE 작업 보고서

## 작업 정보
- 작업 ID: OPS-012
- 작업 등급: 고위험
- 역할: Platform/SRE
- 실제 검증일: 2026-07-27

## 작업 목적
사용자가 production에서 직접 수행한 application release 전환과 원래 release rollback 결과를 비민감 증거로 기록한다.

## 입력 문서
OPS-012 사용자 실행 결과, OPS-010 Runbook, production 운영 아키텍처와 현재 production script 계약을 입력으로 사용했다.

## 승인 입력
사용자는 짧은 서비스 중단 가능성을 수용하고 DB·volume·schema를 변경하지 않는 실제 application rollback을 승인했다.

## 명시적 승인 근거 (고위험 필수)
현재 요청에 원래 release `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6`, 검증용 release `f80e29293146fae13bda1c01d18131d651ede1d1`, 적용 전·중간·최종 결과가 명시됐다.

## 변경 범위
OPS-012 보고서·인수인계와 현재 상태 Runbook·아키텍처 문서만 갱신한다.

## 변경하지 않은 범위
OPS-012 운영 검증에는 사용자가 승인하고 직접 실행한 검증용 release 배포와 원래 release rollback이라는 container 전환이 포함됐다. 다만 이 PR의 문서 정리 단계에서는 추가 AWS·EC2·Docker·DB 명령을 실행하지 않았고 application·test·`infra/production/**`·workflow·dependency를 변경하지 않았다. 운영 검증 중에도 AWS 리소스와 DB·schema·Flyway·volume은 변경하지 않았다. 기존 OPS-010 보고서·인수인계는 당시 사실을 기록한 역사 문서이므로 소급 수정하지 않는다. OPS-010 Runbook은 현재 실행 상태를 제공하는 문서이므로 이번 PR에서 OPS-012 검증 결과에 맞게 갱신했다.

## 인수 조건 매핑
승인, 적용 전, 검증용 release, 원래 release 복귀, DB 비변경 증거를 아래 섹션으로 분리했다.

## 주요 결과
검증용 release 배포 후 원래 release로 `rollback.sh` 실행이 성공했다. 최종 `current-sha`는 원래 release, `previous-sha`는 검증용 release이며 네 container health, MySQL volume, 서버·외부 사용자 PC HTTPS 두 경로가 정상이다.

## 변경 파일
OPS-012 보고서·인수인계, OPS-010 Runbook, production 운영 아키텍처다.

## 결정 상태
Application rollback은 Verified다. Actual production DB restore는 미실행·미완료다.

## API 영향
API 계약 변경 없이 HTTPS `/products`와 `/api/products` 정상만 확인했다.

## DB 영향
DB restore, schema downgrade, Flyway 수정과 volume 삭제는 수행하지 않았고 `pawcycle-production-mysql-data`가 보존됐다.

## 보안 영향
Secret, domain, IP, account ID, ARN, 이메일과 원시 로그를 기록하지 않았다.

## 운영 영향
짧은 중단 가능성을 수용했으나 실제 중단 시간을 측정하지 않아 무중단이나 수치를 주장하지 않는다.

## 성능 영향
성능 측정이나 변경은 없다.

## 실행한 검증
- 관련 Markdown과 UTF-8 문자 인코딩 검사: 통과
- 고위험 task artifact validator: 통과
- production contract validator: 통과
- 원래 release와 검증용 release의 `infra/production/**` 계약 비교: 일치
- `git diff --check`: 통과
- application 전체 테스트: 문서 전용 변경이며 application·`infra/production/**` 동작을 변경하지 않아 로컬에서 반복하지 않음
- 원격 Repository Validation: 동적 상태를 문서에 고정하지 않고 GitHub Checks를 권위 원본으로 확인

## 적용 전 검증 (고위험 필수)
Clean control source, 예상 current SHA, previous-sha 부재, 대상 main 포함, 두 SHA의 `infra/production/**` 계약 일치, 두 GHCR image, 네 container health, MySQL volume과 HTTPS 두 경로를 확인했다.

## 적용 후 검증 (고위험 필수)
검증용 release 배포 후 current·previous SHA, 네 health, volume과 HTTPS를 확인했다. 원래 release rollback 후 최종 current·previous SHA, 네 health, volume, 서버·외부 PC HTTPS 두 경로를 다시 확인했다.

## 독립 검증 (고위험 필수)
구현자가 아닌 사용자/Tech Lead가 실제 production에서 적용 전·중간·최종 상태를 직접 확인했다.

## 실행하지 못한 검증과 이유
문서 전용 변경이므로 application 전체 test와 운영 명령을 반복하지 않는다. 실제 중단 시간, production DB restore와 RPO/RTO는 측정·실행하지 않았다.

## QA 필요 여부
별도 QA 문서는 생략한다. 사용자/Tech Lead의 실제 운영 검증을 독립 검증으로 사용한다.

## QA 문서 경로 또는 생략 사유
제품·인프라 동작 변경이 없고 비민감 운영 결과의 문서 정합성만 변경한다.

## AI 리뷰 반영 여부
PR 생성 후 유효한 문서 정합성 지적을 확인한다.

## AI 리뷰 미반영 항목과 이유
현재 없음. 동적 review 상태는 GitHub가 권위 원본이다.

## 적용 방법
추가 운영 실행 없이 이 보고서와 갱신된 현재 상태 문서를 함께 읽는다.

## 복구·롤백 증거 (고위험 필수)
원래 release로 실제 rollback 성공 후 state·health·volume·HTTPS가 확인됐다. DB restore·schema·Flyway·volume 변경은 없었다. 문서 변경은 revert PR로 복구할 수 있다.

## 위험과 제한
Application rollback 메커니즘만 검증됐으며 actual production DB restore, RPO/RTO와 무중단은 보장되지 않는다. 최종 `previous-sha`인 검증용 release는 원래 release와 Backend·Frontend 기능 차이가 없고 OPS-010 문서만 다르므로, 현재 기본 대상의 무인자 rollback을 application regression 복구 증거로 사용할 수 없다.

## 남은 위험
단일 장애 도메인, DB 복구 훈련, 자동 배포·Blue/Green과 지속 성능은 별도 작업이다. 향후 기능 차이가 있는 정상 release 배포가 `previous-sha`를 갱신하기 전에는 application regression 복구 시 승인된 대상 SHA를 명시한 `rollback.sh --sha`를 application 차이·GHCR image 존재·production 계약·DB schema 호환성 확인과 사용자 승인 아래 사용해야 한다. 이 제한을 우회하려고 state 파일을 수동 편집하지 않는다.

## 다음 작업
Tech Lead가 증거 범위와 DB restore 미완료 분리를 최종 판단한다.

## Git 결과
최신 `main`에서 새 `ops/sre`를 준비했다. commit·push 상태는 PR에서 확인한다.

## PR 결과
`main` 대상 PR을 생성하고 자동 병합하지 않는다.
