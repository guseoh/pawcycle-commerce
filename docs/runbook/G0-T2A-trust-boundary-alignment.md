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
- catalog/auth 도구는 같은 state/deploy.lock을 사용하고 `--runtime-dir`의 bundle을 읽는다. catalog는 datasource host/port/SSL까지 요구하는 9-key 계약, auth 도구는 6-key와 다른 직렬화 제한이 있다. 하나의 env를 두 도구가 모두 수용한다고 가정하지 않는다. 실제 사용할 도구별 env validation을 별도 통과시키며 실패 시 별도 최소 계약 정렬 Delta 전 STOP한다.
- 진단의 `PAWCYCLE_STATE_DIR`/`--state-dir`, 운영자 cron·sudo·unit·작업 디렉터리·Source 업데이트 주체와 관측성 identity 경로 의존성도 목록화한다. sibling observability/metrics-proxy 컨테이너와 인증서 volumes는 전환 대상이 아니다.

## 3. Runtime Bundle 설치·검증 계약

현재 `runtime/backend.env`를 source/echo하거나 환경변수 내용·raw inspect·secret hash를 Chat/PR/로그에 출력하지 않는다. 값과 byte hash는 비공개 프로세스 메모리 안에서만 대조하고 evidence에는 PASS/FAIL만 남긴다.

1. 승인된 작업 창에 env 작성자를 중지/직렬화하고 원본 regular/no-symlink/single-link·opc 0600, inode·size·mtime를 확인한다. 부모가 opc이면 읽는 동안에도 교체될 수 있으므로 열린 FD의 fstat와 재검증 및 쓰기 중단 근거가 필요하다. 변경이 감지되면 Abort한다.
2. root 보호 runtime 안에 새 `.bundle.<unique>` directory 0700을 만들고 원본 bytes를 새 regular `backend.env` root 0600으로 복사한다. 원본을 이동·chown·hardlink하지 않는다. 원본과 사본의 byte hash·길이를 비공개 비교하고 원본 안정성도 재확인한다. 파싱/복사 실패는 values를 출력하지 않는다.
3. Release의 필수 datasource URL/username/password와 automation enabled(boolean)/batch/fixed-delay(양의 정수), 중복 key·누락·빈 값·인용 및 실제 Compose env_file 파싱을 검증한다. datasource host/port/SSL은 사용하는 도구 계약과 대조한다. 기존 실행 컨테이너의 effective env와 semantic 동등성도 비공개 비교한다. shell eval/source는 금지다. 단순 hash 일치는 실제 주입값 검증을 대신하지 않는다.
4. 검증 결과·원본/사본 bytes 일치 판정·검증기 버전·승인 reference를 root 보호 evidence에 결합한다. **모든 검증 성공 후에만** `.complete` root 0600 regular single-link를 생성한다. CLI는 marker 내용의 의미를 입증하지 않고 파일 보호만 검사하므로 marker는 승인된 검증 절차의 완료 증거다. 빈 marker를 미리 touch하여 통과시키지 않는다.
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

운영 감독 호출의 입력은 비공개 승인 Packet의 절대 경로/승인 식별자, UTC 작업 종료 시각, 단조시계 기준 전체 잔여시간, 단계별 timeout, 고정된 Source origin/SHA, 기존 Lock의 device:inode·UID:GID·mode·nlink이다. stdin에는 Secret을 받지 않고 env를 argv/envp로 전달하지 않는다. 검토된 root Python과 표준 라이브러리만 사용하며 shell=True/eval/source, opc executable과 config 로딩을 금지한다. 현재 이 호출을 제공하는 Production executable은 **없다**. 아래 계약을 구현·검토한 도구의 정확한 절대 경로/버전/hash와 격리 증거가 Packet에 없으면 **§8.1의 timer 중지·metadata 변경부터 Apply STOP**이다. fixture 실행을 그 도구로 대체하지 않는다.

1. 보호된 ancestor와 Packet identity를 확인한 기존 Lock만 `os.open(path, os.O_RDWR | os.O_NOFOLLOW | os.O_CLOEXEC)`로 연다. O_CREAT/O_TRUNC 금지. deploy0600 → renewal0644 순서로 FD fstat와 path lstat의 regular·root UID/GID·nlink1·device:inode·mode를 대조한다. 열기 전/후 drift, unsafe ancestor/ACL, bytes 변화는 STOP이다.
2. 각각 `fcntl.flock(fd, LOCK_EX | LOCK_NB)` 후 identity를 다시 대조한다. renewal 획득 실패 시 이미 연 FD를 역순 close하고 Source/권한 변경 없이 Abort한다. 대기 retry·다른 Lock 경로 사용 금지. 외부 자식에게 FD를 넘기지 않으며 subprocess는 close_fds=True, pass_fds=(), shell=False, capture_output=True를 사용한다.
3. 두 FD는 한 감독 프로세스의 try/finally 안에서 postflight까지 유지한다. 단계 전후 deadline/경로·FD identity를 재검증한다. Source subprocess의 timeout은 `min(승인 단계 timeout, 전체 잔여시간 - 승인 복구 여유)`이고 양수가 아니면 실행하지 않는다. 네트워크 지연·timeout·작업 창 만료 시 신규 단계/재시도 금지. timeout 자식 process group을 종료·회수한 뒤 종료를 확인하고 복구한다. 자식 생존/종료 불명확이면 lock만 해제하고 계속하지 않고 Incident로 인계한다.
4. SIGINT/SIGTERM은 다음 단계 금지로 전환하고, 실행 자식의 종료를 확인한 뒤 같은 FD 아래 복구·검증하고 finally에서 renewal→deploy close한다. fsync 실패 등 부분 변경은 원복 성공으로 숨기지 않는다. SIGKILL/crash는 kernel FD 해제만 보장하므로 timer는 계속 중지, phase와 실제 current/Lock/script를 새 preflight에서 재관측한다. 자동 재개·timer 재시작 금지.
5. 정상 해제 조건은 postflight PASS와 복원 가능한 상태의 기록이다. 실패 해제 조건은 후속 mutation 차단·자식 종료·관측된 부분 상태 및 복구/Incident 인계 기록이다. 두 경우 모두 close는 finally로 수행한다. Lock 파일은 unlink/rename/truncate하지 않는다. 실패 상태에서 kernel 해제를 복구 성공으로 해석하지 않는다.

단계 결과는 `PASS:<고정 단계명>` 또는 `FAIL:<고정 reason code>`만 반환한다. exit0은 정의된 모든 검증 성공, exit2는 입력/identity/의미/경합 거부, exit3은 timeout/중단, exit4는 부분 변경·I/O·복구 불명확이다. raw stdout/stderr·exception·env/hash 출력은 금지한다. 이 exit 계약은 향후 감독 도구의 요구 계약이며 구현 완료 주장이 아니다.

### 8.3 두 FD 아래 Source 준비·검증 → 권한 정렬·script 설치

정확한 origin은 `https://github.com/guseoh/pawcycle-commerce.git`이다. 이번 설계 기준 main은 `a3a8a6f5726b79993f19ffea3ca21e394887c440`(#476), 직전 #475는 `fe75e966`이다. **운영 승인 SHA는 실행 직전 main·CI·계약을 다시 확인해 40자리로 고정**한다. main이 바뀌면 이 문서의 SHA로 자동 진행하지 않는다.

승인 후 **두 Lock을 유지한 감독 프로세스의 bounded subprocess로만**, root-owned 새 빈 directory를 생성하여 root의 검토된 Git binary와 깨끗한 HOME/config/template 환경에서 clone한다. 기존 opc checkout, Git config/include, hooks, alternate object store, worktree, executable을 재사용하지 않는다. 아래는 감독 내부 argv의 참고 예시이며 shell에서 따로 실행하지 않는다. `CLEAN_HOME`, `ROOT_SOURCE`, `APPROVED_SHA`는 보호된 정확한 승인 경로/값이어야 한다.

```bash
env -i PATH=/usr/bin:/bin HOME="$CLEAN_HOME" GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null git -c core.hooksPath=/dev/null clone --no-checkout --template= https://github.com/guseoh/pawcycle-commerce.git "$ROOT_SOURCE"
env -i PATH=/usr/bin:/bin HOME="$CLEAN_HOME" GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null git -C "$ROOT_SOURCE" -c core.hooksPath=/dev/null checkout --detach "$APPROVED_SHA"
```

clone 인증정보는 출력하지 않는다. Source는 새로 생성하고 기존 checkout은 chown하지 않는다.

읽기 전용 검증: origin 완전 일치, `rev-parse HEAD`와 승인 SHA 일치, `merge-base --is-ancestor "$APPROVED_SHA" refs/remotes/origin/main` 성공, `status --porcelain` 공백, `fsck --full` 성공을 확인한다. 독립 Git object의 Compose/Nginx/renewal script/migration tree와 작업 tree bytes를 비교하고 symlink/submodule/alternate를 거부한다. Source·`.git`·ancestor의 UID/GID와 비쓰기 경계를 확인한다. 기존 Label 파일은 승인 Compose bytes와 일치해야 한다. 내용 수정이 필요하거나 Label inode/문자열 보존이 불가능하면 STOP하고 Backend/Frontend recreate 별도 승인 Delta로 넘긴다.

Source 실패 시 미완성 Source는 격리·보존하고 script/env/current는 건드리지 않는다. Lock 획득부터 Source 완료까지 경로/FD identity와 bytes가 동일해야 한다. 작업 창을 늘리거나 Lock을 놓았다 재획득하여 이어서 적용하지 않는다. 재시작은 새 Packet/preflight로 돌아간다.

Source 검증 성공 후 §8.2에서 이미 연 두 FD를 재검증하고 **renewal FD에만 `os.fchmod(fd, 0o600)`**을 적용한다. `os.fsync(fd)` 후 fstat/path mode0600·identity·bytes 불변을 재대조한다. path chmod와 Lock 삭제·rename·재생성·truncate는 금지한다. fchmod 후 fsync 실패도 0644 자동 복원 없이 부분 실패로 기록하고 timer를 중지한 채 Incident/복구 Gate로 넘긴다.

이는 승인 작업 명세이며 Production 변경 script를 제공하지 않는다. shell에서 flock을 한 번 실행하고 종료시킨 뒤 다음 작업을 하면 안 된다. bundle/postflight까지 §8.2의 두 FD와 deadline을 그대로 유지한다.

기존 executable의 root 보호·single-link·hash를 확인하고 보호 evidence에 bytes/metadata를 백업한다. 새 Source script hash를 승인값과 대조한다. 같은 libexec directory의 신규 임시 regular file에 `install -o 0 -g 0 -m 0755`로 복사하고 재hash·fsync 후 rename하고 directory를 fsync한다. unit을 변경하지 않으므로 installer 전체와 daemon-reload는 사용하지 않는다. 원본/대상/backup은 Packet의 정확한 경로만 사용한다. 설치 실패/권한 불일치는 timer를 중지한 채 STOP한다. backup은 조사 입력이지 무조건 복원해 실행할 안전한 갱신기가 아니다.

**안전한 갱신기 복원 Gate:** c09302a8 backup은 `exec 9>` 때문에 그대로 재활성화 금지다. 동일 inode/bytes를 보존하는 비truncate open·FD identity·경합 거부가 검증된 승인 script를 설치/재설치하고 hash·root 권한·unit 인자·state·Proxy identity를 확인해야 한다. 기존 FD가 잠겨 있어도 옛 script를 경쟁 실행하면 truncate할 수 있으므로 실행하지 않는다. Lock bytes drift가 발생하면 빈 파일이었다는 이유로 통과시키거나 원래 bytes를 덮어쓰지 않고 Incident로 넘긴다. 정상0600을0644로 되돌리지 않는다.

복원 조건을 통과한 뒤에만 두 FD 해제 후 원래 timer 상태로 복귀한다. 인증서 notAfter, 다음 trigger, 작업 최대시간·복구 여유·승인 최소 잔여 유효기간을 Packet에 고정하고, 그 여유를 확보하지 못하거나 안전한 script/timer 복귀가 Abort 기한까지 불가능하면 timer를 무작정 재개하지 않고 인증서 갱신 불능 Incident를 선언한다. 담당자·인계 시각·수동 갱신 별도 승인 필요성을 기록한다. CD OFF/Adopt STOP은 유지한다.

## 9. Bundle 실행 Packet과 중단 복구 (Task 4)

§3 조작도 승인 후 변경이다. 실제 parser/Compose/effective-env 검사를 수행할 비공개 read-only verifier는 이번 PR에서 구현하지 않는다. 따라서 아래 계약과 fixture가 있어도 **bundle Apply STOP**이다. 단순 copy, synthetic fixture PASS, Release 실행을 검증으로 대체하지 않는다. verifier의 검토된 executable/버전/hash·실패 경로 증거와 아래 항목의 PASS가 Packet에 결합되기 전에는 `.complete` 생성·current 연결을 금지한다.

비공개 verifier의 호출 입력은 보호된 원본/후보 env의 읽기 FD, 승인 Source/Compose·실행 binary 버전, 보간 입력 reference, 기대 Backend container/image identity, 필수 consumer 집합, 비교 제외 key의 승인 목록과 전체 deadline이다. 원시 env/값/hash는 argv/envp/stdin으로 전달하지 않는다. 비공개 root 프로세스 메모리에서만 처리하고 callback이나 subprocess 예외도 값이 아닌 고정 reason code로 변환한다. subprocess stdout/stderr는 capture한 뒤 파싱하고 버리며 core dump·debug logging을 비활성화한 검토된 환경이 필요하다.

| 검사 | 정확한 성공 조건 / 실패 계약 |
| --- | --- |
| Parser | strict UTF-8, BOM/NUL/CR·중복/빈 key·빈 값·잘못된 quote/escape·지원하지 않는 개행/보간 거부. shell dotenv를 재구현하거나 source/eval하지 않는다. 승인 SHA의 Release parser, Catalog `decode_backend_env_value`/9-key validator, Auth 6-key validator 각각의 해석과 소비값을 독립 비교한다. 도구 실행 entrypoint는 DB/Container 작업을 하므로 검증 목적으로 실행하지 않는다. parser-only 추출이 없으면 STOP |
| consumer 호환성 | Release의 필수6-key는 Catalog unknown-key 금지9-key·Auth unknown-key 금지6-key와 같지 않다. Catalog/Auth 동시 요구 시 현 계약은 하나의 env로 모두 만족할 수 없으므로 FAIL:CONSUMER_CONTRACT. key를 더하거나 제거해 통과시키지 않는다. consumer 제외는 명시적 승인과 별도 계약 정렬 Delta 없이 수행하지 않는다 |
| Compose 해석 | 고정된 실제 Docker Compose binary/version으로 승인 Compose, `--env-file /dev/null`, project, `RELEASE_SHA`, image·Nginx·backend.env 경로 및 automation 보간을 동일하게 고정한다. `config --format json`만 사용하고 stdout/stderr를 비공개 capture한다. Compose가 실제 후보 FD의 bytes를 읽었는지 전후 identity/bytes 비교가 필요하며 경로 교체 가능성이 있으면 FAIL:SOURCE_DRIFT. Compose parser와 Release/Catalog/Auth의 의미 차이는 FAIL:PARSER_MISMATCH |
| effective env | 후보 image Config.Env → env_file → Compose environment 우선순위로 예상 전체 map을 만든다. 실행 Backend Config.Env의 중복 key를 거부하고 전체 key 집합/값을 비교한다. datasource URL/host/port/SSL/credential, profile, automation, Auth/session 관련 key는 제외 불가. 나머지 image 기본 key도 기본적으로 비교하며 제외하려면 key별 비의미성 승인 근거가 필요하다. 이전/이후 container ID/image/StartedAt·원본 env 안정성이 같아야 PASS. 실행 중 JVM 내부 override·외부 설정의 의미까지 증명할 수 없으면 FAIL:EFFECTIVE_ENV_UNPROVEN |
| 결과·예외 | stdout은 `PASS:BUNDLE_CHECK` 한 줄 또는 `FAIL:<고정 코드>` 한 줄만 허용. exit0은 모든 필수 검사 성공, exit2는 계약/파싱/의미/identity 실패, exit3은 timeout/중단, exit4는 I/O·예외/도구 실패. permission denied, invalid JSON/encoding, subprocess nonzero, 결과 누락, 지원하지 않는 Compose 버전/문법, 예외는 모두 FAIL이며 raw exception/환경 dump/hash 출력 금지 |
| marker | exit0과 정확한 PASS 한 줄, 모든 필수 consumer 결과, 원본/사본 bytes 일치, root metadata/identity, 두 Lock/작업 창 유효성, 실제 Backend 불변이 한 Packet에 결합되어야 생성 가능. PASS 문자열만 주입하거나 fixture callback 성공으로 marker를 생성하지 않는다 |

이 표는 안전한 verifier의 요구 계약이며 구현 완료/실환경 검증 주장이 아니다. Packet에 다음 입출력·성공/실패 증거를 첨부한다.

1. 원본을 O_RDONLY|O_NOFOLLOW로 열어 fstat/lstat 대조. writer 중단, regular/nlink=1/0600/승인 UID:GID 확인. 복사 전후 device:inode/size/mtime_ns/ctime_ns 및 재독 bytes 일치. 변경·short read·I/O error는 STOP. 원본 chmod/chown 금지.
2. runtime 바로 아래 unique bundle 0700 생성, env를 O_EXCL|O_NOFOLLOW/0600으로 생성. 원본/사본 bytes를 메모리 비교하고 PASS만 기록. Secret·민감 hash·parse exception의 값을 출력하지 않는다.
3. Release 6-key, Catalog 9-key, Auth 6-key와 각각의 decoder를 독립 검증한다. Catalog는 unknown key·comment·blank를 허용하지 않으므로 Release 수용만으로 통과시키지 않는다. Auth의 개행·인용·escape 제약도 대조한다. key 추가/수정으로 통과시키지 않고 계약 Delta에서 STOP한다.
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

G0-T2A 종료는 Source/Label/Lock/bundle 증거, timer 복귀, 기존 서비스/sibling 불변, blocker 제로를 독립 확인한 뒤 판정한다. 이전 별도 승인 읽기 전용 관측은 현재 Apply의 실측 Packet을 대신하지 않는다. 이번 Correction에서는 Production에 접속하지 않으며 운영 감독 도구·비공개 env verifier·소비자 계약 정렬·실환경 검증이 미완료이므로 운영 readiness는 미승인이다.

다음 Nginx Bootstrap은 OPS-OCI-CD-004의 독립 승인이 필요하다. 승인 SHA/설정hash, Proxy-only 대상 identity와 중단시간, nginx 검증, image/mount/network/volume 유지, 옛 Proxy 복귀 입력, 직접HTTPS/TLS 및 Backend/Frontend/sibling 불변 검증을 준비한다. Label 보호 불가라면 먼저 Backend/Frontend recreate Delta 승인이 필요하다. Bootstrap·Release Adopt·Host Pull 활성화는 각각 별도 Gate이며 이 Task 종료나 CI Green에서 자동 진행하지 않는다.

## 11. 최소 disposable-root 재현 증거

운영자가 **Production이 아닌 disposable Linux root 환경**의 검토한 checkout에서 실행한다. 호출은 아래 하나이며 임의 경로·SSH·Docker·apply 인자를 받지 않는다. 모든 쓰기는 Python `TemporaryDirectory(prefix="g0-t2a-fixture-")`가 새로 만든 경로 내부다. 전체 성공 시 exit0/unittest OK, 테스트 실패 시 nonzero, 비root는 STOP이다. Production에서 실행하지 않는다.

```bash
python3 infra/production/test-trust-boundary-transition.py -v
```

fixture는 deploy→renewal 경합, 두번째 Lock 실패 시 첫 FD 해제, UID/GID·inode drift·symlink/hardlink 거부, FD CLOEXEC, fchmod0600의 inode/bytes 보존, fsync 부분 실패, Source subprocess timeout, 감독 프로세스 종료 시 kernel 해제를 검증한다. 구버전 `exec 9>`를 합성 Lock에만 실행하여 flock 거부 전에 bytes가 사라짐을 재현한다. 합성 env 검사 callback FAIL/예외는 marker를 만들지 않으며, marker 전/후·rename 후 fsync 전 중단과 최초 current 부재 복구를 검증한다.

범위 한계: callback은 실제 parser/Compose/Backend 비교기가 아니다. mock fsync 오류·프로세스 종료는 전원 단절 durability 증거가 아니다. 기존 root ancestor/ACL 보호, 자식 process group 회수, 실제 구버전 전체 script/인증서 갱신 복원, 모든 단계에서의 운영 signal handling도 이 fixture의 PASS로 입증하지 않는다. 이 한계는 §8.2/§9 Apply STOP을 해제하지 않는다. 기존 Release47/CD22/Renewal/Production Contract 및 PR stdin validation 결과와 최신 HEAD CI는 PR에 별도로 기록한다.
