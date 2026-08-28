export type ComparisonSelectionResult = {
  ids: number[];
  accepted: boolean;
  reason: "added" | "removed" | "duplicate" | "maximum";
};

export function toggleComparisonId(current: readonly number[], productId: number, maximum = 3): ComparisonSelectionResult {
  if (current.includes(productId)) return { ids: current.filter((id) => id !== productId), accepted: true, reason: "removed" };
  if (current.length >= maximum) return { ids: [...current], accepted: false, reason: "maximum" };
  return { ids: [...current, productId], accepted: true, reason: "added" };
}

export function parseComparisonIds(values: readonly string[]): { ids: number[]; error: string | null } {
  const ids = values.map((value) => Number(value));
  if (values.length < 2 || values.length > 3 || ids.some((id) => !Number.isInteger(id) || id <= 0) || new Set(ids).size !== ids.length) {
    return { ids: [], error: "비교할 상품은 서로 다른 2~3개를 선택해 주세요." };
  }
  return { ids, error: null };
}

export function comparisonIdsKey(values: readonly string[]): string {
  return JSON.stringify(values);
}

export function comparisonIdsFromKey(key: string): string[] {
  try {
    const values: unknown = JSON.parse(key);
    return Array.isArray(values) && values.every((value) => typeof value === "string") ? values : [];
  } catch {
    return [];
  }
}

export function comparisonHref(ids: readonly number[]): string {
  const query = new URLSearchParams();
  ids.forEach((id) => query.append("productId", String(id)));
  return `/compare?${query}`;
}
