package com.pawcycle.backend.member.maintenance;

public class ProductionAuthSmokeMemberCreationException extends RuntimeException {

  private static final String MESSAGE = "Production auth smoke member creation failed.";

  public ProductionAuthSmokeMemberCreationException() {
    super(MESSAGE);
  }
}
