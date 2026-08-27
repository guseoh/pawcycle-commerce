# MVP4-QA-002 Customer Product Experience V3 Local QA Gate

## 목적과 경계

일반 등급의 저장소 변경 + 로컬 QA 절차다. 기존 FOUNDATION-004 구성 위에 전용 overlay를 명시적으로 합쳐 V1 + Customer Catalog Data V3를 검증한다. 제품 코드·기존 bootstrap·기본 Compose·schema·dependency는 변경하지 않는다. Production/AWS/운영 DB/deploy에는 사용하지 않는다.

## 실행 구조와 모드

| 항목 | 설정 |
| --- | --- |
| Compose project | `pawcycle-mvp4-customer-qa` |
| MySQL volume | `pawcycle-mvp4-customer-qa-mysql-data` |
| Backend profile | 기존 `local-integration` |
| V3 flag | `PAWCYCLE_LOCAL_CUSTOMER_CATALOG_V3_ENABLED=true` |
| Clean visual | `PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED=false` |
| Auth flow | `PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED=true` |
| Subscription reset | `PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS=false` |

Clean visual은 V1 + V3 Product 100, DOG/CAT 각 50, Brand 10, 공개 Category 27(9+18), SKU 166을 기준으로 한다. Auth flow에서는 기존 QA fixture가 추가될 수 있으므로 Product 100을 강제하지 않는다. auth flag를 false로 되돌려도 추가 fixture는 삭제되지 않으므로 clean 재검증은 새 전용 disposable volume에서 수행한다. 기존 `pawcycle-local-integration-mysql-data`는 공유하거나 삭제하지 않는다.

## 준비와 안전 경계

저장소 루트의 PowerShell 7(`pwsh`)을 사용한다. 기존 ignored `infra/local-integration/.env.local`을 재사용하며 파일 내용이나 credential을 출력하지 않는다. 전체 `docker compose config` 출력도 credential을 포함할 수 있으므로 사용하지 않는다.

시작 전 기존 container, port, volume을 확인한다. `pawcycle-mvp4-customer-qa` project 또는 동일 전용 volume이 이미 존재하면 소유와 상태를 확인하고, 다른 작업의 자원이면 중단한다. 일반 local-integration project와 `pawcycle-local-integration-mysql-data`에는 `down` 또는 삭제를 수행하지 않는다.

## Clean 실행과 항상 수행되는 cleanup

다음 예시는 환경 변수 원복과 전용 Compose project cleanup을 `finally`에서 보장한다. Volume 삭제는 여기서 수행하지 않는다. cleanup 실패는 원래 QA 실패보다 먼저 판단하지 말고 별도 오류로 기록한다.

```powershell
$previousPort = $env:PAWCYCLE_LOCAL_HTTP_PORT
$previousAuth = $env:PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED
$env:PAWCYCLE_LOCAL_HTTP_PORT = '8082'
$env:PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED = 'false'
$qaCompose = @('-p', 'pawcycle-mvp4-customer-qa',
    '--env-file', 'infra/local-integration/.env.local',
    '-f', 'infra/local-integration/compose.yaml',
    '-f', 'infra/local-integration/compose.customer-product-qa.yaml')
$qaError = $null

try {
    docker compose @qaCompose config --quiet
    if ($LASTEXITCODE) { throw 'Compose config failed' }

    docker compose @qaCompose build backend frontend
    if ($LASTEXITCODE) { throw 'QA image build failed' }

    docker compose @qaCompose up --detach --wait --wait-timeout 180
    if ($LASTEXITCODE) { throw 'QA stack readiness failed' }

    # Runtime isolation assertions use only non-secret values.
    $backendContainer = docker compose @qaCompose ps -q backend
    if ($LASTEXITCODE -or [string]::IsNullOrWhiteSpace($backendContainer)) { throw 'Backend container lookup failed' }
    $projectLabel = docker inspect $backendContainer --format '{{ index .Config.Labels "com.docker.compose.project" }}'
    $profile = docker inspect $backendContainer --format '{{ range .Config.Env }}{{ println . }}{{ end }}' | Where-Object { $_ -eq 'SPRING_PROFILES_ACTIVE=local-integration' }
    $v3 = docker inspect $backendContainer --format '{{ range .Config.Env }}{{ println . }}{{ end }}' | Where-Object { $_ -eq 'PAWCYCLE_LOCAL_CUSTOMER_CATALOG_V3_ENABLED=true' }
    $auth = docker inspect $backendContainer --format '{{ range .Config.Env }}{{ println . }}{{ end }}' | Where-Object { $_ -eq 'PAWCYCLE_LOCAL_QA_BOOTSTRAP_ENABLED=false' }
    $reset = docker inspect $backendContainer --format '{{ range .Config.Env }}{{ println . }}{{ end }}' | Where-Object { $_ -eq 'PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS=false' }
    if ($projectLabel -ne 'pawcycle-mvp4-customer-qa' -or -not $profile -or -not $v3 -or -not $auth -or -not $reset) {
        throw 'Customer QA isolation/environment assertion failed'
    }
    $volumeOwner = docker volume inspect pawcycle-mvp4-customer-qa-mysql-data --format '{{ index .Labels "com.docker.compose.project" }}'
    if ($LASTEXITCODE -or $volumeOwner -ne 'pawcycle-mvp4-customer-qa') { throw 'Customer QA volume ownership assertion failed' }

    pwsh -NoProfile -File infra/local-integration/customer-product-qa-smoke.ps1 -BaseUri http://localhost:8082
    if ($LASTEXITCODE) { throw 'API preflight failed; do not start Browser QA' }

    # Browser QA는 아래 체크리스트를 따른다.
}
catch {
    $qaError = $_
    throw
}
finally {
    docker compose @qaCompose down
    $cleanupExit = $LASTEXITCODE
    $env:PAWCYCLE_LOCAL_HTTP_PORT = $previousPort
    $env:PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED = $previousAuth
    if ($cleanupExit -and $null -eq $qaError) { throw 'Customer QA stack cleanup failed' }
}
```

Windows PowerShell 5.1에서 smoke를 호출해야 한다면 UTF-8을 명시적으로 읽어 실행한다. 시스템 encoding 설정은 바꾸지 않는다.

## API preflight

`customer-product-qa-smoke.ps1`은 모든 HTTP 요청의 redirect를 차단하고 loopback HTTP만 사용한다. 다음을 검증한다.

- Frontend 200
- Discovery top category 9, child 18, Brand 10, facet 존재, system category 미노출
- Product 100, DOG 50, CAT 50
- category/subcategory/brand filtering
- `protein:연어` 단독 결과와 `life-stage:성견` 단독 결과의 Product ID 교집합이 두 repeated `facet` 결과와 정확히 일치
- 공개 검색으로 찾은 대표 상품의 Brand, MAIN 1 + DETAIL 3, option group 2, SKU 4, selectedOptions, 할인, 품절·구매 가능, 구독 가능, detail section 3
- 모든 public Product detail의 SKU 합계 166

Product ID는 공개 API에서 얻고 DB ID를 하드코딩하지 않는다. 실패 시 response body, cookie, credential을 출력하지 않는다.

## Browser QA 체크리스트

API preflight Green 뒤에 실제 브라우저에서 실행한다. 새 browser framework는 추가하지 않는다. Desktop과 320~380px mobile을 포함한다.

| 영역 | 확인 |
| --- | --- |
| Home | Hero, DOG/CAT quick link, category/brand discovery, NEWEST/정기배송/REVIEW_COUNT collection, anonymous personalized 안내, image fallback·긴 이름·품절·할인 |
| List 검색/분류 | q, DOG/CAT, category→subcategory, brand, category-specific facet 단독/복수, URL과 결과 일치 |
| List 조건 | min/max(0·단독 포함), min>max 제출 차단, subscribable/purchasable, 6종 sort |
| List 탐색 | selected chip 제거/초기화, pagination, Browser Back/Forward |
| Detail | 대표 상품 Brand/gallery/rating empty/가격·할인/detail sections/Review·Q&A/recent/related |
| SKU | 불완전 선택 차단, 품절 조합, 구매 가능 조합, SKU 변경에 따른 가격/할인/재고/구독/quantity max 갱신 |
| Responsive | Header/menu/category/search, filter panel, overflow, sticky purchase bar/dialog, option/quantity 상태 공유, close/Escape/focus restore |

Customer Catalog Data V3에는 Review/Q&A seed가 없으므로 `averageRating=null`과 review 0은 데이터 계약이다. 문구가 어색하면 UX finding으로 기록한다.

## Auth mode

Clean QA가 끝난 뒤에만 별도 Auth flow를 실행한다. `PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED=true`로 재생성하고 runtime assertion에서도 `PAWCYCLE_LOCAL_QA_BOOTSTRAP_ENABLED=true`를 확인한다. 기존 `.env.local` credential은 shell/runtime에서만 사용하고 출력하지 않는다.

확인 범위:

- 비인증 CTA의 login redirect와 원래 상품 경로 복귀
- Wishlist ready 이후 add/remove와 Header badge
- Cart 정확한 SKU/quantity와 Header badge
- 품절·잘못된 quantity 차단
- subscribable SKU에서 canonical `/subscriptions/new?productId=...&skuId=...` 진입

실제 구독 생성·결제는 수행하지 않는다. 느린 Wishlist GET 중 Cart race처럼 안전한 request-delay 도구가 없으면 미실행으로 기록한다.

기존 FOUNDATION-004 `smoke.ps1`의 실패가 재현되면 한 번의 최초 실패를 기록하고 이 task에서 기존 smoke/bootstrap/category 정책을 수정하지 않는다.

## Finding 분류와 중단 경계

| 분류 | 기준 |
| --- | --- |
| BLOCKER | 흐름 완료 불가, 잘못된 Product/SKU 구매, stale/wrong state, auth/security 위반, filter 결과 불일치, mobile interaction 불가, 심각한 overflow |
| CORRECTION | 기능은 되지만 명백한 Commerce UX/반응형/접근성/focus/정보 계층/empty/loading/error 문제 |
| FOLLOW-UP | 미세 polish, image optimization, dependency audit, 새 component test framework, AbortController 자원 최적화 |

각 finding에 환경·사전 조건·재현 절차·기대/실제·증거·분류를 남긴다. 제품 수정은 별도 correction task로 전달한다. startup/DB/security 실패 시 우회하거나 bootstrap 데이터를 수동 보정하지 않는다.

## Volume cleanup과 복구

전용 stack은 성공/실패 모두 위 `finally`에서 `down`한다. 전용 MySQL volume은 자동 삭제하지 않는다. 이번 실행 전에 존재하지 않았고 이번 QA가 만든 disposable volume임을 별도 확인한 경우에만 다음 조건을 확인하고 삭제한다.

```powershell
$qaVolume = 'pawcycle-mvp4-customer-qa-mysql-data'
$owner = docker volume inspect $qaVolume --format '{{ index .Labels "com.docker.compose.project" }}'
if ($LASTEXITCODE -or $owner -ne 'pawcycle-mvp4-customer-qa') { throw 'Volume owner mismatch; do not delete' }
# 이번 실행에서 생성된 disposable volume임을 별도로 확인한 경우에만 실행
# docker volume rm $qaVolume
```

기존 `pawcycle-local-integration-mysql-data`와 다른 project의 container/volume은 삭제하지 않는다. image/shared build cache도 일괄 정리하지 않는다. 저장소 변경 복구는 overlay/smoke/runbook/report의 일반 revert이며 제품 코드나 사용자 데이터 reset을 포함하지 않는다.

실제 실행 결과와 finding은 `docs/reports/MVP4-QA-002/qa-report.md`에 기록한다.
