# OPS-OCI-CD-004 — 최초 OCI 전환 준비

고위험 / Repository Change + Operations Readiness. 상위 #470, 관련 #473·PR #472·#474.
실행 순서는 **Nginx Bootstrap → 정상 Baseline Adopt → DB/Capacity/Recovery Gate → Host Pull 활성화**다.
이 문서는 운영 실행 승인이 아니다. 실제 Host 접속·파일 쓰기·reload/recreate·adopt·DB 접근·정책/systemd 설치·timer 시작을 수행하지 않는다.
각 실행 직전 현재 상태를 다시 관측하고 승인한다. 저장소 PR 병합은 운영 승인이 아니다.

## Task 1: 이미지 식별자 Correction

Registry의 승인 Digest는 단일 Manifest 또는 Index다. Index이면 유일한 linux/amd64 child의 Digest·size·mediaType과 실제 bytes를 대조하고 child의 Config Digest를 검증한다.
원격 Config의 platform/revision/source, local RepoDigests·platform·labels·size Gate를 유지한다.
Store는 같은 Engine의 `docker info`로 독립 판별한다. 지원하는 기존 Docker Store는 `OSType=linux`, `Driver=overlay2`, 유효하고 중복 없는 `DriverStatus`에 `driver-type` 항목이 없는 경우로 한정하며, 이때만 Local ID = Config Digest를 요구한다. containerd는 `Driver=overlayfs`와 `driver-type=io.containerd.snapshotter.v1`을 함께 요구한다([Docker 공식 식별 방법](https://docs.docker.com/engine/storage/containerd/)). Store 미확인·정보 누락/모순·legacy 응답의 Descriptor 존재는 STOP한다. containerd는 Docker가 제공한 Descriptor의 Digest = 승인 Digest = Local ID와 mediaType/size를 요구하고, platform을 지정한 local inspect의 Manifest ID를 검증된 child와 대조한다. containerd Descriptor 누락은 Config ID로 fallback하지 않고 거부한다.
실행 컨테이너의 Image는 이 검증을 통과한 실제 Local ID와 비교한다. 식별자가 다르다는 사실만으로 허용하지 않는다.

Registry 원문 Index/Manifest만 `oci-release-state.json`의 각 record `registry_manifests`에 보존한다. Secret/Config 내용/layer는 보존하지 않는다.
매 검증에서 원문 bytes hash와 승인 Manifest→Config 관계를 다시 확인한다. 보호된 verified previous의 근거를 새 CLI process에서도 읽어 local-only rollback/실패 복귀에 사용하며 Registry를 추가 호출하지 않는다.
preflight는 이 근거를 메모리에만 둔다. 기존 state schema와 manifest/approval 입력은 유지한다. 과거 record에 이 근거가 없고 Descriptor 검증이 필요하면 STOP이며 자동 backfill·Config ID fallback·state 수동 편집으로 우회하지 않는다.
Docker의 Descriptor/platform 동작 근거: [Moby containerd inspect 구현](https://github.com/moby/moby/blob/master/daemon/containerd/image_inspect.go). 해당 API 필드가 제공되지 않거나 의미가 달라 검증할 수 없으면 STOP한다.

## Task 2: Nginx Bootstrap

### 승인 전 Preflight

- 전달된 2026-10-28 G0는 구형 정적 proxy_pass와 Release state/타이머 부재를 보고한다. 이는 시점 자료이며 현재 Host의 Source/Hash·정상성·승인을 대신하지 않는다. 실제 mount 경로는 inspect로 재확인한다.
- 운영자가 Backend/Frontend/Proxy의 Compose project/service/config_files labels, container ID/StartedAt, Image ID·RepoDigest·revision/source, Network·Volume identity를 비민감 evidence reference로 고정한다. sibling observability/metrics-proxy도 동일하게 고정한다. env 내용·인증서 private key·raw inspect는 Chat/PR/로그로 내보내지 않는다.
- 기존 Proxy의 실제 bind Source, regular file/owner/mode, byte hash와 effective `nginx -T`의 계약을 보안 경계 안에서 대조한다. 기존 Compose 파일과 필요한 주입 경로를 별도로 보존한다. bytes 백업은 기존 인증서 파일 복제 없이 root 보호 경로에 둔다. mount 원본을 덮어쓰지 않는다.
- 기준 source는 `6bf9fe61eb131e35b6a450ad906edcada9da997b`의 `infra/production/nginx.https.conf`다(마지막 변경 commit `128a78cb2602c7f18ea098d02467479b8c5c3d34`). Template SHA-256 `55c5d19e194223c3ffbd823f836120a892e7b790c6e0676ad68cd29c400b070b`, 승인 hostname 치환 bytes SHA-256 `03494460e700fbbc6a128ba5e3d82dec8e9063494a5f2ddcb83b825d60283c16`. 이는 저장소에서 계산한 기대값이며 Host 실측값이 아니다. 실행 시 승인 source와 실제 bytes를 재대조하고 CLI의 token fingerprint도 통과해야 한다.
- 같은 ref Compose SHA-256은 `fec109c159e495b7a06e94d19f98f0793f42c1424f8f54a76546f55839fe0426`다. 기존 실제 Compose가 다르면 proxy-only 교체와 후속 adopt의 config_files 검사 모두 충족할 수 있는지 먼저 검토한다. Backend/Frontend labels를 조작하거나 무승인 recreate로 맞추지 않는다. 불일치는 추가 승인 Delta다.
- Proxy image는 Release CLI의 pinned `PROXY`와 같아야 한다. DNS resolver `127.0.0.11`, timeout/valid, Backend·Frontend upstream의 shared zone/resolve를 확인한다. TLS hostname/chain/만료, HTTPS redirect·unknown host 거부, ACME webroot, 로그인 rate limit, 헤더, 내부 8081과 외부 80/443 경계를 검증한다.
- 기존 letsencrypt/certbot-webroot named volumes와 Networks를 재사용한다. 인증서 유효기간·갱신 unit/timer·reload-pending을 확인한다. deploy lock 다음 renewal lock 순서로 nonblocking 획득하며 inode 교체·강제 해제하지 않는다. 갱신 중이면 Abort하고 재계획한다. certbot staging/dry-run도 외부 실행이므로 별도 승인 범위다.
- 격리 검증은 기존 `test-production-nginx.sh`(임시 인증서·upstream IP 교체) 및 `test-production-https-renewal.sh`(lock/pending 계약)를 사용한다. 격리 성공은 실제 인증서 갱신 성공이 아니다.

### 별도 승인 후 Apply / Postflight

승인 입력은 대상 Host의 비민감 reference, 기존/신규 source·hash, 정확한 Proxy image, Compose identity·주입 경로, 백업 reference, 유지할 resources, 작업 창·허용 중단, lock 순서, 담당자·복구 기한, 성공/Abort 기준이다. 미확인 값이 있으면 실행 승인을 요청하지 않는다.

운영자는 두 lock을 유지하고 승인 bytes를 새 root-owned 보호 파일로 설치한 뒤 기존 인증서 volumes를 read-only로 연결한 격리 `nginx -t`를 먼저 통과시킨다. 검토한 **동일 Compose identity/주입 값**으로 `up -d --no-deps --pull never --force-recreate proxy`에 해당하는 proxy 한 개 교체만 승인한다. 구체적인 Compose file/env 인자는 위 실측 뒤 확정한다. 다른 service나 project는 호출하지 않는다. 기존 bind 파일을 바꾸고 reload하는 방식으로 provenance를 잃지 않는다.

Proxy recreate에는 짧은 서비스 중단/5xx가 가능하다. 무중단을 약속하지 않는다. Postflight는 새 mount hash·effective 계약·nginx -t·health, 내부 8081 `/products`·`/api/products` 직접 2xx, 인증서 검증 외부 HTTPS 두 경로 직접 2xx, ACME/헤더·rate limit, sibling identity/StartedAt 불변, Backend/Frontend identity 불변을 확인한다. 운영 rate-limit probe의 횟수/영향도 승인 범위에 포함한다.

### Abort / Recovery

hash/identity drift, lock 경합, 인증서/용량/health 미확인, 설정 검사 실패는 교체 전 Abort한다. 교체 뒤 health/TLS/라우팅/보안 계약 실패 또는 허용 기한 초과면 승인된 기존 Compose·bind bytes·image로 Proxy 한 개만 복원하고 기존 정상 서비스 기준을 다시 확인한다. 구형 설정으로의 복원은 incident containment이며 Bootstrap 성공이 아니다. 이때 Adopt/CD는 STOP한다.
원본/복원 image가 없거나 복원 후 health가 실패하면 추가 자동 작업을 중단하고 수동 Incident/Recovery로 넘긴다. DB·인증서·Backend/Frontend·siblings·volumes를 되돌리거나 삭제하지 않는다.

## Task 3: 정상 Baseline Adopt 준비

Bootstrap Postflight 후 새 시점의 다음 evidence를 묶는다. manifest/approval 구조·identity 계산·실행 명령은 [Release Runbook](OPS-OCI-CD-02-release-recovery.md)을 따른다.

| 입력 | 채택 가능한 근거 / STOP |
| --- | --- |
| App SHA와 두 이미지 | 현재 실행 revision/source·Registry raw Index/Manifest/Config·검증된 local ID·RepoDigest·linux/amd64·size가 일치. 과거 전달 SHA나 축약 Digest로 입력 생성 금지 |
| Source fingerprint | 승인 main 조상의 Compose/Nginx source SHA, 실제 Compose config_files bytes와 전환 후 Nginx mount bytes가 일치. Source만 맞고 실행 bytes/labels가 다르면 STOP |
| Runtime 경계 | root source/state/runtime·bundle·env hash, HTTPS adoption·pending 부재, Network/Volume·DB egress·siblings 불변. 기존 source chown/재구성은 별도 승인 |
| 정상성 | 전체 health, 내부 8081 직접 2xx, 외부 TLS 검증 직접 2xx, metrics-proxy identity/endpoint 정상의 독립 관측 |
| DB | Managed MySQL identity/epoch, 현재 Flyway 성공 이력·checksum·pending/failed 부재 및 migration tree 대조. 동일 tree만으로 baseline 검증 선언 금지 |
| State | 권위 state·transition 부재 확인. current-sha·images projection이 있으면 일치 필요. 충돌/불확실성은 수동 조사 |
| 승인 | `action=adopt`, 정확한 `manifest_sha256`, `expected_current=null`, 독립 baseline evidence, DB compatibility evidence/epoch, 만료(최대 24시간), 실측 capacity/reserve/rollback budget. `baseline_verified=true`는 이 독립 확인과 명시적 승인 후에만 입력 |

Adopt는 컨테이너 교체 없이 정상 상태를 채택한다. 최초 previous는 없으며 기존 이미지로 verified previous나 rollback_safe를 만들어내지 않는다. 첫 후보의 복귀 대상은 채택된 정상 current여야 한다. `adopt --execute-approved`는 별도 Production 승인 전 STOP한다. Task 3의 **설계**는 완료 가능하지만 Bootstrap/Adopt 실증 없이 Task 4/5 운영으로 진행할 수 없다.

## Task 4: DB · Capacity · Recovery Gate

### DB 독립 observer 설계와 blocker

[기존 compatibility_observer 계약](OPS-OCI-CD-003-host-pull.md#db애플리케이션-호환-검증기-미해결-g0-입력)을 재사용한다. 현재/후보 정확한 SHA, runtime hash, DB epoch, checked/expires(최대 120초), evidence reference와 observations를 바인딩한다. 관측 실패·범위 불명·기한 경과·데이터 변경으로 결과가 무효면 true를 반환하지 않는다.

운영 승인 전 DBA/운영자가 Managed MySQL identity/상태·engine version, backup retention·최근 성공·PITR window·RPO/RTO·격리 복구 검증 reference를 독립 확인해야 한다. migration Git tree와 실제 Flyway version/description/checksum/success를 대조한다. 복구 불가/미실증은 blocker이며 앱 rollback이 DB 복구를 의미하지 않는다.

후보 쌍의 실제 코드/계약 변경을 검토하고, 별도 승인된 비운영 DB에서 current→target→current의 읽기/쓰기·실패/재시도를 검증한다. 필요한 불변식은 인증/session·권한 지속성, 주문 상태·합계·멱등성, 결제 중복청구/환불 일관성, 재고 예약·차감·해제와 음수/중복 방지, 정기배송 schedule·order의 중복 실행 방지다. 해당 쌍에서 무관하다고 제외한 불변식도 근거를 남긴다. target이 쓴 상태를 current가 읽고 안전하게 처리하는 역방향 근거가 필요하다. 원시 row·PII는 출력하지 않는다.

**미구현 blocker/Delta:** 이 의미상 검증기의 실제 DB identity/Flyway read adapter, 불변식 관측/query 및 pair 검증 방법, freshness/경합 무효화 조건, 최소 read 권한·보호된 실행환경·검증 evidence 위치를 별도 설계·승인·테스트·설치해야 한다. 이번 PR은 fake 구현을 추가하지 않는다. migration tree/path allowlist/CI Green만으로 application_data_compatible·rollback_safe를 생성하지 않는다. 운영 DB 접근/쓰기·Downgrade/Restore/Flyway repair/clean은 실행하지 않는다.

### Capacity: 실측 없으면 STOP

운영자는 `/var/lib/docker`·실제 DockerRootDir와 `/var/lib/containerd`·실제 containerd root의 mount/filesystem/device·available bytes, state/runtime의 filesystem, 현재/후보/복귀 이미지의 local content·unpacked·shared 사용량을 관측한다. DockerRootDir만 보고 containerd 여유를 추정하지 않는다. 전달된 과거 free bytes는 판정에 재사용하지 않는다.

신규 필요량은 기존 CLI의 `2 × compressed layers + 후보 unpacked 상한 합 + reserve + rollback_extra`를 하한으로 유지한다. reserve ≥ 1GiB, rollback 추가 쓰기 양수·실측 근거가 필요하다. 기존 이미지·인증서·볼륨·observability 사용량은 이미 차지한 실제 사용량으로 보존한다. pull/download/unpack 중복, container recreate writable/metadata/log, config/state/transition/fsync/temp와 실패 후 복귀 peak를 포함해 filesystem별 최대치를 측정한다. local-only rollback은 재다운로드/unpack 없이 reserve + 실제 추가 쓰기를 검증한다. 공유 layer/reclaimable을 신규 free로 합산하지 않는다.

**구현 한계:** 기존 capacity 함수는 DockerRootDir/state/runtime device만 비교하며 containerd 별도 root/filesystem을 직접 검증하지 않는다. 두 저장소가 같은 filesystem임을 독립 입증하고 위 보수 상한을 충족할 때만 현재 계약의 적용을 검토할 수 있다. 다른 filesystem이거나 실측/수용 근거가 없으면 STOP하고 filesystem별 capacity 관측·검증 Delta를 별도 승인한다. prune·image/volume 삭제·증설로 우회하지 않는다.

### Recovery

verified current/previous의 정확한 image graph·local image·source/config와 runtime hash·DB epoch·pair 호환·capacity를 보존한다. Registry 장애 때도 local-only image 검증에 성공해야 한다. 복귀 image/근거 부재면 자동 시도하지 않는다.
transition/attempt 또는 commit 불확실성이 있으면 CD OFF/kill switch를 유지하고 state.current와 실제 image/config/health부터 대조한다. 권위 current가 실제 정상 runtime과 일치할 때만 별도 승인된 recover-state를 고려한다. 다른 target 실행·state 손상·복귀 실패는 수동 Incident다. DB 자동 복구·force unlock·marker 삭제로 성공을 만들지 않는다. Bootstrap 복구와 application rollback은 각각 별도 승인한다.

## Task 5: Host Pull 최초 활성화 준비

1. 병합 후 승인한 commit에서 `oci-cd.py`, 수정된 `oci-release.py`, `classify-production-image-changes.py` 각각 bytes SHA-256을 계산하고 설치본과 정책 `controller_sha256`을 대조한다. 이 문서의 작업 전 hash를 설치 승인으로 쓰지 않는다. fetched source 실행/자동 controller 갱신은 금지다.
2. root-owned 전용 bare source·정확한 origin·non-force FF/CAS, Host GitHub/GHCR outbound read·Docker socket 접근을 독립 검증한다. credential·Public SSH·비용 변경은 별도 승인이다.
3. 승인된 정상 baseline, Task 4의 실제 observer·DB epoch/runtime/migration·capacity/복귀 budget, 정확한 Compose/Nginx hash를 root `600` 최초 정책에 결합한다. 예제 OFF/0/null 값으로 설치하지 않는다.
4. exact main 후보 SHA의 push/main/workflow/latest attempt 및 **각 필수 job** 성공과 동일 SHA 양쪽 이미지 증거를 확인한다. 과거 후보에 새 CD의 exact-SHA CI가 없으면 STOP한다. PR CI·readiness dispatch·image 존재·후속 docs-only CI로 대체하지 않는다.
5. 최초 정책 설치, sentinel 설치, systemd unit/timer 설치·enable/start는 각각 명시적 운영 승인 범위에 묶어야 한다. 활성화 전 kill switch·만료/정책 OFF 차단과 journal 수신 주체·연락/대응시간을 검증한다. journal 생성만으로 운영자 수신을 주장하지 않는다. 외부 push는 현재 미연결이며 필요 여부/수신 경계 미확정은 blocker다.
6. 정책상 허용된 새 main **애플리케이션 변경**을 별도 PR로 검토·승인하고 첫 E2E 대상으로 삼는다. current→후보 전체 diff의 위험·양방향 호환과 모든 Gate가 통과해야 한다. 이 도구/Runbook PR만으로 새 앱 배포 후보를 만들거나 과거 후보를 허용하지 않는다.
7. 활성화 후 정확한 target/current/previous SHA·image IDs·health·내외부 HTTPS·sibling 보존·성공/차단/실패 알림 수신, 실패 시 검증된 current 복귀와 state/transition/attempt를 관측한다. 실패 주입·실제 rollback은 별도 승인한다. 기대 기한 내 성공하지 않으면 kill switch와 수동 Recovery 경계를 적용한다.

## Task 종료 상태와 다음 Gate

| Task | 저장소 결과 / 미실행 | blocker · 다음 진행 · 별도 승인 |
| --- | --- | --- |
| 1 | CLI Correction·격리 회귀 준비, 실행 결과는 PR에 기록 | Repository Validation 및 독립 Review 후 사용자 merge 판단. Task 2 설계 진행 가능 |
| 2 | Bootstrap Preflight/Apply/Postflight/Abort/Recovery 설계. 운영 미실행 | 실제 mount/Compose/effective 계약·인증서·복원본·중단 창 확정 후 Bootstrap 별도 승인. Task 3 준비만 가능 |
| 3 | Adopt 입력·독립 정상성·source 보호 조건 설계. adopt 미실행 | Bootstrap Postflight·실제 baseline/DB/source/capacity 없으면 STOP. 별도 Adopt 승인 |
| 4 | DB observer 의미상 Delta, filesystem별 peak·Recovery Gate 설계. 실제 DB/용량/복구 미검증 | observer 구현/독립 검증·운영 실측·복구 근거 없으면 STOP. 필요한 관측/검증과 수동 Recovery 별도 승인 |
| 5 | 기존 controller/정책/systemd를 쓰는 활성화/E2E 조건 설계. 설치·시작 미실행 | 이전 Gate·exact-SHA CI·알림 수신·정책 범위 확정 후 최초 활성화 별도 승인. Production Verified 아님 |

CodeRabbit 요청·PR 병합·운영 실행은 이 저장소 준비에 포함되지 않는다. PR #474의 reviewer 예외를 상속하지 않는다. 독립 Review 미완료는 별도로 남기며 테스트 통과로 대체하지 않는다.
