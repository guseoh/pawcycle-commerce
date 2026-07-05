# PS-003 Product Planner 작업 보고서

## 작업 정보

- 작업 ID: `PS-003`
- 역할: Product Planner
- 기준 브랜치: `main`
- 작업 브랜치: `spec/po`
- 원격 저장소: `https://github.com/guseoh/pawcycle-commerce.git`
- 자동 병합: 하지 않음

## 작업 목적

UX-001에서 열린 상태로 남은 네 가지 제품 정책을 Approved Product Decision으로 저장소에 기록한다.

승인된 결정을 PS-002 요구사항과 연결하고, 후속 UX-002, ARCH-001, API-001, 인증 설계와 QA가 추측 없이 사용할 수 있는 제품 결정 문서와 인수인계를 작성한다.

## 승인된 입력

- PR #11 `docs(ux): 1차 MVP 구독 사용자 흐름 설계`: `main` 병합 완료
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/handoffs/UX-001/ux-to-tl.md`
- 사용자가 승인한 네 가지 Product Decision:
  - 비회원 상품 목록·상세 접근 허용
  - 로그인 후 안전한 서비스 내부 GET 경로 복귀
  - 구독 생성 성공 후 생성된 구독 상세 화면으로 이동
  - 생성 전 정확한 다음 주문 예정일 미표시

## 브랜치 준비와 백업

- 시작 시 로컬 `spec/po`에는 기존 커밋 `352427d`가 남아 있었다.
- 기존 커밋 메시지: `docs(product): 제품 비전과 MVP 범위 정리`
- 기존 로컬 `spec/po`의 변경 파일:
  - `docs/handoffs/PS-001/po-to-be.md`
  - `docs/product/mvp-scope.md`
  - `docs/product/product-decisions/PS-001-mvp-policy.md`
  - `docs/product/service-vision.md`
  - `docs/product/target-users.md`
  - `docs/product/user-journeys.md`
  - `docs/reports/PS-001/po-report.md`
- 삭제 전 백업 브랜치 `backup/spec-po-before-PS-003`를 생성했다.
- 백업 브랜치 SHA: `352427def63b747854908d8da22ede9a64ccdda5`
- patch 백업 파일: `.git/spec-po-before-PS-003.patch`
- 백업 후 기존 로컬 `spec/po`를 삭제했다.
- 원격 브랜치는 삭제하지 않았다.
- force push, reset, rebase, history rewrite는 하지 않았다.
- 새 `spec/po`는 최신 `main`에서 생성했다.

## 변경 범위

- `docs/product/PS-003-ux-product-decisions.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/reports/PS-003/po-report.md`
- `docs/handoffs/PS-003/po-to-ux.md`

## 변경하지 않은 범위

- `docs/design/**` 직접 변경
- UX-002 수행
- DOMAIN-001 변경
- API 요청·응답 JSON 확정
- HTTP 상태와 오류 코드 확정
- URL과 라우트 문자열 확정
- Spring Security 구현 방식
- 로그인 저장 요청 구현 방식
- Open Redirect 방지 코드 구현
- 프론트엔드 라우팅 구현
- 백엔드·프론트엔드 코드
- 데이터베이스 설계
- 신규 의존성
- PR #11 변경 또는 병합
- PS-003 PR 자동 병합

## 변경 내용

- PS-003 승인 Product Decision 문서를 작성했다.
- PS-002 요구사항에 비회원 공개 상품 탐색 정책을 반영했다.
- PS-002 요구사항에 로그인 성공 후 안전한 내부 GET 경로 복귀 정책을 반영했다.
- PS-002 요구사항에 구독 생성 성공 후 생성된 구독 상세 이동과 성공 안내를 반영했다.
- PS-002 요구사항에 생성 전 다음 주문 예정일 미표시와 생성 후 서버 확정일 표시 정책을 반영했다.
- 기존 요구사항 ID와 기존 인수 조건 번호는 변경하거나 재사용하지 않았다.
- 필요한 인수 조건은 기존 마지막 번호 뒤에 추가했다.
- PS-002 최소 추적성 표의 관련 설계 항목을 `DOMAIN-001`, `UX-001`, `PS-003`, `API-001 예정`, `인증 설계 예정` 기준으로 갱신했다.
- PS-003 PO → UX 인수인계를 작성했다.

## 요구사항 반영

| 요구사항 | 반영 내용 |
| --- | --- |
| `REQ-PRODUCT-001` | 비회원과 로그인 회원 모두 상품 목록 조회 가능, 상품 목록은 공개 기능 |
| `REQ-PRODUCT-002` | 비회원과 로그인 회원 모두 상품 상세와 SKU 정보 조회 가능, 구독 가능 여부 확인은 공개 탐색 범위 |
| `REQ-SUB-001` | 구독 생성 성공 후 생성된 구독 상세 이동, 성공 안내, 구독 ID와 다음 주문 예정일 확인, 영구 성공 페이지 제외 |
| `REQ-SUB-004` | 생성 전 정확한 예정일 미표시, 생성 전 배송 주기와 안내만 표시, 생성 후 서버 확정일 표시 |
| `REQ-AUTH-001` | 상품 목록·상세 인증 없이 접근 가능, 보호 기능 인증 필요, 로그인 후 안전한 내부 GET 경로 복귀와 fallback 정책 추가 |

## Product Decision

- 비회원 상품 목록·상세 접근 허용
- 로그인 후 안전한 서비스 내부 GET 경로 복귀
- 구독 생성 성공 후 생성된 구독 상세 화면으로 이동
- 생성 전 정확한 다음 주문 예정일 미표시

## Deferred Technical Decision

- 구체 URI와 라우트 문자열
- HTTP 상태, 오류 코드와 오류 응답 JSON
- API 요청·응답 JSON 구조
- 구독 생성 성공 응답의 정확한 구조
- Spring Security 구현 방식
- 로그인 복귀 경로 저장 방식
- 안전한 내부 GET 경로 검증 방식
- Open Redirect 방지 코드 구현
- 프론트엔드 라우팅 구현 방식

## 검증 결과

| 검증 | 명령 | 결과 |
| --- | --- | --- |
| 저장소 루트 확인 | `git rev-parse --show-toplevel` | 통과 |
| 원격 저장소 확인 | `git remote -v` | 통과 |
| PR #11 병합 확인 | `gh pr view 11 --json ...` | 통과. `MERGED` |
| 열린 `spec/po` PR 확인 | `gh pr list --state open --head spec/po` | 통과. 없음 |
| 기존 로컬 `spec/po` 기록 | `git log origin/main..spec/po`, `git diff --name-status origin/main...spec/po` | 통과 |
| 백업 브랜치 생성 | `git branch backup/spec-po-before-PS-003 spec/po` | 통과 |
| patch 백업 생성 | `git diff origin/main...spec/po > .git/spec-po-before-PS-003.patch` | 통과 |
| 기존 로컬 `spec/po` 삭제 | `git branch -D spec/po` | 통과 |
| 최신 `main` 갱신 | `git pull --ff-only origin main` | 통과 |
| 새 `spec/po` 생성 | `git switch -c spec/po` | 통과 |
| 공백 오류 확인 | `git diff --check` | 통과 |
| 변경 통계 확인 | `git diff --stat` | 통과 |
| 변경 파일 확인 | `git diff --name-status` | 통과 |
| PS-003 산출물 확인 | `Write-Output 'PS-003' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| 커밋 메시지 확인 | `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "docs(product): UX 제품 결정 확정"` | 통과 |
| PR 본문 인코딩 확인 | `py -3 scripts/validate-pr-body-encoding.py --body-file ".git\PS-003-pr-body.md"` | 통과 |
| 추가 문서 점검 | UTF-8 strict 읽기, 요구사항 ID 유지, AC 정의 중복 없음, 금지 경로 변경 없음, 민감정보 패턴 없음 | 통과 |

## 위험과 제한

- API 계약과 HTTP 상태, 오류 응답 JSON은 확정하지 않았다.
- 라우트 문자열과 인증 구현 방식은 확정하지 않았다.
- 로그인 복귀 경로 저장 방식과 Open Redirect 방지 코드는 후속 기술 설계에서 결정해야 한다.
- 입력 폼 자동 복원과 이전 POST 자동 재실행은 첫 번째 MVP에서 제외했다.
- 생성 전에는 정확한 다음 주문 예정일 날짜를 볼 수 없다.
- 기존 로컬 `spec/po` 변경은 백업만 남겼고 PS-003 변경에 섞지 않았다.
- Secret, 토큰, Webhook URL 등 민감정보는 추가하지 않았다.

## Git 결과

- 커밋 메시지: `docs(product): UX 제품 결정 확정`
- push 대상: `origin/spec/po`
- 최종 커밋과 push 결과는 PR 생성 후 완료 보고에서 확정한다.

## PR 결과

- PR 제목: `docs(product): UX 제품 결정 확정`
- PR 방향: `spec/po` → `main`
- PR 상태: Draft 예정
- 자동 병합: 하지 않음
- PR 생성 결과는 완료 보고에서 확정한다.
