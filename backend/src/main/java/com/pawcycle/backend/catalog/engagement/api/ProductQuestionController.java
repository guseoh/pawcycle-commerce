package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.engagement.application.ProductEngagementService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ProductQuestionController {
    private final ProductEngagementService service;

    @GetMapping("/api/products/{productId}/questions")
    QuestionViews.Page questions(@PathVariable long productId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.questions(productId, page, size); }

    @PostMapping("/api/products/{productId}/questions")
    QuestionViews.Question create(@PathVariable long productId, @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
            @Valid @RequestBody EngagementRequests.QuestionCreate request) {
        return service.createQuestion(productId, principal.memberId(), request);
    }

    @PatchMapping("/api/product-questions/{questionId}")
    QuestionViews.Question update(@PathVariable long questionId, @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
            @RequestBody EngagementRequests.QuestionPatch request) {
        return service.updateQuestion(questionId, principal.memberId(), request);
    }

    @DeleteMapping("/api/product-questions/{questionId}")
    void delete(@PathVariable long questionId, @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
        service.deleteQuestion(questionId, principal.memberId());
    }
}
