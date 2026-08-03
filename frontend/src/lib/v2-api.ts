import { ApiError, type ApiErrorBody } from "./api.ts";

export interface Page<T> {
  page: number;
  size: number;
  totalElements: number;
  items: T[];
}

export interface Pet {
  petId: number;
  name: string;
  petType: "DOG" | "CAT";
}

export interface PlanItem { skuId: number; quantity: number }
export interface PlanVersion {
  planId: number;
  planName: string;
  targetPetType: "DOG" | "CAT";
  planVersionId: number;
  packagePriceKrw: number;
  items: PlanItem[];
  allowedDeliveryCycleWeeks: number[];
  sale: { onSale: boolean; startsOn: string; endsOn: string | null };
}

export interface Snapshot {
  planVersionId: number;
  packagePriceKrw: number;
  deliveryCycleWeeks: number;
  items: PlanItem[];
}

export interface Schedule {
  scheduleId: number;
  scheduledDate: string;
  status: "SCHEDULED" | "SKIPPED" | "HELD" | "CANCELED";
  effectiveSnapshotId: number | null;
}

export interface CommandHistory {
  commandType: string;
  result: string;
  occurredAt: string;
}

export interface V2SubscriptionSummary {
  subscriptionId: number;
  pet: Pet | null;
  status: "ACTIVE" | "PAUSED" | "CANCELED";
  version: number;
  currentSnapshot: Snapshot;
  nextScheduledDate: string | null;
}

export interface V2SubscriptionDetail extends V2SubscriptionSummary {
  pendingSnapshot: Snapshot | null;
  schedules: Page<Schedule>;
  commandHistory: Page<CommandHistory>;
}

export interface V2Response<T> { body: T; etag: string | null; location: string | null; replayed: boolean }

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<ApiErrorBody>;
  return typeof candidate.code === "string" && typeof candidate.message === "string" && Array.isArray(candidate.fieldErrors);
}

async function requestV2<T>(path: string, init?: RequestInit): Promise<V2Response<T>> {
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
  return {
    body: body as T,
    etag: response.headers.get("ETag"),
    location: response.headers.get("Location"),
    replayed: response.headers.get("Idempotency-Replayed") === "true",
  };
}

function query(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => { if (value !== undefined) params.set(key, String(value)); });
  const result = params.toString();
  return result ? `?${result}` : "";
}

export function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return `mvp2-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export const v2Api = {
  pets: {
    list: (page = 0, size = 20) => requestV2<Page<Pet>>(`/api/v2/pets${query({ page, size })}`),
    create: (request: Pick<Pet, "name" | "petType">, csrfToken: string) => requestV2<Pet>("/api/v2/pets", {
      method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken }, body: JSON.stringify(request),
    }),
  },
  plans: {
    list: (petId: number, page = 0, size = 20) => requestV2<Page<PlanVersion>>(`/api/v2/subscription-plans${query({ petId, page, size })}`),
    detail: (planVersionId: number, petId: number) => requestV2<PlanVersion>(`/api/v2/subscription-plan-versions/${encodeURIComponent(planVersionId)}${query({ petId })}`),
  },
  subscriptions: {
    list: (page = 0, size = 20) => requestV2<Page<V2SubscriptionSummary>>(`/api/v2/subscriptions${query({ page, size })}`),
    detail: (id: string, pages: { schedulePage?: number; scheduleSize?: number; commandPage?: number; commandSize?: number } = {}) => requestV2<V2SubscriptionDetail>(`/api/v2/subscriptions/${encodeURIComponent(id)}${query(pages)}`),
    create: (request: { petId: number; planVersionId: number; deliveryCycleWeeks: number }, csrfToken: string, idempotencyKey: string) => requestV2<V2SubscriptionDetail>("/api/v2/subscriptions", {
      method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken, "Idempotency-Key": idempotencyKey }, body: JSON.stringify(request),
    }),
    command: (id: number, command: "change-plan" | "skip-next" | "pause" | "resume" | "cancel", request: Record<string, unknown>, csrfToken: string, etag: string, idempotencyKey: string) => requestV2<V2SubscriptionDetail>(`/api/v2/subscriptions/${encodeURIComponent(id)}/commands/${command}`, {
      method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken, "If-Match": etag, "Idempotency-Key": idempotencyKey }, body: JSON.stringify(request),
    }),
  },
};
