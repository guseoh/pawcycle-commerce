import assert from "node:assert/strict";
import test from "node:test";
import { getLogoutFailureFeedback } from "./logout-feedback.ts";

test("로그아웃 AUTH_REQUIRED는 세션 만료 안내와 상품 이동을 반환한다", () => {
  assert.deepEqual(getLogoutFailureFeedback("AUTH_REQUIRED"), {
    notice: "세션이 만료되어 로그아웃 상태로 전환됐습니다.",
    redirectTo: "/products",
  });
});

test("로그아웃 CSRF 획득 실패 안내를 유지한다", () => {
  assert.deepEqual(getLogoutFailureFeedback("CSRF_REFRESH_FAILED"), {
    notice: "보안 정보를 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요.",
  });
});

test("로그아웃 CSRF_INVALID 재시도 안내를 유지한다", () => {
  assert.deepEqual(getLogoutFailureFeedback("CSRF_INVALID"), {
    notice: "보안 정보를 새로 받았습니다. 로그아웃을 다시 눌러 주세요.",
  });
});

test("로그아웃 일반 실패 안내를 유지한다", () => {
  assert.deepEqual(getLogoutFailureFeedback("GENERAL"), {
    notice: "로그아웃하지 못했습니다. 잠시 후 다시 시도해 주세요.",
  });
});
