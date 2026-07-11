package com.pawcycle.backend.member.api;

import com.pawcycle.backend.member.application.AuthApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthApplicationService authApplicationService;

	public AuthController(AuthApplicationService authApplicationService) {
		this.authApplicationService = authApplicationService;
	}

	@GetMapping("/csrf")
	ResponseEntity<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(new CsrfTokenResponse(csrfToken.getToken()));
	}

	@PostMapping("/login")
	MemberIdResponse login(
			@RequestBody LoginRequest loginRequest,
			HttpServletRequest request,
			HttpServletResponse response) {
		Long memberId = authApplicationService.login(
				loginRequest.email(), loginRequest.password(), request, response);
		return new MemberIdResponse(memberId);
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		authApplicationService.logout(request, response);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	MemberIdResponse me(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
		return new MemberIdResponse(principal.memberId());
	}
}
