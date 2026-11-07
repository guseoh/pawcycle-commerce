# OPS-ARM64-FE-001 — Frontend ARM64 memory/cgroup comparison

- 날짜: 2026-11-07 KST (사용자 테스트 출력의 UTC 측정 시작은 2026-11-06)
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 상태: 사용자 실측에 근거한 운영 설정 후보
- 근거 수준: **사용자 제공 Docker Desktop PowerShell 출력**. ChatGPT가 Docker 엔진에서 직접 실행하거나 원본 로그·이미지 digest를 독립 검증한 결과가 아님.
- 범위: 동일 로컬 `pawcycle-frontend:arm64-local`의 Linux ARM64 에뮬레이션, `--network none`, `--cpus 0.35`, `--pids-limit 128`, `--read-only`, 동일 tmpfs, swap 비활성. 실제 OCI A1, GHCR, 운영 Backend/DB/HTTPS 아님.

## 목적

Production Compose Frontend `mem_limit: 256m`와 `node -e` 기반 10초 간격 Healthcheck가 ARM64 로컬 환경에서 OOM에 취약한지 비교하고, 최소한의 메모리 한도·Healthcheck 후보를 정한다.

## 결과 또는 증거

| 조건 | 기동 | HTTP | Healthcheck | 최대 cgroup memory | OOM |
| --- | --- | --- | --- | --- | --- |
| 256 MiB, 초기 `node -e` probe | 준비 실패 | 0/0 | 미실행 | 미수집 | true, exit 137 |
| 256 MiB, `wget` probe | 준비 실패 | 미실행 | 미실행 | 미수집 | true, exit 137 |
| 512 MiB, 초기 `node -e` probe | 준비 성공 | 2/3 | 미완료 | 미수집 | true (종료 시 ExitCode 1 기록) |
| 512 MiB, `wget` probe | 준비 성공 | 30/30 | 별도 미실행 | 507092992 B ≈ 483.6 MiB | false |
| **768 MiB**, `wget` probe + 운영과 동등한 `node -e` probe | 준비 성공 (Next Ready 28.0 s) | **30/30** | **10/10** | **550375424 B ≈ 524.9 MiB** | **false**, Running |

768MiB에서 마지막 `docker stats` 순간 메모리는 `386.4MiB / 768MiB`였고, 최고치 기준 여유는 약 `243.1MiB`(31.7%)였다. 512MiB의 `wget` 단독 검증에서는 최고치 기준 여유가 약 28.4MiB로 작았다. 512MiB에서 Node child probe 때문에 OOM이 증가했을 가능성은 있으나 단독 원인으로 분리 검증하지 않았다.

## 위험과 제한

- 768MiB를 **운영 설정 후보**로 선택하고, Compose의 Frontend 메모리 한도를 `768m`으로 조정한다. `node -e` Healthcheck는 반복적인 Node 프로세스 생성 부담을 피하기 위해 이미 해당 Alpine 이미지에서 단독 HTTP 30/30 성공 근거가 있는 `wget -q -O /dev/null`로 교체한다. 이 선택은 사용자 최종 운영 승인을 대체하지 않는다.
- 위 실측은 `wget` 30회 + `node -e` 10회가 순차적으로 실행된 단기 테스트이다. Docker Compose가 자동 실행하는 10초 간격 Healthcheck나 Backend/Proxy/DB가 함께 있는 Production 실환경을 검증한 것이 아니다. Chrome 브라우저/기능 E2E/장시간 부하/연속 동시 요청도 포함하지 않는다.
- `pawcycle-frontend:arm64-local`의 이번 실행 이미지 ID 및 provenance를 원본 Registry digest와 대조하지 않았다. Native ARM64 호스트 실행·GHCR 서명/라벨·실제 A1 네트워크와 메모리 사용량은 미검증이다.
- Backend 640MiB, Frontend 768MiB, Proxy 128MiB의 Compose 서비스별 메모리 한도 합은 1536MiB이고, Docker·OS·관측 도구·페이지 캐시의 여유는 별도로 검토해야 한다. 이는 상한 합계일 뿐 실제 호스트 메모리 사용량은 아니다.
- Compose 변경은 Registry 이미지 발행과 구분되고, `main` 병합만으로 운영 호스트에 자동 적용됐다고 가정하지 않는다. OCI/A1 배포, 운영 DB 및 Production Verified는 별도 승인·검증 Gate다.
- 회귀 검증은 `test-production-compose.sh`의 최종 Compose 구성에서 `mem_limit=768MiB`, `wget` Healthcheck, read-only/pids_limit 경계를 확인한다. 실제 container lifecycle CI와 A1 native 최종 검증은 별도 근거로 기록한다.
- 실패 시 복구 경계: Production 적용 전에는 해당 PR을 revert할 수 있다. 운영 적용 후에는 중단·원인 분석 및 승인된 기존 정상 Release/Compose 복구 절차를 사용하며, 이미 OOM이 관측된 256MiB 설정으로 무조건 되돌리는 것은 안전한 복구가 아니다.
