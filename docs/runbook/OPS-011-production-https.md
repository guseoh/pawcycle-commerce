# OPS-011 OCI Production HTTPS

## 범위와 상태

OCI A1 Application host의 기존 Nginx/Certbot state contract를 기준으로 HTTP bootstrap, certificate issue/renew, HTTPS enable/disable을 운영한다. **Accepted — Repository Readiness**이며 **Production Verified가 아니다**. 실제 domain, certificate 발급, OCI network와 host 적용은 별도 고위험 실행 승인 대상이다.

## 현재 경계

- `proxy`만 host `80`·`443`을 publish한다.
- Backend `8080`, MySQL `3306`, Prometheus `9090`, Grafana `3000`은 public exposure가 없다.
- Certbot webroot와 Let's Encrypt volume은 기존 이름·mode·read-only proxy mount 계약을 유지한다.
- 실제 domain/IP는 저장소에 기록하지 않는다.

## 절차

1. `https.sh bootstrap`으로 HTTP challenge와 내부 proxy health를 확인한다.
2. 승인된 domain 입력으로 `https.sh issue`를 실행한다. certificate SAN, 최소 잔여 유효기간, Nginx `-t`를 순서대로 검증한다.
3. `https.sh enable`은 승인 domain, HTTPS `/products`·`/api/products`, HTTP redirect를 모두 확인한 뒤 marker를 기록한다.
4. `https.sh renew --dry-run`은 reload하지 않는다. 실제 renew는 Certbot 성공·certificate 재검증·Nginx 검증 후에만 reload한다.
5. `https.sh status`와 `release-common.sh`의 enabled HTTPS verification으로 release health를 재확인한다.

실제 실행의 입력은 운영자가 보유한 domain과 state뿐이며 certificate/private key 값을 출력하거나 저장소에 복사하지 않는다.

## 실패·롤백

- issue 또는 certificate 검증 실패: HTTPS marker를 만들지 않고 bootstrap 상태를 유지한다.
- 후보 Nginx config 검증 실패: 후보를 폐기하고 실행 중 proxy를 확인한다.
- enable 후 path/redirect 실패: marker 제거와 bootstrap 복구를 시도하고 승인 domain은 자동 삭제하지 않는다.
- renew 후 reload/path 검증 실패: certificate volume을 자동 rollback하지 않고 worker·volume·HTTPS path를 확인한다.

HTTPS 실패는 Application release, migration, managed DB, Object Storage backup lifecycle을 변경하지 않는다. 복구가 불명확하거나 Secret·certificate chain이 노출될 우려가 있으면 즉시 중단·에스컬레이션한다.

## Evidence status

Nginx syntax/hostname/redirect fake tests와 repository validator는 통과했다. 실제 OCI A1, DNS, certificate issuance/renewal, public HTTPS와 Production Verified 상태는 **Not Verified**다.
