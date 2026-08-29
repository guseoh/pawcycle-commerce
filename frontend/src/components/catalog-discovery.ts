"use client";

import { useEffect, useState } from "react";
import { catalogDiscoveryApi, type CatalogDiscovery } from "@/lib/api";

type DiscoveryState = { status: "loading" } | { status: "success"; data: CatalogDiscovery } | { status: "error"; message: string };

export function useCatalogDiscovery() {
  const [state, setState] = useState<DiscoveryState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    void catalogDiscoveryApi.get().then((data) => {
      if (active) setState({ status: "success", data });
    }).catch(() => {
      if (active) setState({ status: "error", message: "탐색 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요." });
    });
    return () => { active = false; };
  }, [attempt]);
  return { state, retry: () => { setState({ status: "loading" }); setAttempt((value) => value + 1); } };
}
