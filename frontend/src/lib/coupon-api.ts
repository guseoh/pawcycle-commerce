import { requestJson } from "./http/client.ts";
import type { MemberCoupon } from "./commerce-types.ts";

export const couponApi = {
  list: () => requestJson<MemberCoupon[]>("/api/coupons"),
};

export type { MemberCoupon } from "./commerce-types.ts";
