import { SubscriptionDetailScreen } from "@/components/subscription-detail-screen";

interface SubscriptionDetailPageProps {
  params: Promise<{ subscriptionId: string }>;
  searchParams: Promise<{ created?: string | string[] }>;
}

export default async function SubscriptionDetailPage({ params, searchParams }: SubscriptionDetailPageProps) {
  const [{ subscriptionId }, query] = await Promise.all([params, searchParams]);
  return (
    <SubscriptionDetailScreen
      key={subscriptionId}
      subscriptionId={subscriptionId}
      created={query.created === "1"}
    />
  );
}
