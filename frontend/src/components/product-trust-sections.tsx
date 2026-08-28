"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError, productApi, type EngagementPage, type ProductQuestion, type ProductReview, type ProductTrust } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatDateTime } from "@/lib/frontend-utils";
import { finalProductApi, type ReviewSummary } from "@/lib/final-product-api";
import { isLatestRequest } from "@/lib/request-generation";

interface ProductTrustSectionsProps {
  productId: string;
  trust: ProductTrust;
  onTrustRefresh: () => Promise<void>;
}

type LoadStatus = "loading" | "ready" | "error";

function engagementError(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError)) return fallback;
  if (error.code === "AUTH_REQUIRED") return "로그인 후 이용할 수 있습니다.";
  if (error.code === "REVIEW_PURCHASE_REQUIRED") return "배송 완료된 구매 상품만 리뷰를 작성할 수 있습니다.";
  if (error.code === "REVIEW_ALREADY_EXISTS") return "이 상품에는 이미 리뷰를 작성했습니다.";
  if (error.code === "VALIDATION_FAILED") return error.fieldErrors[0]?.message ?? error.message;
  return error.message || fallback;
}

function PageControls({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return <nav className="pagination-row" aria-label="페이지 이동">
    <button className="button button-secondary" type="button" disabled={page <= 0} onClick={() => onChange(page - 1)}>이전</button>
    <span aria-live="polite">{page + 1} / {totalPages}</span>
    <button className="button button-secondary" type="button" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>다음</button>
  </nav>;
}

function ReviewForm({
  review,
  rating,
  content,
  busy,
  error,
  onRatingChange,
  onContentChange,
  onSubmit,
  onDelete,
}: {
  review: ProductReview | null;
  rating: string;
  content: string;
  busy: boolean;
  error: string | null;
  onRatingChange: (value: string) => void;
  onContentChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onDelete?: () => void;
}) {
  return <form className="product-engagement-form" onSubmit={onSubmit}>
    <div className="form-field">
      <label className="field-label" htmlFor="review-rating">평점</label>
      <select id="review-rating" className="input" value={rating} onChange={(event) => onRatingChange(event.target.value)} disabled={busy}>
        {[5, 4, 3, 2, 1].map((value) => <option key={value} value={value}>{value}점</option>)}
      </select>
    </div>
    <label className="form-field" htmlFor="review-content"><span className="field-label">리뷰 내용</span><textarea id="review-content" className="input textarea" maxLength={10000} value={content} onChange={(event) => onContentChange(event.target.value)} disabled={busy} /></label>
    {error ? <p className="field-error" role="alert">{error}</p> : null}
    <div className="button-row">
      <button className="button button-primary" type="submit" disabled={busy || !content.trim()}>{busy ? "저장 중" : review ? "리뷰 수정" : "리뷰 작성"}</button>
      {review && onDelete ? <button className="button button-danger" type="button" disabled={busy} onClick={onDelete}>리뷰 삭제</button> : null}
    </div>
  </form>;
}

export function ProductTrustSections({ productId, trust, onTrustRefresh }: ProductTrustSectionsProps) {
  const auth = useAuth();
  const [reviews, setReviews] = useState<EngagementPage<ProductReview> | null>(null);
  const [reviewPage, setReviewPage] = useState(0);
  const [reviewStatus, setReviewStatus] = useState<LoadStatus>("loading");
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [myReview, setMyReview] = useState<ProductReview | null>(null);
  const [myReviewStatus, setMyReviewStatus] = useState<LoadStatus | "empty">("loading");
  const [reviewRating, setReviewRating] = useState("5");
  const [reviewContent, setReviewContent] = useState("");
  const [reviewMutation, setReviewMutation] = useState(false);
  const [reviewMutationError, setReviewMutationError] = useState<string | null>(null);
  const [reviewMessage, setReviewMessage] = useState<string | null>(null);
  const [reviewSummary, setReviewSummary] = useState<ReviewSummary | null>(null);
  const [reviewSummaryStatus, setReviewSummaryStatus] = useState<LoadStatus>("loading");
  const [reviewSummaryError, setReviewSummaryError] = useState<string | null>(null);
  const [reviewSummaryRetry, setReviewSummaryRetry] = useState(0);
  const reviewSummaryRequestGeneration = useRef(0);
  const [questions, setQuestions] = useState<EngagementPage<ProductQuestion> | null>(null);
  const [questionPage, setQuestionPage] = useState(0);
  const [questionStatus, setQuestionStatus] = useState<LoadStatus>("loading");
  const [questionError, setQuestionError] = useState<string | null>(null);
  const [questionContent, setQuestionContent] = useState("");
  const [questionMutation, setQuestionMutation] = useState(false);
  const [questionMessage, setQuestionMessage] = useState<string | null>(null);
  const [questionMutationError, setQuestionMutationError] = useState<string | null>(null);
  const reviewRequestGeneration = useRef(0);
  const questionRequestGeneration = useRef(0);
  const myReviewRequestGeneration = useRef(0);

  const loadReviews = useCallback(async (page: number): Promise<boolean> => {
    const generation = ++reviewRequestGeneration.current;
    setReviewStatus("loading");
    setReviewError(null);
    try {
      const next = await productApi.reviews(productId, page);
      if (generation !== reviewRequestGeneration.current) return false;
      setReviews(next);
      setReviewStatus("ready");
      return true;
    } catch (error) {
      if (generation !== reviewRequestGeneration.current) return false;
      setReviewStatus("error");
      setReviewError(engagementError(error, "리뷰를 불러오지 못했습니다."));
      return false;
    }
  }, [productId]);

  const loadQuestions = useCallback(async (page: number): Promise<boolean> => {
    const generation = ++questionRequestGeneration.current;
    setQuestionStatus("loading");
    setQuestionError(null);
    try {
      const next = await productApi.questions(productId, page);
      if (generation !== questionRequestGeneration.current) return false;
      setQuestions(next);
      setQuestionStatus("ready");
      return true;
    } catch (error) {
      if (generation !== questionRequestGeneration.current) return false;
      setQuestionStatus("error");
      setQuestionError(engagementError(error, "상품 문의를 불러오지 못했습니다."));
      return false;
    }
  }, [productId]);

  const loadMyReview = useCallback(async (): Promise<boolean> => {
    const generation = ++myReviewRequestGeneration.current;
    if (auth.status !== "authenticated") {
      setMyReview(null);
      setReviewRating("5");
      setReviewContent("");
      setMyReviewStatus("empty");
      return true;
    }
    setMyReviewStatus("loading");
    try {
      const review = await productApi.myReview(productId);
      if (generation !== myReviewRequestGeneration.current) return false;
      setMyReview(review);
      setReviewRating(String(review.rating));
      setReviewContent(review.content);
      setMyReviewStatus("ready");
      return true;
    } catch (error) {
      if (generation !== myReviewRequestGeneration.current) return false;
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") {
        auth.markAnonymous();
        return false;
      }
      if (error instanceof ApiError && error.code === "REVIEW_NOT_FOUND") {
        setMyReview(null);
        setReviewRating("5");
        setReviewContent("");
        setMyReviewStatus("empty");
        return true;
      }
      setMyReviewStatus("error");
      setReviewMutationError(engagementError(error, "내 리뷰를 불러오지 못했습니다."));
      return false;
    }
  }, [auth, productId]);

  const loadReviewSummary = useCallback(async () => {
    const generation = reviewSummaryRequestGeneration.current + 1;
    reviewSummaryRequestGeneration.current = generation;
    setReviewSummaryStatus("loading");
    setReviewSummaryError(null);
    try {
      const summary = await finalProductApi.reviewSummary(productId);
      if (!isLatestRequest(generation, reviewSummaryRequestGeneration.current)) return;
      setReviewSummary(summary);
      setReviewSummaryStatus("ready");
    } catch (error) {
      if (!isLatestRequest(generation, reviewSummaryRequestGeneration.current)) return;
      setReviewSummary(null);
      setReviewSummaryStatus("error");
      setReviewSummaryError(error instanceof ApiError ? error.message : "리뷰 요약을 불러오지 못했습니다.");
    }
  }, [productId]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void loadReviews(reviewPage); }, 0);
    return () => window.clearTimeout(timer);
  }, [loadReviews, reviewPage]);
  useEffect(() => {
    const timer = window.setTimeout(() => { void loadQuestions(questionPage); }, 0);
    return () => window.clearTimeout(timer);
  }, [loadQuestions, questionPage]);
  useEffect(() => {
    const timer = window.setTimeout(() => { void loadMyReview(); }, 0);
    return () => window.clearTimeout(timer);
  }, [loadMyReview]);
  useEffect(() => {
    const timer = window.setTimeout(() => { void loadReviewSummary(); }, 0);
    return () => window.clearTimeout(timer);
  }, [loadReviewSummary, reviewSummaryRetry]);

  async function refreshReviewData(): Promise<boolean> {
    const [reviewsFresh, myReviewFresh, trustFresh] = await Promise.all([
      loadReviews(reviewPage),
      loadMyReview(),
      onTrustRefresh().then(() => true).catch(() => false),
    ]);
    return reviewsFresh && myReviewFresh && trustFresh;
  }

  async function submitReview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (auth.status !== "authenticated" || reviewMutation) return;
    setReviewMutation(true);
    setReviewMutationError(null);
    setReviewMessage(null);
    const updating = myReview !== null;
    try {
      const rating = Number(reviewRating);
      if (!Number.isInteger(rating) || rating < 1 || rating > 5 || !reviewContent.trim()) {
        throw new ApiError(400, { code: "VALIDATION_FAILED", message: "평점과 리뷰 내용을 확인해 주세요.", fieldErrors: [] });
      }
      const saved = await auth.executeWithCsrf((csrf) => myReview
        ? productApi.updateReview(myReview.reviewId, rating, reviewContent, csrf)
        : productApi.createReview(productId, rating, reviewContent, csrf));
      setMyReview(saved);
      setReviewRating(String(saved.rating));
      setReviewContent(saved.content);
      setMyReviewStatus("ready");
      setReviewMessage(updating ? "리뷰를 수정했습니다." : "리뷰를 작성했습니다.");
      void loadReviewSummary();
      if (!(await refreshReviewData())) {
        setReviewMutationError("리뷰 저장은 완료됐지만 최신 정보를 모두 불러오지 못했습니다. 다시 불러와 확인해 주세요.");
      }
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") auth.markAnonymous();
      else setReviewMutationError(engagementError(error, "리뷰를 저장하지 못했습니다."));
    } finally {
      setReviewMutation(false);
    }
  }

  async function deleteReview() {
    if (auth.status !== "authenticated" || !myReview || reviewMutation) return;
    setReviewMutation(true);
    setReviewMutationError(null);
    setReviewMessage(null);
    try {
      await auth.executeWithCsrf((csrf) => productApi.deleteReview(myReview.reviewId, csrf));
      setMyReview(null);
      setReviewRating("5");
      setReviewContent("");
      setMyReviewStatus("empty");
      setReviewMessage("리뷰를 삭제했습니다.");
      void loadReviewSummary();
      if (!(await refreshReviewData())) {
        setReviewMutationError("리뷰 삭제는 완료됐지만 최신 정보를 모두 불러오지 못했습니다. 다시 불러와 확인해 주세요.");
      }
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") auth.markAnonymous();
      else setReviewMutationError(engagementError(error, "리뷰를 삭제하지 못했습니다."));
    } finally {
      setReviewMutation(false);
    }
  }

  async function submitQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (auth.status !== "authenticated" || questionMutation || !questionContent.trim()) return;
    setQuestionMutation(true);
    setQuestionMutationError(null);
    setQuestionMessage(null);
    try {
      await auth.executeWithCsrf((csrf) => productApi.createQuestion(productId, questionContent, csrf));
      setQuestionContent("");
      const pageChangeWillReloadQuestions = questionPage !== 0;
      setQuestionPage(0);
      setQuestionMessage("상품 문의를 등록했습니다.");
      const [questionsFresh, trustFresh] = await Promise.all([
        pageChangeWillReloadQuestions ? Promise.resolve(true) : loadQuestions(0),
        onTrustRefresh().then(() => true).catch(() => false),
      ]);
      if (!questionsFresh || !trustFresh) {
        setQuestionMutationError("상품 문의 등록은 완료됐지만 최신 정보를 모두 불러오지 못했습니다. 다시 불러와 확인해 주세요.");
      }
    } catch (error) {
      if (error instanceof ApiError && error.code === "AUTH_REQUIRED") auth.markAnonymous();
      else setQuestionMutationError(engagementError(error, "상품 문의를 등록하지 못했습니다."));
    } finally {
      setQuestionMutation(false);
    }
  }

  const reviewPages = reviews?.totalPages ?? 0;
  const questionPages = questions?.totalPages ?? 0;

  return <div className="product-trust-stack">
    <section className="section-card product-trust-summary" aria-labelledby="product-trust-title">
      <div className="section-title"><div><p className="eyebrow">Trust</p><h2 id="product-trust-title">구매자 신뢰 정보</h2></div></div>
      <div className="snapshot-grid">
        <div className="snapshot-tile"><strong>{trust.averageRating === null ? "-" : `${trust.averageRating.toFixed(1)} / 5`}</strong><span>{trust.averageRating === null ? "아직 리뷰 없음" : "평균 평점"}</span></div>
        <div className="snapshot-tile"><strong>{trust.reviewCount}</strong><span>리뷰</span></div>
        <div className="snapshot-tile"><strong>{trust.questionCount}</strong><span>문의</span></div>
      </div>
    </section>

    <section className="section-card product-engagement-section" aria-labelledby="product-reviews-title">
      <div className="section-title"><div><p className="eyebrow">Reviews</p><h2 id="product-reviews-title">리뷰</h2></div><span className="count-badge">{trust.reviewCount}</span></div>
      <ReviewSummaryPanel summary={reviewSummary} status={reviewSummaryStatus} error={reviewSummaryError} onRetry={() => setReviewSummaryRetry((value) => value + 1)} />
      {reviewStatus === "loading" ? <p className="field-help" role="status">리뷰를 불러오고 있습니다.</p> : reviewStatus === "error" ? <div className="error-summary" role="alert"><p>{reviewError}</p><button className="button button-secondary" type="button" onClick={() => void loadReviews(reviewPage)}>다시 시도</button></div> : reviews?.items.length ? <ul className="engagement-list">{reviews.items.map((review) => <li key={review.reviewId}><div className="engagement-list-heading"><strong>{review.rating}점</strong><time dateTime={review.createdAt}>{formatDateTime(review.createdAt)}</time></div><p className="description">{review.content}</p></li>)}</ul> : <div className="empty-callout">아직 공개된 리뷰가 없습니다.</div>}
      <PageControls page={reviewPage} totalPages={reviewPages} onChange={setReviewPage} />
      {auth.status === "authenticated" ? <div className="product-engagement-form-wrap">
        <h3>{myReview ? "내 리뷰 수정" : "리뷰 작성"}</h3>
        {myReviewStatus === "loading" ? <p className="field-help" role="status">내 리뷰를 확인하고 있습니다.</p> : myReviewStatus === "error" ? <p className="field-error" role="alert">{reviewMutationError ?? "내 리뷰를 확인하지 못했습니다."}</p> : <ReviewForm review={myReview} rating={reviewRating} content={reviewContent} busy={reviewMutation} error={reviewMutationError} onRatingChange={(value) => { setReviewRating(value); setReviewMutationError(null); }} onContentChange={(value) => { setReviewContent(value); setReviewMutationError(null); }} onSubmit={submitReview} onDelete={myReview ? () => void deleteReview() : undefined} />}
        {reviewMessage ? <p className="notice-success" role="status">{reviewMessage}</p> : null}
      </div> : <p className="field-help">리뷰를 작성하려면 <Link href={buildLoginHref(`/products/${productId}`)}>로그인</Link>해 주세요.</p>}
    </section>

    <section className="section-card product-engagement-section" aria-labelledby="product-questions-title">
      <div className="section-title"><div><p className="eyebrow">Questions</p><h2 id="product-questions-title">상품 문의</h2></div><span className="count-badge">{trust.questionCount}</span></div>
      {questionStatus === "loading" ? <p className="field-help" role="status">상품 문의를 불러오고 있습니다.</p> : questionStatus === "error" ? <div className="error-summary" role="alert"><p>{questionError}</p><button className="button button-secondary" type="button" onClick={() => void loadQuestions(questionPage)}>다시 시도</button></div> : questions?.items.length ? <ul className="engagement-list">{questions.items.map((question) => <li key={question.questionId}><div className="engagement-list-heading"><strong>{question.answered ? "답변 완료" : "답변 대기"}</strong><time dateTime={question.createdAt}>{formatDateTime(question.createdAt)}</time></div><p className="description">{question.content}</p>{question.answered ? <div className="engagement-answer"><strong>답변</strong><p className="description">{question.answer ?? "답변 내용을 확인할 수 없습니다."}</p></div> : null}</li>)}</ul> : <div className="empty-callout">아직 공개된 상품 문의가 없습니다.</div>}
      <PageControls page={questionPage} totalPages={questionPages} onChange={setQuestionPage} />
      {auth.status === "authenticated" ? <form className="product-engagement-form-wrap product-engagement-form" onSubmit={submitQuestion}><h3>상품 문의 작성</h3><label className="form-field" htmlFor="product-question-content"><span className="field-label">문의 내용</span><textarea id="product-question-content" className="input textarea" maxLength={10000} value={questionContent} onChange={(event) => { setQuestionContent(event.target.value); setQuestionMutationError(null); }} disabled={questionMutation} /></label>{questionMutationError ? <p className="field-error" role="alert">{questionMutationError}</p> : null}<div className="button-row"><button className="button button-primary" type="submit" disabled={questionMutation || !questionContent.trim()}>{questionMutation ? "등록 중" : "문의 등록"}</button></div>{questionMessage ? <p className="notice-success" role="status">{questionMessage}</p> : null}</form> : <p className="field-help">상품 문의를 작성하려면 <Link href={buildLoginHref(`/products/${productId}`)}>로그인</Link>해 주세요.</p>}
    </section>
  </div>;
}

function ReviewSummaryPanel({ summary, status, error, onRetry }: { summary: ReviewSummary | null; status: LoadStatus; error: string | null; onRetry: () => void }) {
  if (status === "loading") return <p className="field-help" role="status">리뷰 요약을 준비하고 있습니다.</p>;
  if (status === "error" || summary?.status === "UNAVAILABLE") return <div className="review-summary-neutral"><strong>리뷰 요약을 준비하지 못했어요.</strong><span>실제 리뷰를 확인해 주세요.</span>{error ? <button className="button button-secondary" type="button" onClick={onRetry}>요약 다시 시도</button> : null}</div>;
  if (summary?.status === "INSUFFICIENT_REVIEWS") return <div className="review-summary-neutral">리뷰가 더 모이면 한눈에 요약해 드려요.</div>;
  if (!summary?.summary) return null;
  return <aside className="review-summary" aria-labelledby="review-summary-title"><p className="eyebrow">Review summary</p><h3 id="review-summary-title">리뷰 한눈에 보기</h3><p>{summary.summary}</p><div className="review-summary-facts"><span>평균 {summary.averageRating === null ? "-" : `${summary.averageRating} / 5`}</span><span>리뷰 {summary.reviewCount}개</span></div></aside>;
}
