package com.pawcycle.backend.subscription.application;

public class SubscriptionDetailUnavailableException extends RuntimeException {

	public SubscriptionDetailUnavailableException(Throwable cause) {
		super("구독 정보를 불러오지 못했습니다.", cause);
	}
}
