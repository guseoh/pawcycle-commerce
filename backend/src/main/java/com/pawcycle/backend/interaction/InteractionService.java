package com.pawcycle.backend.interaction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class InteractionService {
	private static final Set<String> CONTEXT_KEYS = Set.of("hasTextQuery", "petType", "category", "subcategory", "brand", "facets", "minPrice", "maxPrice", "sort");
	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final Clock clock;

	public InteractionService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	@Transactional
	public void record(long memberId, List<Map<String, Object>> events) {
		if (events == null || events.isEmpty() || events.size() > 50) throw invalid("events");
		Instant now = clock.instant();
		for (Map<String, Object> event : events) {
			if (event == null) throw invalid("event");
			String eventId = uuid(event.get("eventId"), "eventId");
			InteractionEventType type = eventType(event.get("type"));
			Long productId = optionalLong(event.get("productId"), "productId");
			Long petId = optionalLong(event.get("petId"), "petId");
			String source = optionalText(event.get("source"), "source", 100);
			String requestId = optionalUuid(event.get("recommendationRequestId"), "recommendationRequestId");
			Map<String, Object> context = context(event.get("context"));
			if (type == InteractionEventType.RECOMMENDATION_IMPRESSION || type == InteractionEventType.RECOMMENDATION_CLICK) {
				if (productId == null || requestId == null) throw invalid("recommendation event");
			}
			if (petId != null && jdbc.queryForObject("SELECT COUNT(*) FROM pets WHERE id=? AND member_id=?", Integer.class, petId, memberId) != 1) {
				throw new InteractionException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다.");
			}
			if (productId != null && jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?", Integer.class, productId) != 1) {
				throw new InteractionException(404, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다.");
			}
			String contextJson = context == null ? null : writeJson(context);
			jdbc.update("""
				INSERT INTO interaction_events(member_id,event_id,event_type,product_id,pet_id,source,recommendation_request_id,context,occurred_at)
				VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id
				""", memberId, eventId, type.name(), productId, petId, source, requestId, contextJson, java.sql.Timestamp.from(now));
		}
	}

	private Map<String, Object> context(Object value) {
		if (value == null) return null;
		if (!(value instanceof Map<?, ?> raw)) throw invalid("context");
		java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : raw.entrySet()) {
			if (!(entry.getKey() instanceof String key) || !CONTEXT_KEYS.contains(key)) throw invalid("context");
			Object item = entry.getValue();
			if (item instanceof String text && text.length() > 100) throw invalid("context");
			if (item instanceof Map<?, ?> || item instanceof List<?>) {
				if (json.writeValueAsString(item).length() > 2000) throw invalid("context");
			}
			result.put(key, item);
		}
		return result;
	}

	private String writeJson(Object value) { try { return json.writeValueAsString(value); } catch (Exception exception) { throw invalid("context"); } }
	private InteractionEventType eventType(Object value) { if (!(value instanceof String text)) throw invalid("type"); try { return InteractionEventType.valueOf(text.trim().toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException exception) { throw invalid("type"); } }
	private String uuid(Object value, String field) { if (!(value instanceof String)) throw invalid(field); try { return UUID.fromString((String) value).toString(); } catch (IllegalArgumentException exception) { throw invalid(field); } }
	private String optionalUuid(Object value, String field) { return value == null ? null : uuid(value, field); }
	private Long optionalLong(Object value, String field) { if (value == null) return null; if (!(value instanceof Number)) throw invalid(field); try { long parsed = new BigDecimal(value.toString()).longValueExact(); if (parsed <= 0) throw invalid(field); return parsed; } catch (NumberFormatException | ArithmeticException exception) { throw invalid(field); } }
	private String optionalText(Object value, String field, int max) { if (value == null) return null; if (!(value instanceof String text)) throw invalid(field); String normalized = text.trim(); if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > max || normalized.chars().anyMatch(Character::isISOControl)) throw invalid(field); return normalized; }
	private InteractionException invalid(String field) { return new InteractionException(400, "VALIDATION_FAILED", field + " 값을 확인해 주세요."); }
}
