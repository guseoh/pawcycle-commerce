package com.pawcycle.backend.member.maintenance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class ProductionAuthSmokeMemberCommand implements ApplicationRunner {

  static final String PASS_MESSAGE = "PASS: production auth smoke member created";

  private final ProductionAuthSmokeMemberService memberService;
  private final InputStream input;
  private final PrintStream output;

  public ProductionAuthSmokeMemberCommand(
      ProductionAuthSmokeMemberService memberService, InputStream input, PrintStream output) {
    this.memberService = memberService;
    this.input = input;
    this.output = output;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    String email;
    String password;
    try {
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
      email = reader.readLine();
      password = reader.readLine();
    } catch (IOException exception) {
      throw new ProductionAuthSmokeMemberCreationException();
    }
    if (email == null || password == null) {
      throw new ProductionAuthSmokeMemberCreationException();
    }

    try {
      memberService.create(email, password);
    } catch (ProductionAuthSmokeMemberCreationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new ProductionAuthSmokeMemberCreationException();
    }
    output.println(PASS_MESSAGE);
  }
}
