package com.pawcycle.backend.member.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(OutputCaptureExtension.class)
class AuthExceptionHandlerTests {

	private final AuthExceptionHandler authExceptionHandler = new AuthExceptionHandler();

	@Test
	void unexpectedExceptionIsLoggedWithoutChangingSafeResponse(CapturedOutput output) {
		RuntimeException exception = new RuntimeException("diagnostic cause");

		ResponseEntity<ApiErrorResponse> response =
				authExceptionHandler.handleUnexpectedException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
				"INTERNAL_ERROR", "요청을 처리할 수 없습니다.", java.util.List.of()));
		assertThat(output).contains(
				"Unexpected exception while handling authentication request",
				"java.lang.RuntimeException: diagnostic cause");
	}
}
