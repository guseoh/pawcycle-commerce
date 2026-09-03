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
import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.commerce.NotificationService;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import com.pawcycle.backend.foundation.persistence.PersistenceExceptionClassifier;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductEngagementService {
  private final NativeQueryExecutor jdbc;
  private final ProductRepository products;
  private final NotificationService notifications;
  private final AdminAuditService audits;
  private final Clock clock;

  public ProductEngagementService(
      NativeQueryExecutor jdbc,
      ProductRepository products,
      NotificationService notifications,
      AdminAuditService audits,
      Clock clock) {
    this.jdbc = jdbc;
    this.products = products;
    this.notifications = notifications;
    this.audits = audits;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ReviewListResponse reviews(long productId, int page, int size) {
    requirePublicProduct(productId);
    PageInput input = page(page, size);
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reviews WHERE product_id=? AND visible=true",
            Long.class,
            productId);
    List<ReviewResponse> items =
        jdbc.query(
            """
            SELECT id,rating,content,created_at,updated_at FROM reviews
            WHERE product_id=? AND visible=true ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
            """,
            (rs, rowNum) ->
                new ReviewResponse(
                    rs.getLong("id"),
                    rs.getInt("rating"),
                    rs.getString("content"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            productId,
            input.size(),
            input.offset());
    return new ReviewListResponse(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional(readOnly = true)
  public ReviewResponse myReview(long productId, long memberId) {
    requirePublicProduct(productId);
    return jdbc
        .query(
            "SELECT id,rating,content,created_at,updated_at FROM reviews WHERE product_id=? AND"
                + " member_id=?",
            (rs, rowNum) ->
                new ReviewResponse(
                    rs.getLong("id"),
                    rs.getInt("rating"),
                    rs.getString("content"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            productId,
            memberId)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "작성한 리뷰를 확인할 수 없습니다."));
  }

  @Transactional
  public ReviewResponse createReview(
      long productId, long memberId, ReviewCreateCommand request) {
    requirePublicProduct(productId);
    Integer purchased =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM orders o
            JOIN order_items oi ON oi.order_id=o.id
            JOIN skus s ON s.id=oi.sku_id
            JOIN deliveries d ON d.order_id=o.id AND d.status='DELIVERED'
            WHERE o.member_id=? AND s.product_id=?
            """,
            Integer.class,
            memberId,
            productId);
    if (purchased == null || purchased == 0)
      throw error(403, "REVIEW_PURCHASE_REQUIRED", "배송 완료 상품만 리뷰를 작성할 수 있습니다.");
    Timestamp now = Timestamp.from(Instant.now(clock));
    try {
      jdbc.update(
          "INSERT INTO reviews(product_id,member_id,rating,content,visible,created_at,updated_at)"
              + " VALUES (?,?,?,?,true,?,?)",
          productId,
          memberId,
          request.rating(),
          request.content(),
          now,
          now);
    } catch (RuntimeException failure) {
      if (PersistenceExceptionClassifier.isDuplicateKey(failure)) {
        throw error(409, "REVIEW_ALREADY_EXISTS", "상품당 리뷰는 하나만 작성할 수 있습니다.");
      }
      throw failure;
    }
    long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return review(id, productId, memberId);
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
    jdbc.update(
        "UPDATE reviews SET rating=?,content=?,updated_at=? WHERE id=?",
        rating,
        content,
        Timestamp.from(Instant.now(clock)),
        reviewId);
    return review(reviewId, current.productId(), memberId);
  }

  @Transactional
  public void deleteReview(long reviewId, long memberId) {
    ReviewMutationState current = lockReview(reviewId);
    if (current.memberId() != memberId)
      throw error(403, "REVIEW_OWNER_REQUIRED", "본인의 리뷰만 삭제할 수 있습니다.");
    jdbc.update("DELETE FROM reviews WHERE id=?", reviewId);
  }

  @Transactional(readOnly = true)
  public AdminReviewListResponse adminReviews(Long productId, int page, int size) {
    PageInput input = page(page, size);
    String filter = productId == null ? "" : " WHERE product_id=?";
    long total =
        productId == null
            ? jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Long.class)
            : jdbc.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE product_id=?", Long.class, productId);
    List<AdminReviewResponse> items =
        jdbc.query(
            "SELECT id,product_id,member_id,rating,content,visible,created_at,updated_at FROM"
                + " reviews"
                + filter
                + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            (rs, rowNum) ->
                new AdminReviewResponse(
                    rs.getLong("id"),
                    rs.getLong("product_id"),
                    rs.getLong("member_id"),
                    rs.getInt("rating"),
                    rs.getString("content"),
                    rs.getBoolean("visible"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            productId == null
                ? new Object[] {input.size(), input.offset()}
                : new Object[] {productId, input.size(), input.offset()});
    return new AdminReviewListResponse(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional
  public void setReviewVisibility(long reviewId, boolean visible, long adminId) {
    if (jdbc.update(
            "UPDATE reviews SET visible=?,updated_at=? WHERE id=?",
            visible,
            Timestamp.from(Instant.now(clock)),
            reviewId)
        != 1) {
      throw error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다.");
    }
    audits.append(adminId, "PRODUCT_REVIEW_VISIBILITY_UPDATE", "REVIEW", reviewId);
  }

  @Transactional(readOnly = true)
  public QuestionListResponse questions(long productId, int page, int size) {
    requirePublicProduct(productId);
    PageInput input = page(page, size);
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM product_questions WHERE product_id=? AND visible=true",
            Long.class,
            productId);
    List<QuestionResponse> items =
        jdbc.query(
            "SELECT id,content,answer,answered_at,created_at,updated_at FROM product_questions"
                + " WHERE product_id=? AND visible=true ORDER BY created_at DESC,id DESC LIMIT ?"
                + " OFFSET ?",
            (rs, rowNum) ->
                new QuestionResponse(
                    rs.getLong("id"),
                    rs.getString("content"),
                    rs.getString("answer"),
                    rs.getTimestamp("answered_at") != null,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            productId,
            input.size(),
            input.offset());
    return new QuestionListResponse(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional
  public QuestionResponse createQuestion(
      long productId, long memberId, QuestionCreateCommand request) {
    requirePublicProduct(productId);
    Timestamp now = Timestamp.from(Instant.now(clock));
    jdbc.update(
        "INSERT INTO"
            + " product_questions(product_id,member_id,content,answer,answered_at,visible,created_at,updated_at)"
            + " VALUES (?,?,?,NULL,NULL,true,?,?)",
        productId,
        memberId,
        request.content(),
        now,
        now);
    return question(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class), productId);
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
    jdbc.update(
        "UPDATE product_questions SET content=?,updated_at=? WHERE id=?",
        validContent(request.content()),
        Timestamp.from(Instant.now(clock)),
        questionId);
    return question(questionId, current.productId());
  }

  @Transactional
  public void deleteQuestion(long questionId, long memberId) {
    QuestionMutationState current = lockQuestion(questionId);
    if (current.memberId() != memberId)
      throw error(403, "PRODUCT_QUESTION_OWNER_REQUIRED", "본인의 문의만 삭제할 수 있습니다.");
    if (current.answered())
      throw error(409, "PRODUCT_QUESTION_LOCKED", "답변이 등록된 문의는 삭제할 수 없습니다.");
    jdbc.update("DELETE FROM product_questions WHERE id=?", questionId);
  }

  @Transactional(readOnly = true)
  public AdminQuestionListResponse adminQuestions(Long productId, int page, int size) {
    PageInput input = page(page, size);
    String filter = productId == null ? "" : " WHERE product_id=?";
    long total =
        productId == null
            ? jdbc.queryForObject("SELECT COUNT(*) FROM product_questions", Long.class)
            : jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_questions WHERE product_id=?", Long.class, productId);
    List<AdminQuestionResponse> items =
        jdbc.query(
            "SELECT"
                + " id,product_id,member_id,content,answer,answered_at,visible,created_at,updated_at"
                + " FROM product_questions"
                + filter
                + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            (rs, rowNum) ->
                new AdminQuestionResponse(
                    rs.getLong("id"),
                    rs.getLong("product_id"),
                    rs.getLong("member_id"),
                    rs.getString("content"),
                    rs.getString("answer"),
                    rs.getTimestamp("answered_at") != null,
                    rs.getBoolean("visible"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            productId == null
                ? new Object[] {input.size(), input.offset()}
                : new Object[] {productId, input.size(), input.offset()});
    return new AdminQuestionListResponse(
        items, input.page(), input.size(), total, totalPages(total, input.size()));
  }

  @Transactional
  public AdminQuestionResponse answerQuestion(long questionId, String answer, long adminId) {
    QuestionMutationState current = lockQuestion(questionId);
    Timestamp now = Timestamp.from(Instant.now(clock));
    jdbc.update(
        "UPDATE product_questions SET answer=?,answered_at=COALESCE(answered_at,?),updated_at=?"
            + " WHERE id=?",
        answer,
        now,
        now,
        questionId);
    if (!current.answered())
      notifications.create(
          current.memberId(), "PRODUCT_QUESTION_ANSWERED", "PRODUCT_QUESTION", questionId);
    audits.append(adminId, "PRODUCT_QUESTION_ANSWER_UPDATE", "PRODUCT_QUESTION", questionId);
    return adminQuestion(questionId);
  }

  @Transactional
  public void setQuestionVisibility(long questionId, boolean visible, long adminId) {
    if (jdbc.update(
            "UPDATE product_questions SET visible=?,updated_at=? WHERE id=?",
            visible,
            Timestamp.from(Instant.now(clock)),
            questionId)
        != 1) {
      throw error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다.");
    }
    audits.append(adminId, "PRODUCT_QUESTION_VISIBILITY_UPDATE", "PRODUCT_QUESTION", questionId);
  }

  private ReviewMutationState lockReview(long reviewId) {
    return jdbc
        .query(
            "SELECT member_id,product_id,rating,content FROM reviews WHERE id=? FOR UPDATE",
            (rs, rowNum) ->
                new ReviewMutationState(
                    rs.getLong("member_id"),
                    rs.getLong("product_id"),
                    rs.getInt("rating"),
                    rs.getString("content")),
            reviewId)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다."));
  }

  private QuestionMutationState lockQuestion(long questionId) {
    return jdbc
        .query(
            "SELECT member_id,product_id,answered_at FROM product_questions WHERE id=? FOR UPDATE",
            (rs, rowNum) ->
                new QuestionMutationState(
                    rs.getLong("member_id"),
                    rs.getLong("product_id"),
                    rs.getTimestamp("answered_at") != null),
            questionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다."));
  }

  private ReviewResponse review(long id, long productId, long memberId) {
    return jdbc
        .query(
            "SELECT id,rating,content,created_at,updated_at FROM reviews WHERE id=? AND"
                + " product_id=? AND member_id=?",
            (rs, rowNum) ->
                new ReviewResponse(
                    rs.getLong("id"),
                    rs.getInt("rating"),
                    rs.getString("content"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            id,
            productId,
            memberId)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다."));
  }

  private QuestionResponse question(long id, long productId) {
    return jdbc
        .query(
            "SELECT id,content,answer,answered_at,created_at,updated_at FROM product_questions"
                + " WHERE id=? AND product_id=?",
            (rs, rowNum) ->
                new QuestionResponse(
                    rs.getLong("id"),
                    rs.getString("content"),
                    rs.getString("answer"),
                    rs.getTimestamp("answered_at") != null,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            id,
            productId)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다."));
  }

  private AdminQuestionResponse adminQuestion(long id) {
    return jdbc
        .query(
            "SELECT"
                + " id,product_id,member_id,content,answer,answered_at,visible,created_at,updated_at"
                + " FROM product_questions WHERE id=?",
            (rs, rowNum) ->
                new AdminQuestionResponse(
                    rs.getLong("id"),
                    rs.getLong("product_id"),
                    rs.getLong("member_id"),
                    rs.getString("content"),
                    rs.getString("answer"),
                    rs.getTimestamp("answered_at") != null,
                    rs.getBoolean("visible"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            id)
        .stream()
        .findFirst()
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
