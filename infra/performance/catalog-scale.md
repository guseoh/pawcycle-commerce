# Catalog scale measurement contract

This repository change prepares the product read path; it does not execute a 10k/100k load test.

For a measurement run, import a generated external catalog manifest into an isolated local MySQL volume with the same category/product/SKU/inventory relationships as `backend/src/main/resources/catalog/demo-catalog.json`. Record the committed manifest checksum and exact row counts before traffic. Use separate `catalog-10k` and `catalog-100k` manifests; do not put generated rows in Flyway or Java source.

The local importer accepts `--pawcycle.local-demo-catalog.manifest=file:/absolute/path/catalog-10k.json` (or a classpath resource) so the measured seed is the same manifest whose checksum and row counts are recorded.

Each run must include dataset cardinality (categories, products, SKUs and inventory rows), target VU/RPS, warm-up and measurement window, actual journeys (first page, filtered page, sorted page, detail and empty result), latency p50/p95/p99/max, actual RPS, dropped iterations, status errors, JVM/Tomcat/Hikari/MySQL indicators, and Redis hit/miss/error only when the legacy reader is explicitly enabled.

Run the existing k6 capacity harness with `CATALOG_CARDINALITY=10000` or `100000`. The summary records the label; it is not evidence until paired with the seed/import checksum and row-count snapshot. Do not introduce Redis, Queue, Kafka, Search, S3 or CDN from cardinality alone; compare the DB-native query and projection first and require a measured bottleneck.
