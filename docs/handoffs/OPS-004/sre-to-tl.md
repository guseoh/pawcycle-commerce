# OPS-004 SRE → TL 인수인계

## 전달 목적

CodeRabbit AI를 PawCycle Commerce PR 리뷰에 적용할 수 있도록 저장소 설정과 운영 지침을 추가했음을 TL에게 전달한다.

CodeRabbit GitHub App 설치, 무료 범위 확인, selected repository 선택은 저장소 UI에서 사용자가 수행해야 하므로 주의사항을 분리한다.

## 적용된 CodeRabbit 설정

- 설정 파일: `.coderabbit.yaml`
- 리뷰 언어: `ko-KR`
- Review profile: `assertive`
- Draft PR 자동 리뷰: 비활성화
- 기본 브랜치: `main`은 기본 리뷰 대상으로 간주
- 추가 base branch: `develop`
- High-level summary placeholder: `@coderabbitai summary`
- 경로별 리뷰 지침:
  - `backend/**`: 도메인 규칙, 트랜잭션, 인증/인가, 예외 처리, 테스트, JPA/N+1
  - `frontend/**`: 사용자 흐름, 인증 상태, API 계약, 접근성, 폼 검증, UI 상태
  - `docs/**`: 승인 문서와 추적성, 결정 상태 오표기
  - `db/**`: 제약 조건, FK, 인덱스, 불변 조건, 롤백·재적용 위험
  - `.github/**`: Secret, 권한, 트리거, 검증 누락, 자동화 실패 은폐

## Codex Review와 CodeRabbit 역할 분리

Codex Review:

- 설계 방향 검토
- 구현 초안 작성
- 리팩터링 방향 논의
- CodeRabbit 리뷰 반영 여부 판단
- 후속 작업 프롬프트 작성

CodeRabbit AI:

- PR 내부 변경사항 요약
- 라인별 코드 리뷰
- 테스트 누락 지적
- 보안 문제 지적
- 인증/인가 누락 지적
- 도메인 규칙 위반 지적
- 유지보수성 문제 지적

## 사람이 최종 판단해야 할 항목

- CodeRabbit 지적을 전부 그대로 반영하지 않는다.
- CodeRabbit은 최종 결정자가 아니라 PR 검토를 도와주는 자동 리뷰어다.
- 보안, 인증/인가, 결제, 주문 상태, 정기배송 상태 전이, 개인정보 관련 변경은 사람이 반드시 다시 검토한다.
- 요구사항, 도메인 규칙, UX, ADR, API 계약과 충돌하는 리뷰 제안은 Product Owner/Tech Lead 판단을 우선한다.
- merge는 사용자가 직접 결정한다.

## GitHub App 설치 시 주의사항

- selected repository로 `guseoh/pawcycle-commerce`만 선택한다.
- 모든 repository 접근 권한을 주지 않는다.
- 무료 범위에서만 사용한다.
- 리뷰 품질과 사용량을 확인한 뒤 확대 여부를 결정한다.
- CodeRabbit 유료 플랜 또는 조직 전체 설정 변경이 필요하면 진행하지 말고 사용자 결정을 받는다.
- GitHub Actions 워크플로 변경은 OPS-004 범위가 아니므로 수행하지 않는다.

## 후속 작업 영향

- DATA-001 이후 PR에서는 CodeRabbit이 문서·DB 설계 변경의 추적성, 제약 조건, 인덱스 후보, 불변 조건 누락을 보조 검토할 수 있다.
- API-001 이후 PR에서는 CodeRabbit이 요청·응답, 오류 형식, 인증/인가 표현, 테스트 누락을 보조 검토할 수 있다.
- Backend/Frontend 구현 PR에서는 CodeRabbit이 보안, 인증/인가, 도메인 규칙 위반, 테스트 누락과 유지보수성 문제를 보조 검토할 수 있다.
- TL은 CodeRabbit 리뷰가 제품 결정이나 기술 결정처럼 오인되지 않도록 리뷰 반영 여부를 선별해야 한다.

## 검증 결과

- OPS-004 작업 산출물 검증을 통과했다.
- 커밋 메시지 규칙 검증은 Git for Windows Bash에서 통과했다.
- PowerShell의 `bash` 명령은 WSL의 `C:\Windows\system32\bash.exe`로 해석되어 실행 오류가 발생했다.
- CodeRabbit 핵심 설정값(`ko-KR`, `assertive`, `drafts: false`, `develop`)을 확인했다.
- `main`을 추가 base branch로 중복 설정하지 않았음을 확인했다.
- GitHub Actions, 제품·도메인·UX·아키텍처 문서, Backend, Frontend, DB, Infra 구현을 변경하지 않았음을 확인했다.
- 실제 Secret, token, webhook URL 패턴이 없음을 확인했다.
- YAML 문법 검증은 로컬 Python에 `yaml` 모듈이 없어 실행하지 못했다. 자세한 내용은 `docs/reports/OPS-004/sre-report.md`를 따른다.

## 중단 조건

- CodeRabbit 유료 플랜 설정이 필요한 경우
- GitHub App 설치 권한 또는 저장소 공개 여부 변경이 필요한 경우
- 모든 repository 접근 권한이 필요한 경우
- GitHub Actions 변경이 필요한 경우
- 제품·도메인·UX·아키텍처 정책 변경이 필요한 경우
- Secret 또는 민감정보 노출이 의심되는 경우
