package com.pawcycle.backend.subscription.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SubscriptionCreateRequest(
		@JsonDeserialize(using = StrictIntegralJsonDeserializers.LongValue.class)
		@NotNull(message = "SKU를 선택해 주세요.") Long skuId,
		@JsonDeserialize(using = StrictIntegralJsonDeserializers.IntegerValue.class)
		@NotNull(message = "수량을 입력해 주세요.")
		@Min(value = 1, message = "수량은 1개 이상 10개 이하로 입력해 주세요.")
		@Max(value = 10, message = "수량은 1개 이상 10개 이하로 입력해 주세요.") Integer quantity,
		@JsonDeserialize(using = StrictIntegralJsonDeserializers.IntegerValue.class)
		@NotNull(message = "배송 주기를 선택해 주세요.")
		@AllowedDeliveryCycle Integer deliveryCycleWeeks) {
}
