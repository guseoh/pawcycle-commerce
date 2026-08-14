package com.pawcycle.backend.subscription.v2;

import java.util.Map;

/** Typed application result; HTTP status and headers are assembled only by the controller. */
record V2SubscriptionOperationResult(
		int status, Map<String, Object> body, String location, String etag, boolean replay) {}
