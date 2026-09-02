package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.application.ProductEngagementService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminProductEngagementController {
  private final ProductEngagementService service;

  @GetMapping("/product-reviews")
  ReviewViews.AdminPage reviews(
      @RequestParam(required = false) Long productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.adminReviews(productId, page, size);
  }

  @PatchMapping("/product-reviews/{reviewId}/visibility")
  void reviewVisibility(
      @PathVariable long reviewId,
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody ReviewVisibilityRequest request) {
    service.setReviewVisibility(reviewId, request.visible(), principal.memberId());
  }

  @GetMapping("/product-questions")
  QuestionViews.AdminPage questions(
      @RequestParam(required = false) Long productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.adminQuestions(productId, page, size);
  }

  @PutMapping("/product-questions/{questionId}/answer")
  QuestionViews.AdminQuestion answer(
      @PathVariable long questionId,
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody QuestionAnswerRequest request) {
    return service.answerQuestion(questionId, request.answer(), principal.memberId());
  }

  @PatchMapping("/product-questions/{questionId}/visibility")
  void questionVisibility(
      @PathVariable long questionId,
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody ReviewVisibilityRequest request) {
    service.setQuestionVisibility(questionId, request.visible(), principal.memberId());
  }
}
