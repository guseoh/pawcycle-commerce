# OPS-010 OCI Production 단일 Application Release

## 범위와 상태

이 Runbook은 OCI Application host에서 단일 SHA release를 준비·배포·확인·복구하는 저장소 기준 절차다. **Accepted — Repository Readiness**이며 **Production Verified가 아니다**. 실제 OCI, host, managed DB, Secret, HTTPS 실행은 별도 고위험 승인 없이는 수행하지 않는다.

## Active release path

```text
materialize-runtime-env.sh
  -> production-command-dispatch.sh
  -> invoke-oci-production-command.sh
  -> deploy.sh / rollback.sh
```

`publish-production-images.yml`은 동일 commit SHA의 Backend·Frontend multiarch image를 GHCR에 게시하고 `production-release-readiness.yml`은 `linux/amd64`와 `linux/arm64` manifest를 확인한다. GitHub main push는 Production을 자동 배포하지 않는다.

## Required runtime

- `/opt/pawcycle/control`: clean detached control worktree
- `/opt/pawcycle/state`: root 전용 mode `700` state directory
- `/opt/pawcycle/runtime`: `materialize-runtime-env.sh`가 만든 atomic `current` bundle
- OCI instance principal과 Run Command 권한은 실행 환경 밖에서 준비
- Backend/Frontend GHCR SHA tag, immutable proxy digest, 승인된 contract/migration SHA

`backend.env`는 datasource host, port `3306`, database, JDBC URL, application username/password와 Scheduler 설정의 정확한 10개 key만 포함한다. datasource는 private IP/FQDN과 TLS `REQUIRED`여야 하며 root/server credential은 포함하지 않는다.

## Preflight and activation

1. `materialize-runtime-env.sh`를 실행해 입력 누락·중복·host·port·TLS·파일 mode를 검증한다.
2. 운영자가 `invoke-oci-production-command.sh --operation preflight --target-sha <40자 SHA>`를 승인한다.
3. dispatcher가 HTTPS origin, fetched main ancestry, current/target ancestry, GHCR repository를 확인한다.
4. `deploy.sh`는 Compose contract, runtime bundle, immutable Backend·Frontend·Proxy digest/revision, migration boundary, state contract를 다시 확인한다.
5. activation은 `backend`, `frontend`, `proxy`만 수행하고 health, internal smoke, enabled HTTPS 경계를 확인한다.
6. 모든 gate가 성공한 뒤에만 `current-sha`, `previous-sha`, `contract-sha`, `previous-contract-sha`를 publication한다.

운영자는 다음 형태만 사용한다. 실제 값·OCID·domain은 문서에 기록하지 않는다.

```bash
sudo bash /opt/pawcycle/control/infra/production/materialize-runtime-env.sh \
  --source-file /run/pawcycle/runtime-source.env \
  --output-dir /opt/pawcycle/runtime

sudo bash /opt/pawcycle/control/infra/production/invoke-oci-production-command.sh \
  --operation preflight --target-sha <approved-40-char-sha> \
  --compartment-id <compartment-ocid> --instance-id <instance-ocid> \
  --region <approved-region>
```

`deploy` operation은 wrapper가 먼저 같은 target으로 preflight를 실행한 뒤 deploy를 실행한다. GitHub workflow에서 이 경로를 자동 호출하지 않는다.

## Rollback and failure handling

`rollback.sh`는 명시 SHA 또는 state의 `previous-sha`를 사용해 Application image만 바꾼다. migration bundle 경계를 넘는 rollback, contract boundary 승인 없는 control 변경, immutable digest/revision 불일치는 fail-closed 한다.

- target health/smoke/HTTPS 실패: 기존 healthy Application release를 복귀하고 state publication을 시작하지 않는다.
- initial release 실패: `backend`, `frontend`, `proxy`를 정지한다.
- incomplete `release-state-transition`: 다음 실행에서 자동으로 재개하지 않고 marker와 state를 조사한다.
- state publication 실패: application을 정지하고 transition marker를 보존한다.
- Scheduler는 deploy/rollback에서 항상 OFF이며 별도 preflight·승인 절차에서만 바꾼다.

모든 실패 메시지는 managed database가 Application release lifecycle에서 수정되지 않았음을 명시한다. DB restore/cutover는 이 Runbook의 작업이 아니다.

## 확인·에스컬레이션

확인 순서는 `current-sha`/transition marker, container exact identity, health/smoke, HTTPS state, non-sensitive logs다. Secret, 실제 resource 부족, account/quota, certificate chain, managed DB 연결 또는 OCI API 오류가 발견되면 값을 출력하지 않고 중단·에스컬레이션한다.

## Evidence status

Repository validator와 fake lifecycle test는 통과했지만, OCI tenancy/account, A1 actual deployment, MySQL.Free connection, Object Storage backup, Run Command actual execution, Production HTTPS와 restore rehearsal은 **Not Verified**다.
