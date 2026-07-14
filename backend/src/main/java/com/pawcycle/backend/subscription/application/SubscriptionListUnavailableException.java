package com.pawcycle.backend.subscription.application;

public class SubscriptionListUnavailableException extends RuntimeException {

	public SubscriptionListUnavailableException(Throwable cause) {
		super("구독 목록을 불러오지 못했습니다.", cause);
	}
}
