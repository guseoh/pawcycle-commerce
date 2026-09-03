package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.api.CreatePetRequest;
import com.pawcycle.backend.subscription.api.PageResponse;
import com.pawcycle.backend.subscription.api.PetResponse;
import com.pawcycle.backend.subscription.api.PlanItemResponse;
import com.pawcycle.backend.subscription.api.PlanSaleResponse;
import com.pawcycle.backend.subscription.api.PlanVersionResponse;
import com.pawcycle.backend.subscription.api.UpdatePetRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PetPlanApplicationService {
  private final SubscriptionPersistenceAdapter store;
  private final SubscriptionApplicationSupport support;

  PetPlanApplicationService(
      SubscriptionPersistenceAdapter store, tools.jackson.databind.ObjectMapper json, Clock clock) {
    this.store = store;
    this.support = new SubscriptionApplicationSupport(json, clock);
  }

  @Transactional
  PetResponse createPet(long memberId, CreatePetRequest request) {
    String name = requiredText(request.name(), "name", 50);
    String type = requiredText(request.petType(), "petType", 3);
    if (!List.of("DOG", "CAT").contains(type)) throw support.validation("petType");
    return pet(store.findOwnedPet(memberId, store.insertPet(memberId, name, type)));
  }

  @Transactional
  PetResponse updatePet(long memberId, long petId, UpdatePetRequest request) {
    store.findOwnedPet(memberId, petId);
    if (!request.hasChanges()) throw support.validation("request");
    String name = request.isNamePresent() ? requiredText(request.getName(), "name", 50) : null;
    String breed =
        request.isBreedPresent() ? nullableText(request.getBreed(), "breed", 80) : null;
    BigDecimal weight =
        request.isWeightKgPresent() ? nullableWeight(request.getWeightKg()) : null;
    store.updatePet(
        memberId,
        petId,
        name,
        request.isNamePresent(),
        breed,
        request.isBreedPresent(),
        weight,
        request.isWeightKgPresent());
    return pet(store.findOwnedPet(memberId, petId));
  }

  @Transactional(readOnly = true)
  PageResponse<PetResponse> pets(long memberId, int page, int size) {
    PageProjection<PetProjection> result = store.findPets(memberId, page(page, size), size);
    return page(result, result.items().stream().map(this::pet).toList());
  }

  @Transactional(readOnly = true)
  PetResponse pet(long memberId, long petId) {
    return pet(store.findOwnedPet(memberId, petId));
  }

  @Transactional(readOnly = true)
  PageResponse<PlanVersionResponse> plans(long memberId, long petId, int page, int size) {
    PetProjection pet = store.findOwnedPet(memberId, petId);
    PageProjection<PlanVersionProjection> result =
        store.findSalePlanVersions(pet.petType(), support.today(), page(page, size), size);
    List<Long> versionIds = result.items().stream().map(PlanVersionProjection::id).toList();
    Map<Long, List<SubscriptionItemProjection>> items = store.findPlanItems(versionIds);
    Map<Long, List<Integer>> cycles = store.findDeliveryCycles(versionIds);
    return page(
        result,
        result.items().stream()
            .map(
                version ->
                    plan(
                        version,
                        items.getOrDefault(version.id(), List.of()),
                        cycles.getOrDefault(version.id(), List.of())))
            .toList());
  }

  @Transactional(readOnly = true)
  PlanVersionResponse planVersion(long memberId, long petId, long versionId) {
    PetProjection pet = store.findOwnedPet(memberId, petId);
    PlanVersionProjection version = store.findPlanVersion(versionId);
    validateAvailable(pet, version);
    return plan(version);
  }

  private void validateAvailable(PetProjection pet, PlanVersionProjection version) {
    LocalDate today = support.today();
    if (!pet.petType().equals(version.targetPetType()))
      throw new SubscriptionApiException(409, "PLAN_PET_TYPE_MISMATCH", "Pet 종과 Plan이 호환되지 않습니다.");
    if (version.currentPlanVersionId() == null
        || version.currentPlanVersionId() != version.id()
        || version.planName() == null
        || !version.onSale()
        || version.migrationOnly()
        || (version.saleStartsOn() != null && version.saleStartsOn().isAfter(today))
        || (version.saleEndsOn() != null && version.saleEndsOn().isBefore(today)))
      throw new SubscriptionApiException(409, "PLAN_NOT_AVAILABLE", "판매 가능한 PlanVersion이 아닙니다.");
  }

  private int page(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size)
      throw support.validation("page");
    return page;
  }

  private PetResponse pet(PetProjection pet) {
    return new PetResponse(
        pet.id(), pet.name(), pet.petType(), pet.breed(), pet.weightKg(), pet.profileComplete());
  }

  private String requiredText(String value, String field, int max) {
    if (value == null) throw support.validation(field);
    String trimmed = value.trim();
    if (trimmed.isBlank()
        || trimmed.codePointCount(0, trimmed.length()) > max
        || trimmed.chars().anyMatch(Character::isISOControl)) throw support.validation(field);
    return trimmed;
  }

  private String nullableText(String value, String field, int max) {
    if (value == null) return null;
    return requiredText(value, field, max);
  }

  private BigDecimal nullableWeight(BigDecimal value) {
    if (value == null) return null;
    try {
      if (value.scale() > 2) throw support.validation("weightKg");
      BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
      if (normalized.signum() <= 0 || normalized.compareTo(new BigDecimal("200.00")) > 0)
        throw support.validation("weightKg");
      return normalized;
    } catch (ArithmeticException exception) {
      throw support.validation("weightKg");
    }
  }

  private PlanVersionResponse plan(PlanVersionProjection version) {
    return plan(version, store.findPlanItems(version.id()), store.findDeliveryCycles(version.id()));
  }

  private PlanVersionResponse plan(
      PlanVersionProjection version,
      List<SubscriptionItemProjection> items,
      List<Integer> cycles) {
    return new PlanVersionResponse(
        version.planId(),
        version.planName(),
        version.targetPetType(),
        version.id(),
        version.packagePriceKrw(),
        items.stream().map(item -> new PlanItemResponse(item.skuId(), item.quantity())).toList(),
        cycles,
        new PlanSaleResponse(version.onSale(), version.saleStartsOn(), version.saleEndsOn()));
  }

  private <T> PageResponse<T> page(PageProjection<?> value, List<T> items) {
    return new PageResponse<>(value.page(), value.size(), value.total(), items);
  }
}
