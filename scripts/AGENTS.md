# Harness / Scripts 경로 규칙

이 파일은 `scripts/**` 및 이와 연결된 CI·Harness 자동화(`.github/workflows/**`, `.github/scripts/**`)를 수정할 때의 **안전 경계와 불변식**만 정의한다. 공통 승인·Git·PR·산출물·병합 규칙은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따른다.

## 책임

- PR metadata·task artifact 검증(`validate-task-artifacts.py`)
- 변경 경로 기반 CI lane 분류(`classify-validation-changes.py`)
- Production 이미지 publish 대상 판정(`classify-production-image-changes.py`)
- 협업 자동화·리뷰 handoff·Discord 알림 보조 스크립트와 회귀 테스트

## 불변식

- **Fail-safe 분류**: 미분류 경로, 등록되지 않은 Skill, classifier·workflow 자체 변경은 보수적으로 전체 영향 lane을 유지한다.
- **Release side effect 차단**: 문서·Harness·Skill·metadata 같은 non-runtime 변경이 Production image build/push나 deploy를 시작하지 않아야 한다.
- **Validator 경계 준수**: `docs/runbook/lean-harness.md` §8에 따라 기계적으로 판별 가능한 안전 누락만 차단하고, 한국어 문체·업무 prefix allowlist·Prompt 길이·선택적 PR 소제목은 차단하지 않는다.
- **민감정보 Redaction**: Secret, token, Webhook URL, 원시 운영 데이터가 로그·알림 payload·fixture에 노출되지 않아야 한다.

## 검증

변경 범위에 맞는 최소 회귀부터 실행하고, classifier·workflow 변경 시 영향 lane 전체 실행을 확인한다.

- `python -m py_compile`로 수정 스크립트 문법 확인
- `python -m unittest scripts.test_validate_task_artifacts scripts.test_validate_conventions_workflow scripts.test_discord_context`
- release workflow/classifier 변경 시 `python -m unittest scripts.test_release_workflows` 추가

## Review guidelines

루트 `AGENTS.md`의 `Review guidelines`와 함께 적용한다.

- `P0`: non-runtime 변경이 Production release·deploy를 시작하도록 classifier·workflow를 완화함, Secret·Webhook URL이 payload나 로그에 노출됨.
- `P1`: classifier가 필요한 component lane을 놓치거나 미분류 경로를 fail-safe하지 않음, validator가 `lean-harness.md` §8의 비차단 항목(prefix allowlist, 문체 등)을 강제함, 스크립트 변경에 회귀 테스트가 없음.
