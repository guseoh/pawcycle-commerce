package com.pawcycle.backend.interaction;

import java.util.Map;

public record InteractionEventRequest(
    String eventId,
    String type,
    Long productId,
    Long petId,
    String source,
    String recommendationRequestId,
    Map<String, Object> context) {}
