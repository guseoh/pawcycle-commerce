package com.pawcycle.backend.member.address.application;

/** 회원 배송지 유스케이스의 HTTP 변환 가능한 오류이다. */
public class MemberAddressException extends RuntimeException {
  private final int status;
  private final String code;

  public MemberAddressException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}
