# MVP4-QA-002 Customer Product Experience V3 Local QA Gate

## 목적과 경계

일반 등급, 저장소 변경 + 로컬 QA 실행이다. 기존 FOUNDATION-004 구성 위에 전용 overlay를 명시적으로 합쳐 V1 + V3 Customer catalog를 검증한다. 제품 코드·기존 bootstrap·기본 Compose·schema·dependency는 변경하지 않는다. Production/AWS/운영 DB/deploy, Ready 전환과 merge는 실행하지 않는다.

## 실행 구조와 모드

| 항목 | 설정 |
| --- | --- |
| Compose project | `pawcycle-mvp4-customer-qa` |
| MySQL volume | `pawcycle-mvp4-customer-qa-mysql-data` |
| Backend profile | 기존 `local-integration` |
| V3 flag | `PAWCYCLE_LOCAL_CUSTOMER_CATALOG_V3_ENABLED=true` |
| Clean visual (기본) | `PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED=false`: V1 + V3 Product 100, DOG/CAT 각 50, Brand 10, 공개 Category 27(9+18), SKU 166 |
| Auth flow | 위 값을 `true`로 설정: 기존 QA 계정/상품/구독 fixture 추가 가능, Product 100을 강제하지 않음 |

`PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS=false`를 overlay에 고정한다. auth flag를 false로 되돌려도 추가된 fixture는 삭제되지 않으므로 clean 재검증에는 새 전용 disposable volume이 필요하다. 기존 `pawcycle-local-integration-mysql-data`는 공유하거나 삭제하지 않는다.

## 준비와 Clean 시작

저장소 루트의 PowerShell 7(`pwsh`)을 사용한다. Windows PowerShell 5.1에서 실행해야 하면 UTF-8 파일을 명시적으로 읽어 실행한다. 시스템 encoding 설정을 변경하지 않는다.

기존 ignored `infra/local-integration/.env.local`을 재사용한다. 파일 내용을 출력하지 않으며 실제 credential을 명령 이력/문서/PR에 쓰지 않는다. 파일이 없으면 FOUNDATION-004 runbook의 로컬 준비 절차를 먼저 수행한다. 전체 `docker compose config` 출력은 credential을 포함하므로 `--quiet`만 사용한다.

```powershell
# 기존 stack/volume과 포트 점유를 확인한다. 전용 project/volume이 이미 있으면 소유자를 확인하고 중단한다.
docker ps --format '{{.Names}}\t{{.Ports}}'
docker volume ls --format '{{.Name}}'
if (Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue) {
    throw '8082 is already in use; choose another unused local port.'
}

$previousPort = $env:PAWCYCLE_LOCAL_HTTP_PORT
$previousAuth = $env:PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED
$env:PAWCYCLE_LOCAL_HTTP_PORT = '8082'
$env:PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED = 'false'
$qaCompose = @('-p', 'pawcycle-mvp4-customer-qa',
    '--env-file', 'infra/local-integration/.env.local',
    '-f', 'infra/local-integration/compose.yaml',
    '-f', 'infra/local-integration/compose.customer-product-qa.yaml')

docker compose @qaCompose config --quiet
if ($LASTEXITCODE) { throw 'Compose config failed' }
docker compose @qaCompose build backend frontend
if ($LASTEXITCODE) { throw 'QA image build failed' }
docker compose @qaCompose up --detach --wait --wait-timeout 180
if ($LASTEXITCODE) { throw 'QA stack readiness failed' }
pwsh -NoProfile -File infra/local-integration/customer-product-qa-smoke.ps1 -BaseUri http://localhost:8082
if ($LASTEXITCODE) { throw 'API preflight failed; do not start Browser QA' }
```

Windows PowerShell 5.1에서 smoke를 호출하는 대안:

```powershell
& ([scriptblock]::Create((Get-Content infra/local-integration/customer-product-qa-smoke.ps1 -Raw -Encoding UTF8))) -BaseUri http://localhost:8082
```

preflight는 Frontend 200, Discovery hierarchy/10 brands/facets/system category 미노출, Product/DOG/CAT 수, category/subcategory/brand/repeated facet, 공개 검색으로 찾은 대표 상품의 image/options/SKU/discount/stock/subscription/detail section을 검증한다. 전체 100개 public detail의 SKU 수를 합산하며 DB credential은 사용하지 않는다. 실패 시 응답 본문·cookie·CSRF를 출력하지 않는다.

## Browser QA 체크리스트

API preflight Green 후 `http://localhost:8082/`에서 시작한다. 기존 브라우저 도구 또는 수동 브라우저만 사용하고 framework를 설치하지 않는다. 일반 desktop(예: 1440×900), mobile(예: 360×800; 추가 320px 경계)에서 아래 결과와 재현 URL을 기록한다. 미실행은 통과로 기록하지 않는다.

| 영역 | 실행 및 기대 결과 |
| --- | --- |
| Home | Hero, DOG/CAT quick link, 9 top categories와 10 brands, NEWEST/정기배송 가능/리뷰 많은 collection, 비인증 personalized 안내. 이미지 실패 fallback·긴 상품명·품절/할인 표시 확인 |
| List 검색/분류 | q, DOG/CAT, top category → subcategory, brand, category-specific facet 1개/복수 적용. URL과 실제 결과 일치 |
| List 조건 | min/max(단독·0 포함), min>max 입력 유지/인접 오류/제출 차단; subscribable/purchasable; 정렬 6개 모두 선택 |
| List 탐색 | selected chip 개별 제거, 전체 초기화, 다음/이전 page, browser Back/Forward로 URL·조건·결과 복원 |
| Detail | 공개 검색에서 `스몰테일 연어 작은 알갱이`를 찾아 진입. Brand, MAIN/thumbnail/DETAIL, rating empty, 가격·compareAtPrice·discount, 상세 섹션, Review/Q&A, recent/related |
| SKU | 불완전 선택 차단; 1kg/1팩 품절, 1kg/2팩·2kg/1팩·2kg/2팩 구매 가능. 변경마다 가격/할인/재고/정기배송/quantity max 갱신. API selectedOptions 조합과 일치 |
| Responsive | Header menu/category 2-depth/search, filter panel, 수평 overflow, sticky purchase bar/dialog, 본문과 동일 option/quantity, 닫기/Escape/focus restore |

V3는 Review/Q&A seed가 없으므로 review 0, averageRating null은 데이터 계약이다. REVIEW_COUNT collection의 문구가 실제 데이터에 비해 어색하면 UX finding으로만 구분한다. 이미지들은 가상 상품의 실제 사진이 아닌 외부 데모 사진이다.

## Auth mode

Clean visual QA를 마친 뒤 같은 전용 project에서만 전환한다. 기존 데이터는 보존되고 QA fixture가 추가될 수 있다.

```powershell
$env:PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED = 'true'
docker compose @qaCompose config --quiet
if ($LASTEXITCODE) { throw 'Auth config failed' }
docker compose @qaCompose up --detach --wait --wait-timeout 180
if ($LASTEXITCODE) { throw 'Auth stack readiness failed' }
```

QA credential은 `.env.local` 또는 shell 환경에서만 읽어 로그인한다. 로그/스크린샷에 email/password/cookie/CSRF를 남기지 않는다. 자동 브라우저가 credential 입력 확인을 요구하면 사용자 확인 후 입력한다.

- 비인증 CTA의 login redirect와 로그인 후 원래 상품 경로 복귀.
- Wishlist initial loading과 ready 이후 add/remove, Cart add, Header badges.
- 느린 Wishlist GET 중 Cart 동작과 stale state는 현재 도구에서 안전한 지연 관찰이 가능할 때만 실행하고 아니면 미실행으로 기록.
- 품절 Cart 차단, quantity 0/소수/재고 초과 차단, 유효 수량.
- subscribable SKU에서 canonical `/subscriptions/new?skuId=...` 진입. 실제 결제는 수행하지 않음.

동일 QA 계정 변수를 현재 shell에 안전하게 제공한 뒤 기존 smoke를 그대로 실행한다(값은 출력하지 않음):

```powershell
pwsh -NoProfile -File infra/local-integration/smoke.ps1 -Scenario Full -BaseUri http://localhost:8082
```

기존 smoke가 공개 pageable 계약과 맞지 않거나 fixture 탐색에 실패하면 최초 실패를 기록하고 제품/기존 smoke를 임의로 바꾸지 않는다. Auth mode에 clean 100-count preflight를 재적용하지 않는다.

## Finding과 중단 경계

| 분류 | 기준 |
| --- | --- |
| BLOCKER | 흐름 완료 불가, 잘못된 Product/SKU 구매, stale/wrong state, auth/security 위반, 필터 결과 불일치, mobile interaction 불가, 심각한 overflow |
| CORRECTION | 기능은 되지만 명백한 Commerce UX/반응형/접근성/focus/정보 계층/empty/loading/error 문제 |
| FOLLOW-UP | 미세 polish, 이미지 최적화 경고, dependency audit, 새 component test framework, AbortController 자원 최적화 |

각 finding에 환경·사전 조건·재현 절차·기대/실제·증거·분류를 남긴다. 제품 수정은 다음 correction task로 전달한다. startup/DB/보안 실패 시 우회하거나 bootstrap 데이터를 수동 보정하지 않는다.

## Cleanup과 복구

검증 성공/실패 모두 전용 stack을 내린다. 아래 volume 삭제는 이번 실행 전에 없었고 이번 task에서 생성한 disposable volume임을 확인했을 때만 허용한다.

```powershell
docker compose @qaCompose down
if ($LASTEXITCODE) { throw 'QA stack cleanup failed' }
$qaVolume = 'pawcycle-mvp4-customer-qa-mysql-data'
$owner = docker volume inspect $qaVolume --format '{{ index .Labels "com.docker.compose.project" }}'
if ($LASTEXITCODE -or $owner -ne 'pawcycle-mvp4-customer-qa') { throw 'Volume owner mismatch; do not delete' }
# 이번 task에서 만든 disposable volume임을 별도로 확인한 경우에만 다음 줄 실행
docker volume rm $qaVolume
$env:PAWCYCLE_LOCAL_HTTP_PORT = $previousPort
$env:PAWCYCLE_CUSTOMER_QA_AUTH_BOOTSTRAP_ENABLED = $previousAuth
```

기존 `pawcycle-local-integration-mysql-data` 및 다른 project의 container/volume은 삭제하지 않는다. 이미지/공유 build cache도 일괄 정리하지 않는다. 변경 복구는 이 overlay/smoke/runbook의 일반 revert이며 제품 코드와 사용자 데이터의 reset은 포함하지 않는다.

## 검증 기록

환경: Windows PowerShell 7.6.4, Docker Desktop Linux Engine / Compose v2.40.0, 기존 Java 25 / Node 24 Dockerfile. 브라우저는 기존 Codex in-app browser, desktop 1440×900 / mobile 360×800 및 320×800이다. 새 테스트 framework나 dependency를 설치하지 않았다.

| 실행 | 결과 |
| --- | --- |
| Clean/auth Compose config | `config --quiet` 통과. 병합된 project/volume/profile/flag를 credential 출력 없이 별도 확인 |
| QA 이미지 build / clean up | Backend bootJar, Frontend build 통과. 5개 service healthy |
| PowerShell parser / clean preflight | 통과. Product 100, DOG 50, CAT 50, Brand 10, 공개 Category 27(9+18), public SKU 166 |
| 대표 상품 | 공개 검색으로 발견. 2 groups, 4 SKUs, MAIN 1 + DETAIL 3, sections 3, 할인/품절/구매 가능/정기배송 상태 통과 |
| Desktop Home/List | Hero/discovery/3 collections/anonymous login 확인. DOG/CAT 50, category/subcategory/brand, 연어 facet 2개 → 성견과 복수 적용 1개. 가격 0~20000/구독/구매 가능 조건, 잘못된 범위 오류·제출 차단, chip 개별 제거/초기화, 6종 sort 선택·URL 반영, 페이지 1↔2 및 Back/Forward 통과 |
| Detail | q 검색 → 상세, gallery thumbnail 전환, brand/trust empty/detail sections/Review·Q&A/related 표시 확인. 불완전/품절 Cart 차단, 1kg/2팩 재고37 → 2kg/2팩 재고51 및 가격/할인/max 갱신, 재고 초과 차단 |
| Mobile | Home/CAT quick link, Header menu/category 2-depth/Escape 닫기/search, filter panel·적용 확인. 320px Home/List 및 360px Detail에서 document scrollWidth=clientWidth. dialog는 desktop 옵션/수량 유지, 수량 변경 후 재열기 유지, 닫기 버튼 및 opener focus 복귀 확인 |
| Auth mode | 기존 bootstrap으로 재생성 후 5 services healthy. DB 전체 Product 101/SKU 167/Brand 10/Category 29; 공개 Product는 100. 비활성 QA category의 fixture 1개가 공개 목록에서 제외됨 |
| Auth API | 기존 QA 계정으로 CSRF/login/me, Wishlist initial/add/remove, 공개 검색 상품의 purchasable SKU Cart add(quantity=2) 및 조회 일치 확인. 임시 Cart 삭제 및 logout 완료 |
| Auth browser | 사용자 승인 후 로그인하여 원래 상품으로 복귀. Wishlist ready → 추가/해제 성공 및 Header 찜 0→1→0 확인. Cart에 1kg/2팩, 수량2, 단가17,800원/금액35,600원 및 Header 장바구니2 일치. 품절/0/1.5 수량 Cart 차단. 2kg/1팩의 26,700원·13% 할인·재고44 갱신 확인. `/subscriptions/new?productId=...&skuId=...` 진입 및 선택 옵션 인계 안내 확인; 반려동물 등록/구독 생성/결제는 하지 않음 |
| 기존 FOUNDATION smoke | **실패**. 수정/skip 없이 `Full` 1회 실행. 첫 실패는 `Expected exactly one FOUNDATION-004 product fixture`. 아래 Q-01 참조 |
| Cleanup | 전용 5개 container와 network를 내린 뒤 이번 task 생성 이력 및 Compose project label을 확인하고 전용 MySQL volume만 삭제. 기존 `pawcycle-local-integration-mysql-data` 보존 확인. 브라우저 viewport 복원. Auth browser가 추가한 Cart 항목은 전용 DB와 함께 폐기 |

### Findings

확정된 Customer 구매 흐름 BLOCKER는 현재 확인한 범위에서는 없다. 아래 CORRECTION과 미확인 항목이 있으므로 전체 browser QA Gate를 무조건 Green으로 판정하지 않는다. 제품 코드는 수정하지 않았다.

| ID / 분류 | 재현·기대·실제·증거 / 다음 조치 |
| --- | --- |
| C-01 / CORRECTION | Clean `/`, 1440px 및 320px에서 Hero의 “다음 배송을 한눈에” 카드 확인. 기대: 안내와 링크를 읽을 수 있음. 실제: 흰 배경 위 제목/링크가 흰색, 설명도 흰색 82%여서 보이지 않음. screenshot 및 computed style `background=rgb(255,255,255)`, strong/link `color=rgb(255,255,255)` 확인. Frontend CSS correction 필요 |
| C-02 / CORRECTION | 대표 상세에서 1kg + 1팩 선택. 기대: 품절 안내와 구매 차단. 실제: 구매는 차단되지만 disabled 수량 1 옆에 “현재 재고 0개 이하로 선택해 주세요.”가 표시되어 유효 최소수량 1과 모순되는 안내. 품절과 수량 오류 UX 분리 필요 |
| C-03 / CORRECTION | 320px `/products`에서 목록 검색 form 확인. 기대: 입력과 검색 버튼 모두 사용 가능한 폭. 실제: 검색 input 24px, 버튼 약240.8px로 입력값을 읽기 어려움(screenshot/bounding rect). Header 검색으로 우회 가능하며 전체 수평 overflow는 없음. 반응형 form sizing correction 필요 |
| C-04 / CORRECTION | Clean Home “많이 이야기하는 상품”에 “다른 반려가족의 리뷰가 쌓인 상품” 설명과 모든 card의 “리뷰 없음”을 함께 확인. Review seed 부재/정렬 자체는 데이터 계약이며 결함 아님. 빈 review 데이터에 맞는 collection 안내는 UX 검토 필요 |
| Q-01 / CORRECTION (기존 QA 경로) | Auth mode에서 기존 `smoke.ps1 -Scenario Full` 실행 시 fixture 탐색 실패. public JSON 필드는 `items,page,size,totalElements,totalPages`; 스크립트는 `products` 사용. 추가로 기존 LocalQaBootstrapService는 QA category를 inactive로 유지하며 ProductDiscoveryReader는 active category만 노출한다. 전용 DB에서 QA product PUBLIC/category_active=0/brand_active=1 확인. 따라서 필드만 바꾸어도 해결되지 않음. 기존 bootstrap/노출 정책을 임의 변경하지 말고 별도 QA correction에서 fixture 접근 경로 결정 |
| F-01 / FOLLOW-UP | 기존 이미지 최적화 경고, 이미지 외부 서비스/fallback, dependency audit(빌드 시 high 6건), 새 component test framework 및 AbortController는 이번 변경에서 다루지 않음. 초기 lazy 이미지의 미로딩을 broken image로 오판하지 않음 |

### 미확인·제한

- native purchase dialog Escape: 자동 `Escape`/`ESC` 입력 뒤 열린 상태가 관찰됐다. 동일 도구에서 Header의 React Escape handler는 정상 작동했다. native cancel 전달 여부를 분리 확인하지 못했으므로 제품 결함/통과로 확정하지 않는다. 실제 키보드로 dialog를 열고 Escape → 닫힘/opener focus를 재확인해야 한다.
- 목록 검색의 Enter 제출도 자동 입력에서 확정하지 못했고 검색 버튼 클릭 경로는 통과했다. 실제 키보드 재검증 항목이다.
- 브라우저 credential 입력과 이번 QA에서 추가한 찜 해제는 각각 사용자 확인 후 실행했다. 값은 출력하거나 저장하지 않았다. 로그인 전 Wishlist CTA는 `/login?returnTo=...` 상품 경로를 보존했다. Wishlist의 ready/추가/해제는 확인했지만 최초 짧은 loading 상태를 브라우저에서 포착하지 못했다.
- 느린 Wishlist GET와 Cart 동시 조작은 안전한 요청 지연 도구가 없어 미실행. API 성공과 기존 helper 회귀 결과로 브라우저 race 검증을 대체하지 않는다.
- 외부 이미지 강제 실패, metadata 실패, seeded review/Q&A 작성, 주문/결제, 전체 Catalog SKU 조합 전수 browser 조작, 장문 상품명 전용 데이터 추가는 수행하지 않았다. 대표 상품의 4개 조합은 확인했다. 관련 상품 링크 클릭 후 임시 브라우저 탭이 종료되어 최근 본 상품의 최종 표시는 미확인이다.
