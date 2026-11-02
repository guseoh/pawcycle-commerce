/** Native buttons retain Tab/Enter; these keys move the active parent selection. */
export function categoryNavigationIndex(key: string, index: number, count: number): number {
  if (count <= 0) return -1;
  if (key === "ArrowDown") return (index + 1) % count;
  if (key === "ArrowUp") return (Math.max(index, 0) - 1 + count) % count;
  if (key === "Home") return 0;
  if (key === "End") return count - 1;
  return -1;
}
