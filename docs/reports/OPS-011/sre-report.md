# OPS-011 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-011
- 작업 등급: 고위험
- 역할: Platform/SRE

## 작업 목적

OPS-010 단일 release와 MySQL volume을 유지하면서 인증서 없는 HTTP bootstrap, DuckDNS·Let's Encrypt HTTP-01 발급, HTTPS 전환·갱신·복구 경로를 제공한다.

## 입력 문서

- 현재 OPS-011 사용자 승인
- 루트·`infra/AGENTS.md`, Platform/SRE 역할 문서와 Skill
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/runbook/lean-harness.md`

## 명시적 승인 근거 (고위험 필수)

사용자가 무료 DuckDNS 단일 hostname, Let's Encrypt HTTP-01, 공식 Certbot image, `80` 유지와 `443` 추가, 2단계 bootstrap, 발급·갱신·복구 script와 고위험 문서를 승인했다. 실제 AWS·DNS·인증서 적용, token 입력, HSTS·DNS-01·자동 배포·backup은 제외했다.

## 변경 범위

- bootstrap·HTTPS Nginx와 Compose의 `80`·`443`, 내부 `8081` health 경계
- linux/amd64 digest 고정 Certbot 발급·dry-run·갱신·reload·HTTP 복구 script
- root 관리 challenge·certificate named volume과 mode `600` HTTPS marker
- 계약 validator, shell 상태기계·Nginx config test와 Repository Validation
- Runbook과 사용자/Tech Lead 인수인계

## 변경하지 않은 범위

AWS·DuckDNS·인증서 실제 변경, Secret·token, application/API/DB schema, 자동 갱신 schedule, backup·restore와 OPS-010 application rollback 실행은 변경하지 않았다.

## 주요 결과

- 인증서가 없으면 기존 HTTP proxy와 challenge 경로가 함께 동작한다.
- 검증된 인증서가 있을 때만 HTTP challenge 예외 외 요청을 HTTPS로 redirect한다.
- HTTPS `/products`, `/api/**`는 기존 upstream을 유지하고 `X-Forwarded-Proto=https`를 전달한다.
- 공개 port는 `80`, `443`뿐이며 Backend의 `SESSION_COOKIE_SECURE=true`를 유지한다.
- 발급·전환 실패는 bootstrap HTTP로 복귀하고 갱신 실패는 Nginx reload를 수행하지 않는다. 어느 경로도 현재 SHA, MySQL·certificate volume을 삭제하지 않는다.

## API·DB·보안·성능 영향

API와 DB 변경은 없다. 인증서·개인 키는 root 관리 Docker volume, challenge는 별도 volume에 두고 Nginx에는 read-only mount한다. email은 실행 인자와 mode `600` 임시 config로만 받고 로그 directory는 tmpfs다. token은 script 입력으로도 받지 않는다. Nginx 자원 상한은 유지되어 별도 성능 개선을 주장하지 않는다.

## 적용 전 검증 (고위험 필수)

PR #59 병합과 최신 `origin/main`, 깨끗한 새 `ops/sre` branch를 확인했다. OPS-010의 동일 SHA, 고정 MySQL volume, `Secure` cookie, 내부 port 비공개, deploy·rollback fail-close 계약을 확인했다.

## 적용 후 검증 (고위험 필수)

Shell syntax, Compose·Nginx 정적 계약, 발급 실패·성공, dry-run 무 reload, 갱신 실패·reload 실패, HTTP 복구와 state·volume 보존 상태기계 test를 수행한다. 실제 Docker Nginx `-t`, Backend·Frontend 회귀와 전체 Repository Validation 결과는 PR check를 권위 원본으로 확인한다.

## 독립 검증 (고위험 필수)

구현과 분리된 `validate-production-contracts.py`가 공개 port, Secure cookie, image digest, named volume, challenge·redirect·certificate path와 삭제 금지를 검사한다. 독립 Ubuntu runner가 shell test, Nginx `-t`, Backend·Frontend 회귀를 실행한다. 실제 외부 TLS와 session은 사용자/Tech Lead Runbook 실행이 필요하다.

## 실행한 검증

| 검증 | 결과 |
| --- | --- |
| production shell files `bash -n` | 통과 |
| `test-production-scripts.sh` | 통과 |
| `validate-production-contracts.py` | 통과 |
| OPS-011 고위험 task artifact validator | 통과 |
| commit message validator | 통과 |
| `git diff --check` | 통과 |
| Frontend lint·build | 통과 |
| 실제 Docker Nginx `-t` | local Docker engine 미실행으로 미수행, Repository Validation에 포함 |
| Backend test·build | local Java 17로 Java 25 요구를 충족하지 못해 미수행, Repository Validation에 포함 |
| Repository Validation | Draft PR 생성 뒤 GitHub를 권위 원본으로 확인 |

## 실행하지 못한 검증과 이유

실제 DuckDNS 생성·DNS 전파, AWS `443` 변경, Let's Encrypt 발급·갱신, 외부 HTTPS smoke, certificate hostname·만료일, 재부팅과 session login/logout은 승인된 사용자 작업이므로 미실행이다.

## QA 필요 여부

별도 QA 문서는 생략한다. 제품 동작 변경이 없고 독립 Repository Validation과 사용자/Tech Lead의 실제 외부 TLS gate를 사용한다.

## QA 문서 경로 또는 생략 사유

제품 기능·API·DB 변경이 없고 독립 CI와 실제 운영자 gate를 사용하므로 별도 QA 문서를 만들지 않는다.

## 적용 방법

`docs/runbook/OPS-011-production-https.md`의 적용 전 gate → bootstrap → 발급 → HTTPS·redirect·certificate·session → dry-run·갱신 → 재부팅 복구 순서를 사용한다.

## 복구·롤백 증거 (고위험 필수)

상태기계 test는 발급 실패 시 marker 부재, dry-run·갱신 실패 시 무 reload, reload 실패 시 오류 반환, disable 뒤 현재 SHA·certificate·MySQL volume 보존을 확인한다. application rollback script와 volume 삭제 금지는 유지한다. 실제 이전 SHA rollback은 OPS-010의 미충족 후속 gate다.

## 위험과 제한

자동 갱신 schedule과 certificate backup은 없다. 단일 EC2·EBS 장애는 복구하지 못한다. DNS 전파, CA rate limit, clock, inbound `80`·`443`와 실제 session 동작은 로컬 test로 보장할 수 없다. TLS contract가 포함된 새 application SHA의 첫 deploy가 기존 `infra/production` diff gate에서 중단되면 우회하지 않고 별도 rebaseline 승인이 필요하다.

## 다음 작업

병합 뒤 사용자/Tech Lead가 실제 Runbook을 실행해 미실행 gate를 확인한다. 실제 이전 SHA rollback, 자동 갱신, backup·restore는 별도 승인 작업으로 유지한다.

## Git 결과

- branch: `ops/sre`
- commit 제목: `feat(sre): OPS-011 HTTPS 운영 기반 구성`
- 정확한 commit·push head는 GitHub를 권위 원본으로 확인한다.

## PR 결과

main 대상 Draft PR이며 동적 head·review·check 상태는 GitHub를 권위 원본으로 확인한다. 자동 병합하지 않는다.
