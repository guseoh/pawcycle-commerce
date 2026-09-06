# Production 운영 아키텍처 개요

## 현재 상태

이 문서는 현재 `main`에서 유효한 Production 운영 경계를 설명한다. 과거 AWS 운영 구조의 실행 절차를 재현하기 위한 문서가 아니다.

- Production deployment target: **없음**
- CD: **DEFER**
- 무중단 배포: **DEFER**
- OCI: 승인된 후속 방향이지만 아직 active Production target이 아니며 **Production Verified가 아니다**.

AWS 리소스 종료 이후 EC2/SSM/RDS/S3/CloudWatch 기반 실행 경로와 해당 공급자 전용 검증·Runbook은 active contract에서 제거했다. 새로운 Cloud/Production 실행은 별도의 고위험 작업으로 설계·승인·적용 전후 검증을 거쳐야 한다.

## 현재 유지하는 공급자 중립 자산

`main`에는 새 배포 target을 미리 확정하지 않고 재사용 가능한 저장소 자산만 유지한다.

| 영역 | 현재 의미 |
| --- | --- |
| `.github/workflows/publish-production-images.yml` | `main`의 runtime 변경 시 Backend/Frontend GHCR image를 commit SHA 기준으로 build·publish하는 공급자 중립 경로 |
| `.github/workflows/production-release-readiness.yml` | 특정 SHA의 GHCR image 존재 여부를 확인하는 readiness 도구. 배포 성공이나 실행 승인을 의미하지 않음 |
| `infra/production/backend.Dockerfile`, `frontend.Dockerfile` | 배포 대상과 독립적인 application image build 계약 |
| `infra/production/compose.yaml` | container topology를 보존하는 저장소 계약. 현재 실제 Production host에 적용 중이라는 뜻이 아님 |
| Nginx·auth smoke·진단·관측 관련 자산 | 새 target 설계 시 재평가할 수 있는 공급자 중립 참고·검증 자산 |

GitHub Actions에는 현재 Cloud provider에 application을 활성화하는 Production Deploy workflow가 없다. GHCR image publication 또는 readiness 성공은 Production 배포, 서비스 활성화, Production Verified를 의미하지 않는다.

## 다음 Production target 경계

OCI는 현재 승인된 후속 배포 방향이다. 하지만 OCI Compute, network, database, secret, ingress, backup/restore와 실제 application activation 계약은 각 단계의 현재 제약을 확인한 뒤 별도로 결정한다. AWS에서 사용했던 구조를 이름만 OCI로 바꾸어 이식하지 않는다.

새 target을 활성화하려면 최소한 다음이 다시 정의되어야 한다.

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
