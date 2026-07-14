import assert from "node:assert/strict";
import test from "node:test";
import {
  clearAuthentication,
  CsrfRefreshError,
  runCsrfRequest,
} from "./csrf-lifecycle.ts";

test("인증 만료 전환은 회원과 기존 CSRF token을 함께 폐기한다", () => {
  let memberId: number | null = 7;
  let token: string | null = "stale-token";
  let status = "authenticated";

  clearAuthentication(
    (value) => { memberId = value; },
    (value) => { token = value; },
    () => { status = "anonymous"; },
  );

  assert.equal(memberId, null);
  assert.equal(token, null);
  assert.equal(status, "anonymous");
});

test("익명 전환 뒤 로그인은 새 token으로 POST를 한 번만 실행한다", async () => {
  let token: string | null = null;
  let acquireCount = 0;
  let loginCount = 0;

  await runCsrfRequest({
    currentToken: token,
    acquireToken: async () => `fresh-token-${++acquireCount}`,
    setToken: (value) => { token = value; },
    request: async (requestToken) => {
      loginCount += 1;
      assert.equal(requestToken, "fresh-token-1");
    },
    isCsrfInvalid: () => false,
    refreshAfterSuccess: true,
  });

  assert.equal(loginCount, 1);
  assert.equal(acquireCount, 2);
  assert.equal(token, "fresh-token-2");
});

test("CSRF 오류는 POST를 재실행하지 않고 token 갱신 성공을 보존한다", async () => {
  const invalid = new Error("CSRF_INVALID");
  let token: string | null = "stale-token";
  let requestCount = 0;

  await assert.rejects(
    runCsrfRequest({
      currentToken: token,
      acquireToken: async () => "renewed-token",
      setToken: (value) => { token = value; },
      request: async () => {
        requestCount += 1;
        throw invalid;
      },
      isCsrfInvalid: (error) => error === invalid,
    }),
    invalid,
  );

  assert.equal(requestCount, 1);
  assert.equal(token, "renewed-token");
});

test("CSRF token 갱신 실패는 token을 비우고 별도 오류로 구분한다", async () => {
  const invalid = new Error("CSRF_INVALID");
  let token: string | null = "stale-token";
  let requestCount = 0;

  await assert.rejects(
    runCsrfRequest({
      currentToken: token,
      acquireToken: async () => { throw new Error("network"); },
      setToken: (value) => { token = value; },
      request: async () => {
        requestCount += 1;
        throw invalid;
      },
      isCsrfInvalid: (error) => error === invalid,
    }),
    CsrfRefreshError,
  );

  assert.equal(requestCount, 1);
  assert.equal(token, null);
});
