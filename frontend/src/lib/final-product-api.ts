import { ApiError, type ApiErrorBody, type ProductSort } from "./api.ts";

export type RecommendationStrategy =
  | "PERSONALIZED"
  | "EXPLORATION"
  | "POPULAR"
  | "TRENDING"
  | "RELATED"
  | "COMPLEMENTARY";

export interface RecommendationItem {
  productId: number;
  name: string;
  shortDescription: string | null;
  thumbnailUrl: string | null;
  category: { categoryId: number; name: string; slug: string } | null;
  reason: string;
  strategy: RecommendationStrategy;
}

export interface RecommendationResponse {
  requestId: string;
  products: RecommendationItem[];
}

export type InteractionType =
  | "PRODUCT_IMPRESSION"
  | "PRODUCT_VIEW"
  | "SEARCH"
  | "FILTER"
  | "RECOMMENDATION_IMPRESSION"
  | "RECOMMENDATION_CLICK";

export interface InteractionContext {
  hasTextQuery?: boolean;
  petType?: "DOG" | "CAT";
  category?: string;
  subcategory?: string;
  brand?: string;
  facets?: string[];
  minPrice?: number;
  maxPrice?: number;
  sort?: ProductSort;
}

export interface InteractionEvent {
  eventId: string;
  type: InteractionType;
  productId?: number;
  petId?: number;
  recommendationRequestId?: string;
  source: string;
  context?: InteractionContext;
}

export interface ReviewSummary {
  status: "INSUFFICIENT_REVIEWS" | "AVAILABLE" | "UNAVAILABLE";
  summary: string | null;
  reviewCount: number;
  averageRating: number | null;
}

export interface ProductComparisonFact {
  productId: number;
  name: string;
  thumbnailUrl: string | null;
  brand: string;
  category: string;
  representativePrice: number | null;
  compareAtPrice: number | null;
  discountRate: number | null;
  averageRating: number | null;
  reviewCount: number;
  subscriptionEligible: boolean;
  purchasable: boolean;
  facets: string[];
}

export interface ProductComparisonResponse {
  products: ProductComparisonFact[];
  aiStatus: "AVAILABLE" | "UNAVAILABLE";
  aiSummary: string | null;
}

export interface ReorderTimingItem {
  productId: number;
  productName: string;
  lastPurchasedDate: string;
  expectedReorderDate: string;
  state: "OVERDUE" | "DUE_SOON";
  purchaseCount: number;
}

export interface ReorderTimingResponse {
  items: ReorderTimingItem[];
}

export interface OrderSubscriptionOption {
  planVersionId: number;
  planName: string;
  matchingProductIds: number[];
  compatibleOwnedPetIds: number[];
  allowedDeliveryCycleWeeks: number[];
  packagePriceKrw: number;
}

export interface OrderSubscriptionOptionsResponse {
  orderId: number;
  options: OrderSubscriptionOption[];
}

export function reorderTimingItems(response: ReorderTimingResponse): ReorderTimingItem[] {
  return response.items;
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<ApiErrorBody>;
  return typeof candidate.code === "string" && typeof candidate.message === "string" && Array.isArray(candidate.fieldErrors);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    cache: "no-store",
    credentials: "same-origin",
    headers: { Accept: "application/json", ...init?.headers },
  });
  const text = await response.text();
  let body: unknown = null;
  if (text) {
    try { body = JSON.parse(text); } catch {
      throw new ApiError(response.status || 500, { code: "INVALID_API_RESPONSE", message: "서버 응답을 확인할 수 없습니다.", fieldErrors: [] });
    }
  }
  if (!response.ok) {
    if (isApiErrorBody(body)) throw new ApiError(response.status, body);
    throw new ApiError(response.status || 500, { code: "INTERNAL_ERROR", message: "요청을 처리하지 못했습니다.", fieldErrors: [] });
  }
  return body as T;
}

async function requestVoid(path: string, init: RequestInit): Promise<void> {
  const response = await fetch(path, {
    ...init,
    cache: "no-store",
    credentials: "same-origin",
    headers: { Accept: "application/json", ...init.headers },
  });
  if (response.ok) return;
  const body = await response.json().catch(() => null);
  if (isApiErrorBody(body)) throw new ApiError(response.status, body);
  throw new ApiError(response.status || 500, { code: "INTERNAL_ERROR", message: "요청을 처리하지 못했습니다.", fieldErrors: [] });
}

function recommendationQuery(limit?: number, petType?: "DOG" | "CAT"): string {
  const query = new URLSearchParams();
  if (limit !== undefined) query.set("limit", String(limit));
  if (petType) query.set("petType", petType);
  return query.size ? `?${query}` : "";
}

export function newInteractionEventId(): string | null {
  if (typeof crypto === "undefined") return null;
  if (typeof crypto.randomUUID === "function") {
    try { return crypto.randomUUID(); } catch { /* fall through to getRandomValues */ }
  }
  if (typeof crypto.getRandomValues !== "function") return null;
  try {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  } catch { return null; }
}

export function createInteractionEvent(event: Omit<InteractionEvent, "eventId">): InteractionEvent | null {
  const eventId = newInteractionEventId();
  return eventId === null ? null : { eventId, ...event };
}

export const finalProductApi = {
  recommendations: {
    personalized: (petId: number) => request<RecommendationResponse>(`/api/recommendations/products?petId=${encodeURIComponent(petId)}`),
    popular: (limit?: number, petType?: "DOG" | "CAT") => request<RecommendationResponse>(`/api/recommendations/popular${recommendationQuery(limit, petType)}`),
    trending: (limit?: number, petType?: "DOG" | "CAT") => request<RecommendationResponse>(`/api/recommendations/trending${recommendationQuery(limit, petType)}`),
    related: (productId: number | string) => request<RecommendationResponse>(`/api/products/${encodeURIComponent(productId)}/related`),
    complementary: (productId: number | string) => request<RecommendationResponse>(`/api/products/${encodeURIComponent(productId)}/complementary`),
  },
  interactions: {
    send: (events: InteractionEvent[], csrfToken: string) => events.length === 0 ? Promise.resolve() : requestVoid("/api/interactions", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body: JSON.stringify({ events }),
    }),
  },
  reorderTiming: () => request<ReorderTimingResponse>("/api/recommendations/reorder-timing"),
  orderSubscriptionOptions: (orderId: number | string) => request<OrderSubscriptionOptionsResponse>(`/api/orders/${encodeURIComponent(orderId)}/subscription-options`),
  reviewSummary: (productId: number | string) => request<ReviewSummary>(`/api/products/${encodeURIComponent(productId)}/reviews/summary`),
  compare: (productIds: readonly number[]) => {
    const query = new URLSearchParams();
    productIds.forEach((productId) => query.append("productId", String(productId)));
    return request<ProductComparisonResponse>(`/api/products/compare?${query}`);
  },
};
