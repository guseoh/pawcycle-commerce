# MVP4-DATA-002 Demo Catalog one-shot import

## 경계

이 문서는 Issue #231의 저장소 준비 절차다. 코드·Production script·contract validation을 저장소에 준비하는 것과 실제 Production DB에 Demo Catalog를 적용하는 것은 별도다. 이번 작업에서는 실제 Production `apply`를 실행하지 않는다.

입력은 Backend image에 포함된 기존 `classpath:catalog/demo-catalog.json` 하나만 사용한다. 별도 credential 처리, 직접 SQL seed, 운영 원시 row 출력은 사용하지 않는다.

## 실행 순서

운영자는 기존 Production runtime bundle, current release SHA, image digest state, healthy Backend/MySQL, internal data network를 먼저 확인한다. `import-demo-catalog.sh`는 기존 release lock과 runtime env 계약을 재사용하며, secret 값은 출력하지 않는다.

1. 저장소 준비 후 current release가 승인된 SHA인지 확인한다.
2. dry-run/validation을 실행한다.

   ```bash
   sudo bash infra/production/import-demo-catalog.sh \
     --operation validate \
     --sha <현재-40자-release-sha> \
     --backend-image ghcr.io/<owner>/<repository>-backend
   ```

   `validate`는 manifest 형식·business key·기존 Product/SKU/Plan 관계·충돌 가능성을 읽고 예상 생성 수를 집계한다. DB write와 cache invalidation은 수행하지 않는다.

3. validation 결과를 확인하고 별도 적용 승인을 받은 경우에만 명시적으로 apply한다.

   ```bash
   sudo bash infra/production/import-demo-catalog.sh \
     --operation apply \
     --confirm-apply \
     --sha <현재-40자-release-sha> \
     --backend-image ghcr.io/<owner>/<repository>-backend
   ```

   `apply`는 하나의 transaction에서 Category → Product → SKU/Inventory → Subscription Plan 관계를 처리한다. 동일 business key는 재사용하고, 충돌·postflight 실패가 발생하면 전체 transaction을 rollback한다. 기존 Inventory 수량과 version은 갱신하지 않는다.

4. 명령이 출력하는 aggregate `postflight=PASS`와 catalog/inventory/plan count를 확인한다. 성공 commit 이후에만 Product List cache가 invalidation된다.

## 실패와 복구

- `validate` 실패: 적용하지 않고 manifest, release 또는 기존 데이터 충돌을 조사한다.
- `apply` 실패: 성공으로 기록하거나 자동 재시도하지 않는다. command의 non-zero 결과와 transaction rollback을 확인한 뒤 원인을 조사한다.
- 성공 후 데이터 복구가 필요하면 Demo row를 직접 삭제하거나 SQL seed를 역실행하지 않는다. 기존 Production backup/restore·rollback 승인 절차와 Product Owner/Tech Lead의 별도 결정을 사용한다.

실제 Production 적용 결과, 적용 전후 증거와 복구 증거는 이번 저장소 준비 작업의 결과가 아니다. 실제 적용을 수행할 때만 고위험 `실제 운영 실행` 승인과 별도 실행 보고서를 남긴다.
