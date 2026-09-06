"use client";

import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { adminOperationsApi, type Operation } from "@/lib/admin-operations-api";
import { useAuth } from "@/lib/auth-context";

export function AdminOperationsScreen() {
  const { executeWithCsrf } = useAuth();
  const [items, setItems] = useState<Operation[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);

  const load = () => adminOperationsApi.list()
    .then((result) => { setItems(result); setError(null); })
    .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "운영 작업을 불러오지 못했습니다."));

  useEffect(() => { void load(); }, []);

  async function run(item: Operation, action: string) {
    const request = operationRequest(item, action);
    if (!request) return;

    setPending(`${item.type}-${action}`);
    setError(null);
    try {
      await executeWithCsrf(request);
      await load();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "작업을 처리하지 못했습니다.");
    } finally {
      setPending(null);
    }
  }

  function operationRequest(item: Operation, action: string): ((csrf: string) => Promise<void>) | null {
    switch (action) {
      case "APPROVE_RETURN":
        return (csrf) => adminOperationsApi.approveReturn(item.referenceId, csrf);
      case "REJECT_RETURN": {
        const reason = window.prompt("반려 사유를 입력하세요.");
        return reason ? (csrf) => adminOperationsApi.rejectReturn(item.referenceId, { reason }, csrf) : null;
      }
      case "PROCESS_REFUND":
        return (csrf) => adminOperationsApi.processRefund(item.referenceId, csrf);
      case "RETRY_REFUND":
        return (csrf) => adminOperationsApi.retryRefund(item.referenceId, csrf);
      case "RECONCILE_REFUND":
        return (csrf) => adminOperationsApi.reconcileRefund(item.referenceId, csrf);
      case "RECONCILE_PAYMENT":
        return (csrf) => adminOperationsApi.reconcilePayment(item.referenceId, csrf);
      case "RETRY_BILLING":
        return (csrf) => adminOperationsApi.retryBilling(item.referenceId, csrf);
      case "SHIP_DELIVERY":
      case "RESHIP_DELIVERY": {
        const carrierCode = window.prompt("택배사 코드를 입력하세요.");
        if (!carrierCode) return null;
        const trackingNumber = window.prompt("송장 번호를 입력하세요.");
        return trackingNumber
          ? (csrf) => adminOperationsApi.shipDelivery(item.referenceId, { carrierCode, trackingNumber }, csrf)
          : null;
      }
      case "COMPLETE_DELIVERY":
        return (csrf) => adminOperationsApi.completeDelivery(item.referenceId, csrf);
      case "FAIL_DELIVERY": {
        const reason = window.prompt("배송 실패 사유를 입력하세요.");
        return reason ? (csrf) => adminOperationsApi.failDelivery(item.referenceId, { reason }, csrf) : null;
      }
      case "RECEIVE_RETURN": {
        const restock = window.confirm("반품 상품을 재고로 복원하시겠습니까?");
        return (csrf) => adminOperationsApi.receiveReturn(item.referenceId, { restock }, csrf);
      }
      default:
        return null;
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
