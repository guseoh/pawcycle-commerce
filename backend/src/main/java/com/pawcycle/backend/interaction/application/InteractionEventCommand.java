package com.pawcycle.backend.interaction.application;

import java.util.Map;

/** Typed application input; the HTTP request maps into this at the controller boundary. */
public record InteractionEventCommand(
    String eventId, String type, Long productId, Long petId, String source,
    String recommendationRequestId, Map<String, Object> context) {}
