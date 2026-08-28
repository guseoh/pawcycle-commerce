package com.pawcycle.backend.recommendation;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api")
class ProductRecommendationController {
	private final ProductRecommendationService service;
	ProductRecommendationController(ProductRecommendationService service) { this.service = service; }
	@GetMapping("/recommendations/popular") RecommendationService.RecommendationResponse popular(@RequestParam(required = false) String petType, @RequestParam(defaultValue = "10") int limit) { return service.popular(petType, limit10(limit)); }
	@GetMapping("/recommendations/trending") RecommendationService.RecommendationResponse trending(@RequestParam(required = false) String petType, @RequestParam(defaultValue = "10") int limit) { return service.trending(petType, limit10(limit)); }
	@GetMapping("/products/{productId}/related") RecommendationService.RecommendationResponse related(@PathVariable long productId, @RequestParam(defaultValue = "4") int limit) { return service.related(productId, limit6(limit)); }
	@GetMapping("/products/{productId}/complementary") RecommendationService.RecommendationResponse complementary(@PathVariable long productId, @RequestParam(defaultValue = "4") int limit) { return service.complementary(productId, limit6(limit)); }
	private int limit10(int value) { if (value < 1 || value > 10) throw new RecommendationException(400, "VALIDATION_FAILED", "limit은 1~10이어야 합니다."); return value; }
	private int limit6(int value) { if (value < 1 || value > 6) throw new RecommendationException(400, "VALIDATION_FAILED", "limit은 1~6이어야 합니다."); return value; }
}
