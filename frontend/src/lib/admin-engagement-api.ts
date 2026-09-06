import { requestJson, requestVoid } from "./http/client.ts";

export interface AdminEngagementPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminReview {
  reviewId: number;
  productId: number;
  memberId: number;
  rating: number;
  content: string;
  visible: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminQuestion {
  questionId: number;
  productId: number;
  memberId: number;
  content: string;
  answer: string | null;
  answered: boolean;
  visible: boolean;
  createdAt: string;
  updatedAt: string;
}

function pageQuery(productId: number | null, page: number, size: number): string {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (productId !== null) params.set("productId", String(productId));
  return `?${params.toString()}`;
}

export const adminEngagementApi = {
  reviews: (productId: number | null, page = 0, size = 20) =>
    requestJson<AdminEngagementPage<AdminReview>>(`/api/admin/product-reviews${pageQuery(productId, page, size)}`),
  setReviewVisibility: (reviewId: number, visible: boolean, csrfToken: string) =>
    requestVoid(`/api/admin/product-reviews/${encodeURIComponent(reviewId)}/visibility`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body: JSON.stringify({ visible }),
    }),
  questions: (productId: number | null, page = 0, size = 20) =>
    requestJson<AdminEngagementPage<AdminQuestion>>(`/api/admin/product-questions${pageQuery(productId, page, size)}`),
  answerQuestion: (questionId: number, answer: string, csrfToken: string) =>
    requestJson<AdminQuestion>(`/api/admin/product-questions/${encodeURIComponent(questionId)}/answer`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body: JSON.stringify({ answer }),
    }),
  setQuestionVisibility: (questionId: number, visible: boolean, csrfToken: string) =>
    requestVoid(`/api/admin/product-questions/${encodeURIComponent(questionId)}/visibility`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body: JSON.stringify({ visible }),
    }),
};
