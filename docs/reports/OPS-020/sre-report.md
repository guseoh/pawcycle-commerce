# OPS-020 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-020
- 작업 등급: 고위험
- 역할: Platform/SRE
- 상태: 저장소 준비 완료, 실제 Production 실행 미완료

## 작업 목적

OPS-019 one-shot Backend 명령을 root 전용 TTY 입력과 immutable one-shot Container로 안전하게 실행할 저장소 기반을 준비한다.

## 입력 문서

`docs/handoffs/OPS-019/be-to-sre.md`, 병합된 OPS-019 Bootstrap 구현, 기존 Production release/runtime/state와 Docker 보안 계약을 사용했다.

## 승인 입력

사용자는 TTY email·hidden password를 stdin 두 줄로만 전달하고, 현재 승인 SHA의 Backend digest image를 Production data network에서 one-shot 실행하는 저장소 준비만 승인했다. 실제 Production DB 연결·회원 생성과 OPS-018은 승인하지 않았다.

## 명시적 승인 근거 (고위험 필수)

Wrapper, fake Docker·PTY 테스트, 실제 Production 이름을 쓰지 않는 격리 MySQL 8.4·Backend lifecycle 테스트, Runbook과 운영 인수인계 작성이 명시적으로 승인됐다.

## 변경 범위

- root·TTY·runtime/state·current SHA·immutable image·MySQL health preflight
- echo 보호 credential 입력과 Bash builtin stdin pipe
- hardened one-shot Docker 실행·signal cleanup·exact PASS 판정
- fake Docker/PTY와 격리 Docker lifecycle 회귀 검증
- production contract validator·Repository Validation 연결
- Runbook과 SRE→Tech Lead/운영자 인수인계

## 변경하지 않은 범위

Backend·API·SecurityConfig·Flyway migration·schema·Compose service를 변경하지 않았다. Production·외부 network·DB·running service·volume에는 접근하지 않았고 회원 생성·삭제·password 변경과 OPS-018을 실행하지 않았다.

## 주요 결과

Wrapper는 root와 실제 `/dev/tty`를 Docker 접근 전에 요구한다. 현재 Production SHA와 OPS-019 source, root 전용 runtime/state 파일, Backend SHA tag·OCI revision·registry digest·non-root user, MySQL running/healthy와 internal data network가 모두 일치한 뒤에만 credential을 읽는다.

Password echo는 정상·오류·INT·TERM에서 복구한다. Credential은 unexported shell variable에서 Bash builtin `printf`로 coprocess stdin pipe에만 기록되고 즉시 비운다. Container argv·env·파일·log에는 포함하지 않는다.

One-shot Container는 digest reference, no persistent log, no port·restart·volume, read-only·tmpfs·non-root·no-new-privileges·capability drop·resource limit을 사용한다. 성공 stdout은 승인된 PASS 한 줄만 허용한다.

## 핵심 결정과 대안

현재 배포 SHA와 다른 Backend image를 DB에 직접 연결하는 대안은 schema/runtime 호환성이 불명확해 제외했다. Compose service 추가는 persistent 운영 표면을 만들므로 standalone `docker run --rm -i`를 선택했다. Credential tempfile·env·argv와 Docker secret mount는 저장 흔적 또는 새 운영 의존성을 만들므로 stdin pipe만 사용했다.

Lifecycle은 임의 label·이름의 internal network, temporary volume, MySQL 8.4와 현재 branch Backend image만 사용한다. 회원 생성은 Backend command로만 수행하고 SQL은 결과 확인에만 사용한다.

## 변경 파일

- `infra/production/create-production-auth-smoke-member.sh`
- `infra/production/test-create-production-auth-smoke-member.py`
- `infra/production/test-create-production-auth-smoke-member-lifecycle.sh`
- `infra/production/validate-production-contracts.py`
- `.github/workflows/validate-conventions.yml`
- `docs/runbook/OPS-020-production-auth-smoke-member.md`
- `docs/reports/OPS-020/sre-report.md`
- `docs/handoffs/OPS-020/sre-to-tl.md`

## API·DB 영향

HTTP API와 schema 계약 변경은 없다. 실제 후속 성공 실행만 기존 `members` row 한 건을 추가한다. 테스트는 격리 DB에서 SELECT 검증만 사용하며 상품·SKU·구독 데이터는 만들지 않는다.

## 보안·운영 영향

Credential과 DB/image 식별 오류를 출력하지 않는다. 현재 running service와 Production volume/network에는 mutation 명령을 호출하지 않는다. 성공 회원 cleanup 기능은 승인 결정에 따라 제공하지 않는다.

## 실행한 검증

- Bash·Python 문법 검사
- Fake Docker·PTY 성공·Docker 실패·TERM echo 복구 계약
- 격리 MySQL 8.4·Backend image 성공·중복·입력 실패 lifecycle
- Production contract validator
- OPS-020 고위험 task artifact validator
- Markdown UTF-8 strict decode, commit 제목 규칙과 `git diff --check`
- Repository Validation 전체 결과는 GitHub Checks를 권위 원본으로 확인

## 적용 전 검증 (고위험 필수)

OPS-019 병합, 최신 main, clean worktree, 열린 PR 없음과 기존 `ops/sre`의 PR #69 squash 병합 blob 일치를 확인한 뒤 최신 main에서 새 `ops/sre`를 만들었다. Production에는 접근하지 않았다.

## 적용 후 검증 (고위험 필수)

Fake Docker/PTY는 credential 비노출, exact Docker options, echo 복구와 cleanup을 검증한다. 격리 lifecycle은 한 건 생성, duplicate 기존 row/hash 불변, 입력 실패, one-shot 제거, 회원 외 데이터 0건과 schema/Flyway fingerprint 불변을 검증한다.

## 독립 검증 (고위험 필수)

GitHub Repository Validation의 Linux·Docker·Java 25 환경에서 기존 검증을 유지한 채 OPS-020 테스트를 실행한다. 동적 run 번호·SHA는 문서에 고정하지 않는다.

## 실행하지 못한 검증과 이유

로컬 Windows에서는 Docker Desktop Linux engine pipe가 없어 fake root PTY와 격리 Docker lifecycle을 실행할 수 없었다. 새 dependency나 환경 우회를 추가하지 않고 GitHub Repository Validation의 Linux·Docker 환경에서 실행한다. 실제 Production DB 연결·회원 생성, 운영 container 실행과 OPS-018은 별도 고위험 승인 대상이라 실행하지 않았다.

## QA 필요 여부

별도 QA 문서는 생략한다. 자동 격리 lifecycle과 Tech Lead/운영자 검토가 이번 저장소 준비의 독립 소비자 검증이다.

## QA 문서 경로 또는 생략 사유

공개 API 동작 변경이 없고 실제 운영 검증은 후속 승인 작업으로 분리돼 있다.

## AI 리뷰 반영 여부

PR 생성 후 CodeRabbit·Codex Review를 현재 코드와 승인 계약에 대조해 유효 지적만 반영한다.

## AI 리뷰 미반영 항목과 이유

미반영 항목은 PR에 근거와 함께 기록한다.

## 적용 방법

`docs/runbook/OPS-020-production-auth-smoke-member.md`를 따른다. 실제 실행 전 별도 고위험 사용자 승인이 필요하다.

## 복구·롤백 증거 (고위험 필수)

Wrapper 실패는 Backend transaction rollback과 task-owned Container cleanup을 사용한다. 테스트 resource는 label과 임의 이름으로만 정리한다. 성공 Production 회원은 자동 삭제하지 않는다. 저장소 변경은 revert PR로 복구한다.

## 위험과 제한

실제 production image/runtime/container 경로와 운영 credential은 미검증이다. Docker daemon 장애 시 회원 생성 성공 여부가 불명확할 수 있으므로 PASS가 없으면 재실행하지 않고 에스컬레이션해야 한다.

## 다음 작업

사용자/Tech Lead가 diff·CI·AI review를 확인한 뒤 병합 여부를 결정한다. 별도 고위험 승인 후 운영자가 회원을 한 번 생성하고, 그 credential로 OPS-018을 수행한다.

## Git 결과

최종 commit·push 상태는 Git을 권위 원본으로 확인한다.

## PR 결과

`ops/sre`에서 `main` 대상 Draft PR을 생성하고 GitHub를 권위 원본으로 확인한다. 자동 병합하지 않는다.
