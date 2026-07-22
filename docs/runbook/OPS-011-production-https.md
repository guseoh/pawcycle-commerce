# OPS-011 production HTTPS Runbook

## 목적과 경계

현재 단일 release의 Backend·Frontend SHA와 MySQL volume을 유지한 채 DuckDNS 단일 hostname과 Let's Encrypt HTTP-01 인증서를 적용한다. 인증서가 없을 때는 bootstrap HTTP가 계속 서비스되고, 인증서 hostname·유효기간과 Nginx 설정을 검증한 뒤에만 HTTP redirect와 HTTPS를 활성화한다. 실제 DuckDNS, AWS Security Group, 인증서 발급과 EC2 적용은 병합 뒤 사용자/Tech Lead가 수행한다.

DuckDNS token, 인증서 개인 키, 인증서 알림 email, 계정 식별자와 공인 IP를 명령 출력·캡처·PR에 남기지 않는다. `3306`, `8080`, `3000`은 공개하지 않으며 `80`, `443`만 허용한다.

## 고정 계약

- HTTP `80`: `/.well-known/acme-challenge/**` 제공, HTTPS 활성화 뒤 나머지는 `301` redirect
- HTTPS `443`: 기존 Frontend `/products`와 Backend `/api/**` same-origin proxy
- container 내부 `8081`: host에 publish하지 않는 health·release smoke 전용 listener
- 인증서 lineage: root 관리 Docker volume의 고정 이름 `pawcycle-production`
- Certbot: 공식 `certbot/certbot:v5.7.0` linux/amd64 digest 고정
- Backend `SESSION_COOKIE_SECURE=true`, MySQL volume `pawcycle-production-mysql-data` 유지

## 1. 적용 전 중단 gate

최신 `main` checkout과 깨끗한 worktree를 확인하고 현재 release 값을 출력하지 않은 채 존재 여부만 검사한다.

```bash
git status --short
sudo test -s /opt/pawcycle/state/current-sha
test "$(sudo stat -c '%a' /opt/pawcycle/state)" = 700
sudo docker volume inspect pawcycle-production-mysql-data >/dev/null
```

다음 중 하나면 중단한다.

- worktree가 깨끗하지 않거나 OPS-011 병합 commit인지 불명확함
- 현재 네 container가 healthy가 아니거나 기존 HTTP 두 endpoint가 실패함
- DuckDNS hostname이 EC2에 연결됐는지 사용자 콘솔에서 확인할 수 없음
- Security Group에서 inbound TCP `80`, `443` 외 application·DB port가 열려 있음
- runtime/state 경로, GHCR repository 또는 현재 release SHA가 불명확함

DuckDNS hostname 생성과 주소 갱신은 DuckDNS UI에서 수행한다. token을 shell, Runbook 증거 또는 명령 history에 붙여 넣지 않는다. DNS 확인 출력에는 공인 IP가 포함될 수 있으므로 저장하거나 PR에 첨부하지 않는다.

## 2. 비민감 실행 변수

값을 저장소 파일에 쓰지 않는다. shell history에는 변수명만 남도록 prompt에서 입력한다.

```bash
read -r -p 'DuckDNS hostname: ' DOMAIN
read -r -p 'Certificate notification email: ' CERTBOT_EMAIL
read -r -p 'Backend GHCR repository: ' BACKEND_IMAGE
read -r -p 'Frontend GHCR repository: ' FRONTEND_IMAGE
```

`DOMAIN`은 소문자 단일 `<label>.duckdns.org`, image는 tag 없는 소문자 `ghcr.io/...` 경로여야 한다.

## 3. 1단계 bootstrap HTTP

이 단계는 현재 application container와 MySQL을 교체하지 않고 proxy만 bootstrap 설정으로 재생성한다. 인증서 volume은 생성하되 삭제하지 않는다.

```bash
sudo bash infra/production/https.sh bootstrap \
  --domain "$DOMAIN" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE"
```

성공 문구 세 개(내부 smoke, HTTP-01 경로, bootstrap 준비)를 확인한다. 외부 네트워크에서 HTTP challenge 경로 접근이 차단되면 발급으로 진행하지 않는다.

## 4. 2단계 최초 발급과 HTTPS 전환

```bash
sudo bash infra/production/https.sh issue \
  --domain "$DOMAIN" \
  --email "$CERTBOT_EMAIL" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE"
```

Certbot 실패 시 bootstrap HTTP가 유지된다. 발급 성공 뒤에도 certificate SAN·유효기간, HTTPS Nginx config와 두 endpoint 검증이 모두 성공해야 mode `600`의 `/opt/pawcycle/state/https-enabled` marker가 유지된다. 전환 실패 시 script가 marker를 제거하고 bootstrap proxy를 복구한다.

## 5. 적용 후 검증

실제 hostname, certificate 출력, cookie 값과 공인 IP는 보고서나 PR에 복사하지 않는다.

```bash
sudo bash infra/production/https.sh status \
  --domain "$DOMAIN" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE"

sudo docker ps --filter label=com.docker.compose.project=pawcycle-production \
  --format '{{.Names}} {{.Status}}'
sudo ss -ltn | grep -E ':(80|443)[[:space:]]' >/dev/null
! sudo ss -ltn | grep -E ':(3306|8080|3000)[[:space:]]' >/dev/null
```

외부 사용자 PC에서 다음을 직접 확인한다.

1. `https://$DOMAIN/products`와 `https://$DOMAIN/api/products`가 성공한다.
2. `http://$DOMAIN/products`가 같은 hostname의 HTTPS 경로로 redirect된다.
3. 브라우저 인증서의 SAN에 정확한 hostname이 있고 만료일이 유효하다.
4. 승인된 test account로 login한 뒤 `JSESSIONID`가 `Secure`, `HttpOnly`, `SameSite=Lax`이고 logout 뒤 인증 상태가 제거된다. credential, cookie와 CSRF token은 기록하지 않는다.

## 6. 갱신 rehearsal과 실제 갱신

`dry-run`을 먼저 수행한다. dry-run은 Nginx를 reload하지 않는다.

```bash
sudo bash infra/production/https.sh renew --dry-run \
  --domain "$DOMAIN" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE"

sudo bash infra/production/https.sh renew \
  --domain "$DOMAIN" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE"
```

실제 `renew`는 Certbot 성공, certificate 재검증, 실행 중 Nginx `-t` 이후에만 reload한다. 갱신 대상이 아직 아니어도 검증과 안전한 reload를 수행한다. 자동 schedule은 OPS-011 범위가 아니므로 운영자가 만료 전에 같은 순서로 실행하고 결과를 비민감 상태만 기록한다.

## 7. 재부팅 복구

재부팅 전 현재 SHA를 root 전용 임시 shell 변수로만 보관하고 출력하지 않는다.

```bash
BEFORE_SHA="$(sudo cat /opt/pawcycle/state/current-sha)"
sudo reboot
```

재접속 뒤 Docker 자동 시작, 같은 SHA와 volume, 네 health를 확인한 다음 5절의 `status`, HTTPS 두 smoke, HTTP redirect와 login/logout을 다시 수행한다.

```bash
test "$(sudo cat /opt/pawcycle/state/current-sha)" = "$BEFORE_SHA"
sudo docker volume inspect pawcycle-production-mysql-data >/dev/null
sudo docker volume inspect pawcycle-production-letsencrypt >/dev/null
sudo test -f /opt/pawcycle/state/https-enabled
```

## 8. 실패 복구

- 발급 실패: 추가 조치 없이 bootstrap HTTP와 현재 release를 유지하고 DNS·`80` 접근성만 조사한다.
- 갱신 실패: Nginx를 reload하지 않는다. 기존 worker와 인증서가 계속 사용되며 volume·marker를 삭제하지 않는다.
- reload 실패: 실행 중 worker는 이전에 적재한 인증서를 유지한다. 원인을 수정한 뒤 `renew --dry-run`부터 다시 시작한다.
- 인증서 volume 손상 또는 HTTPS 기동 실패: 아래 명령으로 HTTP bootstrap을 복구한다. 인증서와 MySQL volume은 삭제하지 않는다.

```bash
sudo bash infra/production/https.sh disable \
  --domain "$DOMAIN" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE"
```

`docker compose down --volumes`, `docker volume rm`, 인증서·개인 키 출력과 state marker 수동 편집을 사용하지 않는다. 실제 이전 application SHA rollback은 OPS-010의 미충족 후속 gate이며 별도 승인 후 기존 Runbook으로 실행한다. OPS-011 적용 뒤에도 기존 old-SHA 간 rollback 계약은 유지되지만, TLS contract가 포함된 새 application SHA로 처음 전환할 때 `infra/production` diff gate가 중단하면 임의 우회하지 않고 별도 contract rebaseline 승인을 받는다.

## 참고

- [Let's Encrypt HTTP-01 challenge](https://letsencrypt.org/docs/challenge-types/)
- [Let's Encrypt의 port 80 유지 권고](https://letsencrypt.org/docs/allow-port-80/)
- [Certbot webroot와 renew](https://eff-certbot.readthedocs.io/en/stable/using.html)
- [DuckDNS update specification](https://www.duckdns.org/spec.jsp)
