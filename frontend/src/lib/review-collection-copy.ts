import type { ProductSummary } from "./api.ts";

export function reviewCollectionCopy(products: Pick<ProductSummary, "reviewCount">[]) {
  if (products.length === 0) return null;
  return products.some((product) => product.reviewCount > 0)
    ? { title: "많이 이야기하는 상품", description: "다른 반려가족의 리뷰가 쌓인 상품을 만나보세요." }
    : { title: "첫 리뷰를 기다리는 상품", description: "아직 리뷰가 쌓이지 않은 상품을 먼저 만나보세요." };
}
