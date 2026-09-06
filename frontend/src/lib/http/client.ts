import { ApiError, isApiErrorBody, type ApiErrorBody } from "./api-error.ts";

export interface ResponseMetadata<T> {
  body: T;
  etag: string | null;
  location: string | null;
  replayed: boolean;
}

function requestInit(init: RequestInit = {}): RequestInit {
  const headers = new Headers(init.headers);
  if (!headers.has("Accept")) headers.set("Accept", "application/json");
  return { ...init, cache: "no-store", credentials: "same-origin", headers };
}

function invalidResponse(status: number): ApiError {
  return new ApiError(status || 500, {
    code: "INVALID_API_RESPONSE",
    message: "서버 응답을 확인할 수 없습니다.",
    fieldErrors: [],
  });
}

function internalError(status: number): ApiError {
  return new ApiError(status || 500, {
    code: "INTERNAL_ERROR",
    message: "요청을 처리하지 못했습니다.",
    fieldErrors: [],
  });
}

async function parseResponse(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    if (response.ok) throw invalidResponse(response.status);
    throw internalError(response.status);
  }
  try {
    return JSON.parse(text);
  } catch {
    if (response.ok) throw invalidResponse(response.status);
    throw internalError(response.status);
  }
}

async function responseWithJson(path: string, init?: RequestInit): Promise<{ response: Response; body: unknown }> {
  const response = await fetch(path, requestInit(init));
  const body = await parseResponse(response);
  if (!response.ok) {
    if (isApiErrorBody(body)) throw new ApiError(response.status, body);
    throw internalError(response.status);
  }
  return { response, body };
}

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const { body } = await responseWithJson(path, init);
  return body as T;
}

export async function requestWithMetadata<T>(path: string, init?: RequestInit): Promise<ResponseMetadata<T>> {
  const { response, body } = await responseWithJson(path, init);
  return {
    body: body as T,
    etag: response.headers.get("ETag"),
    location: response.headers.get("Location"),
    replayed: response.headers.get("Idempotency-Replayed") === "true",
  };
}

export async function requestVoid(path: string, init: RequestInit = {}): Promise<void> {
  const response = await fetch(path, requestInit(init));
  if (response.ok) return;
  const body = await parseResponse(response);
  if (isApiErrorBody(body)) throw new ApiError(response.status, body);
  throw internalError(response.status);
}

export type { ApiErrorBody };
