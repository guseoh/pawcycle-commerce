# OPS-027 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-027
- 작업 등급: 고위험
- 역할: Platform/SRE
- 대상 브랜치: `main`
- 작업 브랜치: `ops/sre`
- 작업 유형: Production rollback 계약의 저장소 준비 수정

## 승인 입력

사용자는 기존 역할 브랜치가 병합된 과거 작업을 가리키는 것을 확인한 뒤 `ops/sre`를 최신 `main`에서 재생성하고, 불필요한 커밋을 만들지 않으며 CI가 연속으로 실패하면 더 진행하지 말고 보고하도록 승인했다.

## 변경 범위

- 기록된 이전 Application Release로 rollback할 때 `previous-contract-sha`와 현재 `contract-sha`의 SHA 문자열 일치가 아니라 Release 계약 호환성을 검증한다.
- 비교 범위는 기존 `RELEASE_CONTRACT_PATHS`인 `compose.yaml`, `nginx.conf`, `nginx.https.conf`를 유지한다.
- 이전·현재 Control의 Release 계약이 다르면 Docker 활성화 전에 fail-closed한다.
- 임의 rollback 대상은 기존 현재 Control과 대상 Application commit의 Release 계약 검증을 유지한다.

## 변경하지 않은 범위

- 실제 Production, AWS, Docker, DB와 Secret 명령 실행
- `current-sha`, `previous-sha`, `contract-sha`, `previous-contract-sha` 수동 수정
- MySQL volume, schema, Flyway history와 데이터 변경
- deploy·rollback의 활성화·자동 복구·state write 순서
- 새 의존성, 새 Production Script와 새 인수인계 문서

## 실행한 검증

격리된 임시 Git 저장소와 fake Docker를 사용한 집중 회귀 검증을 수행했다.

```text
PASS: 동일 previous Control SHA 허용
PASS: 서로 다른 SHA지만 Release 계약이 같은 Control 허용
PASS: Release 계약이 다른 이전·현재 Control 거부
PASS: 임의 비호환 rollback 대상 거부
PASS: 손상된 previous-contract-sha state 거부
PASS: 계약 거부 시 Docker 호출 없음
PASS: 계약 거부 시 보호 state 불변
PASS: rollback 계약 검증이 preflight와 state write보다 먼저 실행
PASS: Bash syntax
```

첫 Repository Validation은 코드 단계 전에 task artifact validator가 PR 제목·본문에서 작업 ID를 찾지 못해 실패했다. 코드 검증 실패가 아니며, 작업 ID와 본 고위험 보고서를 정렬한 뒤 PR을 다시 열어 전체 CI를 한 번 재검증한다.

## 실행하지 못한 검증과 이유

- 실제 Production Control 적용과 Application rollback: 저장소 준비와 실제 운영 실행을 분리하며 별도 명시적 사용자 승인이 필요하다.
- 실제 GHCR image pull, Container health, 내부 Smoke와 외부 HTTPS: 이번 저장소 준비 단계에서 Production을 변경하지 않기 위해 미실행했다.
- Actual Production DB restore: OPS-027 범위 밖이며 DB·volume 변경 승인이 없다.

## 남은 위험

- Release 계약 allowlist 밖의 Control 변경이 실제 활성화 동작에 영향을 주는 경우 별도 계약 확장이 필요하다.
- `previous-contract-sha`가 누락된 역사 상태는 기록된 이전 Control 비교 경로를 사용할 수 없으며 기존 임의 대상 검증으로 이동한다.
- 실제 Production 증거가 확보되기 전에는 현재 Control rollback을 Production Verified로 판정하지 않는다.
- CI 재검증이 다시 실패하면 사용자 지시에 따라 추가 수정 없이 중단하고 실패 원인과 현재 상태를 보고한다.

## Git 결과

- 원격 `ops/sre`가 병합된 PR #76의 과거 head와 동일하고 열린 역할 PR이 없음을 확인했다.
- 사용자 승인 후 `ops/sre`를 최신 `main` 기준으로 재생성했다.
- 코드 수정 커밋은 `release-common.sh`의 +2/-1 최소 변경이다.
- 첫 CI의 필수 산출물 오류를 보완하기 위해 본 보고서 커밋을 추가했으며 이후 추가 구현 커밋을 만들지 않는다.

## PR 결과

- Draft PR: #79
- 첫 Repository Validation: 실패
- 첫 실패 단계: `Validate task artifacts`
- 첫 실패 원인: PR 제목·본문의 작업 ID 누락
- 수정 방식: PR을 잠시 닫고 OPS-027 작업 ID와 본 고위험 보고서를 정렬한 뒤 다시 연다.
- 최신 CI·리뷰·Draft 상태는 GitHub를 동적 권위 원본으로 확인한다.

## QA 필요 여부

별도 제품 QA 문서는 필요하지 않다.

## QA 생략 사유

제품 화면·API·도메인·DB schema를 변경하지 않는 Production rollback 안전 계약 수정이다. 격리 회귀 검증, Repository Validation과 사용자·Tech Lead 검토를 독립 검증으로 사용한다.

## 명시적 승인 근거

사용자가 `역할 브랜치 재생성 승인`을 명시했으며, 불필요한 커밋 금지와 CI 연속 실패 시 중단·보고 조건을 추가 승인 경계로 제공했다. 실제 Production 실행은 승인하지 않았다.

## 적용 전 검증

- 최신 `main` SHA와 원격 `ops/sre`의 분기 상태를 GitHub에서 확인했다.
- 열린 `ops/sre` PR이 없고 기존 역할 브랜치가 병합된 PR #76 head와 정확히 동일함을 확인했다.
- 현재 Production은 이전 read-only 기준선에서 healthy이며 이번 작업에서 Production 명령을 실행하지 않았다.
- 기존 함수가 `previous-contract-sha == contract-sha` exact match만 빠른 경로로 허용함을 확인했다.

## 적용 후 검증

- GitHub commit diff가 `release-common.sh` 한 파일의 +2/-1 변경임을 확인했다.
- 기록된 이전 Release이면 이전 Control과 현재 Control의 Release 계약을 비교한 뒤에만 성공하도록 변경했다.
- 격리 회귀 검증에서 호환 Control 허용, 비호환 Control 거부, Docker 미호출과 보호 state 불변을 확인했다.
- 실제 Production, Docker, DB, Secret과 volume은 변경하지 않았다.

## 독립 검증

Repository Validation 전체 재실행과 GitHub 리뷰를 독립 검증으로 사용한다. 첫 CI는 task artifact 단계에서만 실패했으며 Application validation은 실행되지 않았다. 재실행 결과를 통과로 미리 기록하지 않고 GitHub Checks에서 확인한다.

## 복구·롤백 증거

저장소 변경은 PR을 병합하지 않거나 두 관련 커밋을 revert하는 방식으로 복구할 수 있다. 계약 차이는 Docker·state 변경 전에 거부되며 격리 검증에서 거부 시 Docker 호출 없음과 보호 state 불변을 확인했다. Application rollback과 DB restore 자체는 수행하지 않았다.

## 인수인계 생략 사유

사용자·ChatGPT·Platform/SRE가 같은 운영 검증 세션을 계속 진행하며 다음 역할 또는 새 세션으로 전환하지 않는다. 따라서 별도 인수인계를 만들지 않고 본 보고서와 PR을 현재 근거로 사용한다.
