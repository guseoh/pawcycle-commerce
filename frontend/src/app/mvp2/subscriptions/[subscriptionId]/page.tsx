import { Mvp2SubscriptionDetail } from "@/components/mvp2-subscription-detail";

export default async function Mvp2SubscriptionDetailPage({ params, searchParams }: { params: Promise<{ subscriptionId: string }>; searchParams: Promise<{ created?: string; replayed?: string }> }) {
  const [{ subscriptionId }, query] = await Promise.all([params, searchParams]);
  return <Mvp2SubscriptionDetail key={subscriptionId} subscriptionId={subscriptionId} created={query.created === "1"} replayed={query.replayed === "1"} />;
}
