import { requestJson } from "./http/client.ts";

export interface ProductComparisonFact { productId: number; name: string; thumbnailUrl: string | null; brand: string; category: string; representativePrice: number | null; compareAtPrice: number | null; discountRate: number | null; averageRating: number | null; reviewCount: number; subscriptionEligible: boolean; purchasable: boolean; facets: string[] }
export interface ProductComparisonResponse { products: ProductComparisonFact[]; aiStatus: "AVAILABLE" | "UNAVAILABLE"; aiSummary: string | null }

export const productComparisonApi = {
  compare: (productIds: readonly number[]) => {
    const query = new URLSearchParams();
    productIds.forEach((productId) => query.append("productId", String(productId)));
    return requestJson<ProductComparisonResponse>(`/api/products/compare?${query}`);
  },
};
