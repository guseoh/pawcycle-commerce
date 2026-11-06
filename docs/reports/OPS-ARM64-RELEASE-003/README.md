# OPS-ARM64-RELEASE-003 — ARM64 Release 후속 Delta

- 날짜: 2026-11-06 (Asia/Seoul)
- 분류: 고위험 / 저장소 변경과 승인된 로컬 격리 검증
- 기준 main: `fbbb4d20a2542a13dc712b0572d94a87f8ab1975`
- 상태: Implemented, 부분 검증. **Production Verified 아님. 실제 Docker Release/Rollback 검증 미완료.**

## 목적

amd64 Release/Rollback 계약을 유지하면서 ARM64 플랫폼 검증을 최소 반영하고 실제 로컬 Docker 검증 범위와 미완료 사항을 보존한다.

## 입력 비교와 변경

`pawcycle-arm64-unverified-changes.zip`의 실제 존재와 두 Python 파일을 확인했다. ZIP SHA-256은 `37a18d1844d21f5a28b1c958ca9238361aa045377ae476b3f616510526680d99`다. ZIP README는 미검증 초안의 설명으로만 취급했다.

`oci-release.py`, `test-oci-release.py`는 최신 main과 기존 `codex/ops-oci-a1-002` worktree에서 각각 byte-identical이었다. ZIP은 같은 main 기반의 순수 추가 Delta로, 엔진 36개 및 테스트 101개 추가/삭제 line이었다. 기존 ARM64 구현과 충돌하거나 중복되는 hunk는 없었다. ZIP을 추출해 덮어쓰지 않고 검토한 hunk만 반영했다.

- native host `x86_64 → amd64`, `aarch64 → arm64`만 허용한다.
- Registry child 선택/config, 저장된 offline graph, local image 및 pull의 플랫폼을 일치시킨다.
- ZIP과 달리 `Host.activate` 진입 시 아키텍처를 확인해 local-only 경로에서도 지원하지 않는 CPU를 조기에 거부한다.
- Manifest/approval/state 형식, amd64 identity, capacity reserve, DB/잠금 Gate와 rollback의 Registry/pull 생략 계약은 유지한다.
- ZIP 테스트 외 ARM64 single manifest/wrapped remote config 및 capacity 회귀 2개를 보강했다.
- Production Compose, image publication workflow, 운영 리소스는 변경하지 않았다.

기존 `codex/ops-oci-cd-011-result` 브랜치의 미커밋 보고서와 기존 worktree는 수정하지 않고, 최신 main의 독립 `codex/ops-arm64-release-003` worktree에서 작업했다.

## 검증 결과

| 검증 | 결과 | 범위와 한계 |
| --- | --- | --- |
| 변경 전 `python infra/production/test-oci-release.py` | PASS 61/61 | `python:3.12-slim`, Linux/root, network none. Docker 효과는 fake/mock |
| 변경 후 같은 테스트 | PASS 67/67 | 기존 61 + 신규 6. amd64/ARM64 Registry/local graph, local-only rollback, capacity 및 실패 복구 회귀 |
| `python3 infra/production/test-oci-cd.py` | PASS 36/36 | 첫 slim 실행은 git 누락으로 ERROR. git이 있는 격리 런타임에서 1회 재실행 성공 |
| `python3 infra/production/validate-production-contracts.py` | PASS | 기존 Production 계약 static 검증 |
| `python3 -m unittest scripts.test_release_workflows` | PASS 15/15 | Release workflow의 기존 계약 |
| 실제 원본 ARM64 이미지 platform/digest/config | PASS 두 이미지 | Docker save OCI bytes 및 격리 Docker load/inspect, 아래 상세 |
| 실제 원본 이미지 Release provenance | 예상된 REFUSED 두 이미지 | revision/source OCI labels 부재. Release 적격 PASS가 아님 |
| 격리 Docker 실제 Release/Rollback·복구 | **FAIL / 미완료** | 두 번째 실행에서 실제 ARM64 fixture 컨테이너 기동 후 adopt의 운영 network Gate에서 중단. activate/rollback/실패 복구는 실행되지 않음 |
| native ARM64 host / GHCR pull / 실제 DB·HTTPS smoke | 미검증 | 현재 Docker Engine/Python host는 linux/amd64, ARM64 이미지 실행은 에뮬레이션 |

`test-oci-release.py`의 PASS에는 Linux 파일 권한·flock·journal·state projection과 fake host를 통한 Release/Rollback·실패 중단·복구 검증이 포함된다. 실제 컨테이너를 사용하는 별도 검증의 실패를 대신하지 않는다.

## 실제 이미지 근거

| 이미지 | OCI index digest | Config digest |
| --- | --- | --- |
| `pawcycle-backend:arm64-local` | `sha256:8c2f45ad64aa1aaf11db0cd2ce6003f7f884fce22912aeb201c0a0aefceac5a5` | `sha256:360d38eb1b9147d27531a46c70d840d65d24d96562ad2a7623efb225bed7f990` |
| `pawcycle-frontend:arm64-local` | `sha256:410bbf61999da795b9850c118b05c57c055a16868800300270d61c593f392881` | `sha256:d2585a2bb8e530d75e764451cef933e3a3a62b6a08c130b9231431a6b3d7d992` |

각 이미지의 OCI index와 유일한 linux/arm64 child manifest의 원본 bytes를 SHA-256/descriptor size로 검증하고, config bytes SHA-256 및 OS/architecture를 확인했다. 실제 Docker `image inspect --platform linux/arm64`의 manifest ID와 config를 archive와 비교했다. Engine이 추가하는 optional null 기본값만 config 비교에서 제외했다. Layer bytes 전체 해시 검증은 이 검사의 범위가 아니다.

실제 원본 이미지에는 Release가 요구하는 revision/source labels가 없어 `Host.local_image`가 `local image identity/platform/provenance mismatch`로 거부하는 것도 확인했다. 라벨 검증을 완화하지 않았다. 별도 lifecycle fixture에만 가상 SHA/source labels를 추가했으며, 실제 소스 provenance로 주장하지 않는다.

## 격리 Docker 실패와 다음 검증

`test-oci-release-docker.py`는 명시적인 `PAWCYCLE_ISOLATED_DOCKER=1`과 daemon hostname `pawcycle-arm64-release-test`를 요구하는 opt-in 검증이다. 자체 daemon만 사용하고 host Docker socket, 운영 Secret/DB, 외부 네트워크를 제공하지 않았다. Source mount는 read-only다. 테스트 전용 privileged dind이며 운영 배포 방식이 아니다.

첫 실행은 inspect의 optional null 필드 비교와 nested overlay-on-overlay mount 오류로 실패했다. config 비교를 보정하고 `/isolated`에 전용 Docker volume을 제공해 각각 수정했다. 한 번 재실행한 결과 원본 이미지 검증은 통과했고, lifecycle은 `adopt → Host.containers → runtime network boundary mismatch`에서 실패했다. fixture의 `network_mode: none`과 실제 `Host.containers`의 운영 네트워크 이름 계약이 충돌했다. 이 Gate를 우회하거나 실패를 PASS로 표시하지 않았다. 지정된 1회 보정/재검증 예산에 따라 추가 반복은 중단했다.

다음 조치: 이 외부 연결 없는 nested daemon **내부에만** 기존 network 이름/경계를 재현하고 fixture Compose의 네트워크 선언을 맞춘 뒤 재검증한다. 이 실패는 당시 버전에서 얻은 실행 근거이며, 후속 Correction의 통과를 증명하지 않는다. 이후 actual adopt/activate/rollback, 변조 시 무변경, 교체 후 health 실패 시 local-only 복귀, 복구 실패 marker 유지, manual recover-state와 proxy 보존을 모두 확인해야 한다. Registry transport, source/main, DB·HTTPS는 이 fixture의 adapter/mock 경계이므로 이후에도 별도 검증이 필요하다. sleep/health fixture 컨테이너를 실제 application HTTP 검증으로 주장하지 않는다.

재현 준비(저장소 밖 실행이 아닌, 이 독립 worktree에서만 수행):

```powershell
docker image save -o tmp/arm64-images.tar pawcycle-backend:arm64-local pawcycle-frontend:arm64-local
# 일회성 로컬 test image. 저장소나 Production 의존성 변경이 아님.
@'
FROM docker:28-dind
RUN apk add --no-cache python3
'@ | docker build -t local/pawcycle-arm64-release-test:003 -
docker run -d --name pawcycle-arm64-release-003 --hostname pawcycle-arm64-release-test --privileged --network none -e DOCKER_TLS_CERTDIR= -e TMPDIR=/isolated/tmp --mount 'type=volume,source=pawcycle-arm64-release-003-data,target=/isolated' --mount "type=bind,source=$((Get-Location).Path),target=/src,readonly" local/pawcycle-arm64-release-test:003 sh -c 'mkdir -p /isolated/tmp; exec dockerd --host unix:///var/run/docker.sock --data-root /isolated/docker --feature containerd-snapshotter=true --iptables=false --bridge=none'
# daemon readiness 확인 후:
docker exec -e PAWCYCLE_ISOLATED_DOCKER=1 -e PYTHONDONTWRITEBYTECODE=1 pawcycle-arm64-release-003 python3 /src/infra/production/test-oci-release-docker.py
```

실행 dind 기반 digest: `docker:28-dind@sha256:2a232a42256f70d78e3cc5d2b5d6b3276710a0de0596c145f627ecfae90282ac` (Engine 28.5.2/containerd store). 패키지 설치와 test image build만 네트워크를 사용했고 검증 daemon은 network none이었다. 원본 image archive와 일회성 test image/volume은 Git에 포함하지 않는다.

### 후속 Correction — 2026-11-06

- 이전 Draft PR #512의 Correction commit `4db685942c220a44252c7939eb87fe659cc97b9f`: 운영 Release 엔진의 네트워크·Health Gate를 변경하지 않고 격리 DinD Compose fixture에 `app`(internal), `edge`, `database-egress` 네트워크, 역할별 연결, Proxy Healthcheck를 추가했다.
- Fixture 검증은 실제 nested Docker network의 `Internal` 속성과 Compose network 이름·격리 플래그·Healthcheck를 대조한다. 바깥 DinD의 `--network none` 실행 경계는 유지한다.
- **수정 후 실제 Docker lifecycle 재실행은 미완료**다. 마지막 실행 증거는 위의 `adopt` 실패이며, 다음 Gate에서 이미지의 `repo@digest` 조회 등 추가 실패 가능성도 미확인이다. `adopt/activate/rollback/recover-state` PASS로 판정하지 않는다.

## Frontend 메모리 판단 유보

사용자가 전달한 선행 로컬 실험 결과는 **256MiB OOM, 512MiB HTTP 30/30 PASS**다. 이번 작업에서 메모리 실험을 다시 실행하거나 원시 OOM/HTTP 로그를 독립 검증하지 않았다. 따라서 선행 관측으로 기록하며 이번 실행의 PASS 집계에는 포함하지 않는다.

Production `compose.yaml`의 Frontend `mem_limit: 256m`은 그대로다. ARM64 운영 적합성의 남은 capacity 위험이며, 512MiB 상향 여부는 총 host memory·Backend/Proxy·여유분과 동일 workload 근거를 바탕으로 별도 운영 판단/승인이 필요하다. 이번 저장소 변경은 상향이나 운영 Release를 승인하지 않는다.

## 남은 위험

운영 OCI·DB·Secret·Production Release, merge와 image push는 실행하지 않았다. 저장소 변경의 복구는 별도 revert PR이며 운영 rollback 승인을 의미하지 않는다. 독립 외부 reviewer와 PR CI 결과는 이 로컬 보고서로 보증하지 않는다. 실제 Docker lifecycle 미완료와 native ARM64/운영 검증이 남아 있으므로 배포 또는 병합 준비 완료를 주장하지 않는다.
