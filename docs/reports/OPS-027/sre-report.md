# OPS-027 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-027
- 작업 등급: 고위험
- 역할: Platform/SRE
- 대상 브랜치: `main`
- 작업 브랜치: `ops/sre`
- 작업 유형: Production rollback 계약의 저장소 준비 수정

## 목적

Control 채택 뒤 `previous-contract-sha`와 현재 `contract-sha`가 서로 다른 SHA여도 실제 Release 계약이 호환되면 기록된 이전 Application Release로 안전하게 rollback할 수 있도록 한다. 부분 state 기록이나 비호환 Control은 Docker 활성화와 state 변경 전에 fail-closed한다.

## 입력 문서

- `infra/production/release-common.sh`: Control·Release 계약 비교, state 검증과 공통 preflight
- `infra/production/deploy.sh`: 성공 전환 state 기록 순서
- `infra/production/rollback.sh`: rollback 대상 선택, 계약 검증, 활성화와 복구
- `infra/production/test-production-scripts.sh`: 기존 Production lifecycle 회귀 검증
- `infra/production/test-rollback-control-compatibility.sh`: OPS-027 전용 Control 호환성 회귀 검증
- `infra/production/validate-production-contracts.py`: Production 계약 정적 validator
- `docs/runbook/OPS-010-production-single-release.md`: 사용자 rollback 절차와 중단 조건
- `docs/reports/OPS-026/tl-report.md`: 현재 Control의 실제 Production rollback 증거가 남은 판정 근거

## 승인 입력

사용자는 OPS-027 저장소 수정을 먼저 진행하도록 승인했다. 이후 병합된 과거 `ops/sre` 역할 브랜치를 최신 `main`에서 재생성하는 작업을 명시적으로 승인했으며, 불필요한 커밋을 만들지 않고 CI가 두 번 이상 실패하면 추가 수정을 중단해 원인을 보고하도록 지시했다. 실제 Production 실행은 승인하지 않았다.

## 변경 범위

- 기록된 이전 Application Release의 rollback 계약을 `previous-contract-sha`와 현재 `contract-sha`의 SHA 문자열 일치가 아니라 Release 계약 호환성으로 판정한다.
- `current-sha`를 성공 transition의 최종 commit marker로 사용해 `previous-sha == current-sha`인 부분 기록 상태에서는 이전 Control fast-path를 사용하지 않는다.
- 비교 범위는 기존 `RELEASE_CONTRACT_PATHS`인 `compose.yaml`, `nginx.conf`, `nginx.https.conf`를 유지한다.
- 이전·현재 Control의 Release 계약이 다르면 Docker 활성화와 state write 전에 fail-closed한다.
- 기록된 `previous-sha`가 아닌 명시적 rollback 대상은 기존 현재 Control과 대상 commit의 Release 계약 검증을 유지한다.
- OPS-027 전용 회귀 테스트를 Repository Validation에서 지속 실행한다.

## 변경하지 않은 범위

- 실제 Production, AWS, Docker, DB와 Secret 명령 실행
- Production의 `current-sha`, `previous-sha`, `contract-sha`, `previous-contract-sha` 수동 수정
- MySQL volume, schema, Flyway history와 데이터 변경
- deploy·rollback의 Application 활성화, 자동 복구와 성공 state write 순서
- `RELEASE_CONTRACT_PATHS` 확대
- 새 제품 기능, 의존성, DB migration과 인프라 리소스 추가

## 적용 방법

### 저장소 적용

1. `rollback.sh`에서 검증된 `current-sha`와 기록된 `previous-sha`의 관계를 확인한다.
2. 대상이 기록된 이전 Release이고 `previous-sha != current-sha`이며 `previous-contract-sha`가 존재할 때만 이전·현재 Control의 Release 계약을 비교한다.
3. 조건이 충족되지 않으면 기존 현재 Control과 target commit 비교 경로를 사용한다.
4. 호환성 검증을 통과한 뒤에만 기존 image preflight와 Application 활성화로 진행한다.
5. 전용 회귀 테스트와 Repository Validation으로 허용·거부·부분 기록 상태를 검증한다.

### 병합 후 Production 적용

병합만으로 Production에 적용하지 않는다. 별도 사용자 승인 후 다음 순서로 진행한다.

```text
최신 main Control checkout
→ 현재 Application에서 새 Control 채택·재검증
→ state·Container health·MySQL volume 확인
→ 기록된 이전 Application rollback
→ health·내부 Smoke·외부 HTTPS·volume 확인
→ 원래 Application 재배포
→ 같은 검증 반복
```

각 Production 단계는 기존 운영 세션의 중단·복구 조건을 유지하며 한 단계씩 별도 승인한다.

## 실행한 검증

저장소에 남는 검증 경로와 명령은 다음과 같다.

```bash
bash -n \
  infra/production/release-common.sh \
  infra/production/rollback.sh \
  infra/production/test-production-scripts.sh \
  infra/production/test-rollback-control-compatibility.sh

python infra/production/validate-production-contracts.py
bash infra/production/test-production-scripts.sh
bash infra/production/test-rollback-control-compatibility.sh
```

`infra/production/test-rollback-control-compatibility.sh`는 다음을 재현한다.

- 서로 다른 Control SHA지만 Release 계약이 같은 기록된 이전 Release rollback 허용
- Release 계약이 다른 이전·현재 Control의 Docker 호출 전 거부
- `previous-sha == current-sha`인 부분 기록 상태에서 이전 Control fast-path 미사용
- 거부 시 `current-sha`, `previous-sha`, `previous-contract-sha`, `active-mysql-volume` 불변
- 성공 rollback 뒤 이전 Release state와 MySQL volume 보존

전체 Repository Validation은 Production Script lifecycle, MySQL, Backend test·build와 Frontend install·lint·build를 포함하며 최신 결과는 GitHub Checks를 권위 원본으로 확인한다.

## 실행하지 못한 검증과 이유

- 실제 Production Control 적용과 Application rollback: 저장소 준비와 실제 운영 실행을 분리하며 별도 명시적 사용자 승인이 필요하다.
- 실제 GHCR image pull, Container health, 내부 Smoke와 외부 HTTPS: 이번 저장소 준비 단계에서 Production을 변경하지 않기 위해 미실행했다.
- Actual Production DB restore: OPS-027 범위 밖이며 DB·volume 변경 승인이 없다.

## 남은 위험

- Release 계약 allowlist 밖의 Control 변경이 실제 활성화 동작에 영향을 주는 경우 별도 계약 확장이 필요하다.
- `previous-contract-sha`가 누락된 역사 상태는 기록된 이전 Control 비교 경로를 사용할 수 없으며 기존 target commit 계약 검증으로 이동한다.
- 세 state 파일은 각각 원자적으로 기록되지만 복합 파일 하나로 기록되지는 않는다. `current-sha` 최종 commit marker와 shared `deploy.lock` 불변 조건을 유지해야 한다.
- 실제 Production 증거가 확보되기 전에는 현재 Control rollback을 Production Verified로 판정하지 않는다.
- 이번 리뷰 대응 head의 Repository Validation이 두 번 실패하면 사용자 지시에 따라 추가 수정을 중단하고 원인과 현재 상태를 보고한다.

## Git 결과

- 원격 `ops/sre`가 병합된 PR #76의 과거 head와 동일하고 열린 역할 PR이 없음을 확인했다.
- 사용자 승인 후 `ops/sre`를 당시 최신 `main` 기준으로 재생성했다.
- 최초 구현은 rollback Control 호환성 비교와 본 고위험 보고서로 구성됐다.
- 리뷰 대응은 state 연결 검증, 지속 회귀 테스트, Runbook과 보고서 정렬을 하나의 추가 커밋으로 묶는다.
- 자동 병합, Production 실행, force merge와 unrelated ref 변경은 수행하지 않는다.

## PR 결과

- PR 식별자: `#79`
- PR 링크: `https://github.com/guseoh/pawcycle-commerce/pull/79`
- 역사적 CI 사건: 최초 Repository Validation은 PR 제목·본문의 작업 ID 누락으로 `Validate task artifacts` 단계에서 실패했다.
- 역사적 보완: OPS-027 작업 ID와 고위험 보고서를 추가한 뒤 기존 head의 Repository Validation이 성공했다.
- 최신 Draft·Ready 상태, head SHA, CI, 리뷰와 미해결 thread는 GitHub PR과 Checks를 동적 권위 원본으로 확인한다.

## QA 필요 여부

별도 제품 QA 문서는 필요하지 않다.

## QA 생략 사유

제품 화면·API·도메인·DB schema를 변경하지 않는 Production rollback 안전 계약 수정이다. Production lifecycle 회귀 테스트, OPS-027 전용 계약 테스트, Repository Validation과 사용자·Tech Lead 검토를 독립 검증으로 사용한다.

## 명시적 승인 근거

- 사용자 승인: OPS-027 저장소 수정 진행
- 사용자 승인: 병합된 과거 `ops/sre` 역할 브랜치를 최신 `main`에서 재생성
- 추가 경계: 불필요한 커밋 금지
- 추가 경계: 리뷰 대응 이후 CI가 두 번 이상 실패하면 중단·원인 보고
- 미승인: 실제 Production Control 적용, rollback, 재배포, DB·volume·AWS 변경

## 적용 전 검증

- 작업 시작 시 최신 `main`과 원격 `ops/sre`의 분기 상태를 GitHub에서 확인했다.
- 열린 역할 PR이 없고 기존 역할 브랜치가 병합된 PR #76 head와 동일함을 확인했다.
- Production은 앞선 운영 세션에서 최신 Control 채택 뒤 healthy 상태였으며 OPS-027 저장소 작업 중 Production 명령을 실행하지 않았다.
- 기존 rollback 로직이 서로 다른 호환 Control을 target Application commit 비교 경로로 보내 차단함을 확인했다.
- 기존 state write 순서가 shared `deploy.lock` 아래 `previous-sha`, `previous-contract-sha`, `current-sha` 순서임을 확인했다.

## 적용 후 검증

- 기록된 이전 Release일 때 `previous-sha != current-sha`를 확인한 뒤에만 `previous-contract-sha`를 비교 기준으로 사용한다.
- 조건이 불충분하면 기존 현재 Control과 target commit 비교로 fail-closed한다.
- 전용 회귀 테스트가 호환 Control 허용, 비호환 Control 거부, 부분 기록 state 거부와 보호 state 불변을 검증한다.
- Runbook의 rollback 판정 설명을 실제 Script와 일치시킨다.
- 실제 Production, Docker, DB, Secret과 volume은 변경하지 않는다.

## 독립 검증

Repository Validation과 GitHub의 CodeRabbit·Codex 리뷰를 독립 검증으로 사용한다. 저장소에 남는 OPS-027 전용 테스트를 CI에서 실행해 일회성 격리 결과에만 의존하지 않는다. 최신 결과는 GitHub를 권위 원본으로 확인하며 성공을 미리 고정하지 않는다.

## 복구·롤백 증거

저장소 변경은 PR을 병합하지 않거나 OPS-027 관련 커밋을 revert하는 PR로 복구할 수 있다. 계약 차이와 부분 기록 state는 Docker·state 변경 전에 거부되며 전용 회귀 테스트에서 보호 state와 active MySQL volume 불변을 확인한다. 실제 Application rollback과 DB restore는 수행하지 않았다.

## 인수인계 생략 사유

사용자·ChatGPT·Platform/SRE가 같은 운영 검증 세션을 계속 진행하며 다음 역할 또는 새 세션으로 전환하지 않는다. 따라서 별도 인수인계를 만들지 않고 본 보고서, Runbook, 테스트와 PR을 현재 근거로 사용한다.
