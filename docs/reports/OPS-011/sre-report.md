# OPS-011 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-011
- 작업 등급: 고위험
- 역할: Platform/SRE

## 작업 목적

OPS-010 단일 release와 MySQL volume을 유지하면서 인증서 없는 HTTP bootstrap, DuckDNS·Let's Encrypt HTTP-01 발급, HTTPS 전환·갱신·복구 경로를 제공하고 실제 운영 검증 결과를 기록한다.

## 입력 문서

- 현재 OPS-011 사용자 승인
- 현재 OPS-011 PR #60 보안·운영 결함 수정 승인
- 루트·`infra/AGENTS.md`, Platform/SRE 역할 문서와 Skill
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/runbook/lean-harness.md`

## 명시적 승인 근거 (고위험 필수)

사용자가 무료 DuckDNS 단일 hostname, Let's Encrypt HTTP-01, 공식 Certbot image, `80` 유지와 `443` 추가, 2단계 bootstrap, 발급·갱신·복구 script와 고위험 문서를 승인했다. PR 후속 승인으로 hostname state·unknown Host 차단, deploy·rollback HTTPS gate, 공개 인증서 API와 실패 복구 보완을 승인했다. 병합 뒤 사용자·Tech Lead가 실제 DuckDNS·EC2 HTTPS 적용, 발급·갱신과 재부팅 복구를 수행하고 비민감 결과의 문서 반영을 승인했다. token 입력, HSTS·DNS-01·자동 갱신 schedule과 backup은 제외했다.

## 변경 범위

- bootstrap·HTTPS Nginx와 Compose의 `80`·`443`, 내부 `8081` health 경계
- linux/amd64 digest 고정 Certbot 발급·dry-run·갱신·reload·HTTP 복구 script
- root 관리 challenge·certificate named volume, mode `600` domain·Nginx config·HTTPS marker
- 계약 validator, shell 상태기계·Nginx config test와 Repository Validation
- Runbook과 사용자/Tech Lead 인수인계

## 변경하지 않은 범위

이번 저장소 변경에서는 AWS·DuckDNS·인증서, Secret·token, application/API/DB schema와 Runbook을 변경하지 않았다. 자동 갱신 schedule, backup·restore와 실제 이전 SHA application rollback 실행도 범위에서 제외했다.

## 주요 결과

- 인증서가 없으면 기존 HTTP proxy와 challenge 경로가 함께 동작한다.
- bootstrap의 로컬 challenge 검사는 Nginx·webroot만 확인하고 hostname을 승인하지 않는다.
- 최초 hostname은 Certbot HTTP-01 발급과 후보 SAN·최소 유효기간 검증 성공 뒤에만 mode `600` 승인 state로 고정된다.
- 검증된 인증서가 있을 때만 HTTP challenge 예외 외 요청을 HTTPS로 redirect한다.
- 승인 DuckDNS hostname만 같은 hostname으로 redirect하고 unknown Host는 연결을 종료한다.
- HTTPS `/products`, `/api/**`는 기존 upstream을 유지하고 `X-Forwarded-Proto=https`를 전달한다.
- 공개 port는 `80`, `443`뿐이며 Backend의 `SESSION_COOKIE_SECURE=true`를 유지한다.
- 일반 deploy·rollback도 HTTPS certificate·두 endpoint·redirect gate 통과 전에는 `current-sha`를 기록하지 않는다.
- 발급·전환 실패는 challenge까지 검증한 bootstrap HTTP로 복귀하고 갱신 실패는 Nginx reload를 수행하지 않는다. 어느 경로도 현재 SHA, MySQL·certificate volume을 삭제하지 않는다.

## API·DB·보안·성능 영향

API와 DB 변경은 없다. 인증서·개인 키는 root 관리 Docker volume, challenge는 별도 volume에 두고 Nginx에는 read-only mount한다. 미승인 hostname은 Certbot 발급과 후보 인증서 검증 전까지 실행 메모리에만 둔다. 검증 성공 뒤 승인 hostname과 생성 Nginx config를 root state의 일반 파일·mode `600`으로 제한한다. 인증서는 pinned Certbot image의 공개 `cryptography.x509` API로 정확한 후보 SAN과 최소 잔여 유효기간을 검사한다. email은 실행 인자와 mode `600` 임시 config로만 받고 로그 directory는 tmpfs다. token은 script 입력으로도 받지 않는다.

## 적용 전 검증 (고위험 필수)

PR #59 병합과 최신 `origin/main`, 깨끗한 새 `ops/sre` branch를 확인했다. OPS-010의 동일 SHA, 고정 MySQL volume, `Secure` cookie, 내부 port 비공개, deploy·rollback fail-close 계약을 확인했다.

## 적용 후 검증 (고위험 필수)

병합 전 Shell syntax, Compose·Nginx 정적 계약, hostname 승인 경계, 실패 cleanup·복구, dry-run 무 reload, 갱신 실패·reload 실패와 state·volume 보존 상태기계 test를 수행했다. 병합 뒤 사용자·Tech Lead 운영 증거로 실제 HTTPS 발급과 SAN·최소 잔여 유효기간, 내부·외부 smoke, 실제 갱신과 재부팅 복구를 확인했다. 운영 회원이 `0`건이고 승인된 test account가 없어 session 검증은 보류했다.

## 독립 검증 (고위험 필수)

구현과 분리된 `validate-production-contracts.py`가 공개 port, Secure cookie, image digest, named volume, challenge·redirect·certificate path와 삭제 금지를 검사한다. PR #60의 독립 Ubuntu Repository Validation에서 shell test, Nginx `-t`, Backend·Frontend 회귀가 통과했다. 실제 외부 TLS는 사용자·Tech Lead가 별도 운영환경에서 확인했으며 session은 보류 상태다.

## 실행한 검증

| 검증 | 결과 | 근거 |
| --- | --- | --- |
| production shell·상태기계·Nginx·Compose lifecycle·계약 validator | 통과 | PR #60 Repository Validation |
| Backend·Frontend 회귀 | 통과 | PR #60 Repository Validation |
| OPS-011 고위험 task artifact·commit message validator | 통과 | PR #60 Repository Validation |
| DuckDNS·EC2 HTTPS 적용과 Let's Encrypt 발급 | 통과 | 사용자·Tech Lead 운영 증거 |
| 인증서 SAN·최소 잔여 유효기간 | 통과 | 사용자·Tech Lead 운영 증거 |
| 내부 release smoke, HTTPS application과 승인 hostname redirect | 통과 | 사용자·Tech Lead 운영 증거 |
| 외부 PC HTTPS `/products`·`/api/products`, HTTP→HTTPS redirect와 브라우저 인증서 | 통과 | 사용자·Tech Lead 운영 증거 |
| 운영 상품 empty state | 상품 `0`건을 정상 empty state로 확인 | 사용자·Tech Lead 운영 증거 |
| `renew --dry-run` | Nginx reload 없이 통과 | 사용자·Tech Lead 운영 증거 |
| 실제 `renew` | 인증서 재검증과 Nginx reload까지 통과 | 사용자·Tech Lead 운영 증거 |
| 재부팅 복구 | 동일 application SHA·MySQL·Let's Encrypt·webroot volume, HTTPS marker, 네 container health와 HTTPS status 복구 | 사용자·Tech Lead 운영 증거 |
| 외부 unknown Host 거부 | 미실행, Repository Validation의 계약·Nginx 검증만 통과 | 사용자 제공 미실행 범위 |
| login/logout과 `JSESSIONID` 속성 | 보류, 운영 회원 `0`건이며 승인된 test account 없음 | 사용자·Tech Lead 운영 증거 |

## 보류·미실행 검증과 이유

login/logout과 `JSESSIONID` 보안 속성은 운영 회원이 `0`건이고 승인된 test account가 없어 실패가 아닌 보류로 남겼다. 외부 unknown Host 거부와 실제 이전 SHA rollback은 수행하지 않았다. 사용자 제공 증거 밖의 항목은 운영 성공으로 추정하지 않는다.

## QA 필요 여부

별도 QA 문서는 생략한다. 제품 동작 변경이 없고 독립 Repository Validation과 사용자/Tech Lead의 실제 외부 TLS gate를 사용한다.

## QA 문서 경로 또는 생략 사유

제품 기능·API·DB 변경이 없고 독립 CI와 실제 운영자 gate를 사용하므로 별도 QA 문서를 만들지 않는다.

## 적용 방법

`docs/runbook/OPS-011-production-https.md`의 적용 전 gate → bootstrap → 발급 → HTTPS·redirect·certificate·session → dry-run·갱신 → 재부팅 복구 순서를 사용한다.

## AI 리뷰 반영 여부

유효한 hostname 승인 경계·volume 문구, unknown Host, 공개 인증서 API, probe cleanup, bootstrap 복구와 활성화 실패 test를 반영했다. 공식 registry manifest에서 기존 Certbot linux/amd64 digest가 일치함을 확인해 pin은 변경하지 않았다.

## AI 리뷰 미반영 항목과 이유

동적 PR head·run/check ID 기록은 GitHub를 권위 원본으로 두라는 사용자 결정과 충돌해 미반영했다. Certbot config 중복과 Nginx `8081` snippet 추출은 현재 보안·복구 수정에 직접 필요하지 않은 제외 범위라 변경하지 않았다.

## 최종 병합 위험 점검

전체 PR diff에서 hostname 외부 검증·SAN 승인 순서, unknown Host·TLS, Secure cookie, deploy·rollback의 HTTPS gate와 `current-sha` 기록 순서, MySQL·certificate volume 보존, Secret 비노출을 다시 확인했다. 병합을 막는 P0·P1 결함은 발견되지 않았다. Certbot config·Nginx `8081` 중복과 인증서 경로 추상화는 P2 이하 비차단 유지보수 범위로 남긴다.

## 복구·롤백 증거 (고위험 필수)

Repository Validation의 상태기계 test는 bootstrap·발급·전환·갱신 실패 cleanup, 이전 SHA 복귀와 MySQL·certificate volume 보존 계약을 확인했다. 실제 재부팅 뒤에는 application SHA가 바뀌지 않았고 동일 MySQL·Let's Encrypt·webroot volume, HTTPS marker, 네 container health와 HTTPS status가 복구됐다. 실제 이전 SHA rollback과 backup·restore는 수행하지 않았다.

## 위험과 제한

자동 갱신 schedule과 certificate backup은 없고 backup·restore도 검증하지 않았다. 실제 이전 SHA rollback, 외부 unknown Host 거부와 session 보안 검증은 미실행이다. 단일 EC2·EBS 장애, 이후 DNS·CA·clock·certificate 만료 위험은 이번 단일 시점 성공 결과만으로 제거되지 않는다.

## 다음 작업

승인된 test account가 준비되면 login/logout과 `JSESSIONID` 속성을 검증한다. 외부 unknown Host 거부, 실제 이전 SHA rollback, 자동 갱신 schedule, certificate backup과 backup·restore는 별도 승인 작업으로 유지한다.

## Git 결과

- branch: `ops/sre`
- commit 제목: `feat(sre): OPS-011 HTTPS 운영 기반 구성`
- 후속 commit 제목: `fix(sre): OPS-011 HTTPS release 검증 보완`
- 최종 보완 commit 제목: `fix(sre): OPS-011 HTTPS 외부 검증 승인 경계 보완`
- 운영 검증 반영 commit 제목: `docs(sre): OPS-011 운영 검증 결과 반영`
- 정확한 commit·push 상태는 GitHub를 권위 원본으로 확인한다.

## PR 결과

HTTPS 구현 PR `#60`은 main에 병합됐다. 운영 검증 문서 PR의 동적 head·review·check·Draft/Ready 상태는 GitHub를 권위 원본으로 확인한다. 자동 병합하지 않는다.
