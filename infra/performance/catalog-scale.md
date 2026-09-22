# Catalog Scale Dataset 측정 계약

이 문서는 PawCycle 상품 읽기 성능에서 **데이터 규모 자체의 영향**을 분리하기 위한 Scale Dataset 계약이다.

저장소 준비와 실제 Production 실행은 분리한다. 이 문서와 관련 script는 대량 manifest를 준비·검증할 뿐이며, Production DB import나 Production k6 load를 자동 실행하지 않는다.

## 1. 단계 분리

현재 성능 단계는 데이터 변수를 한 번에 섞지 않는다.

```text
Stage 0
현재 Canonical Production Catalog
→ OCI 환경의 mutation 없는 control baseline

Dataset A
Catalog Core 10K
→ Product / SKU / Inventory cardinality 증가만 우선 측정

Dataset B
Discovery Realism 10K
→ Brand / 2-depth Category / Facet / Image / Option 분포 확대
→ 별도 후속 작업

Engagement Scale
→ Review / Rating 분포
→ 승인된 분포 근거가 생길 때 별도 후속 작업
```

첫 OCI Re-baseline은 Stage 0과 Dataset A를 우선한다.

## 2. 10K의 의미

`10K`는 **추가 synthetic Product 10,000개**가 아니라 **최종 manifest의 총 Product 10,000개**를 뜻한다.

현재 V1 base manifest는 Product 32개다.

따라서 Catalog Core 10K는 다음과 같다.

```text
base V1 Product        32
synthetic V2 Product  9,968
---------------------------
total Product        10,000
```

기존 generator를 직접 다음처럼 실행하면 안 된다.

```text
--additional-products 10000
```

이 경우 최종 Product가 10,032개가 되어 실험 label과 실제 cardinality가 어긋난다.

## 3. Dataset A — Catalog Core 10K

Dataset ID:

```text
catalog-core-10k-v1
```

고정 seed:

```text
20260826
```

기준 base:

```text
backend/src/main/resources/catalog/demo-catalog.json
```

기존 `scripts/generate-product-data-v2.py`의 default 생성 규칙은 변경하지 않는다.

Dataset A는 다음 관계를 확대한다.

- Product
- 기존 Category
- SKU
- Inventory

다음 관계는 Dataset A에 추가하지 않는다.

- V3 Brand 분포
- 2-depth Category / Subcategory
- Facet
- Product Image
- Option Group / Value
- Review / Rating
- Order / Payment / Member

따라서 Dataset A는 **Catalog Core cardinality control**이며 전체 Commerce 현실성 데이터라고 표현하지 않는다.

## 4. 준비 명령

대량 manifest와 report는 gitignored `tmp/` 아래 또는 저장소 바깥에만 생성한다.

```bash
python scripts/prepare-product-scale-data.py \
  --target-products 10000 \
  --seed 20260826 \
  --dataset-id catalog-core-10k-v1 \
  --output tmp/performance/catalog-core-10k.json \
  --report tmp/performance/catalog-core-10k-report.json
```

이 wrapper는 다음 순서로 동작한다.

```text
target total Product
→ base Product 수 읽기
→ 필요한 synthetic Product 수 계산
→ 기존 deterministic V2 generator 재사용
→ 관계·분포 검증
→ manifest checksum 계산
→ deterministic report 생성
```

기존 generator와 같은 base/count/seed를 사용했을 때 생성 manifest bytes는 동일해야 한다.

## 5. Report 계약

report에는 다음을 저장한다.

```text
schemaVersion
datasetId
seed
baseManifestSha256
generatedManifestSha256

products
  base
  synthetic
  total
  petType counts
  category counts
  withoutSku
  duplicateCatalogKeys
  unknownCategoryReferences

skus
  total
  fanoutByProduct
  status counts
  subscribable true/false
  duplicateSkuCodes

inventory
  total
  stockout
  low_1_5
  normal_gt_5

price
  min
  p50NearestRank
  p95NearestRank
  max
```

report에는 Credential, Production OCID, 실제 DB row, Cookie, Session, 개인정보를 넣지 않는다.

## 6. 현재 generator 기준 10K 예상값

아래 값은 현재 V1 manifest와 seed `20260826`의 deterministic generator 규칙에서 계산한 기대값이다.

실제 완료 근거는 사람이 계산한 이 표가 아니라 **prepare/verify report의 PASS 결과**다.

| 항목 | 기대값 |
| --- | ---: |
| Product total | 10,000 |
| DOG Product | 5,000 |
| CAT Product | 5,000 |
| food Product | 2,578 |
| treats Product | 2,500 |
| hygiene Product | 2,495 |
| toilet Product | 2,427 |
| 1-SKU Product | 3,345 |
| 2-SKU Product | 3,333 |
| 3-SKU Product | 3,322 |
| SKU total | 19,977 |
| Inventory total | 19,977 |
| ACTIVE SKU | 18,164 |
| INACTIVE SKU | 1,813 |
| subscribable=true | 9,991 |
| subscribable=false | 9,986 |
| stockout inventory | 6,651 |
| low inventory 1..5 | 6,650 |
| normal inventory >5 | 6,676 |

## 7. Local import 경계

기존 local importer는 명시적인 manifest override를 사용한다.

```bash
java -jar backend/build/libs/pawcycle-backend-*.jar \
  --spring.profiles.active=local-integration \
  --pawcycle.local-demo-catalog.enabled=true \
  --pawcycle.local-demo-catalog.manifest=file:/absolute/path/catalog-core-10k.json
```

Import 전에는 report checksum과 실제 manifest checksum이 일치해야 한다.

Import 후에는 최소한 다음 DB cardinality가 report와 일치하는지 확인한다.

- Product
- SKU
- Inventory
- Category relationship

같은 manifest 재적용은 business-key 중복을 만들지 않아야 한다.

## 8. Stage 0 / Dataset A 측정 비교

첫 Production workload는 기존 승인된 read-only 경로를 유지한다.

```text
GET /api/products
25 → 50 → 100 → 150 → 200 → 250 RPS
각 단계 30초 warm-up + 120초 measurement
```

Stage 0과 Dataset A에서 workload를 바꾸지 않는다.

비교해야 할 것은 요청 경로가 아니라 **데이터 cardinality 변화**다.

각 실행 evidence에는 최소 다음을 같이 기록한다.

- dataset ID와 manifest checksum
- 실제 Product/SKU/Inventory cardinality
- target/actual RPS
- dropped iteration
- error rate
- p50/p95/p99/max
- Backend HTTP/JVM/Tomcat/Hikari
- app01 Host/Container
- OCI Managed MySQL CPU/Memory/Connection/Statement latency/I/O
- 실행 전후 Production READY / Observability NORMAL

## 9. 현재 Dataset A의 한계

현재 V2 generator는 deterministic하지만 현실적인 full Commerce 분포를 목표로 하지 않는다.

특히 synthetic SKU 재고는 stockout/low/normal이 거의 1/3씩이고 Product의 Category 분포도 비교적 균형적이다.

따라서 Dataset A 결과로 다음을 주장하지 않는다.

- 실제 고객 검색 분포를 재현했다.
- Brand/Facet filter 비용을 재현했다.
- Rating/Review sort 비용을 재현했다.
- 실제 Commerce 전체 데이터 분포를 재현했다.

Dataset A의 목적은 **기존 DB-native 목록 경로에서 Catalog Core cardinality 증가가 어떤 병목을 만드는지 확인하는 것**이다.

## 10. Dataset B는 별도 작업

Canonical Customer Catalog V3는 실제로 다음 관계와 비균일 분포를 가진다.

```text
Product             68
SKU                124
Brand                9
Category            23
Facet definition     8
Facet assignment   148
Image               98
Option group         48
Option value         96
Detail section      204
```

Dataset B는 이 상대 분포를 근거로 별도 profile/version으로 설계한다.

Dataset A generator의 기존 default 분포를 현실적으로 보이게 만들기 위해 조용히 변경하지 않는다.

## 11. 100K 진입 조건

10K 준비가 끝났다는 이유만으로 100K를 바로 만들지 않는다.

```text
Stage 0
→ Core 10K
→ 병목과 한계 분석
→ 100K가 추가 정보를 줄 수 있는지 판단
→ 필요할 때만 100K
```

10K에서 이미 명확한 병목이 재현되면 먼저 원인을 분석하고 가장 작은 개선을 검증한다.

Redis, Queue, Kafka, Search, S3, CDN, Read Replica, Partitioning 같은 기술은 cardinality 숫자만으로 도입하지 않는다.
