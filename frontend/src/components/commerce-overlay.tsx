"use client";

import { useEffect, useLayoutEffect, useRef } from "react";

/** Native dialog supplies modal inertness, keyboard containment and focus restoration. */
export function CommerceOverlay({ children, label, className, onClose, responsive = false, id }: { children: React.ReactNode; label: string; className: string; onClose: () => void; responsive?: boolean; id?: string }) {
  const ref = useRef<HTMLDialogElement>(null);
  const closeRef = useRef(onClose);
  useEffect(() => { closeRef.current = onClose; }, [onClose]);
  useLayoutEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    const trigger = document.activeElement as HTMLElement | null;
    const media = window.matchMedia("(max-width: 1023px)");
    const modal = !responsive || media.matches;
    const previous = document.body.style.overflow;
    if (modal) { dialog.showModal(); document.body.style.overflow = "hidden"; } else dialog.show();
    const pointer = (event: PointerEvent) => {
      const target = event.target as Node;
      if (target === dialog || (!modal && !dialog.contains(target) && target !== trigger)) closeRef.current();
    };
    const key = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); closeRef.current(); return; }
      if (event.key !== "Tab" || !modal) return;
      const controls = Array.from(dialog.querySelectorAll<HTMLElement>("a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),summary,[tabindex]:not([tabindex='-1'])")).filter(node => node.getClientRects().length > 0);
      const first = controls[0]; const last = controls.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    const focus = (event: FocusEvent) => { if (!modal && !dialog.contains(event.target as Node)) closeRef.current(); };
    const resize = () => closeRef.current();
    document.addEventListener("pointerdown", pointer);
    document.addEventListener("keydown", key);
    document.addEventListener("focusin", focus);
    media.addEventListener("change", resize);
    return () => {
      document.removeEventListener("pointerdown", pointer); document.removeEventListener("keydown", key); document.removeEventListener("focusin", focus); media.removeEventListener("change", resize);
      const focusWasInside = dialog.contains(document.activeElement);
      dialog.close(); document.body.style.overflow = previous;
      if ((modal || focusWasInside) && trigger?.isConnected) trigger.focus();
    };
  }, [responsive]);
  return <dialog id={id} ref={ref} className={className} aria-label={label} onCancel={(event) => { event.preventDefault(); onClose(); }}><div className="overlay-content">{children}</div></dialog>;
}
