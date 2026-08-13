"use client";

import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { commerceFinalApi, type Operation } from "@/lib/commerce-final-api";
import { useAuth } from "@/lib/auth-context";

const route: Record<string, string> = { APPROVE_RETURN:"returns", REJECT_RETURN:"returns", PROCESS_REFUND:"refunds", RETRY_REFUND:"refunds", RECONCILE_REFUND:"refunds", RECONCILE_PAYMENT:"payments" };
export function AdminOperationsScreen() {
  const { executeWithCsrf } = useAuth(); const [items, setItems] = useState<Operation[] | null>(null); const [error, setError] = useState<string | null>(null); const [pending, setPending] = useState<string | null>(null);
  const load = () => commerceFinalApi.operations().then(setItems).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "운영 작업을 불러오지 못했습니다."));
  useEffect(() => { void load(); }, []);
  async function run(item: Operation, action: string) { const path = route[action]; if (!path) return; const suffix = action === "APPROVE_RETURN" ? "/approve" : action === "REJECT_RETURN" ? "/reject" : action === "PROCESS_REFUND" ? "/process" : action === "RETRY_REFUND" ? "/retry" : action === "RECONCILE_REFUND" || action === "RECONCILE_PAYMENT" ? "/reconcile" : "/ship"; const reason = action === "REJECT_RETURN" ? window.prompt("반려 사유를 입력하세요.") : null; if (action === "REJECT_RETURN" && !reason) return; setPending(`${item.type}-${action}`); try { await executeWithCsrf((csrf) => commerceFinalApi.operation(`${path}/${item.referenceId}${suffix}`, csrf, reason ? { reason } : undefined)); await load(); } catch (exception) { setError(exception instanceof Error ? exception.message : "작업을 처리하지 못했습니다."); } finally { setPending(null); } }
  if (!items && !error) return <LoadingState>운영 작업을 불러오고 있습니다.</LoadingState>;
  if (!items) return <ErrorState title="운영 작업을 불러오지 못했습니다." message={error ?? "다시 시도해 주세요."} onRetry={() => void load()} />;
  return <section className="section-card"><h1>운영 작업</h1>{items.length === 0 ? <p>처리할 작업이 없습니다.</p> : <ul className="history-list">{items.map((item) => <li key={`${item.type}-${item.referenceId}`}><strong>{item.type}</strong><span>#{item.referenceId}</span><div className="button-row">{item.availableActions.map((action) => route[action] ? <button key={action} className="button button-secondary" disabled={pending !== null} onClick={() => void run(item, action)}>{action}</button> : <span key={action}>상세 배송 화면에서 처리: {action}</span>)}</div></li>)}</ul>}</section>;
}
