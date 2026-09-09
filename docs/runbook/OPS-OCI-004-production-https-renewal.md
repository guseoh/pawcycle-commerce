# OPS-OCI-004 Production HTTPS certificate renewal

## 범위와 안전 경계

이 Runbook은 이미 별도 승인된 OCI Production host에서 현재 HTTPS 인증서를 자동 갱신하기 위한 저장소-backed 설치·점검 절차다. 이 저장소 변경과 아래 절차는 OCI, DNS, certificate account, secret, running container를 자동으로 변경하지 않는다. 사용자가 별도로 명시 승인하지 않은 실제 Production 실행은 이 작업에서 수행하지 않는다.

자동화는 `pawcycle-production` Compose project의 실행 중 `proxy` 하나만 읽고, 기존 named volume을 그대로 사용한다. Backend·Frontend를 시작·중지·재시작·recreate하지 않으며, 인증서 volume을 삭제하거나 생성하지 않는다.

## 고정 계약

- Compose contract: `infra/production/compose.yaml`
- Certbot webroot volume: `pawcycle-production-certbot-webroot` → `/var/www/certbot`
- Let's Encrypt volume: `pawcycle-production-letsencrypt` → `/etc/letsencrypt`
- Certificate lineage: `pawcycle.duckdns.org` (the already-live OCI lineage; do not rename or reissue it)
- Runtime HTTPS Nginx config: Compose가 `/etc/nginx/conf.d/default.conf`에 read-only mount한 승인 config. 인증서 경로와 hostname 계약은 `infra/production/nginx.https.conf`를 따른다.
- Approved hostname state: `/opt/pawcycle/state/https-domain`, regular file, mode `600`; OCI adoption 전에는 파일이 없을 수 있으며 자동화가 추정·생성하지 않는다.
- HTTPS enabled state: `/opt/pawcycle/state/https-enabled`, 내용 `enabled`, mode `600`; 두 파일은 명시적인 adoption 이후에만 보호된 runtime state로 취급한다.
- Certbot image: `certbot/certbot:v5.8.0@sha256:398c47284a6d6782825be71685f677ef3a1e65b8b5c278a8b1e99f6da84b4eb9`; 실행은 `--pull never`이고 image presence는 별도 preflight다.
- Renewal checks: 매일 03:00·15:00, `Persistent=true`

인증서·private key·Certbot account·email·공인 IP·실제 hostname은 shell history, journal 복사본, PR, 저장소에 기록하지 않는다. 상태 확인은 exit code와 민감하지 않은 unit 상태만 보존한다.

## 설치

최신 승인 branch의 파일을 별도 승인된 control checkout에서 사용한다. 설치 wrapper는 script와 systemd unit/timer만 idempotently 설치하고 timer를 enable/start하지 않는다.

```bash
sudo bash infra/production/install-production-https-renewal.sh
sudo systemd-analyze verify \
  /etc/systemd/system/pawcycle-production-https-renew.service \
  /etc/systemd/system/pawcycle-production-https-renew.timer
```

설치가 실패하면 `systemctl enable`, `systemctl start`, `docker`, `certbot`을 추가로 실행하지 않는다. 설치를 다시 수행할 수 있으며 기존 timer enable 상태를 변경하지 않는다.

## OCI adoption / migration preflight

이 절차는 과거 `pawcycle-production` lineage나 과거 `https-domain`·`https-enabled` 파일을 복원하는 절차가 아니다. 현재 OCI에서 이미 확인된 `pawcycle.duckdns.org` HTTPS 상태를 저장소 계약에 명시적으로 채택하는 경계다. 아래 순서에서 하나라도 실패하면 중단하고 certificate, private key, volume, running container를 조작하지 않는다.

1. state directory가 이미 존재하는 regular directory이며 mode `700`인지 확인한다. 없으면 별도 운영 승인으로 directory만 준비하고, 파일 내용은 임의로 만들지 않는다.
2. 설치된 wrapper로 read-only preflight를 실행한다. 이 명령은 두 state 파일이 없으면 이를 정상적인 미채택 상태로 보고, 현재 lineage의 SAN/유효성, runtime Nginx 경로, read-only mount, 두 named volume, running proxy, `nginx -t`를 확인한다. state 파일과 certificate를 만들거나 변경하지 않는다.

```bash
sudo /usr/local/libexec/pawcycle-production-https-renew preflight
```

3. preflight가 image absence로 중단되면 자동화가 pull하지 않는다. 별도 승인된 image 설치 경계에서만 아래 exact reference를 준비하고, 성공 여부를 출력에 secret 없이 확인한 뒤 preflight를 다시 실행한다.

```bash
sudo docker pull certbot/certbot:v5.8.0@sha256:398c47284a6d6782825be71685f677ef3a1e65b8b5c278a8b1e99f6da84b4eb9
```

4. preflight가 현재 OCI HTTPS 상태를 확인한 뒤에만 명시적 adoption을 실행한다. `--confirm-adopt` 없이는 파일이 생성되지 않는다. adoption은 두 state file만 기록하고 Certbot issuance/renewal, certificate 변경, Nginx reload를 수행하지 않는다.

```bash
sudo /usr/local/libexec/pawcycle-production-https-renew adopt --confirm-adopt
sudo /usr/local/libexec/pawcycle-production-https-renew preflight
```

부분 파일, 잘못된 mode/content, `pawcycle-production` lineage, hostname·SAN·runtime path 불일치가 보이면 adoption과 enable을 중단한다. 기존 certificate를 rename/reissue/delete/replace하거나 state 파일을 수동으로 덮어쓰지 않는다.

## Enable

설치와 unit 검증, adoption preflight가 성공하고, 현재 HTTPS service·두 named volume·approved state가 확인된 뒤에만 enable한다.

```bash
sudo systemctl enable --now pawcycle-production-https-renew.timer
sudo systemctl is-enabled --quiet pawcycle-production-https-renew.timer
sudo systemctl is-active --quiet pawcycle-production-https-renew.timer
```

`enable --now`는 timer만 시작한다. service는 schedule 시점에 oneshot으로 실행된다. 기존 Nginx·Backend·Frontend를 지금 재기동하지 않는다.

## Inspect

```bash
systemctl list-timers pawcycle-production-https-renew.timer --no-pager
sudo systemctl status pawcycle-production-https-renew.timer --no-pager
sudo journalctl -u pawcycle-production-https-renew.service --since '24 hours ago' --no-pager
```

성공 로그는 renewal 여부와 validation/reload 결과만 확인한다. 인증서 file, `docker inspect`의 전체 output, environment dump, `nginx -T`, private key와 account material은 출력·저장하지 않는다.

## Dry-run

dry-run은 Let's Encrypt staging rehearsal만 수행하며 Nginx reload를 수행하지 않는다.

```bash
sudo /usr/local/libexec/pawcycle-production-https-renew renew --dry-run
```

실패하면 service를 다시 실행하거나 volume을 조작하지 않고 실패 절차로 이동한다. 성공은 실제 renewal 또는 Production certificate reload 성공을 의미하지 않는다.

## 실행 순서와 failure contract

정상 실행 순서는 다음과 같다.

1. 이미 adopted 된 HTTPS state, approved hostname, 두 기존 named volume과 실행 중 `proxy`의 read-only mount를 확인한다.
2. exact pinned Certbot image가 local에 존재하는지 `docker image inspect`로 확인한다. image가 없으면 `--pull never` 경계에서 중단한다.
3. Certbot `renew`를 수행한다. renewal은 현재 `pawcycle.duckdns.org` lineage와 `/var/www/certbot` webroot를 사용한다.
4. deploy hook와 certificate fingerprint 변화로 실제 갱신 여부를 확인한다. 아직 갱신 대상이 아니면 성공 종료하고 Nginx는 reload하지 않는다.
5. 실제 갱신인 경우 certificate SAN이 approved hostname 하나와 정확히 일치하고 최소 잔여 유효기간을 만족하는지 검증한다.
6. 실행 중 proxy에서 `nginx -t`를 통과한 뒤에만 `nginx -s reload`를 수행한다.

다음은 모두 fail-closed다.

- Certbot renewal, renewal marker, fingerprint, hostname/validity validation, `nginx -t` 또는 reload 실패
- adoption state가 없거나 partial/invalid하거나, exact Certbot image가 local에 없음
- proxy가 없거나 둘 이상이거나 Compose project/service/volume/runtime config mount가 계약과 다름
- volume이 없거나 기존 service 상태가 불명확함

실패 시 인증서 volume을 삭제·재생성하지 않고, Backend·Frontend를 재시작하지 않으며, private key나 credential을 출력하지 않는다. actual renewal 뒤 validation 또는 reload가 실패해도 Nginx worker는 reload 전 기존에 로드한 certificate를 계속 사용한다. 기존 certificate가 유효한 동안 public service를 유지하는 것이 이 자동화의 복구 경계다.

## Failure / recovery

1. timer 상태와 마지막 service journal의 비민감 오류만 확인한다. `systemctl stop pawcycle-production-https-renew.timer`로 반복 실행을 일시 중지할 수 있다.
2. approved state의 존재·mode·허용된 값, running proxy의 service와 read-only volume/config mount, exact image presence, 외부 HTTP-01 reachability를 별도 승인 범위에서 확인한다. certificate/private key 내용을 출력하지 않는다.
3. state가 없으면 위 adoption/preflight 절차로 돌아간다. partial/invalid state면 파일을 임의로 삭제·수정하지 말고 운영 복구 승인을 요청한다.
4. 원인이 수정되기 전에는 `docker compose up`, `restart`, `down`, volume remove/create, Backend·Frontend 조작을 하지 않는다.
5. preflight가 다시 통과하면 먼저 `renew --dry-run`을 실행하고, 그 뒤 timer service를 한 번 실행한다.

```bash
sudo systemctl stop pawcycle-production-https-renew.timer
sudo /usr/local/libexec/pawcycle-production-https-renew renew --dry-run
sudo systemctl start pawcycle-production-https-renew.service
sudo systemctl status pawcycle-production-https-renew.service --no-pager
```

`nginx -t` 또는 reload가 계속 실패하면 timer를 disabled 상태로 유지하고 일반 Nginx incident/recovery 절차와 사용자 승인을 요청한다. 인증서 volume을 수동으로 덮어쓰거나 lineage를 삭제하지 않는다. 현재 certificate가 만료될 위험이 있으면 별도 운영 incident로 승격한다.

## Disable

자동 갱신만 중지하며 현재 HTTPS, Nginx, Backend·Frontend와 certificate volumes은 변경하지 않는다.

```bash
sudo systemctl disable --now pawcycle-production-https-renew.timer
sudo systemctl is-enabled pawcycle-production-https-renew.timer >/dev/null 2>&1 && exit 1 || true
```

service는 oneshot이므로 상주 process가 없다. 재활성화 전에는 설치 파일과 unit을 다시 검증하고 dry-run을 수행한다.

## 저장소 검증과 중단 조건

저장소에서 다음 검증을 실행한다. 이 명령은 OCI/Production에 연결하지 않는다.

```bash
bash -n \
  infra/production/renew-production-certificate.sh \
  infra/production/install-production-https-renewal.sh \
  infra/production/test-production-https-renewal.sh
bash infra/production/test-production-https-renewal.sh
```

실제 timer enable, dry-run, service start, recovery와 disable은 저장소 검증 결과만으로 승인되지 않는다. 대상 host·현재 certificate state·rollback/recovery 경계가 불명확하면 즉시 중단한다.
