# Production 운영 아키텍처 개요

## 현재 상태

이 문서는 현재 `main`에서 유효한 Production 운영 경계를 설명한다. 과거 AWS 운영 구조의 실행 절차를 재현하기 위한 문서가 아니다.

- Production deployment target: **OCI app01**
- CD: **DEFER**
- 무중단 배포: **DEFER**
- 배포 방식: **단일 서버·수동 배포**. Release/Recovery CLI와 Host Pull CD는 저장소 구현이며, 실제 설치·정책 승인·자동 실행은 OFF다.
- 현재 운영 사실의 확인은 전체 운영 기능의 검증을 의미하지 않으며 **Production Verified가 아니다**.

AWS 리소스 종료 이후 EC2/SSM/RDS/S3/CloudWatch 기반 실행 경로와 해당 공급자 전용 검증·Runbook은 active contract에서 제거했다. 새로운 Cloud/Production 실행은 별도의 고위험 작업으로 설계·승인·적용 전후 검증을 거쳐야 한다.

## 현재 유지하는 공급자 중립 자산

OCI `app01`의 Backend/Frontend/Nginx와 별도 Managed MySQL topology는 `docs/adr/ARCH-012-production-observability-boundary.md`를 따른다. Application Compose는 DB lifecycle을 소유하지 않는다. 다음 자산의 존재는 실제 운영 실행 승인을 대신하지 않는다.

| 영역 | 현재 의미 |
| --- | --- |
| `.github/workflows/publish-production-images.yml` | `main`의 runtime 변경 시 Backend/Frontend GHCR image를 commit SHA 기준으로 build·publish하는 공급자 중립 경로 |
| `.github/workflows/production-release-readiness.yml` | 특정 SHA의 GHCR image 존재 여부를 확인하는 readiness 도구. CI 전체 성공·배포 승인·배포 성공을 의미하지 않음 |
| `infra/production/backend.Dockerfile`, `frontend.Dockerfile` | 배포 대상과 독립적인 application image build 계약 |
| `infra/production/compose.yaml` | 현재 OCI Application topology의 저장소 계약. 파일 존재만으로 운영 적용·검증을 보장하지 않음 |
| `infra/production/oci-cd.py`, `systemd/pawcycle-oci-cd.*` | 정확한 main SHA의 CI·GHCR을 재검증하는 outbound Host Pull. 사전 정책·정상 baseline·DB 호환 검증기·G0 승인 없이는 실행 불가 |
| Nginx·auth smoke·진단·관측 관련 자산 | 개별 승인·적용 전후 확인이 필요한 운영·검증 자산 |

GitHub Actions에는 현재 Cloud provider에 application을 활성화하는 Production Deploy workflow가 없다. GHCR image publication 또는 readiness 성공은 Production 배포, 서비스 활성화, Production Verified를 의미하지 않는다.

## 확인 사실과 검증 한계

2026-10-25 A07 읽기 전용 조사에서 Application 컨테이너 healthy, `/`·`/products`·`/api/products` GET 200, Backend metric 수집, Prometheus/Grafana health 응답, 제공 인증서와 디스크 인증서 일치 및 갱신 timer 활성 상태를 확인했다. 이는 조사 시점의 제한된 관측이며 지속적인 가용성이나 갱신·복구 성공을 보장하지 않는다.

OCI Application Rollback, 인증서 갱신 중단 복구, DB backup/PITR/restore, Cloud 접근 제어, 전체 알림 연결과 로그인·주문 등 업무 흐름은 이 조사로 검증하지 않았다. Managed MySQL topology와 외부 DB 연결 경계는 확인했으나 OCI DB resource identity·권한·복구 설정은 별도 확인이 필요하다.

## 후속 운영 실행 경계

현재 OCI 운영 대상의 존재는 새로운 배포·복구·Cloud 변경 승인을 의미하지 않는다. CD 운영 활성화와 블루/그린 등 무중단 배포는 DEFER이며, 기존 AWS 실행 경로를 재활성화하지 않는다. 저장소 CD 계약과 남은 G0는 [Host Pull Runbook](../runbook/OPS-OCI-CD-003-host-pull.md)을 따른다.

후속 운영 변경은 최소한 다음의 적용 범위·현재 상태·미검증 항목을 확인하고 필요한 계약을 별도 승인해야 한다.

1. 실제 target과 network/ingress 경계
2. runtime secret 전달 방식
3. database와 backup/restore 계약
4. deployment activation 및 rollback 방식
5. health/smoke/observability와 장애 대응
6. 비용·복구·보안 위험과 중단 조건
7. 적용 전후 증거와 Production Verified 판정 조건

이 결정과 실제 Cloud 실행은 저장소 준비와 분리하며 사용자 명시 승인 없이 수행하지 않는다.

## 과거 AWS 운영 증거

AWS에서 EC2/EBS, SSM, RDS 전환 준비, S3 논리 backup·격리 restore, CloudWatch/SNS alert, HTTPS, release·rollback 등을 구축하거나 검증했던 사실은 삭제하지 않는다. 원래 시점의 사실과 결과는 다음 위치에서 확인한다.

- `docs/reports/**`: 실행·측정·검증 결과
- `docs/learning/**`: PR별 작업 맥락과 학습 기록
- `docs/adr/**`: 당시 기술·운영 결정과 trade-off
- Git commit / Pull Request history: 실제 변경 이력

이 자료는 회고·포트폴리오·기술 글을 위한 역사적 evidence다. 현재 AWS 리소스나 실행 가능한 AWS Production contract가 남아 있다는 의미는 아니다.
