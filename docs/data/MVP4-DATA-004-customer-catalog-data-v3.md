# MVP4-DATA-004 Customer Catalog Data V3

## 데이터 역할

| 데이터 | 역할 | 적용 경로 |
| --- | --- | --- |
| Data V1 | 기존 Customer Demo baseline: Product 32 / SKU 42 / Subscription Plan 6 | `catalog/demo-catalog.json`, 기존 manifest importer와 detail fixture |
| Data V2 | synthetic integration/performance data | `scripts/generate-product-data-v2.py`, 명시적인 custom manifest override |
| Data V3 | curated Customer QA Catalog | `catalog/customer-catalog-v3.json`, V1 뒤에 적용하는 별도 local fixture |

V1 manifest와 catalogKey·skuCode·category slug·Plan 관계는 변경하지 않는다. V2의 의미나 기본 실행 방식도 바꾸지 않는다. V3 JSON은 version 3의 별도 형식이며 Production one-shot importer의 version 1 manifest가 아니다.

## 범위와 구성

작업 등급은 일반, 실행 구분은 저장소 변경이다. 새로운 schema, migration, dependency, API 계약 또는 제품 정책은 추가하지 않는다.

깨끗한 Catalog에 V1과 V3만 적용한 기준:

| 항목 | V1 | V3 추가 | 최종 |
| --- | ---: | ---: | ---: |
| Product | 32 | 68 | 100 |
| SKU | 42 | 124 | 166 |
| Brand | 1 | 9 | 10 |
| Customer Category | 4 | 23 | 27 |
| Subscription Plan | 6 | 0 | 6 |

DOG/CAT은 각각 50개다. 신규 Product는 종별 34개이며 28개는 단일 SKU·옵션 없음, 32개는 옵션 그룹 1개·SKU 2개, 8개는 용량/팩 수 그룹 2개·SKU 4개다. SKU는 각 그룹에서 값 하나만 선택하며 동일 Product의 조합이 중복되지 않는다. V1의 기존 옵션 없는 다중 SKU는 그대로 보존한다.

가상 브랜드는 그레인테일, 멜로바이트, 클라우드그룸, 타이디포, 보울노트, 플레이오빗, 트레일메이트, 네스트루프, 웨어페탈이다. 기존 PawCycle Demo Catalog 브랜드는 V1에 남는다.

기존 `food`, `treats`, `hygiene`, `toilet`에 `feeding`, `play`, `outdoor`, `living`, `fashion`을 추가한다. 상위 9개마다 하위 Category 2개를 구성해 하위는 18개이며, 3-depth는 없다. V1 Product는 기존 상위 Category 관계를 유지한다.

Facet은 `life-stage`, `food-form`, `protein`, `texture`, `pack-size`, `material`, `usage`, `scent`의 enum형 definition을 사용한다. 사료에는 생애 단계·형태·주원료, 간식에는 주원료·식감, 목욕/배변에는 향·포장, 도구에는 소재·사용 장소를 연결한다. 각 Product의 실제 Category에 허용된 definition만 선택한다.

모든 신규 Product에 MAIN 이미지 1개와 plain-text 상세 섹션 3개(상품 특징, 사용·구성 안내, 구매 전 확인)를 제공한다. 대표 10개에는 DETAIL 이미지 3개도 제공한다. 기존 V1의 Unsplash demo URL 정책을 재사용하며 binary asset은 추가하지 않는다. 이미지는 반려동물 데모 참고 사진이며 가상 상품의 실제 외관·구성품 사진이 아니다. 외부 이미지 서비스의 가용성은 보장하지 않는다. V1의 `thumbnailUrl` fallback은 유지한다.

기존 V13의 비공개 시스템 Category `__pawcycle_uncategorized__` 1개를 포함하면 DB 전체 Category 수는 28개다. 위 표의 27개는 Customer QA용 분류만 센 값이다.

V3의 다중 SKU 회귀에서 발견한 가격 필터 오류도 수정한다. 기존 `minPrice`와 `maxPrice`가 서로 다른 SKU에 각각 맞는 경우까지 포함하던 두 EXISTS 조건을 하나로 묶어, API-009의 ACTIVE SKU 가격 범위 조건을 같은 SKU가 만족하도록 한다. 요청/응답 형식이나 대표 가격·정렬 계약은 변경하지 않는다.

## 로컬 적용과 재실행

기본 local-integration은 계속 V1을 사용한다. Customer UI QA에서만 다음 flag를 추가한다. 연결 대상은 폐기 가능한 로컬 MySQL이어야 하며 datasource 설정은 기존 local-integration 환경을 사용한다.

```text
--spring.profiles.active=local-integration
--pawcycle.local-customer-catalog-v3.enabled=true
```

V3 flag는 기본 false다. `test`, `production`, `prod`에서는 service와 runner가 등록되지 않으며, `local-integration`과 함께 활성화해도 차단된다. V2/custom manifest override 또는 `pawcycle.local-demo-catalog.enabled=false`와 V3 flag의 동시 사용은 오류로 중단한다. 이 작업에는 Production/AWS/운영 DB/import 실행이 없다.

`LocalCustomerCatalogV3FixtureService`는 기존 `LocalCommerceDemoFixtureService`를 통해 V1 import와 detail fixture를 먼저 적용한다. 이어 V24의 option group/value, SKU option combination, MAIN image, category facet/product facet 관리 서비스를 재사용한다. SQL 값은 parameter binding을 사용한다.

`LocalCustomerCatalogV3FixtureService.bootstrap()` 호출 안에서는 V1 bootstrap과 V3 적용이 동일 transaction에 참여한다. 다만 기본 local-integration의 V1 `ApplicationRunner`는 별도로 존재하므로 애플리케이션 startup 전체가 V1+V3 단일 transaction으로 묶이는 것은 아니다. V3 적용 단계는 기존 demo brand row를 잠가 동시 V3 실행을 직렬화한다. `qa3-*` catalogKey, `QA3-*` skuCode, brand/category slug, facet key/value와 Product 단위 image/detail collection을 fixture 식별·충돌 검증 경계로 사용한다. 재실행은 같은 데이터를 확인하며 카탈로그 필드 또는 fixture-owned image/detail collection의 drift는 덮어쓰거나 누락분을 보충하지 않고 실패한다. Inventory는 최초 생성 때만 채우며, 이후 available/reserved/version을 초기화하지 않는다. 해당 V3 transaction이 실패하면 그 transaction의 변경은 rollback한다.

기존 local QA bootstrap이나 사용자가 생성한 데이터가 함께 있으면 DB 전체 수는 위 표보다 많을 수 있다. 개별 상품·SKU 식별에는 숫자 ID 대신 business key를 사용한다. V3 flag를 끄는 것은 재실행만 막으며 이미 생성된 데이터를 삭제하지 않는다. 사용 흔적이 있는 DB에서 주문/참조 행을 무시한 삭제는 하지 않는다. 초기 상태가 필요하면 별도 disposable local DB를 준비한다.

## Customer UI 대표 확인점

| catalogKey | 확인할 상태 |
| --- | --- |
| `qa3-dog-salmon-small` | 2개 옵션 그룹, SKU 4개, 일부 SKU 품절, compareAtPrice 할인, MAIN+DETAIL 3개 |
| `qa3-cat-tuna-pate` | 옵션 그룹 1개, 다중 SKU, 할인 없음, 습식/파테/참치 facet |
| `qa3-cat-chicken-soup` | 전체 SKU 품절 |
| `qa3-dog-daily-pad` | low stock(3), 정기배송 가능 |
| `qa3-cat-cotton-kicker` | 옵션 없음, 단일 SKU, 일반구매 전용 |
| `demo-dog-food-salmon` | V1 business key·상세 fixture·thumbnail fallback 보존 |

## Review / Q&A 경계

실행용 Review/Q&A seed는 제외한다. Review는 해당 회원 소유 Order에 Product SKU가 존재하고 연결 Delivery가 DELIVERED여야 한다. 기존 local fixture는 Catalog·계정·구독 Plan용이며 신규 68개 Product의 정상 구매/배송 완료 이력이나 Q&A 작성자·답변자 준비 경로를 제공하지 않는다. 이 작업에서 주문 상태, ownership 또는 인증 규칙을 우회하거나 Account/Order/Payment fixture로 범위를 확장하지 않는다.

따라서 새 V3 Product의 초기 trust는 `reviewCount=0`, `averageRating=null`, `questionCount=0`이다. 리뷰가 있는 UI와 답변/미답변 Q&A는 실제 로컬 QA 흐름에서 작성한 뒤 확인해야 한다. 회귀 테스트에서는 기존 engagement 테스트 패턴에 따라 테스트 transaction 안에서만 배송 완료 주문을 구성하고 `ProductEngagementService.createReview`로 서로 다른 rating을 작성해 RATING/REVIEW_COUNT 및 detail trust 집계를 검증한다. 이 테스트 데이터는 애플리케이션 시작 시 생성되지 않는다.

## 검증 경계

`LocalCustomerCatalogV3FixtureIntegrationTests`는 MySQL에서 V1 보존, V3 최종 수, DOG/CAT·Brand 분포, 2-depth, MAIN uniqueness, 가격, 옵션 조합, Category/Facet 호환성, 재고·구독 상태, 전체 fixture 멱등성, mutable inventory 보존, 충돌 rollback과 public list/detail을 검증한다. `LocalCustomerCatalogV3FixtureDriftIntegrationTests`는 Admin에서 image/detail section의 순서를 변경한 뒤 재실행할 때 중복을 생성하지 않고 명시적 conflict로 실패하는 것을 검증한다. `LocalQaBootstrapConfigurationTests`는 명시적 flag, profile 차단, V2 혼용 차단을 검증한다.

공식 검증은 Java 25 / MySQL 8.4의 `./gradlew compileJava compileTestJava`, `./gradlew test`, `./gradlew build -x test`와 저장소 task-artifact·commit-message·PR metadata validator다. 데이터 문서에는 변동하는 PR/HEAD/CI 상태를 기록하지 않는다.
