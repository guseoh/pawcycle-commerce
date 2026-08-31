"use client";

import { useEffect, useRef, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { useAuth } from "@/lib/auth-context";
import { createInteractionEvent, finalProductApi, type RecommendationResponse } from "@/lib/final-product-api";
import { loadRecommendation, recommendationRequestKey, recommendationRequestPetId, type RecommendationRequest } from "@/lib/recommendation";
import { RecommendationGrid } from "./recommendation-card";

function trackBestEffort(auth: ReturnType<typeof useAuth>, response: RecommendationResponse, source: string, petId?: number) {
  if (auth.status !== "authenticated") return;
  const events = response.products.flatMap((item) => {
    const event = createInteractionEvent({ type: "RECOMMENDATION_IMPRESSION", productId: item.productId, petId, recommendationRequestId: response.requestId, source });
    return event ? [event] : [];
  });
  if (!events.length) return;
  void auth.executeWithCsrf((csrf) => finalProductApi.interactions.send(events, csrf)).catch(() => undefined);
}

export function RecommendationSection({ id, title, description, request, source }: { id: string; title: string; description: string; request: RecommendationRequest; source: string }) {
  const auth = useAuth();
  const authRef = useRef(auth);
  const requestRef = useRef(request);
  const requestKey = recommendationRequestKey(request);
  const [state, setState] = useState<{ status: "loading" } | { status: "success"; data: RecommendationResponse } | { status: "error"; message: string }>({ status: "loading" });
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    authRef.current = auth;
    requestRef.current = request;
  }, [auth, request]);
  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(() => {
      if (!active) return;
      setState({ status: "loading" });
      void loadRecommendation(requestRef.current).then((data) => { if (active) { setState({ status: "success", data }); trackBestEffort(authRef.current, data, source, recommendationRequestPetId(requestRef.current)); } }).catch((error: unknown) => {
        if (active) setState({ status: "error", message: error instanceof Error ? error.message : "추천을 불러오지 못했습니다." });
      });
    }, 0);
    return () => { active = false; window.clearTimeout(timer); };
  }, [requestKey, retry, source]);

  if (state.status === "success" && state.data.products.length === 0) return null;
  return <section className="recommendation-section" data-source={source} aria-labelledby={id}>
    <div className="section-title"><div><h2 id={id}>{title}</h2><p>{description}</p></div></div>
    {state.status === "loading" ? <LoadingState>추천 상품을 불러오고 있습니다.</LoadingState> : null}
    {state.status === "error" ? <ErrorState headingLevel={3} title="추천을 불러오지 못했습니다." message={state.message} onRetry={() => setRetry((value) => value + 1)} /> : null}
    {state.status === "success" && state.data.products.length === 0 ? <div className="empty-callout">현재 추천 가능한 상품이 없습니다.</div> : null}
    {state.status === "success" && state.data.products.length > 0 ? <RecommendationGrid requestId={state.data.requestId} products={state.data.products} onClick={(item, requestId) => {
      if (auth.status !== "authenticated") return;
      const event = createInteractionEvent({ type: "RECOMMENDATION_CLICK", productId: item.productId, petId: recommendationRequestPetId(request), recommendationRequestId: requestId, source });
      if (!event) return;
      void auth.executeWithCsrf((csrf) => finalProductApi.interactions.send([event], csrf)).catch(() => undefined);
    }} /> : null}
  </section>;
}
