import type { ApiError } from "./api.ts";

export function addonErrorCopy(error: unknown): string | null {
  if (!(error as ApiError)?.code) return null;
  const code = (error as ApiError).code;
  return {
    ADDON_SKU_ALREADY_INCLUDED: "기본 구성에 이미 포함된 옵션입니다.",
    ADDON_CONFLICTS_WITH_PLAN: "추가 상품과 변경하려는 플랜 구성이 겹칩니다. 추가 상품을 먼저 확인해 주세요.",
    ADDON_LIMIT_EXCEEDED: "이번 배송에는 추가 상품을 최대 10개까지 선택할 수 있습니다.",
    ADDON_NOT_AVAILABLE: "현재 추가할 수 없는 상품입니다. 상품 상태를 다시 확인해 주세요.",
    ADDON_NOT_FOUND: "추가 상품을 찾을 수 없습니다. 최신 배송 정보를 다시 확인해 주세요.",
  }[code] ?? null;
}
