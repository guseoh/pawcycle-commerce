# MVP4-DATA-003 Product Data V2

## 역할과 보존 경계

Data V1의 `backend/src/main/resources/catalog/demo-catalog.json`은 Product 32개, SKU 42개와 기존 Category·Inventory·Subscription Plan 관계를 가진 Customer Demo Catalog 기준선이다. Product Detail UI와 기존 Product Discovery QA가 이 business key를 사용하므로 V1 manifest를 확장하거나 재작성하지 않는다.

Data V2는 V1 manifest 뒤에 통합·성능 검증용 synthetic Product만 덧붙인 생성 결과다. 실제 업체·상품·가격·재고를 나타내지 않으며, Review·Rating·Q&A·Order·Payment·Member 개인정보는 생성하지 않는다. Synthetic Product에는 기존 Category를 재사용하고 Subscription Plan item은 추가하지 않는다.

## 생성

저장소 root에서 Python 표준 라이브러리만으로 실행한다.

```bash
python scripts/generate-product-data-v2.py \
  --additional-products 1000 \
  --seed 20260826 \
  --output tmp/product-data-v2/catalog-1000.json
```

`--base-manifest`를 생략하면 기존 V1 manifest를 사용한다. 다른 base 파일을 검증할 때는 다음처럼 명시한다.

```bash
python scripts/generate-product-data-v2.py \
  --base-manifest backend/src/main/resources/catalog/demo-catalog.json \
  --additional-products 1000 \
  --seed 20260826 \
  --output tmp/product-data-v2/catalog-1000.json
```

같은 base manifest, count, seed는 같은 `catalogKey`, `skuCode`와 같은 JSON 구조를 생성한다. Product는 DOG/CAT이 번갈아 생성되고 Category는 base Category pool에서 선택된다. Synthetic Product마다 SKU 1~3개를 만들며, SKU별 가격·`subscribable`·ACTIVE/INACTIVE·재고 0/low/normal 상태를 결정론적으로 분포한다. 생성 결과는 기존 `DemoCatalogManifestImportService`의 version 1 manifest 계약과 호환된다.

## local 실행

기본 `local-integration` startup은 계속 V1 manifest 32개만 사용한다. 대량 검증이 필요할 때만 disposable local/MySQL 환경에서 명시적으로 override한다.

```bash
java -jar backend/build/libs/pawcycle-backend-*.jar \
  --spring.profiles.active=local-integration \
  --pawcycle.local-demo-catalog.enabled=true \
  --pawcycle.local-demo-catalog.manifest=file:/absolute/path/to/catalog-1000.json
```

`DemoCatalogManifestImportService`가 `file:` resource를 읽고 기존 Product·Category·SKU·Inventory·Plan importer를 그대로 재사용한다. 같은 generated manifest를 재적용해도 business-key 중복을 만들지 않으며, Inventory는 기존 mutable 값을 초기화하지 않는다. Generated manifest는 repository에 commit하지 않는다. 권장 출력 경로인 `tmp/`는 local disposable 산출물이며 이후 count 확장 시 `--additional-products`만 늘려 새 파일을 생성한다.

V1 32개 Product의 Product Detail 화면은 `demo-product-detail-sections.json`의 plain-text section fixture를 local-integration에서 Catalog import 직후 적용해 채운다. 이 fixture도 `demo-*` catalogKey만 대상으로 하며, Production/prod/test profile에서는 자동 실행되지 않는다. Synthetic Product에는 별도 Detail·Review·Trust 데이터를 seed하지 않는다.

이번 작업은 저장소 준비만 수행한다. Production DB apply, Production one-shot import 실행, 10k/100k import 측정과 성능 최적화는 포함하지 않는다.
