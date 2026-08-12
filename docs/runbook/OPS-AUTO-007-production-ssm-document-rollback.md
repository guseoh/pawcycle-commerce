# OPS-AUTO-007 Production SSM Document rollback

## 목적과 경계

이 Runbook은 `PawCycle-Production-Deploy` SSM Document의 새 immutable version을 Production Deploy workflow에 반영한 뒤 문제가 발생했을 때 dispatcher 계약을 이전 version으로 되돌리는 절차를 정의한다.

- 작업 등급: 고위험
- 실행 구분: 실제 운영 실행
- 저장소 병합만으로 AWS 또는 Production 상태가 변경되지는 않는다.
- SSM Document version rollback은 Application Release rollback이나 DB rollback이 아니다.
- 실제 AWS Document 갱신, GitHub Environment variable 변경, Production Deploy 재실행은 사용자가 별도로 승인하고 수행한다.

## 적용 전 확인

새 SSM Document version을 반영하기 전에 다음을 확인한다.

1. 병합된 `main`에서 Repository Validation의 Production contract lane이 성공했다.
2. 새 document content가 병합된 `infra/production/pawcycle-production-deploy-ssm-document.json`과 일치한다.
3. 현재 GitHub `production` Environment의 `PAWCYCLE_PRODUCTION_SSM_DOCUMENT_VERSION`이 가리키는 이전 immutable numeric version을 비민감 운영 증거로 기록한다.
4. `/opt/pawcycle/state/current-sha`와 현재 Backend·Frontend·MySQL·Nginx 상태를 기존 `OPS-010-production-single-release.md` 절차로 확인한다.
5. 새 version 반영 뒤 첫 실행은 `preflight` 성공을 먼저 요구하며, preflight가 실패하면 `deploy`를 실행하지 않는다.

## 즉시 중단 조건

다음 중 하나라도 발생하면 추가 Production Deploy dispatch를 중단한다.

- 새 SSM Document version 생성 또는 조회가 실패한다.
- document content 또는 parameter 계약을 병합된 `main`과 일치한다고 확인할 수 없다.
- GitHub Environment가 의도한 immutable numeric version을 가리키지 않는다.
- SSM `preflight`가 실패하거나 command status를 확정할 수 없다.
- `preflight` 성공 전에 `deploy`가 실행된 정황이 있다.
- `current-sha`, Container health 또는 MySQL volume 상태가 적용 전 기준과 예상하지 않게 달라졌다.

실패 원인이 불명확한 상태에서 새 document version 생성, blind retry, state 파일 수동 편집, Flyway repair, DB downgrade를 수행하지 않는다.

## SSM Document version rollback

새 version 자체가 원인으로 판단되면 다음 순서로 dispatcher만 복구한다.

1. Production Deploy를 추가 실행하지 않는다.
2. GitHub repository의 `Settings > Environments > production`에서 `PAWCYCLE_PRODUCTION_SSM_DOCUMENT_VERSION`을 적용 전에 기록한 이전 immutable numeric version으로 복원한다.
3. incident 중에는 기존 SSM Document version을 삭제하거나 version history를 재작성하지 않는다. Production Deploy workflow는 Environment에 고정된 numeric version을 명시적으로 사용한다.
4. 복원 직후 Application Release를 자동으로 rollback하지 않는다. document version 변경은 dispatcher 선택만 되돌린다.
5. 이전 version이 현재 알려진 자동 preflight 결함을 포함한다면, 서비스 상태를 보존한 채 자동 배포를 중단하고 수정된 새 version이 승인될 때까지 Production Deploy를 재개하지 않는다.

## rollback 후 확인

SSM Document version 복원 후 다음을 독립적으로 확인한다.

- 새 document version에서 `deploy`가 실행되지 않았다면 `/opt/pawcycle/state/current-sha`가 적용 전 값과 같다.
- Backend·Frontend·MySQL·Nginx의 실행 상태와 health가 기존 OPS-010 기준을 만족한다.
- Production MySQL은 기존 active named volume을 계속 사용한다.
- transition marker나 Application Release state가 예상하지 않게 변경되지 않았다.
- GitHub `production` Environment가 의도한 이전 SSM Document numeric version을 가리킨다.

이미 `deploy` 단계가 시작되어 Application Release 상태가 바뀌었다면 SSM Document version 복원만으로 복구 완료로 판단하지 않는다. `OPS-010-production-single-release.md`와 관련 migration/restore Runbook의 보호된 Application·DB 복구 경계를 별도로 따른다.

## 금지 사항

- 실패한 document version을 반복 dispatch해서 원인을 확인하는 행위
- SSM Document version history 삭제 또는 임의 재작성
- `/opt/pawcycle/state/*` 수동 편집으로 승인 경계 우회
- Production MySQL volume 삭제
- Flyway repair, schema downgrade, 직접 row 수정
- document rollback을 근거로 Application 또는 DB가 복구됐다고 간주하는 행위

## 완료 기준

다음이 모두 확인돼야 SSM Document rollback을 완료로 기록한다.

- GitHub Environment의 document version이 승인된 이전 numeric version으로 복원됨
- `current-sha`와 Application/Container 상태의 영향 여부가 확인됨
- MySQL active volume 보존이 확인됨
- 추가 자동 배포가 중단되거나, 별도 승인된 후속 preflight가 정상 통과함
- 실제 실행 결과와 남은 위험이 비민감 증거로 기록됨
