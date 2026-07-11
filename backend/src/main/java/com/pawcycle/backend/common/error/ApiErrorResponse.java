package com.pawcycle.backend.common.error;

import java.util.List;

public record ApiErrorResponse(String code, String message, List<FieldErrorResponse> fieldErrors) {

	public static ApiErrorResponse withoutFieldErrors(String code, String message) {
		return new ApiErrorResponse(code, message, List.of());
	}
}
