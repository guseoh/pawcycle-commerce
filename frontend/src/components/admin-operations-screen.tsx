"use client";

import { useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { adminOperationsApi, type Operation } from "@/lib/admin-operations-api";
import { useAuth } from "@/lib/auth-context";

import { CommerceOverlay } from "./commerce-overlay";
import { createOperationExecutor } from "@/lib/admin-operation-executor";

import { createReceiptSafety } from "@/lib/receipt-safety";

const ACTION_LABELS: Record<string, string> = {
  APPROVE_RETURN: "반품 승인", REJECT_RETURN: "반품 반려", PROCESS_REFUND: "환불 처리", RETRY_REFUND: "환불 재시도", RECONCILE_REFUND: "환불 상태 확인", RECONCILE_PAYMENT: "결제 상태 확인", RETRY_BILLING: "정기결제 재시도", SHIP_DELIVERY: "배송 시작", RESHIP_DELIVERY: "재배송", COMPLETE_DELIVERY: "배송 완료", FAIL_DELIVERY: "배송 실패", RECEIVE_RETURN: "반품 입고 처리",
};

export function AdminOperationsScreen() {
  const { executeWithCsrf } = useAuth();
  const execute = useRef(createOperationExecutor()).current;
  const receiptSafety = useRef(createReceiptSafety()).current;
  const [uncertainReceipts, setUncertainReceipts] = useState<number[]>([]);
  const [receipt, setReceipt] = useState<Operation | null>(null);
  const [restock, setRestock] = useState(true);
  const [items, setItems] = useState<Operation[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);

  const closeReceipt = () => { if (!execute.isBusy()) { void execute(null); setReceipt(null); } };

  const load = () => adminOperationsApi.list()
    .then((result) => { setItems(result); setError(null); })
    .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "운영 작업을 불러오지 못했습니다."));

  useEffect(() => { void load(); }, []);

  async function run(item: Operation, action: string, restoreStock?: boolean) {
    const request = operationRequest(item, action, restoreStock);
    if (!request) return;

    try {
      await execute(async () => {
        setPending(`${item.type}-${action}`);
        setError(null);
        try {
          if (action === "RECEIVE_RETURN") {
            const result = await receiptSafety.run(item.referenceId, () => executeWithCsrf(request));
            if (result === "unknown") {
              setUncertainReceipts(current => current.includes(item.referenceId) ? current : [...current, item.referenceId]);
              setReceipt(null);
              return;
            }
          } else await executeWithCsrf(request);
          setReceipt(null);
          await load();
        } finally {
          setPending(null);
        }
      });
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "작업을 처리하지 못했습니다.");
    }
  }

  function operationRequest(item: Operation, action: string, restoreStock?: boolean): ((csrf: string) => Promise<void>) | null {
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
        return restoreStock === undefined ? null : (csrf) => adminOperationsApi.receiveReturn(item.referenceId, { restock: restoreStock }, csrf);
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
    {uncertainReceipts.map(id => <p key={id} className="provider-block" role="alert">반품 #{id} 처리 결과 확인 필요. 서버 응답을 확인하지 못해 입고·재고 반영 여부를 확정할 수 없습니다. 같은 입고 요청을 다시 보내지 말고 별도 관리자 확인을 진행해 주세요.</p>)}
    {items.length === 0 ? <p>처리할 작업이 없습니다.</p> : <ul className="history-list">{items.map((item) => <li key={`${item.type}-${item.referenceId}`}>
      <strong>{item.type}</strong><span>#{item.referenceId}</span>
      <div className="button-row">{item.availableActions.map((action) => <button key={action} className="button button-secondary" disabled={pending !== null || (action === "RECEIVE_RETURN" && uncertainReceipts.includes(item.referenceId))} onClick={() => { if (action === "RECEIVE_RETURN") { setRestock(true); setError(null); setReceipt(item); } else void run(item, action); }}>{ACTION_LABELS[action] ?? action}</button>)}</div>
    </li>)}</ul>}
    {receipt ? <CommerceOverlay label="반품 입고 확인" className="confirmation-dialog" onClose={closeReceipt}>
      <h2>반품 입고 처리</h2><p>대상 {receipt.type} #{receipt.referenceId}</p><p>반품 입고를 처리합니다. 재고 복원 여부를 확인해 주세요.</p>
      {error ? <p className="field-error" role="alert">{error}</p> : null}
      <fieldset disabled={pending !== null}><legend>재고 처리</legend><label className="radio-card"><input type="radio" name="restock" checked={restock} onChange={() => setRestock(true)} />재고 복원 후 반품 입고 처리</label><label className="radio-card"><input type="radio" name="restock" checked={!restock} onChange={() => setRestock(false)} />재고 복원 없이 반품 입고 처리</label></fieldset>
      <div className="button-row"><button className="button button-secondary" autoFocus disabled={pending !== null} onClick={closeReceipt}>작업 취소</button><button className="button button-primary" disabled={pending !== null} onClick={() => void run(receipt, "RECEIVE_RETURN", restock)}>{pending ? "처리 중" : "반품 입고 확정"}</button></div>
    </CommerceOverlay> : null}
  </section>;
}
