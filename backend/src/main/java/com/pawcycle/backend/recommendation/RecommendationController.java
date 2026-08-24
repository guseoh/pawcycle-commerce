package com.pawcycle.backend.recommendation;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations/products")
class RecommendationController {
	private final RecommendationService service;

	RecommendationController(RecommendationService service) { this.service = service; }

	@GetMapping
	RecommendationService.RecommendationResponse products(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @RequestParam long petId) {
		return service.recommend(principal.memberId(), petId);
	}
}
