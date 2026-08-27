export interface ProductWishlistState {
  memberId: number | null;
  productId: string;
  status: "loading" | "ready" | "error";
  value: boolean;
}

export function currentProductWishlist(state: ProductWishlistState, memberId: number | null, productId: string): ProductWishlistState {
  if (memberId !== null && state.memberId === memberId && state.productId === productId && state.status === "ready") return state;
  return { memberId, productId, status: memberId !== null && state.memberId === memberId && state.productId === productId ? state.status : "loading", value: false };
}

// This generation belongs only to wishlist reads, never to Cart/mutation requests.
export function loadProductWishlist(
  generation: { current: number }, memberId: number, productId: string,
  load: () => Promise<boolean>, publish: (state: ProductWishlistState) => void,
  onError: (error: unknown) => void,
) {
  const request = ++generation.current;
  publish({ memberId, productId, status: "loading", value: false });
  void load().then((value) => {
    if (generation.current === request) publish({ memberId, productId, status: "ready", value });
  }).catch((error: unknown) => {
    if (generation.current !== request) return;
    publish({ memberId, productId, status: "error", value: false });
    onError(error);
  });
  return () => { if (generation.current === request) generation.current += 1; };
}
