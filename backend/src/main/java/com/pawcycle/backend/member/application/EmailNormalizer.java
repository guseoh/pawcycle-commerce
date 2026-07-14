package com.pawcycle.backend.member.application;

import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class EmailNormalizer {

	private static final String EMAIL_MESSAGE = "올바른 이메일을 입력해 주세요.";
	private static final String PASSWORD_MESSAGE = "비밀번호를 입력해 주세요.";

	NormalizedLoginCredentials normalize(String email, String password) {
		List<FieldErrorResponse> fieldErrors = new ArrayList<>();
		String normalizedEmail = trimEmail(email);
		if (!isValid(normalizedEmail)) {
			fieldErrors.add(new FieldErrorResponse("email", EMAIL_MESSAGE));
		}
		if (password == null || password.isEmpty()) {
			fieldErrors.add(new FieldErrorResponse("password", PASSWORD_MESSAGE));
		}
		if (!fieldErrors.isEmpty()) {
			throw new AuthValidationException(fieldErrors);
		}
		return new NormalizedLoginCredentials(normalizeDomain(normalizedEmail), password);
	}

	public String normalizeEmail(String email) {
		String normalizedEmail = trimEmail(email);
		if (!isValid(normalizedEmail)) {
			throw new AuthValidationException(List.of(new FieldErrorResponse("email", EMAIL_MESSAGE)));
		}
		return normalizeDomain(normalizedEmail);
	}

	private String trimEmail(String email) {
		if (email == null) {
			return null;
		}
		int start = 0;
		int end = email.length();
		while (start < end && isEdgeWhitespace(email.charAt(start))) {
			start++;
		}
		while (end > start && isEdgeWhitespace(email.charAt(end - 1))) {
			end--;
		}
		return email.substring(start, end);
	}

	private String normalizeDomain(String email) {
		int separator = email.indexOf('@');
		String localPart = email.substring(0, separator);
		String domain = email.substring(separator + 1).toLowerCase(Locale.ROOT);
		return localPart + "@" + domain;
	}

	private boolean isValid(String email) {
		if (email == null || email.isEmpty() || email.length() > 254) {
			return false;
		}
		int separator = -1;
		for (int index = 0; index < email.length(); index++) {
			char character = email.charAt(index);
			if (character > 0x7f || Character.isISOControl(character) || character == ' ') {
				return false;
			}
			if (character == '@') {
				if (separator >= 0) {
					return false;
				}
				separator = index;
			}
		}
		return separator > 0 && separator < email.length() - 1;
	}

	private boolean isEdgeWhitespace(char character) {
		return character == ' ' || character == '\t';
	}
}
