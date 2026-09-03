package com.pawcycle.backend.interaction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class InteractionService {
  private static final Set<String> CONTEXT_KEYS =
      Set.of(
          "hasTextQuery",
          "petType",
          "category",
          "subcategory",
          "brand",
          "facets",
          "minPrice",
          "maxPrice",
          "sort");
  private static final Set<String> SORT_VALUES =
      Set.of("RECOMMENDED", "NEWEST", "PRICE_ASC", "PRICE_DESC", "RATING", "REVIEW_COUNT");
  private static final java.util.regex.Pattern IDENTIFIER =
      java.util.regex.Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  private static final java.util.regex.Pattern FACET_VALUE =
      java.util.regex.Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} _-]*");
  private final InteractionEventPersistenceAdapter repository;
  private final ObjectMapper json;
  private final Clock clock;

  public InteractionService(InteractionEventPersistenceAdapter repository, ObjectMapper json, Clock clock) {
    this.repository = repository;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public void record(long memberId, List<InteractionEventRequest> events) {
    if (events == null || events.isEmpty() || events.size() > 50) throw invalid("events");
    Instant now = clock.instant();
    for (InteractionEventRequest event : events) {
      if (event == null) throw invalid("event");
      String eventId = uuid(event.eventId(), "eventId");
      InteractionEventType type = eventType(event.type());
      Long productId = optionalLong(event.productId(), "productId");
      Long petId = optionalLong(event.petId(), "petId");
      String source = optionalText(event.source(), "source", 100);
      String requestId = optionalUuid(event.recommendationRequestId(), "recommendationRequestId");
      Map<String, Object> context = context(event.context());
      if ((type == InteractionEventType.PRODUCT_IMPRESSION
              || type == InteractionEventType.PRODUCT_VIEW)
          && productId == null) {
        throw invalid("productId");
      }
      if (type == InteractionEventType.RECOMMENDATION_IMPRESSION
          || type == InteractionEventType.RECOMMENDATION_CLICK) {
        if (productId == null || requestId == null) throw invalid("recommendation event");
      }
      if (petId != null && !repository.petBelongsToMember(petId, memberId)) {
        throw new InteractionException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다.");
      }
      if (productId != null && !repository.productExists(productId)) {
        throw new InteractionException(404, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다.");
      }
      String contextJson = context == null ? null : writeJson(context);
      repository.insert(
          new InteractionEventPersistenceAdapter.InteractionRecord(
              memberId,
              eventId,
              type.name(),
              productId,
              petId,
              source,
              requestId,
              contextJson,
              java.sql.Timestamp.from(now)));
    }
  }

  private Map<String, Object> context(Object value) {
    if (value == null) return null;
    if (!(value instanceof Map<?, ?> raw)) throw invalid("context");
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      if (!(entry.getKey() instanceof String key) || !CONTEXT_KEYS.contains(key))
        throw invalid("context");
      result.put(key, contextValue(key, entry.getValue()));
    }
    if (writeJson(result).length() > 4000) throw invalid("context");
    return result;
  }

  private Object contextValue(String key, Object value) {
    if (value == null) throw invalid("context");
    return switch (key) {
      case "hasTextQuery" -> value instanceof Boolean ? value : invalidValue();
      case "petType" -> identifier(value, "petType", Set.of("DOG", "CAT"), true);
      case "category", "subcategory", "brand" -> identifier(value, key, Set.of(), false);
      case "sort" -> identifier(value, "sort", SORT_VALUES, true);
      case "minPrice", "maxPrice" -> price(value);
      case "facets" -> facets(value);
      default -> invalidValue();
    };
  }

  private String identifier(Object value, String field, Set<String> allowed, boolean enumValue) {
    if (!(value instanceof String text)) throw invalid(field);
    String normalized =
        enumValue ? text.trim().toUpperCase(Locale.ROOT) : text.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()
        || normalized.codePointCount(0, normalized.length()) > 80
        || (!enumValue && !IDENTIFIER.matcher(normalized).matches())
        || (enumValue && !allowed.contains(normalized))) throw invalid(field);
    return normalized;
  }

  private BigDecimal price(Object value) {
    if (!(value instanceof Number)) throw invalid("price");
    try {
      BigDecimal decimal =
          value instanceof BigDecimal exact ? exact : new BigDecimal(value.toString());
      if (decimal.signum() < 0 || decimal.scale() > 2 || decimal.precision() > 18)
        throw invalid("price");
      return decimal.stripTrailingZeros();
    } catch (NumberFormatException exception) {
      throw invalid("price");
    }
  }

  private List<String> facets(Object value) {
    if (!(value instanceof List<?> values) || values.size() > 20) throw invalid("facets");
    List<String> normalized = new java.util.ArrayList<>();
    for (Object item : values) {
      if (!(item instanceof String text)) throw invalid("facets");
      String[] pair = text.split(":", 2);
      if (pair.length != 2) throw invalid("facets");
      String key = pair[0].trim().toLowerCase(Locale.ROOT);
      String facetValue = pair[1].trim();
      if (!IDENTIFIER.matcher(key).matches()
          || key.length() > 80
          || facetValue.isBlank()
          || facetValue.codePointCount(0, facetValue.length()) > 100
          || !FACET_VALUE.matcher(facetValue).matches()) throw invalid("facets");
      normalized.add(key + ":" + facetValue);
    }
    return List.copyOf(normalized);
  }

  private Object invalidValue() {
    throw invalid("context");
  }

  private String writeJson(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception exception) {
      throw invalid("context");
    }
  }

  private InteractionEventType eventType(Object value) {
    if (!(value instanceof String text)) throw invalid("type");
    try {
      return InteractionEventType.valueOf(text.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw invalid("type");
    }
  }

  private String uuid(Object value, String field) {
    if (!(value instanceof String)) throw invalid(field);
    try {
      return UUID.fromString((String) value).toString();
    } catch (IllegalArgumentException exception) {
      throw invalid(field);
    }
  }

  private String optionalUuid(Object value, String field) {
    return value == null ? null : uuid(value, field);
  }

  private Long optionalLong(Object value, String field) {
    if (value == null) return null;
    if (!(value instanceof Number)) throw invalid(field);
    try {
      long parsed = new BigDecimal(value.toString()).longValueExact();
      if (parsed <= 0) throw invalid(field);
      return parsed;
    } catch (NumberFormatException | ArithmeticException exception) {
      throw invalid(field);
    }
  }

  private String optionalText(Object value, String field, int max) {
    if (value == null) return null;
    if (!(value instanceof String text)) throw invalid(field);
    String normalized = text.trim();
    if (normalized.isBlank()
        || normalized.codePointCount(0, normalized.length()) > max
        || normalized.chars().anyMatch(Character::isISOControl)) throw invalid(field);
    return normalized;
  }

  private InteractionException invalid(String field) {
    return new InteractionException(400, "VALIDATION_FAILED", field + " 값을 확인해 주세요.");
  }
}
