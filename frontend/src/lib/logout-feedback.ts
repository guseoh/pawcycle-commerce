interface LogoutFailureFeedback {
  notice: string;
  redirectTo?: "/products";
}

export type LogoutFailureReason =
  | "CSRF_REFRESH_FAILED"
  | "CSRF_INVALID"
  | "AUTH_REQUIRED"
  | "GENERAL";

export function getLogoutFailureFeedback(reason: LogoutFailureReason): LogoutFailureFeedback {
  if (reason === "CSRF_REFRESH_FAILED") {
    return { notice: "보안 정보를 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요." };
  }
  if (reason === "CSRF_INVALID") {
    return { notice: "보안 정보를 새로 받았습니다. 로그아웃을 다시 눌러 주세요." };
  }
  if (reason === "AUTH_REQUIRED") {
    return {
      notice: "세션이 만료되어 로그아웃 상태로 전환됐습니다.",
      redirectTo: "/products",
    };
  }
  return { notice: "로그아웃하지 못했습니다. 잠시 후 다시 시도해 주세요." };
}
