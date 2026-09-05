package com.pawcycle.backend.catalog.engagement.persistence;

import com.pawcycle.backend.catalog.engagement.domain.ProductQuestionEntity;
import com.pawcycle.backend.catalog.engagement.domain.ReviewEntity;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductEngagementPersistence {
  private final EntityManager entityManager;
  private final ReviewRepository reviews;
  private final ProductQuestionRepository questions;

  public ProductEngagementPersistence(
      EntityManager entityManager, ReviewRepository reviews, ProductQuestionRepository questions) {
    this.entityManager = entityManager;
    this.reviews = reviews;
    this.questions = questions;
  }

  @Transactional(readOnly = true)
  public long countVisibleReviews(long productId) {
    return reviews.countByProductIdAndVisibleTrue(productId);
  }

  @Transactional(readOnly = true)
  public List<ReviewView> findVisibleReviews(long productId, int size, int offset) {
    return entityManager
        .createQuery(
            "select r from ReviewEntity r where r.productId = :productId and r.visible = true "
                + "order by r.createdAt desc, r.id desc",
            ReviewEntity.class)
        .setParameter("productId", productId)
        .setFirstResult(offset)
        .setMaxResults(size)
        .getResultList()
        .stream()
        .map(this::review)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ReviewView> findMemberReview(long productId, long memberId) {
    return reviews.findByProductIdAndMemberId(productId, memberId).stream()
        .map(this::review)
        .toList();
  }

  @Transactional(readOnly = true)
  public boolean hasDeliveredPurchase(long memberId, long productId) {
    Number count =
        (Number)
            entityManager
                .createNativeQuery(
                    "SELECT COUNT(*) FROM orders o JOIN order_items oi ON oi.order_id=o.id "
                        + "JOIN skus s ON s.id=oi.sku_id JOIN deliveries d ON d.order_id=o.id "
                        + "AND d.status='DELIVERED' WHERE o.member_id=:memberId AND s.product_id=:productId")
                .setParameter("memberId", memberId)
                .setParameter("productId", productId)
                .getSingleResult();
    return count.longValue() > 0;
  }

  @Transactional
  public long insertReview(
      long productId, long memberId, int rating, String content, Timestamp now) {
    ReviewEntity review =
        reviews.saveAndFlush(
            new ReviewEntity(
                productId,
                memberId,
                rating,
                content,
                true,
                localDateTime(now),
                localDateTime(now)));
    return review.getId();
  }

  @Transactional
  public int updateReview(long reviewId, int rating, String content, Timestamp now) {
    return reviews
        .findById(reviewId)
        .map(
            review -> {
              review.update(rating, content, localDateTime(now));
              reviews.flush();
              return 1;
            })
        .orElse(0);
  }

  @Transactional
  public int deleteReview(long reviewId) {
    return reviews
        .findById(reviewId)
        .map(
            review -> {
              reviews.delete(review);
              reviews.flush();
              return 1;
            })
        .orElse(0);
  }

  @Transactional(readOnly = true)
  public long countReviews(Long productId) {
    return productId == null ? reviews.count() : reviews.countByProductId(productId);
  }

  @Transactional(readOnly = true)
  public List<AdminReviewView> findAdminReviews(Long productId, int size, int offset) {
    String jpql =
        productId == null
            ? "select r from ReviewEntity r order by r.createdAt desc, r.id desc"
            : "select r from ReviewEntity r where r.productId = :productId order by r.createdAt desc, r.id desc";
    var query = entityManager.createQuery(jpql, ReviewEntity.class);
    if (productId != null) query.setParameter("productId", productId);
    return query.setFirstResult(offset).setMaxResults(size).getResultList().stream()
        .map(this::adminReview)
        .toList();
  }

  @Transactional
  public int updateReviewVisibility(long reviewId, boolean visible, Timestamp now) {
    return reviews
        .findById(reviewId)
        .map(
            review -> {
              review.updateVisibility(visible, localDateTime(now));
              reviews.flush();
              return 1;
            })
        .orElse(0);
  }

  @Transactional(readOnly = true)
  public long countVisibleQuestions(long productId) {
    return questions.countByProductIdAndVisibleTrue(productId);
  }

  @Transactional(readOnly = true)
  public List<QuestionView> findVisibleQuestions(long productId, int size, int offset) {
    return entityManager
        .createQuery(
            "select q from ProductQuestionEntity q where q.productId = :productId and q.visible = true "
                + "order by q.createdAt desc, q.id desc",
            ProductQuestionEntity.class)
        .setParameter("productId", productId)
        .setFirstResult(offset)
        .setMaxResults(size)
        .getResultList()
        .stream()
        .map(this::question)
        .toList();
  }

  @Transactional
  public long insertQuestion(long productId, long memberId, String content, Timestamp now) {
    ProductQuestionEntity question =
        questions.saveAndFlush(
            new ProductQuestionEntity(
                productId, memberId, content, true, localDateTime(now), localDateTime(now)));
    return question.getId();
  }

  @Transactional
  public int updateQuestion(long questionId, String content, Timestamp now) {
    return questions
        .findById(questionId)
        .map(
            question -> {
              question.update(content, localDateTime(now));
              questions.flush();
              return 1;
            })
        .orElse(0);
  }

  @Transactional
  public int deleteQuestion(long questionId) {
    return questions
        .findById(questionId)
        .map(
            question -> {
              questions.delete(question);
              questions.flush();
              return 1;
            })
        .orElse(0);
  }

  @Transactional(readOnly = true)
  public long countQuestions(Long productId) {
    return productId == null ? questions.count() : questions.countByProductId(productId);
  }

  @Transactional(readOnly = true)
  public List<AdminQuestionView> findAdminQuestions(Long productId, int size, int offset) {
    String jpql =
        productId == null
            ? "select q from ProductQuestionEntity q order by q.createdAt desc, q.id desc"
            : "select q from ProductQuestionEntity q where q.productId = :productId order by q.createdAt desc, q.id desc";
    var query = entityManager.createQuery(jpql, ProductQuestionEntity.class);
    if (productId != null) query.setParameter("productId", productId);
    return query.setFirstResult(offset).setMaxResults(size).getResultList().stream()
        .map(this::adminQuestion)
        .toList();
  }

  @Transactional
  public int answerQuestion(long questionId, String answer, Timestamp now) {
    return questions
        .findById(questionId)
        .map(
            question -> {
              question.answer(answer, localDateTime(now), localDateTime(now));
              questions.flush();
              return 1;
            })
        .orElse(0);
  }

  @Transactional
  public int updateQuestionVisibility(long questionId, boolean visible, Timestamp now) {
    return questions
        .findById(questionId)
        .map(
            question -> {
              question.updateVisibility(visible, localDateTime(now));
              questions.flush();
              return 1;
            })
        .orElse(0);
  }

  @Transactional(readOnly = true)
  public ReviewMutationState lockReview(long reviewId) {
    return reviews
        .findByIdForUpdate(reviewId)
        .map(review -> new ReviewMutationState(review.getMemberId(), review.getProductId(), review.getRating(), review.getContent()))
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public QuestionMutationState lockQuestion(long questionId) {
    return questions
        .findByIdForUpdate(questionId)
        .map(question -> new QuestionMutationState(question.getMemberId(), question.getProductId(), question.getAnsweredAt() != null))
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public ReviewView findReview(long id, long productId, long memberId) {
    return reviews.findByProductIdAndMemberId(productId, memberId).stream()
        .filter(review -> review.getId().equals(id))
        .findFirst()
        .map(this::review)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public QuestionView findQuestion(long id, long productId) {
    return questions.findByIdAndProductId(id, productId).map(this::question).orElse(null);
  }

  @Transactional(readOnly = true)
  public AdminQuestionView findAdminQuestion(long id) {
    return questions.findById(id).map(this::adminQuestion).orElse(null);
  }

  private ReviewView review(ReviewEntity review) {
    return new ReviewView(
        review.getId(), review.getRating(), review.getContent(), instant(review.getCreatedAt()), instant(review.getUpdatedAt()));
  }

  private AdminReviewView adminReview(ReviewEntity review) {
    return new AdminReviewView(
        review.getId(), review.getProductId(), review.getMemberId(), review.getRating(), review.getContent(), review.isVisible(), instant(review.getCreatedAt()), instant(review.getUpdatedAt()));
  }

  private QuestionView question(ProductQuestionEntity question) {
    return new QuestionView(
        question.getId(), question.getContent(), question.getAnswer(), question.getAnsweredAt() != null, instant(question.getCreatedAt()), instant(question.getUpdatedAt()));
  }

  private AdminQuestionView adminQuestion(ProductQuestionEntity question) {
    return new AdminQuestionView(
        question.getId(), question.getProductId(), question.getMemberId(), question.getContent(), question.getAnswer(), question.getAnsweredAt() != null, question.isVisible(), instant(question.getCreatedAt()), instant(question.getUpdatedAt()));
  }

  private static java.time.LocalDateTime localDateTime(Timestamp timestamp) {
    return timestamp.toLocalDateTime();
  }

  private static java.time.Instant instant(java.time.LocalDateTime value) {
    return value.atZone(ZoneId.systemDefault()).toInstant();
  }

  public record ReviewMutationState(long memberId, long productId, int rating, String content) {}

  public record QuestionMutationState(long memberId, long productId, boolean answered) {}

  public record ReviewView(
      Long reviewId, int rating, String content, java.time.Instant createdAt, java.time.Instant updatedAt) {}

  public record AdminReviewView(
      Long reviewId,
      Long productId,
      Long memberId,
      int rating,
      String content,
      boolean visible,
      java.time.Instant createdAt,
      java.time.Instant updatedAt) {}

  public record QuestionView(
      Long questionId,
      String content,
      String answer,
      boolean answered,
      java.time.Instant createdAt,
      java.time.Instant updatedAt) {}

  public record AdminQuestionView(
      Long questionId,
      Long productId,
      Long memberId,
      String content,
      String answer,
      boolean answered,
      boolean visible,
      java.time.Instant createdAt,
      java.time.Instant updatedAt) {}
}
