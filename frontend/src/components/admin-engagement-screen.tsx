"use client";

import { FormEvent, useCallback, useState } from "react";
import { formatDateTime } from "@/lib/frontend-utils";
import { adminEngagementApi, type AdminEngagementPage, type AdminQuestion, type AdminReview } from "@/lib/admin-engagement-api";
import { AdminGate, errorMessage, MutationFeedback, ResourceState, useAdminMutation, useAdminResource } from "@/components/admin-catalog/shared";

type EngagementTab = "reviews" | "questions";
const PAGE_SIZE = 20;

function productFilterValue(value: string): number | null {
  const parsed = Number(value.trim());
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export function AdminEngagementScreen() {
  return <AdminGate><AdminEngagementContent /></AdminGate>;
}

function AdminEngagementContent() {
  const [tab, setTab] = useState<EngagementTab>("questions");
  const [productInput, setProductInput] = useState("");
  const [productId, setProductId] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const loader = useCallback(async (): Promise<AdminEngagementPage<AdminReview | AdminQuestion>> => tab === "reviews"
    ? adminEngagementApi.reviews(productId, page, PAGE_SIZE)
    : adminEngagementApi.questions(productId, page, PAGE_SIZE), [page, productId, tab]);
  const resource = useAdminResource<AdminEngagementPage<AdminReview | AdminQuestion>>(loader);
  const mutation = useAdminMutation();

  function submitFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = productInput.trim();
    if (trimmed && productFilterValue(trimmed) === null) return;
    setProductId(trimmed ? productFilterValue(trimmed) : null);
    setPage(0);
  }

  function selectTab(next: EngagementTab) {
    if (next === tab) return;
    setTab(next);
    setPage(0);
  }

  const refresh = () => resource.reload();
  const totalPages = resource.data?.totalPages ?? 0;

  return <section className="admin-engagement" aria-labelledby="admin-engagement-title">
    <header className="admin-heading">
      <p className="eyebrow">ADMIN ENGAGEMENT</p>
      <h1 id="admin-engagement-title">리뷰·상품 문의 운영</h1>
      <p>고객이 남긴 리뷰와 상품 문의의 공개 상태를 관리하고, 문의에 답변합니다.</p>
    </header>
    <nav className="admin-sections" aria-label="고객 참여 영역">
      <button type="button" aria-pressed={tab === "questions"} onClick={() => selectTab("questions")}>상품 문의</button>
      <button type="button" aria-pressed={tab === "reviews"} onClick={() => selectTab("reviews")}>리뷰</button>
    </nav>
    <form className="admin-resource admin-engagement-filter" onSubmit={submitFilter}>
      <label className="form-field" htmlFor="engagement-product-id">상품 ID로 좁히기 <input id="engagement-product-id" className="input" inputMode="numeric" value={productInput} onChange={(event) => setProductInput(event.target.value)} placeholder="전체 상품" /></label>
      <div className="button-row"><button className="button button-primary" type="submit">조회</button><button className="button button-secondary" type="button" onClick={() => { setProductInput(""); setProductId(null); setPage(0); }}>전체 보기</button></div>
    </form>
    <section className="admin-resource" aria-live="polite">
      <div className="admin-section-heading"><h2>{tab === "questions" ? "상품 문의" : "리뷰"}</h2><span>{resource.data ? `${resource.data.totalElements}건` : "—"}</span></div>
      <ResourceState loading={resource.loading} error={resource.error} onRetry={() => void refresh().catch(() => undefined)} />
      {mutation.error ? <div className="admin-feedback" role="alert"><p>{errorMessage(mutation.error)}</p></div> : null}
      <MutationFeedback mutation={mutation} retry={() => void refresh().then(mutation.reset).catch(() => undefined)} />
      {resource.error ? null : resource.data ? tab === "questions" ? <QuestionList items={resource.data.items as unknown as AdminQuestion[]} pending={mutation.pending} onAnswer={(id, answer) => void mutation.run((csrf) => adminEngagementApi.answerQuestion(id, answer, csrf), refresh)} onVisibility={(id, visible) => void mutation.run((csrf) => adminEngagementApi.setQuestionVisibility(id, visible, csrf), refresh)} /> : <ReviewList items={resource.data.items as unknown as AdminReview[]} pending={mutation.pending} onVisibility={(id, visible) => void mutation.run((csrf) => adminEngagementApi.setReviewVisibility(id, visible, csrf), refresh)} /> : null}
      {resource.data && resource.data.items.length === 0 ? <div className="empty-callout">조건에 맞는 {tab === "questions" ? "상품 문의" : "리뷰"}가 없습니다.</div> : null}
      {resource.data && resource.data.items.length > 0 && totalPages > 1 ? <nav className="button-row" aria-label={`${tab === "questions" ? "상품 문의" : "리뷰"} 페이지`}><button className="button button-secondary" type="button" disabled={page === 0 || mutation.pending} onClick={() => setPage((current) => current - 1)}>이전</button><span>{page + 1} / {totalPages}</span><button className="button button-secondary" type="button" disabled={page + 1 >= totalPages || mutation.pending} onClick={() => setPage((current) => current + 1)}>다음</button></nav> : null}
    </section>
  </section>;
}

function QuestionList({ items, pending, onAnswer, onVisibility }: { items: AdminQuestion[]; pending: boolean; onAnswer: (id: number, answer: string) => void; onVisibility: (id: number, visible: boolean) => void }) {
  return <ul className="admin-engagement-list">{items.map((question) => <AdminQuestionRow key={question.questionId} question={question} pending={pending} onAnswer={onAnswer} onVisibility={onVisibility} />)}</ul>;
}

function AdminQuestionRow({ question, pending, onAnswer, onVisibility }: { question: AdminQuestion; pending: boolean; onAnswer: (id: number, answer: string) => void; onVisibility: (id: number, visible: boolean) => void }) {
  const [answer, setAnswer] = useState(question.answer ?? "");
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const trimmed = answer.trim(); if (trimmed) onAnswer(question.questionId, trimmed); };
  return <li className="admin-engagement-row"><div><strong>상품 #{question.productId} · 문의 #{question.questionId}</strong><span>회원 #{question.memberId} · {formatDateTime(question.createdAt)} · {question.answered ? "답변 완료" : "답변 대기"}</span><p>{question.content}</p></div><form className="admin-answer-form" onSubmit={submit}><label className="form-field" htmlFor={`question-answer-${question.questionId}`}>답변<textarea id={`question-answer-${question.questionId}`} className="input textarea" maxLength={10000} value={answer} onChange={(event) => setAnswer(event.target.value)} disabled={pending} /></label><div className="button-row"><button className="button button-primary" type="submit" disabled={pending || !answer.trim()}>{pending ? "저장 중…" : question.answered ? "답변 수정" : "답변 등록"}</button><button className="button button-secondary" type="button" disabled={pending} aria-pressed={question.visible} onClick={() => onVisibility(question.questionId, !question.visible)}>{question.visible ? "공개 중 · 숨기기" : "숨김 · 공개하기"}</button></div></form></li>;
}

function ReviewList({ items, pending, onVisibility }: { items: AdminReview[]; pending: boolean; onVisibility: (id: number, visible: boolean) => void }) {
  return <ul className="admin-engagement-list">{items.map((review) => <li className="admin-engagement-row" key={review.reviewId}><div><strong>상품 #{review.productId} · 리뷰 #{review.reviewId}</strong><span>회원 #{review.memberId} · {review.rating}점 · {formatDateTime(review.createdAt)}</span><p>{review.content}</p></div><button className="button button-secondary" type="button" disabled={pending} aria-pressed={review.visible} onClick={() => onVisibility(review.reviewId, !review.visible)}>{review.visible ? "공개 중 · 숨기기" : "숨김 · 공개하기"}</button></li>)}</ul>;
}
