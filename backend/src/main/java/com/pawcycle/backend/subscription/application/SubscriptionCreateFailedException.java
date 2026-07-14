package com.pawcycle.backend.subscription.application;

public class SubscriptionCreateFailedException extends RuntimeException {

	public SubscriptionCreateFailedException(Throwable cause) {
		super("구독을 생성하지 못했습니다.", cause);
	}
}
