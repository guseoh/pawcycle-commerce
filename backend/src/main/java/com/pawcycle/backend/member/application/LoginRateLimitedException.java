package com.pawcycle.backend.member.application;

/** The account's login failure budget is exhausted; maps to the edge 429 RATE_LIMITED contract. */
public class LoginRateLimitedException extends RuntimeException {

  public LoginRateLimitedException() {
    super("로그인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
  }
}
