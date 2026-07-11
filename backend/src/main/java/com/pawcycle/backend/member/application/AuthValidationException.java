package com.pawcycle.backend.member.application;

import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.util.List;

public class AuthValidationException extends RuntimeException {

	private final List<FieldErrorResponse> fieldErrors;

	AuthValidationException(List<FieldErrorResponse> fieldErrors) {
		super("로그인 입력값이 유효하지 않습니다.");
		this.fieldErrors = List.copyOf(fieldErrors);
	}

	public List<FieldErrorResponse> getFieldErrors() {
		return fieldErrors;
	}
}
