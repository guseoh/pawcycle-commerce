import { SubscriptionDetail } from "@/components/subscription-detail";

interface SubscriptionDetailPageProps {
  params: Promise<{ subscriptionId: string }>;
  searchParams: Promise<{ created?: string | string[]; replayed?: string | string[] }>;
}

export default async function SubscriptionDetailPage({ params, searchParams }: SubscriptionDetailPageProps) {
  const [{ subscriptionId }, query] = await Promise.all([params, searchParams]);
  return (
      <SubscriptionDetail
      key={subscriptionId}
      subscriptionId={subscriptionId}
      created={query.created === "1"}
      replayed={query.replayed === "1"}
      basePath="/subscriptions"
      />
  );
}
