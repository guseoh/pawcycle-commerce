package com.pawcycle.backend.catalog.engagement.persistence;

import com.pawcycle.backend.catalog.engagement.api.AdminQuestionResponse;
import com.pawcycle.backend.catalog.engagement.api.AdminReviewResponse;
import com.pawcycle.backend.catalog.engagement.api.QuestionResponse;
import com.pawcycle.backend.catalog.engagement.api.ReviewResponse;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductEngagementPersistence {
  private final JdbcTemplate jdbc;

  public ProductEngagementPersistence(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long countVisibleReviews(long productId) {
    return count("SELECT COUNT(*) FROM reviews WHERE product_id=? AND visible=true", productId);
  }

  public List<ReviewResponse> findVisibleReviews(long productId, int size, int offset) {
    return jdbc.query(
        "SELECT id,rating,content,created_at,updated_at FROM reviews"
            + " WHERE product_id=? AND visible=true ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
        (rs, rowNum) ->
            new ReviewResponse(
                rs.getLong("id"),
                rs.getInt("rating"),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()),
        productId,
        size,
        offset);
  }

  public List<ReviewResponse> findMemberReview(long productId, long memberId) {
    return jdbc.query(
        "SELECT id,rating,content,created_at,updated_at FROM reviews WHERE product_id=? AND"
            + " member_id=?",
        (rs, rowNum) -> review(rs),
        productId,
        memberId);
  }

  public boolean hasDeliveredPurchase(long memberId, long productId) {
    return count(
            "SELECT COUNT(*) FROM orders o JOIN order_items oi ON oi.order_id=o.id"
                + " JOIN skus s ON s.id=oi.sku_id JOIN deliveries d ON d.order_id=o.id"
                + " AND d.status='DELIVERED' WHERE o.member_id=? AND s.product_id=?",
            memberId,
            productId)
        > 0;
  }

  public long insertReview(long productId, long memberId, int rating, String content, Timestamp now) {
    jdbc.update(
        "INSERT INTO reviews(product_id,member_id,rating,content,visible,created_at,updated_at)"
            + " VALUES (?,?,?,?,true,?,?)",
        productId,
        memberId,
        rating,
        content,
        now,
        now);
    return lastInsertId();
  }

  public int updateReview(long reviewId, int rating, String content, Timestamp now) {
    return jdbc.update(
        "UPDATE reviews SET rating=?,content=?,updated_at=? WHERE id=?",
        rating,
        content,
        now,
        reviewId);
  }

  public int deleteReview(long reviewId) {
    return jdbc.update("DELETE FROM reviews WHERE id=?", reviewId);
  }

  public long countReviews(Long productId) {
    return productId == null
        ? count("SELECT COUNT(*) FROM reviews")
        : count("SELECT COUNT(*) FROM reviews WHERE product_id=?", productId);
  }

  public List<AdminReviewResponse> findAdminReviews(Long productId, int size, int offset) {
    String filter = productId == null ? "" : " WHERE product_id=?";
    Object[] args =
        productId == null ? new Object[] {size, offset} : new Object[] {productId, size, offset};
    return jdbc.query(
        "SELECT id,product_id,member_id,rating,content,visible,created_at,updated_at FROM reviews"
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
        args);
  }

  public int updateReviewVisibility(long reviewId, boolean visible, Timestamp now) {
    return jdbc.update("UPDATE reviews SET visible=?,updated_at=? WHERE id=?", visible, now, reviewId);
  }

  public long countVisibleQuestions(long productId) {
    return count("SELECT COUNT(*) FROM product_questions WHERE product_id=? AND visible=true", productId);
  }

  public List<QuestionResponse> findVisibleQuestions(long productId, int size, int offset) {
    return jdbc.query(
        "SELECT id,content,answer,answered_at,created_at,updated_at FROM product_questions"
            + " WHERE product_id=? AND visible=true ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
        (rs, rowNum) -> question(rs),
        productId,
        size,
        offset);
  }

  public long insertQuestion(long productId, long memberId, String content, Timestamp now) {
    jdbc.update(
        "INSERT INTO product_questions(product_id,member_id,content,answer,answered_at,visible,created_at,updated_at)"
            + " VALUES (?,?,?,NULL,NULL,true,?,?)",
        productId,
        memberId,
        content,
        now,
        now);
    return lastInsertId();
  }

  public int updateQuestion(long questionId, String content, Timestamp now) {
    return jdbc.update(
        "UPDATE product_questions SET content=?,updated_at=? WHERE id=?", content, now, questionId);
  }

  public int deleteQuestion(long questionId) {
    return jdbc.update("DELETE FROM product_questions WHERE id=?", questionId);
  }

  public long countQuestions(Long productId) {
    return productId == null
        ? count("SELECT COUNT(*) FROM product_questions")
        : count("SELECT COUNT(*) FROM product_questions WHERE product_id=?", productId);
  }

  public List<AdminQuestionResponse> findAdminQuestions(Long productId, int size, int offset) {
    String filter = productId == null ? "" : " WHERE product_id=?";
    Object[] args =
        productId == null ? new Object[] {size, offset} : new Object[] {productId, size, offset};
    return jdbc.query(
        "SELECT id,product_id,member_id,content,answer,answered_at,visible,created_at,updated_at"
            + " FROM product_questions"
            + filter
            + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
        (rs, rowNum) -> adminQuestion(rs),
        args);
  }

  public int answerQuestion(long questionId, String answer, Timestamp now) {
    return jdbc.update(
        "UPDATE product_questions SET answer=?,answered_at=COALESCE(answered_at,?),updated_at=?"
            + " WHERE id=?",
        answer,
        now,
        now,
        questionId);
  }

  public int updateQuestionVisibility(long questionId, boolean visible, Timestamp now) {
    return jdbc.update(
        "UPDATE product_questions SET visible=?,updated_at=? WHERE id=?",
        visible,
        now,
        questionId);
  }

  public ReviewMutationState lockReview(long reviewId) {
    return jdbc.query(
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
        .orElse(null);
  }

  public QuestionMutationState lockQuestion(long questionId) {
    return jdbc.query(
            "SELECT member_id,product_id,answered_at FROM product_questions WHERE id=? FOR UPDATE",
            (rs, rowNum) ->
                new QuestionMutationState(
                    rs.getLong("member_id"),
                    rs.getLong("product_id"),
                    rs.getTimestamp("answered_at") != null),
            questionId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public ReviewResponse findReview(long id, long productId, long memberId) {
    return jdbc.query(
            "SELECT id,rating,content,created_at,updated_at FROM reviews WHERE id=? AND"
                + " product_id=? AND member_id=?",
            (rs, rowNum) -> review(rs),
            id,
            productId,
            memberId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public QuestionResponse findQuestion(long id, long productId) {
    return jdbc.query(
            "SELECT id,content,answer,answered_at,created_at,updated_at FROM product_questions"
                + " WHERE id=? AND product_id=?",
            (rs, rowNum) -> question(rs),
            id,
            productId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public AdminQuestionResponse findAdminQuestion(long id) {
    return jdbc.query(
            "SELECT id,product_id,member_id,content,answer,answered_at,visible,created_at,updated_at"
                + " FROM product_questions WHERE id=?",
            (rs, rowNum) -> adminQuestion(rs),
            id)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  private long lastInsertId() {
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private ReviewResponse review(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new ReviewResponse(
        rs.getLong("id"),
        rs.getInt("rating"),
        rs.getString("content"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private QuestionResponse question(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new QuestionResponse(
        rs.getLong("id"),
        rs.getString("content"),
        rs.getString("answer"),
        rs.getTimestamp("answered_at") != null,
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private AdminQuestionResponse adminQuestion(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new AdminQuestionResponse(
        rs.getLong("id"),
        rs.getLong("product_id"),
        rs.getLong("member_id"),
        rs.getString("content"),
        rs.getString("answer"),
        rs.getTimestamp("answered_at") != null,
        rs.getBoolean("visible"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  public record ReviewMutationState(long memberId, long productId, int rating, String content) {}

  public record QuestionMutationState(long memberId, long productId, boolean answered) {}
}
