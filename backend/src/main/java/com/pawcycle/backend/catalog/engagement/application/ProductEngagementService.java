package com.pawcycle.backend.catalog.engagement.application;

import com.pawcycle.backend.catalog.engagement.api.EngagementRequests;
import com.pawcycle.backend.catalog.engagement.api.QuestionViews;
import com.pawcycle.backend.catalog.engagement.api.ReviewViews;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.commerce.NotificationService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductEngagementService {
    private final JdbcTemplate jdbc;
    private final ProductRepository products;
    private final NotificationService notifications;
    private final AdminAuditService audits;

    public ProductEngagementService(JdbcTemplate jdbc, ProductRepository products,
            NotificationService notifications, AdminAuditService audits) {
        this.jdbc = jdbc;
        this.products = products;
        this.notifications = notifications;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public ReviewViews.Page reviews(long productId, int page, int size) {
        requirePublicProduct(productId);
        PageInput input = page(page, size);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE product_id=? AND visible=true", Long.class, productId);
        List<ReviewViews.Review> items = jdbc.query("""
                SELECT id,rating,content,created_at,updated_at FROM reviews
                WHERE product_id=? AND visible=true ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new ReviewViews.Review(rs.getLong("id"), rs.getInt("rating"), rs.getString("content"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), productId, input.size(), input.offset());
        return new ReviewViews.Page(items, input.page(), input.size(), total, totalPages(total, input.size()));
    }

    @Transactional(readOnly = true)
    public ReviewViews.Review myReview(long productId, long memberId) {
        requirePublicProduct(productId);
        return jdbc.query("SELECT id,rating,content,created_at,updated_at FROM reviews WHERE product_id=? AND member_id=?",
                (rs, rowNum) -> new ReviewViews.Review(rs.getLong("id"), rs.getInt("rating"), rs.getString("content"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), productId, memberId)
                .stream().findFirst().orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "작성한 리뷰를 확인할 수 없습니다."));
    }

    @Transactional
    public ReviewViews.Review createReview(long productId, long memberId, EngagementRequests.ReviewCreate request) {
        requirePublicProduct(productId);
        Integer purchased = jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders o
                JOIN order_items oi ON oi.order_id=o.id
                JOIN skus s ON s.id=oi.sku_id
                JOIN deliveries d ON d.order_id=o.id AND d.status='DELIVERED'
                WHERE o.member_id=? AND s.product_id=?
                """, Integer.class, memberId, productId);
        if (purchased == null || purchased == 0) throw error(403, "REVIEW_PURCHASE_REQUIRED", "배송 완료 상품만 리뷰를 작성할 수 있습니다.");
        Timestamp now = Timestamp.from(Instant.now());
        try {
            jdbc.update("INSERT INTO reviews(product_id,member_id,rating,content,visible,created_at,updated_at) VALUES (?,?,?,?,true,?,?)",
                    productId, memberId, request.rating(), request.content(), now, now);
        } catch (DataIntegrityViolationException exception) {
            throw error(409, "REVIEW_ALREADY_EXISTS", "상품당 리뷰는 하나만 작성할 수 있습니다.");
        }
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return review(id, productId, memberId);
    }

    @Transactional
    public ReviewViews.Review updateReview(long reviewId, long memberId, EngagementRequests.ReviewPatch request) {
        if (!request.isRatingPresent() && !request.isContentPresent()) throw error(400, "VALIDATION_FAILED", "수정할 필드를 하나 이상 입력해 주세요.");
        Map<String, Object> current = jdbc.queryForList("SELECT * FROM reviews WHERE id=? FOR UPDATE", reviewId)
                .stream().findFirst().orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다."));
        if (((Number) current.get("member_id")).longValue() != memberId) throw error(403, "REVIEW_OWNER_REQUIRED", "본인의 리뷰만 수정할 수 있습니다.");
        int rating = request.isRatingPresent() ? validRating(request.getRating()) : ((Number) current.get("rating")).intValue();
        String content = request.isContentPresent() ? validContent(request.getContent()) : (String) current.get("content");
        jdbc.update("UPDATE reviews SET rating=?,content=?,updated_at=? WHERE id=?", rating, content, Timestamp.from(Instant.now()), reviewId);
        return review(reviewId, ((Number) current.get("product_id")).longValue(), memberId);
    }

    @Transactional
    public void deleteReview(long reviewId, long memberId) {
        Map<String, Object> current = jdbc.queryForList("SELECT member_id FROM reviews WHERE id=? FOR UPDATE", reviewId)
                .stream().findFirst().orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다."));
        if (((Number) current.get("member_id")).longValue() != memberId) throw error(403, "REVIEW_OWNER_REQUIRED", "본인의 리뷰만 삭제할 수 있습니다.");
        jdbc.update("DELETE FROM reviews WHERE id=?", reviewId);
    }

    @Transactional(readOnly = true)
    public ReviewViews.AdminPage adminReviews(Long productId, int page, int size) {
        PageInput input = page(page, size);
        String filter = productId == null ? "" : " WHERE product_id=?";
        long total = productId == null ? jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Long.class)
                : jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE product_id=?", Long.class, productId);
        List<ReviewViews.AdminReview> items = jdbc.query("SELECT id,product_id,member_id,rating,content,visible,created_at,updated_at FROM reviews" + filter + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new ReviewViews.AdminReview(rs.getLong("id"), rs.getLong("product_id"), rs.getLong("member_id"), rs.getInt("rating"), rs.getString("content"), rs.getBoolean("visible"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                productId == null ? new Object[]{input.size(), input.offset()} : new Object[]{productId, input.size(), input.offset()});
        return new ReviewViews.AdminPage(items, input.page(), input.size(), total, totalPages(total, input.size()));
    }

    @Transactional
    public void setReviewVisibility(long reviewId, boolean visible, long adminId) {
        if (jdbc.update("UPDATE reviews SET visible=?,updated_at=? WHERE id=?", visible, Timestamp.from(Instant.now()), reviewId) != 1) {
            throw error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다.");
        }
        audits.append(adminId, "PRODUCT_REVIEW_VISIBILITY_UPDATE", "REVIEW", reviewId);
    }

    @Transactional(readOnly = true)
    public QuestionViews.Page questions(long productId, int page, int size) {
        requirePublicProduct(productId);
        PageInput input = page(page, size);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM product_questions WHERE product_id=? AND visible=true", Long.class, productId);
        List<QuestionViews.Question> items = jdbc.query("SELECT id,content,answer,answered_at,created_at,updated_at FROM product_questions WHERE product_id=? AND visible=true ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new QuestionViews.Question(rs.getLong("id"), rs.getString("content"), rs.getString("answer"), rs.getTimestamp("answered_at") != null, rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                productId, input.size(), input.offset());
        return new QuestionViews.Page(items, input.page(), input.size(), total, totalPages(total, input.size()));
    }

    @Transactional
    public QuestionViews.Question createQuestion(long productId, long memberId, EngagementRequests.QuestionCreate request) {
        requirePublicProduct(productId);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO product_questions(product_id,member_id,content,answer,answered_at,visible,created_at,updated_at) VALUES (?,?,?,NULL,NULL,true,?,?)",
                productId, memberId, request.content(), now, now);
        return question(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class), productId);
    }

    @Transactional
    public QuestionViews.Question updateQuestion(long questionId, long memberId, EngagementRequests.QuestionPatch request) {
        if (!request.isContentPresent()) throw error(400, "VALIDATION_FAILED", "수정할 필드를 하나 이상 입력해 주세요.");
        Map<String, Object> current = jdbc.queryForList("SELECT * FROM product_questions WHERE id=? FOR UPDATE", questionId)
                .stream().findFirst().orElseThrow(() -> error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다."));
        if (((Number) current.get("member_id")).longValue() != memberId) throw error(403, "PRODUCT_QUESTION_OWNER_REQUIRED", "본인의 문의만 수정할 수 있습니다.");
        if (current.get("answered_at") != null) throw error(409, "PRODUCT_QUESTION_LOCKED", "답변이 등록된 문의는 수정할 수 없습니다.");
        jdbc.update("UPDATE product_questions SET content=?,updated_at=? WHERE id=?", validContent(request.getContent()), Timestamp.from(Instant.now()), questionId);
        return question(questionId, ((Number) current.get("product_id")).longValue());
    }

    @Transactional
    public void deleteQuestion(long questionId, long memberId) {
        Map<String, Object> current = jdbc.queryForList("SELECT * FROM product_questions WHERE id=? FOR UPDATE", questionId)
                .stream().findFirst().orElseThrow(() -> error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다."));
        if (((Number) current.get("member_id")).longValue() != memberId) throw error(403, "PRODUCT_QUESTION_OWNER_REQUIRED", "본인의 문의만 삭제할 수 있습니다.");
        if (current.get("answered_at") != null) throw error(409, "PRODUCT_QUESTION_LOCKED", "답변이 등록된 문의는 삭제할 수 없습니다.");
        jdbc.update("DELETE FROM product_questions WHERE id=?", questionId);
    }

    @Transactional(readOnly = true)
    public QuestionViews.AdminPage adminQuestions(Long productId, int page, int size) {
        PageInput input = page(page, size);
        String filter = productId == null ? "" : " WHERE product_id=?";
        long total = productId == null ? jdbc.queryForObject("SELECT COUNT(*) FROM product_questions", Long.class)
                : jdbc.queryForObject("SELECT COUNT(*) FROM product_questions WHERE product_id=?", Long.class, productId);
        List<QuestionViews.AdminQuestion> items = jdbc.query("SELECT id,product_id,member_id,content,answer,answered_at,visible,created_at,updated_at FROM product_questions" + filter + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new QuestionViews.AdminQuestion(rs.getLong("id"), rs.getLong("product_id"), rs.getLong("member_id"), rs.getString("content"), rs.getString("answer"), rs.getTimestamp("answered_at") != null, rs.getBoolean("visible"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                productId == null ? new Object[]{input.size(), input.offset()} : new Object[]{productId, input.size(), input.offset()});
        return new QuestionViews.AdminPage(items, input.page(), input.size(), total, totalPages(total, input.size()));
    }

    @Transactional
    public QuestionViews.AdminQuestion answerQuestion(long questionId, String answer, long adminId) {
        Map<String, Object> current = jdbc.queryForList("SELECT * FROM product_questions WHERE id=? FOR UPDATE", questionId)
                .stream().findFirst().orElseThrow(() -> error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다."));
        boolean firstAnswer = current.get("answered_at") == null;
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("UPDATE product_questions SET answer=?,answered_at=COALESCE(answered_at,?),updated_at=? WHERE id=?", answer, now, now, questionId);
        if (firstAnswer) notifications.create(((Number) current.get("member_id")).longValue(), "PRODUCT_QUESTION_ANSWERED", "PRODUCT_QUESTION", questionId);
        audits.append(adminId, "PRODUCT_QUESTION_ANSWER_UPDATE", "PRODUCT_QUESTION", questionId);
        return adminQuestion(questionId);
    }

    @Transactional
    public void setQuestionVisibility(long questionId, boolean visible, long adminId) {
        if (jdbc.update("UPDATE product_questions SET visible=?,updated_at=? WHERE id=?", visible, Timestamp.from(Instant.now()), questionId) != 1) {
            throw error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다.");
        }
        audits.append(adminId, "PRODUCT_QUESTION_VISIBILITY_UPDATE", "PRODUCT_QUESTION", questionId);
    }

    private ReviewViews.Review review(long id, long productId, long memberId) {
        return jdbc.query("SELECT id,rating,content,created_at,updated_at FROM reviews WHERE id=? AND product_id=? AND member_id=?",
                (rs, rowNum) -> new ReviewViews.Review(rs.getLong("id"), rs.getInt("rating"), rs.getString("content"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id, productId, memberId)
                .stream().findFirst().orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "리뷰를 확인할 수 없습니다."));
    }

    private QuestionViews.Question question(long id, long productId) {
        return jdbc.query("SELECT id,content,answer,answered_at,created_at,updated_at FROM product_questions WHERE id=? AND product_id=?",
                (rs, rowNum) -> new QuestionViews.Question(rs.getLong("id"), rs.getString("content"), rs.getString("answer"), rs.getTimestamp("answered_at") != null, rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id, productId)
                .stream().findFirst().orElseThrow(() -> error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다."));
    }

    private QuestionViews.AdminQuestion adminQuestion(long id) {
        return jdbc.query("SELECT id,product_id,member_id,content,answer,answered_at,visible,created_at,updated_at FROM product_questions WHERE id=?",
                (rs, rowNum) -> new QuestionViews.AdminQuestion(rs.getLong("id"), rs.getLong("product_id"), rs.getLong("member_id"), rs.getString("content"), rs.getString("answer"), rs.getTimestamp("answered_at") != null, rs.getBoolean("visible"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id)
                .stream().findFirst().orElseThrow(() -> error(404, "PRODUCT_QUESTION_NOT_FOUND", "상품 문의를 확인할 수 없습니다."));
    }

    private void requirePublicProduct(long productId) {
        if (products.findPublicById(productId).isEmpty()) throw error(404, "PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다.");
    }

    private int validRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) throw error(400, "VALIDATION_FAILED", "rating은 1~5여야 합니다.");
        return rating;
    }

    private String validContent(String content) {
        if (content == null || content.isBlank() || content.length() > 10000) throw error(400, "VALIDATION_FAILED", "content는 필수이며 10000자 이하여야 합니다.");
        return content;
    }

    private PageInput page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw error(400, "VALIDATION_FAILED", "page는 0 이상, size는 1~100이어야 합니다.");
        try {
            return new PageInput(page, size, Math.multiplyExact(page, size));
        } catch (ArithmeticException exception) {
            throw error(400, "VALIDATION_FAILED", "page가 너무 큽니다.");
        }
    }

    private int totalPages(long total, int size) { return (int) Math.ceil((double) total / size); }
    private ProductEngagementException error(int status, String code, String message) { return new ProductEngagementException(status, code, message); }
    private record PageInput(int page, int size, int offset) {}
}
