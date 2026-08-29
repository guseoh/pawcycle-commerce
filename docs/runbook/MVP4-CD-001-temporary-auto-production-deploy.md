# MVP4-CD-001 Temporary Auto Production Deploy

## 상태와 목적

- 작업 ID: `MVP4-CD-001`
- 작업 등급: 고위험
- 상태: MVP4 기간 한정 운영 정책
- 목적: MVP4의 기능·UI 수정에서 `main` 병합 뒤 수동 `Production Deploy` 실행을 반복하지 않고 실제 배포 결과를 빠르게 검토한다.

이 문서는 OPS-010의 기존 단일 release 계약을 교체하지 않는다. MVP4 기간에 **배포 시작 trigger만 임시 자동화**하며, 실제 preflight·deploy·rollback·Control·Flyway 경계는 기존 `Production Deploy`와 OPS-010 계약을 그대로 사용한다.

## 자동 배포 흐름

```text
main push
  -> Publish production images
  -> Backend/Frontend 동일 main SHA image publish 성공
  -> MVP4 Temporary Auto Production Deploy
  -> 기존 Production Deploy workflow_dispatch
       operation=deploy
       target_sha=<workflow_run.head_sha>
  -> 기존 SSM preflight
  -> 기존 SSM deploy
```

자동 dispatch는 다음 조건을 모두 만족할 때만 실행한다.

1. source workflow가 `Publish production images`다.
2. source workflow conclusion이 `success`다.
3. source event가 `push`다.
4. source branch가 `main`이다.
5. `head_sha`가 소문자 40자 commit SHA다.

`workflow_run` trigger 자체도 `branches: main`으로 제한하고 job 조건에서 `success + push + main`을 다시 검증한다. 따라서 `Publish production images`를 사람이 `workflow_dispatch`로 실행한 경우에는 Production 자동 배포로 이어지지 않는다.

## GitHub orchestration 권한

임시 workflow는 `contents: read`, `actions: write`만 요청한다. AWS credential, package write, repository contents write 권한은 요청하지 않는다.

GitHub-hosted runner의 `gh` CLI와 job의 `GITHUB_TOKEN`을 사용해 기존 `production-deploy.yml`의 `workflow_dispatch`만 호출한다. 전달 입력은 아래 두 개뿐이다.

```text
operation=deploy
target_sha=<successful main image release SHA>
```

자동 workflow 자체에는 Production Secret, AWS role, SSM target 값을 전달하지 않는다. 실제 Production 권한과 실행은 기존 `Production Deploy` workflow의 `production` environment와 OIDC 경계 안에 그대로 남는다.

## 보존되는 안전 경계

자동화 workflow는 AWS나 EC2에 직접 접근하지 않는다. 검증된 main SHA를 기존 `.github/workflows/production-deploy.yml`에 전달하는 역할만 한다.

다음 경계는 변경하지 않는다.

- Backend와 Frontend는 동일한 immutable commit SHA image를 사용한다.
- Production Deploy는 main에서 실행돼야 한다.
- 대상 SHA는 main에 포함되어야 한다.
- 대상 SHA의 Backend/Frontend GHCR image가 모두 존재해야 한다.
- AWS credential은 기존 OIDC 경로를 사용한다.
- Production SSM target은 기존 규칙대로 단 하나의 online EC2 managed node여야 한다.
- 실제 활성화 전에 기존 SSM preflight가 성공해야 한다.
- Release contract 또는 Flyway migration 경계가 기존 승인 입력을 요구하면 빈 승인값을 사용하는 자동 deploy는 실패해야 하며 자동 우회하지 않는다.
- `control-adopt`, DB migration 승인, rollback, restore는 이 임시 workflow가 자동 실행하지 않는다.
- Production Deploy concurrency와 기존 rollback 동작을 변경하지 않는다.

## 저장소 검증 기준

병합 전에는 저장소 변경만 검증하며 실제 Production 배포를 시험 목적으로 실행하지 않는다.

- PR HEAD의 Repository Validation이 Green이어야 한다.
- 변경 파일은 임시 orchestration workflow와 이 Runbook으로 제한한다.
- 기존 `publish-production-images.yml`과 `production-deploy.yml`의 diff가 없어야 한다.
- 임시 workflow는 `main`의 성공한 push 기반 image publish만 허용하고 manual image publish를 배포 trigger로 사용하지 않아야 한다.
- dispatch 대상 SHA는 source `workflow_run.head_sha` 그대로여야 하며 branch tag, `latest`, 임의 SHA 변환을 사용하지 않는다.
- 임시 workflow에는 Production Secret, AWS credential 또는 SSM 식별 값을 넣지 않는다.
- 실제 chain 검증은 사용자 승인으로 이 PR을 병합한 뒤 첫 `main` image publish → temporary dispatcher → 기존 Production Deploy 결과를 각각 확인한다.

Repository Validation 성공은 Auto-CD 저장소 준비가 유효하다는 증거일 뿐 실제 Production 배포 성공이나 Production Verified를 의미하지 않는다.

## 활성화 주의

이 workflow는 default branch에 존재해야 `workflow_run` trigger가 활성화된다. 따라서 이 변경 PR을 `main`에 병합하면 이후 `main push`부터 임시 Auto-CD가 활성화된다.

**이 PR 자체의 merge도 `main push`다.** 병합 commit의 Production image publish가 성공하면 그 SHA가 자동 Production 배포 대상이 될 수 있으므로, merge는 임시 Auto-CD 활성화와 첫 자동 배포를 함께 승인하는 시점으로 취급한다.

ChatGPT/Codex가 이 PR을 자동 merge하지 않는다. 실제 merge는 사용자가 최종 승인한다.

## 실패 시 판정

- image publish 실패: 자동 dispatch job은 실행하지 않는다.
- temporary dispatcher 실패: Production Deploy는 시작되지 않은 것으로 판정한다.
- Production Deploy preflight 실패: 활성화하지 않고 기존 workflow 실패로 판정한다.
- Production Deploy deploy 실패: 기존 deploy/rollback 계약과 증거를 따른다.
- dispatcher 성공만으로 Production Verified를 선언하지 않는다.

## MVP4 종료 후 원복

MVP4 Product Complete 이후 기본 정책을 수동 Production 배포로 되돌릴 때는 다음 파일을 별도 PR에서 제거한다.

```text
.github/workflows/mvp4-temporary-auto-production-deploy.yml
```

기존 아래 workflow는 삭제하거나 약화하지 않는다.

```text
.github/workflows/publish-production-images.yml
.github/workflows/production-deploy.yml
```

따라서 임시 Auto-CD 제거 후에도 OPS-010 기반 수동 `Production Deploy` 경로는 그대로 남는다.
