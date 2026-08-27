import { productApi, type ProductFilters, type ProductListResponse } from "./api.ts";

// Each URL request owns its callbacks. Cleanup also suppresses late failures.
export function loadProductResults(filters: ProductFilters, success: (response: ProductListResponse) => void, failure: (error: unknown) => void) {
  let active = true;
  void productApi.list(filters).then((response) => { if (active) success(response); })
    .catch((error: unknown) => { if (active) failure(error); });
  return () => { active = false; };
}
