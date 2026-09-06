export { ApiError } from "./http/api-error.ts";
export type { ApiErrorBody, FieldError } from "./http/api-error.ts";
import { requestJson, requestVoid } from "./http/client.ts";

export interface ProductPrice {
  skuId: number;
  skuName: string;
  price: number;
}
export interface Category { categoryId: number; name: string; slug: string }
export interface CategoryListResponse { items: Category[] }
export interface Brand { brandId: number; name: string; slug: string; logoUrl: string | null }
export interface DiscoveryCategoryChild extends Category { displayOrder: number }
export interface DiscoveryCategory extends DiscoveryCategoryChild { children: DiscoveryCategoryChild[] }
export interface CatalogFacet {
  key: string;
  name: string;
  displayOrder: number;
  options: { optionId: number; value: string; displayOrder: number }[];
}
export interface CatalogDiscovery {
  categories: DiscoveryCategory[];
  brands: (Brand & { displayOrder: number })[];
  categoryFacets: { categorySlug: string; facets: CatalogFacet[] }[];
}
export type ProductSort = "RECOMMENDED" | "NEWEST" | "PRICE_ASC" | "PRICE_DESC" | "RATING" | "REVIEW_COUNT";
export interface ProductFilters {
  q?: string;
  petType?: string;
  category?: string;
  subcategory?: string;
  brand?: string;
  facet?: string[];
  minPrice?: number;
  maxPrice?: number;
  subscribable?: boolean;
  purchasable?: boolean;
  page?: number;
  size?: number;
  sort?: ProductSort;
}

export interface ProductSummary {
  productId: number;
  name: string;
  petType: string;
  shortDescription: string;
  thumbnailUrl: string | null;
  category: Category;
  skuPriceSummary: { skuPrices: ProductPrice[] };
  hasSubscribableSku: boolean;
  representativePrice: number | null;
  brand: Brand | null;
  compareAtPrice: number | null;
  discountRate: number | null;
  averageRating: number | null;
  reviewCount: number;
  purchasable: boolean;
}

export interface ProductListResponse {
  items: ProductSummary[];
  /** Legacy reader/cache compatibility; new pageable responses use items. */
  products?: ProductSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProductSku {
  skuId: number;
  skuName: string;
  price: number;
  compareAtPrice: number | null;
  discountRate: number | null;
  selectedOptions: SelectedOption[];
  subscribable: boolean;
  availableDeliveryCycles: number[];
  availableQuantity: number;
  purchasable: boolean;
}

export interface ProductImage { imageId: number; imageUrl: string; altText: string | null; displayOrder: number; imageType: string }
export interface ProductOptionValue { optionValueId: number; value: string; displayOrder: number }
export interface ProductOptionGroup { optionGroupId: number; name: string; displayOrder: number; values: ProductOptionValue[] }
export interface SelectedOption { optionGroupId: number; groupName: string; optionValueId: number; value: string }

export interface ProductDetail {
  productId: number;
  name: string;
  shortDescription: string | null;
  petType: string;
  description: string | null;
  thumbnailUrl: string | null;
  category: Category;
  brand: Brand | null;
  images: ProductImage[];
  optionGroups: ProductOptionGroup[];
  detailSections: ProductDetailSection[];
  trust: ProductTrust;
  skus: ProductSku[];
  purchasable: boolean;
}

export interface ProductDetailSection {
  sectionId: number;
  title: string;
  body: string;
  displayOrder: number;
  visible: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductTrust {
  averageRating: number | null;
  reviewCount: number;
  questionCount: number;
}

export interface EngagementPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProductReview {
  reviewId: number;
  rating: number;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProductQuestion {
  questionId: number;
  content: string;
  answer: string | null;
  answered: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MemberResponse {
  memberId: number;
}

export interface CsrfResponse {
  token: string;
}

export const productApi = {
  list: (filters: ProductFilters = {}) => {
    const query = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (Array.isArray(value)) value.forEach((item) => query.append(key, item));
      else if (value !== undefined && value !== "") query.set(key, String(value));
    });
    return requestJson<ProductListResponse>(`/api/products${query.size ? `?${query}` : ""}`);
  },
  detail: (productId: string) =>
    requestJson<ProductDetail>(`/api/products/${encodeURIComponent(productId)}`),
  reviews: (productId: string, page = 0, size = 20) =>
    requestJson<EngagementPage<ProductReview>>(`/api/products/${encodeURIComponent(productId)}/reviews?page=${page}&size=${size}`),
  myReview: (productId: string) =>
    requestJson<ProductReview>(`/api/products/${encodeURIComponent(productId)}/reviews/me`),
  createReview: (productId: string, rating: number, content: string, csrfToken: string) =>
    requestJson<ProductReview>(`/api/products/${encodeURIComponent(productId)}/reviews`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body: JSON.stringify({ rating, content }),
    }),
  updateReview: (reviewId: number, rating: number, content: string, csrfToken: string) =>
    requestJson<ProductReview>(`/api/reviews/${encodeURIComponent(reviewId)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body: JSON.stringify({ rating, content }),
    }),
  deleteReview: (reviewId: number, csrfToken: string) =>
    requestVoid(`/api/reviews/${encodeURIComponent(reviewId)}`, {
      method: "DELETE",
      headers: { "X-CSRF-TOKEN": csrfToken },
    }),
  questions: (productId: string, page = 0, size = 20) =>
    requestJson<EngagementPage<ProductQuestion>>(`/api/products/${encodeURIComponent(productId)}/questions?page=${page}&size=${size}`),
  createQuestion: (productId: string, content: string, csrfToken: string) =>
    requestJson<ProductQuestion>(`/api/products/${encodeURIComponent(productId)}/questions`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body: JSON.stringify({ content }),
    }),
};

export const categoryApi = {
  list: () => requestJson<CategoryListResponse>("/api/categories"),
};

export const catalogDiscoveryApi = {
  get: () => requestJson<CatalogDiscovery>("/api/catalog/discovery"),
};

export const authApi = {
  csrf: () => requestJson<CsrfResponse>("/api/auth/csrf"),
  me: () => requestJson<MemberResponse>("/api/auth/me"),
  login: (email: string, password: string, csrfToken: string) =>
    requestJson<MemberResponse>("/api/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": csrfToken,
      },
      body: JSON.stringify({ email, password }),
    }),
  logout: (csrfToken: string) =>
    requestVoid("/api/auth/logout", {
      method: "POST",
      headers: { "X-CSRF-TOKEN": csrfToken },
    }),
};
