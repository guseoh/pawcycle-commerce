"use client";

import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { commerceFinalApi, type Notification } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";

export function NotificationScreen() {
  const { executeWithCsrf } = useAuth();
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
      setItems(null);
      setMessage(reason instanceof Error ? reason.message : "알림을 불러오지 못했습니다.");
      return false;
    }
  }, []);

  async function refresh() {
    setItems(null);
    setMessage(null);
    return load();
  }

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function readAll() {
    setPending("all");
    setMessage(null);
    try {
      await executeWithCsrf((csrf) => commerceFinalApi.readAll(csrf));
      await refresh();
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : "알림을 읽음 처리하지 못했습니다.");
    } finally {
      setPending(null);
    }
  }

  async function readOne(id: number) {
    setPending(String(id));
    setMessage(null);
    try {
      await executeWithCsrf((csrf) => commerceFinalApi.readNotification(id, csrf));
      await refresh();
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : "알림을 읽음 처리하지 못했습니다.");
    } finally {
      setPending(null);
    }
  }

  if (!items && !message) return <LoadingState>알림을 불러오고 있습니다.</LoadingState>;
  if (!items) return <ErrorState title="알림을 불러오지 못했습니다." message={message ?? "다시 시도해 주세요."} onRetry={() => void load()} />;

  return <section className="section-card notification-panel">
    <div className="section-title"><div><p className="eyebrow">Notifications</p><h1>알림</h1></div></div>
    {message ? <p className="error-summary" role="alert">{message}</p> : null}
    <button className="button button-secondary" disabled={pending !== null} onClick={() => void readAll()}>모두 읽음</button>
    {items.length === 0 ? <p>새 알림이 없습니다.</p> : <ul className="history-list">{items.map((item) => <li key={item.notificationId}>
      <strong>{item.type}</strong><span className={item.readAt ? "status-badge" : "status-badge tag-positive"}>{item.readAt ? "읽음" : "새 알림"}</span>
      {!item.readAt ? <button className="button button-secondary" disabled={pending !== null} onClick={() => void readOne(item.notificationId)}>읽음</button> : null}
    </li>)}</ul>}
  </section>;
}
