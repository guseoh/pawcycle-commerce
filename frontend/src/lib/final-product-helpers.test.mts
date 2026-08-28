import assert from "node:assert/strict";
import test from "node:test";
import { addonErrorCopy } from "./subscription-addon.ts";
import { comparisonHref, parseComparisonIds, toggleComparisonId } from "./comparison-selection.ts";
import { interactionContext } from "./catalog-filters.ts";
import { cycleSuggestionCopy } from "./subscription-cycle-suggestion.ts";
import { recommendationRequestKey, recommendationStrategyLabel, selectedPersonalizedPetId } from "./recommendation.ts";
import { notificationCopy, notificationHref } from "./notification-routing.ts";
import { parseSubscriptionStartQuery, subscriptionStartQueryKey } from "./subscription-start-query.ts";
import { petDraft, petPatch, petProfileLoadState, petWeightError } from "./pet-profile.ts";
import { productRouteMatches } from "./product-view.ts";
import { isLatestRequest } from "./request-generation.ts";

const pet = { petId: 4, name: "보리", petType: "DOG" as const, breed: null, weightKg: null, profileComplete: false };

test("상품 비교 선택은 로컬 2~3개 규칙과 반복 query를 지킨다", () => {
  assert.deepEqual(toggleComparisonId([], 10).ids, [10]);
  assert.equal(toggleComparisonId([10, 11, 12], 13).accepted, false);
  assert.deepEqual(toggleComparisonId([10, 11], 10).ids, [11]);
  assert.deepEqual(parseComparisonIds(["10", "11"]), { ids: [10, 11], error: null });
  assert.notEqual(parseComparisonIds(["10", "10"]).error, null);
  assert.notEqual(parseComparisonIds(["1,2"]).error, null);
  assert.equal(comparisonHref([10, 11]), "/compare?productId=10&productId=11");
});

test("검색 상호작용은 구조화된 필터만 남기고 raw q를 보내지 않는다", () => {
  const context = interactionContext({ q: "raw-query", page: 0, size: 12, sort: "PRICE_ASC", petType: "DOG", category: "food", subcategory: "dry", brand: "brand-a", facet: ["size:small"], minPrice: 100, maxPrice: 200 });
  assert.deepEqual(context, { hasTextQuery: true, petType: "DOG", category: "food", subcategory: "dry", brand: "brand-a", facets: ["size:small"], minPrice: 100, maxPrice: 200, sort: "PRICE_ASC" });
  assert.equal("q" in context, false);
});

test("추천 요청 descriptor는 부모 rerender에서 동일한 요청을 유지하고 자동 반려동물 선택을 하지 않는다", () => {
  const request = { kind: "popular" as const, limit: 4 };
  assert.equal(recommendationRequestKey(request), recommendationRequestKey({ ...request }));
  assert.equal(selectedPersonalizedPetId([{ petId: 4 }], null), null);
  assert.equal(selectedPersonalizedPetId([{ petId: 4 }], 4), 4);
  assert.equal(selectedPersonalizedPetId([{ petId: 4 }], 9), null);
});

test("상품 view와 리뷰 요약은 현재 identity·최신 generation만 허용한다", () => {
  assert.equal(productRouteMatches("301", 301), true);
  assert.equal(productRouteMatches("302", 301), false);
  assert.equal(productRouteMatches("1,2", 1), false);
  assert.equal(isLatestRequest(2, 2), true);
  assert.equal(isLatestRequest(1, 2), false);
});

test("pet patch는 변경된 허용 필드만 보내고 blank breed·weight를 null로 만든다", () => {
  assert.deepEqual(petDraft(pet), { name: "보리", breed: "", weightKg: "" });
  assert.deepEqual(petPatch(pet, { name: "보리", breed: " 푸들 ", weightKg: "" }), { breed: "푸들" });
  assert.deepEqual(petPatch({ ...pet, breed: "푸들", weightKg: 5 }, { name: "보리", breed: "", weightKg: "" }), { breed: null, weightKg: null });
  assert.deepEqual(petPatch(pet, { name: "보리", breed: "", weightKg: "" }), {});
  assert.notEqual(petWeightError("abc"), null);
  assert.notEqual(petWeightError("Infinity"), null);
  assert.deepEqual(petPatch(pet, { name: "보리", breed: "", weightKg: "abc" }), {});
});

test("최종 UI 보조 규칙은 내부 enum을 노출하지 않고 reminder를 subscription으로 연결한다", () => {
  assert.equal(recommendationStrategyLabel("EXPLORATION"), "새로운 발견");
  assert.equal(recommendationStrategyLabel("POPULAR"), null);
  assert.equal(notificationCopy({ type: "SUBSCRIPTION_DELIVERY_REMINDER" }), "정기배송이 곧 예정되어 있어요.");
  assert.equal(notificationHref({ referenceType: "SCHEDULE", referenceId: 88, subscriptionId: 7 }), "/subscriptions/7");
  assert.equal(notificationHref({ referenceType: "SCHEDULE", referenceId: 88, subscriptionId: null }), "/subscriptions");
  assert.equal(addonErrorCopy({ code: "ADDON_LIMIT_EXCEEDED" }), "이번 배송에는 추가 상품을 최대 10개까지 선택할 수 있습니다.");
  assert.equal(cycleSuggestionCopy({ subscriptionId: 7, currentDeliveryCycleWeeks: 4, medianSuccessfulIntervalWeeks: 5, allowedDeliveryCycleWeeks: [2, 4, 6], suggestion: { deliveryCycleWeeks: 6 } }), "최근 성공 배송 간격의 중앙값은 5주이고, 현재 허용 주기는 2, 4, 6주입니다. 6주 주기를 추천합니다.");
  assert.equal(cycleSuggestionCopy({ subscriptionId: 7, currentDeliveryCycleWeeks: 4, medianSuccessfulIntervalWeeks: 5, allowedDeliveryCycleWeeks: [2, 4, 6], suggestion: null }), null);
});

test("구독 시작 query는 양의 정수만 additive prefill 값으로 허용한다", () => {
  assert.deepEqual(parseSubscriptionStartQuery(new URLSearchParams("petId=4&planVersionId=12&deliveryCycleWeeks=4&fromOrderId=9")), { petId: 4, planVersionId: 12, deliveryCycleWeeks: 4, fromOrderId: 9 });
  assert.deepEqual(parseSubscriptionStartQuery(new URLSearchParams("petId=0&planVersionId=nope&deliveryCycleWeeks=-1")), { petId: null, planVersionId: null, deliveryCycleWeeks: null, fromOrderId: null });
  assert.equal(subscriptionStartQueryKey({ petId: 4, planVersionId: 12, deliveryCycleWeeks: 4, fromOrderId: 9 }), '[4,12,4,9]');
  assert.notEqual(subscriptionStartQueryKey({ petId: 4, planVersionId: 12, deliveryCycleWeeks: 4, fromOrderId: 9 }), subscriptionStartQueryKey({ petId: 5, planVersionId: 13, deliveryCycleWeeks: 6, fromOrderId: 10 }));
  assert.equal(petProfileLoadState("authenticated", null, "불러오기 실패"), "error");
});
