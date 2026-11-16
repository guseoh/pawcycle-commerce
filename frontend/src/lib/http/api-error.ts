export interface FieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors?: FieldError[];
  /** Same value as the X-Request-ID response header and the backend log `requestId`. */
  requestId?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  /** Request id from the error body, otherwise from the X-Request-ID header; null when absent. */
  readonly requestId: string | null;

  constructor(status: number, body: ApiErrorBody, headerRequestId: string | null = null) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors ?? [];
    this.requestId = body.requestId ?? headerRequestId ?? null;
  }
}

export function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<ApiErrorBody>;
  return typeof candidate.code === "string"
    && typeof candidate.message === "string"
    && (!("requestId" in candidate) || typeof candidate.requestId === "string")
    && (!("fieldErrors" in candidate) || (
      Array.isArray(candidate.fieldErrors)
      && candidate.fieldErrors.every((fieldError) => (
        fieldError !== null
        && typeof fieldError === "object"
        && typeof (fieldError as FieldError).field === "string"
        && typeof (fieldError as FieldError).message === "string"
      ))
    ));
}

/**
 * Adds the request id to a message for server-side failures (5xx), where a customer may need to
 * quote it when asking for help. Client errors (4xx) are left as they are.
 */
export function withRequestReference(message: string, error: unknown): string {
  if (!(error instanceof ApiError) || error.status < 500 || !error.requestId) return message;
  return `${message} (문의 시 요청 ID: ${error.requestId})`;
}
