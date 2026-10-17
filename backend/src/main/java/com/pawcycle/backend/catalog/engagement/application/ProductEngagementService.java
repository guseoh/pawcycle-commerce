package com.pawcycle.backend.catalog.engagement.application;

import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.ReviewView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.AdminReviewView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.QuestionView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.AdminQuestionView;
import com.pawcycle.backend.catalog.engagement.model.PageView;
import com.pawcycle.backend.catalog.engagement.persistence.ProductEngagementPersistence;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.notification.application.NotificationService;
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
  public PageView<ReviewView> reviews(long productId, int page, int size) {
    requirePublicProduct(productId);
    PageInput input = page(page, size);
    long total = persistence.countVisibleReviews(productId);
    List<ReviewView> items =
        persistence.findVisibleReviews(productId, input.size(), input.offset()).stream()

            .toList();
    return new PageView<>(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional(readOnly = true)
  public ReviewView myReview(long productId, long memberId) {
    requirePublicProduct(productId);
    return persistence.findMemberReview(productId, memberId).stream()

        .findFirst()
        .orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "작성한 리뷰를 확인할 수 없습니다."));
  }

  @Transactional
  public ReviewView createReview(long productId, long memberId, ReviewCreateCommand request) {
    requirePublicProduct(productId);
    if (!persistence.hasDeliveredPurchase(memberId, productId))
      throw error(403, "REVIEW_PURCHASE_REQUIRED", "배송 완료 상품만 리뷰를 작성할 수 있습니다.");
    Timestamp now = Timestamp.from(Instant.now(clock));
    try {
      long id =
          persistence.insertReview(productId, memberId, request.rating(), request.content(), now);
      return review(id, productId, memberId);
    } catch (RuntimeException failure) {
      if (PersistenceExceptionClassifier.isDuplicateKey(failure)) {
        throw error(409, "REVIEW_ALREADY_EXISTS", "상품당 리뷰는 하나만 작성할 수 있습니다.");
      }
      throw failure;
    }
  }

  @Transactional
  public ReviewView updateReview(long reviewId, long memberId, ReviewPatchCommand request) {
    if (!request.ratingPresent() && !request.contentPresent())
      throw error(400, "VALIDATION_FAILED", "수정할 필드를 하나 이상 입력해 주세요.");
    ReviewMutationState current = lockOwnedReview(reviewId, memberId);
    int rating = request.ratingPresent() ? validRating(request.rating()) : current.rating();
    String content = request.contentPresent() ? validContent(request.content()) : current.content();
    persistence.updateReview(reviewId, rating, content, Timestamp.from(Instant.now(clock)));
    return review(reviewId, current.productId(), memberId);
  }

  @Transactional
  public void deleteReview(long reviewId, long memberId) {
    lockOwnedReview(reviewId, memberId);
    persistence.deleteReview(reviewId);
  }

  @Transactional(readOnly = true)
  public PageView<AdminReviewView> adminReviews(Long productId, int page, int size) {
    PageInput input = page(page, size);
    long total = persistence.countReviews(productId);
    List<AdminReviewView> items =
        persistence.findAdminReviews(productId, input.size(), input.offset()).stream()

            .toList();
    return new PageView<>(
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
  public PageView<QuestionView> questions(long productId, int page, int size) {
    requirePublicProduct(productId);
    PageInput input = page(page, size);
    long total = persistence.countVisibleQuestions(productId);
    List<QuestionView> items =
        persistence.findVisibleQuestions(productId, input.size(), input.offset()).stream()

            .toList();
    return new PageView<>(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional
  public QuestionView createQuestion(
      long productId, long memberId, QuestionCreateCommand request) {
    requirePublicProduct(productId);
    Timestamp now = Timestamp.from(Instant.now(clock));
    return question(
        persistence.insertQuestion(productId, memberId, request.content(), now), productId);
  }

  @Transactional
  public QuestionView updateQuestion(
      long questionId, long memberId, QuestionPatchCommand request) {
    if (!request.contentPresent()) throw error(400, "VALIDATION_FAILED", "수정할 필드를 하나 이상 입력해 주세요.");
    QuestionMutationState current = lockOwnedQuestion(questionId, memberId);
    if (current.answered()) throw error(409, "PRODUCT_QUESTION_LOCKED", "답변이 등록된 문의는 수정할 수 없습니다.");
    persistence.updateQuestion(
        questionId, validContent(request.content()), Timestamp.from(Instant.now(clock)));
    return question(questionId, current.productId());
  }

  @Transactional
  public void deleteQuestion(long questionId, long memberId) {
    QuestionMutationState current = lockOwnedQuestion(questionId, memberId);
    if (current.answered()) throw error(409, "PRODUCT_QUESTION_LOCKED", "답변이 등록된 문의는 삭제할 수 없습니다.");
    persistence.deleteQuestion(questionId);
  }

  @Transactional(readOnly = true)
  public PageView<AdminQuestionView> adminQuestions(Long productId, int page, int size) {
    PageInput input = page(page, size);
    long total = persistence.countQuestions(productId);
    List<AdminQuestionView> items =
        persistence.findAdminQuestions(productId, input.size(), input.offset()).stream()

            .toList();
    return new PageView<>(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional
  public AdminQuestionView answerQuestion(long questionId, String answer, long adminId) {
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
    return new ReviewMutationState(
        state.memberId(), state.productId(), state.rating(), state.content());
  }

  private ReviewMutationState lockOwnedReview(long reviewId, long memberId) {
    ProductEngagementPersistence.ReviewMutationState state =
        persistence.lockOwnedReview(reviewId, memberId);
    if (state == null) throw error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다.");
    return new ReviewMutationState(
        state.memberId(), state.productId(), state.rating(), state.content());
  }

  private QuestionMutationState lockQuestion(long questionId) {
    ProductEngagementPersistence.QuestionMutationState state = persistence.lockQuestion(questionId);
    if (state == null) throw error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다.");
    return new QuestionMutationState(state.memberId(), state.productId(), state.answered());
  }

  private QuestionMutationState lockOwnedQuestion(long questionId, long memberId) {
    ProductEngagementPersistence.QuestionMutationState state =
        persistence.lockOwnedQuestion(questionId, memberId);
    if (state == null)
      throw error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다.");
    return new QuestionMutationState(state.memberId(), state.productId(), state.answered());
  }

  private ReviewView review(long id, long productId, long memberId) {
    return java.util.Optional.ofNullable(persistence.findReview(id, productId, memberId))

        .orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다."));
  }

  private QuestionView question(long id, long productId) {
    return java.util.Optional.ofNullable(persistence.findQuestion(id, productId))

        .orElseThrow(() -> error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다."));
  }

  private AdminQuestionView adminQuestion(long id) {
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
