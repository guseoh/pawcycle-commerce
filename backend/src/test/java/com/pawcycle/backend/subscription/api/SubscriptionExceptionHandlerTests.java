package com.pawcycle.backend.subscription.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import com.pawcycle.backend.subscription.application.SkuNotFoundException;
import com.pawcycle.backend.subscription.application.SkuNotSubscribableException;
import com.pawcycle.backend.subscription.application.SubscriptionCreateFailedException;
import com.pawcycle.backend.subscription.application.SubscriptionDetailUnavailableException;
import com.pawcycle.backend.subscription.application.SubscriptionListUnavailableException;
import com.pawcycle.backend.subscription.application.SubscriptionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.exc.MismatchedInputException;

class SubscriptionExceptionHandlerTests {

	private final SubscriptionExceptionHandler handler = new SubscriptionExceptionHandler();

	@Test
	void expectedDomainErrorsUseApprovedStatusesCodesAndEmptyFieldErrors() {
		assertError(handler.handleSkuNotFound(new SkuNotFoundException()),
				HttpStatus.NOT_FOUND, "SKU_NOT_FOUND");
		assertError(handler.handleSkuNotSubscribable(new SkuNotSubscribableException()),
				HttpStatus.CONFLICT, "SKU_NOT_SUBSCRIBABLE");
		assertError(handler.handleSubscriptionNotFound(new SubscriptionNotFoundException()),
				HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND");
	}

	@Test
	void unexpectedErrorsUseEndpointSpecificSafeResponses() {
		assertSafeError(
				handler.handleCreateFailed(new SubscriptionCreateFailedException(
						new IllegalStateException("subscriptions table create"))),
				"SUBSCRIPTION_CREATE_FAILED",
				"구독을 생성하지 못했습니다.");
		assertSafeError(
				handler.handleListUnavailable(new SubscriptionListUnavailableException(
						new IllegalStateException("subscriptions table list"))),
				"SUBSCRIPTION_LIST_UNAVAILABLE",
				"구독 목록을 불러오지 못했습니다.");
		assertSafeError(
				handler.handleDetailUnavailable(new SubscriptionDetailUnavailableException(
						new IllegalStateException("subscriptions table detail"))),
				"SUBSCRIPTION_DETAIL_UNAVAILABLE",
				"구독 정보를 불러오지 못했습니다.");
	}

	@Test
	void jsonTypeErrorsUseKnownFieldAndMalformedJsonUsesRequestFallback() {
		MismatchedInputException typeMismatch = MismatchedInputException.from(
				(JsonParser) null, Long.class, "wrong type");
		typeMismatch.prependPath(SubscriptionCreateRequest.class, "skuId");

		ResponseEntity<ApiErrorResponse> typeMismatchResponse = handler.handleMalformedJson(
				new HttpMessageNotReadableException("wrong type", typeMismatch, mock(HttpInputMessage.class)));
		ResponseEntity<ApiErrorResponse> malformedResponse = handler.handleMalformedJson(
				new HttpMessageNotReadableException(
						"malformed", new IllegalArgumentException("malformed"), mock(HttpInputMessage.class)));

		assertThat(typeMismatchResponse.getBody()).isNotNull();
		assertThat(typeMismatchResponse.getBody().fieldErrors()).extracting("field").containsExactly("skuId");
		assertThat(malformedResponse.getBody()).isNotNull();
		assertThat(malformedResponse.getBody().fieldErrors()).extracting("field").containsExactly("request");
	}

	private void assertError(
			ResponseEntity<ApiErrorResponse> response,
			HttpStatus status,
			String code) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo(code);
		assertThat(response.getBody().fieldErrors()).isEmpty();
	}

	private void assertSafeError(
			ResponseEntity<ApiErrorResponse> response,
			String code,
			String message) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo(code);
		assertThat(response.getBody().message()).isEqualTo(message);
		assertThat(response.getBody().fieldErrors()).isEmpty();
	}
}
