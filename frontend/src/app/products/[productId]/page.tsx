import { ProductDetailScreen } from "@/components/product-detail-screen";

interface ProductDetailPageProps {
  params: Promise<{ productId: string }>;
}

export default async function ProductDetailPage({ params }: ProductDetailPageProps) {
  const { productId } = await params;
  return <ProductDetailScreen key={productId} productId={productId} />;
}
