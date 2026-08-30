# MVP4-DATA-005 Canonical Customer Catalog

## 결정

PawCycle의 Customer Catalog는 앞으로 **Data V1 + Customer Catalog V3를 하나의 논리 데이터셋**으로 취급한다. UI/UX 검증용 Catalog와 실제 Customer 화면용 Catalog를 별도로 복제하지 않는다.

현재 구성은 다음과 같다.

| 항목 | Data V1 | V3 추가 | Canonical 합계 |
| --- | ---: | ---: | ---: |
| Product | 32 | 68 | **100** |
| SKU | 42 | 124 | **166** |
| Brand | 1 | 9 | **10** |
| Customer Category | 4 | 23 | **27** |

DOG/CAT Product는 각각 50개다. V3의 브랜드, 2-depth Category, Facet, 옵션 조합, compare-at price, 일반구매/정기배송 조합, 정상/low-stock/품절 Inventory, MAIN/DETAIL image와 상세 section은 그대로 Customer Catalog의 일부로 사용한다.

기존 `customer-catalog-v3.json`, `qa3-*` catalog key, `QA3-*` SKU code는 이미 테스트·문서·fixture에서 business key로 사용되고 있으므로 이번 작업에서 대량 rename하지 않는다. 이 값들은 내부 식별자이며 Customer UI에 노출되는 상품명·브랜드명과는 별개다. 이름만 바꾸기 위한 데이터 churn보다 동일 business key의 재현성과 기존 참조 안전성을 우선한다.

## 공통 적재 경계

`CustomerCatalogImportService`가 Data V1 baseline과 V3 supplement를 하나의 transaction 경계에서 조합한다.

```text
Customer Catalog
  ├─ Data V1: Category / Product / SKU / Inventory / Subscription Plan
  └─ V3: Brand / 2-depth Category / Facet / Product / Option / SKU / Image / Detail
```

`validate`는 manifest와 기존 DB business key·관계의 호환성을 확인하되 row를 생성하지 않는다. 존재하지 않는 V3 row는 virtual id로 추적해 실제 INSERT 없이 후속 관계까지 검증한다. `apply`는 같은 검증 경계를 사용해 누락 row만 생성하고 기존 mutable Inventory의 수량·예약·version은 초기화하지 않는다. 기존 row가 manifest와 충돌하면 덮어쓰지 않고 실패한다.

기존 local `pawcycle.local-customer-catalog-v3.enabled=true` 경로는 호환 wrapper로 유지하되 V3 적재 로직 자체는 공통 importer를 사용한다. 따라서 local/QA와 향후 승인된 one-shot 적용이 서로 다른 V3 구현을 갖지 않는다.

## Production 경계

이번 작업은 **저장소 변경**까지만 수행한다. 공통 Customer Catalog importer는 production profile에서도 사용할 수 있는 application service로 준비하지만, 기존 Production one-shot shell/config 계약은 이번 변경에서 전환하지 않는다. `pawcycle.duckdns.org`가 사용하는 Production DB에도 실제 Catalog를 적용하지 않는다.

Production 실제 적용은 다음을 별도 고위험 작업으로 다룬다.

1. 최신 release와 Production DB 상태 재확인
2. 기존 one-shot 경로가 Canonical Customer Catalog를 명시적으로 선택하도록 SRE adapter 준비
3. read-only validate/preflight
4. 사용자 적용 승인
5. apply와 aggregate postflight
6. 실제 Home / PLP / PDP 확인

저장소 준비 승인을 Production DB mutation 승인으로 해석하지 않는다.

## 성능 데이터와의 관계

`scripts/generate-product-data-v2.py`는 대량 cardinality를 만드는 deterministic synthetic dataset으로 그대로 유지한다. Customer Catalog를 UI/기능 검증용과 별도 실제 데이터로 다시 나누지는 않지만, 향후 10K/100K 이상 성능 측정에 필요한 synthetic scale extension은 목적이 다르므로 별도 생성 경로를 유지한다.

## 검증 기준

- Canonical apply 후 Product 100, SKU 166, Brand 10, Customer Category 27을 만족한다.
- DOG/CAT Product는 각각 50개다.
- 동일 apply 재실행에서 Product/SKU가 중복되지 않는다.
- validate는 관련 Catalog table count를 변경하지 않는다.
- 기존 V3의 옵션·Facet·가격·Inventory·이미지·상세 section 회귀 테스트를 계속 통과한다.
- Production 실제 apply는 이번 작업 완료 조건에 포함하지 않는다.
