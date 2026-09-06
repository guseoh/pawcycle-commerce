import { requestJson } from "./http/client.ts";

export interface ReviewSummary { status: "INSUFFICIENT_REVIEWS" | "AVAILABLE" | "UNAVAILABLE"; summary: string | null; reviewCount: number; averageRating: number | null }

export const productEngagementApi = {
  reviewSummary: (productId: number | string) => requestJson<ReviewSummary>(`/api/products/${encodeURIComponent(productId)}/reviews/summary`),
};
