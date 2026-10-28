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

현재 `runtime/backend.env`를 source/echo하거나 환경변수 내용·raw inspect·secret hash를 Chat/PR/로그에 출력하지 않는다. 값과 byte hash는 보호된 운영 evidence 안에서만 대조한다.

1. 승인된 작업 창에 env 작성자를 중지/직렬화하고 원본 regular/no-symlink/single-link·opc 0600, inode·size·mtime를 확인한다. 부모가 opc이면 읽는 동안에도 교체될 수 있으므로 열린 FD의 fstat와 재검증 및 쓰기 중단 근거가 필요하다. 변경이 감지되면 Abort한다.
2. root 보호 runtime 안에 새 `.bundle.<unique>` directory 0700을 만들고 원본 bytes를 새 regular `backend.env` root 0600으로 복사한다. 원본을 이동·chown·hardlink하지 않는다. 원본과 사본의 byte hash·길이를 비공개 비교하고 원본 안정성도 재확인한다. 파싱/복사 실패는 values를 출력하지 않는다.
3. Release의 필수 datasource URL/username/password와 automation enabled(boolean)/batch/fixed-delay(양의 정수), 중복 key·누락·빈 값·인용 및 실제 Compose env_file 파싱을 검증한다. datasource host/port/SSL은 사용하는 도구 계약과 대조한다. 기존 실행 컨테이너의 effective env와 semantic 동등성도 비공개 비교한다. shell eval/source는 금지다. 단순 hash 일치는 실제 주입값 검증을 대신하지 않는다.
4. 검증 결과·원본/사본 hash·검증기 버전·승인 reference를 root 보호 evidence에 결합한다. **모든 검증 성공 후에만** `.complete` root 0600 regular single-link를 생성한다. CLI는 marker 내용의 의미를 입증하지 않고 파일 보호만 검사하므로 marker는 승인된 검증 절차의 완료 증거다. 빈 marker를 미리 touch하여 통과시키지 않는다.
5. env·marker 파일을 fsync하고 bundle directory를 fsync한다. 같은 runtime directory 안의 임시 symlink를 완료된 bundle로 연결한 뒤 rename으로 `current`를 원자 전환하고 runtime directory를 fsync한다. 최종 `current` resolve가 runtime 바로 아래 완료 bundle인지 확인한다. 기존 current가 없었던 사실과 기존 파일·bundle은 보존한다.
6. 이 연결은 이미 실행 중인 Backend의 env를 바꾸지 않는다. 설치 후 보호 경계·hash·marker/필수 key와 기존 서비스 identity/health를 재확인한다. 동등성 검증과 Source/Label/Nginx가 미완료면 Adopt는 계속 STOP이다.

Bundle 복구는 deploy/renewal lock과 env 작성자 중지 경계를 유지한 상태에서 기존의 검증된 current target으로 atomic 복원한다. 최초 설치라 이전 current가 없으면 새로 만든 연결만 제거하고 기존 backend.env는 보존하며 CLI/Adopt/CD를 OFF로 둔다. 새 bundle이 실행 컨테이너에 이미 소비됐으면 symlink만 돌려 서비스가 복구됐다고 주장하지 않고 별도 승인된 lifecycle Recovery로 넘긴다. 미완성 bundle을 current에 연결하거나 자동 삭제하지 않는다.

## 4. Renewal Lock 전환

갱신기는 root 소유의 안전한 ancestor, root regular 단일-link 0600 lock만 사용한다. 신규 생성은 script 내부 umask 077 + exclusive create로 수행해 service UMask=0022에서도 0600이다. 기존 파일은 truncate/chmod/unlink/replace하지 않고 `<>`로 열어 FD와 경로의 device/inode·owner/mode/nlink를 대조하고 실제 flock을 유지한다. symlink/hardlink·owner/mode 위반은 certbot/reload 이전에 거부한다. `/run/lock`의 기존 sticky directory 계약은 Release와 같다. systemd unit 변경은 안전성의 전제가 아니며 기존 갱신 동작을 유지한다.

현재 0644 lock은 새 script가 자동 보정하지 않는다. 별도 승인된 운영자는 renewal timer/진행 중 갱신과 Release/catalog/auth 작업을 배제하고, 실제 해당 regular root 단일-link inode를 열어 nonblocking flock을 획득한 뒤 **그 FD에만 fchmod(0600)**하고 inode 동일성을 재확인한다. lock을 rename/delete/recreate하지 않는다. 누군가 lock을 보유하거나 안전하지 않은 파일이면 STOP한다. 그 뒤 검토된 갱신 script를 설치하고 기존 unit/인자·timer 상태를 확인한다. UMask 변경·timer 시작은 이번 저장소 작업에서 수행하지 않는다.

permission 전환 실패 시 서비스는 건드리지 않고 timer/CD를 안전한 중지 상태로 둔 채 운영자가 조사한다. 정상 0600 lock을 옛 0644로 자동 복원하지 않는다. script 복귀가 필요하면 사전 승인된 설치본을 쓰되 공유 lock과 그 inode를 유지하고 CD를 OFF로 둔다. 실제 갱신·ACME dry-run·Nginx reload는 별도 승인이다.

## 5. 최초 전환 순서와 승인 Gate

| 단계 | 승인·확인·중단·복구 |
| --- | --- |
| Preflight | 전달된 근거 재관측, 정확한 Source/Label/mount/ancestor·UID/mode/inode·env 계약·lock·unit 경로 목록, 세 서비스/sibling identity/health·HTTPS, 인증서 유효성·pending/state/transition 부재를 확정. 운영 raw 값은 비공개 evidence로 관리 |
| 별도 승인 | 변경할 각 directory/file metadata와 설치 source/hash, bundle 검증·전환, 기존 0644 lock의 FD permission 변경, 유지할 자원·작업 창·담당자·Abort 기한·복구 입력을 각각 승인. 미확인 경로나 env 의미가 있으면 실행 승인 입력을 완성하지 않음 |
| Apply 1: 직렬화·Lock | 배포/수동 운영/env writer를 배제하고 timer 상태를 보존·승인된 중지. 먼저 승인된 `/opt/pawcycle` ancestor metadata 보호를 완료하고 기존 state/deploy.lock의 보호·inode를 확인해야 공유 lock을 신뢰할 수 있다. deploy→renewal lock 획득. 기존 renewal lock 0600 전환 후 새 갱신기 설치. inode·경합 검사 실패는 Abort |
| Apply 2: Source/Runtime | 승인된 최소 ancestor metadata 보호와 깨끗한 root Source 준비. state/deploy.lock 경로·inode와 기존 env는 유지. 위 bundle 검증·완료·atomic 연결. 서비스 교체 없음 |
| Apply 3: Label | 기존 Label 경로를 안전하게 보호할 수 있는지 승인된 ancestor/파일 목록과 대조. 불가하면 **여기서 STOP**하고 C의 별도 운영 승인 요구. Source 변경·Proxy 교체로 Backend/Frontend Label이 바뀌었다고 주장하지 않음 |
| Apply 4: Nginx | Label·Source/Runtime/Lock 조건 충족 후 OPS-OCI-CD-004의 별도 Bootstrap 승인·검증·Proxy-only 복구 절차 적용. 이 단계에도 Backend/Frontend 무조건 recreate 금지 |
| Postflight | 전체 protected 경로·기존 Label hash·bundle hash/동등성·lock0600/inode·의도된 경합·서비스/sibling identity/health·직접 2xx/TLS·인증서/volume 보존 확인. 승인된 timer 재개 후 실제 갱신 관측은 별도 승인 범위로 구분 |
| Abort | drift·Lock 경합·권한/marker/Label/의미 검증 실패·건강성 저하 시 다음 단계 금지. 기존 정상 서비스는 유지, CD OFF·Adopt STOP |
| Recovery | 변경한 metadata 목록·기존 owner/mode·Source 및 current target을 보호 evidence로 복구. root Source를 opc writable 상태로 복구하면 CLI/CD는 계속 OFF. 0600 공유 lock은 보존. 컨테이너 교체가 있었으면 별도 승인한 image/env/config 복귀 및 health를 확인하며 DB/인증서/볼륨 복구로 확대하지 않음 |

## 6. 검증과 남은 blocker

`test-production-https-renewal.sh`는 root disposable fixture/fake Docker로 UMask0022 신규 lock0600, 기존 bytes/inode 유지, 외부 flock 경합, symlink/hardlink·owner·mode 거부와 기존 갱신/pending/no-restart 회귀를 검증한다. Release/CD 회귀와 Production Contract 및 최신 HEAD Repository Validation 결과는 PR에 기록한다. 실제 경로·bundle 설치·운영 경합·갱신·컨테이너는 검증하지 않았다.

Adopt에는 여전히 안전한 세 config_files 경로, 새 bundle의 실제 주입 동등성, 전환 후 Nginx fingerprint, Source/HTTPS·DB epoch·Flyway 및 정상 baseline·capacity 근거가 필요하다. Host Pull은 추가로 검증된 controller/source 실행환경·DB compatibility observer·filesystem별 peak/recovery budget·exact-SHA CI·알림 수신·최초 정책/활성화 승인이 필요하다. 이 문서나 `.complete` 생성은 어느 Gate도 자동 승인하지 않는다.
