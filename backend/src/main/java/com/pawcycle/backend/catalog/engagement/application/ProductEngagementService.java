package com.pawcycle.backend.catalog.engagement.application;

import com.pawcycle.backend.catalog.engagement.api.AdminQuestionListResponse;
import com.pawcycle.backend.catalog.engagement.api.AdminQuestionResponse;
import com.pawcycle.backend.catalog.engagement.api.AdminReviewListResponse;
import com.pawcycle.backend.catalog.engagement.api.AdminReviewResponse;
import com.pawcycle.backend.catalog.engagement.api.QuestionListResponse;
import com.pawcycle.backend.catalog.engagement.api.QuestionResponse;
import com.pawcycle.backend.catalog.engagement.api.ReviewListResponse;
import com.pawcycle.backend.catalog.engagement.api.ReviewResponse;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.engagement.persistence.ProductEngagementPersistence;
import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.commerce.NotificationService;
import com.pawcycle.backend.foundation.persistence.PersistenceExceptionClassifier;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductEngagementService {
  private final ProductEngagementPersistence persistence;
  private final ProductRepository products;
  private final NotificationService notifications;
  private final AdminAuditService audits;
  private final Clock clock;

  public ProductEngagementService(
      ProductEngagementPersistence persistence,
      ProductRepository products,
      NotificationService notifications,
      AdminAuditService audits,
      Clock clock) {
    this.persistence = persistence;
    this.products = products;
    this.notifications = notifications;
    this.audits = audits;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ReviewListResponse reviews(long productId, int page, int size) {
    requirePublicProduct(productId);
    PageInput input = page(page, size);
    long total = persistence.countVisibleReviews(productId);
    List<ReviewResponse> items = persistence.findVisibleReviews(productId, input.size(), input.offset());
    return new ReviewListResponse(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional(readOnly = true)
  public ReviewResponse myReview(long productId, long memberId) {
    requirePublicProduct(productId);
    return persistence
        .findMemberReview(productId, memberId)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "작성한 리뷰를 확인할 수 없습니다."));
  }

  @Transactional
  public ReviewResponse createReview(
      long productId, long memberId, ReviewCreateCommand request) {
    requirePublicProduct(productId);
    if (!persistence.hasDeliveredPurchase(memberId, productId))
      throw error(403, "REVIEW_PURCHASE_REQUIRED", "배송 완료 상품만 리뷰를 작성할 수 있습니다.");
    Timestamp now = Timestamp.from(Instant.now(clock));
    try {
      long id = persistence.insertReview(productId, memberId, request.rating(), request.content(), now);
      return review(id, productId, memberId);
    } catch (RuntimeException failure) {
      if (PersistenceExceptionClassifier.isDuplicateKey(failure)) {
        throw error(409, "REVIEW_ALREADY_EXISTS", "상품당 리뷰는 하나만 작성할 수 있습니다.");
      }
      throw failure;
    }
  }

  @Transactional
  public ReviewResponse updateReview(long reviewId, long memberId, ReviewPatchCommand request) {
    if (!request.ratingPresent() && !request.contentPresent())
      throw error(400, "VALIDATION_FAILED", "수정할 필드를 하나 이상 입력해 주세요.");
    ReviewMutationState current = lockReview(reviewId);
    if (current.memberId() != memberId)
      throw error(403, "REVIEW_OWNER_REQUIRED", "본인의 리뷰만 수정할 수 있습니다.");
    int rating = request.ratingPresent() ? validRating(request.rating()) : current.rating();
    String content = request.contentPresent() ? validContent(request.content()) : current.content();
    persistence.updateReview(reviewId, rating, content, Timestamp.from(Instant.now(clock)));
    return review(reviewId, current.productId(), memberId);
  }

  @Transactional
  public void deleteReview(long reviewId, long memberId) {
    ReviewMutationState current = lockReview(reviewId);
    if (current.memberId() != memberId)
      throw error(403, "REVIEW_OWNER_REQUIRED", "본인의 리뷰만 삭제할 수 있습니다.");
    persistence.deleteReview(reviewId);
  }

  @Transactional(readOnly = true)
  public AdminReviewListResponse adminReviews(Long productId, int page, int size) {
    PageInput input = page(page, size);
    long total = persistence.countReviews(productId);
    List<AdminReviewResponse> items =
        persistence.findAdminReviews(productId, input.size(), input.offset());
    return new AdminReviewListResponse(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional
  public void setReviewVisibility(long reviewId, boolean visible, long adminId) {
    if (persistence.updateReviewVisibility(reviewId, visible, Timestamp.from(Instant.now(clock)))
        != 1) {
      throw error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다.");
    }
    audits.append(adminId, "PRODUCT_REVIEW_VISIBILITY_UPDATE", "REVIEW", reviewId);
  }

  @Transactional(readOnly = true)
  public QuestionListResponse questions(long productId, int page, int size) {
    requirePublicProduct(productId);
    PageInput input = page(page, size);
    long total = persistence.countVisibleQuestions(productId);
    List<QuestionResponse> items =
        persistence.findVisibleQuestions(productId, input.size(), input.offset());
    return new QuestionListResponse(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional
  public QuestionResponse createQuestion(
      long productId, long memberId, QuestionCreateCommand request) {
    requirePublicProduct(productId);
    Timestamp now = Timestamp.from(Instant.now(clock));
    return question(persistence.insertQuestion(productId, memberId, request.content(), now), productId);
  }

  @Transactional
  public QuestionResponse updateQuestion(
      long questionId, long memberId, QuestionPatchCommand request) {
    if (!request.contentPresent()) throw error(400, "VALIDATION_FAILED", "수정할 필드를 하나 이상 입력해 주세요.");
    QuestionMutationState current = lockQuestion(questionId);
    if (current.memberId() != memberId)
      throw error(403, "PRODUCT_QUESTION_OWNER_REQUIRED", "본인의 문의만 수정할 수 있습니다.");
    if (current.answered())
      throw error(409, "PRODUCT_QUESTION_LOCKED", "답변이 등록된 문의는 수정할 수 없습니다.");
    persistence.updateQuestion(
        questionId, validContent(request.content()), Timestamp.from(Instant.now(clock)));
    return question(questionId, current.productId());
  }

  @Transactional
  public void deleteQuestion(long questionId, long memberId) {
    QuestionMutationState current = lockQuestion(questionId);
    if (current.memberId() != memberId)
      throw error(403, "PRODUCT_QUESTION_OWNER_REQUIRED", "본인의 문의만 삭제할 수 있습니다.");
    if (current.answered())
      throw error(409, "PRODUCT_QUESTION_LOCKED", "답변이 등록된 문의는 삭제할 수 없습니다.");
    persistence.deleteQuestion(questionId);
  }

  @Transactional(readOnly = true)
  public AdminQuestionListResponse adminQuestions(Long productId, int page, int size) {
    PageInput input = page(page, size);
    long total = persistence.countQuestions(productId);
    List<AdminQuestionResponse> items =
        persistence.findAdminQuestions(productId, input.size(), input.offset());
    return new AdminQuestionListResponse(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional
  public AdminQuestionResponse answerQuestion(long questionId, String answer, long adminId) {
    QuestionMutationState current = lockQuestion(questionId);
    Timestamp now = Timestamp.from(Instant.now(clock));
    persistence.answerQuestion(questionId, answer, now);
    if (!current.answered())
      notifications.create(
          current.memberId(), "PRODUCT_QUESTION_ANSWERED", "PRODUCT_QUESTION", questionId);
    audits.append(adminId, "PRODUCT_QUESTION_ANSWER_UPDATE", "PRODUCT_QUESTION", questionId);
    return adminQuestion(questionId);
  }

  @Transactional
  public void setQuestionVisibility(long questionId, boolean visible, long adminId) {
    if (persistence.updateQuestionVisibility(
            questionId, visible, Timestamp.from(Instant.now(clock)))
        != 1) {
      throw error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다.");
    }
    audits.append(adminId, "PRODUCT_QUESTION_VISIBILITY_UPDATE", "PRODUCT_QUESTION", questionId);
  }

  private ReviewMutationState lockReview(long reviewId) {
    ProductEngagementPersistence.ReviewMutationState state = persistence.lockReview(reviewId);
    if (state == null) throw error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다.");
    return new ReviewMutationState(state.memberId(), state.productId(), state.rating(), state.content());
  }

  private QuestionMutationState lockQuestion(long questionId) {
    ProductEngagementPersistence.QuestionMutationState state = persistence.lockQuestion(questionId);
    if (state == null) throw error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다.");
    return new QuestionMutationState(state.memberId(), state.productId(), state.answered());
  }

  private ReviewResponse review(long id, long productId, long memberId) {
    return java.util.Optional.ofNullable(persistence.findReview(id, productId, memberId))
        .orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다."));
  }

  private QuestionResponse question(long id, long productId) {
    return java.util.Optional.ofNullable(persistence.findQuestion(id, productId))
        .orElseThrow(() -> error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다."));
  }

  private AdminQuestionResponse adminQuestion(long id) {
    return java.util.Optional.ofNullable(persistence.findAdminQuestion(id))
        .orElseThrow(() -> error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다."));
  }

  private void requirePublicProduct(long productId) {
    if (products.findPublicById(productId).isEmpty())
      throw error(404, "PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다.");
  }

  private int validRating(Integer rating) {
    if (rating == null || rating < 1 || rating > 5)
      throw error(400, "VALIDATION_FAILED", "rating은 1~5여야 합니다.");
    return rating;
  }

  private String validContent(String content) {
    if (content == null || content.isBlank() || content.length() > 10000)
      throw error(400, "VALIDATION_FAILED", "content는 필수이며 10000자 이하여야 합니다.");
    return content;
  }

  private PageInput page(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw error(400, "VALIDATION_FAILED", "page는 0 이상, size는 1~100이어야 합니다.");
    try {
      return new PageInput(page, size, Math.multiplyExact(page, size));
    } catch (ArithmeticException exception) {
      throw error(400, "VALIDATION_FAILED", "page가 너무 큽니다.");
    }
  }

  private int totalPages(long total, int size) {
    return (int) Math.ceil((double) total / size);
  }

  private ProductEngagementException error(int status, String code, String message) {
    return new ProductEngagementException(status, code, message);
  }

  private record PageInput(int page, int size, int offset) {}

  private record ReviewMutationState(long memberId, long productId, int rating, String content) {}

  private record QuestionMutationState(long memberId, long productId, boolean answered) {}
}
