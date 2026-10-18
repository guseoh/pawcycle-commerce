import { productApi, type ProductDetail } from "./api.ts";

export const RECOMMENDATION_VISIBLE_LIMIT = 8;
const CACHE_TTL_MS = 60_000;
const DETAIL_TIMEOUT_MS = 8_000;

export interface RecommendationMetadata {
  brandName: string | null;
  price: number | null;
  priceCaption: string | null;
  averageRating: number | null;
  reviewCount: number;
  subscribable: boolean;
}

/** Show an actual SKU price with its server name, not an invented representative price. */
export function recommendationMetadata(detail: ProductDetail): RecommendationMetadata {
  const sku = detail.skus.find((item) => item.purchasable) ?? detail.skus[0];
  const price = sku && Number.isFinite(sku.price) && sku.price >= 0 ? sku.price : null;
  return {
    brandName: detail.brand?.name ?? null,
    price,
    priceCaption: price !== null ? (sku?.skuName ? `${sku.skuName} 기준` : "상품 옵션 기준") : null,
    averageRating: detail.trust.averageRating,
    reviewCount: detail.trust.reviewCount,
    subscribable: detail.skus.some((item) => item.subscribable),
  };
}

/** Shared queue limits detail traffic across all mounted recommendation sections. */
export function createRecommendationMetadataLoader(
  detail: (id: string, signal?: AbortSignal) => Promise<ProductDetail> = productApi.detail,
  now: () => number = Date.now,
  timeoutMs = DETAIL_TIMEOUT_MS,
) {
  const cache = new Map<number, { value: RecommendationMetadata; expires: number }>();
  const queue: (() => void)[] = [];
  let running = 0;
  function drain() {
    while (running < 3 && queue.length) queue.shift()!();
  }
  function loadOne(id: number, signal: AbortSignal): Promise<RecommendationMetadata | null> {
    if (signal.aborted) return Promise.resolve(null);
    const hit = cache.get(id);
    if (hit && hit.expires > now()) {
      cache.delete(id);
      cache.set(id, hit);
      return Promise.resolve(hit.value);
    }
    cache.delete(id);
    return new Promise((resolve) => {
      let settled = false;
      let timer: ReturnType<typeof setTimeout> | undefined;
      let requestController: AbortController | null = null;
      let releaseSlot = () => {};
      let queued = false;
      const settle = (value: RecommendationMetadata | null, abortRequest = false) => {
        if (settled) return;
        settled = true;
        if (timer !== undefined) clearTimeout(timer);
        signal.removeEventListener("abort", onAbort);
        if (queued) {
          const index = queue.indexOf(start);
          if (index >= 0) queue.splice(index, 1);
          queued = false;
        }
        if (abortRequest) requestController?.abort();
        // Release on abort/timeout even when fetch or a test double ignores AbortSignal.
        releaseSlot();
        resolve(value);
      };
      const onAbort = () => settle(null, true);
      const start = () => {
        queued = false;
        if (settled || signal.aborted) { settle(null, true); return; }
        running += 1;
        let released = false;
        releaseSlot = () => {
          if (released) return;
          released = true;
          running -= 1;
          drain();
        };
        requestController = new AbortController();
        timer = setTimeout(() => settle(null, true), timeoutMs);
        void Promise.resolve().then(() => detail(String(id), requestController!.signal)).then((product) => {
          if (settled || signal.aborted || product.productId !== id) { settle(null, signal.aborted); return; }
          const value = recommendationMetadata(product);
          cache.delete(id);
          cache.set(id, { value, expires: now() + CACHE_TTL_MS });
          while (cache.size > RECOMMENDATION_VISIBLE_LIMIT) cache.delete(cache.keys().next().value!);
          settle(value);
        }).catch(() => settle(null)).finally(() => releaseSlot());
      };
      signal.addEventListener("abort", onAbort, { once: true });
      if (signal.aborted) { settle(null, true); return; }
      queued = true;
      queue.push(start);
      drain();
    });
  }
  return async (productIds: readonly number[], signal: AbortSignal): Promise<Map<number, RecommendationMetadata>> => {
    const ids = [...new Set(productIds.slice(0, RECOMMENDATION_VISIBLE_LIMIT))];
    const entries = await Promise.all(ids.map(async (id) => [id, await loadOne(id, signal)] as const));
    if (signal.aborted) return new Map();
    return new Map(entries.flatMap(([id, value]) => value ? [[id, value] as const] : []));
  };
}

export const loadRecommendationMetadata = createRecommendationMetadataLoader();
