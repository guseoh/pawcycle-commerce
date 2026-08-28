import type { CycleSuggestionResponse } from "./v2-api.ts";

export function cycleSuggestionCopy(response: CycleSuggestionResponse): string | null {
  if (!response.suggestion) return null;
  const allowed = response.allowedDeliveryCycleWeeks.join(", ");
  return `최근 성공 배송 간격의 중앙값은 ${response.medianSuccessfulIntervalWeeks}주이고, 현재 허용 주기는 ${allowed}주입니다. ${response.suggestion.deliveryCycleWeeks}주 주기를 추천합니다.`;
}
