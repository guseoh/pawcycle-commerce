export interface FieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors: FieldError[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors;
  }
}

export interface ProductPrice {
  skuId: number;
  skuName: string;
  price: number;
}

export interface ProductSummary {
  productId: number;
  name: string;
  petType: string;
  shortDescription: string;
  thumbnailUrl: string | null;
  skuPriceSummary: { skuPrices: ProductPrice[] };
  hasSubscribableSku: boolean;
}

export interface ProductListResponse {
  products: ProductSummary[];
}

export interface ProductSku {
  skuId: number;
  skuName: string;
  price: number;
  subscribable: boolean;
  availableDeliveryCycles: number[];
}

export interface ProductDetail {
  productId: number;
  name: string;
  petType: string;
  description: string | null;
  thumbnailUrl: string | null;
  skus: ProductSku[];
}

export interface MemberResponse {
  memberId: number;
}

export interface CsrfResponse {
  token: string;
}

export interface SubscriptionCreateRequest {
  skuId: number;
  quantity: number;
  deliveryCycleWeeks: number;
}

export interface SubscriptionCreateResponse {
  subscriptionId: number;
  nextOrderDate: string;
}

export interface SubscriptionProduct {
  productId: number;
  name: string;
}

export interface SubscriptionSku {
  skuId: number;
  skuName: string;
}

export interface SubscriptionSummary {
  subscriptionId: number;
  product: SubscriptionProduct;
  sku: SubscriptionSku;
  quantity: number;
  deliveryCycleWeeks: number;
  nextOrderDate: string;
}

export interface SubscriptionListResponse {
  subscriptions: SubscriptionSummary[];
}

export interface SubscriptionDetail extends SubscriptionSummary {
  sku: SubscriptionSku & { price: number };
  createdDate: string;
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (!value || typeof value !== "object") {
    return false;
  }
  const candidate = value as Partial<ApiErrorBody>;
  return (
    typeof candidate.code === "string" &&
    typeof candidate.message === "string" &&
    Array.isArray(candidate.fieldErrors)
  );
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    cache: "no-store",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      ...init?.headers,
    },
  });
  const text = await response.text();
  let body: unknown = null;

  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      throw new ApiError(response.status || 500, {
        code: "INVALID_API_RESPONSE",
        message: "서버 응답을 확인할 수 없습니다.",
        fieldErrors: [],
      });
    }
  }

  if (!response.ok) {
    if (isApiErrorBody(body)) {
      throw new ApiError(response.status, body);
    }
    throw new ApiError(response.status, {
      code: "INTERNAL_ERROR",
      message: "요청을 처리하지 못했습니다.",
      fieldErrors: [],
    });
  }

  return body as T;
}

async function requestVoid(path: string, init: RequestInit): Promise<void> {
  const response = await fetch(path, {
    ...init,
    cache: "no-store",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      ...init.headers,
    },
  });
  if (response.ok) {
    return;
  }
  const body = (await response.json().catch(() => null)) as unknown;
  if (isApiErrorBody(body)) {
    throw new ApiError(response.status, body);
  }
  throw new ApiError(response.status, {
    code: "INTERNAL_ERROR",
    message: "요청을 처리하지 못했습니다.",
    fieldErrors: [],
  });
}

export const productApi = {
  list: () => requestJson<ProductListResponse>("/api/products"),
  detail: (productId: string) =>
    requestJson<ProductDetail>(`/api/products/${encodeURIComponent(productId)}`),
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

export const subscriptionApi = {
  create: (request: SubscriptionCreateRequest, csrfToken: string) =>
    requestJson<SubscriptionCreateResponse>("/api/subscriptions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": csrfToken,
      },
      body: JSON.stringify(request),
    }),
  list: () => requestJson<SubscriptionListResponse>("/api/subscriptions"),
  detail: (subscriptionId: string) =>
    requestJson<SubscriptionDetail>(
      `/api/subscriptions/${encodeURIComponent(subscriptionId)}`,
    ),
};
