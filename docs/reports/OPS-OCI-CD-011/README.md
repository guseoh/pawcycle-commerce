# OPS-OCI-CD-011 — 최초 OCI 전환 중단 결과

- 작업 ID: OPS-OCI-CD-011
- 작업 등급: 고위험
- 실행 구분: 실제 운영 실행

## 목적

기존 OCI 앱의 감독형 정렬→정상 Adopt→`application-v1` Controller/Timer 활성화를 하나의 작업으로 수행한다.

## 명시적 승인 근거

2026-10-31 사용자의 최초 OCI 애플리케이션 전환 일괄 위임. DB·Secret·NSG·인증서·비용 리소스 변경과 강제 Rollback은 제외했다. 운영 중 코드 결함은 별도 Task Branch/PR에서 수정하고 승인 없이 병합하지 않는다.

## 적용 전 확인

| 항목 | 실제 결과 |
| --- | --- |
| 승인 main | `d9117f7390e5fcde5866e24e8691c1a3fff3fb6c`; 정확한 main push CI와 두 이미지 발행 성공 확인 |
| 적용 전 | 운영 SHA `831eb9033fc67f8d4ee47a1ebdb73f1465041a95`; 세 서비스 healthy; 두 HTTPS 경로 200 |
| 용량 | Registry layer digest·압축 bytes·해제 DiffID 직접 검증. 필요 여유 3,754,908,847 bytes, 최종 여유 6,066,139,136 bytes. 기존 이미지 보존, reserve 1GiB 및 로그 cap/현재 metadata 기반 복구 쓰기 63,004,672 bytes 포함 |

## 주요 결과·적용 후 확인

승인 bytes/hash의 기존 실행파일 5개, root 보호 bare mirror와 private receipt를 준비했다. 기존 root Source의 HEAD·worktree는 유지하고 main ref만 fast-forward/CAS 갱신했다.

`CDHost.paths(base, head)`가 `Host.paths(manifest)`를 덮어써 실제 `materialize`에서 `TypeError`가 발생했다. 대상 이미지 pull·서비스 교체 전에 중단했다. 운영 SHA·앱/Proxy container ID·StartedAt·Runtime·Compose·Nginx·sibling·Network·인증서 Volume은 불변이며 세 서비스 healthy다. Release State·Policy·enable sentinel은 없고 Timer inactive다.

## 독립 확인

OCI 외부에서 기본 CA 검증과 redirect 금지 상태로 `/products`, `/api/products` 직접 HTTPS 200을 확인했다.

## 복구·rollback

기존 정상 이미지의 local descriptor/config/platform/revision과 실제 용량을 확인했다. 서비스 교체가 없어 복구는 불필요했다. verified previous Release는 없으며 실제 Rollback 성공을 주장하지 않는다.

현재 Backend Digest는 `sha256:c9126bf0f2a3ee6ff21c531f9710418484227833cae76206a6e8b48c9cda2b7c`, Frontend Digest는 `sha256:568134c6a7ae4a64940b41151bb5a41efa17c3c2fe8f639077b192f705b6f11a`다. 원시 운영 데이터와 credential은 보관하지 않았다. 선택한 hash/identity와 검사 결과는 호스트 root 보호 receipt로 보존했다.

## 수정 및 검증

최소 해결책은 Controller의 Git diff 메서드를 `changed_paths`로 분리해 상속된 Release snapshot 경로를 보존하는 것이다. 실제 CDHost의 snapshot·Compose·세 서비스 선택 및 이전 이미지 복귀 호출을 검증하는 회귀를 추가했다. Docker/이미지 조회/Health만 격리하며 새 Harness나 운영 bypass를 추가하지 않는다.

검증: 기존 WSL Linux/root에서 `python3 infra/production/test-oci-cd.py` **36개 PASS**, `python3 infra/production/test-oci-release.py` **61개 PASS**. 이는 운영 배포·Rollback 검증을 대체하지 않는다. 최초 추가 회귀는 격리된 Health stub 누락으로 실패했으며 해당 경계를 수정한 뒤 통과했다.

## 미실행 항목

대상 이미지 pull·실제 Docker Size 확인·앱 교체·Adopt·systemd 설치/활성화·최초 reconcile·자동 배포 E2E·강제 Rollback은 수행하지 않았다. 외부 독립 reviewer는 아직 미실행이다.

## 남은 위험

수정 PR 검토·명시적 병합 승인 후 최신 main의 승인 설치본을 다시 고정하고, 기존 이미지 출처를 보존해 최초 감독형 배포→실제 정상 Adopt→Policy/Timer 활성화를 이어가야 한다. 운영 설치본은 미병합 코드로 수정하지 않았다. 대상 이미지의 실제 Docker Size, pull 이후 용량, 정책 만료와 정상 기준선 일치는 실행 직전 Gate로 남는다. Proxy를 보존하려면 Compose 입력의 실제 변경과 동일 Proxy 렌더링을 교체 전에 입증해야 한다.
