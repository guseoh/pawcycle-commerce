import { requestJson, requestVoid } from "./http/client.ts";
import type { Notification } from "./commerce-types.ts";

export const notificationApi = {
  list: () => requestJson<Notification[]>("/api/notifications"),
  markRead: (id: number, csrf: string) => requestVoid(`/api/notifications/${id}/read`, { method: "PATCH", headers: { "X-CSRF-TOKEN": csrf } }),
  markAllRead: (csrf: string) => requestVoid("/api/notifications/read-all", { method: "PATCH", headers: { "X-CSRF-TOKEN": csrf } }),
};

export type { Notification } from "./commerce-types.ts";
