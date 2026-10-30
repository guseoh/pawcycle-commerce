# G0-T2A — Source / Runtime / Lock 신뢰 경계 정렬

상위 #470, 관련 PR #475 / OPS-OCI-CD-004. 고위험 / 저장소 변경 및 운영 설계.
이 문서는 실행 승인이 아니다. 실제 Source/Runtime/Lock/Container/Certificate/DB에는 접근하거나 변경하지 않는다. 전달된 opc 소유 경로·Healthy·CD OFF 등의 관측은 시점 근거이며 실행 직전 재확인이 필요하다.

## 1. 대안과 선택

| 대안 | 이점 | 제약·판정 |
| --- | --- | --- |
| A. 기존 경로 최소 소유권 전환 | state/deploy.lock·Runtime 경로·Compose Label을 보존 가능 | `protected()`는 파일뿐 아니라 `/`부터 모든 ancestor의 root 소유·비쓰기 경계를 요구한다. `/opt/pawcycle`이 opc이면 root state도 보호되지 않는다. 전체 재귀 chown은 금지. 명시한 directory inode·mode와 필요한 파일만 개별 승인 가능 |
| B. 별도 Root-owned Source/Runtime/State | opc checkout과 secret 원본을 그대로 보존 | 새 Source만으로 기존 config_files Label을 충족하지 못한다. state 분리는 갱신/진단/catalog/auth와 deploy.lock을 둘로 나눌 위험이 있고 CD는 state/runtime 기본 경로를 고정하므로 선택하지 않는다 |
| C. 승인된 Compose Label 정렬 | 새 Root-owned Compose 경로를 컨테이너 identity에 반영 가능 | Label은 실행 identity다. 실행 컨테이너 Label을 사후 조작하지 않는다. 새 파일 경로가 필요하면 Backend/Frontend lifecycle 변경이 필요할 수 있어 별도 운영 승인 전 STOP |

**선택: A의 제한된 경로 보호 + B의 깨끗한 Source와 새 bundle, C는 조건부 별도 승인.** state/deploy.lock 경로와 inode는 유지한다. `/opt/pawcycle` 및 `runtime` directory 자체를 root 소유·group/other 비쓰기(상위 0755, runtime 0700)로 보호하는 **비재귀 metadata 변경**을 별도 승인 대상으로 한다. 기존 opc backend.env와 Source 내용·git 소유권을 일괄 변경하지 않는다. opc의 이후 상위 경로 rename/create가 제한되는 영향은 운영자가 승인해야 한다. 적용 불가하면 state 복사나 검사 완화로 우회하지 않고 STOP한다.

Release Source는 보호된 ancestor 아래 새 root-owned 검증 checkout으로 구성한다. origin·정확한 승인 commit·main 조상·Compose/Nginx bytes·migration tree를 검증하고 opc checkout의 `.git`, hooks, config, executable을 root 신뢰로 승격하지 않는다. CLI smoke가 Source 아래 관측성 identity 검증 shell을 실행하므로 단순 bare Git만으로 수동 Release Source를 대체하지 않는다. Host Pull의 별도 bare source/설치 controller 계약은 그대로 유지하며 그 통합 실행 경계는 활성화 전에 별도 확인한다.

실제 세 config_files 경로의 **정확한 ancestor 목록**을 먼저 확정한다. 경로가 제한된 root 보호 전환을 허용하고 Compose 파일도 root regular·single-link·비쓰기이며 승인 hash와 같아질 수 있다면 Label 문자열·컨테이너를 유지하는 방안을 우선 검토한다. opc checkout 업데이트가 해당 보호 경로를 교체할 수 없도록 운영 책임도 전환해야 한다. `/home/opc` 등 광범위 개인 경로의 소유권 변경이 필요하거나 안전한 기존 경로 고정이 불가능하면 이 방안은 거부한다.

그 경우 새 Root-owned Compose 경로로 정렬하려면 C가 필요하다. **Proxy-only 교체는 Backend/Frontend의 config_files Label을 바꾸지 못한다.** 실제 Backend/Frontend recreate는 수행하지 않으며, 별도 승인에 세 서비스 범위·유지할 image/env/network/volume·예상 중단·session/정기배송 영향·DB migration 위험·복귀 경로를 포함해야 한다. 같은 Compose SHA라는 사실은 경로 소유권과 실행 환경의 동등성을 입증하지 않는다.

## 2. 기존 도구와 의존성

- Release `initial()`은 root Source/state/runtime, HTTPS adoption/pending, `runtime/current → .bundle.*`, env/.complete의 root 0600을 요구한다. `runtime_check()`는 세 서비스의 config_files 파일과 Proxy 실제 bind를 각각 검사한다. proxy nginx bytes가 승인값이어도 opc Compose 경로를 무시하지 않는다.
- 갱신기는 Source나 backend.env를 소비하지 않고 실행 Proxy identity·mount·인증서 named volumes·HTTPS state를 검사한다. renewal.lock만 획득한다. Release는 deploy.lock 다음 renewal.lock을 nonblocking으로 획득한다. 순서를 바꾸거나 lock을 복제하지 않는다.
- catalog/auth 도구는 같은 state/deploy.lock을 사용한다. `g0-t2a-canonical-v1` marker bundle은 Canonical9-key `backend.env`에서 검증된 `catalog.env`9-key, `auth.env`6-key, `release.env`6-key를 읽는다. 공통 `production_env.py`가 unknown/missing/중복 key와 의미 차이를 거부하며 값은 추가·추론하지 않는다. 기존 legacy bundle/running-container 경로는 그대로 유지한다. Catalog/Auth의 기존 streamer는 automation batch/delay를 제거하고 enabled=false를 강제하는 계약을 유지한다.
- 진단의 `PAWCYCLE_STATE_DIR`/`--state-dir`, 운영자 cron·sudo·unit·작업 디렉터리·Source 업데이트 주체와 관측성 identity 경로 의존성도 목록화한다. sibling observability/metrics-proxy 컨테이너와 인증서 volumes는 전환 대상이 아니다.

## 3. Runtime Bundle 설치·검증 계약

현재 `runtime/backend.env`를 source/echo하거나 환경변수 내용·raw inspect·secret hash를 Chat/PR/로그에 출력하지 않는다. 값과 byte hash는 비공개 프로세스 메모리 안에서만 대조하고 evidence에는 PASS/FAIL만 남긴다.

1. 승인된 작업 창에 env 작성자를 중지/직렬화하고 원본 regular/no-symlink/single-link·opc 0600, inode·size·mtime를 확인한다. 부모가 opc이면 읽는 동안에도 교체될 수 있으므로 열린 FD의 fstat와 재검증 및 쓰기 중단 근거가 필요하다. 변경이 감지되면 Abort한다.
2. root 보호 runtime 안에 새 `.bundle.g0-t2a-<packet id>` directory 0700을 만들고 원본 bytes를 새 regular `backend.env` root 0600으로 복사한다. 원본을 이동·chown·hardlink하지 않는다. 원본/사본은 비공개 bytes 비교하고, 세 Consumer projection을 추가한 뒤 원본 안정성을 재확인한다. 파싱/복사 실패는 values를 출력하지 않는다.
3. Release의 필수 datasource URL/username/password와 automation enabled(boolean)/batch/fixed-delay(양의 정수), 중복 key·누락·빈 값·인용 및 실제 Compose env_file 파싱을 검증한다. datasource host/port/SSL은 사용하는 도구 계약과 대조한다. 기존 실행 컨테이너의 effective env와 semantic 동등성도 비공개 비교한다. shell eval/source는 금지다. 단순 hash 일치는 실제 주입값 검증을 대신하지 않는다.
4. 검증 결과·원본/사본 bytes 일치 판정·검증기 버전·승인 reference를 root 보호 Packet/journal에 결합한다. **모든 검증 성공 후에만** `g0-t2a-canonical-v1` 한 줄의 `.complete` root0600을 생성한다. Release는 Canonical과 release projection 일치도 확인한다. marker를 미리 touch하거나 비공개 env hash를 evidence에 쓰지 않는다.
5. env·marker 파일을 fsync하고 bundle directory를 fsync한다. 같은 runtime directory 안의 임시 symlink를 완료된 bundle로 연결한 뒤 rename으로 `current`를 원자 전환하고 runtime directory를 fsync한다. 최종 `current` resolve가 runtime 바로 아래 완료 bundle인지 확인한다. 기존 current가 없었던 사실과 기존 파일·bundle은 보존한다.
6. 이 연결은 이미 실행 중인 Backend의 env를 바꾸지 않는다. 설치 후 보호 경계·hash·marker/필수 key와 기존 서비스 identity/health를 재확인한다. 동등성 검증과 Source/Label/Nginx가 미완료면 Adopt는 계속 STOP이다.

Bundle 복구는 deploy/renewal lock과 env 작성자 중지 경계를 유지한 상태에서 기존의 검증된 current target으로 atomic 복원한다. 최초 설치라 이전 current가 없으면 새로 만든 연결만 제거하고 기존 backend.env는 보존하며 CLI/Adopt/CD를 OFF로 둔다. 새 bundle이 실행 컨테이너에 이미 소비됐으면 symlink만 돌려 서비스가 복구됐다고 주장하지 않고 별도 승인된 lifecycle Recovery로 넘긴다. 미완성 bundle을 current에 연결하거나 자동 삭제하지 않는다.

## 4. Renewal Lock 전환

갱신기는 root 소유의 안전한 ancestor, root regular 단일-link 0600 lock만 사용한다. 신규 생성은 script 내부 umask 077 + exclusive create로 수행해 service UMask=0022에서도 0600이다. 기존 파일은 truncate/chmod/unlink/replace하지 않고 `<>`로 열어 FD와 경로의 device/inode·owner/mode/nlink를 대조하고 실제 flock을 유지한다. symlink/hardlink·owner/mode 위반은 certbot/reload 이전에 거부한다. `/run/lock`의 기존 sticky directory 계약은 Release와 같다. systemd unit 변경은 안전성의 전제가 아니며 기존 갱신 동작을 유지한다.

현재 0644 lock은 새 script가 자동 보정하지 않는다. 별도 승인된 운영자는 renewal timer/진행 중 갱신과 Release/catalog/auth 작업을 배제하고, 실제 해당 regular root 단일-link inode를 열어 nonblocking flock을 획득한 뒤 **그 FD에만 fchmod(0600)**하고 inode 동일성을 재확인한다. lock을 rename/delete/recreate하지 않는다. 누군가 lock을 보유하거나 안전하지 않은 파일이면 STOP한다. 그 뒤 검토된 갱신 script를 설치하고 기존 unit/인자·timer 상태를 확인한다. UMask 변경·timer 시작은 이번 저장소 작업에서 수행하지 않는다.

permission 전환 실패 시 서비스는 건드리지 않고 timer/CD를 중지한 채 복구 담당자가 조사한다. 정상 0600 lock을 옛 0644로 자동 복원하지 않는다. **c09302a8의 `exec 9>"$LOCK_FILE"`은 flock 전에 truncate한다.** 같은 inode·0600이라도 bytes 보존을 보장하지 않으며, 다른 작업이 Lock을 보유 중이어도 truncate할 수 있다. 따라서 구버전 복원만으로 복구 성공을 선언하거나 timer를 재개하지 않는다. §8.3의 안전한 갱신기 복원 Gate가 필요하다. 실제 갱신·ACME dry-run·Nginx reload는 별도 승인이다.

## 5. 최초 전환 순서와 승인 Gate

| 단계 | 승인·확인·중단·복구 |
| --- | --- |
| Preflight | 전달된 근거 재관측, 정확한 Source/Label/mount/ancestor·UID/mode/inode·env 계약·lock·unit 경로 목록, 세 서비스/sibling identity/health·HTTPS, 인증서 유효성·pending/state/transition 부재를 확정. 운영 raw 값은 비공개 evidence로 관리 |
| 별도 승인 | 변경할 각 directory/file metadata와 설치 source/hash, bundle 검증·전환, 기존 0644 lock의 FD permission 변경, 유지할 자원·작업 창·담당자·Abort 기한·복구 입력을 각각 승인. 미확인 경로나 env 의미가 있으면 실행 승인 입력을 완성하지 않음 |
| Apply 1: 직렬화·경로·Lock | Quiescence → 최소 경로 보호(Label 포함) → deploy → renewal 획득. Label 경로 보호 불가 또는 경합이면 STOP. Source 설치·env 복사는 두 Lock 획득 전 금지 |
| Apply 2: Source/갱신기 | 두 FD를 유지하며 독립 root Source 준비·검증 → renewal FD fchmod0600·script 설치. Source network timeout·작업 창 만료·검증 실패는 Abort |
| Apply 3: Runtime | 같은 감독 session과 두 FD 아래 bundle 검증·전환. 서비스 교체 없음. current만 바꾸어 실행 env가 바뀌었다고 주장하지 않음 |
| Postflight·해제·timer | 두 FD 유지 상태에서 protected 경로·Label·bundle 동등성·inode/bytes·서비스/sibling·HTTPS·인증서/volume 보존 검증 → renewal/deploy 역순 해제 → 안전한 갱신기 복원 Gate 확인 → 승인된 timer 복귀 |
| 다음 별도 Gate: Nginx | G0-T2A 종료와 Lock 해제 후 OPS-OCI-CD-004의 독립 Bootstrap 승인·검증·Proxy-only 복구. 이번 Apply 안에서 실행하지 않음 |
| Abort | drift·Lock 경합·권한/marker/Label/의미 검증 실패·건강성 저하 시 다음 단계 금지. 기존 정상 서비스는 유지, CD OFF·Adopt STOP |
| Recovery | 변경한 metadata 목록·기존 owner/mode·Source 및 current target을 보호 evidence로 복구. root Source를 opc writable 상태로 복구하면 CLI/CD는 계속 OFF. 0600 공유 lock은 보존. 컨테이너 교체가 있었으면 별도 승인한 image/env/config 복귀 및 health를 확인하며 DB/인증서/볼륨 복구로 확대하지 않음 |

## 6. 검증과 남은 blocker

`test-production-https-renewal.sh`는 root disposable fixture/fake Docker로 UMask0022 신규 lock0600, 기존 bytes/inode 유지, 외부 flock 경합, symlink/hardlink·owner·mode 거부와 기존 갱신/pending/no-restart 회귀를 검증한다. Release/CD 회귀와 Production Contract 및 최신 HEAD Repository Validation 결과는 PR에 기록한다. 실제 경로·bundle 설치·운영 경합·갱신·컨테이너는 검증하지 않았다.

Adopt에는 여전히 안전한 세 config_files 경로, 새 bundle의 실제 주입 동등성, 전환 후 Nginx fingerprint, Source/HTTPS·DB epoch·Flyway 및 정상 baseline·capacity 근거가 필요하다. Host Pull은 추가로 검증된 controller/source 실행환경·DB compatibility observer·filesystem별 peak/recovery budget·exact-SHA CI·알림 수신·최초 정책/활성화 승인이 필요하다. 이 문서나 `.complete` 생성은 어느 Gate도 자동 승인하지 않는다.

## 7. 운영자가 채워야 하는 승인 Packet (Task 1)

아래 명령은 **미실행 운영 명령**이다. 이 PR은 OCI/SSH/Cloud Shell 접속 권한을 부여하지 않는다. 명령 블록은 일괄 실행 스크립트가 아니다. 각 단계 결과를 확인한 뒤 다음 단계로 이동한다. Bash root session에서 `set +x; umask 077`을 사용하고 terminal recording·history·CI 업로드를 끈다. 경로 변수는 비공개 승인 Packet의 정확한 값으로 설정한다. 미설정 변수, placeholder, 빈 경로는 STOP이다.

승인 Packet은 root 전용 별도 evidence directory(보호된 ancestor, 0700)에 보관한다. 각 대상에 `absolute path / realpath / type / numeric UID:GID / octal mode / device:inode / nlink / size / mtime_ns / ctime_ns / public source SHA256 또는 private comparison PASS / desired metadata / impact / inverse operation / approval`을 기록한다. symlink는 link와 target을 구분한다. directory·lock의 내용 hash는 적용하지 않는다. env와 env에서 파생된 hash는 Packet에도 출력하지 않고 메모리 내 동등성 판정만 기록한다. 기존 §3의 비공개 hash 대조는 **비공개 프로세스 내부 비교**를 뜻한다.

| 대상 | 기준·전환값 | 영향 / 복구 입력 |
| --- | --- | --- |
| `/`, `/opt`, `/opt/pawcycle` 및 Label의 모든 ancestor | root UID/GID와 group/other 비쓰기; `/opt/pawcycle`은 승인 후 0:0 0755 | directory inode 유지; opc create/rename 중단. 개별 원래 UID/GID/mode와 ACL 필요 |
| `/opt/pawcycle/state`, `state/deploy.lock` | 0:0 0700 / 0:0 0600 유지 | state bytes 및 deploy.lock device:inode 불변. Release State 신규 생성 금지 |
| `/opt/pawcycle/runtime`, 원본 `backend.env` | directory만 0:0 0700; env는 원래 opc UID/GID 0600 유지 | opc env writer 접근 제한. 원본 inode/bytes 불변; writer 재개는 별도 판단 |
| 각 서비스의 config_files 경로 및 Proxy bind source | 정확한 문자열·regular·nlink=1·root·비쓰기·승인 bytes | 기존 파일 inode/bytes 보존, 부모만 최소 변경. 원래 metadata/ACL과 사용 주체 필요 |
| 독립 Source와 `.git` 전체 | 새 root 생성; 승인 SHA, exact origin, 비쓰기 ancestor | opc checkout executable/config를 실행하지 않음; 되돌릴 때 미사용 Source 보존 |
| renewal lock | 실제 unit 설정 경로, 0:0 0644 → 0600; device:inode 불변 | bytes 불변. old/new script hash, timer 상태와 복구 담당자 필요 |
| 설치 renewal executable | 실제 ExecStart 경로, 0:0 0755, 승인 main bytes | 단일 파일 교체; unit/drop-in은 그대로. 보호된 기존 bytes/metadata 백업 필요 |
| bundle/current | 새 0:0 0700 bundle, env/marker 0600; current 최초 부재 재확인 | 원본 env 유지; 이전 target 또는 부재, 미완성 directory, durable phase 기록 필요 |
| 서비스·sibling·DB·인증서·볼륨 | 변경 없음 | container ID/image ID/StartedAt/RestartCount/health, mount/network/volume identity, 인증서 serial/expiry 기준값 필요 |

**읽기 전용**, 승인된 운영자가 실행 직전에 수행할 최소 metadata 확인 예시:

```bash
stat -c '%n %F %u:%g %a %d:%i %h %s %Y %Z' -- /opt/pawcycle /opt/pawcycle/state /opt/pawcycle/state/deploy.lock /opt/pawcycle/runtime /opt/pawcycle/runtime/backend.env /run/lock/pawcycle-production-https-renewal.lock
namei -l -- /opt/pawcycle/state/deploy.lock
systemctl show pawcycle-production-https-renew.timer -p ActiveState -p UnitFileState -p NextElapseUSecRealtime -p LastTriggerUSec
systemctl show pawcycle-production-https-renew.service -p ActiveState -p SubState -p MainPID -p ExecStart -p FragmentPath -p DropInPaths
```

unit/drop-in은 로컬에서만 검토하고 Secret 포함 가능성이 있는 전체 `systemctl cat`, process environment, raw Docker inspect는 외부에 출력하지 않는다. 서비스별 `docker inspect --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}' "$CONTAINER_ID"`로 Label만 확인한다. 각 Label과 Proxy mount source에 `namei -l` 및 위 `stat`을 개별 적용한다. ACL(`getfacl`), parent mount/read-only 상태, open writer와 자동화/sudo 권한도 비공개 확인한다. mode만으로 ACL/기존 writable FD·mmap·권한 있는 opc 작업의 재진입을 배제할 수 없다.

작업 창에는 인증서 expiry와 다음 timer trigger 사이의 여유, 최대 작업시간·Abort 시각·갱신 복원 담당자를 명시한다. env writer, 수동 배포 shell, cron, systemd, Host Pull, catalog/auth, opc checkout update와 Docker 권한 보유 작업을 모두 중단·배제할 수 있어야 한다. 열린 writable FD나 다시 실행 가능한 opc 작업을 배제하지 못하면 chown으로 해결했다고 보지 않고 STOP한다. sibling observability/metrics-proxy의 config/env/working directory가 보호 전환으로 읽기 실패하지 않는지 확인한다. 기존 checkout HEAD `c09302a82d541f0c351d0f619f30232c7adaca51` 및 사용자 제공 실측은 과거 기준일 뿐이다.

## 8. 승인 후 수동 Apply와 단계별 STOP (Task 2·3)

### 8.1 Quiescence와 최소 경로 보호

전제: §7 Packet 완성 및 운영 승인, 인증서 갱신 여유, 모든 writer 중단, root 신뢰 실행 도구 확보. 다음은 **승인 후 변경 명령**이다.

```bash
systemctl stop pawcycle-production-https-renew.timer
systemctl is-active pawcycle-production-https-renew.service
```

두 번째 명령은 inactive이면 일반적으로 nonzero다. active/activating/deactivating이면 진행 중 갱신을 강제 종료하지 않고 종료를 기다린 뒤 재관측한다. timer stop은 이미 queue된 service나 수동 실행을 취소하지 않는다. job queue와 MainPID=0을 확인하고 수동 시작 주체도 배제한다. 경합/기한 초과는 STOP하고 기존 갱신 기능 복원 판단으로 이동한다.

```bash
chown --no-dereference 0:0 -- /opt/pawcycle
chmod 0755 -- /opt/pawcycle
chown --no-dereference 0:0 -- /opt/pawcycle/runtime
chmod 0700 -- /opt/pawcycle/runtime
```

각 명령 **직전** Packet의 lstat device:inode/type/owner/mode/ACL과 대조한다. 경로 rename 가능성이 사라진 뒤에만 다음 경로를 처리한다. 다른 Label ancestor/file은 실제 승인 목록의 한 경로씩 같은 방식으로 처리한다. `chown -R`, glob, find 기반 일괄 변경 금지. symlink, hardlink, writable ancestor 또는 미승인 광범위 경로가 발견되면 STOP. 기존 opc `.git`/executable은 변경하지 않는다. 이 단계 후 state와 deploy.lock 보호를 재확인한다. 양쪽 lock 획득 전에는 Source 설치나 env 복사를 진행하지 않는다.

### 8.2 두 Lock 획득과 감독 생명주기

**정규 순서:** Quiescence → 최소 경로 보호 → deploy 획득 → renewal 획득 → Source 준비·검증 → renewal FD 0600·script 설치 → bundle 검증·전환 → postflight → renewal/deploy 역순 해제 → timer 복귀. §5와 §8~10 모두 이 순서를 따른다. Source 설치·env 복사는 두 Lock 획득 전에 금지한다.

운영 도구는 `trust_transition.py`와 `production_env.py` 두 파일이다. 검토된 root 보호 tool directory에 함께 설치한 정확한 bytes/hash를 별도 승인한다. Python은 -B로 bytecode 쓰기를 막고 core dump를 비활성화한다. stdin을 사용하지 않고 FD로 Secret을 읽으며 출력은 고정 PASS/FAIL code뿐이다. 아래는 **저장소 준비 결과의 호출 계약**이며 이번 Task에서 Production에 실행하지 않는다.

```bash
/usr/bin/env -i PATH=/usr/bin:/bin LANG=C /usr/bin/python3 -E -s -B /var/lib/pawcycle-transition/tools/trust_transition.py preflight --packet /var/lib/pawcycle-transition/approval.json
# 독립 운영 승인 후에만:
/usr/bin/env -i PATH=/usr/bin:/bin LANG=C /usr/bin/python3 -E -s -B /var/lib/pawcycle-transition/tools/trust_transition.py apply --packet /var/lib/pawcycle-transition/approval.json
# crash/incident의 읽기 전용 journal 판정; 자동 복구/재시도 아님:
/usr/bin/env -i PATH=/usr/bin:/bin LANG=C /usr/bin/python3 -E -s -B /var/lib/pawcycle-transition/tools/trust_transition.py recover --packet /var/lib/pawcycle-transition/approval.json
```

`preflight`는 파일/Timer/Lock을 변경하지 않으며 Packet·현재 identity·Canonical syntax·열린 writer·인증서·runtime 기준을 확인한다. Source 설치/Root 경계 전환 후에만 가능한 전체 Compose/effective-env 검증까지 성공했다는 뜻은 아니다. `apply`는 재검증 후 고정 순서로 한 번만 진행한다. `recover`는 journal complete만 `PASS:RECOVERY_RECORD_ONLY`로 읽고, 다른 phase는 exit4 `FAIL:INCIDENT`를 반환한다. 실제 복구 성공을 주장하지 않는다.

Packet은 `/var/lib/pawcycle-transition`0700 바로 아래 root:root0600 단일 regular file(최대64KiB)이다. JSON schema=1의 정확한 필드만 허용하며 중복 JSON key·Secret·env값·민감 hash를 넣지 않는다. 필수 입력은 다음과 같다. 예시 값/실측 Packet은 저장소에 보관하지 않는다.

| 필드 | 입력·검증 계약 |
| --- | --- |
| `id`, `approval_reference`, `schema` | id는 1~48자리 lowercase 영숫자/hyphen; 승인 reference는 비어 있지 않은 독립 운영 승인 근거. 단순 id/파일 존재만 승인으로 간주하지 않음 |
| `source_sha`, `main_sha`, `release_sha` | 40자리 lowercase SHA. Source SHA=main SHA, 독립 artifact의 origin은 고정 guseoh/pawcycle-commerce.git. 기존 image tag의 release SHA는 별도 고정 |
| `compose_sha256`, `renewal_sha256`, `installed_sha256`, `nginx_sha256`, `tool_hashes` | 64자리 lowercase **공개 코드/설정 bytes** hash. tool_hashes는 두 도구 파일명만 허용. env hash 금지 |
| `not_before_unix`, `expires_unix`, `recovery_seconds`, `command_timeout_seconds` | Unix 작업 창 최대1시간, 남은 작업시간에서 복구 여유(최소30초)를 뺀 단조시계 deadline. 명령 timeout1~120초. 만료/부족이면 STOP |
| `certificate_not_after_unix`, `minimum_certificate_seconds` | 실제 공개 cert.pem을 OpenSSL로 확인해 expiry 완전 일치. 작업 종료 후 최소86400초 이상 인증서 여유. 다른 정책이면 새 승인 필요 |
| `quiescence_confirmed`, `timer` | 모든 opc/sudo/manual deploy/env writer/sibling 운영자의 중단·재진입 배제 승인이 true. timer는 기존 active/enabled만 이번 전환에서 지원. service inactive/MainPID0/job 없음, env/Compose/Nginx 열린 writable FD·mapping 없음. root privileged 재진입 배제는 운영 책임이며 기술적으로 추정하지 않음 |
| `changes` | `allowed_changes()`의 정확한8개 경로를 key로 사용: base, runtime, source, source/repo, source/repo/infra, source/repo/infra/production, 그 Compose 파일, runtime/nginx-https.conf. 값은 `[dev,inode,uid,gid,st_mode,nlink,size,mtime_ns,ctime_ns]`. 추가/누락·ACL·symlink·drift 거부. root 소유로 개별 FD fchown/fchmod만 수행 |
| `env_identity`, `deploy_identity`, `renewal_identity`, `installed_identity` | 위와 같은9항목. 원본 env0600·단일 regular file, deploy root0600, renewal root0644, 설치 script root0755. 원본 env owner/bytes 유지. Lock 삭제·생성·truncate 금지 |
| `snapshot` | preflight의 private `snapshot()` 형태로 모든 컨테이너 ID/image/ref/StartedAt/status/health/restarts·Mounts·project/service/config_files·network ID/endpoint ID를 고정. 세 앱은 Healthy이며 기존 Label 정확한 경로 유지. DB/sibling 포함 전체 snapshot 비교 |
| `backend_image`, `frontend_image`, `compose_version`, `exclusions` | 기존 image repository·정확한 Compose version. 비교 제외는 [] 또는 승인된 [HOSTNAME]만 가능. credential/profile/Auth/automation 제외 불가 |

사전 입력은 CD enable sentinel 부재, Release State/transition/pending 부재, 기존 current·target bundle·새 Root Source·journal/미완성 journal 부재를 요구한다. 갱신 unit은 승인 Source의 unit bytes 및 loaded FragmentPath/ExecStart/User/Group/oneshot와 일치하고 drop-in/Environment/EnvironmentFiles가 없어야 한다. manager 환경의 State/Lock 경로·shell 주입 override도 거부한다. 모든 권한 변경은 위 allowlist 내부이며 tool/Packet/staging 준비는 별도 승인된 root artifact provisioning이다. 이 조건과 실제 운영 승인·중단/복구 담당자가 없으면 **timer 중지·metadata 변경부터 Apply STOP**이다.

1. 보호된 ancestor와 Packet identity를 확인한 기존 Lock만 `os.open(path, os.O_RDWR | os.O_NOFOLLOW | os.O_CLOEXEC)`로 연다. O_CREAT/O_TRUNC 금지. deploy0600 → renewal0644 순서로 FD fstat와 path lstat의 regular·root UID/GID·nlink1·device:inode·mode를 대조한다. 열기 전/후 drift, unsafe ancestor/ACL, bytes 변화는 STOP이다.
2. 각각 `fcntl.flock(fd, LOCK_EX | LOCK_NB)` 후 identity를 다시 대조한다. renewal 획득 실패 시 이미 연 FD를 역순 close하고 Source/권한 변경 없이 Abort한다. 대기 retry·다른 Lock 경로 사용 금지. 외부 자식에게 FD를 넘기지 않으며 subprocess는 close_fds=True, pass_fds=(), shell=False, capture_output=True를 사용한다.
3. 두 FD는 한 감독 프로세스의 try/finally 안에서 postflight까지 유지한다. 단계 전후 deadline/경로·FD identity를 재검증한다. 명령 timeout은 `min(승인 단계 timeout, 전체 잔여시간 - 승인 복구 여유)`다. timeout/signal/child 잔존 시 별도 **총2초 절대 deadline** 안에서 TERM→KILL, 유한 communicate/wait 및 parent/group/pipe EOF를 재관측한다. 남은 pipe는 닫고 원시 출력을 버린다. escaped child/pipe 또는 회수 불명확은 PROCESS_UNCERTAIN/CHILD_REMAINS이며 Apply에서는 Incident다. group 밖 PID를 추측하여 kill하거나 무제한 pipe drain/wait·재시도를 하지 않는다. FD 비상속/Secret 보호는 유지하며, 종료 불명확성을 Lock 해제나 성공으로 덮지 않고 인계한다.
4. SIGINT/SIGTERM은 다음 단계 금지로 전환하고, 실행 자식의 종료를 확인한 뒤 같은 FD 아래 복구·검증하고 finally에서 renewal→deploy close한다. fsync 실패 등 부분 변경은 원복 성공으로 숨기지 않는다. SIGKILL/crash는 kernel FD 해제만 보장하므로 timer는 계속 중지, phase와 실제 current/Lock/script를 새 preflight에서 재관측한다. 자동 재개·timer 재시작 금지.
5. 정상 해제 조건은 postflight PASS와 복원 가능한 상태의 기록이다. 실패 해제 조건은 후속 mutation 차단·자식 종료·관측된 부분 상태 및 복구/Incident 인계 기록이다. 두 경우 모두 close는 finally로 수행한다. Lock 파일은 unlink/rename/truncate하지 않는다. 실패 상태에서 kernel 해제를 복구 성공으로 해석하지 않는다.

최종 출력은 `PASS:PREFLIGHT`, `PASS:TRANSITION`, `PASS:RECOVERY_RECORD_ONLY` 또는 `FAIL:<고정 reason code>` 한 줄이다. exit0은 해당 명령 검사 성공, exit2는 mutation 전 입력/identity/의미 거부, exit3은 mutation 전 timeout/중단, exit4는 INTERNAL/INCIDENT다. Apply mutation 이후 모든 실패·중단은 exit4 Incident로 수렴하고 timer를 자동 재개하지 않는다. journal은 root0600 atomic/fsync phase 기록뿐이며 raw stdout/stderr·exception·env/hash를 담지 않는다.

Timer 복귀는 Lock 역순 해제 후 `timer-restoring` journal을 먼저 기록하고 start 시도 전에 effect-unknown 경계를 설정한다. start 성공/응답 유실 뒤 unit_state 또는 complete journal 실패도 Incident다. 이때 Packet 만료와 recovery_seconds 중 짧은 **하나의 복구 예산**으로 timer stop을 한 번 시도하고 timer/enabled·service MainPID/ActiveState·queued job을 다시 읽는다. 결과는 고정 분류의 `timer_handoff`로 남긴다. stop 실패이면 active를 그대로, 관측 실패/예산 소진이면 unknown을 기록하며 inactive로 추정하지 않는다. 이미 실행되었거나 queue된 renewal은 강제 종료·cert rollback하지 않는다. Lock 해제 후 current를 되돌리지 않으며 실제 갱신/인증서·서비스 상태의 독립 재관측과 Incident 승인을 요구한다. journal 쓰기까지 실패하면 stdout의 FAIL:INCIDENT와 기존 phase/미완성 `.new`를 인계하고 자동 재개하지 않는다. read-only recover는 `.new` 부재와 fresh Timer 계약까지 확인하며 불확실하면 Incident다. 정상 경로는 active/enabled 복귀와 durable complete를 모두 확인한 경우만 PASS이고, 정상 즉시 renewal 자체를 실패로 보지 않는다. PROCESS_UNCERTAIN/CHILD_REMAINS도 exit4다.

### 8.3 두 FD 아래 Source 준비·검증 → 권한 정렬·script 설치

정확한 origin은 `https://github.com/guseoh/pawcycle-commerce.git`이다. **운영 승인 SHA는 이 Task의 PR 병합 후 실행 직전 main·CI·계약을 다시 확인해 40자리로 고정**한다. Supervisor는 승인된 source_sha=main_sha와 실제 ls-remote main의 완전 일치를 요구한다. 이전 #476/#477 SHA로 자동 진행하지 않는다.

사전 승인 artifact는 `/var/lib/pawcycle-transition/source/<source_sha>`의 독립 root checkout이다. operator가 깨끗한 config/template/hook 환경으로 별도 확보한 입력이며 기존 opc `.git`를 복사/chown한 것이 아니어야 한다. Supervisor는 **두 Lock을 유지한 상태에서만** 이 입력의 모든 ancestor/file/ACL·single-link/root 소유를 확인하고 `/opt/pawcycle/root-source/<source_sha>`로 새 copytree를 생성·fsync한다. 기존 opc checkout을 Git executable/metadata 신뢰 Source로 사용하지 않는다. 운영 Source 설치 전 기존 target 부재를 요구하며 재귀 chown을 하지 않는다. artifact provisioning 자체도 이 저장소 작업의 운영 승인이 아니다.

Source 검증: origin 완전 일치, `rev-parse HEAD`=승인 SHA, 승인 main 조상, `status --porcelain` 공백, `fsck --full`, remote main 완전 일치를 확인한다. Compose/renewal script는 승인 Git object bytes 및 Packet hash와 대조한다. `.git`/Source tree의 root 소유·비쓰기·single-link·ACL/symlink 거부를 확인한다. 기존 Label Compose bytes는 새 Source와 같아야 하며 기존 Nginx는 독립 승인 hash와 비교한다. label path/inode/bytes 보존 불가면 STOP하고 Backend/Frontend recreate Delta로 넘긴다. Source/clone를 명분으로 DB migration이나 서비스 재생성을 하지 않는다.

Source 실패 시 미완성 Source는 격리·보존하고 script/env/current는 건드리지 않는다. Lock 획득부터 Source 완료까지 경로/FD identity와 bytes가 동일해야 한다. 작업 창을 늘리거나 Lock을 놓았다 재획득하여 이어서 적용하지 않는다. 재시작은 새 Packet/preflight로 돌아간다.

Source 검증 성공 후 §8.2에서 이미 연 두 FD를 재검증하고 **renewal FD에만 `os.fchmod(fd, 0o600)`**을 적용한다. `os.fsync(fd)` 후 fstat/path mode0600·identity·bytes 불변을 재대조한다. path chmod와 Lock 삭제·rename·재생성·truncate는 금지한다. fchmod 후 fsync 실패도 0644 자동 복원 없이 부분 실패로 기록하고 timer를 중지한 채 Incident/복구 Gate로 넘긴다.

이는 승인 작업 명세이며 Production 변경 script를 제공하지 않는다. shell에서 flock을 한 번 실행하고 종료시킨 뒤 다음 작업을 하면 안 된다. bundle/postflight까지 §8.2의 두 FD와 deadline을 그대로 유지한다.

기존 executable의 root 보호·single-link·hash를 확인하고 보호 evidence에 bytes/metadata를 백업한다. 새 Source script hash를 승인값과 대조한다. 같은 libexec directory의 신규 임시 regular file에 `install -o 0 -g 0 -m 0755`로 복사하고 재hash·fsync 후 rename하고 directory를 fsync한다. unit을 변경하지 않으므로 installer 전체와 daemon-reload는 사용하지 않는다. 원본/대상/backup은 Packet의 정확한 경로만 사용한다. 설치 실패/권한 불일치는 timer를 중지한 채 STOP한다. backup은 조사 입력이지 무조건 복원해 실행할 안전한 갱신기가 아니다.

**안전한 갱신기 복원 Gate:** c09302a8 backup은 `exec 9>` 때문에 그대로 재활성화 금지다. 동일 inode/bytes를 보존하는 비truncate open·FD identity·경합 거부가 검증된 승인 script를 설치/재설치하고 hash·root 권한·unit 인자·state·Proxy identity를 확인해야 한다. 기존 FD가 잠겨 있어도 옛 script를 경쟁 실행하면 truncate할 수 있으므로 실행하지 않는다. Lock bytes drift가 발생하면 빈 파일이었다는 이유로 통과시키거나 원래 bytes를 덮어쓰지 않고 Incident로 넘긴다. 정상0600을0644로 되돌리지 않는다.

복원 조건을 통과한 뒤에만 두 FD 해제 후 원래 timer 상태로 복귀한다. 인증서 notAfter, 다음 trigger, 작업 최대시간·복구 여유·승인 최소 잔여 유효기간을 Packet에 고정하고, 그 여유를 확보하지 못하거나 안전한 script/timer 복귀가 Abort 기한까지 불가능하면 timer를 무작정 재개하지 않고 인증서 갱신 불능 Incident를 선언한다. 담당자·인계 시각·수동 갱신 별도 승인 필요성을 기록한다. CD OFF/Adopt STOP은 유지한다.

## 9. Bundle 실행 Packet과 중단 복구 (Task 4)

§3 조작도 승인 후 변경이다. 실제 verifier는 `production_env.py`의 `stable_read/parse/projection/verify_effective`와 `Transition.verify_bundle()`이다. Supervisor 내부에서만 호출하여 Secret FD를 subprocess에 상속하지 않는다. 원본/후보 root 보호 경로를 FD로 열고 동일 bytes·identity를 검증한 뒤 실제 Compose JSON·image Config.Env·실행 Backend Config.Env를 메모리 비교한다. 전부 성공한 경우에만 Supervisor가 marker/current를 만든다. CLI PASS를 조작하여 외부에서 marker를 만드는 기능은 없다.

비공개 verifier의 입력은 보호된 원본/후보 env의 읽기 FD, 승인 Source/Compose·실행 binary 버전, 보간 입력 reference, 기대 Backend container/image identity, 비교 제외 key와 deadline이다. Secret·원시 env dump/hash는 argv/envp/stdin으로 전달하지 않는다. Compose에는 경로/image reference와 검증된 비밀이 아닌 automation3-key만 보간 envp로 전달한다. credentials는 보호된 env_file로만 읽는다. 비공개 root 메모리에서 비교하고 subprocess stdout/stderr·예외는 고정 code로 대체한다. core dump/debug logging을 비활성화한 환경이 필요하다.

| 검사 | 정확한 성공 조건 / 실패 계약 |
| --- | --- |
| Parser | 정확히 Catalog9-key의 single-quoted UTF-8 한 줄씩. BOM/NUL/CR·중복/빈 key·빈 값·comment·blank line·unknown/missing·잘못된 quote/escape·multiline 거부. escape는 apostrophe의 `\'`만 허용한다. 단순 unquoted 원본/다른 형식은 ENV_QUOTE로 STOP하며 자동 직렬화하지 않는다. datasource host/명시적 port와 jdbc:mysql URL host/port 일치, SSL mode allowlist/URL sslMode 일치, boolean 및 양의 정수 automation을 검증한다. source/eval 및 DB/Container 작업 entrypoint 실행 금지 |
| consumer 호환성 | Canonical9-key 원본 bytes는 backend.env로 그대로 보존한다. 공통 검증 map에서 Release/Auth6-key와 Catalog9-key를 serialize하여 root0600 개별 projection을 만든다. 없는 값 생성·추론/unknown 키 허용 없음. 새 marker의 Consumer만 해당 projection을 사용한다. 기존 Catalog/Auth validator와 실행 streamer의 enabled=false 및 batch/delay 제외는 유지한다. 기존 legacy bundle 허용 범위는 확대하지 않는다 |
| Compose 해석 | 고정된 실제 Docker Compose binary/version으로 승인 Compose, `--env-file /dev/null`, project, `RELEASE_SHA`, image·Nginx·backend.env 경로 및 automation 보간을 동일하게 고정한다. `config --format json`만 사용하고 stdout/stderr를 비공개 capture한다. 전후 후보 FD/경로 identity·bytes 비교 실패는 ENV_DRIFT, Canonical 해석과 Compose의 차이는 ENV_OVERRIDE로 거부한다 |
| effective env | image Config.Env → env_file → Compose environment 우선순위로 전체 map을 만든다. 실행 Backend Config.Env 중복 key를 거부하고 전체 key 집합/값을 비교한다. 제외는 Packet 승인 HOSTNAME만 가능하며 datasource/profile/Auth/automation은 제외 불가. container ID/image/StartedAt와 원본 안정성이 같아야 성공한다. 이 검사는 Config.Env 동등성까지이며 실행 JVM 내부 override·외부 설정/DB 의미는 별도 운영 검증 Gate다 |
| 결과·예외 | library 성공은 예외 없는 반환이며 별도 stdout/stderr가 없다. ENV_KEYS/ENV_QUOTE/ENV_ESCAPE/ENV_DATABASE/ENV_AUTOMATION/ENV_DRIFT/ENV_OVERRIDE/ENV_EFFECTIVE/ENV_EXCLUSION/COMPOSE_VERSION 등 고정 Refused만 반환한다. unexpected 예외도 CLI에서는 INTERNAL 또는 mutation 이후 INCIDENT로만 출력한다. invalid JSON/encoding, permission denied, subprocess nonzero/timeout, 누락/불일치는 전부 실패이며 raw exception/환경 dump/hash 출력 금지 |
| marker | verifier 성공, Consumer projection bytes 일치, 원본/사본 bytes 일치, root metadata/identity, 두 Lock/작업 창 유효성, 실제 Backend 불변 이후에만 생성한다. PASS 문자열/fixture callback은 운영 코드의 입력이 아니다. `.complete`와 current 생성 전 실패는 둘 다 생성하지 않는다 |

이 표는 구현된 검사 범위이며 실환경 의미 검증의 성공 주장이 아니다. 실제 Env가 이 형식/비교 범위와 다르면 APPLY STOP이다. Packet에 다음 입출력·성공/실패 증거를 첨부한다.

1. 원본을 O_RDONLY|O_NOFOLLOW로 열어 fstat/lstat 대조. writer 중단, regular/nlink=1/0600/승인 UID:GID 확인. 복사 전후 device:inode/size/mtime_ns/ctime_ns 및 재독 bytes 일치. 변경·short read·I/O error는 STOP. 원본 chmod/chown 금지.
2. runtime 바로 아래 unique bundle 0700 생성, env를 O_EXCL|O_NOFOLLOW/0600으로 생성. 원본/사본 bytes를 메모리 비교하고 PASS만 기록. Secret·민감 hash·parse exception의 값을 출력하지 않는다.
3. Canonical9-key와 Release6/Catalog9/Auth6 projection 의미를 독립 검증한다. Auth의 개행·인용·escape 제약도 대조한다. 원본 key 추가/수정으로 통과시키지 않고 STOP한다.
4. 승인 Compose를 실제 version/`--env-file /dev/null`/project/보간 입력으로 `config --format json` 파싱한다. stdout/stderr는 비공개 프로세스에서 capture하고 로그/화면에 출력하지 않는다. Backend Config.Env도 capture하여 중복key·누락·추가 override를 포함한 datasource/profile/automation/Auth 및 동작에 영향을 주는 모든 key의 effective 값을 비교한다. image ENV/Compose environment override/env_file 우선순위를 반영하고 무관 key 제외 근거도 승인한다. source/eval 금지. Compose parser와 Release의 단순 quote 처리에 의미 차이가 있으면 STOP한다.
5. 비교 종료까지 Backend container ID/image/StartedAt 동일성을 확인한다. PASS를 bundle ID·Source SHA·validator 버전·승인 reference에 결합한다. env fsync 후 모든 검증 성공 시에만 `.complete`를 O_EXCL/0600으로 생성·fsync하고 bundle directory를 fsync한다.
6. 같은 filesystem의 runtime 안에 unique temporary symlink를 생성한다. current의 원래 상태가 Packet과 같은지 재확인하고 rename/replace로 전환한 뒤 runtime directory를 fsync한다. 선행 writer 가능성이 있으면 TOCTOU를 허용하지 않고 STOP한다. bundle/current를 재resolve하고 metadata/marker를 검증한다.

중단 복구: marker 전에는 미완성 bundle을 격리한 채 current에 연결하지 않는다. marker 후/rename 전에는 완료 bundle을 보존하고 current는 원래 상태다. rename 후/fsync 전 crash라면 current를 실측하여 완료 bundle만 인정하고 추정 재개하지 않는다. 전환 후 복구는 검증된 이전 target으로 같은 atomic 절차를 사용한다. 이전 current가 없으면 이번 symlink임을 확인한 연결만 unlink하고 runtime을 fsync한다(Lock에는 적용 금지). bundle/원본을 삭제하지 않는다. ENOSPC·fsync 실패·예상 밖 current·프로세스 종료에 따른 Lock 해제는 새 작업 창에서 preflight부터 재시작한다.

## 10. 종료 판정·Abort·다음 승인 (Task 5)

각 단계 기대 결과는 Packet의 desired metadata/identity 완전 일치이며 미확인 값을 성공 처리하지 않는다. postflight는 같은 비공개 비교 방법으로 다음을 판정한다.

- Compose Label 문자열/bytes/inode, Backend/Frontend/Proxy/DB/sibling container ID/image/StartedAt/RestartCount/health, network/bind/named volume identity를 before와 비교한다. 기존 HTTPS 직접2xx/TLS, 인증서 serial/expiry, state/adoption bytes 불변을 확인한다. DB query나 인증서 갱신을 이 확인에 추가하지 않는다.
- deploy.lock/renewal.lock device:inode/bytes 불변, owner/mode, Source/runtime/current/bundle 보호를 재확인한다. Lock 경합은 격리 fixture에서 실패를 기대하며 실제 Production renewal/Release를 경합 시험으로 실행하지 않는다.
- 두 Lock 해제 후 승인된 원래 enabled/active 상태로만 timer를 복원한다. 원래 enabled/active이면 `systemctl start pawcycle-production-https-renew.timer` 후 §7 show로 다음 trigger와 service 상태를 확인한다. 재개 직후 renewal은 인증서/reload를 일으킬 수 있으므로 복귀 동작도 운영 승인에 명시한다. 신규 enable이나 renew --dry-run을 암묵 실행하지 않는다.

Abort는 다음 단계를 중단하고 CD OFF/Adopt STOP을 유지한다. metadata 복원은 Packet의 개별 경로/원래 숫자 UID:GID/mode/ACL로 자식부터 부모 순서로 수행한다. state가 root여도 부모를 opc로 돌리면 rename/교체가 가능해 Root State 신뢰 경계가 사라진다. 이 경우 Root CLI/Host Pull/catalog/auth 재개를 금지하고 별도 신뢰 재확립 승인까지 CD OFF를 유지한다. root 보호 유지를 우선하고 opc writer 재개 요청만으로 owner를 자동 복원하지 않는다. 복원 불가/Health 저하는 blocker이며 DB/volume/인증서 변경으로 확대하지 않는다.

G0-T2A 종료는 Source/Label/Lock/bundle 증거, timer 복귀, 기존 서비스/sibling 불변, blocker 제로를 독립 확인한 뒤 판정한다. OPS-G0-T2A-002는 도구와 projection 계약을 저장소에서 구현하지만 Production에 접속하지 않는다. 이전 읽기 전용 관측은 현재 Packet을 대신하지 않는다. 실제 Canonical Env·effective env·Root artifact·작업 창/승인·cert 여유·quiescence가 모두 충족되기 전 운영 readiness는 미승인이다. Host Pull 설치에는 이제 `production_env.py`도 controller SHA allowlist에 포함해야 하며 기존 정책으로 암묵 활성화하지 않는다.

다음 Nginx Bootstrap은 OPS-OCI-CD-004의 독립 승인이 필요하다. 승인 SHA/설정hash, Proxy-only 대상 identity와 중단시간, nginx 검증, image/mount/network/volume 유지, 옛 Proxy 복귀 입력, 직접HTTPS/TLS 및 Backend/Frontend/sibling 불변 검증을 준비한다. Label 보호 불가라면 먼저 Backend/Frontend recreate Delta 승인이 필요하다. Bootstrap·Release Adopt·Host Pull 활성화는 각각 별도 Gate이며 이 Task 종료나 CI Green에서 자동 진행하지 않는다.

## 11. 최소 disposable-root 재현 증거

운영자가 **Production이 아닌 disposable Linux root 환경**의 검토한 checkout에서 실행한다. 호출은 아래 하나이며 임의 경로·SSH·Docker·apply 인자를 받지 않는다. 모든 쓰기는 Python `TemporaryDirectory(prefix="g0-t2a-fixture-")`가 새로 만든 경로 내부다. 전체 성공 시 exit0/unittest OK, 테스트 실패 시 nonzero, 비root는 STOP이다. Production에서 실행하지 않는다.

```bash
python3 infra/production/test-trust-boundary-transition.py -v
```

기존15개 rehearsal에 실제 Supervisor/parser/verifier의 Source/Env/Docker 합성 통합 검증을 연결한다. deploy→renewal 경합·FD 해제, owner/ACL·inode drift·symlink/hardlink, CLOEXEC, fchmod0600 inode/bytes 보존, 부분 fsync 실패, subprocess timeout·SIGTERM·잔존 child 회수, quote/escape/중복/누락/unknown·DB 의미·override·effective env 불일치·Secret redaction, marker/current 중단·Incident 재시도 거부를 검증한다. 실제 Docker Compose CLI의 `config`는 daemon/Container 생성 없이 합성 Env에서 실행한다. 구버전 exec9>의 truncate 위험도 합성 Lock에서만 재현한다. 신규 fixture는 기존 Production Contract CI contracts lane에 연결한다.

범위 한계: 합성 Docker metadata와 실제 Compose config는 실행 Production Backend/JVM·외부 설정·DB 의미나 실제 갱신을 입증하지 않는다. fsync 오류 주입·프로세스 중단도 전원 단절 durability 증거가 아니다. `recover`는 read-only journal 판정으로 crash 이후 mutation을 자동 복구하지 않는다. 담당자가 실제 phase·Lock/script/current/timer를 재관측하고 별도 Incident/복구 승인을 받아야 하며, root 경계를 약화시키거나 구버전script를 재활성화하지 않는다. 기존 Release47/CD22/Renewal/Catalog/Auth/Production Contract 및 PR stdin validation 결과와 최신 HEAD CI는 PR에 기록한다.

## 12. OPS-G0-T2A-003 — Unit 관측 교정과 운영 인수인계

`SERVICE_ENV_OVERRIDE: FAIL`만으로 실제 Override가 존재한다고 판단하지 않는다. [systemctl 공식 문서](https://www.freedesktop.org/software/systemd/man/255/systemctl.html)는 기본 출력의 빈 속성 생략과 `--all`을 설명하지만, [systemd 255 EnvironmentFiles printer](https://github.com/systemd/systemd/blob/v255/src/systemctl/systemctl-show.c#L1168-L1184)는 배열 항목마다 출력하므로 빈 배열은 `--all`에서도 행이 없다. 로컬 WSL systemd 255.4에서 기존 systemd-journald.service를 읽기만 한 재현 결과는 기본/`--all` 모두 `Environment=` 존재·`EnvironmentFiles` 행 부재, D-Bus의 두 속성은 각각 `as 0` / `a(sb) 0`이었다. 이는 Repository의 오거부 재현이며 OCI의 실제 Unit 설정에 대한 증거가 아니다.

Supervisor는 일반 Unit 속성을 `show --all`로 읽고 필수 속성 누락·중복·잘못된 행을 거부한다. Service의 Environment/EnvironmentFiles는 systemd에 포함된 `/usr/bin/busctl --system get-property`로 고정 Service object/interface에서 함께 읽어 정확한 두 typed empty array만 허용한다. `busctl` binary와 resolve된 경로에도 기존 root 보호 검사를 적용한다. 별도 package 설치나 D-Bus mutation은 수행하지 않는다. binary 부재·권한/명령 실패·timeout·누락·타입/출력 오류·실제 항목 존재는 모두 STOP이다. EnvironmentFile의 ignore_errors=true도 허용하지 않는다. 원시 출력은 비공개 메모리에만 유지하고 Packet/terminal/PR에는 값·파일 경로·Secret을 출력하지 않는다.

읽기 전용 진단은 승인된 운영자가 설치된 **동일 SHA의** `Transition.unit_contract()`를 private capture로 실행하거나 준비된 Packet으로 §8.2 preflight를 실행하고 고정 PASS/FAIL만 남긴다. 초안의 `quiescence_confirmed=false`를 검사 통과 목적으로 true로 바꾸지 않는다. 초안은 승인 Packet이 아니며 `validate_packet()`에서 QUIESCENCE 거부되는 것이 정상이다. Unit 단독 검사 PASS도 전체 preflight 또는 Apply 승인으로 해석하지 않는다. 기존 외부 Packet 생성 도구의 bytes·설치 버전은 이 저장소에서 관측하지 않았으므로 별도 검토 대상이다.

### Artifact와 Packet 재준비

- 이번 PR HEAD는 검토·CI 대상이다. **운영 Source SHA는 아직 확정되지 않았다.** 병합 승인 후 실행 직전 `refs/heads/main`의 정확한40자리 SHA와 그 SHA의 Repository Validation 성공을 재확인해 `source_sha=main_sha`로 고정한다. 시작 baseline `2e6432895524159f9a6eaac0ca7e75e9d9153941`이나 미병합 PR HEAD를 새 운영 Source로 사용하지 않는다. main이 다시 이동하면 실행 입력·CI·Packet을 재확인한다. 기존 실행 image의 `release_sha`는 이 Source 교정 때문에 바꾸지 않는다.
- 별도 Root Artifact provisioning 승인을 받아 `/var/lib/pawcycle-transition/source/<승인 SHA>`의 독립 checkout을 새로 준비한다. 정확한 origin/HEAD/main·clean tree/fsck·모든 ancestor/ACL/owner/mode/single-link 및 공개 bytes를 확인한다. tool directory의 `trust_transition.py`와 `production_env.py`도 그 SHA의 bytes로 함께 검증하고 공개 hash를 다시 고정한다. busctl의 존재·root 보호·읽기 권한을 확인한다. 자동 설치/교체, opc checkout 승격, unit 변경/daemon-reload는 하지 않는다.
- 기존 SHA artifact, 실패한 초안, 설치 도구의 이전 bytes/metadata는 private evidence로 보존한다. 기존 artifact를 새 SHA 이름으로 덮어쓰거나 이름만 변경하지 않는다. 미완성 Source/bundle/current/journal/`.new`/`renewal.old`/임시 script가 있으면 실제 phase와 identity를 관측해 STOP하고 별도 Incident 판단으로 넘긴다. 재시도를 위해 자동 삭제하지 않는다. 정상0600 renewal lock을0644로 되돌리거나 inode를 교체하지 않는다.
- Source/tool bytes·설치 script identity·공개 hash가 바뀌거나 작업 창/인증서/파일 identity/snapshot/timer/승인 범위가 달라지면 초안을 새로 작성한다. `snapshot` 전체, allowlist의9항목 identity, env/deploy/renewal/설치 script identity, 공개 cert notAfter, 최대1시간 창·복구 여유·종료 후 최소86400초 인증서 여유를 새로 관측한다. env값/hash는 기록하지 않는다. 이전 PASS를 새 Artifact/Packet에 이월하지 않는다. Quiescence의 writer/manual/sudo/자동화 재진입 배제와 담당자·Abort 시각을 실제 확인하고 **사용자의 별도 운영 승인 후에만** 승인 reference 및 true를 활성 Packet에 반영한다. 이번 Repository 작업은 approval.json 생성·활성화를 수행하지 않는다.

### 남은 운영 Gate와 STOP/Recovery

순서는 새 Artifact/초안 관측 → 읽기 전용 준비 확인 → **별도 운영 승인** → 활성 Packet의 전체 Preflight 재확인 → Apply → 두 FD 아래 Postflight → 역순 Lock 해제 → 안전한 갱신기 Gate와 Timer 복귀·durable complete 확인이다. Preflight PASS 이후에도 Apply는 같은 입력을 재검증한다. Unit 관측은 원자적인 운영 차단 장치가 아니며 Quiescence가 필수다. 중간 Override는 Postflight에서 Incident로 거부하며 임의 privileged 재진입을 완전히 배제했다는 주장은 하지 않는다.

관측 불완전·실제 Override·경합·identity/runtime/source drift·기한/인증서 여유 부족이면 다음 단계 금지다. mutation 전에는 기존 서비스/Timer를 유지한다. mutation 이후에는 Incident로 인계하며 current disconnect나 FD 해제만으로 복구 성공을 선언하지 않는다. Timer start 응답 유실·재관측 실패는 §8.2의 유한 stop/관측 인계이며 이미 시작된 renewal을 강제 종료하지 않는다. read-only recover는 자동 복구가 아니다. DB migration/restore·인증서/volume 복구로 확대하지 않는다.

G0-T2A 완료 뒤 **별도 Nginx Bootstrap 승인·검증·Proxy-only Recovery**, 그 뒤 **별도 Release Adopt Gate**를 따른다. Source 설치나 bundle/current 전환은 실행 컨테이너 env 변경·DB 호환성·rollback 가능성을 입증하지 않는다. OCI Unit/ACL/Source/snapshot/인증서/Quiescence, 실제 Compose/effective env·JVM 설정, Nginx fingerprint, DB epoch/Flyway·baseline/capacity와 복구 입력은 실행 시점 미확인이면 계속 STOP이다. Production CD 활성화/실제 배포는 별도 승인 전 OFF를 유지한다. 이 작업은 Production Verified가 아니다.
