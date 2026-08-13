"use client";

import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { commerceFinalApi, type Operation } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";

export function AdminOperationsScreen() {
  const { executeWithCsrf } = useAuth();
  const [items, setItems] = useState<Operation[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);

  const load = () => commerceFinalApi.operations()
    .then((result) => { setItems(result); setError(null); })
    .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "운영 작업을 불러오지 못했습니다."));

  useEffect(() => { void load(); }, []);

  async function run(item: Operation, action: string) {
    let endpoint: string | null = null;
    let body: Record<string, unknown> | undefined;
    if (action === "APPROVE_RETURN") endpoint = `returns/${item.referenceId}/approve`;
    if (action === "REJECT_RETURN") {
      const reason = window.prompt("반려 사유를 입력하세요.");
      if (!reason) return;
      endpoint = `returns/${item.referenceId}/reject`;
      body = { reason };
    }
    if (action === "PROCESS_REFUND") endpoint = `refunds/${item.referenceId}/process`;
    if (action === "RETRY_REFUND") endpoint = `refunds/${item.referenceId}/retry`;
    if (action === "RECONCILE_REFUND") endpoint = `refunds/${item.referenceId}/reconcile`;
    if (action === "RECONCILE_PAYMENT") endpoint = `payments/${item.referenceId}/reconcile`;
    if (action === "RESHIP_DELIVERY") {
      const carrierCode = window.prompt("택배사 코드를 입력하세요.");
      if (!carrierCode) return;
      const trackingNumber = window.prompt("송장 번호를 입력하세요.");
      if (!trackingNumber) return;
      endpoint = `deliveries/${item.referenceId}/ship`;
      body = { carrierCode, trackingNumber };
    }
    const selectedEndpoint = endpoint;
    if (!selectedEndpoint) return;

    setPending(`${item.type}-${action}`);
    setError(null);
    try {
      await executeWithCsrf((csrf) => commerceFinalApi.operation(selectedEndpoint, csrf, body));
      await load();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "작업을 처리하지 못했습니다.");
    } finally {
      setPending(null);
    }
  }

  if (!items && !error) return <LoadingState>운영 작업을 불러오고 있습니다.</LoadingState>;
  if (!items) return <ErrorState title="운영 작업을 불러오지 못했습니다." message={error ?? "다시 시도해 주세요."} onRetry={() => void load()} />;

  return <section className="section-card">
    <h1>운영 작업</h1>
    {error ? <p role="alert">{error}</p> : null}
    {items.length === 0 ? <p>처리할 작업이 없습니다.</p> : <ul className="history-list">{items.map((item) => <li key={`${item.type}-${item.referenceId}`}>
      <strong>{item.type}</strong><span>#{item.referenceId}</span>
      <div className="button-row">{item.availableActions.map((action) => <button key={action} className="button button-secondary" disabled={pending !== null} onClick={() => void run(item, action)}>{action}</button>)}</div>
    </li>)}</ul>}
  </section>;
}
