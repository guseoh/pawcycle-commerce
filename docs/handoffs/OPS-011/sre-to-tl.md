# OPS-011 Platform/SRE → 사용자·Tech Lead 인수인계

## 작업 정보

- 작업 ID: OPS-011
- 작업 등급: 고위험

## 전달 목적

병합된 HTTPS 계약을 실제 DuckDNS·EC2에 적용하고 외부 TLS, 재부팅과 session 보안을 검증할 운영자에게 전달한다.

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
- root 관리 named volume, mode `600` domain·생성 config·marker와 Secret 비출력 경계

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
2. `https.sh bootstrap`으로 현재 HTTP와 challenge 경로를 확인한다.
3. `issue`로 발급하고 certificate 검증 뒤 HTTPS를 활성화한다.
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

발급 실패는 bootstrap HTTP를 유지한다. 갱신 실패는 Nginx를 reload하지 않는다. HTTPS 기동이 복구되지 않으면 Runbook의 `https.sh disable`로 HTTP bootstrap만 복원하며 current SHA, MySQL·certificate volume은 삭제하지 않는다.
HTTPS 활성화 뒤 일반 deploy·rollback의 TLS·두 endpoint·redirect gate가 실패하면 새 `current-sha`를 기록하지 않고 이전 release를 복구한다.

## 소비자 검증 포인트

- 공개 listener가 `80`, `443`뿐인가?
- HTTP-01 외 HTTP가 정확한 HTTPS hostname·path로 `301`되는가?
- 알 수 없는 Host가 외부 domain으로 redirect되지 않고 거부되는가?
- HTTPS `/products`, `/api/products`와 certificate hostname·만료일이 유효한가?
- login/logout과 `JSESSIONID` 보안 속성이 유지되는가?
- dry-run은 reload하지 않고 실제 갱신 성공 뒤에만 reload하는가?
- 재부팅 뒤 root 임시 SHA 파일 `cmp`와 삭제, 동일 MySQL·certificate volume, health와 smoke가 복구되는가?

## 미실행 gate와 남은 위험

AWS·DuckDNS·Let's Encrypt, 외부 HTTPS, 재부팅과 session 검증은 아직 미실행이다. 자동 갱신 schedule, certificate backup, 실제 이전 SHA rollback과 단일 EC2 장애 복구도 완료되지 않았다. 실행 결과는 Secret과 식별자를 제외한 성공·실패 상태만 후속 보고서에 반영한다.

## 미결정 사항 또는 승인 필요 항목

실제 hostname 생성, Security Group `443`, 인증서 발급·갱신과 재부팅은 사용자 실행 승인이 필요하다. 자동 갱신 schedule, certificate backup, HSTS와 실제 application rollback은 후속 작업으로 분리한다.

## QA 필요 여부

별도 QA 문서는 생략한다. Repository Validation을 독립 자동 검증으로 사용하고 사용자/Tech Lead가 실제 외부 TLS·session gate를 수행한다.
