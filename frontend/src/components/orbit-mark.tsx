/** Brand signature only. Never use for status, category icons or a 16px favicon. */
export function OrbitMark({ size = 32 }: { size?: 20 | 24 | 32 | 96 | 160 }) {
  return <svg className="orbit-mark" width={size} height={size} viewBox="0 0 40 40" fill="none" aria-hidden="true"><ellipse cx="14" cy="20" rx="12" ry="18" stroke="currentColor" strokeWidth="1.6" /><ellipse cx="26" cy="20" rx="12" ry="18" stroke="currentColor" strokeWidth="1.6" /></svg>;
}
