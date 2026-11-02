/** Viewport coordinates for a non-modal desktop overlay. */
export function desktopOverlayPosition(anchor: { left: number; top: number; bottom: number }, panel: { width: number; height: number }, viewport: { width: number; height: number }) {
  const gap = 8;
  const margin = 16;
  const below = Math.max(0, viewport.height - anchor.bottom - gap - margin);
  const above = Math.max(0, anchor.top - gap - margin);
  const upward = below < Math.min(panel.height, 240) && above > below;
  const maxHeight = Math.min(480, upward ? above : below);
  return {
    left: Math.max(margin, Math.min(anchor.left, viewport.width - panel.width - margin)),
    top: upward ? Math.max(margin, anchor.top - gap - Math.min(panel.height, maxHeight)) : anchor.bottom + gap,
    maxHeight,
  };
}
