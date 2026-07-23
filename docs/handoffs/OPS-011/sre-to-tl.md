# OPS-011 Platform/SRE → 사용자·Tech Lead 인수인계

## 작업 정보

- 작업 ID: OPS-011
- 작업 등급: 고위험

## 전달 목적

병합된 HTTPS 계약의 실제 DuckDNS·EC2 적용, 갱신과 재부팅 복구 결과를 사용자·Tech Lead에게 전달하고 보류·후속 위험을 구분한다.

## 대상 역할 또는 운영자

- Product Owner이자 Tech Lead인 사용자
- 병합 뒤 DuckDNS·AWS·EC2를 직접 적용하는 운영자

## 입력 문서

- 현재 OPS-011 사용자 승인
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/runbook/OPS-011-production-https.md`
- `docs/reports/OPS-011/sre-report.md`

## 완료된 작업

- 인증서 없는 bootstrap HTTP와 HTTP-01 challenge
- 검증 후 HTTP→HTTPS redirect와 기존 Frontend·Backend HTTPS proxy
- Certbot 최초 발급, dry-run·갱신, 검증 후 reload와 HTTP 복구 script
- `80`·`443`만 공개하는 계약 validator와 shell·Nginx test
- Certbot HTTP-01·후보 SAN 검증 성공 뒤에만 고정되는 mode `600` domain, root 관리 named volume·생성 config·marker와 Secret 비출력 경계
- 실제 DuckDNS·EC2 HTTPS 적용, 인증서 발급과 SAN·최소 잔여 유효기간 검증
- 내부 release smoke와 HTTPS application·승인 hostname redirect
- 외부 PC HTTPS 두 endpoint, HTTP→HTTPS redirect와 브라우저 인증서 확인
- dry-run 무 reload, 실제 갱신 뒤 인증서 재검증·Nginx reload
- 재부팅 뒤 동일 application SHA·세 volume·HTTPS marker·네 container health·HTTPS status 복구

## 관련 파일

- `infra/production/compose.yaml`, Nginx config와 `https.sh`
- `docs/runbook/OPS-011-production-https.md`
- `docs/reports/OPS-011/sre-report.md`

## 소비자 입력

- 사용자가 만든 단일 DuckDNS hostname과 인증서 알림 email
- 기존 두 GHCR repository, root runtime·state 경로
- DuckDNS UI의 DNS 연결과 AWS Security Group `80`·`443` 상태

실제 token, email, 계정 식별자, 공인 IP, 인증서·개인 키와 session 값은 저장소·PR·검증 기록에 입력하지 않는다.

## 적용 순서

1. Runbook의 최신 main·기존 release·DNS·port 중단 gate를 확인한다.
2. `https.sh bootstrap`으로 현재 HTTP와 로컬 challenge 경로를 확인하고 hostname state가 생성되지 않았는지 확인한다.
3. `issue`로 외부 HTTP-01 발급과 후보 SAN 검증을 수행하고, 성공 뒤 mode `600` hostname 승인 state와 HTTPS를 확인한다.
4. 외부 PC에서 HTTPS 두 endpoint, 승인 hostname HTTP redirect, unknown Host 거부와 hostname·만료일을 확인한다.
5. 승인된 test account로 login/logout과 Secure·HttpOnly·SameSite cookie를 확인한다.
6. `renew --dry-run`, 실제 `renew`, 재부팅 복구를 확인한다.

## 중단 조건

- DNS가 현재 EC2를 가리키는지 불명확하거나 `80`·`443` 접근이 불가능함
- 현재 release·MySQL volume·네 health 또는 root state 경계가 불명확함
- token·개인 키·credential·cookie를 출력해야 함
- `3306`·`8080`·`3000` 공개나 volume 삭제가 필요함
- `infra/production` diff gate를 우회해야 함

## 복구

bootstrap은 성공해도 hostname을 승인하지 않는다. Certbot·후보 인증서 검증·domain 후보 cleanup 실패는 승인 domain·marker·생성 config·probe를 남기지 않아 올바른 hostname으로 재시도할 수 있다. HTTPS 전환 실패 뒤에는 bootstrap 복구 성공과 challenge 실패, 복구 자체 실패를 구분한다. 갱신 실패는 Nginx를 reload하지 않으며 current SHA, MySQL·certificate volume은 삭제하지 않는다.
HTTPS 활성화 뒤 일반 deploy·rollback의 TLS·두 endpoint·redirect gate가 실패하면 새 `current-sha`를 기록하지 않고 이전 release를 복구한다.

## 운영 검증 결과

- 사용자·Tech Lead 운영 증거: HTTPS 발급, SAN·최소 잔여 유효기간, 내부 release smoke, HTTPS application과 승인 hostname redirect 통과
- 사용자·Tech Lead 운영 증거: 외부 PC의 HTTPS `/products`·`/api/products`, HTTP→HTTPS redirect와 브라우저 인증서 확인
- 사용자·Tech Lead 운영 증거: 운영 상품 `0`건을 정상 empty state로 확인
- 사용자·Tech Lead 운영 증거: `renew --dry-run`은 reload 없이 통과하고 실제 `renew`는 인증서 재검증과 Nginx reload까지 통과
- 사용자·Tech Lead 운영 증거: 재부팅 뒤 동일 application SHA, MySQL·Let's Encrypt·webroot volume, HTTPS marker, 네 container health와 HTTPS status 복구
- PR #60 Repository Validation: shell·상태기계·Nginx·Compose lifecycle·계약 validator와 Backend·Frontend 회귀 통과

## 보류·미실행 gate와 남은 위험

- login/logout과 `JSESSIONID` 속성은 운영 회원이 `0`건이고 승인된 test account가 없어 실패가 아닌 보류다.
- 외부 unknown Host 거부와 실제 이전 SHA rollback은 미실행이다.
- 자동 갱신 schedule, certificate backup과 backup·restore는 미완료 후속 작업이다.
- 단일 EC2·EBS 장애와 이후 DNS·CA·clock·certificate 만료 위험은 이번 단일 시점 성공 결과로 제거되지 않는다.

## 미결정 사항 또는 승인 필요 항목

승인된 test account를 준비한 session 검증은 별도 사용자 결정이 필요하다. 외부 unknown Host 검증, 자동 갱신 schedule, certificate backup, 실제 application rollback과 backup·restore는 후속 작업으로 분리한다.

## QA 필요 여부

별도 QA 문서는 생략한다. PR #60 Repository Validation을 독립 자동 검증으로 사용했고 사용자·Tech Lead가 실제 외부 TLS gate를 수행했다. session gate는 보류 상태다.
