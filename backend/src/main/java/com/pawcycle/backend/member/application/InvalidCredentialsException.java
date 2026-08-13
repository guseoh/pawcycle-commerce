package com.pawcycle.backend.member.application;

import org.springframework.security.core.AuthenticationException;

public class InvalidCredentialsException extends AuthenticationException {

	public InvalidCredentialsException() {
		super("이메일 또는 비밀번호가 올바르지 않습니다.");
	}
}
