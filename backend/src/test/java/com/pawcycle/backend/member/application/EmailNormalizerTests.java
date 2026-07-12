package com.pawcycle.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EmailNormalizerTests {

	private final EmailNormalizer emailNormalizer = new EmailNormalizer();

	@Test
	void trimsOnlyAsciiEdgesPreservesLocalPartAndLowercasesDomain() {
		NormalizedLoginCredentials credentials = emailNormalizer.normalize(
				" \tCase.Sensitive+tag@EXAMPLE.COM\t ", " password ");

		assertThat(credentials.email()).isEqualTo("Case.Sensitive+tag@example.com");
		assertThat(credentials.password()).isEqualTo(" password ");
	}

	@ParameterizedTest
	@MethodSource("invalidEmails")
	void rejectsInvalidEmail(String email) {
		assertThatThrownBy(() -> emailNormalizer.normalize(email, "password"))
				.isInstanceOf(AuthValidationException.class)
				.satisfies(exception -> assertThat(((AuthValidationException) exception).getFieldErrors())
						.extracting("field")
						.containsExactly("email"));
	}

	@Test
	void rejectsNullAndEmptyPasswordWithoutTrimmingIt() {
		assertThatThrownBy(() -> emailNormalizer.normalize("member@example.com", ""))
				.isInstanceOf(AuthValidationException.class)
				.satisfies(exception -> assertThat(((AuthValidationException) exception).getFieldErrors())
						.extracting("field")
						.containsExactly("password"));

		assertThatThrownBy(() -> emailNormalizer.normalize(null, null))
				.isInstanceOf(AuthValidationException.class)
				.satisfies(exception -> assertThat(((AuthValidationException) exception).getFieldErrors())
						.extracting("field")
						.containsExactly("email", "password"));
	}

	@Test
	void acceptsEmailAtMaxLengthBoundary() {
		String email = "a".repeat(242) + "@example.com";

		NormalizedLoginCredentials credentials = emailNormalizer.normalize(email, "password");

		assertThat(credentials.email()).hasSize(254);
	}

	private static Stream<String> invalidEmails() {
		return Stream.of(
				" ",
				"local part@example.com",
				"local@domain example.com",
				"local\n@example.com",
				"local@@example.com",
				"@example.com",
				"local@",
				"가@example.com",
				"a".repeat(243) + "@example.com");
	}
}
