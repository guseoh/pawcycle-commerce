package com.pawcycle.backend.subscription.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawcycle.backend.subscription.api.*;
import com.pawcycle.backend.subscription.domain.SubscriptionFailure;
import com.pawcycle.backend.subscription.model.*;
import com.pawcycle.backend.subscription.persistence.*;
import com.pawcycle.backend.subscription.persistence.projection.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class PetRecurringBoundaryTests {
  private final ObjectMapper json = new ObjectMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneOffset.UTC);
  private final SubscriptionAggregatePersistence store = mock(SubscriptionAggregatePersistence.class);
  private final RepeatCommerceQueryRepository queries = mock(RepeatCommerceQueryRepository.class);
  private final PetPlanApplicationService pets = new PetPlanApplicationService(store, json, clock);
  private final FrozenPetPlanApplicationService oldPets = new FrozenPetPlanApplicationService(store, json, clock);
  private final LocalDate today = LocalDate.of(2026, 10, 18);

  static IntStream patches() { return IntStream.range(0, 27); }

  @ParameterizedTest @MethodSource("patches")
  void everyPatchPresenceNullValueCombinationKeepsErrorsNormalizationAndCallOrder(int pattern) {
    var request = new UpdatePetRequest();
    int name = pattern % 3, breed = pattern / 3 % 3, weight = pattern / 9;
    if (name > 0) request.readName(name == 1 ? null : "  pet  ");
    if (breed > 0) request.readBreed(breed == 1 ? null : "  breed  ");
    if (weight > 0) request.readWeightKg(weight == 1 ? null : new BigDecimal("12.3"));
    when(store.findOwnedPet(7, 9)).thenReturn(new PetProjection(9, "pet", "DOG", "breed", new BigDecimal("12.30")));
    same(store, () -> oldPets.updatePet(7, 9, request), () -> PetResponse.from(pets.updatePet(7, 9,
        new PetUpdateCommand(request.getName(), request.isNamePresent(), request.getBreed(),
            request.isBreedPresent(), request.getWeightKg(), request.isWeightKgPresent()))));
    if (name == 2 || (name == 0 && (breed > 0 || weight > 0))) verify(store).updatePet(7, 9,
        name == 2 ? "pet" : null, name > 0, breed == 2 ? "breed" : null, breed > 0,
        weight == 2 ? new BigDecimal("12.30") : null, weight > 0);
  }

  @ParameterizedTest @ValueSource(strings = {"0", "-1", "200.01", "1.001", "1.000", "0.01", "200"})
  void weightValidationScaleAndRangeStayIdentical(String number) {
    when(store.findOwnedPet(7, 9)).thenReturn(new PetProjection(9, "pet", "DOG", null, null));
    var request = new UpdatePetRequest(); request.readWeightKg(new BigDecimal(number));
    same(store, () -> oldPets.updatePet(7, 9, request), () -> PetResponse.from(pets.updatePet(7, 9,
        new PetUpdateCommand(null, false, null, false, request.getWeightKg(), true))));
  }

  @Test void createValidationOwnershipAndPageOverflowKeepFailureOrder() {
    for (String name : Arrays.asList(null, " ", "bad\nname", "a".repeat(51), " pet ")) {
      for (String type : Arrays.asList(null, "DOG", " CAT ", "dog")) {
        when(store.insertPet(7, "pet", "DOG")).thenReturn(9L);
        when(store.insertPet(7, "pet", "CAT")).thenReturn(9L);
        when(store.findOwnedPet(7, 9)).thenReturn(new PetProjection(9, "pet", "DOG", null, null));
        same(store, () -> oldPets.createPet(7, new CreatePetRequest(name, type)),
            () -> PetResponse.from(pets.createPet(7, new PetCreateCommand(name, type))));
      }
    }
    when(store.findOwnedPet(7, 99)).thenThrow(new SubscriptionFailure(SubscriptionFailure.Kind.NOT_FOUND, "PET_NOT_FOUND", "missing"));
    same(store, () -> oldPets.updatePet(7, 99, new UpdatePetRequest()),
        () -> pets.updatePet(7, 99, new PetUpdateCommand(null, false, null, false, null, false)));
    same(store, () -> oldPets.pets(7, Integer.MAX_VALUE, 100), () -> pets.pets(7, Integer.MAX_VALUE, 100));
  }

  @Test void plansBatchAndSingleWindowsPricesAndListsMatchFrozen() {
    when(store.findOwnedPet(7, 9)).thenReturn(new PetProjection(9, "pet", "DOG", null, null));
    var items = List.of(new SubscriptionItemProjection(91, 2), new SubscriptionItemProjection(11, 1));
    var cycles = List.of(8, 2, 4);
    when(store.findPlanItems(List.of(12L))).thenReturn(Map.of(12L, items));
    when(store.findDeliveryCycles(List.of(12L))).thenReturn(Map.of(12L, cycles));
    when(store.findPlanItems(12)).thenReturn(items); when(store.findDeliveryCycles(12)).thenReturn(cycles);
    for (int shift : List.of(-1, 0, 1)) for (boolean start : List.of(false, true)) {
      var version = new PlanVersionProjection(1, "plan", 12L, "DOG", true,
          start ? today.plusDays(shift) : null, start ? null : today.plusDays(shift),
          12, 9007199254740991L, false);
      when(store.findPlanVersion(12)).thenReturn(version);
      when(store.findSalePlanVersions("DOG", today, 0, 20)).thenReturn(new PageProjection<>(0, 20, 1, List.of(version)));
      same(store, () -> oldPets.planVersion(7, 9, 12), () -> PlanVersionResponse.from(pets.planVersion(7, 9, 12)));
      same(store, () -> oldPets.plans(7, 9, 0, 20), () -> PageResponse.from(pets.plans(7, 9, 0, 20), PlanVersionResponse::from));
    }
  }

  @ParameterizedTest @ValueSource(ints = {2, 3, 4, 5, 6})
  void reorderMedianRecentFiveTodaySevenBoundaryStableTiesAndLimitMatch(int purchases) {
    var rows = new ArrayList<RepeatCommerceQueryRepository.PurchaseRow>();
    for (int product = 0; product < 25; product++) for (int n = 0; n < purchases; n++) {
      rows.add(new RepeatCommerceQueryRepository.PurchaseRow(product, product == 0 ? null : "p" + product,
          today.minusDays(n * 10L + product % 5 * 3L)));
    }
    when(queries.findOneTimePurchaseDates(7)).thenReturn(rows);
    same(queries, () -> new FrozenRepeatPurchaseApplicationService(queries, clock).reorderTiming(7),
        () -> RepeatPurchaseResponse.from(new RepeatPurchaseApplicationService(queries, clock).reorderTiming(7)));
  }

  @Test void conversionIntersectionNullablePriceListsAndErrorPrecedenceMatch() {
    var old = new FrozenSubscriptionConversionApplicationService(queries, clock);
    var service = new SubscriptionConversionApplicationService(queries, clock);
    same(queries, () -> old.subscriptionOptions(7, 9), () -> service.subscriptionOptions(7, 9));
    when(queries.findPaidOneTimeOrder(7, 9)).thenReturn(Optional.of(9L));
    when(queries.findOrderProductIds(9)).thenReturn(new LinkedHashSet<>(List.of(2L, 1L)));
    when(queries.findAvailablePlanOptions(today, 7)).thenReturn(List.of(
        new RepeatCommerceQueryRepository.PlanOptionRow(3, null, new BigDecimal("123.40"), List.of(1L, 8L, 2L), List.of(9L, 4L), List.of(8, 2)),
        new RepeatCommerceQueryRepository.PlanOptionRow(4, "empty", null, List.of(8L), List.of(), List.of())));
    same(queries, () -> old.subscriptionOptions(7, 9), () -> SubscriptionOptionsResponse.from(service.subscriptionOptions(7, 9)));
    var response = SubscriptionOptionsResponse.from(service.subscriptionOptions(7, 9));
    response.options().clear(); // Original conversion returned an ArrayList.
    assertThat(response.options()).isEmpty();
  }

  @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 8})
  void cycleMedianAndTieFavorCurrentThenLargerAndNullableSuggestionMatch(int current) {
    var old = new FrozenSubscriptionCycleSuggestionApplicationService(queries);
    var service = new SubscriptionCycleSuggestionApplicationService(queries);
    same(queries, () -> old.cycleSuggestion(7, 9), () -> service.cycleSuggestion(7, 9));
    for (String state : List.of("PAUSED", "ACTIVE")) {
      when(queries.findRuntimeSubscription(7, 9)).thenReturn(Optional.of(new RepeatCommerceQueryRepository.SubscriptionRow(9, state, current, 11)));
      for (int count : List.of(2, 3, 4)) {
        when(queries.findSuccessfulScheduleDates(9)).thenReturn(IntStream.range(0, count).mapToObj(n -> today.minusDays(n * 21L)).toList());
        when(queries.findSourcePlanVersionId(11)).thenReturn(12L);
        when(queries.findAllowedDeliveryCycles(12)).thenReturn(List.of(2, 4, 8));
        same(queries, () -> old.cycleSuggestion(7, 9), () -> SubscriptionCycleSuggestionResponse.from(service.cycleSuggestion(7, 9)));
      }
    }
  }

  @Test void publicConstructorsNullEmptyNestedOrderAndMutableListsRemainCompatible() throws Exception {
    var options = new RepeatCommerceReadModel.SubscriptionOptions(1, new ArrayList<>(Arrays.asList(null,
        new RepeatCommerceReadModel.SubscriptionOptions.Option(2, null, null, List.of(3L, 1L), List.of(), null))));
    assertThat(json.writeValueAsString(SubscriptionOptionsResponse.from(options))).isEqualTo(json.writeValueAsString(
        new SubscriptionOptionsResponse(1, new ArrayList<>(Arrays.asList(null,
            new SubscriptionOptionsResponse.Option(2, null, null, List.of(3L, 1L), List.of(), null))))));
    assertThat(RepeatPurchaseResponse.from(new RepeatCommerceReadModel.RepeatPurchase(null)).items()).isNull();
    assertThat(SubscriptionOptionsResponse.from(new RepeatCommerceReadModel.SubscriptionOptions(1, null)).options()).isNull();
    assertThat(PlanVersionResponse.from(new SubscriptionReadModel.PlanVersion(1, null, null, 2, 3, null, null, null)))
        .isEqualTo(new PlanVersionResponse(1, null, null, 2, 3, null, null, null));
    assertThat(SubscriptionCycleSuggestionResponse.from(new RepeatCommerceReadModel.SubscriptionCycleSuggestion(1, 2, 3, null, null)))
        .isEqualTo(new SubscriptionCycleSuggestionResponse(1, 2, 3, null, null));
  }

  private void same(Object mock, Supplier<?> frozen, Supplier<?> actual) {
    clearInvocations(mock); String expected = outcome(frozen); var calls = calls(mock);
    clearInvocations(mock); assertThat(outcome(actual)).isEqualTo(expected); assertThat(calls(mock)).isEqualTo(calls);
  }
  private List<String> calls(Object mock) {
    return mockingDetails(mock).getInvocations().stream()
        .map(i -> i.getMethod().getName() + Arrays.deepToString(i.getArguments())).toList();
  }
  private String outcome(Supplier<?> action) {
    try { return json.writeValueAsString(action.get()); }
    catch (SubscriptionFailure failure) { return failure.getClass().getName() + ":" + failure.kind() + ":" + failure.code() + ":" + failure.getMessage(); }
  }
}
