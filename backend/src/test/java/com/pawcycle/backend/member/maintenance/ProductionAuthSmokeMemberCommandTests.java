package com.pawcycle.backend.member.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class ProductionAuthSmokeMemberCommandTests {

	private final ProductionAuthSmokeMemberService memberService =
			mock(ProductionAuthSmokeMemberService.class);

	@Test
	void readsTwoStandardInputLinesAndPrintsOnlyFixedPassMessage() {
		String email = runtimeEmail();
		String password = runtimePassword();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ProductionAuthSmokeMemberCommand command = command(email + "\n" + password + "\n", output);

		command.run(new DefaultApplicationArguments());

		verify(memberService).create(email, password);
		assertThat(output.toString(StandardCharsets.UTF_8))
				.isEqualTo(ProductionAuthSmokeMemberCommand.PASS_MESSAGE + System.lineSeparator())
				.doesNotContain(email, password);
	}

	@Test
	void missingEmailOrPasswordFailsWithoutOutput() {
		ByteArrayOutputStream missingEmailOutput = new ByteArrayOutputStream();
		assertThatThrownBy(() -> command("", missingEmailOutput).run(new DefaultApplicationArguments()))
				.isInstanceOf(ProductionAuthSmokeMemberCreationException.class);
		assertThat(missingEmailOutput).hasToString("");

		ByteArrayOutputStream missingPasswordOutput = new ByteArrayOutputStream();
		assertThatThrownBy(() -> command(runtimeEmail() + "\n", missingPasswordOutput)
				.run(new DefaultApplicationArguments()))
				.isInstanceOf(ProductionAuthSmokeMemberCreationException.class);
		assertThat(missingPasswordOutput).hasToString("");
	}

	@Test
	void serviceFailureDoesNotPrintCredentialOrPassMessage() {
		String email = runtimeEmail();
		String password = runtimePassword();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		doThrow(new IllegalStateException("SENSITIVE_PERSISTENCE_DETAIL"))
				.when(memberService).create(email, password);

		assertThatThrownBy(() -> command(email + "\n" + password + "\n", output)
				.run(new DefaultApplicationArguments()))
				.isInstanceOf(ProductionAuthSmokeMemberCreationException.class)
				.hasMessageNotContaining(email)
				.hasMessageNotContaining(password)
				.hasMessageNotContaining("SENSITIVE_PERSISTENCE_DETAIL")
				.hasNoCause();

		assertThat(output.toString(StandardCharsets.UTF_8))
				.doesNotContain(email, password, ProductionAuthSmokeMemberCommand.PASS_MESSAGE);
	}

	private ProductionAuthSmokeMemberCommand command(String input, ByteArrayOutputStream output) {
		return new ProductionAuthSmokeMemberCommand(
				memberService,
				new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
				new PrintStream(output, true, StandardCharsets.UTF_8));
	}

	private String runtimeEmail() {
		return "ops-019-" + UUID.randomUUID() + "@example.test";
	}

	private String runtimePassword() {
		return UUID.randomUUID().toString();
	}
}
