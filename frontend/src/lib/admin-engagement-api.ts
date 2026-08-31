import { ApiError, type ApiErrorBody } from "./api.ts";

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

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<ApiErrorBody>;
  return typeof candidate.code === "string" && typeof candidate.message === "string" && Array.isArray(candidate.fieldErrors);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/admin${path}`, {
    ...init,
    credentials: "same-origin",
    cache: "no-store",
    headers: { Accept: "application/json", ...init.headers },
  });
  const text = await response.text();
  let body: unknown = null;
  try { body = text ? JSON.parse(text) : null; } catch {
    throw new ApiError(response.status, { code: "INVALID_API_RESPONSE", message: "서버 응답을 확인할 수 없습니다.", fieldErrors: [] });
  }
  if (!response.ok) {
    if (isApiErrorBody(body)) throw new ApiError(response.status, body);
    throw new ApiError(response.status || 500, { code: "INTERNAL_ERROR", message: "요청을 처리하지 못했습니다.", fieldErrors: [] });
  }
  return body as T;
}

function pageQuery(productId: number | null, page: number, size: number): string {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (productId !== null) params.set("productId", String(productId));
  return `?${params.toString()}`;
}

export const adminEngagementApi = {
  reviews: (productId: number | null, page = 0, size = 20) =>
    request<AdminEngagementPage<AdminReview>>(`/product-reviews${pageQuery(productId, page, size)}`),
  setReviewVisibility: (reviewId: number, visible: boolean, csrfToken: string) =>
    request<void>(`/product-reviews/${encodeURIComponent(reviewId)}/visibility`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body: JSON.stringify({ visible }),
    }),
  questions: (productId: number | null, page = 0, size = 20) =>
    request<AdminEngagementPage<AdminQuestion>>(`/product-questions${pageQuery(productId, page, size)}`),
  answerQuestion: (questionId: number, answer: string, csrfToken: string) =>
    request<AdminQuestion>(`/product-questions/${encodeURIComponent(questionId)}/answer`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body: JSON.stringify({ answer }),
    }),
  setQuestionVisibility: (questionId: number, visible: boolean, csrfToken: string) =>
    request<void>(`/product-questions/${encodeURIComponent(questionId)}/visibility`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body: JSON.stringify({ visible }),
    }),
};
