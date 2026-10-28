# OPS-OCI-CD-02 — OCI application Release/Recovery

작업 등급 고위험 / 저장소 변경. 상위 #470, 구현 #471. 이 단계는 Linux의 `oci-release.py`와 fake/격리 회귀를 제공한다. app01 적용, 운영 baseline 채택, 배포·Rollback, DB SQL/Restore, Secret/Cloud 변경과 CD 연결은 별도 운영 승인 대상이다. CD·무중단 배포는 DEFER이며 Production Verified가 아니다.

## 실행 전 경계

명령은 root, host-local `/var/run/docker.sock`, 정확한 `guseoh/pawcycle-commerce` source repository만 사용한다. 외부 Docker context/Compose 환경 override는 전달하지 않는다. 승인된 source checkout은 root 소유이며 경로 ancestor의 group/other write와 symlink를 거절한다. 현재 opc 소유 source를 임의 chown하거나 runtime을 재구성하지 않는다. 운영 source 준비와 baseline 채택은 별도 승인 후 진행한다.

기존 `/opt/pawcycle/state`와 `/opt/pawcycle/runtime`은 root 소유 non-symlink directory, mode `700`이어야 한다. `runtime/current`만 의도된 symlink이며 같은 runtime directory의 `.bundle.*` mode `700`으로 한정한다. `backend.env`, `.complete`, 입력 manifest/approval 및 state 파일은 root 소유 regular file, mode `600`, hardlink 금지다. env는 source/echo하지 않으며 필수 datasource·automation 주입의 형태·존재와 runtime hash만 검사한다. protected runtime bundle의 설치/갱신은 이 도구의 책임이 아니다.

기존 `deploy.lock`의 inode를 유지한 채 `flock(LOCK_EX|LOCK_NB)`을 사용한다. catalog-import, auth-smoke-member와 실제 동일한 잠금이다. 그 다음 `/run/lock/pawcycle-production-https-renewal.lock`을 nonblocking으로 잡는다. lock 파일을 truncate/삭제/교체하거나 stale lock을 강제 해제하지 않는다. 잠금은 커널이 process 종료 시 해제하지만 남은 transition은 계속 중단시킨다. 읽기 preflight는 기존 lock이 있어야 한다. 명시적인 실행 승인 모드에서만 없는 lock을 mode `600`으로 생성할 수 있다.

HTTPS adoption state(`https-domain`, `https-enabled`)와 reload-pending 부재가 필요하다. 관측성·metrics-proxy·performance Compose는 release 대상이 아니다. 인증서 두 named volume과 앱·DB egress network는 이미 존재해야 하며, 앱에 DB service·추가 mount·port를 넣을 수 없다.

## 입력과 출처

manifest는 Secret 없는 JSON 한 개다. 아래 구조의 값은 예시이며 실제 Registry/source 검증을 통과하지 않는다. 승인 운영자는 검증된 값으로 별도 보호 파일을 준비한다. `unpacked_bytes`는 layer 압축 크기가 아니라 독립적으로 측정·승인된 보수적인 풀린 image 크기 상한이다.

```json
{
  "sha": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "compose_source_sha": "cccccccccccccccccccccccccccccccccccccccc",
  "compose_sha256": "sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
  "nginx_source_sha": "dddddddddddddddddddddddddddddddddddddddd",
  "nginx_sha256": "sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
  "migration_tree": "1111111111111111111111111111111111111111",
  "backend": {
    "repository": "ghcr.io/guseoh/pawcycle-commerce-backend",
    "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "config_digest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "unpacked_bytes": 1073741824
  },
  "frontend": {
    "repository": "ghcr.io/guseoh/pawcycle-commerce-frontend",
    "digest": "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
    "config_digest": "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
    "unpacked_bytes": 2147483648
  }
}
```

- `sha`, `compose_source_sha`, `nginx_source_sha`는 각각 정확한 40자리 lowercase commit이며 조회 시점 main의 조상이어야 한다. `git ls-remote`와 cached `origin/main`이 다르면 source를 별도로 갱신한 뒤 다시 판단한다. 신규 activate는 current의 후손만 가능하고 의도된 이전 버전 복귀는 rollback action을 쓴다.
- Compose는 `git show <compose_source_sha>:infra/production/compose.yaml` 원문 hash다. Nginx는 `nginx.https.conf`의 `__PAWCYCLE_DOMAIN__`을 기존 승인 hostname으로 치환한 **실제 적용 bytes hash**다. 두 source SHA를 이미지 SHA와 별도로 기록한다. 개행도 hash에 포함한다.
- Nginx source의 조상 여부·입력 hash·문법만으로 보안 정책을 승인하지 않는다. CLI에 버전 고정된 승인 directive/token 구조 fingerprint를 별도로 대조해 HTTPS·보안 헤더·로그인 rate limit·ACME·내부 8081·Docker DNS `zone/resolve`와 routing 경계를 보호한다. Source SHA는 독립적으로 유지하되 현재 승인 구조와 같은 설정만 허용하며, 주석과 문자열 밖 whitespace 차이만 정규화한다. 그 외 설정 변경은 검토된 계약과 fingerprint 갱신이 필요하다. 보안 헤더·rate limit·DNS 재해석이 없는 역사적 `c09302a8` 설정은 올바른 source hash를 제시해도 거부한다.
- `migration_tree`는 release SHA의 `backend/src/main/resources/db/migration` Git tree ID다. source path가 없거나 current와 다르면 이 단계에서 거부한다. 동일 tree는 애플리케이션·데이터 호환성의 보증이 아니므로 별도 pair compatibility evidence도 필수다.
- Registry의 `repo:<sha>` raw manifest digest, index의 유일한 `linux/amd64` child/config digest, remote config의 OS/arch와 OCI revision/source를 독립 확인한다. 각 local image도 config ID·RepoDigests·OS/arch·OCI labels·측정 크기를 확인하고 실행 컨테이너 ID와 대조한다. 서로 다른 revision, 미존재·변조 digest, index platform 불명확은 거부한다.
- 명시적인 `rollback`만 Registry 검증을 생략한다. 보호된 previous record와 정확히 같은 manifest, 저장된 정상 검증·승인/호환 근거·runtime hash/DB epoch, 보존된 local Digest/config ID/platform/OCI labels를 확인하고 `pull=False`로 복귀한다. Registry 접근 불가만으로 이 경로를 막지 않지만 local 이미지 누락·변조는 거부한다. 새 rollback 승인과 main/source·설정·잠금·디스크·DB Gate는 유지하며, Git main 조회를 포함한 모든 네트워크 의존성을 제거했다는 뜻은 아니다. 일반 adopt/preflight/activate의 원격 Registry 검증은 유지한다.
- 기존 Compose의 `repo:<RELEASE_SHA>` 구문은 유지한다. pull은 `repo@digest`로만 수행한 뒤 검증된 local image에 SHA tag를 붙이고 `up --pull never`를 쓴다. Registry tag 변경을 up 시 재조회하지 않으며 실행 후 image ID를 다시 확인한다.

approval은 operator/Tech Lead가 승인한 근거를 가리키는 별도 root `600` JSON이다. 이 파일은 암호학적 서명이나 GitHub approval 연동이 아니다. root 권한으로 작성된 명시적 운영 승인 입력이라는 신뢰 경계이며 CI가 생성하거나 자동 승인하지 않는다.

```json
{
  "action": "activate",
  "manifest_sha256": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "expected_current": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "evidence": "APPROVED-OPS/release-pair-01",
  "db_compatibility_evidence": "APPROVED-OPS/application-data-pair-01",
  "db_epoch": "approved-db-baseline-01",
  "compatible_with": ["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],
  "rollback_safe": true,
  "expires_unix": 0,
  "capacity_evidence": "APPROVED-OPS/measured-peak-01",
  "reserve_bytes": 1073741824,
  "rollback_extra_bytes": 1073741824
}
```

`manifest_sha256`는 JSON을 sorted keys, separators `(',', ':')`, ASCII escaping, 끝 newline 하나로 직렬화한 SHA-256이다(`oci-release.py`의 `identity`). 만료는 실행 시점 이후 최대 24시간이다. action·expected current·runtime env hash·DB epoch·양방향 application/data compatibility와 rollback-safe 승인을 바인딩한다. approval의 reference에는 Secret·원시 DB row·로그를 넣지 않는다. DB epoch는 실제 DB fingerprint를 도구가 조회한 값이 아니라 운영자가 확인한 baseline 식별자다. 승인 뒤 DB 외부 변경이 있으면 그 approval을 폐기하고 재평가한다.

초기 `adopt`에는 `expected_current: null`, `baseline_verified: true`와 독립 정상 baseline evidence가 추가로 필요하다. 기존 current-sha가 다른 경우 덮어쓰지 않는다. 이전 이미지가 local에 있다는 이유만으로 정상 baseline을 생성하지 않는다. 현재 미검증된 이전 Release를 이 도구가 자동 채택하지 않는다.

## Preflight와 용량

아래는 **별도 운영 승인을 받은 경우에만** 사용할 command 형태다. 파일은 운영자가 보호된 절대 경로로 준비해야 한다.

```bash
sudo python3 infra/production/oci-release.py status --repository /approved/root-owned/source
sudo python3 infra/production/oci-release.py preflight --repository /approved/root-owned/source \
  --manifest /approved/private/manifest.json --approval /approved/private/preflight-approval.json
```

Preflight도 action이 `preflight`인 승인 근거를 받아 현재 DB 호환 정책을 검증한다. 미채택 baseline은 activate preflight를 통과할 수 없다. 초기 adoption은 별도 정상 기준선 조사·승인으로 준비한다.

preflight는 Registry/source 읽기와 임시 config 렌더링·현재 runtime smoke를 수행한다. 후보 Nginx는 기존 인증서 volume을 read-only로 연결한 임시 `--network none --pull never` 컨테이너에서 `nginx -t`로 검사하고 제거한다. image pull, 기존 앱 컨테이너 recreate, persistent release state 생성은 하지 않는다. source/config 검증, 기존 runtime image와 경계 확인, 이전 current의 local image 보존 확인이 activation보다 먼저다.

신규 activate·adopt·preflight의 필요 여유 bytes = **양쪽 압축 layer 합 × 2 + 양쪽 unpacked 상한 합 + log/temp reserve + rollback 추가 필요량**이다. 이 보수적인 계산은 유지한다.

명시적 rollback의 필요 여유 bytes = **승인된 reserve_bytes + rollback_extra_bytes**다. current와 target 이미지의 local Digest/config ID/platform·크기를 확인하며, 이미 `df` 사용량에 포함된 target 이미지의 unpacked 상한을 신규 필요량으로 다시 더하지 않는다. Pull이 없는 정확한 verified previous 복귀만 이 계산을 사용한다. reserve의 기존 최소 1 GiB는 유지한다. `rollback_extra_bytes`는 양수이며 `capacity_evidence`가 가리키는 승인된 측정/상한 근거에 따라 해당 source/state와 컨테이너 재생성의 실제 추가 쓰기 peak를 포함해야 한다. config snapshot·atomic state/transition 임시 쓰기, Docker container metadata·writable layer·로그와 validation 임시파일 등을 포함하고, 코드가 임의의 고정 overhead를 대신 넣지 않는다. 0·누락·근거 부재는 공간이 많아도 거부한다. 측정 근거의 확보·승인은 별도 운영 Gate이며 이 도구가 자동으로 입증하지 않는다.

공유 layer·reclaimable image/cache를 여유 공간에 더하거나 4.9 GB를 충분하다고 가정하지 않는다. 실제 filesystem의 available bytes가 해당 실행의 요구량보다 작으면 mutation 전에 거부한다. DockerRootDir, state, runtime이 서로 다른 filesystem이면 이 구현은 거부한다. 서로 다른 FS의 budget은 후속 별도 설계가 필요하다. 실행 중 실제 ENOSPC도 실패로 취급하며 prune이나 볼륨 삭제로 우회하지 않는다.

기존 정상 current의 image/config/network/volume/health, 내부 8081 `/products`·`/api/products`의 직접 2xx와 외부 인증서 검증 HTTPS 2xx, metrics-proxy identity/health/수집 endpoint를 확인한다. redirect chain을 최종 200으로 성공 처리하지 않는다. 업무 로그인·결제·전체 metric 수집은 이 smoke의 보증 범위가 아니다.

## 승인된 activation과 rollback

```bash
sudo python3 infra/production/oci-release.py adopt --execute-approved --repository /approved/root-owned/source \
  --manifest /approved/private/baseline.json --approval /approved/private/adopt-approval.json
sudo python3 infra/production/oci-release.py activate --execute-approved --repository /approved/root-owned/source \
  --manifest /approved/private/manifest.json --approval /approved/private/activate-approval.json
sudo python3 infra/production/oci-release.py rollback --execute-approved --repository /approved/root-owned/source \
  --manifest /approved/private/previous.json --approval /approved/private/rollback-approval.json
```

adopt는 이미 실행 중인 정상 baseline을 검사·기록하며 컨테이너를 바꾸지 않는다. 활성화는 source bytes를 manifest identity별 root `700` snapshot directory에 보존한다. Backend·Frontend만 `up --no-deps --pull never --wait`하고, release별 read-only config bind를 적용하기 위해 proxy 하나를 같은 방식으로 reconcile한다. DB/sibling project/인증서 volume lifecycle은 소유하지 않는다. proxy recreate에 따른 일시 중단은 가능하며 무중단을 주장하지 않는다.

Application Nginx는 기존 metrics-proxy와 같은 `127.0.0.11` resolver 및 shared upstream `zone`/`resolve`를 사용한다. IP가 바뀌어도 재해석하며 기존 HTTPS, 보안 헤더, rate limit, ACME와 8081 계약을 유지한다. smoke와 sibling container identity/시작시각 확인이 끝난 뒤에만 current를 commit한다.

## 원자 상태와 중단 복구

`oci-release-state.json` 하나가 current/previous·검증 결과·source/image identity·runtime hash·DB epoch의 권위 원본이다. 각 파일 쓰기는 mode `600` temporary → fsync → atomic replace → directory fsync다. 기존 `current-sha`, `previous-sha`, `<sha>.images`는 호환 projection이며 이 파일들의 묶음을 atomic하다고 주장하지 않는다. 쓰기 전 `release-state-transition`을 내구 기록하고 projection과 결과 기록 완료 후에만 제거한다. 기존 진단은 이 marker를 보고 배포 중으로 판단하고, catalog/auth 도구는 같은 deploy lock에 막힌다.

- activation과 직후 검증 구간의 일반 예외(JSON parsing, I/O, 상태 해석 오류 포함) 및 SIGINT/SIGTERM은 사전 승인된 동일 migration/runtime/DB epoch의 기존 정상 current로 이미지·source 복귀를 시도하고 smoke를 다시 확인한다. 복귀는 보존된 local image를 사용하고 Registry pull에 의존하지 않는다. 실패 exit code는 유지하며 `failed-release-restored`를 기록한다.
- 복귀 중 일반 예외도 marker를 `recovery-required`로 남기고 current를 성공으로 갱신하지 않는다. SystemExit/SIGKILL/host crash 같은 강제 종료는 자동 복귀 처리로 삼키지 않으며, 남은 marker가 후속 activation을 막는다. marker 자체의 I/O 기록 실패도 기존 marker를 제거하지 않고 수동 Recovery로 넘긴다.
- state commit 중 disk-full 또는 projection write 중단 시 DB나 앱을 추정하여 되돌리지 않는다. `oci-release-state.json`과 실제 runtime을 먼저 대조한다.
- `recover-state --execute-approved`는 새 만료 approval이 있고 **권위 state의 current가 실제 live smoke/config/image와 일치할 때만** 호환 projection을 다시 기록하고 marker를 해제한다. 다른 target이 실제로 실행 중이거나 JSON이 torn/invalid하면 거부한다. 수동 Incident/Recovery 승인이 필요하며 강제 unlock·임의 state 삭제 기능은 없다.
- DB tree/epoch·runtime hash·호환 근거가 없거나 다르면 rollback action도 거부한다. schema downgrade, Flyway repair/clean, DB Restore는 구현하지 않는다.

## 저장소 검증과 남은 운영 Gate

`test-oci-release.py`는 disposable root-owned directory, 실제 flock/atomic state와 fake external runtime으로 정상·중복·경합·부분 실패·중단·torn write·복귀 불가를 검증한다. registry/config/platform/capacity 경계는 별도 fixture로 검증한다. `test-production-nginx.sh`는 pinned Nginx와 임시 self-signed certificate, 변경된 두 upstream IP에서 재해석·HTTPS·ACME·헤더·rate limit을 검증한다. 기존 `test-production-compose.sh`는 disposable MySQL/Backend/Frontend topology와 lifecycle을 검증한다. 로컬/CI의 결과 및 미실행은 PR에 기록한다.

이 근거는 app01의 정상 baseline, 실제 peak 용량, 실제 DB/application/data 양방향 호환, 운영 인증서 갱신과 Release race, 실제 OCI Rollback 성공을 대신하지 않는다. #471 완료 뒤에도 #470의 별도 운영 Gate와 독립 리뷰·사용자 승인 없이 운영 활성화하지 않는다. 저장소 복구는 일반 revert PR이며, runtime 복구는 이 문서의 별도 승인 절차를 따른다.
