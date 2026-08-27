import { notFound } from "next/navigation";
import { AdminProductWorkspace } from "@/components/admin-catalog/screens";

export default async function AdminProductPage({ params }: { params: Promise<{ productId: string }> }) {
  const { productId } = await params;
  if (!/^[1-9]\d*$/.test(productId) || !Number.isSafeInteger(Number(productId))) notFound();
  return <AdminProductWorkspace productId={Number(productId)} />;
}
