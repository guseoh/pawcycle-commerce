package com.pawcycle.backend.subscription.v2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class V2PetPlanApplicationService {
  private final V2SubscriptionJdbcStore store;
  private final V2SubscriptionApplicationSupport support;

  V2PetPlanApplicationService(
      V2SubscriptionJdbcStore store, tools.jackson.databind.ObjectMapper json, Clock clock) {
    this.store = store;
    this.support = new V2SubscriptionApplicationSupport(json, clock);
  }

  @Transactional
  Map<String, Object> createPet(long memberId, Map<String, Object> body) {
    String name = support.requiredText(body, "name", 50);
    if (name.chars().anyMatch(Character::isISOControl)) throw support.validation("name");
    String type = support.requiredText(body, "petType", 3);
    if (!List.of("DOG", "CAT").contains(type)) throw support.validation("petType");
    return pet(store.findOwnedPet(memberId, store.insertPet(memberId, name, type)));
  }

  @Transactional
  Map<String, Object> updatePet(long memberId, long petId, Map<String, Object> body) {
    store.findOwnedPet(memberId, petId);
    if (!body.containsKey("name") && !body.containsKey("breed") && !body.containsKey("weightKg"))
      throw support.validation("request");
    String name = body.containsKey("name") ? support.requiredText(body, "name", 50) : null;
    if (body.containsKey("name") && name.chars().anyMatch(Character::isISOControl))
      throw support.validation("name");
    String breed = body.containsKey("breed") ? nullableText(body.get("breed"), "breed", 80) : null;
    BigDecimal weight = body.containsKey("weightKg") ? nullableWeight(body.get("weightKg")) : null;
    store.updatePet(
        memberId,
        petId,
        name,
        body.containsKey("name"),
        breed,
        body.containsKey("breed"),
        weight,
        body.containsKey("weightKg"));
    return pet(store.findOwnedPet(memberId, petId));
  }

  @Transactional(readOnly = true)
  Map<String, Object> pets(long memberId, int page, int size) {
    V2SubscriptionData.Page<V2SubscriptionData.Pet> result =
        store.findPets(memberId, page(page, size), size);
    return page(result, result.items().stream().map(this::pet).toList());
  }

  @Transactional(readOnly = true)
  Map<String, Object> pet(long memberId, long petId) {
    return pet(store.findOwnedPet(memberId, petId));
  }

  @Transactional(readOnly = true)
  Map<String, Object> plans(long memberId, long petId, int page, int size) {
    V2SubscriptionData.Pet pet = store.findOwnedPet(memberId, petId);
    V2SubscriptionData.Page<V2SubscriptionData.PlanVersion> result =
        store.findSalePlanVersions(pet.petType(), support.today(), page(page, size), size);
    List<Long> versionIds =
        result.items().stream().map(V2SubscriptionData.PlanVersion::id).toList();
    Map<Long, List<V2SubscriptionData.Item>> items = store.findPlanItems(versionIds);
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
  Map<String, Object> planVersion(long memberId, long petId, long versionId) {
    V2SubscriptionData.Pet pet = store.findOwnedPet(memberId, petId);
    V2SubscriptionData.PlanVersion version = store.findPlanVersion(versionId);
    validateAvailable(pet, version);
    return plan(version);
  }

  private void validateAvailable(
      V2SubscriptionData.Pet pet, V2SubscriptionData.PlanVersion version) {
    LocalDate today = support.today();
    if (!pet.petType().equals(version.targetPetType()))
      throw new V2ApiException(409, "PLAN_PET_TYPE_MISMATCH", "Pet 종과 Plan이 호환되지 않습니다.");
    if (version.currentPlanVersionId() == null
        || version.currentPlanVersionId() != version.id()
        || version.planName() == null
        || !version.onSale()
        || version.migrationOnly()
        || (version.saleStartsOn() != null && version.saleStartsOn().isAfter(today))
        || (version.saleEndsOn() != null && version.saleEndsOn().isBefore(today)))
      throw new V2ApiException(409, "PLAN_NOT_AVAILABLE", "판매 가능한 PlanVersion이 아닙니다.");
  }

  private int page(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size)
      throw support.validation("page");
    return page;
  }

  private Map<String, Object> pet(V2SubscriptionData.Pet pet) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("petId", pet.id());
    result.put("name", pet.name());
    result.put("petType", pet.petType());
    result.put("breed", pet.breed());
    result.put("weightKg", pet.weightKg());
    result.put("profileComplete", pet.profileComplete());
    return result;
  }

  private String nullableText(Object value, String field, int max) {
    if (value == null) return null;
    if (!(value instanceof String)) throw support.validation(field);
    String text = ((String) value).trim();
    if (text.isBlank()
        || text.codePointCount(0, text.length()) > max
        || text.chars().anyMatch(Character::isISOControl)) throw support.validation(field);
    return text;
  }

  private BigDecimal nullableWeight(Object value) {
    if (value == null) return null;
    if (!(value instanceof Number)) throw support.validation("weightKg");
    try {
      BigDecimal weight = new BigDecimal(value.toString());
      if (weight.scale() > 2) throw support.validation("weightKg");
      weight = weight.setScale(2, RoundingMode.UNNECESSARY);
      if (weight.signum() <= 0 || weight.compareTo(new BigDecimal("200.00")) > 0)
        throw support.validation("weightKg");
      return weight;
    } catch (NumberFormatException | ArithmeticException exception) {
      throw support.validation("weightKg");
    }
  }

  private Map<String, Object> plan(V2SubscriptionData.PlanVersion version) {
    return plan(version, store.findPlanItems(version.id()), store.findDeliveryCycles(version.id()));
  }

  private Map<String, Object> plan(
      V2SubscriptionData.PlanVersion version,
      List<V2SubscriptionData.Item> items,
      List<Integer> cycles) {
    Map<String, Object> sale = new LinkedHashMap<>();
    sale.put("onSale", version.onSale());
    sale.put("startsOn", version.saleStartsOn());
    sale.put("endsOn", version.saleEndsOn());
    return Map.of(
        "planId",
        version.planId(),
        "planName",
        version.planName(),
        "targetPetType",
        version.targetPetType(),
        "planVersionId",
        version.id(),
        "packagePriceKrw",
        version.packagePriceKrw(),
        "items",
        items.stream()
            .map(i -> Map.<String, Object>of("skuId", i.skuId(), "quantity", i.quantity()))
            .toList(),
        "allowedDeliveryCycleWeeks",
        cycles,
        "sale",
        sale);
  }

  private Map<String, Object> page(
      V2SubscriptionData.Page<?> page, List<Map<String, Object>> items) {
    return Map.of(
        "page", page.page(), "size", page.size(), "totalElements", page.total(), "items", items);
  }
}
