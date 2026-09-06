import { requestJson } from "./http/client.ts";

export type RecommendationStrategy = "PERSONALIZED" | "EXPLORATION" | "POPULAR" | "TRENDING" | "RELATED" | "COMPLEMENTARY";
export interface RecommendationItem { productId: number; name: string; shortDescription: string | null; thumbnailUrl: string | null; category: { categoryId: number; name: string; slug: string } | null; reason: string; strategy: RecommendationStrategy }
export interface RecommendationResponse { requestId: string; products: RecommendationItem[] }

function query(limit?: number, petType?: "DOG" | "CAT"): string {
  const params = new URLSearchParams();
  if (limit !== undefined) params.set("limit", String(limit));
  if (petType) params.set("petType", petType);
  return params.size ? `?${params}` : "";
}

export const recommendationApi = {
  personalized: (petId: number) => requestJson<RecommendationResponse>(`/api/recommendations/products?petId=${encodeURIComponent(petId)}`),
  popular: (limit?: number, petType?: "DOG" | "CAT") => requestJson<RecommendationResponse>(`/api/recommendations/popular${query(limit, petType)}`),
  trending: (limit?: number, petType?: "DOG" | "CAT") => requestJson<RecommendationResponse>(`/api/recommendations/trending${query(limit, petType)}`),
  related: (productId: number | string) => requestJson<RecommendationResponse>(`/api/products/${encodeURIComponent(productId)}/related`),
  complementary: (productId: number | string) => requestJson<RecommendationResponse>(`/api/products/${encodeURIComponent(productId)}/complementary`),
};
