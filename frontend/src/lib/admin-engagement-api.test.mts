import assert from "node:assert/strict";
import test from "node:test";
import { adminEngagementApi as api } from "./admin-engagement-api.ts";

type Call = { path: string; method: string; csrf: string | null; body: unknown };

function capture(response: Response) {
  const original = globalThis.fetch;
  const calls: Call[] = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ path: String(input), method: init?.method ?? "GET", csrf: new Headers(init?.headers).get("X-CSRF-TOKEN"), body: init?.body ? JSON.parse(String(init.body)) : undefined });
    return response.clone();
  }) as typeof fetch;
  return { calls, restore: () => { globalThis.fetch = original; } };
}

test("admin engagement reads use bounded pagination and optional product filter", async () => {
  const mock = capture(Response.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
  try {
    await api.questions(null);
    await api.reviews(42, 1, 10);
    assert.deepEqual(mock.calls.map((call) => [call.path, call.method, call.csrf, call.body]), [
      ["/api/admin/product-questions?page=0&size=20", "GET", null, undefined],
      ["/api/admin/product-reviews?page=1&size=10&productId=42", "GET", null, undefined],
    ]);
  } finally { mock.restore(); }
});

test("admin engagement mutations preserve CSRF and backend request shapes", async () => {
  const mock = capture(Response.json({}));
  try {
    await api.setReviewVisibility(7, false, "review-csrf");
    await api.answerQuestion(8, "답변입니다.", "answer-csrf");
    await api.setQuestionVisibility(8, true, "question-csrf");
    assert.deepEqual(mock.calls.map((call) => [call.path, call.method, call.csrf, call.body]), [
      ["/api/admin/product-reviews/7/visibility", "PATCH", "review-csrf", { visible: false }],
      ["/api/admin/product-questions/8/answer", "PUT", "answer-csrf", { answer: "답변입니다." }],
      ["/api/admin/product-questions/8/visibility", "PATCH", "question-csrf", { visible: true }],
    ]);
  } finally { mock.restore(); }
});
