import { productApi, type ProductFilters, type ProductListResponse } from "./api.ts";
import { catalogPriceRangeError } from "./catalog-filters.ts";

// Each URL request owns its callbacks. Cleanup also suppresses late failures.
export function loadProductResults(filters: ProductFilters, success: (response: ProductListResponse) => void, failure: (error: unknown) => void) {
  let active = true;
  const rangeError = catalogPriceRangeError(filters);
  const result = rangeError ? Promise.reject(new Error(rangeError)) : productApi.list(filters);
  void result.then((response) => { if (active) success(response); })
    .catch((error: unknown) => { if (active) failure(error); });
  return () => { active = false; };
}
