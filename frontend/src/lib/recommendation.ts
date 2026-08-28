import { finalProductApi, type RecommendationItem, type RecommendationResponse } from "./final-product-api.ts";

export type RecommendationRequest =
  | { kind: "personalized"; petId: number }
  | { kind: "popular" | "trending"; limit?: number; petType?: "DOG" | "CAT" }
  | { kind: "related" | "complementary"; productId: number | string };

export function recommendationRequestKey(request: RecommendationRequest): string {
  return JSON.stringify(request);
}

export function recommendationRequestPetId(request: RecommendationRequest): number | undefined {
  return request.kind === "personalized" ? request.petId : undefined;
}

export function loadRecommendation(request: RecommendationRequest): Promise<RecommendationResponse> {
  switch (request.kind) {
    case "personalized": return finalProductApi.recommendations.personalized(request.petId);
    case "popular": return finalProductApi.recommendations.popular(request.limit, request.petType);
    case "trending": return finalProductApi.recommendations.trending(request.limit, request.petType);
    case "related": return finalProductApi.recommendations.related(request.productId);
    case "complementary": return finalProductApi.recommendations.complementary(request.productId);
  }
}

export function selectedPersonalizedPetId(pets: readonly { petId: number }[], selectedPetId: number | null): number | null {
  return selectedPetId !== null && pets.some((pet) => pet.petId === selectedPetId) ? selectedPetId : null;
}

export function recommendationStrategyLabel(strategy: RecommendationItem["strategy"]): string | null {
  return strategy === "EXPLORATION" ? "새로운 발견" : null;
}
