package com.pawcycle.backend.common.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

	private final ApiErrorWriter errorWriter;

	public ApiAccessDeniedHandler(ApiErrorWriter errorWriter) {
		this.errorWriter = errorWriter;
	}

	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException, ServletException {
		if (accessDeniedException instanceof CsrfException) {
			errorWriter.write(response, HttpStatus.FORBIDDEN.value(), "CSRF_INVALID", "CSRF 토큰이 유효하지 않습니다.");
			return;
		}
		errorWriter.write(response, HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED", "접근 권한이 없습니다.");
	}
}
