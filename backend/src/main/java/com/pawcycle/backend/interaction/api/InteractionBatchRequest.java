package com.pawcycle.backend.interaction.api;

import java.util.List;

public record InteractionBatchRequest(List<InteractionEventRequest> events) {}
