export function productRouteMatches(routeProductId: string, currentProductId: number): boolean {
  const parsed = Number(routeProductId);
  return Number.isSafeInteger(parsed) && parsed > 0 && parsed === currentProductId;
}
