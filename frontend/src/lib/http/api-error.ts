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

export function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<ApiErrorBody>;
  return typeof candidate.code === "string"
    && typeof candidate.message === "string"
    && Array.isArray(candidate.fieldErrors)
    && candidate.fieldErrors.every((fieldError) => (
      fieldError !== null
      && typeof fieldError === "object"
      && typeof (fieldError as FieldError).field === "string"
      && typeof (fieldError as FieldError).message === "string"
    ));
}
