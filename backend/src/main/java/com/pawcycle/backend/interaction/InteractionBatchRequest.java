package com.pawcycle.backend.interaction;

import java.util.List;

public record InteractionBatchRequest(List<InteractionEventRequest> events) {}
