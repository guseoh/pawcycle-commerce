package com.pawcycle.backend.interaction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class InteractionServiceTests {
	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final InteractionService service = new InteractionService(
			jdbc, new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));

	@Test
	void batchOverFiftyIsRejectedBeforePersistence() {
		List<Map<String, Object>> events = new ArrayList<>();
		for (int index = 0; index < 51; index++) {
			events.add(Map.of("eventId", UUID.randomUUID().toString(), "type", "PRODUCT_VIEW"));
		}

		assertThatThrownBy(() -> service.record(10L, events))
				.isInstanceOf(InteractionException.class)
				.hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
		verifyNoInteractions(jdbc);
	}

	@Test
	void rawSearchTextIsRejected() {
		Map<String, Object> event = Map.of(
				"eventId", UUID.randomUUID().toString(),
				"type", "SEARCH",
				"context", Map.of("q", "private search text"));

		assertThatThrownBy(() -> service.record(10L, List.of(event)))
				.isInstanceOf(InteractionException.class)
				.hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
		verifyNoInteractions(jdbc);
	}

	@Test
	void petOwnershipIsValidatedBeforeEventInsert() {
		when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(99L), eq(10L))).thenReturn(0);
		Map<String, Object> event = Map.of(
				"eventId", UUID.randomUUID().toString(),
				"type", "PRODUCT_VIEW",
				"petId", 99L);

		assertThatThrownBy(() -> service.record(10L, List.of(event)))
				.isInstanceOf(InteractionException.class)
				.hasFieldOrPropertyWithValue("code", "PET_NOT_FOUND");
	}
}
