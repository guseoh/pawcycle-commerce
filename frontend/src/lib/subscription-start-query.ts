export interface SubscriptionStartQuery {
  petId: number | null;
  planVersionId: number | null;
  deliveryCycleWeeks: number | null;
  fromOrderId: number | null;
}

export function subscriptionStartQueryKey(query: SubscriptionStartQuery): string {
  return JSON.stringify([query.petId, query.planVersionId, query.deliveryCycleWeeks, query.fromOrderId]);
}

function positiveInteger(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

export function parseSubscriptionStartQuery(query: URLSearchParams): SubscriptionStartQuery {
  return {
    petId: positiveInteger(query.get("petId")),
    planVersionId: positiveInteger(query.get("planVersionId")),
    deliveryCycleWeeks: positiveInteger(query.get("deliveryCycleWeeks")),
    fromOrderId: positiveInteger(query.get("fromOrderId")),
  };
}
