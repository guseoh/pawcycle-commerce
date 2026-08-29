"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { commerceFinalApi, type Notification } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref, formatDateTime, formatIsoLocalDate } from "@/lib/frontend-utils";
import { notificationCopy, notificationHref } from "@/lib/notification-routing";

export function NotificationScreen() {
  const auth = useAuth();
  const [items, setItems] = useState<Notification[] | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);

  const load = useCallback(async (): Promise<boolean> => {
    try {
      const result = await commerceFinalApi.notifications();
      setItems(result);
      setMessage(null);
      return true;
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === "AUTH_REQUIRED") { auth.markAnonymous(); return false; }
      setItems(null);
      setMessage("알림을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
      return false;
    }
  }, [auth]);

  async function refresh() {
    setItems(null);
    setMessage(null);
    return load();
  }

  useEffect(() => {
    if (auth.status !== "authenticated") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [auth.status, load]);

  async function readAll() {
    setPending("all");
    setMessage(null);
    try {
      await auth.executeWithCsrf((csrf) => commerceFinalApi.readAll(csrf));
      await refresh();
    } catch {
      setMessage("알림을 읽음 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setPending(null);
    }
  }

  async function readOne(id: number) {
    setPending(String(id));
    setMessage(null);
    try {
      await auth.executeWithCsrf((csrf) => commerceFinalApi.readNotification(id, csrf));
      await refresh();
    } catch {
      setMessage("알림을 읽음 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setPending(null);
    }
  }

  if (auth.status === "anonymous") return <ErrorState title="로그인이 필요합니다." message="알림을 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/notifications")}>로그인</Link></ErrorState>;
  if (auth.status === "error") return <ErrorState title="로그인 상태를 확인할 수 없습니다." message={auth.errorMessage ?? "다시 시도해 주세요."} onRetry={() => void auth.refresh()} />;
  if (!items && !message) return <LoadingState>알림을 불러오고 있습니다.</LoadingState>;
  if (!items) return <ErrorState title="알림을 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={() => void load()} />;

  const unreadCount = items.filter((item) => !item.readAt).length;
  return <section className="section-card notification-panel"><div className="section-title"><div><p className="eyebrow">Notifications</p><h1>알림</h1><p>주문과 정기배송 소식을 관련 화면에서 바로 확인하세요.</p></div><span className="count-badge">새 알림 {unreadCount}</span></div>{message ? <p className="error-summary" role="alert">{message}</p> : null}<button className="button button-secondary" type="button" disabled={pending !== null || unreadCount === 0} onClick={() => void readAll()}>모두 읽음</button>{items.length === 0 ? <div className="empty-callout"><strong>새 알림이 없습니다.</strong><span>주문과 정기배송 상태가 바뀌면 이곳에서 알려드릴게요.</span></div> : <ul className="history-list">{items.map((item) => <li key={item.notificationId}><div><Link href={notificationHref(item)}><strong>{notificationCopy(item)}</strong></Link>{item.type === "SUBSCRIPTION_DELIVERY_REMINDER" && item.scheduledDate ? <span>예정일 {formatIsoLocalDate(item.scheduledDate)}</span> : null}<span>{formatDateTime(item.createdAt)}</span></div><span className={item.readAt ? "status-badge" : "status-badge tag-positive"}>{item.readAt ? "읽음" : "새 알림"}</span>{!item.readAt ? <button className="button button-secondary" type="button" disabled={pending !== null} onClick={() => void readOne(item.notificationId)}>읽음</button> : <Link className="button button-secondary" href={notificationHref(item)}>관련 화면</Link>}</li>)}</ul>}</section>;
}
